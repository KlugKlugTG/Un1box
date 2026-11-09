"""The link watchdog's connections, checked as text: the patch has to be armed, fed and saved.

tests/network/run.py compiles and drives the watchdog itself. What it cannot see is whether the rest
of the dongle model feeds it: the events are only re-sent if the guest's own frames are counted where
they leave the model, the timer has to exist for it to be armed, the state has to be cleared with the
machine, and a saved machine has to carry it.

Each check raises with one sentence when it fails, and returns a short note when it passes.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCH = ROOT / "ci" / "engine-patches" / "0002-engine-sdio-link-retry.patch"
HEADER = "include/hw/arm/ipod_touch_sdio.h"
SOURCE = "hw/arm/ipod_touch_sdio.c"


def read(path):
    if not path.is_file():
        raise AssertionError(f"{path.relative_to(ROOT)} is missing")
    return path.read_text(errors="ignore")


def added_lines(patch):
    return "\n".join(line[1:] for line in read(patch).splitlines() if line.startswith("+"))


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def the_watchdog_is_armed_by_every_association():
    patch = read(PATCH)
    require("static void sdio_link_check(void *opaque)" in added_lines(PATCH),
            "the patch adds no link check: a join the guest drops is never repeated")
    # The hunk header names the function it patches (git puts it there), so the arming is looked for
    # in the hunk that changes sdio_send_assoc_events rather than anywhere in the file.
    for hunk in patch.split("\n@@ "):
        if "sdio_send_assoc_events" not in hunk.splitlines()[0]:
            continue
        arming = "\n".join(line[1:] for line in hunk.splitlines() if line.startswith("+"))
        require("timer_mod(s->link_timer" in arming,
                "the association does not arm the check, so nothing looks at whether the guest used it")
        require("s->link_frames = s->guest_frames;" in arming,
                "the join does not record which frame count it started from: every guest would look "
                "silent or none would")
        return "armed by the association, with the frame count it starts from"
    raise AssertionError("the patch no longer adds the arming to sdio_send_assoc_events")


def the_guest_s_own_traffic_is_the_signal():
    body = added_lines(PATCH)
    check = body.split("static void sdio_link_check", 1)
    require(len(check) > 1, "the check itself is gone")
    check = check[1].split("\n}", 1)[0]
    require("s->guest_frames != s->link_frames" in check,
            "the check does not look at whether the guest has sent anything: it would either retry "
            "for ever or never")
    require(re.search(r"if \(!s->iscan_reported\)", check) is not None,
            "the check does not defer while a scan is outstanding, and a join during a scan is the "
            "one the driver drops -- what the retry must not run into")
    require("SDIO_LINK_RETRIES_MAX" in check,
            "the retries are unbounded: a guest that never comes up would be joined at for ever")
    require("s->guest_frames++" in body,
            "nothing counts the guest's frames, so the check can never see the guest talking: the "
            "count has to be added where a guest frame leaves the dongle")
    return "guest frames counted, scan-guarded, bounded"


def the_state_lives_and_dies_with_the_machine():
    body = added_lines(PATCH)
    for field in ("link_timer", "link_retries", "guest_frames", "link_frames"):
        require(field in read(PATCH),
                f"the patch does not carry {field}: the watchdog would read state the model has not got")
    require("timer_del(s->link_timer);" in body,
            "the machine's reset does not stop the check: a stale timer would fire into a new session")
    require(re.search(r"s->link_retries = s->guest_frames = s->link_frames = 0;", body) is not None,
            "the machine's reset does not clear the counters")
    require(re.search(r"s->link_timer = timer_new_ns\(QEMU_CLOCK_VIRTUAL, sdio_link_check, s\);", body)
            is not None,
            "the check's timer is never created, so the arming has nothing to arm")
    require("VMSTATE_TIMER_PTR(link_timer" in body and "version_id = 2" in body,
            "a saved machine does not carry the watchdog (or its version was not raised for the new "
            "fields)")
    require(re.search(r"s->link_retries = 0;", body) is not None,
            "a join the driver asks for does not get a fresh retry budget")
    return "created, reset, saved (version 2), and a driver-asked join starts over"


def the_resolver_caches_what_the_phone_answered():
    """A phone's resolver can take seconds behind a VPN, and Cydia asks for the same names again."""
    patch = ROOT / "ci" / "engine-patches" / "0003-engine-dns-cache.patch"
    body = read(patch)
    require("dns_cache_lookup" in body and "dns_cache_store" in body,
            "the Android resolver still asks the phone's resolver for every query: with a slow DNS "
            "(seconds per name) the guest feels dead")
    require("DNS_CACHE_TTL_S" in body and "DNS_CACHE_FAILURE_TTL_S" in body,
            "the cache has no lifetime: an answer would be reused for ever")
    require("G_LOCK" in body,
            "the cache is shared between the jobs' own threads with no lock")
    require("dns_cache_lookup(j, name, qtype)" in body,
            "the resolver does not consult the cache before asking")
    return "cached with a TTL, locked, and consulted first"


CHECKS = [
    ("the patch: every association arms the check", the_watchdog_is_armed_by_every_association),
    ("the patch: the guest's own traffic is the signal", the_guest_s_own_traffic_is_the_signal),
    ("the patch: the state is created, reset and saved", the_state_lives_and_dies_with_the_machine),
]
    ("the engine: the Android resolver caches its answers", the_resolver_caches_what_the_phone_answered),
]
