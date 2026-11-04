# Librus Tygodniowy — Android

Aplikacja w Jetpack Compose. Klient API Librusa jest już w Kotlinie, a ekran tygodnia pokazuje prawdziwe dane. Jest prostsza niż wersja iOS — brak widoku „obok siebie”, brak trwałej pamięci podręcznej danych, brak przełącznika języka w ustawieniach (język idzie za językiem telefonu).

## Co działa

- Logowanie Synergia (`1234567u` + hasło rodzica); konta szyfrowane w `EncryptedSharedPreferences`.
- Wiele dzieci, przełączanie z menu, dodawanie i usuwanie.
- Widok tygodnia: nawigacja, przegląd (oceny / sprawdziany / odwołane / ogłoszenia / szczęśliwy numerek), nowe oceny, odwołane lekcje, wydarzenia dodane w tym tygodniu, dni (lekcje i wydarzenia), ogłoszenia, ostrzeżenia.
- Zadania domowe scrapowane ze strony „Moje zadania” i wrzucane jako wydarzenia na dzień oddania.
- Ogłoszenia starsze niż 30 dni są filtrowane (jak na iOS).
- Strings `values/` (angielski) + `values-pl/` (polski) — system wybiera na podstawie języka telefonu.

## Czego jeszcze nie ma

- Widok obok siebie (porównanie rodzeństwa w kolumnach) — tylko iOS.
- Trwały cache danych (zapis `StudentData` na dysku) — teraz tylko w pamięci, ginie przy zamknięciu aplikacji.
- Śledzenie zmian („co nowego od ostatniej aktualizacji”) — brak.
- Przełącznik języka w ustawieniach (iOS → System / English / Polski) — tu idzie za systemem.
- Konfiguracja podpisu dla buildu release.

## Jak zbudować

1. Otwórz folder `android/` w Android Studio i poczekaj na synchronizację. Pierwsza synchronizacja pobiera Gradle i zależności.
2. Jeśli wolisz terminal i jeszcze nie otwierałeś Android Studio, wygeneruj wrapper raz:
   ```bash
   cd android
   gradle wrapper --gradle-version 8.10.2
   ```
   (potrzebny jest `gradle` w `PATH` — `brew install gradle` na macOS.)
3. Potem:
   ```bash
   ./gradlew assembleDebug
   ```
   APK trafia do `app/build/outputs/apk/debug/`.

## Jak zainstalować na telefonie

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Albo w Android Studio: podłącz telefon przez USB, wybierz go jako cel i naciśnij **Run**.

## Układ projektu

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
        MainActivity.kt
        ui/AppRoot.kt         ekrany Compose
        ui/theme/Theme.kt     jasny motyw
        state/                ViewModel, konta, logika tygodnia
        librus/               klient API Librus (odpowiednik iOS LibrusKit)
          core/               stałe, błędy, JSON, HTTP
          auth/               zapisane konta
          api/                Librus API 2.0 + parsery + StudentClient
          synergia/           sesja + scraping strony (zadania domowe)
          models/             modele danych
      res/values/strings.xml     angielski
      res/values-pl/strings.xml  polski
      res/drawable/…             ikona launchera
```

## Jak pomóc

Najbliższe rzeczy do zrobienia:

- **Widok obok siebie** — port z `ios/LibrusSummary/Views/SideBySideView.swift`.
- **Trwały cache** — zapis `StudentData` do DataStore lub po prostu do pliku w internal storage; wczytywanie na starcie.
- **Śledzenie zmian** — port z `ios/LibrusSummary/State/UpdateChanges.swift` (dyf między starym a świeżym `StudentData`, znaczniki NEW).
- **Przełącznik języka** — ustawienia z opcjami System / English / Polski, aplikowane przez `AppCompatDelegate.setApplicationLocales` albo `appcompat`-owy per-app language.
- **Podpis release** — `signingConfigs.release` w `app/build.gradle.kts`.
