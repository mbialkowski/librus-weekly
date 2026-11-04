# Librus Tygodniowy — Android

Aplikacja w Jetpack Compose. Klient API Librusa jest w Kotlinie, a wszystkie główne widoki pokazują prawdziwe dane — z wersją iOS różni ją tylko brak konfiguracji podpisu release.

## Co działa

- Logowanie Synergia (`1234567u` + hasło rodzica); konta szyfrowane w `EncryptedSharedPreferences`.
- Wiele dzieci, przełączanie z menu, dodawanie i usuwanie.
- **Widok tygodnia**: nawigacja, przegląd (oceny / sprawdziany / odwołane / ogłoszenia / szczęśliwy numerek), nowe oceny, odwołane lekcje, wydarzenia dodane w tym tygodniu, dni (lekcje i wydarzenia), ogłoszenia, ostrzeżenia.
- **Widok „obok siebie”**: rodzeństwo w kolumnach, dotknięcie imienia przełącza na widok pojedynczego dziecka.
- **„Co się zmieniło”**: dyf między ostatnim a świeżym pobraniem — nowe oceny, nowe i zmienione wydarzenia, zmiany w planie, nowe ogłoszenia; na pozycjach pojawia się znacznik NEW.
- **Trwały cache**: ostatnio pobrane `StudentData` i rekord zmian zapisywane do pamięci wewnętrznej (`files/cache/<id>.json`). Aplikacja otwiera się od razu z tym, co ostatnio pobrała.
- **Przełącznik języka** w Ustawieniach (Systemowy / English / Polski) przez `AppCompatDelegate.setApplicationLocales`.
- Zadania domowe scrapowane ze strony „Moje zadania” i wrzucane jako wydarzenia na dzień oddania.
- Ogłoszenia starsze niż 30 dni są filtrowane (jak na iOS).

## Czego jeszcze nie ma

- Konfiguracja podpisu (`signingConfigs.release`) dla buildu wydawniczego.
- Widżety ekranu głównego, powiadomienia push, synchronizacja w tle.

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
