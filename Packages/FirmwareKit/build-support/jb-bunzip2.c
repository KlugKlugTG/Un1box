// A bunzip2 for the Jailbreak tab, self-contained: the repositories these systems use serve their
// package indexes as .bz2 (BigBoss's 9 973 packages are only there), and neither Android nor the
// Java library can read bzip2 at all.
//
// It is a decompressor, nothing else: bzip2 is a bit stream of Huffman-coded, move-to-front coded,
// run-length coded Burrows-Wheeler blocks, and this walks one to the end. Randomised blocks (the
// format's deprecated -R mode, which no writer of these indexes uses) are refused rather than
// guessed at, and the block CRCs are read and ignored -- the caller parses the text, and a package
// list that is corrupt shows up there.
//
// Build (the workflow does this): aarch64-linux-android29-clang -O2, or the host's cc for the tests.
//
//   jb-bunzip2 <in.bz2> <out>
#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define BLOCK_MAX (900000 + 8)     /* bzip2's largest block, level 9 */
#define MAX_GROUPS 6
#define MAX_ALPHA 258              /* nInUse + 2 at most: 256 symbols, RUNA/RUNB */
#define MAX_CODE_LEN 20
#define MIN_CODE_LEN 1

static const char *program = "jb-bunzip2";

static void fail(const char *what) {
    fprintf(stderr, "%s: %s\n", program, what);
}

/* --- the bit stream: bzip2 packs bits from the most significant bit down ------------------------- */

struct bits {
    FILE *file;
    uint32_t buffer;
    int live;
    bool ended;
};

static int bit(struct bits *b, unsigned *out) {
    if (b->live == 0) {
        int c = fgetc(b->file);
        if (c == EOF) {
            b->ended = true;
            return -1;
        }
        b->buffer = (uint32_t) c;
        b->live = 8;
    }
    b->live--;
    *out = (b->buffer >> b->live) & 1u;
    return 0;
}

static int take(struct bits *b, int count, uint32_t *out) {
    uint32_t value = 0;
    for (int i = 0; i < count; i++) {
        unsigned one;
        if (bit(b, &one) != 0) return -1;
        value = (value << 1) | one;
    }
    *out = value;
    return 0;
}

/* --- one Huffman table, decoded the way bzip2's own decoder does --------------------------------- */

struct table {
    int limit[MAX_CODE_LEN + 2];
    int base[MAX_CODE_LEN + 2];
    int perm[MAX_ALPHA];
    int length[MAX_ALPHA];
};

static void build_table(struct table *t, int symbols) {
    int at = 0;
    for (int len = MIN_CODE_LEN; len <= MAX_CODE_LEN; len++) {
        for (int symbol = 0; symbol < symbols; symbol++) {
            if (t->length[symbol] == len) t->perm[at++] = symbol;
        }
    }
    memset(t->base, 0, sizeof t->base);
    for (int symbol = 0; symbol < symbols; symbol++) t->base[t->length[symbol] + 1]++;
    for (int i = 1; i <= MAX_CODE_LEN + 1; i++) t->base[i] += t->base[i - 1];
    int vector = 0;
    for (int len = MIN_CODE_LEN; len <= MAX_CODE_LEN; len++) {
        vector += t->base[len + 1] - t->base[len];
        t->limit[len] = vector - 1;
        vector <<= 1;
    }
    for (int len = MIN_CODE_LEN + 1; len <= MAX_CODE_LEN; len++)
        t->base[len] = ((t->limit[len - 1] + 1) << 1) - t->base[len];
}

static int symbol_of(struct bits *b, const struct table *t, int *out);

/** The selectors in use: each one names the Huffman table for the next fifty symbols. */
struct selection {
    const unsigned char *selector;
    uint32_t count;
    uint32_t at;
    int current;
    int left;
};

static int next_symbol(struct bits *b, const struct table *tables, int groups,
                       struct selection *s, int *out) {
    if (s->left == 0) {
        if (s->at >= s->count) {
            fail("the data runs past its selectors");
            return -1;
        }
        s->current = s->selector[s->at++];
        if (s->current < 0 || s->current >= groups) {
            fail("a selector names no table");
            return -1;
        }
        s->left = 50;                      /* bzip2's group size, the format's own number */
    }
    s->left--;
    return symbol_of(b, &tables[s->current], out);
}

static int symbol_of(struct bits *b, const struct table *t, int *out) {
    uint32_t vector;
    if (take(b, MIN_CODE_LEN, &vector) != 0) return -1;
    int len = MIN_CODE_LEN;
    while (len <= MAX_CODE_LEN && (int) vector > t->limit[len]) {
        unsigned one;
        if (bit(b, &one) != 0) return -1;
        vector = (vector << 1) | one;
        len++;
    }
    if (len > MAX_CODE_LEN) return -1;
    int index = (int) vector - t->base[len];
    if (index < 0 || index >= MAX_ALPHA) return -1;
    *out = t->perm[index];
    return 0;
}

/* --- one block ----------------------------------------------------------------------------------- */

