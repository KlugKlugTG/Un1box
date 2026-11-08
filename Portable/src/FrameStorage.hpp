#pragma once
#include "lighttouch.h"
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <vector>

namespace lt {
inline bool valid_frame(const lt_frame_info &f) {
    return f.size == sizeof(f) && f.version == 1 && f.sequence && f.format == 1 && f.width && f.height &&
           f.width <= 4096 && f.height <= 4096 && uint64_t(f.stride) >= uint64_t(f.width) * 4 && f.bytes &&
           f.bytes <= LT_FRAME_CAPACITY && f.bytes <= std::numeric_limits<size_t>::max() &&
           f.bytes >= uint64_t(f.stride) * f.height;
}
// Metadata fields are individually atomic, including same-process readers.
// The existing generation bracket makes the whole snapshot coherent. The
// physical C ABI and readonly-FD layout do not change. Relaxed per-field
// operations are ordered by the writer's acquire-release/release generation
// bracket and the reader's acquire load plus acquire fence before re-checking
// generation, avoiding nine full sequential-consistency barriers per access.
inline void store_frame_metadata(lt_frame_info &to, const lt_frame_info &f) {
#define STORE(field) __atomic_store_n(&to.field, f.field, __ATOMIC_RELAXED)
    STORE(size);
    STORE(version);
    STORE(width);
    STORE(height);
    STORE(stride);
    STORE(format);
    STORE(sequence);
    STORE(timestamp_ns);
    STORE(bytes);
#undef STORE
}
inline lt_frame_info load_frame_metadata(const lt_frame_info &from) {
    lt_frame_info f{};
#define LOAD(field) f.field = __atomic_load_n(&from.field, __ATOMIC_RELAXED)
    LOAD(size);
    LOAD(version);
    LOAD(width);
    LOAD(height);
    LOAD(stride);
    LOAD(format);
    LOAD(sequence);
    LOAD(timestamp_ns);
    LOAD(bytes);
#undef LOAD
    return f;
}
enum class MetadataRead { fresh, again, invalid };
// Observer hook permits deterministic interference testing of the actual
// reader, without a second test implementation. Production uses a no-op hook.
template <class Observe>
MetadataRead read_frame_metadata(const lt_shared_frame *shared, uint64_t since, lt_frame_info &out, Observe observe) {
    if (!shared || shared->size != sizeof(*shared) || shared->version != 1 || shared->capacity != LT_FRAME_CAPACITY)
        return MetadataRead::invalid;
    for (unsigned attempt = 0; attempt < 3; ++attempt) {
        const auto before = __atomic_load_n(&shared->generation, __ATOMIC_ACQUIRE);
        if (before & 1)
            continue;
        const auto f = load_frame_metadata(shared->frame);
        observe(attempt);
        __atomic_thread_fence(__ATOMIC_ACQUIRE);
        const auto after = __atomic_load_n(&shared->generation, __ATOMIC_RELAXED);
        if (before != after || (after & 1))
            continue;
        // Never diagnose malformed bounds from a torn intermediate record.
        if (!f.sequence)
            return MetadataRead::again;
        if (!valid_frame(f))
            return MetadataRead::invalid;
        if (f.sequence <= since)
            return MetadataRead::again;
        out = f;
        return MetadataRead::fresh;
    }
    return MetadataRead::again;
}
inline MetadataRead read_frame_metadata(const lt_shared_frame *shared, uint64_t since, lt_frame_info &out) {
    return read_frame_metadata(shared, since, out, [](unsigned) {});
}
inline bool publish_frame_metadata(lt_shared_frame *shared, const lt_frame_info &f) {
    if (!shared || shared->size != sizeof(*shared) || shared->version != 1 || !valid_frame(f) ||
        shared->capacity != LT_FRAME_CAPACITY)
        return false;
    __atomic_add_fetch(&shared->generation, 1, __ATOMIC_ACQ_REL);
    __atomic_thread_fence(__ATOMIC_ACQ_REL);
    store_frame_metadata(shared->frame, f);
    __atomic_add_fetch(&shared->generation, 1, __ATOMIC_RELEASE);
    return true;
}
inline bool publish_frame(lt_shared_frame *shared, const lt_frame_info &f, const void *pixels, size_t capacity) {
    if (!shared || shared->size != sizeof(*shared) || shared->version != 1 || !valid_frame(f) || !pixels ||
        f.bytes > capacity || shared->capacity != LT_FRAME_CAPACITY)
        return false;
    __atomic_add_fetch(&shared->generation, 1, __ATOMIC_ACQ_REL);
    __atomic_thread_fence(__ATOMIC_ACQ_REL);
    store_frame_metadata(shared->frame, f);
    memcpy(reinterpret_cast<uint8_t *>(shared) + sizeof(*shared), pixels, size_t(f.bytes));
    __atomic_add_fetch(&shared->generation, 1, __ATOMIC_RELEASE);
    return true;
}
enum class FrameCopy { copied, small, no_frame, not_ready };
enum class FrameAcquire { copied, retry, no_frame, not_ready, invalid };
template <class Copy>
FrameAcquire acquire_frame(std::vector<uint8_t> &scratch, uint64_t since, lt_frame_info &out, Copy copy) {
    lt_frame_info f{sizeof(f), 1, 0, 0, 0, 0, 0, 0, 0};
    if (!scratch.empty()) {
        const auto direct = copy(scratch.data(), scratch.size(), since, f);
        if (direct == FrameCopy::no_frame)
            return FrameAcquire::no_frame;
        if (direct == FrameCopy::not_ready)
            return FrameAcquire::not_ready;
        if (!valid_frame(f))
            return FrameAcquire::invalid;
        if (direct == FrameCopy::copied) {
            if (f.bytes > scratch.size())
                return FrameAcquire::invalid;
            out = f;
            return FrameAcquire::copied;
        }
        if (direct != FrameCopy::small || f.bytes <= scratch.size())
            return FrameAcquire::invalid;
    } else {
        const auto result = copy(nullptr, 0, since, f);
        if (result == FrameCopy::no_frame)
            return FrameAcquire::no_frame;
        if (result == FrameCopy::not_ready)
            return FrameAcquire::not_ready;
        if (result != FrameCopy::small || !valid_frame(f))
            return FrameAcquire::invalid;
    }
    for (unsigned attempt = 0; attempt < 3; ++attempt) {
        if (f.bytes > scratch.size()) {
            // Avoid vector's geometric growth exceeding the frame-size bound.
            std::vector<uint8_t> grown(size_t(f.bytes));
            scratch.swap(grown);
        }
        const auto result = copy(scratch.data(), scratch.size(), since, f);
        if (result == FrameCopy::no_frame)
            return FrameAcquire::no_frame;
        if (result == FrameCopy::not_ready)
            return FrameAcquire::not_ready;
        if (!valid_frame(f))
            return FrameAcquire::invalid;
        if (result == FrameCopy::small) {
            if (f.bytes <= scratch.size())
                return FrameAcquire::invalid; // Contradictory capacity result.
            continue;
        }
        if (result != FrameCopy::copied || f.bytes > scratch.size())
            return FrameAcquire::invalid;
        out = f;
        return FrameAcquire::copied;
    }
    return FrameAcquire::retry;
}
} // namespace lt
