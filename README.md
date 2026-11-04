# Librus Weekly

A weekly view of your kids' Librus Synergia week on your own phone — grades added that week, the timetable with changes, tests and events on the calendar, the lucky number, and school announcements. Fetches straight from Librus; passwords stay in the iOS Keychain (or Android Keystore).

Build and run it yourself — nothing to install from a store, no third-party server in the loop.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/single.svg" alt="One child's week view" width="300"/></td>
    <td align="center"><img src="docs/screenshots/side-by-side.svg" alt="Side-by-side view of two children" width="300"/></td>
  </tr>
  <tr>
    <td align="center"><sub>One child's week</sub></td>
    <td align="center"><sub>Side-by-side view for siblings</sub></td>
  </tr>
</table>

## What it shows

- **Weekly summary** — grades added that week, tests and calendar events, the lucky number, cancelled lessons and substitutions.
- **Multiple children** — one Synergia login per child, switch between them from the menu, or view all of them side by side in columns.
- **"What changed"** — pull-to-refresh fetches fresh data and tells you what's new since the last update; new items get a NEW badge.
- **Offline-first** — cached on the phone, so the app opens with what it last downloaded and doesn't go to Librus unless you ask.
- **Polish or English UI** — pick one in Settings, or follow the system language.
- **Portrait and landscape**, light theme.

## Prerequisites

### iOS

- A Mac with **Xcode 16 or newer**.
- An **Apple Developer Program** membership (paid). TestFlight distribution needs it; running on your own phone also needs it beyond the 7-day free-signing window.
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`.

### Android

- **Android Studio** (any recent version).
- JDK 17+ (Android Studio bundles one).
- The Librus client itself is **not implemented yet** — the Android app currently has the shell, login screen, and settings but no real data. Help wanted. See [`android/README.md`](android/README.md).

## Build for iOS

### 1. Signing

In Xcode, open **Settings → Accounts** and add your Apple ID.

Edit `ios/Config/Signing.xcconfig`:
- `DEVELOPMENT_TEAM`: your 10-character Team ID (Xcode → Settings → Accounts, or developer.apple.com → Account → Membership details).
- `BUNDLE_ID`: a unique identifier, e.g. `com.yourname.librusweekly`.

### 2. Generate and open the project

```bash
cd ios
xcodegen generate
open LibrusSummary.xcodeproj
```

Pick your iPhone or a simulator and press **Run**. Xcode downloads SwiftSoup (a Swift Package dependency) the first time.

### 3. Log in to your children

On first launch the app shows a **Log in** screen. Enter one child's Synergia login (`1234567u`) and the parent password. Use the **Add child** button in the top-left menu to add more.

Passwords are stored in the iOS Keychain, on-device only.

### 4. (Optional) TestFlight

If you want to install it on more devices without plugging them in:

1. At [App Store Connect](https://appstoreconnect.apple.com), **Apps → + → New App** with the same Bundle ID. Platform: iOS. Name: anything unique across the App Store.
2. In Xcode, **Product → Archive**, then in the Organizer **Distribute App → TestFlight Internal Only**. Or from the terminal:
   ```bash
   cd ios && ./scripts/upload-testflight.sh
   ```
3. In App Store Connect, under **TestFlight → Internal Testing**, create a group and add testers. Internal testers skip Beta App Review.
4. TestFlight builds expire after 90 days — upload a new one before that.

**Keep it to internal testers.** Librus has objected to third-party Librus apps in public stores.

## Build for Android

See [`android/README.md`](android/README.md). Short version: the project builds and runs as a Compose shell with a login screen placeholder and a Settings screen. The Librus API client is the open piece of work.

```bash
cd android
./gradlew assembleDebug  # once the Gradle wrapper is in place; see android/README.md
```

## How it talks to Librus

- Only the `Logowanie Synergia` (parent) login is supported, not the Konto Librus e-mail.
- Fetches: grades, timetable (with substitutions and cancellations), events on the calendar, homework, announcements, lucky number.
- Announcements older than 30 days are hidden from the weekly view.
- Sessions live as long as Librus allows; when the session expires, the app re-authenticates using the stored password.

## Project layout

```
ios/
  LibrusSummary/           SwiftUI app
    App/                   Entry point
    State/                 Model, Keychain store, settings, summary logic
    Views/                 Login, week, side-by-side, settings
    Resources/             Assets, en.lproj + pl.lproj Localizable.strings
  Packages/LibrusKit/      Librus API client (Swift package)
  Config/Signing.xcconfig  Your Team ID and Bundle ID
  scripts/                 TestFlight upload helper
  project.yml              XcodeGen input

android/                   Compose scaffold (login + settings), Librus client TODO
docs/screenshots/          Mockups used in this README
```

## Credits and license

The Swift `LibrusKit` package in `ios/Packages/LibrusKit/` was ported by [wadamczewski/szkolny.eu-iOS](https://github.com/wadamczewski/szkolny.eu-iOS) from the Android [Szkolny.eu](https://github.com/szkolny-eu/szkolny-android) project, both GPL-3.0. Thanks to the authors.

This repository is licensed under **GPL-3.0** (see [`LICENSE`](LICENSE)). You can fork it, change it, build it for yourself or share your changes — but any derivative that you distribute must also stay GPL-3.0 and keep the attribution above.

Unofficial app, not affiliated with Librus sp. z o.o.
