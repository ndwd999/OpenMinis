#!/usr/bin/env python3
"""Pre-flight for `:app:compileDebugUnitTestKotlin`.

The test source set was never compiled in CI, so it carried references to
symbols the source cleanup had already removed — a whole CI round trip was
spent discovering four of them. This checks the one class of error that
actually occurred: a named argument that no longer exists on the target
constructor.

It is deliberately narrow. It does NOT try to type-check; it collects the
constructor parameter lists of the provider classes the tests instantiate and
reports named arguments that are not in them. That is enough to catch the
regression at hand without pretending to be a compiler.

Usage: scripts/check_test_ctor_args.py
Exit 0 = no suspects, 1 = suspects found.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/android/app/src"
MAIN = SRC / "main/java/com/yujian/minis"
TEST_DIRS = [SRC / "test/java/com/yujian/minis", SRC / "androidTest/java/com/yujian/minis"]

# Constructor-bearing classes worth checking, and the file that declares each.
TARGETS = {
    "OpenAIProvider": MAIN / "provider/openai/OpenAIProvider.kt",
    "AnthropicProvider": MAIN / "provider/anthropic/AnthropicProvider.kt",
    "GeminiProvider": MAIN / "provider/gemini/GeminiProvider.kt",
}


def ctor_params(src: str, cls: str) -> set:
    """All constructor parameter names declared for `cls` (primary + secondary).

    Union across constructors on purpose: a call site may legitimately use the
    secondary (public) constructor, and this check must not flag its params as
    unknown just because the primary constructor spells them differently.
    """
    names = set()
    # primary constructor: `class X(...)` up to the matching paren
    m = re.search(r"\bclass\s+" + re.escape(cls) + r"\s*(?:@[^\n]*\n\s*)?\(", src)
    if m:
        i = src.index("(", m.start())
        depth, end = 0, i
        while end < len(src):
            if src[end] == "(":
                depth += 1
            elif src[end] == ")":
                depth -= 1
                if depth == 0:
                    break
            end += 1
        body = _strip(src[i:end])
        names |= set(re.findall(r"\b(?:private\s+|internal\s+|override\s+)*"
                                r"(?:val|var)\s+([A-Za-z_]\w*)\s*:", body))
    # secondary constructors: `constructor(...)`
    for m in re.finditer(r"\bconstructor\s*\(", src):
        i = src.index("(", m.start())
        depth, end = 0, i
        while end < len(src):
            if src[end] == "(":
                depth += 1
            elif src[end] == ")":
                depth -= 1
                if depth == 0:
                    break
            end += 1
        body = _strip(src[i:end])
        names |= set(re.findall(r"^\s*([A-Za-z_]\w*)\s*:", body, re.M))
    return names


def _strip(s: str) -> str:
    s = re.sub(r"//[^\n]*", "", s)
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
    return s


failures = []
known = {}
for cls, path in TARGETS.items():
    if not path.exists():
        failures.append(f"declaring file missing: {path}")
        continue
    known[cls] = ctor_params(path.read_text(encoding="utf-8"), cls)
    print(f"  {cls}: {len(known[cls])} params")

print()

# Only inspect files that reference a target class at all.
for test_dir in TEST_DIRS:
    if not test_dir.is_dir():
        continue
    for kt in sorted(test_dir.rglob("*.kt")):
        src = kt.read_text(encoding="utf-8")
        if "OAuth" in src or "oauthTokenProvider" in src or "isOAuth" in src:
            bare = _strip(src)
            # A `Cls(` call site whose args name something not in Cls's ctors.
            for cls, allowed in known.items():
                for m in re.finditer(r"(?<![A-Za-z0-9_.])" + re.escape(cls) + r"\s*\(", bare):
                    i = m.end()
                    depth, end = 1, i
                    while end < len(bare) and depth:
                        if bare[end] == "(":
                            depth += 1
                        elif bare[end] == ")":
                            depth -= 1
                        end += 1
                    args = set(re.findall(r"(?:^|[,(])\s*([A-Za-z_]\w*)\s*=(?!=)", bare[i:end]))
                    bad = sorted(a for a in args if a not in allowed)
                    if bad:
                        line = bare[:m.start()].count("\n") + 1
                        failures.append(f"{kt.relative_to(SRC)}:{line} {cls}(...) unknown: {bad}")

if failures:
    print("SUSPECTS:")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("OK — no dangling constructor arguments in the test source sets")
