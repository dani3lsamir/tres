#!/usr/bin/env bash
# Captures README screenshots on an emulator with synthetic sample notes.
set -euo pipefail
PKG=app.tres.notes
OUT=docs/screenshots
mkdir -p "$OUT" debug
adb install -r app/build/outputs/apk/debug/app-debug.apk

adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter; demo clock -e hhmm 0941; demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide; demo notifications -e visible false

python3 - > notes.json <<'PY'
import json, time, datetime
now = int(time.time() * 1000); day = 86400000
today = datetime.date.today()
nxt = (today.replace(day=1) + datetime.timedelta(days=32)).replace(day=1)
def n(i, text, p=0, due="", rep="none", done=False, age=0):
    ts = now - age
    return dict(id=i, text=text, created=ts, updated=ts, done=done, priority=p, due=due, repeat=rep,
                completedAt=ts if done else 0, anchorDay=int(due[-2:]) if due else 0, nextId="")
notes = [
    n("n1", "Finish the project proposal", 1, today.isoformat(), age=5 * 60000),
    n("n2", "Call the dentist to confirm Thursday's appointment", 2, age=40 * 60000),
    n("n3", "Go for a 30-minute walk", 3, today.isoformat(), "daily", age=2 * 3600000),
    n("n4", "Buy coffee on the way home", age=3 * 3600000),
    n("n5", "Pay rent", due=nxt.isoformat(), rep="monthly", age=day),
    n("n6", "Idea: a weekly review every Sunday evening", age=2 * day),
    n("d1", "Reply to Ana's email", done=True, age=2 * day),
    n("d2", "Water the plants", done=True, age=6 * day),
]
print(json.dumps({"version": 1, "notes": notes}))
PY
adb push notes.json /data/local/tmp/notes.json >/dev/null
adb shell "cat /data/local/tmp/notes.json | run-as $PKG sh -c 'mkdir -p files && cat > files/notes.json'"

shot() { sleep 3; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  adb pull /sdcard/ui.xml "debug/ui-$RANDOM.xml" >/dev/null
  local xy
  xy=$(python3 - "$1" "$(ls -t debug/ui-*.xml | head -1)" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want, path = sys.argv[1], sys.argv[2]
for node in ET.parse(path).iter("node"):
    if node.get("text") == want:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  [ -n "$xy" ] || { echo "text not found: $1"; return 1; }
  adb shell input tap $xy
}
start() { adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity "$@" >/dev/null; sleep 3; }

start; shot 1-priorities
tap_text "Completadas"; shot 2-completed
start --es note_id n1; shot 3-editor
start; tap_text "Fecha y repetición"; shot 4-schedule
adb shell am force-stop $PKG
