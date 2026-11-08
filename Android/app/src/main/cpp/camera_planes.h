// One YUV_420_888 image into the NV12 frame the emulated ISP draws.
//
// Android hands a camera frame as three planes with a row stride (bytes between
// rows) and a pixel stride (bytes between samples) that are the camera's, not
// the picture's: phones pad rows for alignment, and some deliver chroma
// semi-planar (two planes interleaved, pixel stride 2) while others deliver it
// planar (pixel stride 1). All three shapes are legal in one frame's description,
// so all three are handled here rather than assumed.
//
// The emulated device wants exactly one NV12 frame: luma at full size, then
// interleaved Cb/Cr at half size in both directions. This is a pass over the
// picture -- a few hundred microseconds for a preview frame on a phone -- done on
// the camera's own thread, once per frame it hands over.
#ifndef UN1BOX_CAMERA_PLANES_H
#define UN1BOX_CAMERA_PLANES_H

#include <cstddef>
#include <cstdint>
#include <cstring>
#if defined(__aarch64__)
#include <arm_neon.h>
#elif defined(__SSE2__) || defined(__x86_64__)
#include <emmintrin.h>
#endif

namespace camera_planes {

using std::ptrdiff_t;

struct Plane {
    const uint8_t *data;
    int row_stride;     // bytes from the start of one row to the next
    int pixel_stride;   // bytes from one sample to the next
};

// Copies the luma plane, honouring both strides.
inline void luma(const Plane &plane, int width, int height, uint8_t *out, int out_stride) {
    if (plane.pixel_stride == 1 && plane.row_stride == width && out_stride == width) {
        std::memcpy(out, plane.data, size_t(width) * size_t(height));
        return;
    }
    for (int y = 0; y < height; ++y) {
        const uint8_t *row = plane.data + ptrdiff_t(y) * plane.row_stride;
        uint8_t *target = out + ptrdiff_t(y) * out_stride;
        if (plane.pixel_stride == 1) {
            std::memcpy(target, row, size_t(width));
        } else {
            for (int x = 0; x < width; ++x) target[x] = row[ptrdiff_t(x) * plane.pixel_stride];
        }
    }
}

// Copies the two chroma planes into one interleaved Cb/Cr half-size plane. A
// planar frame (pixel stride 1) has one chroma sample per two pixels; a
// semi-planar one (pixel stride 2) has the sample at every other byte of its own
// plane, and the two planes may even be views of one buffer, which is why each
// sample is read by its own stride rather than by arithmetic on the other plane.
inline void chroma(const Plane &cb, const Plane &cr, int width, int height, uint8_t *out,
                   int out_stride) {
    const int rows = height / 2, columns = width / 2;
    const bool both_planar = cb.pixel_stride == 1 && cr.pixel_stride == 1;
    const bool both_semi = cb.pixel_stride == 2 && cr.pixel_stride == 2;
    for (int y = 0; y < rows; ++y) {
        const uint8_t *cb_row = cb.data + ptrdiff_t(y) * cb.row_stride;
        const uint8_t *cr_row = cr.data + ptrdiff_t(y) * cr.row_stride;
        uint8_t *target = out + ptrdiff_t(y) * out_stride;
        int x = 0;
        if (both_semi &&
            reinterpret_cast<std::uintptr_t>(cr_row) == reinterpret_cast<std::uintptr_t>(cb_row) + 1) {
            // Native NV12 layout: copy up to the last Cb byte from cb_row and the
            // trailing Cr byte from cr_row so neither view is read past its bound.
            const size_t prefix = size_t(columns) * 2 - 1;
            std::memcpy(target, cb_row, prefix);
            target[prefix] = cr_row[ptrdiff_t(columns - 1) * 2];
            continue;
        }
#if defined(__aarch64__)
        if (both_planar) {
            for (; x + 16 <= columns; x += 16) {
                uint8x16x2_t pair{vld1q_u8(cb_row + x), vld1q_u8(cr_row + x)};
                vst2q_u8(target + ptrdiff_t(x) * 2, pair);
            }
            for (; x + 8 <= columns; x += 8) {
                uint8x8x2_t pair{vld1_u8(cb_row + x), vld1_u8(cr_row + x)};
                vst2_u8(target + ptrdiff_t(x) * 2, pair);
            }
        } else if (both_semi) {
            // Stop one sample short of the end so 32-byte deinterleaved loads never
            // touch the padding byte after the final chroma sample of either plane.
            for (; x + 16 < columns; x += 16) {
                const uint8x16x2_t u = vld2q_u8(cb_row + ptrdiff_t(x) * 2);
                const uint8x16x2_t v = vld2q_u8(cr_row + ptrdiff_t(x) * 2);
                uint8x16x2_t pair{u.val[0], v.val[0]};
                vst2q_u8(target + ptrdiff_t(x) * 2, pair);
            }
        }
#elif defined(__SSE2__) || defined(__x86_64__)
        if (both_planar) {
            for (; x + 16 <= columns; x += 16) {
                const __m128i u = _mm_loadu_si128(reinterpret_cast<const __m128i *>(cb_row + x));
                const __m128i v = _mm_loadu_si128(reinterpret_cast<const __m128i *>(cr_row + x));
                _mm_storeu_si128(reinterpret_cast<__m128i *>(target + ptrdiff_t(x) * 2),
                                 _mm_unpacklo_epi8(u, v));
                _mm_storeu_si128(reinterpret_cast<__m128i *>(target + ptrdiff_t(x) * 2 + 16),
                                 _mm_unpackhi_epi8(u, v));
            }
        }
#endif
        for (; x < columns; ++x) {
            target[ptrdiff_t(x) * 2] = cb_row[ptrdiff_t(x) * cb.pixel_stride];
            target[ptrdiff_t(x) * 2 + 1] = cr_row[ptrdiff_t(x) * cr.pixel_stride];
        }
    }
}

// The whole frame. `out` must hold width * height * 3 / 2 bytes.
inline bool to_nv12(const Plane &y, const Plane &cb, const Plane &cr, int width, int height,
                    uint8_t *out) {
    if (!y.data || !cb.data || !cr.data || !out) return false;
    if (width < 2 || height < 2 || (width & 1) || (height & 1)) return false;
    if (y.row_stride < width || cb.row_stride < width / 2 || cr.row_stride < width / 2) return false;
    if (y.pixel_stride < 1 || cb.pixel_stride < 1 || cr.pixel_stride < 1) return false;
    luma(y, width, height, out, width);
    chroma(cb, cr, width, height, out + ptrdiff_t(width) * height, width);
    return true;
}

}  // namespace camera_planes

#endif
