"""The vertex array object contract, checked as text.

tests/gles/vao.c drives the state machine itself. These checks hold the pieces
around it in place: the patch carries the four entry points in both spellings
and touches nothing else, it asks the host for no VAO of its own -- which is the
design that keeps it working on an ES 1.1 host context and on every backend --
it reads only the arguments the name table says the guest sends, it releases the
records with the context, and the record layout the slice test restates is the
engine's own.

Each check raises with one sentence when it fails, and returns a short note when
it passes.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCHES = ROOT / "ci" / "engine-patches"
VAO_PATCH = sorted(PATCHES.glob("*.patch"))[0]
TEST = ROOT / "tests" / "gles" / "vao.c"

BRIDGE = "hw/arm/gles-host.c"

# The four entry points in both spellings: the id the guest calls, the function
# that runs it, and how many arguments the name table says arrive with it.
ENTRY_POINTS = (
    (550, "gles_vao_bind", "glBindVertexArrayEXT", 1),
    (551, "gles_vao_delete", "glDeleteVertexArraysEXT", 2),
    (552, "gles_vao_gen", "glGenVertexArraysEXT", 2),
    (553, "gles_vao_is", "glIsVertexArrayEXT", 1),
    (837, "gles_vao_bind", "glBindVertexArrayOES", 1),
    (838, "gles_vao_delete", "glDeleteVertexArraysOES", 2),
    (839, "gles_vao_gen", "glGenVertexArraysOES", 2),
    (840, "gles_vao_is", "glIsVertexArrayOES", 1),
)

# The host's own VAO entry points. The engine must not call one: an ES 1.1 host
# context does not have them, and both draw paths re-apply the arrays anyway.
HOST_VAO_CALL = re.compile(r"\bgl(?:Gen|Delete|Bind|Is)VertexArrays?\w*\s*\(")


def read(relative):
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"{relative} is missing")
    return path.read_text(errors="ignore")


def patch_added_lines(patch):
    """The lines a patch adds, as one body of text (its commit message is not in it)."""
    lines = read(patch).splitlines()
    return "\n".join(line[1:] for line in lines
                     if line.startswith("+") and not line.startswith("+++"))


def code_only(text):
    """The added text with its comments dropped: a host call in a comment is not a call."""
    kept = []
    for line in text.splitlines():
        if line.strip().startswith(("/*", "*", "//")):
            continue
        kept.append(re.sub(r"/\*.*?\*/", "", line))
    return "\n".join(kept)


def patch_files(patch):
    return sorted({match.group(1) for match in
                   re.finditer(r"^diff --git a/(\S+) b/\S+$", read(patch), re.M)})


def files_touched_by_the_patch():
    """The bridge only: the name table, and with it the guest package, stays as it was."""
    if not VAO_PATCH.is_file():
        raise AssertionError(f"{VAO_PATCH.name} is missing")
    touched = patch_files("ci/engine-patches/" + VAO_PATCH.name)
    if touched != [BRIDGE]:
        raise AssertionError(f"the patch touches {touched}, wanted only {BRIDGE}: a change to "
                             f"the name table would rebuild the guest package for nothing")
    return f"{VAO_PATCH.name}: {BRIDGE} only"


def every_entry_point_is_dispatched():
    """All eight ids run one of the four functions, and the dispatch says which."""
    added = patch_added_lines("ci/engine-patches/" + VAO_PATCH.name)
    missing = []
    for slot, function, name, _ in ENTRY_POINTS:
        if not re.search(rf"case {slot}: \*r = {function}\(.*{name}", added):
            missing.append(f"{name} ({slot})")
    if missing:
        raise AssertionError("the patch does not dispatch " + ", ".join(missing))
    return f"{len(ENTRY_POINTS)} ids in both spellings"


def each_entry_point_reads_only_the_arguments_it_is_sent():
    """A one-argument call must not read a[1]: the guest sends no such word."""
    added = patch_added_lines("ci/engine-patches/" + VAO_PATCH.name)
    wrong = []
    for slot, _, name, argc in ENTRY_POINTS:
        line = next((line for line in added.splitlines()
                     if line.strip().startswith(f"case {slot}:")), None)
        if line is None:
            raise AssertionError(f"the patch does not dispatch {name} ({slot})")
        reads = sorted({int(match) for match in re.findall(r"\ba\[(\d+)\]", line)})
        wanted = list(range(argc))
        if reads != wanted:
            wrong.append(f"{name} reads a{reads}, the name table sends {argc} argument(s)")
    if wrong:
        raise AssertionError("; ".join(wrong))
    return "bind and is read a[0]; generate and delete read a[0] and a[1]"


def the_host_is_asked_for_no_vao():
    """The state is the engine's own, so no backend needs ARB_vertex_array_object."""
    added = code_only(patch_added_lines("ci/engine-patches/" + VAO_PATCH.name))
    calls = sorted({match.group(0).rstrip("(").strip() for match in HOST_VAO_CALL.finditer(added)})
    if calls:
        raise AssertionError("the patch calls into the host's VAO: " + ", ".join(calls) +
                             " (an ES 1.1 host context has none, and the draws re-apply the arrays)")
    if "gles_vao_bind" not in added or "gles_vao_load" not in added:
        raise AssertionError("the patch has no VAO state of its own either")
    return "no host VAO call; the record is loaded into the context's own arrays"


