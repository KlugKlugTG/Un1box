#!/usr/bin/env python3
"""One name, everywhere it has to agree, checked as text.

The app is Un1box. Renaming it touched four things that must match each other
and nothing else can check without a build:

  manifest   the label a launcher shows, and the package it belongs to
  gradle     namespace and applicationId, which decide the package's own name
  sources    every package line against the directory it sits in, plus the AIDL
             interfaces the services are bound through
  JNI        the library the app loads, the target CMake builds, and the C
             symbol names Java's package spelling produces

Any one of these left on the old name is either a build failure or an app whose
services cannot be bound; all four are text, so all four are checked here.

  python3 tests/branding/run.py [-v]
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "Android" / "app"
MANIFEST = APP / "src" / "main" / "AndroidManifest.xml"
GRADLE = APP / "build.gradle.kts"
JNI_CPP = APP / "src" / "main" / "cpp" / "un1box_jni.cpp"
CMAKE = APP / "src" / "main" / "cpp" / "CMakeLists.txt"
NATIVE_KT = APP / "src" / "main" / "java" / "org" / "un1box" / "android" / "Native.kt"

PACKAGE = "org.un1box.android"


class Failure(RuntimeError):
    pass


def read(path):
    if not path.is_file():
        raise Failure(f"{path.relative_to(ROOT)} is missing")
    return path.read_text(errors="ignore")


def require(condition, message):
    if not condition:
        raise Failure(message)


def the_label_is_the_product_name():
    manifest = read(MANIFEST)
    label = re.search(r'android:label="([^"]+)"', manifest)
    require(label is not None, "the manifest names no app label, so the launcher shows the package")
    # The label is a resource because the jailbreak build is a second application beside this one and
    # names itself "Un1box JB" (build.gradle.kts, resValue app_name): what must stay the product name is
    # the default, which is what a launcher shows for the normal build.
    require(label.group(1) == "@string/app_name",
            f'the manifest labels the app "{label.group(1)}": it must be the app_name resource, so a '
            "launcher can tell the jailbreak application from this one")
    gradle = read(GRADLE)
    default = re.search(r'resValue\("string", "app_name", if \(jailbreak\) "[^"]+" else "([^"]+)"\)', gradle)
    require(default is not None, "build.gradle.kts defines no app_name resource, so the label is unresolved")
    require(default.group(1) == "Un1box", f'the launcher would show "{default.group(1)}"')
    require("LightTouch" not in manifest, "the manifest still carries the old product name")


def the_package_is_un1box():
    gradle = read(GRADLE)
    namespace = re.search(r'namespace\s*=\s*"([^"]+)"', gradle)
    application = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
    require(namespace and namespace.group(1) == PACKAGE,
            f"the namespace is {namespace.group(1) if namespace else 'missing'}, not {PACKAGE}")
    require(application and application.group(1) == PACKAGE,
            f"the applicationId is {application.group(1) if application else 'missing'}, not {PACKAGE}")


def every_source_sits_in_its_own_package():
    """A file whose package line disagrees with its directory does not compile."""
    roots = [APP / "src" / part / kind for part in ("main", "debug", "test", "androidTest") for kind in ("java", "aidl")]
    checked, wrong = 0, []
    for root in roots:
        if not root.is_dir():
            continue
        for source in list(root.rglob("*.kt")) + list(root.rglob("*.java")) + list(root.rglob("*.aidl")):
            text = source.read_text(errors="ignore")
            declared = re.search(r"^\s*package\s+([\w.]+)", text, re.M)
            if not declared:
                continue
            expected = ".".join(source.relative_to(root).parent.parts)
            checked += 1
            if declared.group(1) != expected:
                wrong.append(f"{source.relative_to(ROOT)} says {declared.group(1)}, sits in {expected}")
    require(checked > 50, f"only {checked} sources were found; the layout has changed")
    require(not wrong, "a source's package does not match its directory:\n  " + "\n  ".join(wrong[:10]))


def the_native_library_is_built_loaded_and_named_for_the_package():
    native, cmake, cpp = read(NATIVE_KT), read(CMAKE), read(JNI_CPP)
    library = re.search(r'loadLibrary\("([^"]+)"\)', native)
    require(library is not None, "the app loads no native library")
    name = library.group(1)
    require(name == "un1box_jni", f'the app loads "{name}"')
    require(f"add_library({name} SHARED {name}.cpp)" in cmake,
            f"CMake does not build {name} from {name}.cpp")
    expected = "Java_" + PACKAGE.replace(".", "_") + "_"
    require(f"Java_org_un1box_android_" in cpp, "the JNI entry points are not named for the app's package")
    require("Java_org_lighttouch_android_" not in cpp,
            "a JNI entry point is still spelled for the old package, so that method would be unresolved")
    spellings = set(re.findall(r"Java_org_[a-z0-9_]+?_android_", cpp))
    require(all(spelling == expected for spelling in spellings),
            f"a JNI entry point names another package: {sorted(spellings)}")


CHECKS = [
    ("android: the launcher label is Un1box", the_label_is_the_product_name),
    ("android: namespace and applicationId are org.un1box.android", the_package_is_un1box),
    ("android: every source's package matches its directory", every_source_sits_in_its_own_package),
    ("android: the JNI library is built, loaded and named for the package",
     the_native_library_is_built_loaded_and_named_for_the_package),
]


class Report:
    def __init__(self, verbose=False):
        self.rows, self.verbose = [], verbose

    def add(self, name, ok, detail=""):
        self.rows.append((name, ok, detail))
        print(f"  {'ok  ' if ok else 'FAIL'} {name}" + (f" — {detail}" if detail and self.verbose else ""))
        return ok

    def run(self, name, action):
        try:
            return self.add(name, True, action() or "")
        except Failure as failure:
            return self.add(name, False, str(failure))
        except Exception as failure:  # a crash in a check is a failed check
            return self.add(name, False, f"{type(failure).__name__}: {failure}")

    @property
    def failed(self):
        return [row for row in self.rows if not row[1]]


def main():
    report = Report("-v" in sys.argv)
    for name, check in CHECKS:
        report.run(name, check)
    print()
    if report.failed:
        # Also as workflow commands: these checks run with continue-on-error in CI, where a failure is not
        # allowed to withhold an APK -- so the annotation is the only place it is visible.
        for name, _, detail in report.failed:
            print(f"::error title={name}::{detail}")
        print(f"{len(report.failed)} of {len(report.rows)} checks failed:")
        for name, _, detail in report.failed:
            print(f"  {name}: {detail.splitlines()[0] if detail else ''}")
        return 1
    print(f"all {len(report.rows)} branding checks pass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
