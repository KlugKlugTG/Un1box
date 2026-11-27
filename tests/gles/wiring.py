"""The vertex array object contract, checked as text.

tests/gles/vao.c drives the state machine itself. These checks hold the pieces
around it in place: 0011 adds the four EXT fallthrough aliases on top of the
upstream OES dispatch in hw/arm/gles-host.c without touching any other file,
the bridge asks the host for no VAO of its own (keeping ES 1.1 host contexts
working), reads only the arguments the name table sends, frees the records with
the context, answers GL_VERTEX_ARRAY_BINDING_OES, and agrees with vao.c on
GLESVao's layout.

Each check raises with one sentence when it fails, and returns a short note when
it passes.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCHES = ROOT / "ci" / "engine-patches"
TEST = ROOT / "tests" / "gles" / "vao.c"
BRIDGE = "hw/arm/gles-host.c"

# The four entry points in both spellings: the id the guest calls, its GLES_ID
# macro, its name in gles-names.h, and how many arguments the table sends.
ENTRY_POINTS = (
    (550, "GLES_ID_glBindVertexArrayEXT", "glBindVertexArrayEXT", 1),
    (551, "GLES_ID_glDeleteVertexArraysEXT", "glDeleteVertexArraysEXT", 2),
    (552, "GLES_ID_glGenVertexArraysEXT", "glGenVertexArraysEXT", 2),
    (553, "GLES_ID_glIsVertexArrayEXT", "glIsVertexArrayEXT", 1),
    (837, "GLES_ID_glBindVertexArrayOES", "glBindVertexArrayOES", 1),
    (838, "GLES_ID_glDeleteVertexArraysOES", "glDeleteVertexArraysOES", 2),
    (839, "GLES_ID_glGenVertexArraysOES", "glGenVertexArraysOES", 2),
    (840, "GLES_ID_glIsVertexArrayOES", "glIsVertexArrayOES", 1),
)

HOST_VAO_CALL = re.compile(r"\bgl(?:Gen|Delete|Bind|Is)VertexArrays?\w*\s*\(")

ENGINE_TREE: Path | None = None


def set_engine(path):
    global ENGINE_TREE
    ENGINE_TREE = Path(path) if path is not None else None


def patch_with(marker):
    for candidate in sorted(PATCHES.glob("*.patch")):
        if marker in candidate.read_text(errors="ignore"):
            return candidate
    raise AssertionError("no patch in ci/engine-patches carries " + marker)


VAO_PATCH = patch_with("GLES_ID_glBindVertexArrayEXT")


def read(relative):
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"{relative} is missing")
    return path.read_text(errors="ignore")


def read_engine_bridge():
    if ENGINE_TREE is None:
        return None
    path = ENGINE_TREE / BRIDGE
    if not path.is_file():
        raise AssertionError(f"engine/{BRIDGE} is missing")
    return path.read_text(errors="ignore")


def patch_added_lines(patch):
    lines = read(patch).splitlines()
    return "\n".join(line[1:] for line in lines
                     if line.startswith("+") and not line.startswith("+++"))


def code_only(text):
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


def patch_dispatches_ext_aliases():
    """0011 adds the four EXT fallthrough cases directly above the upstream OES cases."""
    added = patch_added_lines("ci/engine-patches/" + VAO_PATCH.name)
    for ext in ("glGenVertexArraysEXT", "glDeleteVertexArraysEXT",
                "glBindVertexArrayEXT", "glIsVertexArrayEXT"):
        if f"case GLES_ID_{ext}:" not in added:
            raise AssertionError(f"{VAO_PATCH.name} does not dispatch {ext}")
    return "all four EXT VAO aliases dispatched"


def every_entry_point_is_dispatched():
    """All eight ids reach the engine's VAO dispatch cases."""
    bridge = read_engine_bridge()
    if bridge is None:
        return "skipped: no engine checkout"
    missing = [name for _, macro, name, _ in ENTRY_POINTS if f"case {macro}:" not in bridge]
    if missing:
        raise AssertionError("the bridge does not dispatch " + ", ".join(missing))
    for ext, oes in (
        ("GLES_ID_glGenVertexArraysEXT", "GLES_ID_glGenVertexArraysOES"),
        ("GLES_ID_glDeleteVertexArraysEXT", "GLES_ID_glDeleteVertexArraysOES"),
        ("GLES_ID_glBindVertexArrayEXT", "GLES_ID_glBindVertexArrayOES"),
        ("GLES_ID_glIsVertexArrayEXT", "GLES_ID_glIsVertexArrayOES"),
    ):
        pattern = rf"case {ext}:\s*\n\s*case {oes}:"
        if not re.search(pattern, bridge):
            raise AssertionError(f"{ext} does not fall straight through to {oes}")
    return f"{len(ENTRY_POINTS)} ids in both spellings"


