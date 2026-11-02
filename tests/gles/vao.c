/* Vertex array objects: the state a bind swaps, and what must not travel with it.
 *
 * The guest's OES_vertex_array_object calls reach the bridge as ids 837-840:
 * glBindVertexArrayOES, glDeleteVertexArraysOES, glGenVertexArraysOES and
 * glIsVertexArrayOES. Until ci/engine-patches/0014 they fell through to the
 * dispatch's default and were refused, which costs the frame at the generate:
 * the app is handed 0, binds 0 -- the default state -- and every pointer it set
 * through its VAO belongs to nothing, so the draw runs with the arrays off
 * ("[gles] UNHANDLED ... returning 0; the guest will render wrong").
 *
 * The engine keeps a VAO as a name for the array state its context already
 * keeps, with no host VAO object: the host may be an ES 1.1 context, where the
 * ES3 entry point is not valid, and both draw paths re-apply the arrays
 * themselves, so a host VAO would only duplicate a cache that is maintained
 * already. These cases drive that state machine, sliced out of the engine rather
 * than copied:
 *
 *   names    generate hands out names and writes them to the guest's list, and a
 *            name becomes a VAO at its first bind, which is what is-then reports
 *   bind     stores the current state into the record it came from and loads the
 *            named one's, element array and buffer references included
 *   delete   deleting the bound name falls back to the default, as GL specifies
 *   context  the host mirror, the fetch scratch and GL_ARRAY_BUFFER do not
 *            travel: they belong to the context, not to the VAO
 *
 * Everything around the slice is a stub, because this runs with no glib and no
 * QEMU headers: GHashTable, the buffer refcount and the guest-memory access are
 * reduced to what these functions use. Buffers are never really freed, so a
 * refcount can still be read after it reaches zero. The record's field list is
 * the one thing this file restates (a struct member needs its type complete, and
 * the sliced types arrive after this setup); tests/gles/wiring.py compares it
 * against the engine's, so the two cannot drift apart quietly.
 *
 * SLICE engine hw/arm/gles-host.c define GLES_MAX_(NAMES|TEXUNITS|ATTRIBS)
 * SLICE engine hw/arm/gles-host.c define GLES_VAO_(ARRAYS|BINDING)
 * SLICE engine hw/arm/gles-host.c fn gles_vao_arrays gles_vao_release gles_vao_destroy gles_vao_record gles_vao_store gles_vao_load gles_vao_gen gles_vao_delete gles_vao_bind gles_vao_is
 * CFLAGS -Wall -Wextra -Werror -Wno-unused-function -Wno-unused-parameter
 */
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* The GL and QEMU names these functions are written against. */
typedef uint32_t GLenum, GLuint;
typedef int32_t GLint;
typedef uint64_t vaddr;
typedef struct CPUState CPUState;

/* --- glib, reduced to the hash table and the allocation macros --- */
typedef void *gpointer;
typedef void (*GDestroyNotify)(void *);
#define g_direct_hash  NULL
#define g_direct_equal NULL
#define GUINT_TO_POINTER(u) ((void *)(uintptr_t)(u))

/* Open addressing on the uint32 keys the records are filed under. */
typedef struct { void *key; void *val; bool used; } HSlot;
typedef struct GHashTable { HSlot slot[64]; GDestroyNotify destroy; } GHashTable;

static unsigned hash_of(uintptr_t key)
{
    key *= 0x9E3779B97F4A7C15ull;
    return (unsigned)(key >> 58) & 63;
}

static GHashTable *g_hash_table_new_full(void *hash, void *equal, GDestroyNotify key_destroy,
                                         GDestroyNotify value_destroy)
{
    (void)hash; (void)equal; (void)key_destroy;
    GHashTable *table = calloc(1, sizeof(*table));
    table->destroy = value_destroy;
    return table;
}

static void *g_hash_table_lookup(GHashTable *table, void *key)
{
    unsigned i = hash_of((uintptr_t)key);
    for (unsigned n = 0; n < 64; n++, i = (i + 1) & 63) {
        if (!table->slot[i].used) {
            return NULL;
        }
        if (table->slot[i].key == key) {
            return table->slot[i].val;
        }
    }
    return NULL;
}

static void g_hash_table_insert(GHashTable *table, void *key, void *val)
{
    unsigned i = hash_of((uintptr_t)key);
    while (table->slot[i].used && table->slot[i].key != key) {
        i = (i + 1) & 63;
    }
    if (table->slot[i].used && table->destroy && table->slot[i].val != val) {
        table->destroy(table->slot[i].val);
    }
    table->slot[i].key = key;
    table->slot[i].val = val;
    table->slot[i].used = true;
}

