/* The one store hw/arm/host-mic.c uses from qemu/bswap.h. Little-endian host
 * assumed, which every target the engine builds for is. */
#ifndef MIC_TEST_BSWAP_H
#define MIC_TEST_BSWAP_H
#include <stdint.h>
#include <string.h>
static inline void stw_le_p(void *p, uint16_t v) { memcpy(p, &v, sizeof v); }
static inline void stl_le_p(void *p, uint32_t v) { memcpy(p, &v, sizeof v); }
static inline uint32_t ldl_le_p(const void *p)
{
    uint32_t v;
    memcpy(&v, p, sizeof v);
    return v;
}
static inline uint16_t lduw_le_p(const void *p)
{
    uint16_t v;
    memcpy(&v, p, sizeof v);
    return v;
}
#endif

/* The two qemu bits the sliced channel/packing helpers use, in their real spelling. */
#define MAX(a, b) ((a) > (b) ? (a) : (b))
static inline int ctpop32(uint32_t value)
{
    return __builtin_popcount(value);
}
