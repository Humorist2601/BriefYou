# Perf lab (a throw-away branch)

Everything here lives on the `perf-lab` branch only and is never merged into main. Delete the
branch to remove it all: `git push origin --delete perf-lab`.

It builds the Nightly variant, starts an Android emulator in GitHub Actions, puts a generated
database with thousands of articles into the app, drives the screens (open an article, a feed to
its article list, Triage switches) and reports frame timings and what the main thread was doing.

Files:
- `.github/workflows/perf-lab.yaml`: the workflow (runs on pushes to `perf-lab`, or by hand).
- `make_db.py`: makes the test database (Room schema version 7).
- `run.sh`: installs the app, seeds the database, runs `drive.py`, collects results.
- `drive.py`: taps through the screens and records timings.
Nothing is published: no release, no Nightly. Results are kept as a run artifact.
