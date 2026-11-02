/* The guest's capture port hands out what the frontend's microphone recorded.
 *
 * This is the path a phone's recording actually takes once the app pushes:
 *   host_mic_push() -> ring -> i2s_next_frame() -> RX FIFO -> the guest reads it
 * and it is exercised here against the real hw/arm/host-mic.c and the real
 * i2s_next_frame()/i2s_rx_read(), sliced out of the engine rather than copied.
 *
 * The case that made the first device build record silence is first: the audio
 * backend the phone's engine runs (driver=none) DOES open an input voice and
 * hands out silence paced at the port's rate, so its ring is never empty. If the
 * port consults that ring before the frontend, the guest records silence no
 * matter what the microphone captured.
 *
 * SLICE engine hw/arm/s5l8930_i2s.c fn i2s_next_frame i2s_rx_read
 * SLICE engine hw/arm/s5l8930_i2s.c define I2S_RING
 * SLICE engine hw/arm/s5l8930_i2s.c define TONE_AMPLITUDE
 * CFLAGS -Wall -Wextra -Werror -Wno-unused-function -Wno-unused-parameter
 * LDLIBS -lm
 */
#include <assert.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <string.h>

#include "hw/arm/host-mic.h"
#include "qemu/bswap.h"

/* The test's clock, substituted for glib's in stub/qemu/osdep.h. */
extern int64_t mic_test_now_us;

/* hw/arm/s5l8930_i2s.c's state, reduced to the fields these two functions
 * touch: whether this board gave the port the frontend's microphone, the port's
 * ring of host input, the frame being read out, and the tone property. */
typedef struct S5L8930I2SState {
    bool host_mic;
    int tone_hz;
    uint64_t tone_frame;
    unsigned in_rate;
    uint8_t in_ring[I2S_RING];
    uint32_t in_head, in_tail, in_level;
    uint8_t rx_frame[4];
    unsigned rx_pos;
} S5L8930I2SState;

static void i2s_next_frame(S5L8930I2SState *s);
static uint64_t i2s_rx_read(S5L8930I2SState *s, unsigned size);

/* What the guest sees: one stereo S16 frame per four bytes read. */
static void read_frames(S5L8930I2SState *s, int16_t (*out)[2], int frames)
{
    for (int i = 0; i < frames; i++) {
        uint64_t value = i2s_rx_read(s, 4);

        out[i][0] = (int16_t)(value & 0xffff);
        out[i][1] = (int16_t)((value >> 16) & 0xffff);
    }
}

/* The none backend's voice: full of silence, and never empty while it runs. */
static void silent_voice(S5L8930I2SState *s)
{
    memset(s->in_ring, 0, sizeof s->in_ring);
    s->in_head = 0;
    s->in_tail = 0;
    s->in_level = I2S_RING;
}

static void reset_clock(void) { mic_test_now_us = 1000000; }

static void test_microphone_wins_over_a_silent_voice(void)
{
    S5L8930I2SState port = { .host_mic = true };
    int16_t sent[512][2], got[512][2];

    reset_clock();
    silent_voice(&port);
    host_mic_set_rate(44100);
    for (int i = 0; i < 512; i++) {
        sent[i][0] = (int16_t)(1000 + i);
        sent[i][1] = (int16_t)(-1000 - i);
    }
    assert(host_mic_push(sent, sizeof sent) == 512);
    read_frames(&port, got, 512);
    for (int i = 0; i < 512; i++) {
        assert(got[i][0] == sent[i][0]);
        assert(got[i][1] == sent[i][1]);
    }
    /* And the voice's ring was left alone: it is still full of its silence. */
    assert(port.in_level == I2S_RING);
}

/* The S5L8920 boards share this port model but set no `host-mic` property
 * (patch 0013): the ring is left alone and the guest hears the voice's
 * silence, exactly as it did before the microphone patch. */
static void test_a_board_without_the_microphone_ignores_the_ring(void)
{
    S5L8930I2SState port = {0};
    int16_t sent[8][2], got[8][2];
    HostMicState state;

    reset_clock();
    host_mic_flush();
    silent_voice(&port);
    host_mic_set_rate(44100);
    for (int i = 0; i < 8; i++) {
        sent[i][0] = 5000;
        sent[i][1] = -5000;
    }
    assert(host_mic_push(sent, sizeof sent) == 8);
    read_frames(&port, got, 8);
    for (int i = 0; i < 8; i++) {
        assert(got[i][0] == 0 && got[i][1] == 0);
    }
    /* And it consumed nothing: the frames are still there for a port that has
     * the microphone, which is the point of not asking. */
    host_mic_state(&state);
    assert(state.queued == 8);
    host_mic_flush();
}

