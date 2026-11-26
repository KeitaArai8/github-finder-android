# GitHubFinder (Android)

Pointers only. The source of truth for each topic is the file named next to it.

## Commands

- Unit tests: `./gradlew test` (one module: `./gradlew :feature:search:test`)
- Lint: `./gradlew lint`
- Build: `./gradlew assembleDebug`
- Format: `./gradlew spotlessApply` before committing; CI runs `spotlessCheck`
- Warnings as errors: add `-PwarningsAsErrors=true`
- Compose UI tests (emulator required): `./gradlew :feature:search:connectedAndroidTest`

## Where things are

- Module dependency direction and layering: `CONTRIBUTING.md` ("モジュール構成ルール"), `README.md` (architecture)
- Why each technology was chosen and the intended deviations from Now in Android: `doc/de-facto-standard-design (2026).md`
- Versions and the dependency catalog: `gradle/libs.versions.toml` (add dependencies there, never hard-code versions in build files)
- Shared Gradle setup: `build-logic/convention/`
- Formatting rules: `.editorconfig`

## Tests

- Use hand-written fakes, not MockK/Mockito. Shared fakes and `MainDispatcherRule` live in `:core:testing`; module-local fakes stay in that module's `src/test/`.
- Follow the AAA comment convention (`// Arrange:` / `// Act:` / `// Assert:`, each saying what is set up, run and checked). See `feature/favorite/src/test/` for examples.
- Change a ViewModel or a data-layer class together with its tests.
- Turbine is available for Flow assertions; coroutine tests use `kotlinx-coroutines-test`.

## Rules of thumb

- The official Android guidance and Now in Android take priority over house rules.
- UI state is a single `sealed interface UiState` per screen; ViewModels expose `StateFlow`.
- Strings go in `res/values/strings.xml` plus `res/values-en/strings.xml`; no hard-coded text.
- Do not add new libraries without recording rationale and layer in the PR (see `.github/PULL_REQUEST_TEMPLATE.md`).
