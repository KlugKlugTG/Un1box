/* The dongle's link watchdog: a join the guest never used is reported again.
 *
 * The model reports an association as a real dongle does (AUTH, ASSOC, SET_SSID, LINK), and it used
 * to consider that the end of it. iOS 5 is where that is not enough: its first wifid scan lands about
 * when the auto-join timer does, the driver drops a join that arrives in the middle of it, and
 * nothing ever repeats it -- the model believes itself associated while the guest has no link and no
 * lease. The Wi-Fi pane lists the network (a scan put it there) and a browser sits on "Loading",
 * which is exactly what an iPad 1 on 5.x showed while an iPod touch 4G on 6.x and an iPhone 4 on
 * 7.1.2 were fine on the same engine.
 *
 * The proof that a link is real is the guest's own traffic: DHCP leaves within a second of a join
 * the driver accepted. So the watchdog sends the association events again when the guest has said
 * nothing since the join, defers while a scan is outstanding (joining mid-scan is what gets
 * dropped in the first place), and gives up after a few tries.
 *
 * Drives the production sdio_send_assoc_events() and sdio_link_check() with a fake clock:
 *   - a join arms the check, and a silent guest gets the four events again when it fires
 *   - a guest that has sent a frame is left alone
 *   - a scan in flight defers the retry instead of joining into it
 *   - the retries are bounded, and stop
 *
 * The Wi-Fi model moved (hw/arm/ipod_touch_sdio.c became hw/arm/bcm_fullmac.c, the full-MAC
 * device, with its state in include/hw/net/bcm_fullmac.h), and the watchdog moved with it: the
 * check is bcm_fullmac_link_check() now, over the same state it always had.
 *
 * Mutation (named, must fail this test): drop the guest-traffic test in bcm_fullmac_link_check,
 * i.e. delete
 *        if (!s->associated || s->guest_frames != s->link_frames) { return; }
 * and every case that expects silence to be respected fails.
 *
 * SLICE engine include/hw/net/bcm_fullmac.h define WLC_
 * SLICE engine hw/arm/bcm_fullmac.c define BCM_LINK_
 * SLICE engine hw/arm/bcm_fullmac.c fn sdio_send_assoc_events bcm_fullmac_link_check sdio_handle_set_ssid
 * CFLAGS -Wall -Wextra -Werror -Wno-unused-function -Wno-unused-parameter
 */
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define trace_sdio(...) ((void)0)
#define NANOSECONDS_PER_SECOND 1000000000LL
#define QEMU_CLOCK_VIRTUAL 0

typedef struct {
    int64_t expire;
    int armed;
    void (*cb)(void *);
    void *opaque;
} QEMUTimer;

static int64_t now;
static int64_t qemu_clock_get_ns(int clock) { (void) clock; return now; }
static void timer_mod(QEMUTimer *timer, int64_t when) { timer->expire = when; timer->armed = 1; }

/* The fields the watchdog reads and writes, and no others. */
typedef struct BCMFullmacState {
    bool associated, iscan_reported;
    QEMUTimer *link_timer;
    unsigned link_retries, guest_frames, link_frames;
} BCMFullmacState;

#define MIN(a, b) ((a) < (b) ? (a) : (b))
static uint32_t ldl_le_p(const void *p) {
    const uint8_t *b = p;
    return b[0] | b[1] << 8 | b[2] << 16 | (uint32_t) b[3] << 24;
}

/* The slicer appends the real definitions below; the driver here comes first. */
static void sdio_send_assoc_events(BCMFullmacState *s);
static void bcm_fullmac_link_check(void *opaque);
static void sdio_handle_set_ssid(BCMFullmacState *s, const uint8_t *payload, uint32_t len);

static int events[128], nevents;
static void sdpcm_send_event(BCMFullmacState *s, uint32_t type, uint32_t status, uint16_t flags) {
    (void) s; (void) status; (void) flags;
    assert(nevents < (int) (sizeof events / sizeof events[0]));
    events[nevents++] = (int) type;
}

static const int ASSOCIATION[4] = { WLC_E_AUTH, WLC_E_ASSOC, WLC_E_SET_SSID, WLC_E_LINK };

