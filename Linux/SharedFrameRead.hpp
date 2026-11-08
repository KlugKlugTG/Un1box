#pragma once
#include "../Portable/src/FrameStorage.hpp"

namespace lt {
enum class ScreenshotRead { copied, again, invalid };
// An explicit screenshot rereads the current worker mapping, including a static
// sequence. Metadata and pixels belong to one equal/even publication bracket.
// Ordinary presentation retains its existing latest-sequence path.
template <class Observe>
ScreenshotRead screenshot_read(const lt_shared_frame *shared, std::vector<uint8_t> &pixels, lt_frame_info &frame,
                               uint64_t &generation, Observe observe) {
    if (!shared || shared->size != sizeof(*shared) || shared->version != 1 || shared->capacity != LT_FRAME_CAPACITY)
        return ScreenshotRead::invalid;
    for (unsigned attempt = 0; attempt < 3; ++attempt) {
        const auto before = __atomic_load_n(&shared->generation, __ATOMIC_ACQUIRE);
        if (before & 1)
            continue;
        const auto candidate = load_frame_metadata(shared->frame);
        __atomic_thread_fence(__ATOMIC_ACQUIRE);
        const auto metadata_after = __atomic_load_n(&shared->generation, __ATOMIC_RELAXED);
        if (before != metadata_after || (metadata_after & 1))
            continue;
        if (!candidate.sequence)
            return ScreenshotRead::again;
        if (!valid_frame(candidate))
            return ScreenshotRead::invalid;
        if (pixels.size() < candidate.bytes) {
            std::vector<uint8_t> grown(size_t(candidate.bytes));
            pixels.swap(grown);
        }
        memcpy(pixels.data(), reinterpret_cast<const uint8_t *>(shared) + sizeof(*shared), size_t(candidate.bytes));
        observe(attempt);
        __atomic_thread_fence(__ATOMIC_ACQUIRE);
        const auto after = __atomic_load_n(&shared->generation, __ATOMIC_RELAXED);
        if (before != after || (after & 1))
            continue;
        frame = candidate;
        generation = after;
        return ScreenshotRead::copied;
    }
    // Caller must discard candidate pixels on AGAIN; frame/proof stay unchanged.
    return ScreenshotRead::again;
}
inline ScreenshotRead screenshot_read(const lt_shared_frame *shared, std::vector<uint8_t> &pixels, lt_frame_info &frame,
                                      uint64_t &generation) {
    return screenshot_read(shared, pixels, frame, generation, [](unsigned) {});
}
} // namespace lt