static bool g_hash_table_remove(GHashTable *table, void *key)
{
    unsigned i = hash_of((uintptr_t)key);
    for (unsigned n = 0; n < 64; n++, i = (i + 1) & 63) {
        if (!table->slot[i].used) {
            return false;
        }
        if (table->slot[i].key == key) {
            if (table->destroy) {
                table->destroy(table->slot[i].val);
            }
            table->slot[i].used = false;
            return true;
        }
    }
    return false;
}

static void g_hash_table_destroy(GHashTable *table)
{
    for (unsigned i = 0; i < 64; i++) {
        if (table->slot[i].used && table->destroy) {
            table->destroy(table->slot[i].val);
        }
    }
    free(table);
}

#define g_new0(T, n) ((T *)calloc((n), sizeof(T)))
#define g_free(p) free(p)
#define g_autofree __attribute__((cleanup(autofree)))
static void autofree(void *p) { free(*(void **)p); }

/* --- the engine's refusals and its access to guest memory --- */
static int refusals;

static bool gles_refuse(const char *fmt, ...)
{
    (void)fmt;
    refusals++;
    return false;
}

static uint8_t guest_mem[4096];
static int guest_reads, guest_writes;

int gles_guest_rw(CPUState *cpu, vaddr va, void *buf, size_t len, bool write)
{
    (void)cpu;
    if (write) {
        guest_writes++;
    } else {
        guest_reads++;
    }
    if (va + len > sizeof guest_mem) {
        return -1;
    }
    memcpy(write ? guest_mem + va : buf, write ? buf : guest_mem + va, len);
    return 0;
}

/* --- the buffer, and the array a VAO owns a slice of ---
 * GLESArray reduced to what a record carries plus the fields that must stay
 * behind: the fetch scratch, the client-state enum and the host mirror, which
 * the engine's own comment calls this array's host context's. */
typedef struct GLESBuffer { uint32_t name; unsigned refs; unsigned freed; } GLESBuffer;

/* The engine frees a buffer at its last reference. Here the buffers come from a
 * pool and only get marked, so a refcount can still be read once it reaches zero
 * -- which is what these cases assert on -- and a --sanitize run has nothing to
 * report that the test allocated on purpose. */
static void gles_buffer_destroy(GLESBuffer *b)
{
    if (b && --b->refs == 0) {
        b->freed = 1;
    }
}

static void gles_buffer_bind(GLESBuffer **slot, GLESBuffer *b)
{
    if (b) {
        b->refs++;
    }
    gles_buffer_destroy(*slot);
    *slot = b;
}

typedef struct {
    uint32_t enabled, size, type, stride, ptr;
    GLESBuffer *vbo;
    uint8_t *buf; size_t buf_size;          /* scratch: stays with the context */
    float *fbuf; size_t fbuf_count;
    GLenum client_state;
    bool host_enabled, host_pointer_valid;  /* the host mirror: stays too */
    uint32_t host_size, host_stride;
    GLenum host_type;
    const void *host_pointer;
} GLESArray;

/* The record, as hw/arm/gles-host.c declares it (wiring.py keeps this honest). */
typedef struct {
    uint32_t enabled, size, type, stride, ptr;
    bool normalized;
    GLESBuffer *vbo;
} GlesArrayState;

typedef struct {
    GlesArrayState array[GLES_VAO_ARRAYS];
    GLESBuffer *element_buffer;
} GlesVertexArray;

/* The context, reduced to the fields a VAO touches. */
typedef struct {
    GLESArray vertex, color, normal, pointsize;
    GLESArray texcoord[GLES_MAX_TEXUNITS];
    GLESArray attr[GLES_MAX_ATTRIBS];
    bool attr_normalized[GLES_MAX_ATTRIBS];
    GLESBuffer *array_buffer, *element_buffer;
    GHashTable *vaos;
    uint32_t next_vao_name;
    GLuint vao;
    GlesVertexArray vao_default;
} GLESHost;

static GLESHost gh_state;
#define gh (gh_state)

/* The slice arrives after this setup, so the ten functions are declared here. */
static unsigned gles_vao_arrays(GLESArray **array, bool **normalized);
static void gles_vao_release(GlesVertexArray *v);
static void gles_vao_destroy(gpointer data);
static GlesVertexArray *gles_vao_record(uint32_t name, bool create);
static void gles_vao_store(GlesVertexArray *v);
static void gles_vao_load(const GlesVertexArray *v);
static int64_t gles_vao_gen(CPUState *cpu, uint32_t n, uint32_t gptr);
static int64_t gles_vao_delete(CPUState *cpu, uint32_t n, uint32_t gptr);
static int64_t gles_vao_bind(uint32_t name);
static int64_t gles_vao_is(uint32_t name);

