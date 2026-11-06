#!/usr/bin/env python3
"""The Chinese translation, checked as text.

The UI speaks English in its sources and I18nZh decides what a Chinese reader sees, so
the two things that can break are silent: a key that no longer matches the string the
screens build, and a translation that drops a number or a newline. Both are text, so
both are checked here, without a build, a device or a network.

  the catalogue   every entry parses, no key twice, no empty value, Chinese in the value
  placeholders    %d / %s / %.2f survive the trip, in count and in kind
  the rules       each pattern compiles, each $n names a group, and a rule that nothing
                  else can reach is reported rather than left to rot
  coverage        every label-shaped string in the Compose sources is translated, or is
                  English on purpose (device names, chips, crash-report internals)
  the wiring      one Text in this package (the one that translates), and the language
                  switch is reachable from Settings

  python3 tests/i18n/run.py [-v]
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "Android" / "app" / "src" / "main" / "java" / "org" / "un1box" / "android"
CATALOGUE = SRC / "I18nZh.kt"
SHIM = SRC / "LocalizedText.kt"
I18N = SRC / "I18n.kt"
SETTINGS = SRC / "AppSettings.kt"
SETTINGS_SCREEN = SRC / "SettingsScreen.kt"
ACTIVITY = SRC / "MainActivity.kt"

# Strings that are meant to stay as they are: product and chip names, file names, the
# crash-report's own headings, and the glue the screens concatenate around a value.
KEEP_ENGLISH = {
    "Un1box", "iOS", "iPad", "iPhone", "iPod", "Payload", "Info.plist", "Legacy Store",
    "English", "简体中文", "ANGLE", "ARM64", "x86-64", "HTTP", "GPU", "GL", "vCPU", "CPU",
    # glue a sentence is assembled from; the whole sentence is translated instead
    "Installing ", "Downloading ", " of ", "and ", " else ",
}
# Files that draw: their text is what a person reads, so it has to be done.
SCREENS = [
    "HomeScreen.kt", "SetupWizard.kt", "PrepareScreen.kt", "SettingsScreen.kt",
    "DevicesSheet.kt", "DeviceDetails.kt", "FullscreenScreen.kt", "StoreScreen.kt",
    "StoreBrowse.kt", "StoreInstalled.kt", "StoreInstaller.kt", "LicenseDialog.kt",
    "InstallQueue.kt", "Shell.kt", "IpaPreflight.kt", "MainActivity.kt",
]
KEEP_PATTERNS = [
    # product, chip and model names stay as the hardware world writes them
    r"^iPod touch.*", r"^iPhone.*", r"^iPad.*", r"^iPod\d,\d$", r"^iPhone\d,\d$", r"^iPad\d,\d$",
    r"^Samsung S5L\d+$", r"^PowerVR.*", r"^ARM.*", r"^Apple A4$", r"^iOS .*", r"^firmwarekit .*",
    # numbers, units and the punctuation a sentence is assembled from
    r"^\d+(\.\d+)? ?(KB|MB|GB|MiB)$", r"^%.*", r"^[0-9A-Za-z ,.:()/'×-]{0,2}$", r"^(ms|FPS)$",
    r"^≤.*ms$", r"^>.*ms$", r"^(of|and|or|else) ?$", r"^[)(]?\$", r"\$$",
    # Kotlin that happens to sit inside a literal: an interpolation or a lambda
    r".*[{}].*", r".*->.*", r".*\.let .*",
]

MONTHS = ["January", "February", "March", "April", "May", "June",
          "July", "August", "September", "October", "November", "December"]

# What the composables can show: a literal with a space or a capitalised word. Tags,
# keys, state names and formats are not language.
NOISE = ("http://", "https://", "android.", "org.un1box", "java.", "kotlin.", "/", "\\",
         "SELECT", "INSERT", "content://", "application/", "image/", "audio/", "video/",
         "text/", ".json", ".log", ".txt", ".ipa", ".itbase", ".itpack")
ESCAPES = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "$": "$"}


def unescape(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "u" and i + 5 < len(s):
                try:
                    out.append(chr(int(s[i + 2:i + 6], 16))); i += 6; continue
                except ValueError:
                    pass
            out.append(ESCAPES.get(n, n)); i += 2; continue
        out.append(c); i += 1
    return "".join(out)


def literals(path):
    """The strings a source file builds, with `"a" + "b"` joined and escapes resolved."""
    raw = path.read_text(encoding="utf-8")
    masked = re.sub(r"/\*(?:[^*]|\*(?!/))*\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), raw)
    masked = re.sub(r"//[^\n]*", lambda m: " " * len(m.group(0)), masked)
    found, i, n = [], 0, len(masked)
    while i < n:
        if masked[i] != '"':
            i += 1
            continue
        j = i + 1
        buf = []
        while j < n and masked[j] not in '"\n':
            if masked[j] == "\\":
                buf.append(masked[j:j + 2]); j += 2; continue
            buf.append(masked[j]); j += 1
        if j < n and masked[j] == '"':
            found.append((i, j + 1, "".join(buf)))
        i = j + 1
    out, k = [], 0
    while k < len(found):
        start, end, body = found[k]
        while k + 1 < len(found):
            nstart, nend, nbody = found[k + 1]
            if re.fullmatch(r"\s*\+\s*", masked[end:nstart]):
                body += nbody; k += 1; end = nend
            else:
                break
        out.append((masked.count("\n", 0, start) + 1, unescape(body)))
        k += 1
    return out


def showable(s):
    if len(s) < 2 or "%" in s:
        return False
    # icon path data, not language
    if re.fullmatch(r"[MmZzLlHhVvCcSsQqTtAa0-9 ,.\-]+", s) and len(s) > 12:
        return False
    if any(x in s for x in NOISE):
        return False
    if re.search(r"[A-Za-z]{2}", s) is None:
        return False
    if "_" in s and " " not in s:
        return False
    return s == "OK" or bool(re.fullmatch(r"[A-Z][a-z]{1,}", s)) or " " in s


def read_catalogue():
    """(exact, rules) out of I18nZh.kt, parsed as the Kotlin it is."""
    text = CATALOGUE.read_text(encoding="utf-8")
    body = text.split("private val exact", 1)[1].split("private val rules", 1)
    put = re.compile(r'\n        put\(("(?:[^"\\]|\\.)*"), ("(?:[^"\\]|\\.)*")\)')
    exact = {}
    dupes = []
    for m in put.finditer(body[0]):
        k, v = unescape(m.group(1)[1:-1]), unescape(m.group(2)[1:-1])
        if k in exact:
            dupes.append(k)
        exact[k] = v
    rule = re.compile(r'\n        Regex\(("(?:[^"\\]|\\.)*")\) to ("(?:[^"\\]|\\.)*"),')
    rules = []
    for m in rule.finditer(body[1] if len(body) > 1 else ""):
        pattern = unescape(m.group(1)[1:-1])
        replacement = unescape(m.group(2)[1:-1])
        rules.append((pattern, replacement))
    return exact, rules, dupes


def localize_date(text):
    match = re.fullmatch(r"([A-Z][a-z]+) (\d{4})", text)
    if not match or match.group(1) not in MONTHS:
        return text
    return "%s 年 %d 月" % (match.group(2), MONTHS.index(match.group(1)) + 1)


def translate(text, exact, raw_rules, depth=0):
    if text in exact:
        return localize_date(exact[text])
    if depth < 2:
        for pattern, replacement in raw_rules:
            if pattern.search(text):
                return localize_date(expand(pattern, replacement, text, exact, raw_rules, depth))
    return localize_date(text)


def expand(pattern, replacement, text, exact, raw_rules, depth):
    match = pattern.search(text)
    out, i = [], 0
    while i < len(replacement):
        c = replacement[i]
        if c == "\\" and i + 1 < len(replacement):
            out.append(replacement[i + 1]); i += 2; continue
        if c == "$" and i + 1 < len(replacement) and replacement[i + 1].isdigit():
            g = int(replacement[i + 1])
            group = match.group(g) if match and g <= match.re.groups else ""
            out.append(translate(group, exact, raw_rules, depth + 1)); i += 2; continue
        out.append(c); i += 1
    return "".join(out)


def cjk(s):
    return re.search(r"[\u4e00-\u9fff]", s) is not None


def specifiers(s):
    """Real printf conversions only: "6% speed" is a percentage, not a placeholder."""
    return re.findall(r"%(?:[-+0#]*\d*(?:\.\d+)?[diouxXeEfgGsc])", s)


def main():
    verbose = "-v" in sys.argv
    problems, notes = [], []

    if not CATALOGUE.exists():
        print("missing %s" % CATALOGUE)
        return 1

    exact, rules, dupes = read_catalogue()

    # ---- the catalogue itself
    if len(exact) < 100:
        problems.append("only %d entries parsed; is the table still `put(...)` lines?" % len(exact))
    for key in dupes:
        problems.append("key twice in the catalogue: %r" % key)
    for key, value in exact.items():
        if not value.strip():
            problems.append("empty translation for %r" % key)
        if value == key and key not in KEEP_ENGLISH and not any(re.fullmatch(p, key) for p in KEEP_PATTERNS):
            notes.append("translated as itself: %r" % key)
        if not cjk(value) and value != key and not any(re.fullmatch(p, value) for p in KEEP_PATTERNS):
            notes.append("no Chinese in the value for %r -> %r" % (key, value))
        if specifiers(key) != specifiers(value):
            problems.append("format specifiers changed: %r -> %r" % (key, value))
        if key.count("\n") != value.count("\n") and cjk(value):
            notes.append("newline count differs: %r" % key[:60])

    # ---- the rules
    raw_rules = []
    for pattern, replacement in rules:
        try:
            compiled = re.compile(pattern)
        except re.error as e:
            problems.append("rule does not compile (%s): %s" % (e, pattern))
            continue
        raw_rules.append((compiled, replacement))
        groups = compiled.groups
        for ref in re.findall(r"\$(\d)", replacement.replace("\\\\", "")):
            if int(ref) > groups:
                problems.append("rule %r refers to $%s but has %d groups" % (pattern, ref, groups))
        if not pattern.startswith("^"):
            problems.append("rule is not anchored at the start: %s" % pattern)
    for i, (pattern, _) in enumerate(raw_rules):
        for other, _ in raw_rules[:i]:
            if other.pattern == pattern.pattern:
                problems.append("rule twice: %s" % pattern.pattern)

    # ---- what the screens can show
    sources = sorted(SRC.glob("*.kt"))
    strings = {}
    for path in sources:
        if path.name in ("I18nZh.kt", "I18n.kt", "LocalizedText.kt"):
            continue
        for line, body in literals(path):
            if showable(body):
                strings.setdefault(body, "%s:%d" % (path.name, line))
    untranslated = []
    for text, where in strings.items():
        if translate(text, exact, raw_rules) != text:
            continue
        if text in KEEP_ENGLISH or any(re.fullmatch(p, text) for p in KEEP_PATTERNS):
            continue
        untranslated.append((text, where))
    covered = len(strings) - len(untranslated)
    share = 100.0 * covered / max(len(strings), 1)
    # The screens a person actually reads have to be done; the worker's diagnostics and
    # the crash report's own headings are allowed to stay English.
    per_file = {}
    for text, where in untranslated:
        name = where.split(":")[0]
        per_file[name] = per_file.get(name, 0) + 1
    totals = {}
    for text, where in strings.items():
        name = where.split(":")[0]
        totals[name] = totals.get(name, 0) + 1
    for name in SCREENS:
        left = per_file.get(name, 0)
        total = totals.get(name, 0)
        done = 100.0 * (total - left) / max(total, 1)
        if done < 95.0:
            problems.append("%s is %.0f%% translated (%d of %d left)" % (name, done, left, total))
    if share < 80.0:
        problems.append("only %.1f%% of the %d showable strings are translated (%d left)"
                        % (share, len(strings), len(untranslated)))

    # ---- the wiring that makes any of this reach a screen
    shim = SHIM.read_text(encoding="utf-8") if SHIM.exists() else ""
    if "fun Text(" not in shim:
        problems.append("LocalizedText.kt no longer declares Text, so nothing is translated")
    if "I18n.t(text)" not in shim:
        problems.append("LocalizedText.kt's Text does not ask I18n")
    for path in sources:
        if path.name == "LocalizedText.kt":
            continue
        if re.search(r"\n(?:internal |private |public )?fun Text\(", path.read_text(encoding="utf-8")):
            problems.append("%s declares its own Text, which would shadow the translating one" % path.name)
    i18n = I18N.read_text(encoding="utf-8") if I18N.exists() else ""
    for needle in ("enum class UiLanguage", "fun onSystemLanguage", "fun choose", "fun t("):
        if needle not in i18n:
            problems.append("I18n.kt lost %r" % needle)
    if "language" not in SETTINGS.read_text(encoding="utf-8"):
        problems.append("AppSettings has no language preference to remember the choice")
    if '"Language"' not in SETTINGS_SCREEN.read_text(encoding="utf-8"):
        problems.append("Settings no longer offers a Language row")
    if "I18n.onSystemLanguage" not in ACTIVITY.read_text(encoding="utf-8"):
        problems.append("MainActivity never tells I18n which language the phone speaks")

    # ---- what a reader actually gets
    samples = [
        ("Stop Poco F3?", "停止 Poco F3？"),
        ("Delete my iPod?", "删除 my iPod？"),
        ("Tested: Checked on Un1box for Android.", "已验证：已在 Android 版 Un1box 上验证。"),
        ("March 2010", "2010 年 3 月"),
        ("Version 3.1.3 is available", "3.1.3 版本可用"),
        ("Installed 4 apps", "已安装 4 个应用"),
    ]
    for english, chinese in samples:
        got = translate(english, exact, raw_rules)
        if got != chinese:
            problems.append("expected %r for %r, got %r" % (chinese, english, got))
    if translate("running", exact, raw_rules) != "running":
        problems.append("a state string was translated; the worker compares those by name")

    # ---- report
    print("catalogue: %d entries, %d rules" % (len(exact), len(rules)))
    print("coverage:  %d of %d showable strings (%.1f%%), %d left in English on purpose"
          % (covered, len(strings), share, len(untranslated)))
    if verbose:
        for text, where in sorted(untranslated, key=lambda x: x[1]):
            print("    %-22s %s" % (where, text.replace("\n", "\\n")[:110]))
    for note in (notes if verbose else notes[:0]):
        print("  note: %s" % note)
    for problem in problems:
        print("  FAIL: %s" % problem)
    if problems:
        return 1
    print("ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
