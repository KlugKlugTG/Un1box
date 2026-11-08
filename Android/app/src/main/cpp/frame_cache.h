#pragma once
#include "lighttouch.h"
#include <cstdio>
#include <new>
#include <vector>

/** Sole presenter writes under its control guard; captures only read under it. */
struct FrameCopyCache {
    std::vector<uint8_t> pixels;
    lt_frame_info frame = LT_INIT(lt_frame_info);

    static bool valid(const lt_frame_info &f) {
        return f.size >= sizeof(f) && f.version == 1 && f.width && f.width <= 4096 &&
               f.height && f.height <= 4096 && f.format == 1 && f.sequence &&
               uint64_t(f.stride) >= uint64_t(f.width) * 4 && f.bytes &&
               f.bytes == uint64_t(f.stride) * f.height && f.bytes <= LT_FRAME_CAPACITY;
    }
    template<class Invalidated> void invalidate(Invalidated invalidated) {
        frame = lt_frame_info LT_INIT(lt_frame_info);
        invalidated();
    }
    /** Query reports metadata only. Neither query nor a failed copy advances since. */
    template<class Query, class Copy, class Invalidated>
    lt_status latest(uint64_t &since, lt_error &error, Query query, Copy copy, Invalidated invalidated) {
        auto fail = [&](const char *message) {
            invalidate(invalidated);
            error.code = LT_ENGINE;
            std::snprintf(error.message, sizeof(error.message), "%s", message);
            return LT_ENGINE;
        };
        for (unsigned attempt = 0; attempt < 3; ++attempt) {
            lt_frame_info metadata = LT_INIT(lt_frame_info);
            const auto described = query(since, metadata, error);
            if (described != LT_OK) return described;
            if (!valid(metadata) || metadata.sequence <= since)
                return fail("Invalid published guest frame metadata");
            if (since == 0 && valid(frame) && metadata.sequence == frame.sequence &&
                metadata.width == frame.width && metadata.height == frame.height &&
                metadata.stride == frame.stride && metadata.format == frame.format &&
                metadata.bytes == frame.bytes && frame.bytes <= pixels.size()) {
                // Surface reattach, scale change, or resume rewinds `since` to 0 to
                // repaint; reuse the already-cached frame when no newer sequence exists.
                frame.timestamp_ns = metadata.timestamp_ns;
                since = frame.sequence;
                return LT_OK;
            }
            if (metadata.bytes > pixels.size()) {
                // Capture may not observe old dimensions against changed storage,
                // including when allocation or the following copy fails.
                invalidate(invalidated);
                try {
                    // resize() may retain geometric capacity beyond the actual
                    // validated requirement. Fresh construction avoids that
                    // growth policy; old storage is released after the swap.
                    std::vector<uint8_t> replacement(size_t(metadata.bytes));
                    if (replacement.capacity() > metadata.bytes)
                        return fail("Guest frame cache allocation exceeded its size bound");
                    pixels.swap(replacement);
                }
                catch (const std::bad_alloc &) { return fail("Cannot allocate guest frame cache"); }
            }
            lt_frame_info copied = LT_INIT(lt_frame_info);
            uint64_t next = since;
            const auto status = copy(pixels.data(), pixels.size(), next, copied, error);
            if (status == LT_OK) {
                if (!valid(copied) || copied.bytes > pixels.size() || next != copied.sequence || next <= since)
                    return fail("Invalid copied guest frame metadata");
                frame = copied;
                since = next;
                return LT_OK;
            }
            if (status == LT_AGAIN) return status; // No bytes copied; keep a still-valid cache.
            invalidate(invalidated);
            if (status != LT_INVALID || attempt == 2) return status;
            // Existing copy_frame reports BUFFER_SMALL as LT_INVALID. Re-query
            // bounded metadata rather than parsing its error text or trusting a
            // metadata generation while engine dimensions change independently.
        }
        return LT_AGAIN;
    }
};
