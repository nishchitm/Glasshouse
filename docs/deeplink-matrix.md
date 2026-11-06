# Deep-link results on a Sony Bravia (Android TV 12)

Checked by hand over adb; rerun `scripts/deeplink-probe.sh <tv-ip>:5555` to fill in the rest.

| Service | Link | Result |
|---|---|---|
| Netflix | `https://www.netflix.com/title/<id>` + `source=30`, cold start (profile picker shown first) | ✅ opens the title |
| Netflix | same, Netflix already running | ✅ opens the title |
| Netflix | `netflix://title/<id>` (cold and warm) | 🏠 app home only |
| Netflix | `https://www.netflix.com/search?q=…` | ❌ not accepted (the app declares only /title, /watch, /browse, /home) |
| Netflix | a title whose Netflix id isn't in the account's region | 🏠 app home only |
