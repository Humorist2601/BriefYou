#!/bin/sh
# Posts the sample digest (DigestPreviewReceiver, Nightly only) in both styles, light and dark,
# and screenshots the notification shade. Run by notification-preview.yml on an emulator.
set -x
PKG=com.ay.briefyou.nightly
RECEIVER=$PKG/me.ash.reader.brief.notifications.DigestPreviewReceiver
APK=$(ls app/build/outputs/apk/github/nightly/*.apk | head -1)
mkdir -p shots
adb install -r -g "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
# Start the app once (an app never opened doesn't receive broadcasts), then go home.
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 8
adb shell input keyevent KEYCODE_HOME
sleep 2
SIZE=$(adb shell wm size | sed 's/.*: //' | tr -d '\r')
W=${SIZE%x*}; H=${SIZE#*x}
for mode in no yes; do
    adb shell cmd uimode night $mode
    sleep 3
    for style in conversation list; do
        adb shell cmd statusbar collapse
        adb shell am broadcast -f 32 -n $RECEIVER --es style $style
        sleep 4
        adb shell cmd statusbar expand-notifications
        sleep 3
        adb exec-out screencap -p > shots/$style-$([ $mode = yes ] && echo dark || echo light)-1.png
        # Drag the notification down to expand it fully.
        adb shell input swipe $((W / 2)) $((H * 22 / 100)) $((W / 2)) $((H * 70 / 100)) 400
        sleep 3
        adb exec-out screencap -p > shots/$style-$([ $mode = yes ] && echo dark || echo light)-2.png
        adb shell cmd statusbar collapse
        sleep 1
    done
done
ls -la shots