def each_entry_point_reads_only_the_arguments_it_is_sent():
    """A one-argument call must not read a[1]: the guest sends no such word."""
    bridge = read_engine_bridge()
    if bridge is None:
        return "skipped: no engine checkout"
    cases = {
        "glGenVertexArraysOES": 2,
        "glDeleteVertexArraysOES": 2,
        "glBindVertexArrayOES": 1,
        "glIsVertexArrayOES": 1,
    }
    wrong = []
    for name, argc in cases.items():
        marker = f"case GLES_ID_{name}:"
        start = bridge.find(marker)
        if start < 0:
            raise AssertionError(f"missing {marker}")
        chunk = bridge[start:bridge.find("\n    case ", start + len(marker))]
        reads = sorted({int(m) for m in re.findall(r"\ba\[(\d+)\]", chunk)})
        if reads != list(range(argc)):
            wrong.append(f"{name} reads a{reads}, table sends {argc}")
    if wrong:
        raise AssertionError("; ".join(wrong))
    return "bind and is read a[0]; generate and delete read a[0] and a[1]"


def the_host_is_asked_for_no_vao():
    """The state is the engine's own, so no backend needs ARB_vertex_array_object."""
    bridge = read_engine_bridge()
    if bridge is None:
        return "skipped: no engine checkout"
    calls = sorted({match.group(0).rstrip("(").strip()
                    for match in HOST_VAO_CALL.finditer(code_only(bridge))})
    if calls:
        raise AssertionError("hw/arm/gles-host.c calls into the host's VAO: " + ", ".join(calls))
    if "gles_vao_bind" not in bridge or "gles_vao_swap" not in bridge:
        raise AssertionError("hw/arm/gles-host.c lacks gles_vao_bind/gles_vao_swap")
    return "no host VAO call; gles_vao_bind swaps the context's live arrays"


def the_records_are_released_with_the_context():
    """A record holds buffer references, so gles_context_free has to let them go."""
    bridge = read_engine_bridge()
    if bridge is None:
        return "skipped: no engine checkout"
    cleanup = bridge.split("static void gles_context_free(", 1)
    if len(cleanup) < 2:
        raise AssertionError("gles_context_free missing")
    body = cleanup[1].split("\n}", 1)[0]
    for needed in ("g_hash_table_destroy(state->vaos)", "gles_vao_free(state->vao_default)"):
        if needed not in body:
            raise AssertionError(f"gles_context_free does not release the records: {needed} missing")
    if "gles_buffer_bind(&v->element_buffer, NULL)" not in bridge:
        raise AssertionError("gles_vao_free does not release element_buffer")
    return "gles_context_free destroys the table and frees the default record"


def the_binding_query_is_answered():
    """glGetIntegerv(GL_VERTEX_ARRAY_BINDING_OES) answers the bound name."""
    bridge = read_engine_bridge()
    if bridge is None:
        return "skipped: no engine checkout"
    if bridge.count("case GLES_VERTEX_ARRAY_BINDING_OES:") != 2:
        raise AssertionError("GL_VERTEX_ARRAY_BINDING_OES is not both counted and answered")
    if "v.i[0] = gh.vao_bound;" not in bridge:
        raise AssertionError("the binding query does not answer gh.vao_bound")
    return "counted for the query, and answered with gh.vao_bound"


def the_test_restates_the_record_the_engine_declares():
    """vao.c needs GLESVao's layout before the slice arrives, so it must match the engine's."""
    bridge = read_engine_bridge()
    if bridge is None:
        return "skipped: no engine checkout"
    field = re.compile(r"([A-Za-z_]\w*)\s*(?:\[[^\]]*\])?\s*(?:,|;)")

    def fields(text, name):
        match = re.search(r"typedef struct " + name + r" \{([^{}]*)\}\s*" + name + r";", text)
        if not match:
            raise AssertionError(f"no {name} in source")
        return sorted(set(field.findall(match.group(1))))

    declared, restated = fields(bridge, "GLESVao"), fields(TEST.read_text(errors="ignore"), "GLESVao")
    if declared != restated:
        raise AssertionError(f"GLESVao in tests/gles/vao.c is {restated}, engine declares {declared}")
    return "GLESVao matches the engine's declaration"


CHECKS = [
    ("engine patch: the bridge only, so the guest package stays as it was", files_touched_by_the_patch),
    ("engine patch: all four EXT VAO aliases dispatched", patch_dispatches_ext_aliases),
    ("engine: all eight ids are dispatched", every_entry_point_is_dispatched),
    ("engine: each entry point reads only its own arguments",
     each_entry_point_reads_only_the_arguments_it_is_sent),
    ("engine: the host is asked for no VAO of its own", the_host_is_asked_for_no_vao),
    ("engine: the records are released with the context", the_records_are_released_with_the_context),
    ("engine: glGet(GL_VERTEX_ARRAY_BINDING_OES) answers the bound name", the_binding_query_is_answered),
    ("test: GLESVao matches the engine's declaration", the_test_restates_the_record_the_engine_declares),
]