/* ------------------------------------------------------------- cases ---- */
static int failures, checks;

#define CHECK(cond, ...) do { \
    checks++; \
    if (!(cond)) { failures++; printf("FAIL %s:%d ", __func__, __LINE__); printf(__VA_ARGS__); printf("\n"); } \
} while (0)

/* A buffer starts with its caller's own reference, so "who holds it" is the
 * caller, the context, and whichever records captured it. */
static void expect_refs(const GLESBuffer *b, unsigned want, const char *who)
{
    checks++;
    if (b->refs != want) {
        failures++;
        printf("FAIL %s: %s holds %u references, wanted %u\n", __func__, who, b->refs, want);
    }
}

#define POOL 16
static GLESBuffer pool[POOL];
static unsigned pooled;

/* A fresh context and a fresh pool: what each case starts from. */
static void reset(void)
{
    memset(&gh_state, 0, sizeof gh_state);
    pooled = 0;
}

static GLESBuffer *new_buffer(uint32_t name)
{
    GLESBuffer *b;
    CHECK(pooled < POOL, "out of test buffers");
    b = &pool[pooled++];
    memset(b, 0, sizeof *b);
    b->name = name;
    b->refs = 1;
    return b;
}

static void set_array(GLESArray *a, bool *normalized, uint32_t enabled, uint32_t size,
                      uint32_t type, uint32_t stride, uint32_t ptr, GLESBuffer *vbo, bool norm)
{
    a->enabled = enabled; a->size = size; a->type = type; a->stride = stride; a->ptr = ptr;
    gles_buffer_bind(&a->vbo, vbo);
    if (normalized) {
        *normalized = norm;
    }
}

/* gles_context_free, in its own order: the context's arrays, then the VAO
 * records, then the two buffer bindings, and last the caller's references. */
static void teardown(GLESBuffer **owner, unsigned count)
{
    GLESArray *arrays[] = { &gh_state.vertex, &gh_state.color, &gh_state.normal, &gh_state.pointsize };

    for (unsigned i = 0; i < 4; i++) {
        gles_buffer_destroy(arrays[i]->vbo);
    }
    for (unsigned i = 0; i < GLES_MAX_TEXUNITS; i++) {
        gles_buffer_destroy(gh_state.texcoord[i].vbo);
    }
    for (unsigned i = 0; i < GLES_MAX_ATTRIBS; i++) {
        gles_buffer_destroy(gh_state.attr[i].vbo);
    }
    if (gh_state.vaos) {
        g_hash_table_destroy(gh_state.vaos);
    }
    gles_vao_release(&gh_state.vao_default);
    gles_buffer_destroy(gh_state.array_buffer);
    gles_buffer_destroy(gh_state.element_buffer);
    for (unsigned i = 0; i < count; i++) {
        gles_buffer_destroy(owner[i]);
    }
}

/* 1. names: handed out, written to the guest, and a VAO only once bound */
static void test_names_are_generated_and_reported(void)
{
    reset();
    refusals = 0; guest_reads = 0; guest_writes = 0;
    uint32_t ids[3];

    CHECK(gles_vao_gen(NULL, 3, 64) == 0, "gen returned nonzero");
    memcpy(ids, guest_mem + 64, sizeof ids);
    CHECK(ids[0] == 1 && ids[1] == 2 && ids[2] == 3, "ids = %u,%u,%u", ids[0], ids[1], ids[2]);
    CHECK(guest_writes == 1 && guest_reads == 0, "guest access = %d writes, %d reads",
          guest_writes, guest_reads);
    CHECK(gles_vao_is(ids[0]) == 0, "a generated but unbound name reports itself a VAO");
    CHECK(gles_vao_is(0) == 0, "0 reports itself a VAO");

    gles_vao_bind(ids[0]);
    CHECK(gles_vao_is(ids[0]) == 1, "a bound name is not a VAO");
    CHECK(gh_state.vao == ids[0], "the bound name was not recorded: %u", gh_state.vao);

    CHECK(gles_vao_gen(NULL, 0, 0) == 0, "gen(0) failed");
    CHECK(gles_vao_gen(NULL, 1, 64) == 0 && guest_writes == 2, "gen(1) failed or did not write");
    memcpy(ids, guest_mem + 64, 4);
    CHECK(ids[0] == 4, "the next name was %u, wanted 4: names are not reused", ids[0]);

    guest_writes = 0;
    CHECK(gles_vao_gen(NULL, GLES_MAX_NAMES + 1, 0) == -1, "the generate cap was not enforced");
    CHECK(refusals == 1 && guest_writes == 0, "the capped generate still wrote to the guest");
    teardown(NULL, 0);
}