static void test_voice_answers_when_nothing_was_recorded(void)
{
    S5L8930I2SState port = { .host_mic = true };
    int16_t got[2][2];

    reset_clock();
    host_mic_flush();
    memset(port.in_ring, 0, sizeof port.in_ring);
    /* A host voice's own frames: 0x0102, 0x0304 for the left/right pair. */
    port.in_ring[0] = 0x02; port.in_ring[1] = 0x01;
    port.in_ring[2] = 0x04; port.in_ring[3] = 0x03;
    port.in_head = 0;
    port.in_tail = 0;
    port.in_level = 4;
    read_frames(&port, got, 1);
    assert(got[0][0] == 0x0102);
    assert(got[0][1] == 0x0304);
    assert(port.in_level == 0);
}

static void test_nothing_anywhere_is_silence(void)
{
    S5L8930I2SState port = {0};
    int16_t got[4][2];

    reset_clock();
    host_mic_flush();
    memset(&port, 0, sizeof port);
    read_frames(&port, got, 4);
    for (int i = 0; i < 4; i++) {
        assert(got[i][0] == 0 && got[i][1] == 0);
    }
}

static void test_a_slower_port_resamples(void)
{
    S5L8930I2SState port = { .host_mic = true };
    int16_t sent[64][2], got[64][2];

    reset_clock();
    host_mic_flush();
    silent_voice(&port);
    host_mic_set_rate(22050);
    for (int i = 0; i < 64; i++) {
        sent[i][0] = (int16_t)(i * 3);
        sent[i][1] = (int16_t)(-i * 3);
    }
    assert(host_mic_push(sent, sizeof sent) == 64);
    read_frames(&port, got, 32);
    /* 44.1 kHz into a 22.05 kHz port: every second recorded frame, in pitch.
     * (The guest's own HAL interpolates after this point; here the point is
     * that the port reads at its rate, not at the recorder's.) */
    for (int i = 0; i < 32; i++) {
        assert(got[i][0] == sent[2 * i][0]);
        assert(got[i][1] == sent[2 * i][1]);
    }
}

static void test_the_tail_stops_when_recording_stops(void)
{
    S5L8930I2SState port = { .host_mic = true };
    int16_t sent[8][2], got[8][2];

    reset_clock();
    host_mic_flush();
    silent_voice(&port);
    host_mic_set_rate(44100);
    for (int i = 0; i < 8; i++) {
        sent[i][0] = 7000;
        sent[i][1] = 7000;
    }
    assert(host_mic_push(sent, sizeof sent) == 8);
    /* The app stopped pushing 300 ms ago: the ring's tail must not loop. */
    mic_test_now_us += 300000;
    read_frames(&port, got, 8);
    for (int i = 0; i < 8; i++) {
        assert(got[i][0] == 0 && got[i][1] == 0);
    }
}

static void test_a_stopped_capture_leaves_nothing_behind(void)
{
    S5L8930I2SState port = { .host_mic = true };
    int16_t sent[8][2], got[2][2];
    HostMicState state;

    reset_clock();
    silent_voice(&port);
    host_mic_set_rate(44100);
    assert(host_mic_push(sent, sizeof sent) == 8);
    host_mic_flush();
    host_mic_state(&state);
    assert(state.capturing == 0);
    assert(state.queued == 0);
    read_frames(&port, got, 2);
    assert(got[0][0] == 0 && got[1][0] == 0);
}

static void test_bytes_are_little_endian_per_half(void)
{
    S5L8930I2SState port = { .host_mic = true };
    int16_t sent[1][2] = {{0x1234, (int16_t)0xabcd}};
    uint8_t bytes[4];

    reset_clock();
    host_mic_flush();
    silent_voice(&port);
    host_mic_set_rate(44100);
    assert(host_mic_push(sent, sizeof sent) == 1);
    for (int i = 0; i < 4; i++) {
        bytes[i] = (uint8_t)i2s_rx_read(&port, 1);
    }
    /* Left 0x1234 then right 0xabcd, each low byte first: what the guest's
     * 32-bit read of the RX FIFO reassembles. */
    assert(bytes[0] == 0x34 && bytes[1] == 0x12);
    assert(bytes[2] == 0xcd && bytes[3] == 0xab);
}

static void test_a_full_ring_drops_and_counts(void)
{
    static int16_t sent[HOST_MIC_FRAMES + 64][2];
    HostMicState before, after;

    reset_clock();
    host_mic_flush();
    host_mic_state(&before);
    assert(host_mic_push(sent, sizeof sent) == HOST_MIC_FRAMES);
    host_mic_state(&after);
    assert(after.queued == HOST_MIC_FRAMES);
    assert(after.pushed - before.pushed == HOST_MIC_FRAMES);
    assert(after.dropped - before.dropped == 64);
}

int main(void)
{
    test_microphone_wins_over_a_silent_voice();
    test_a_board_without_the_microphone_ignores_the_ring();
    test_voice_answers_when_nothing_was_recorded();
    test_nothing_anywhere_is_silence();
    test_a_slower_port_resamples();
    test_the_tail_stops_when_recording_stops();
    test_a_stopped_capture_leaves_nothing_behind();
    test_bytes_are_little_endian_per_half();
    test_a_full_ring_drops_and_counts();
    puts("capture port: the frontend's microphone reaches the guest on a board that has one, and "
         "silence still wins when it should");
    return 0;
}