static int decode_block(struct bits *b, FILE *out) {
    uint32_t ignored;
    if (take(b, 32, &ignored) != 0) return -1;                 /* the block's CRC */
    unsigned randomised;
    if (bit(b, &randomised) != 0) return -1;
    if (randomised) {
        fail("this archive uses randomised blocks, which are not supported");
        return -1;
    }
    uint32_t orig_ptr;
    if (take(b, 24, &orig_ptr) != 0) return -1;

    /* Which bytes occur: sixteen bits say which groups of sixteen are in use, and each group that
     * is then says, in another sixteen bits, which of its bytes are (bzip2's two-level map). */
    bool used[256] = {false};
    int symbols = 0;
    bool group_used[16];
    for (int group = 0; group < 16; group++) {
        unsigned one;
        if (bit(b, &one) != 0) return -1;
        group_used[group] = one != 0;
    }
    for (int group = 0; group < 16; group++) {
        if (!group_used[group]) continue;
        for (int bit_ = 0; bit_ < 16; bit_++) {
            unsigned one;
            if (bit(b, &one) != 0) return -1;
            if (one) {
                used[group * 16 + bit_] = true;
                symbols++;
            }
        }
    }
    if (symbols == 0) {
        fail("a block uses no bytes at all");
        return -1;
    }

    uint32_t groups_raw;
    if (take(b, 3, &groups_raw) != 0) return -1;
    int groups = (int) groups_raw;
    if (groups < 2 || groups > MAX_GROUPS) {
        fail("a block names an impossible number of Huffman groups");
        return -1;
    }
    uint32_t selectors;
    if (take(b, 15, &selectors) != 0) return -1;
    if (selectors == 0 || selectors > 18002) {
        fail("a block names an impossible number of selectors");
        return -1;
    }

    unsigned char selector_mtf[18002];
    for (uint32_t i = 0; i < selectors; i++) {
        int moves = 0;
        unsigned one;
        do {
            if (moves >= groups) {
                fail("a selector runs past its groups");
                return -1;
            }
            if (bit(b, &one) != 0) return -1;
            if (one) moves++;
        } while (one);
        selector_mtf[i] = (unsigned char) moves;
    }
    unsigned char pos[MAX_GROUPS];
    for (int i = 0; i < groups; i++) pos[i] = (unsigned char) i;
    unsigned char selector[18002];
    for (uint32_t i = 0; i < selectors; i++) {
        int move = selector_mtf[i];
        unsigned char held = pos[move];
        while (move > 0) {
            pos[move] = pos[move - 1];
            move--;
        }
        pos[0] = held;
        selector[i] = held;
    }

    /* The Huffman tables themselves: lengths, delta-coded from 5-bit starting points. */
    struct table tables[MAX_GROUPS];
    memset(tables, 0, sizeof tables);
    int alphabet = symbols + 2;                                 /* RUNA, RUNB, the bytes, EOB */
    for (int g = 0; g < groups; g++) {
        uint32_t current;
        if (take(b, 5, &current) != 0) return -1;
        int value = (int) current;
        for (int i = 0; i < alphabet; i++) {
            unsigned one;
            while (true) {
                if (bit(b, &one) != 0) return -1;
                if (!one) break;
                if (bit(b, &one) != 0) return -1;
                if (one) value--;
                else value++;
            }
            if (value < MIN_CODE_LEN || value > MAX_CODE_LEN) {
                fail("a Huffman code length is out of range");
                return -1;
            }
            tables[g].length[i] = value;
        }
        build_table(&tables[g], alphabet);
    }

    /* The data: move-to-front coded symbols, with runs of RUNA/RUNB as a repeat count, and the
     * move-to-front list reset every fifty symbols. */
    unsigned char mtf[256];
    int at = 0;
    for (int byte_ = 0; byte_ < 256; byte_++) {
        if (used[byte_]) mtf[at++] = (unsigned char) byte_;
    }
    unsigned char *block = malloc(BLOCK_MAX);
    unsigned char *walk = malloc(BLOCK_MAX);
    uint32_t *tt = malloc(sizeof(uint32_t) * BLOCK_MAX);
    if (block == NULL || walk == NULL || tt == NULL) {
        free(block);
        free(walk);
        free(tt);
        fail("out of memory");
        return -1;
    }
    uint32_t nblock = 0;
    struct selection choosing = { .selector = selector, .count = selectors, .at = 0, .current = -1, .left = 0 };
    int pending = -1;                 /* a symbol read while a run was being counted */
    bool done = false;
    while (!done) {
        if (nblock >= BLOCK_MAX) {
            fail("a block is longer than bzip2 allows");
            goto bad;
        }
        int symbol;
        if (pending >= 0) {
            symbol = pending;
            pending = -1;
        } else if (next_symbol(b, tables, groups, &choosing, &symbol) != 0) {
            fail("the bit stream ended inside a block");
            goto bad;
        }

        if (symbol == 0 || symbol == 1) {                          /* RUNA / RUNB: a repeat count */
            int64_t run = -1;
            int64_t weight = 1;
            int current = symbol;
            while (current == 0 || current == 1) {
                run += (current == 0 ? 1 : 2) * weight;
                weight <<= 1;
                if (next_symbol(b, tables, groups, &choosing, &current) != 0) {
                    fail("the bit stream ended inside a run");
                    goto bad;
                }
            }
            pending = current;                                     /* it belongs to the next turn */
            run++;
            unsigned char repeated = mtf[0];
            for (int64_t i = 0; i < run; i++) {
                if (nblock >= BLOCK_MAX) {
                    fail("a block is longer than bzip2 allows");
                    goto bad;
                }
                block[nblock++] = repeated;
            }
            continue;
        }

        if (symbol == symbols + 1) {                               /* EOB */
            done = true;
            break;
        }
        int index = symbol - 1;                                    /* 2 -> the front of the list */
        if (index < 0 || index >= symbols) {
            fail("a symbol is outside the block's alphabet");
            goto bad;
        }
        unsigned char byte_ = mtf[index];
        memmove(mtf + 1, mtf, (size_t) index);
        mtf[0] = byte_;
        block[nblock++] = byte_;
    }

    /* The inverse Burrows-Wheeler: counts, a cumulative start per byte, then the transposition
     * vector, and the block is the walk that starts at the block's own origin. */
    if (nblock == 0) {
        free(block);
        free(walk);
        free(tt);
        return 0;
    }
    if (orig_ptr >= nblock) {
        fail("a block's origin is outside it");
        goto bad;
    }
    {
        uint32_t counts[256];
        memset(counts, 0, sizeof counts);
        for (uint32_t i = 0; i < nblock; i++) counts[block[i]]++;
        uint32_t total = 0;
        for (int i = 0; i < 256; i++) {
            uint32_t here = counts[i];
            counts[i] = total;
            total += here;
        }
        for (uint32_t i = 0; i < nblock; i++) tt[counts[block[i]]++] = i;
        uint32_t position = tt[orig_ptr];
        for (uint32_t i = 0; i < nblock; i++) {
            walk[i] = block[position];
            position = tt[position];
        }

        /* What the block holds is the encoder's run-length pass, not the file: a byte, and where the
         * original had four or more of it, a count of the rest. bzip2's own decoder expands it in the
         * same shape -- one byte, then three more one at a time, each proving the run continues, then
         * the count -- so this walks the BWT output exactly that way. */
        uint32_t at = 0;
        int64_t left = 0;
        unsigned char current = 0;
        while (at < nblock || left > 0) {
            if (left > 0) {
                if (fputc(current, out) == EOF) {
                    fail("cannot write the output");
                    goto bad;
                }
                left--;
                continue;
            }
            current = walk[at++];
            left = 1;
            if (at < nblock) {
                unsigned char next = walk[at++];
                if (next != current) {
                    at--;                      /* a byte of its own: the next run starts with it */
                } else {
                    left = 2;
                    if (at < nblock) {
                        next = walk[at++];
                        if (next != current) {
                            at--;
                        } else {
                            left = 3;
                            if (at < nblock) {
                                next = walk[at++];
                                if (next != current) {
                                    at--;
                                } else if (at < nblock) {
                                    left = 4 + (int64_t) walk[at++];
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    free(block);
    free(walk);
    free(tt);
    return 0;
bad:
    free(block);
    free(walk);
    free(tt);
    return -1;
}

int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "usage: %s <in.bz2> <out>\n", program);
        return 1;
    }
    FILE *in = fopen(argv[1], "rb");
    if (in == NULL) {
        fprintf(stderr, "%s: %s: %s\n", program, argv[1], strerror(errno));
        return 1;
    }
    FILE *out = fopen(argv[2], "wb");
    if (out == NULL) {
        fprintf(stderr, "%s: %s: %s\n", program, argv[2], strerror(errno));
        fclose(in);
        return 1;
    }

    struct bits reader = { .file = in, .buffer = 0, .live = 0, .ended = false };
    unsigned char magic[4];
    if (fread(magic, 1, 4, in) != 4 || magic[0] != 'B' || magic[1] != 'Z' || magic[2] != 'h' ||
        magic[3] < '1' || magic[3] > '9') {
        fail("not a bzip2 file");
        fclose(in);
        fclose(out);
        remove(argv[2]);
        return 1;
    }

    int status = 0;
    while (!reader.ended) {
        /* Each block starts with a 48-bit value -- the block's magic, or the stream's end -- and the
         * bit stream does not pad before it: bzip2 reads it with the same bit reader as everything
         * else (bsGetUChar), so a byte of padding is not there to drop. */
        uint32_t high, low;
        if (take(&reader, 24, &high) != 0 || take(&reader, 24, &low) != 0) break;
        uint64_t value = ((uint64_t) high << 24) | low;
        if (value == 0x314159265359ull) {
            if (decode_block(&reader, out) != 0) {
                status = 1;
                break;
            }
        } else if (value == 0x177245385090ull) {
            break;
        } else {
            fail("the stream lost its block boundary");
            status = 1;
            break;
        }
    }

    fclose(in);
    if (fclose(out) != 0) {
        fail("cannot finish the output");
        status = 1;
    }
    if (status != 0) remove(argv[2]);
    return status;
}