static void fresh(BCMFullmacState *s) {
    QEMUTimer *timer = calloc(1, sizeof *timer);
    assert(timer != NULL);
    timer->cb = bcm_fullmac_link_check;
    timer->opaque = s;
    *s = (BCMFullmacState) { .iscan_reported = true, .link_timer = timer };
    nevents = 0;
}

/** The check the join armed, run when it is due: false when nothing was armed. */
static bool fire_check(BCMFullmacState *s) {
    if (!s->link_timer->armed) return false;
    int64_t when = s->link_timer->expire;
    assert(when >= now);
    now = when;
    s->link_timer->armed = 0;
    s->link_timer->cb(s->link_timer->opaque);
    return true;
}

static void expect_association(int from, const char *what) {
    assert(nevents - from == 4);
    for (int i = 0; i < 4; i++)
        assert(events[from + i] == ASSOCIATION[i] && what);
}

/** One association, in order, as the model reports it. */
static int association_events(BCMFullmacState *s) {
    int from = nevents;
    sdio_send_assoc_events(s);
    expect_association(from, "the four association events, in the driver's order");
    return from;
}

static void a_silent_guest_gets_the_join_again(void) {
    BCMFullmacState s;
    fresh(&s);
    association_events(&s);
    assert(s.link_timer->armed);
    assert(fire_check(&s));
    expect_association(4, "the join reported again: the guest never used the first one");
    assert(s.link_retries == 1);
    puts("  ok   a join the guest never used is reported again");
}

static void a_talking_guest_is_left_alone(void) {
    BCMFullmacState s;
    fresh(&s);
    association_events(&s);
    s.guest_frames = 3;   /* DHCP went out: the driver took the link */
    assert(fire_check(&s));
    assert(nevents == 4);
    assert(s.link_retries == 0);
    puts("  ok   a guest that has sent a frame is left alone");
}

static void a_scan_in_flight_defers_the_retry(void) {
    BCMFullmacState s;
    fresh(&s);
    s.iscan_reported = false;    /* the driver armed a scan: a join now is the one it drops */
    association_events(&s);
    assert(fire_check(&s));
    assert(nevents == 4);        /* nothing joined into the scan */
    assert(s.link_timer->armed); /* and it will look again */
    assert(s.link_timer->expire == now + 2 * NANOSECONDS_PER_SECOND);
    s.iscan_reported = true;     /* the scan reported its BSS */
    assert(fire_check(&s));
    expect_association(4, "the join after the scan finished");
    puts("  ok   a scan in flight defers the retry, and the retry comes after it");
}

static void the_retries_stop(void) {
    BCMFullmacState s;
    fresh(&s);
    association_events(&s);
    int attempts = 0;
    while (fire_check(&s)) {
        if (nevents > 4 + 4 * attempts) attempts++;
        if (attempts > 16) break;   /* a runaway retry loop, not a bound */
    }
    assert(attempts == BCM_LINK_RETRIES_MAX);
    assert(nevents == 4 * (1 + BCM_LINK_RETRIES_MAX));
    assert(!s.link_timer->armed);
    puts("  ok   the retries are bounded and then stop");
}

static void a_driver_asked_join_gets_a_fresh_budget(void) {
    BCMFullmacState s;
    fresh(&s);
    association_events(&s);
    while (fire_check(&s)) { }             /* the auto-join's budget runs out */
    assert(s.link_retries == BCM_LINK_RETRIES_MAX);
    int from = nevents;

    /* WLC_SET_SSID: the driver itself asking to join this SSID (wlc_ssid_t: length then bytes). */
    uint8_t payload[4 + 8] = { 8, 0, 0, 0, 'q', 'e', 'm', 'u', '-', 'i', 'o', 's' };
    sdio_handle_set_ssid(&s, payload, sizeof payload);
    expect_association(from, "the events of the join the driver asked for");
    assert(s.link_retries == 0);
    assert(fire_check(&s));
    expect_association(from + 4, "and that join is watched too");
    puts("  ok   a driver-asked join starts a new budget");
}

int main(void) {
    puts("the link watchdog");
    a_silent_guest_gets_the_join_again();
    a_talking_guest_is_left_alone();
    a_scan_in_flight_defers_the_retry();
    the_retries_stop();
    a_driver_asked_join_gets_a_fresh_budget();
    puts("all 5 link-watchdog checks pass");
    return 0;
}
