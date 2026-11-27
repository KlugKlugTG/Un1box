/* Vertex array objects: the state a bind swaps, and what must not travel with it.
 *
 * The guest's OES_vertex_array_object and EXT calls reach the bridge as ids
 * 550-553 and 837-840: glBindVertexArray*, glDeleteVertexArrays*,
 * glGenVertexArrays* and glIsVertexArray*. The engine keeps a VAO as a record
 * for the array state its context already keeps, with no host VAO object: the
 * host may be an ES 1.1 context, where ES3 entry points are not valid, and both
 * draw paths re-apply the arrays themselves.
 *
 * These cases drive gles_vao_* and gles_array_swap_guest(), sliced directly
 * out of hw/arm/gles-host.c:
 *
 *   names    generated names live in the context's hash table; 0 is the
 *            default VAO and ungenerated names fail gles_vao_bind()
 *   bind     swaps the live context arrays with the named VAO's copy,
 *            element_buffer and per-array VBO refcounts included
 *   delete   deleting the bound VAO after binding 0 leaves the default intact
 *   context  host_* mirrors, scratch buffers and GL_ARRAY_BUFFER stay with the
 *            context rather than travelling with a VAO
 *
 * SLICE engine hw/arm/gles-host.c define GLES_MAX_(NAMES|TEXUNITS|ATTRIBS)
 * SLICE engine hw/arm/gles-host.c define GLES_VERTEX_ARRAY_BINDING_OES
 * SLICE engine hw/arm/gles-host.c fn gles_array_swap_guest gles_vao_swap gles_vao_free gles_vao_table gles_vao_find gles_vao_bind
 * CFLAGS -Wall -Wextra -Werror -Wno-unused-function -Wno-unused-parameter
 */
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef uint32_t GLenum, GLuint;
typedef int32_t GLint;

#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))

/* --- glib, reduced to the hash table and allocation macros --- */
typedef void *gpointer;
typedef void (*GDestroyNotify)(void *);
#define g_direct_hash  NULL
#define g_direct_equal NULL
#define GUINT_TO_POINTER(u) ((void *)(uintptr_t)(u))

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

/* --- the buffer, the array, the VAO record and the reduced host context --- */
typedef struct GLESBuffer { uint32_t name; unsigned refs; unsigned freed; } GLESBuffer;

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
    uint8_t *buf; size_t buf_size;
    float *fbuf; size_t fbuf_size;
    GLenum client_state;
    bool host_enabled, host_pointer_valid, host_normalized;
    uint32_t host_size, host_stride;
    GLenum host_type;
    const void *host_pointer;
} GLESArray;

/* The VAO record, matching hw/arm/gles-host.c (wiring.py keeps this honest). */
typedef struct GLESVao {
    GLESArray vertex, color, normal, pointsize, weight, matrix_index;
    GLESArray texcoord[GLES_MAX_TEXUNITS];
    GLESArray attr[GLES_MAX_ATTRIBS];
    bool attr_normalized[GLES_MAX_ATTRIBS];
    GLESBuffer *element_buffer;
} GLESVao;

typedef struct {
    GLESArray vertex, color, normal, pointsize, weight, matrix_index;
    GLESArray texcoord[GLES_MAX_TEXUNITS];
    GLESArray attr[GLES_MAX_ATTRIBS];
    bool attr_normalized[GLES_MAX_ATTRIBS];
    GLESBuffer *array_buffer, *element_buffer;
    GHashTable *vaos;
    uint32_t vao_next;
    GLuint vao_bound;
    GLESVao *vao_default;
} GLESHost;

static GLESHost gh_state;
#define gh (gh_state)

static void gles_array_swap_guest(GLESArray *x, GLESArray *y);
static void gles_vao_swap(GLESHost *h, GLESVao *v);
static void gles_vao_free(gpointer p);
static GHashTable *gles_vao_table(GLESHost *h);
static GLESVao *gles_vao_find(GLESHost *h, uint32_t name);
static bool gles_vao_bind(GLESHost *h, uint32_t name);

/* ------------------------------------------------------------- cases ---- */
static int failures, checks;

#define CHECK(cond, ...) do { \
    checks++; \
    if (!(cond)) { failures++; printf("FAIL %s:%d ", __func__, __LINE__); printf(__VA_ARGS__); printf("\n"); } \
} while (0)

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

static void gen_names(GLESHost *h, uint32_t n, uint32_t *out)
{
    for (uint32_t i = 0; i < n; i++) {
        do {
            out[i] = ++h->vao_next;
        } while (!out[i] || gles_vao_find(h, out[i]));
        g_hash_table_insert(gles_vao_table(h), GUINT_TO_POINTER(out[i]), g_new0(GLESVao, 1));
    }
}

