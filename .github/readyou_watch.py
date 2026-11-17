"""Weekly look at Read You, the app Brief You is built on (run by readyou-watch.yaml).

Brief You no longer merges Read You's changes. This lists what changed in Read You since the last
review in one GitHub Issue, "Read You changes to look at", so anything worth having (a fix for
account syncing, feed parsing, the database, security) can be copied over by hand. Closing the
Issue marks those changes as reviewed; the next new commits open a new one.

Usage: python3 readyou_watch.py READ_YOU_CLONE REVIEWED_SHA > body.md
Prints nothing when there is nothing new since REVIEWED_SHA.
"""

import re
import subprocess
import sys

clone, reviewed = sys.argv[1], sys.argv[2]


def git(*args: str) -> str:
    return subprocess.run(["git", "-C", clone, *args], capture_output=True, text=True, check=True).stdout


# What matters most to Brief You, by the files a commit touches (and, for security, its words).
AREAS = [
    ("Account syncing", re.compile(r"domain/service/|infrastructure/rss/provider/|SyncWorker|/sync/", re.I)),
    ("Feed parsing", re.compile(r"infrastructure/rss/|RssHelper|rome|Readability|html", re.I)),
    ("Database", re.compile(r"infrastructure/db/|domain/model/|app/schemas/|Migration", re.I)),
    ("Security", re.compile(r"network_security|AndroidManifest|signing|keystore", re.I)),
]
SECURITY_WORDS = re.compile(r"\b(security|CVE-\d+|vulnerab|exploit|leak)", re.I)
TRANSLATION = re.compile(r"(^|/)values-[^/]+/|Weblate|README-|fastlane/metadata", re.I)

shas = git("rev-list", "--reverse", f"{reviewed}..HEAD").split()
if not shas:
    sys.exit(0)

important: dict[str, list[str]] = {name: [] for name, _ in AREAS}
other: list[str] = []
translations = 0
for sha in shas:
    subject = git("log", "-1", "--format=%s", sha).strip()
    date = git("log", "-1", "--format=%cd", "--date=short", sha).strip()
    files = [f for f in git("show", "--name-only", "--format=", sha).split("\n") if f]
    line = f"- {date} [{subject}](https://github.com/ReadYouApp/ReadYou/commit/{sha}) `{sha[:7]}`"
    if files and all(TRANSLATION.search(f) for f in files) or TRANSLATION.search(subject):
        translations += 1
        continue
    hit = False
    for name, pattern in AREAS:
        if any(pattern.search(f) for f in files) or (name == "Security" and SECURITY_WORDS.search(subject)):
            important[name].append(line)
            hit = True
            break
    if not hit:
        other.append(line)

tags = [t for t in git("tag", "--contains", reviewed, "--sort=creatordate").split() if t]
tags = [t for t in tags if git("rev-list", "-n", "1", t).strip() != git("rev-parse", reviewed).strip()]

print(f"Read You has **{len(shas)} new commits** since the last review (`{reviewed[:7]}`).")
print()
print("Nothing is merged into Brief You. Copy over by hand what's worth having, then close this")
print("Issue to mark these as reviewed.")
print()
if tags:
    print("### New releases")
    for t in tags:
        print(f"- [{t}](https://github.com/ReadYouApp/ReadYou/releases/tag/{t})")
    print()
for name, lines in important.items():
    if lines:
        print(f"### {name}")
        print("\n".join(lines))
        print()
if other:
    print("### Everything else")
    print("\n".join(other[:60]))
    if len(other) > 60:
        print(f"- …and {len(other) - 60} more")
    print()
if translations:
    print(f"Also {translations} translation or documentation commits (not listed).")
    print()
print(f"<!-- reviewed-up-to: {shas[-1]} -->")