/* 2. a bind swaps the whole array state, and the references move with it */
static void test_bind_saves_and_restores_the_arrays(void)
{
    reset();
    GLESBuffer *vbuf = new_buffer(7), *ebuf = new_buffer(9), *own[2] = { vbuf, ebuf };

    set_array(&gh_state.vertex, NULL, 1, 3, 0x1406, 12, 0x100, vbuf, false);
    set_array(&gh_state.texcoord[2], NULL, 1, 2, 0x1406, 8, 0x200, vbuf, false);
    set_array(&gh_state.attr[5], &gh_state.attr_normalized[5], 1, 4, 0x1406, 16, 0x300, vbuf, true);
    gles_buffer_bind(&gh_state.element_buffer, ebuf);
    expect_refs(vbuf, 4, "the caller + three arrays in the context");
    expect_refs(ebuf, 2, "the caller + the element array");

    gles_vao_bind(1);                       /* the default record keeps what the context had */
    expect_refs(vbuf, 4, "the caller + the default record, with VAO 1 bound");
    gles_vao_bind(2);                       /* a second, empty one */
    CHECK(gh_state.vertex.enabled == 0, "binding an empty VAO left the vertex array on");
    CHECK(gh_state.texcoord[2].enabled == 0, "binding an empty VAO left a texture array on");
    CHECK(gh_state.attr[5].enabled == 0, "binding an empty VAO left a generic attribute on");
    CHECK(gh_state.attr_normalized[5] == false, "binding an empty VAO left a normalized flag set");
    CHECK(gh_state.element_buffer == NULL, "binding an empty VAO left the element array bound");
    expect_refs(vbuf, 4, "the caller + the default record");

    gles_vao_bind(0);                       /* the default state comes back, references and all */
    CHECK(gh_state.vertex.enabled == 1 && gh_state.vertex.size == 3 &&
          gh_state.vertex.type == 0x1406 && gh_state.vertex.stride == 12 &&
          gh_state.vertex.ptr == 0x100, "the default vertex array did not come back whole");
    CHECK(gh_state.texcoord[2].ptr == 0x200 && gh_state.attr[5].ptr == 0x300,
          "a texture or a generic array did not come back");
    CHECK(gh_state.attr_normalized[5], "the normalized flag did not come back");
    CHECK(gh_state.element_buffer == ebuf, "the default element array did not come back");
    expect_refs(vbuf, 7, "the caller + the default record + three arrays in the context");
    expect_refs(ebuf, 3, "the caller + the default record + the element array");

    gles_vao_bind(2);                       /* the empty one again */
    expect_refs(vbuf, 4, "the caller + the default record, the context having let go");
    expect_refs(ebuf, 2, "the caller + the default record");
    gles_vao_bind(2);                       /* binding the bound name only writes the state back */
    CHECK(gh_state.vao == 2, "re-binding the same name unbound it: %u", gh_state.vao);
    expect_refs(vbuf, 4, "re-binding the same name");

    /* The host mirror is the context's: a bind must neither clear it nor carry it
     * into another VAO, or the ES1 path would skip the host calls the new state
     * needs, believing the host already knows it. */
    gh_state.vertex.host_enabled = true;
    gh_state.vertex.host_pointer = (const void *)0x100;
    gh_state.vertex.host_pointer_valid = true;
    gh_state.vertex.host_size = 3;
    gles_vao_bind(1);
    CHECK(gh_state.vertex.host_pointer_valid, "a bind cleared the host mirror");
    gles_vao_bind(0);
    CHECK(gh_state.vertex.host_pointer == (const void *)0x100 && gh_state.vertex.host_size == 3,
          "the host mirror did not survive a round trip (it belongs to the context)");

    teardown(own, 2);
    expect_refs(vbuf, 0, "the vertex buffer after teardown");
    expect_refs(ebuf, 0, "the element buffer after teardown");
    CHECK(vbuf->freed && ebuf->freed, "a buffer's last reference was not dropped");
}