static void delete_names(GLESHost *h, uint32_t n, const uint32_t *ids)
{
    if (!h->vaos) {
        return;
    }
    for (uint32_t i = 0; i < n; i++) {
        if (!ids[i] || !gles_vao_find(h, ids[i])) {
            continue;
        }
        if (ids[i] == h->vao_bound) {
            gles_vao_bind(h, 0);
        }
        g_hash_table_remove(h->vaos, GUINT_TO_POINTER(ids[i]));
    }
}

static void teardown(GLESBuffer **owner, unsigned count)
{
    GLESArray *arrays[] = {
        &gh_state.vertex, &gh_state.color, &gh_state.normal,
        &gh_state.pointsize, &gh_state.weight, &gh_state.matrix_index,
    };

    for (unsigned i = 0; i < ARRAY_SIZE(arrays); i++) {
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
    gles_vao_free(gh_state.vao_default);
    gles_buffer_destroy(gh_state.array_buffer);
    gles_buffer_destroy(gh_state.element_buffer);
    for (unsigned i = 0; i < count; i++) {
        gles_buffer_destroy(owner[i]);
    }
}

/* 1. names: generated, looked up via gles_vao_find, bound cleanly */
static void test_names_are_generated_and_reported(void)
{
    reset();
    uint32_t ids[3] = {0};

    gen_names(&gh_state, 3, ids);
    CHECK(ids[0] == 1 && ids[1] == 2 && ids[2] == 3, "ids = %u,%u,%u", ids[0], ids[1], ids[2]);
    CHECK(gles_vao_find(&gh_state, ids[0]) != NULL, "generated name is missing from table");
    CHECK(gles_vao_find(&gh_state, 99) == NULL, "ungenerated name reported as VAO");
    CHECK(gles_vao_find(&gh_state, 0) != NULL, "default VAO 0 missing");

    CHECK(gles_vao_bind(&gh_state, ids[0]), "failed to bind generated VAO");
    CHECK(gh_state.vao_bound == ids[0], "vao_bound = %u", gh_state.vao_bound);

    uint32_t one = 0;
    gen_names(&gh_state, 1, &one);
    CHECK(one == 4, "next generated name = %u, wanted 4", one);
    teardown(NULL, 0);
}

/* 2. a bind swaps the whole array state, and buffer references have a single owner */
static void test_bind_saves_and_restores_the_arrays(void)
{
    reset();
    GLESBuffer *vbuf = new_buffer(7), *ebuf = new_buffer(9), *own[2] = { vbuf, ebuf };
    uint32_t ids[2];
    gen_names(&gh_state, 2, ids);

    set_array(&gh_state.vertex, NULL, 1, 3, 0x1406, 12, 0x100, vbuf, false);
    set_array(&gh_state.weight, NULL, 1, 2, 0x1406, 8, 0x140, vbuf, false);
    set_array(&gh_state.texcoord[2], NULL, 1, 2, 0x1406, 8, 0x200, vbuf, false);
    set_array(&gh_state.attr[5], &gh_state.attr_normalized[5], 1, 4, 0x1406, 16, 0x300, vbuf, true);
    gles_buffer_bind(&gh_state.element_buffer, ebuf);
    expect_refs(vbuf, 5, "caller + four arrays in context");
    expect_refs(ebuf, 2, "caller + element_buffer");

    CHECK(gles_vao_bind(&gh_state, ids[0]), "bind VAO 1 failed");
    CHECK(gh_state.vertex.enabled == 0 && gh_state.weight.enabled == 0 &&
          gh_state.texcoord[2].enabled == 0 && gh_state.attr[5].enabled == 0,
          "binding fresh VAO left arrays enabled");
    CHECK(!gh_state.attr_normalized[5], "binding fresh VAO left normalized flag set");
    CHECK(gh_state.element_buffer == NULL, "binding fresh VAO left element_buffer bound");
    expect_refs(vbuf, 5, "caller + default VAO (ownership moved, not duplicated)");
    expect_refs(ebuf, 2, "caller + default VAO");

    /* Host mirrors belong to the context and must survive VAO binds untouched. */
    gh_state.vertex.host_enabled = true;
    gh_state.vertex.host_pointer = (const void *)0x100;
    gh_state.vertex.host_pointer_valid = true;
    gh_state.vertex.host_size = 3;

    CHECK(gles_vao_bind(&gh_state, ids[1]), "bind VAO 2 failed");
    CHECK(gh_state.vertex.host_pointer_valid && gh_state.vertex.host_pointer == (const void *)0x100,
          "bind disturbed host mirror");

    CHECK(gles_vao_bind(&gh_state, 0), "bind default VAO 0 failed");
    CHECK(gh_state.vertex.enabled == 1 && gh_state.vertex.ptr == 0x100 && gh_state.weight.ptr == 0x140,
          "default vertex/weight arrays lost");
    CHECK(gh_state.texcoord[2].ptr == 0x200 && gh_state.attr[5].ptr == 0x300 && gh_state.attr_normalized[5],
          "default texcoord/attr state lost");
    CHECK(gh_state.element_buffer == ebuf, "default element_buffer lost");
    expect_refs(vbuf, 5, "caller + live context after returning to 0");

    /* Rebinding the already-bound name is a no-op. */
    CHECK(gles_vao_bind(&gh_state, 0), "re-bind 0 failed");
    CHECK(gh_state.vao_bound == 0, "re-bind 0 changed vao_bound");

    teardown(own, 2);
    expect_refs(vbuf, 0, "vbuf after teardown");
    expect_refs(ebuf, 0, "ebuf after teardown");
    CHECK(vbuf->freed && ebuf->freed, "a buffer's last reference leaked");
}

/* 3. deleting the bound VAO falls back to 0 and restores the default arrays */
static void test_deleting_the_bound_one_falls_back_to_the_default(void)
{
    reset();
    GLESBuffer *vbuf = new_buffer(7), *own[1] = { vbuf };
    uint32_t ids[2];
    gen_names(&gh_state, 2, ids);

    set_array(&gh_state.vertex, NULL, 1, 3, 0x1406, 12, 0x100, vbuf, false);
    CHECK(gles_vao_bind(&gh_state, ids[0]), "bind VAO 1 failed");
    CHECK(gles_vao_bind(&gh_state, ids[1]), "bind VAO 2 failed");

    uint32_t del[2] = { 0, ids[1] };
    delete_names(&gh_state, 2, del);
    CHECK(gh_state.vao_bound == 0, "bound name not reset to 0 after delete: %u", gh_state.vao_bound);
    CHECK(gh_state.vertex.enabled == 1 && gh_state.vertex.ptr == 0x100, "default state not restored");
    CHECK(gles_vao_find(&gh_state, ids[1]) == NULL, "deleted name still in table");
    CHECK(gles_vao_find(&gh_state, ids[0]) != NULL, "unrelated VAO deleted");

    teardown(own, 1);
    expect_refs(vbuf, 0, "vbuf after teardown");
    CHECK(vbuf->freed, "vertex buffer leaked");
}

/* 4. binding a name that was never generated returns false and leaves state untouched */
static void test_binding_ungenerated_name_is_rejected(void)
{
    reset();
    set_array(&gh_state.color, NULL, 1, 4, 0x1406, 16, 0x400, NULL, false);
    CHECK(!gles_vao_bind(&gh_state, 99), "ungenerated name 99 succeeded");
    CHECK(gh_state.vao_bound == 0 && gh_state.color.enabled == 1 && gh_state.color.ptr == 0x400,
          "failed bind altered state");
    teardown(NULL, 0);
}

/* 5. GL_ARRAY_BUFFER stays with the context; per-array VBOs travel with the VAO */
static void test_the_array_buffer_binding_stays_with_the_context(void)
{
    reset();
    GLESBuffer *abuf = new_buffer(3), *vbuf = new_buffer(4), *own[2] = { abuf, vbuf };
    uint32_t id = 0;
    gen_names(&gh_state, 1, &id);

    gles_buffer_bind(&gh_state.array_buffer, abuf);
    set_array(&gh_state.vertex, NULL, 1, 2, 0x1406, 8, 0x500, vbuf, false);
    CHECK(gles_vao_bind(&gh_state, id), "bind VAO failed");
    CHECK(gh_state.array_buffer == abuf, "bind moved GL_ARRAY_BUFFER");
    CHECK(gh_state.vertex.vbo == NULL, "fresh VAO had vertex VBO");

    CHECK(gles_vao_bind(&gh_state, 0), "bind 0 failed");
    CHECK(gh_state.array_buffer == abuf, "bind 0 moved GL_ARRAY_BUFFER");
    CHECK(gh_state.vertex.vbo == vbuf, "per-array VBO lost");

    teardown(own, 2);
    expect_refs(abuf, 0, "array_buffer after teardown");
    expect_refs(vbuf, 0, "vertex VBO after teardown");
    CHECK(abuf->freed && vbuf->freed, "buffer leaked on teardown");
}

int main(void)
{
    test_names_are_generated_and_reported();
    test_bind_saves_and_restores_the_arrays();
    test_deleting_the_bound_one_falls_back_to_the_default();
    test_binding_ungenerated_name_is_rejected();
    test_the_array_buffer_binding_stays_with_the_context();
    if (failures) {
        printf("%d of %d checks failed\n", failures, checks);
        return 1;
    }
    printf("5 cases, %d checks: names, swap bind, delete, rejection, and context state", checks);
    return 0;
}
