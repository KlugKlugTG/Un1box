#pragma once
#include <cstdint>
#include <cstring>
#include <string>
#if defined(__aarch64__)
#include <arm_neon.h>
#if defined(__linux__)
#include <asm/hwcap.h>
#include <sys/auxv.h>
#endif
#elif defined(__x86_64__)
#include <cpuid.h>
#include <immintrin.h>
#endif
namespace lt {
namespace sha256_detail {
alignas(16) static const uint32_t k[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};
static inline uint32_t r(uint32_t n, unsigned b) { return (n >> b) | (n << (32 - b)); }
static inline void portable(uint32_t h[8], const uint8_t *p, size_t blocks) {
    for (; blocks--; p += 64) {
        uint32_t w[64];
        for (int i = 0; i < 16; i++)
            w[i] = (uint32_t(p[i * 4]) << 24) | (uint32_t(p[i * 4 + 1]) << 16) | (uint32_t(p[i * 4 + 2]) << 8) |
                   p[i * 4 + 3];
        for (int i = 16; i < 64; i++)
            w[i] = w[i - 16] + (r(w[i - 15], 7) ^ r(w[i - 15], 18) ^ (w[i - 15] >> 3)) + w[i - 7] +
                   (r(w[i - 2], 17) ^ r(w[i - 2], 19) ^ (w[i - 2] >> 10));
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], v = h[7];
        for (int i = 0; i < 64; i++) {
            uint32_t t = v + (r(e, 6) ^ r(e, 11) ^ r(e, 25)) + ((e & f) ^ (~e & g)) + k[i] + w[i];
            v = g;
            g = f;
            f = e;
            e = d + t;
            d = c;
            c = b;
            b = a;
            a = t + (r(b, 2) ^ r(b, 13) ^ r(b, 22)) + ((b & c) ^ (b & d) ^ (c & d));
        }
        uint32_t q[8] = {a, b, c, d, e, f, g, v};
        for (int i = 0; i < 8; i++)
            h[i] += q[i];
    }
}
// The CPU's own SHA-256 instructions (ARMv8 SHA2, x86 SHA-NI) are several times faster than the
// portable rounds; a prepared base is gigabytes, so admission and import time is mostly this.
#if defined(__aarch64__)
// The '+', which GCC insists on for an architectural extension and clang accepts as well: the
// unprefixed name compiles only under clang, and the self-checks that slice Portable/src/*.cpp
// build this header with the host's compiler.
__attribute__((target("+sha2"))) static inline void accelerated(uint32_t h[8], const uint8_t *p, size_t blocks) {
    uint32x4_t abcd = vld1q_u32(h), efgh = vld1q_u32(h + 4);
    for (; blocks--; p += 64) {
        const uint32x4_t abcd_saved = abcd, efgh_saved = efgh;
        uint32x4_t m[4];
        for (int i = 0; i < 4; i++)
            m[i] = vreinterpretq_u32_u8(vrev32q_u8(vld1q_u8(p + 16 * i)));
        for (int i = 0; i < 16; i++) {
            const uint32x4_t wk = vaddq_u32(m[i & 3], vld1q_u32(k + 4 * i));
            const uint32x4_t before = abcd;
            abcd = vsha256hq_u32(abcd, efgh, wk);
            efgh = vsha256h2q_u32(efgh, before, wk);
            if (i < 12)
                m[i & 3] = vsha256su1q_u32(vsha256su0q_u32(m[i & 3], m[(i + 1) & 3]), m[(i + 2) & 3], m[(i + 3) & 3]);
        }
        abcd = vaddq_u32(abcd, abcd_saved);
        efgh = vaddq_u32(efgh, efgh_saved);
    }
    vst1q_u32(h, abcd);
    vst1q_u32(h + 4, efgh);
}
static inline bool has_instructions() {
#if defined(__APPLE__)
    return true;
#elif defined(__linux__)
    static const bool present = (getauxval(AT_HWCAP) & HWCAP_SHA2) != 0;
    return present;
#else
    return false;
#endif
}
#elif defined(__x86_64__)
__attribute__((target("sha,sse4.1"))) static inline void accelerated(uint32_t h[8], const uint8_t *p, size_t blocks) {
    const __m128i mask = _mm_set_epi64x(0x0c0d0e0f08090a0bULL, 0x0405060700010203ULL);
    __m128i t = _mm_shuffle_epi32(_mm_loadu_si128(reinterpret_cast<const __m128i *>(h)), 0xB1);
    __m128i s1 = _mm_shuffle_epi32(_mm_loadu_si128(reinterpret_cast<const __m128i *>(h + 4)), 0x1B);
    __m128i s0 = _mm_alignr_epi8(t, s1, 8); // ABEF
    s1 = _mm_blend_epi16(s1, t, 0xF0);      // CDGH
    for (; blocks--; p += 64) {
        const __m128i s0_saved = s0, s1_saved = s1;
        __m128i m[4];
        for (int i = 0; i < 4; i++)
            m[i] = _mm_shuffle_epi8(_mm_loadu_si128(reinterpret_cast<const __m128i *>(p + 16 * i)), mask);
        for (int i = 0; i < 16; i++) {
            __m128i wk = _mm_add_epi32(m[i & 3], _mm_load_si128(reinterpret_cast<const __m128i *>(k + 4 * i)));
            s1 = _mm_sha256rnds2_epu32(s1, s0, wk);
            wk = _mm_shuffle_epi32(wk, 0x0E);
            s0 = _mm_sha256rnds2_epu32(s0, s1, wk);
            if (i < 12)
                m[i & 3] = _mm_sha256msg2_epu32(
                    _mm_add_epi32(_mm_sha256msg1_epu32(m[i & 3], m[(i + 1) & 3]),
                                  _mm_alignr_epi8(m[(i + 3) & 3], m[(i + 2) & 3], 4)),
                    m[(i + 3) & 3]);
        }
        s0 = _mm_add_epi32(s0, s0_saved);
        s1 = _mm_add_epi32(s1, s1_saved);
    }
    t = _mm_shuffle_epi32(s0, 0x1B);  // FEBA
    s1 = _mm_shuffle_epi32(s1, 0xB1); // DCHG
    _mm_storeu_si128(reinterpret_cast<__m128i *>(h), _mm_blend_epi16(t, s1, 0xF0));
    _mm_storeu_si128(reinterpret_cast<__m128i *>(h + 4), _mm_alignr_epi8(s1, t, 8));
}
static inline bool has_instructions() {
    static const bool present = [] {
        unsigned a, b, c, d;
        if (!__get_cpuid(1, &a, &b, &c, &d) || !(c & (1u << 19)) || !(c & (1u << 9)))
            return false; // SSE4.1, SSSE3
        return __get_cpuid_count(7, 0, &a, &b, &c, &d) && (b & (1u << 29));
    }();
    return present;
}
#else
static inline void accelerated(uint32_t h[8], const uint8_t *p, size_t blocks) { portable(h, p, blocks); }
static inline bool has_instructions() { return false; }
#endif
static inline void compress(uint32_t h[8], const uint8_t *p, size_t blocks) {
    if (!blocks)
        return;
    if (has_instructions())
        accelerated(h, p, blocks);
    else
        portable(h, p, blocks);
}
} // namespace sha256_detail
// FIPS 180-4 SHA-256; streaming so NAND/import never needs a whole image in RAM.
class Sha256 {
    uint32_t h[8] = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
    uint8_t block[64]{};
    uint64_t length = 0;
    size_t used = 0;

  public:
    void update(const void *p, size_t n) {
        const auto *b = static_cast<const uint8_t *>(p);
        length += n;
        if (used) {
            const size_t take = n < 64 - used ? n : 64 - used;
            memcpy(block + used, b, take);
            used += take;
            b += take;
            n -= take;
            if (used < 64)
                return;
            sha256_detail::compress(h, block, 1);
            used = 0;
        }
        sha256_detail::compress(h, b, n / 64);
        b += n / 64 * 64;
        n %= 64;
        memcpy(block, b, n);
        used = n;
    }
    std::string finish() {
        uint64_t bits = length * 8;
        uint8_t pad[128]{};
        pad[0] = 0x80;
        size_t n = used < 56 ? 56 - used : 120 - used;
        update(pad, n);
        uint8_t tail[8];
        for (int i = 0; i < 8; i++)
            tail[i] = uint8_t(bits >> (56 - i * 8));
        update(tail, 8);
        static const char hex[] = "0123456789abcdef";
        std::string out;
        for (uint32_t x : h)
            for (int shift = 28; shift >= 0; shift -= 4)
                out += hex[(x >> shift) & 15];
        return out;
    }
};
} // namespace lt