def the_records_are_released_with_the_context():
    """A record holds buffer references, so gles_context_free has to let them go."""
    added = patch_added_lines("ci/engine-patches/" + VAO_PATCH.name)
    for needed in ("g_hash_table_destroy(state->vaos)", "gles_vao_release(&state->vao_default)"):
        if needed not in added:
            raise AssertionError(f"gles_context_free does not release the records: {needed} is missing")
    if "gles_buffer_bind(&v->array[i].vbo, NULL)" not in added:
        raise AssertionError("a record's buffer references are not dropped")
    return "gles_context_free destroys the table and releases the default record"


def the_binding_query_is_answered():
    """glGetIntegerv(GL_VERTEX_ARRAY_BINDING_OES) answers the bound name."""
    added = patch_added_lines("ci/engine-patches/" + VAO_PATCH.name)
    if added.count("case GLES_VAO_BINDING:") != 2:
        raise AssertionError("GL_VERTEX_ARRAY_BINDING_OES is not both counted and answered")
    if "v.i[0] = gh.vao;" not in added:
        raise AssertionError("the binding query does not answer the bound name")
    return "counted for the query, and answered with the bound name"


def the_test_restates_the_record_the_engine_declares():
    """vao.c needs the layout before the slice arrives, so it must not drift from it."""
    field = re.compile(r"([A-Za-z_]\w*)\s*(?:\[[^\]]*\])?\s*(?:,|;)")

    def fields(text, name):
        # [^{}]* so the match cannot span an earlier struct in the same file
        match = re.search(r"typedef struct \{([^{}]*)\}\s*" + name + r";", text)
        if not match:
            raise AssertionError(f"no {name} in what it was given")
        return sorted(set(field.findall(match.group(1))))

    engine = patch_added_lines("ci/engine-patches/" + VAO_PATCH.name)
    test = TEST.read_text(errors="ignore")
    for name in ("GlesArrayState", "GlesVertexArray"):
        declared, restated = fields(engine, name), fields(test, name)
        if declared != restated:
            raise AssertionError(f"{name} in tests/gles/vao.c is {restated}, the engine declares "
                                 f"{declared}")
    return "GlesArrayState and GlesVertexArray match the engine's"


CHECKS = [
    ("engine patch: the bridge only, so the guest package stays as it was", files_touched_by_the_patch),
    ("engine patch: all eight ids are dispatched", every_entry_point_is_dispatched),
    ("engine patch: each entry point reads only its own arguments",
     each_entry_point_reads_only_the_arguments_it_is_sent),
    ("engine patch: the host is asked for no VAO of its own", the_host_is_asked_for_no_vao),
    ("engine patch: the records are released with the context", the_records_are_released_with_the_context),
    ("engine patch: the binding query is answered", the_binding_query_is_answered),
    ("slice test: the record it restates is the engine's", the_test_restates_the_record_the_engine_declares),
]
