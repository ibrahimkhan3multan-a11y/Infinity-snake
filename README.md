# INFINITY SNAKE

An original, fully offline Slither-style snake arena game for Android (Kotlin, Jetpack Compose + Material 3, Canvas rendering).

- Portrait and landscape, with auto-rotation that keeps the running game (score, snake, food, bots)
- 14 offline AI bots in three difficulty levels, plus a bot-free Solo Mode
- Touch-and-drag or virtual joystick steering, big boost button, adjustable sensitivity; mouse and keyboard (A/D, arrows, Space) also work
- Skins unlocked with coins earned in-game; settings, high score, best rank and skins saved locally
- No ads, no analytics, no accounts, **no INTERNET permission** (only VIBRATE)

## Build

    ./gradlew testDebugUnitTest      # engine unit tests
    ./gradlew :app:assembleDebug     # APK -> app/build/outputs/apk/debug/

## CI

`.github/workflows/build-apk.yaml` runs unit tests, builds the debug APK and uploads it as the
`infinity-snake-debug-apk` artifact. A second job runs the instrumented UI tests
(`app/src/androidTest`) on an API 30 emulator and uploads screenshots as `emulator-test-output`.

## Layout

- `app/.../game`: pure-Kotlin simulation (World, Snake, BotBrain, Skins), unit tested
- `app/.../ui`: Compose screens and the Canvas renderer
- `app/.../data`, `audio`: local settings and synthesized sound (no audio files)
