# WaterH Widget (Android)

A home-screen widget for the [waterh_to_garmin](https://github.com/spogulis/waterh_to_garmin)
service. Shows today's water intake against Garmin's **dynamic** hydration goal
(base goal + estimated sweat loss from your activities — the auto-increase your
Fenix applies after workouts), with one-tap manual sync.

```
┌──────────────────────────────────┐
│ 1250 / 2950 ml                ↻  │
│ ████████████░░░░░░░░░░░░░░░░░░░  │
│ 42% · goal +550 sweat · 14:05    │
└──────────────────────────────────┘
```

- **Tap anywhere** on the widget → refresh from the server (`GET /status`).
- **Tap ↻** → run a WaterH → Garmin sync (`GET /sync`), then refresh.
- **Coffee buttons** (black ☕ / white / cappuccino, each with its own icon):
  one tap logs a configurable amount (`GET /add?ml=…`) straight into Garmin.
  Each button can be disabled and its ml amount set in the app's settings.
  Requires the server's delta-based sync (v2+), which never absorbs manual
  Garmin additions into the bottle total. Offline taps fail fast with an
  error on the widget — re-tap when connected; taps are never queued.
- Auto-refreshes every 30 minutes via WorkManager (network required; Doze may
  delay it — tap to force).
- No credentials on the phone: only the server URL and the `SYNC_KEY`.

## Requirements

- The waterh_to_garmin service running on your LAN with the `/status` endpoint
  (added alongside this widget — redeploy the service: `docker compose up -d --build`).
- The phone must reach the server: on home Wi-Fi or through your WireGuard
  tunnel. The app allows cleartext HTTP because the tunnel/LAN provides the
  transport; don't point it at a server across the public internet unless it's
  behind HTTPS.

## Cover displays (Motorola Razr and similar)

The widget deliberately declares **no** `android:configure` activity: cover-
screen widget pickers exclude widgets that require a setup step and ignore
`configuration_optional` (verified on a Razr 60 — dropping `configure` is
what made the widget appear in the external display's widget list; keyguard
category, small minimums, and previewImage alone did not). Settings live in
the app instead. There's also a full-screen dashboard (the launcher activity)
for running the app itself on a cover display.

## Build

Needs JDK 17+ and the Android SDK (set `sdk.dir` in `local.properties`):

```bash
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Install & set up

1. Copy `app-debug.apk` to the phone (or `adb install app-debug.apk`) and
   install it — allow "install unknown apps" for your file manager when asked.
2. Long-press the home screen → **Widgets** → *WaterH Widget* → place it.
   The settings screen opens automatically.
3. Enter the server URL (e.g. `http://10.x.x.x:8000` — your LAN/WireGuard IP)
   and the `SYNC_KEY` from the server's `.env`. Hit **Test**, then **Save**.

Settings can be changed anytime by opening the *WaterH Widget* app.

## How the dynamic goal is computed

Garmin's hydration API reports the base daily goal (`goalInML`) and the
estimated sweat loss (`sweatLossInML`) separately; the effective auto-increased
goal shown in Garmin Connect is their sum. The server's `/status` endpoint
returns both plus the computed `goal_ml`, which is what the widget displays.
Verify once against the Garmin Connect app after a sweaty workout — if your
Connect goal ever differs, the composition rule changed on Garmin's side and
`garmin_status()` in `waterh_to_garmin.py` is the place to adjust.
