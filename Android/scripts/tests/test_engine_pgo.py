"""Unit and workflow contract tests for Android engine PGO preparation."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT_PATH = ROOT / "Android/scripts/prepare-engine-pgo.py"
SPEC = importlib.util.spec_from_file_location("prepare_engine_pgo", SCRIPT_PATH)
PGO = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PGO)

FAKE_LLVM_PROFDATA = """#!/usr/bin/env python3
import pathlib, sys
args = sys.argv[1:]
if args[0] == "merge":
    if "--text" in args:
        out_idx = args.index("-o") + 1
        out = pathlib.Path(args[out_idx])
        src = pathlib.Path(args[-1])
        out.write_text(src.read_text(encoding="utf-8"), encoding="utf-8")
    else:
        out_idx = args.index("-o") + 1
        out = pathlib.Path(args[out_idx])
        src = pathlib.Path(args[-1])
        out.write_text(src.read_text(encoding="utf-8"), encoding="utf-8")
elif args[0] == "show":
    src = pathlib.Path(args[-1]).read_text(encoding="utf-8")
    print("Counters:")
    count = 0
    for line in src.splitlines():
        s = line.strip()
        if not s or s.startswith(("#", ":")) or s.isdigit():
            continue
        if s.startswith(("0:", "1:")):
            continue
        print(f"  {s}:")
        print("    Hash: 0x0000000000000001")
        print("    Counters: 1")
        print("    Function count: 42")
        count += 1
    print("Instrumentation level: IR  entry_first = 0")
    print(f"Total functions: {count}")
else:
    raise SystemExit(f"unsupported fake command: {args}")