/* 3. deleting the bound one is as though 0 had been bound */
static void test_deleting_the_bound_one_falls_back_to_the_default(void)
{
    reset();
    GLESBuffer *vbuf = new_buffer(7), *own[1] = { vbuf };
    uint32_t ids[2] = { 0, 2 };             /* 0 is the default state and is skipped */

    set_array(&gh_state.vertex, NULL, 1, 3, 0x1406, 12, 0x100, vbuf, false);
    gles_vao_bind(1);                       /* the default record holds the array now */
    gles_vao_bind(2);                       /* an empty one is bound */
    expect_refs(vbuf, 2, "the caller + the default record");

    memcpy(guest_mem + 128, ids, sizeof ids);
    guest_writes = 0;
    CHECK(gles_vao_delete(NULL, 2, 128) == 0, "delete failed");
    CHECK(guest_writes == 0, "delete wrote to the guest's list");
    CHECK(gh_state.vao == 0, "the bound name was not cleared: %u", gh_state.vao);
    CHECK(gh_state.vertex.enabled == 1 && gh_state.vertex.ptr == 0x100,
          "the default state did not come back");
    CHECK(gles_vao_is(2) == 0, "a deleted name still reports itself a VAO");
    CHECK(gles_vao_is(1) == 1, "an unrelated name stopped being a VAO");
    expect_refs(vbuf, 3, "the caller + the default record + the context");

    teardown(own, 1);
    expect_refs(vbuf, 0, "the vertex buffer after teardown");
    CHECK(vbuf->freed, "the vertex buffer leaked");
}

/* 4. names that are not VAOs, an empty table, a null pointer: all quiet */
static void test_deleting_unknown_names_is_quiet(void)
{
    reset();
    uint32_t ids[2] = { 5, 6 };

    memcpy(guest_mem + 256, ids, sizeof ids);
    refusals = 0;
    CHECK(gles_vao_delete(NULL, 2, 256) == 0, "delete on an empty table failed");
    CHECK(gles_vao_delete(NULL, 0, 0) == 0, "delete(0) failed");
    CHECK(gles_vao_delete(NULL, 1, 0) == 0, "delete with a null guest pointer failed");

    gles_vao_bind(1);
    memcpy(guest_mem + 256, ids, sizeof ids);
    CHECK(gles_vao_delete(NULL, 2, 256) == 0, "delete of unknown names failed");
    CHECK(gh_state.vao == 1, "an unknown name unbound the current one: %u", gh_state.vao);
    CHECK(gles_vao_delete(NULL, GLES_MAX_NAMES + 1, 256) == -1 && refusals == 1,
          "the delete cap was not enforced");
    teardown(NULL, 0);
}

/* 5. a name never generated still binds, and holds an empty record */
static void test_binding_a_name_that_was_never_generated(void)
{
    reset();

    set_array(&gh_state.color, NULL, 1, 4, 0x1406, 16, 0x400, NULL, false);
    gles_vao_bind(99);
    CHECK(gles_vao_is(99) == 1, "binding a name did not make it a VAO");
    CHECK(gh_state.color.enabled == 0, "binding a fresh VAO left the colour array on");
    gles_vao_bind(0);
    CHECK(gh_state.color.enabled == 1 && gh_state.color.ptr == 0x400,
          "the default state did not come back");
    teardown(NULL, 0);
}

/* 6. GL_ARRAY_BUFFER stays with the context; the per-array buffer travels */
static void test_the_array_buffer_binding_stays_with_the_context(void)
{
    reset();
    GLESBuffer *abuf = new_buffer(3), *vbuf = new_buffer(4), *own[2] = { abuf, vbuf };

    gles_buffer_bind(&gh_state.array_buffer, abuf);
    set_array(&gh_state.vertex, NULL, 1, 2, 0x1406, 8, 0x500, vbuf, false);
    gles_vao_bind(1);
    CHECK(gh_state.array_buffer == abuf, "binding a VAO moved the array-buffer binding");
    gles_vao_bind(0);
    CHECK(gh_state.array_buffer == abuf, "binding 0 moved the array-buffer binding");
    CHECK(gh_state.vertex.vbo == vbuf, "the per-array buffer did not come back with the VAO");

    teardown(own, 2);
    expect_refs(abuf, 0, "the array buffer after teardown");
    expect_refs(vbuf, 0, "the vertex buffer after teardown");
    CHECK(abuf->freed && vbuf->freed, "a buffer survived teardown");
}

int main(void)
{
    test_names_are_generated_and_reported();
    test_bind_saves_and_restores_the_arrays();
    test_deleting_the_bound_one_falls_back_to_the_default();
    test_deleting_unknown_names_is_quiet();
    test_binding_a_name_that_was_never_generated();
    test_the_array_buffer_binding_stays_with_the_context();
    if (failures) {
        printf("%d of %d checks failed\n", failures, checks);
        return 1;
    }
    printf("6 cases, %d checks: names, bind, delete, and what stays with the context", checks);
    return 0;
}
