# Librus Tygodniowy

Tygodniowy podgląd Librus Synergia na własnym telefonie — oceny dodane w tym tygodniu, plan lekcji ze zmianami, sprawdziany i wydarzenia z kalendarza, szczęśliwy numerek i ogłoszenia szkolne. Dane są pobierane prosto z Librusa; hasła zostają w Pęku kluczy iOS (lub Android Keystore).

Aplikacja do samodzielnego zbudowania — nic do pobrania ze sklepu, żadnego pośredniego serwera.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/single.svg" alt="Tydzień jednego dziecka" width="300"/></td>
    <td align="center"><img src="docs/screenshots/side-by-side.svg" alt="Widok obok siebie dla rodzeństwa" width="300"/></td>
  </tr>
  <tr>
    <td align="center"><sub>Tydzień jednego dziecka</sub></td>
    <td align="center"><sub>Widok obok siebie dla rodzeństwa</sub></td>
  </tr>
</table>

## Co pokazuje

- **Podsumowanie tygodnia** — oceny dodane w tym tygodniu, sprawdziany i wydarzenia z kalendarza, szczęśliwy numerek, odwołane lekcje i zastępstwa.
- **Wiele dzieci** — jeden login Synergia na dziecko, przełączanie z menu albo widok wszystkich obok siebie w kolumnach (iOS).
- **„Co się zmieniło”** — przeciągnięcie w dół pobiera świeże dane i pokazuje, co jest nowe od ostatniej aktualizacji; nowe pozycje dostają znacznik NEW.
- **Praca offline** — dane trzymane są lokalnie, więc aplikacja otwiera się z tym, co ostatnio pobrała, i nie łączy się z Librusem, dopóki o to nie poprosisz.
- **Interfejs po polsku lub po angielsku** — wybierz w Ustawieniach (iOS) albo ustaw język telefonu (Android).
- Orientacja pozioma i pionowa, jasny motyw.

## Wymagania

### iOS

- Mac z **Xcode 16** lub nowszym.
- Płatne członkostwo w **Apple Developer Program**. Dystrybucja przez TestFlight tego wymaga; uruchomienie na własnym telefonie dłużej niż 7 dni również.
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`.

### Android

- **Android Studio** (dowolna nowsza wersja) lub `gradle` + Android SDK.
- JDK 17+ (Android Studio ma własny).

## Jak zbudować iOS

### 1. Podpisywanie

W Xcode otwórz **Ustawienia → Konta** i dodaj swoje Apple ID.

Edytuj `ios/Config/Signing.xcconfig`:
- `DEVELOPMENT_TEAM`: dziesięcioznakowy Team ID (Xcode → Ustawienia → Konta, albo developer.apple.com → Konto → Dane członkostwa).
- `BUNDLE_ID`: unikalny identyfikator, np. `com.twojanazwa.librusweekly`.

### 2. Wygeneruj i otwórz projekt

```bash
cd ios
xcodegen generate
open LibrusSummary.xcodeproj
```

Wybierz iPhone albo symulator i naciśnij **Run**. Przy pierwszym uruchomieniu Xcode pobierze zależność SwiftSoup.

### 3. Zaloguj dziecko

Po pierwszym uruchomieniu aplikacja pokazuje ekran logowania. Wpisz login Synergia dziecka (`1234567u`) i hasło rodzica. Przycisk **Dodaj dziecko** w menu u góry po lewej służy do dodawania kolejnych.

Hasła są przechowywane w Pęku kluczy iOS, wyłącznie na urządzeniu.

### 4. (Opcjonalnie) TestFlight

Żeby zainstalować aplikację na większej liczbie urządzeń bez podłączania ich kablem:

1. W [App Store Connect](https://appstoreconnect.apple.com) wybierz **Apps → + → New App** z tym samym Bundle ID. Platforma: iOS. Nazwa: cokolwiek unikalnego w całym App Store.
2. W Xcode **Product → Archive**, potem w Organizerze **Distribute App → TestFlight Internal Only**. Albo z terminala:
   ```bash
   cd ios && ./scripts/upload-testflight.sh
   ```
3. W App Store Connect w sekcji **TestFlight → Internal Testing** utwórz grupę i dodaj testerów. Testerzy wewnętrzni nie przechodzą Beta App Review.
4. Buildy TestFlight wygasają po 90 dniach — wyślij nowy przed terminem.

**Trzymaj to przy testerach wewnętrznych.** Librus wyrażał sprzeciw wobec aplikacji trzecich w publicznych sklepach.

## Jak zbudować Androida

```bash
cd android
./gradlew assembleDebug
```

APK trafia do `app/build/outputs/apk/debug/app-debug.apk`. Zainstaluj go na telefonie przez `adb install` albo przez Android Studio.

Więcej szczegółów (stan prac, jak dołączyć): [`android/README.md`](android/README.md).

## Jak rozmawia z Librusem

- Obsługiwany jest tylko login **Synergia** (np. `1234567u`), nie e-mail Konta Librus.
- Pobiera: oceny, plan lekcji (ze zmianami i odwołaniami), wydarzenia z kalendarza, zadania domowe, ogłoszenia, szczęśliwy numerek.
- Ogłoszenia starsze niż 30 dni są ukryte z widoku tygodnia.
- Sesje żyją tak długo, jak pozwala Librus; kiedy wygasają, aplikacja loguje się ponownie zapisanym hasłem.

## Struktura projektu

```
ios/
  LibrusSummary/           aplikacja SwiftUI
    App/                   punkt wejścia
    State/                 model, Pęk kluczy, ustawienia, logika tygodnia
    Views/                 logowanie, tydzień, obok siebie, ustawienia
    Resources/             assety, en.lproj + pl.lproj Localizable.strings
  Packages/LibrusKit/      klient API Librus (pakiet Swift)
  Config/Signing.xcconfig  Twój Team ID i Bundle ID
  scripts/                 pomocnik do TestFlight
  project.yml              wejście dla XcodeGen

android/
  app/src/main/java/com/example/librusweekly/
    librus/                klient API Librus w Kotlinie (core, auth, api, synergia, models)
    state/                 ViewModel, pamięć kont, logika tygodnia
    ui/                    ekrany Compose (logowanie, tydzień, ustawienia)
  app/src/main/res/        strings (en + pl), ikona, motyw

docs/screenshots/          makiety pokazane w tym README
```

## Podziękowania i licencja

Pakiet Swift `LibrusKit` w `ios/Packages/LibrusKit/` został przeportowany przez [wadamczewski/szkolny.eu-iOS](https://github.com/wadamczewski/szkolny.eu-iOS) z androidowego projektu [Szkolny.eu](https://github.com/szkolny-eu/szkolny-android), oba na GPL-3.0. Klient w Kotlinie (`android/app/src/main/java/com/example/librusweekly/librus/`) bazuje na tym samym kodzie. Podziękowania dla autorów.

Repozytorium jest na licencji **GPL-3.0** (zobacz [`LICENSE`](LICENSE)). Możesz forkować, zmieniać, budować dla siebie, udostępniać własne zmiany — ale każda pochodna, którą rozpowszechniasz, też musi zostać na GPL-3.0 i zachować powyższe podziękowania.

Aplikacja nieoficjalna, niezwiązana z Librus sp. z o.o.
