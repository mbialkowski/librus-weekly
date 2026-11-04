# Librus Weekly — Android

A Jetpack Compose scaffold. **The Librus API client is not written yet** — login is a placeholder and the week screen only shows a TODO message. Contributions welcome.

## What's here

- Gradle / Compose project (`com.example.librusweekly`), min SDK 26, compile SDK 35.
- **Login screen** — collects the Synergia login + password and routes to the week screen. Does not talk to Librus.
- **Week screen** — placeholder.
- **Settings** — language picker (System / English / Polski) with strings for both `values/` and `values-pl/`.
- Light theme only, matching the iOS app.

## What's missing

- A Kotlin port of the Librus / Synergia client (login, session renewal, grades, timetable, events, announcements, homework, lucky number). The iOS app uses the Swift `LibrusKit` package; the Android equivalent would use Jsoup + OkHttp (both already declared in `app/build.gradle.kts`).
- Encrypted credential storage via Jetpack Security (`androidx.security:security-crypto`, already declared).
- The `WeekScreen` composable — list sections for grades, cancelled lessons, days, announcements, and a side-by-side layout for multiple children.
- Multi-child support (add / remove / switch) and offline caching through DataStore.
- Signing config for release builds.

## Build

1. Open the `android/` folder in Android Studio and let it sync. The first sync will fetch Gradle and the dependencies.
2. If you prefer the terminal and haven't opened Android Studio yet, create the Gradle wrapper once:
   ```bash
   cd android
   gradle wrapper --gradle-version 8.10.2
   ```
   (needs `gradle` on `PATH` — `brew install gradle` on macOS.)
3. Then:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK lands in `app/build/outputs/apk/debug/`.

## Project layout

```
android/
  build.gradle.kts            root
  settings.gradle.kts
  gradle.properties
  app/
    build.gradle.kts
    proguard-rules.pro
    src/main/
      AndroidManifest.xml
      java/com/example/librusweekly/
        MainActivity.kt       entry point
        ui/AppRoot.kt         login + week + settings screens
        ui/theme/Theme.kt     light colour scheme
      res/values/strings.xml    English
      res/values-pl/strings.xml Polish
```

## Where to help

The most useful thing is porting `ios/Packages/LibrusKit/` from Swift to Kotlin:

- **Auth** (`Auth/`) — OAuth-style login against `portal.librus.pl`, session renewal against `synergia.librus.pl`.
- **API** (`API/`) — JSON parsers for grades, subjects, events, announcements.
- **Synergia** (`Synergia/`) — HTML scrapers for the timetable, homework and lucky number (use Jsoup).
- **Models** (`Models/`) — plain data classes.

Keep the Kotlin port under a GPL-3.0 licence, mirroring the Swift package.
