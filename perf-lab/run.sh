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
adb install -r -g "$APK"

# First start: the app makes its own folders and settings.
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 20
adb exec-out screencap -p > "$OUT/01_first_start.png"
adb shell am force-stop "$PKG"

# Purple like the README (theme colour 2 = purple), written into the app's settings file.
adb root
sleep 3
SETTINGS=/data/data/$PKG/files/datastore/settings.preferences_pb
adb pull "$SETTINGS" /tmp/settings.pb || true
python3 perf-lab/prefs.py /tmp/settings.pb themeIndex=2
adb push /tmp/settings.pb /data/local/tmp/settings.pb
adb shell "mkdir -p /data/data/$PKG/files/datastore && cp /data/local/tmp/settings.pb $SETTINGS && chown -R \$(stat -c %u /data/data/$PKG):\$(stat -c %u /data/data/$PKG) /data/data/$PKG/files/datastore && restorecon -R /data/data/$PKG/files/datastore"

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

# Clean status bar (full battery, 12:00) for screenshots
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
python3 perf-lab/drive.py "$PKG" "$OUT" "${LAB_MODE:-scenarios}" || true
adb logcat -d -t 400 -s AndroidRuntime:E > "$OUT/crashes.txt" || true
