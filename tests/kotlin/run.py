#!/usr/bin/env python3
"""The Android sources, read for the mistakes a compiler would find ten minutes into a CI run.

This exists because the same four have cost a build each while the Jailbreak tab was written, all of
them Python habits in Kotlin, and none of them something the text suites would otherwise notice:

  a '#' comment        Kotlin has no comment that starts with a hash
  a, b = x, y          no tuple assignment: each name needs its own '='
  take(n).toString(..)  ByteArray.take gives a List<Byte>, which has no charset toString
  a /* inside a doc     Kotlin's block comments nest, so one glob in a KDoc unclosed the file

  python3 tests/kotlin/run.py [-v]
"""
import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = [ROOT / "Android" / "app" / "src" / "main" / "java",
           ROOT / "Android" / "app" / "src" / "test" / "java"]


class Failure(RuntimeError):
    pass


def files():
    for root in SOURCES:
        if root.is_dir():
            yield from sorted(root.rglob("*.kt"))


def read(path):
    return path.read_text(errors="ignore")


def scan(text):
    """(the code with comment and string bodies blanked, the comments with their line numbers).

    A small lexer rather than a set of regexes: Kotlin's strings carry templates with braces, quotes
    and even comments of their own (`"${a.b("x")}"`), and its block comments nest. A plain regex
    reads a template's inner quote as the string's end and reports mistakes that are not there -- the
    first version of this file did exactly that for JailbreakStore.kt and MainActivity.kt.
    """
    code = []
    comments = []
    index = 0
    line = 1
    length = len(text)

    def blank(count):
        nonlocal index, line
        chunk = text[index:index + count]
        line += chunk.count("\n")
        code.append("".join("\n" if c == "\n" else " " for c in chunk))
        index += count

    def template():
        """Inside a ${...}: braces nest, and strings, chars and comments may appear again."""
        nonlocal index
        depth = 1
        while index < length and depth > 0:
            pair = text[index:index + 2]
            if pair == "/*":
                block_comment()
            elif pair == "//":
                line_comment()
            elif text[index] == '"':
                string()
            elif text[index] == "'":
                character()
            elif text[index] == "{":
                depth += 1
                code.append("{")
                index += 1
            elif text[index] == "}":
                depth -= 1
                code.append("}" if depth else " ")
                index += 1
            else:
                code.append(text[index])
                index += 1

    def string():
        nonlocal index
        if text[index:index + 3] == '"""':
            blank(3)
            while index < length and text[index:index + 3] != '"""':
                blank(1)
            blank(3)
            return
        blank(1)
        while index < length and text[index] != '"':
            if text[index] == "\\":
                blank(2)
            elif text[index:index + 2] == "${":
                blank(2)
                template()
            else:
                blank(1)
        blank(1)

    def character():
        blank(1)
        while index < length and text[index] != "'":
            blank(2 if text[index] == "\\" else 1)
        blank(1)

    def line_comment():
        start = index
        while index < length and text[index] != "\n":
            blank(1)
        comments.append((text[start:index], line - text[start:index].count("\n")))

    def block_comment():
        nonlocal index
        start = index
        depth = 0
        while index < length:
            if text[index:index + 2] == "/*":
                depth += 1
            elif text[index:index + 2] == "*/":
                depth -= 1
                blank(2)
                if depth == 0:
                    comments.append((text[start:index], line - text[start:index].count("\n")))
                    return
                continue
            blank(1)
        comments.append((text[start:index], line - text[start:index].count("\n")))

    while index < length:
        pair = text[index:index + 2]
        if pair == "/*":
            block_comment()
        elif pair == "//":
            line_comment()
        elif text[index] == '"':
            string()
        elif text[index] == "'":
            character()
        elif text[index] == "`":
            # a backtick-quoted name (a test's, usually) may contain an apostrophe, which a scanner
            # that does not know backticks reads as the start of a character literal.
            blank(1)
            while index < length and text[index] != "`":
                blank(1)
            blank(1)
        else:
            code.append(text[index])
            if text[index] == "\n":
                line += 1
            index += 1
    return "".join(code), comments


def read_code(path):
    code, comments = scan(read(path))
    return code, comments


def hash_comments(path):
    code, _ = read_code(path)
    for number, line in enumerate(code.splitlines(), 1):
        if re.match(r"\s*#", line):
            raise Failure(f"{path.relative_to(ROOT)}:{number}: a '#' comment, which Kotlin has not got")
    return None


def tuple_assignments(path):
    code, _ = read_code(path)
    for number, line in enumerate(code.splitlines(), 1):
        if re.match(r"\s*(val|var)?\s*[A-Za-z_][A-Za-z0-9_]*(\s*,\s*[A-Za-z_][A-Za-z0-9_]*)+\s*=[^=]", line):
            raise Failure(f"{path.relative_to(ROOT)}:{number}: a tuple assignment, which Kotlin has not got")
    return None


def byte_array_take(path):
    code, _ = read_code(path)
    for match in re.finditer(r"\.take\((\d+)\)\s*\.toString\(\s*Charset", code):
        line = code[:match.start()].count("\n") + 1
        raise Failure(f"{path.relative_to(ROOT)}:{line}: take(n) gives a List<Byte>; use String(bytes, ...)")
    return None


def nested_comments(path):
    _, found = read_code(path)
    for text, line in found:
        if not text.startswith("/*"):
            continue
        body = text[2:-2]
        if "/*" in body:
            raise Failure(f"{path.relative_to(ROOT)}:{line}: a '/*' inside a block comment: Kotlin's "
                          "comments nest, so this one never closes")
    return None


def balanced(path):
    code, _ = read_code(path)
    for opening, closing in (("{", "}"), ("(", ")"), ("[", "]")):
        if code.count(opening) != code.count(closing):
            raise Failure(f"{path.relative_to(ROOT)}: {code.count(opening)} '{opening}' against "
                          f"{code.count(closing)} '{closing}'")
    return None


CHECKS = [
    ("no '#' comments", hash_comments),
    ("no tuple assignments", tuple_assignments),
    ("no take(n).toString(charset)", byte_array_take),
    ("no '/*' inside a comment", nested_comments),
    ("braces and brackets balance", balanced),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-v", "--verbose", action="store_true")
    options = parser.parse_args()
    checked = list(files())
    if not checked:
        print("no Kotlin sources found")
        return 1
    failures = []
    for name, check in CHECKS:
        broken = []
        for path in checked:
            try:
                check(path)
            except Failure as failure:
                broken.append(str(failure))
        if broken:
            failures += broken
            print(f"  FAIL {name}")
            for one in broken[:5]:
                print(f"       {one}")
        else:
            print(f"  ok   {name} ({len(checked)} files)")
    if failures:
        print(f"\n{len(failures)} Kotlin mistakes in {len(checked)} files")
        return 1
    print(f"\nall {len(CHECKS)} Kotlin checks pass over {len(checked)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