"""


def make_tar_gz(path: Path, members: dict[str, bytes]) -> str:
    with tarfile.open(path, "w:gz") as tar:
        for name, data in members.items():
            info = tarfile.TarInfo(name=name)
            info.size = len(data)
            info.mode = 0o644
            tar.addfile(info, io.BytesIO(data))
    return hashlib.sha256(path.read_bytes()).hexdigest()


class EnginePgoTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.source = self.root / "qemu-ios"
        for rel in (
            "accel/tcg/cpu-exec.c",
            "hw/arm/s5l8950.c",
            "target/arm/tcg/decode-vfp.c.inc",
        ):
            p = self.source / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("/* stub */\n", encoding="utf-8")
        # Decoy sibling matching the `../engine/` suffix outside source_dir:
        decoy = self.root / "engine/accel/tcg/cpu-exec.c"
        decoy.parent.mkdir(parents=True, exist_ok=True)
        decoy.write_text("/* decoy */\n", encoding="utf-8")
        self.build_dir = self.root / "ci/engine-build"

    def tearDown(self):
        self.tmp.cleanup()

    def test_compute_target_prefix(self):
        self.assertEqual(PGO.compute_target_prefix(self.source, self.build_dir), "../../qemu-ios/")
        rel_engine = self.root / "release/engine"
        rel_build = self.root / "release/app/build/obj/android-engine-arm64"
        self.assertEqual(PGO.compute_target_prefix(rel_engine, rel_build), "../../../../engine/")

    def test_detect_and_rewrite_remaps_static_and_vp_without_escaping(self):
        raw_text = (
            ":ir\n"
            "# Comment mentioning ../../../../engine/accel/tcg/cpu-exec.c\n"
            "cpu_exec\n"
            "123\n"
            "1\n"
            "42\n\n"
            "../../../../engine/accel/tcg/cpu-exec.c:tb_lookup\n"
            "456\n"
            "2\n"
            "10\n"
            "20\n"
            "# Value Kind: IPVK_IndirectCallTarget\n"
            "1\n"
            "1\n"
            "../../../../engine/hw/arm/s5l8950.c;irq_cb:99\n\n"
            "/home/ci/engine/target/arm/tcg/decode-vfp.c.inc:disas_vfp\n"
            "789\n"
            "1\n"
            "5\n"
        )
        prefixes, matched = PGO.detect_source_prefixes(raw_text, self.source)
        self.assertEqual(prefixes, {"../../../../engine/", "/home/ci/engine/"})
        self.assertEqual(
            matched,
            {
                "accel/tcg/cpu-exec.c",
                "hw/arm/s5l8950.c",
                "target/arm/tcg/decode-vfp.c.inc",
            },
        )
        rewritten = PGO.rewrite_profdata_text(raw_text, prefixes, "../../qemu-ios/")
        self.assertIn("# Comment mentioning ../../../../engine/accel/tcg/cpu-exec.c", rewritten)
        self.assertIn("../../qemu-ios/accel/tcg/cpu-exec.c:tb_lookup", rewritten)
        self.assertIn("../../qemu-ios/hw/arm/s5l8950.c;irq_cb:99", rewritten)
        self.assertIn("../../qemu-ios/target/arm/tcg/decode-vfp.c.inc:disas_vfp", rewritten)
        self.assertNotIn("/home/ci/engine/", rewritten)
        # Non-comment lines should not keep the old prefix.
        non_comment = [l for l in rewritten.splitlines() if not l.startswith("#")]
        self.assertFalse(any("../../../../engine/" in l for l in non_comment))

    def test_prepare_extracts_aligns_and_caches(self):
        fake_tool = self.root / "llvm-profdata"
        fake_tool.write_text(FAKE_LLVM_PROFDATA, encoding="utf-8")
        fake_tool.chmod(0o755)

        prof_text = (
            ":ir\n"
            "qemu_ios_main\n"
            "../../../../engine/accel/tcg/cpu-exec.c:tb_lookup\n"
            "../../../../engine/hw/arm/s5l8950.c:tick\n"
        ).encode("utf-8")
        archive = self.root / "engine-arm64-pgo-v2.tar.gz"
        sha = make_tar_gz(archive, {"builds/backend/pgo/engine-arm64.profdata": prof_text})
        out = self.root / "ci/pgo/engine-arm64.profdata"
        meta_path = self.root / "ci/pgo/metadata.json"
        summary_path = self.root / "ci/pgo/summary.txt"

        args = PGO.parse_args(
            [
                "--input",
                str(archive),
                "--sha256",
                sha,
                "--output",
                str(out),
                "--metadata",
                str(meta_path),
                "--summary-file",
                str(summary_path),
                "--llvm-profdata",
                str(fake_tool),
                "--source-dir",
                str(self.source),
                "--build-dir",
                str(self.build_dir),
            ]
        )
        meta = PGO.prepare(args)
        self.assertEqual(meta["target_prefix"], "../../qemu-ios/")
        self.assertEqual(meta["remapped_from"], ["../../../../engine/"])
        self.assertEqual(meta["matched_static_functions"], 2)
        self.assertEqual(meta["matched_source_files"], 2)
        self.assertIn("../../qemu-ios/accel/tcg/cpu-exec.c:tb_lookup", out.read_text(encoding="utf-8"))
        self.assertIn("enabled", summary_path.read_text(encoding="utf-8"))

        # Second invocation with identical metadata uses cache without even reading fake_tool.
        fake_tool.unlink()
        cached_meta = PGO.prepare(args)
        self.assertEqual(cached_meta["aligned_sha256"], meta["aligned_sha256"])

    def test_already_aligned_local_input_caches_without_sha256(self):
        fake_tool = self.root / "llvm-profdata"
        fake_tool.write_text(FAKE_LLVM_PROFDATA, encoding="utf-8")
        fake_tool.chmod(0o755)
        prof = self.root / "local.profdata"
        prof.write_text(
            ":ir\n../../qemu-ios/accel/tcg/cpu-exec.c:tb_lookup\n",
            encoding="utf-8",
        )
        out = self.root / "obj/pgo/aligned.profdata"
        args = PGO.parse_args(
            [
                "--input",
                str(prof),
                "--output",
                str(out),
                "--llvm-profdata",
                str(fake_tool),
                "--source-dir",
                str(self.source),
                "--build-dir",
                str(self.build_dir),
            ]
        )
        meta = PGO.prepare(args)
        self.assertEqual(meta["remapped_from"], [])
        self.assertEqual(meta["matched_static_functions"], 1)
        fake_tool.unlink()
        cached = PGO.prepare(args)
        self.assertEqual(cached["aligned_sha256"], meta["aligned_sha256"])

    def test_rejects_wrong_sha256_and_unsafe_tar(self):
        fake_tool = self.root / "llvm-profdata"
        fake_tool.write_text(FAKE_LLVM_PROFDATA, encoding="utf-8")
        fake_tool.chmod(0o755)
        archive = self.root / "bad.tar.gz"
        make_tar_gz(archive, {"../escape.profdata": b":ir\n"})
        out = self.root / "ci/pgo/out.profdata"

        with self.assertRaises(SystemExit):
            PGO.prepare(
                PGO.parse_args(
                    [
                        "--input",
                        str(archive),
                        "--sha256",
                        "0" * 64,
                        "--output",
                        str(out),
                        "--llvm-profdata",
                        str(fake_tool),
                        "--source-dir",
                        str(self.source),
                        "--build-dir",
                        str(self.build_dir),
                    ]
                )
            )
        real_sha = hashlib.sha256(archive.read_bytes()).hexdigest()
        with self.assertRaisesRegex(SystemExit, "unsafe archive path"):
            PGO.prepare(
                PGO.parse_args(
                    [
                        "--input",
                        str(archive),
                        "--sha256",
                        real_sha,
                        "--output",
                        str(out),
                        "--llvm-profdata",
                        str(fake_tool),
                        "--source-dir",
                        str(self.source),
                        "--build-dir",
                        str(self.build_dir),
                    ]
                )
            )

    def test_rejects_unmatched_profile_by_default(self):
        fake_tool = self.root / "llvm-profdata"
        fake_tool.write_text(FAKE_LLVM_PROFDATA, encoding="utf-8")
        fake_tool.chmod(0o755)
        prof = self.root / "empty.profdata"
        prof.write_text(":ir\nonly_global\n", encoding="utf-8")
        out = self.root / "ci/pgo/out.profdata"
        with self.assertRaisesRegex(SystemExit, "matched files"):
            PGO.prepare(
                PGO.parse_args(
                    [
                        "--input",
                        str(prof),
                        "--output",
                        str(out),
                        "--llvm-profdata",
                        str(fake_tool),
                        "--source-dir",
                        str(self.source),
                        "--build-dir",
                        str(self.build_dir),
                    ]
                )
            )

    def test_workflow_contract(self):
        perf = (ROOT / ".github/workflows/android-perf-apk.yml").read_text(encoding="utf-8")
        jb = (ROOT / ".github/workflows/jailbreak-apk.yml").read_text(encoding="utf-8")
        self.assertIn("engine-arm64-pgo-v2.tar.gz", perf)
        self.assertIn("13a1a4a45fcc3927f76e294c08234114b6f3c3a735bb1948ad3f127ce812272f", perf)
        self.assertIn("Android/scripts/prepare-engine-pgo.py", perf)
        self.assertIn("steps.pgo.outputs.key", perf)
        self.assertIn("-fprofile-use=", perf)
        self.assertIn("-Wl,-z,keep-text-section-prefix", perf)
        self.assertIn(r"\.text\.(hot|unlikely)", perf)
        self.assertIn("engine PGO:", perf)
        self.assertIn('engine_pgo: "true"', jb)


if __name__ == "__main__":
    unittest.main()
