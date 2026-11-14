#!/usr/bin/env bash
# Runs inside the emulator (see .github/workflows/perf-lab.yaml). Results go to perf-lab/out/.
set -x
PKG=com.ay.briefyou.nightly
OUT=perf-lab/out
mkdir -p "$OUT"
APK=$(ls app/build/outputs/apk/github/nightly/*.apk | head -1)

adb wait-for-device
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb install -r "$APK"

# First start: the app makes its own folders and settings.
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 20
adb exec-out screencap -p > "$OUT/01_first_start.png"
adb shell am force-stop "$PKG"

# Put the generated database in place of the app's own.
adb root
sleep 3
DIR=/data/data/$PKG/databases
UID_APP=$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')
adb push perf-lab/Reader.db /data/local/tmp/Reader.db
adb shell "mkdir -p $DIR && rm -f $DIR/Reader $DIR/Reader-wal $DIR/Reader-shm && cp /data/local/tmp/Reader.db $DIR/Reader && chown $UID_APP:$UID_APP $DIR/Reader && chmod 660 $DIR/Reader && restorecon $DIR/Reader"

adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 15
adb exec-out screencap -p > "$OUT/02_seeded.png"
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml "$OUT/02_seeded.xml"
adb shell dumpsys activity activities | grep -m3 -i "topResumedActivity\|mResumedActivity" > "$OUT/activity.txt"

python3 perf-lab/drive.py "$PKG" "$OUT" || true
adb logcat -d -t 400 -s AndroidRuntime:E > "$OUT/crashes.txt" || true
