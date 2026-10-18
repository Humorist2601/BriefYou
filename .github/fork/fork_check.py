#!/usr/bin/env python3
"""Brief You's fork tools: keeps Brief You's changes to Read You's files small and findable.

  fork_check.py check BASE          Fails if a change to a Read You file has no "Brief You" marker.
                                    (Set FORK_WORKTREE=1 to check uncommitted edits too.)
  fork_check.py report BASE NEW     Markdown merge-risk report for a Read You update (BASE = the
                                    Read You commit Brief You is on, NEW = Read You's latest).

A "Read You file" is any file that exists in Read You (at BASE). Brief You's own files (new
files, such as everything under app/src/main/java/me/ash/reader/brief/) are never checked.
Every changed block (a diff hunk) in a Read You file must mention "Brief You" in the block or
right next to it, so a person resolving a merge conflict can always tell whose line is whose.
"""
import os
import re
import subprocess
import sys

MARKER = "Brief You"
ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()

# Files Brief You replaced as a whole on purpose (no marker possible or useful).
EXEMPT = [
    r"^README\.md$",
    r"^README-.*\.md$",
    r"^\.github/",
    r"^fastlane/",
    r"^signature/",
    r"^app/src/main/res/drawable[^/]*/ic_",
    r"^app/src/main/res/mipmap[^/]*/",
    r"^app/src/main/res/values/ic_launcher_background\.xml$",
    r"^app/src/main/baseline-prof\.txt$",
]


# Compare against the working tree (uncommitted edits) instead of the last commit.
TARGET = [] if os.environ.get("FORK_WORKTREE") else ["HEAD"]


def git(*args):
    return subprocess.run(["git", "-C", ROOT, *args], capture_output=True, text=True, check=True).stdout


def read_you_files(base):
    return set(git("ls-tree", "-r", "--name-only", base).split("\n")) - {""}


def changed_read_you_files(base):
    """Read You files Brief You changed since [base] (modified, not added)."""
    names = git("diff", "--name-only", "--diff-filter=MD", base, *TARGET).split("\n")
    ry = read_you_files(base)
    return [n for n in names if n and n in ry and not any(re.search(p, n) for p in EXEMPT)]


def hunks(base, path, context=2):
    out = git("diff", f"-U{context}", base, *TARGET, "--", path)
    blocks, cur = [], None
    for line in out.split("\n"):
        if line.startswith("@@"):
            cur = [line]
            blocks.append(cur)
        elif cur is not None and not line.startswith(("diff ", "index ", "--- ", "+++ ")):
            cur.append(line)
    return blocks


def copies():
    path = os.path.join(ROOT, ".github/fork/copies.txt")
    result = {}
    if os.path.exists(path):
        for line in open(path):
            line = line.strip()
            if line and not line.startswith("#") and "->" in line:
                a, b = (x.strip() for x in line.split("->"))
                result[a] = b
    return result


def check(base):
    shadowed = copies()
    bad = []
    for path in changed_read_you_files(base):
        if path in shadowed:
            # A replaced file must stay exactly as Read You has it.
            if git("diff", base, *TARGET, "--", path).strip():
                bad.append((path, "changed, but Brief You has its own copy: keep Read You's original as is"))
            continue
        if not os.path.exists(os.path.join(ROOT, path)):
            bad.append((path, "deleted (restore it, or list it as exempt in fork_check.py)"))
            continue
        for h in hunks(base, path):
            changed = [l for l in h[1:] if l.startswith(("+", "-"))]
            if not changed:
                continue
            if not any(MARKER in l for l in h):
                bad.append((path, h[0]))
    if bad:
        print(f"Changes to Read You's files without a '{MARKER}' marker:\n")
        for path, where in bad:
            print(f"- {path}: {where}")
        print(f"\nAdd a comment mentioning '{MARKER}' to each block (for example `// Brief You: why`),")
        print("or move the change into Brief You's own files (app/src/main/java/me/ash/reader/brief/).")
        return 1
    print(f"All changes to Read You's files are marked ({len(changed_read_you_files(base))} files).")
    return 0


def report(base, new):
    """What a Read You update touches that Brief You also changed."""
    upstream = [n for n in git("diff", "--name-only", base, new).split("\n") if n]
    ours = set(changed_read_you_files(base))
    shadowed = copies()
    lines = ["### Merge risk", ""]
    touched = [f for f in upstream if f in ours and f not in shadowed]
    replaced = [f for f in upstream if f in shadowed]
    if not touched and not replaced:
        lines.append("Read You's update doesn't touch any file Brief You changes. Low risk.")
    if touched:
        lines.append("Read You changed files that Brief You also changes (check the marked `Brief You` blocks still do their job):")
        lines.append("")
        for f in touched:
            n = sum(1 for h in hunks(base, f) if any(l.startswith(("+", "-")) for l in h[1:]))
            lines.append(f"- `{f}` ({n} Brief You block{'s' if n != 1 else ''})")
        lines.append("")
    if replaced:
        lines.append("Read You changed files that Brief You replaced with its own copy. Their changes are **not** in Brief You yet: look at them and copy what's needed into Brief You's copy:")
        lines.append("")
        for f in replaced:
            lines.append(f"- `{f}` → `{shadowed[f]}`")
        lines.append("")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "check":
        sys.exit(check(sys.argv[2]))
    if len(sys.argv) >= 4 and sys.argv[1] == "report":
        sys.exit(report(sys.argv[2], sys.argv[3]))
    print(__doc__)
    sys.exit(2)
