# Anniehilation

**Anniehilation** is an Android project combining a device dashboard with an original cinematic platform-adventure game. The game follows Annie, her orange-tabby companion Berthu, and their friend Xingxing through connected rooms, traversal challenges, combat encounters, and world-finale reunions.

> **Project status:** Active prototype. The Android project has successfully built a debug APK, and the current development baseline includes a cinematic room prototype and a smooth-motion pass. The full 50-stage campaign, eight-world progression, pet-unlock system, and expanded obstacle catalogue described in the roadmap are **planned work** and should not be considered complete until implemented and tested on-device.

## Project highlights

### Android application

- Java-based Android application with XML layouts and resources.
- Main dashboard with RAM, storage, and temperature-related UI components.
- Cooldown and cleanup-related workflows, including an accessibility service that requires explicit user activation in Android settings.
- Android Canvas-based game rendering and custom game views.

### Game prototype

- Room-based cinematic-platformer direction inspired by classic adventure-platform games.
- Annie movement prototype with left/right movement, walking/running pace, jumping, ladder interaction, ledge pull-up, combat, and room transitions.
- Berthu as Annie's persistent protective companion.
- Xingxing as a special world-finale reunion character.
- A stylised fictional martial-arts antagonist and original sound effects.

Controls and gameplay are prototype-stage and may change as the engine is developed.

## Campaign direction

The planned campaign contains **50 stages across eight themed worlds**. Xingxing appears at the end of each world, not at the end of every ordinary stage. Each world finale introduces an unlockable support pet.

| World | Stages | Finale reward concept |
|---|---:|---|
| School Grounds | 1–6 | Poodle |
| Old City | 7–12 | Sheep |
| Forgotten Fort | 13–18 | Pet to be designed |
| Wild Jungle | 19–24 | Pet to be designed |
| Underground Kingdom | 25–30 | Pet to be designed |
| Mountain Passage | 31–36 | Pet to be designed |
| Night Citadel | 37–42 | Pet to be designed |
| Xingxing's Kingdom | 43–50 | Final pet to be designed |

See [`ROADMAP.md`](ROADMAP.md) for the planned stage concepts and mechanics. This table describes the design target, not a claim that all stages have shipped.

## Technology stack

- **Java** — Android application and game logic.
- **XML** — Android manifest, layouts, and resource definitions.
- **Gradle Kotlin DSL** — project and module build configuration (`*.gradle.kts`).
- **TOML** — Gradle version catalog (`gradle/libs.versions.toml`).
- **Markdown** — project documentation.

GitHub calculates the repository's Languages bar automatically from committed files. It is not a field that is manually added to a commit. See [`LANGUAGES.md`](LANGUAGES.md).

## Requirements

- Android Studio with Android SDK components compatible with this project's Gradle and Android Gradle Plugin configuration.
- A JDK supported by the Gradle wrapper and Android Gradle Plugin configured in this repository.
- Android SDK Platform 37 and **minimum SDK 26** in the project snapshot used to prepare this documentation. Check `app/build.gradle.kts` on your branch if these values have since changed.
- An Android device or emulator for runtime testing.

Do not upgrade Gradle, the Android Gradle Plugin, SDK levels, or dependencies as part of an unrelated change. Make build-tool upgrades separately and verify them on a clean build.

## Open and build

1. Clone this repository.
2. Open the repository root—the folder containing `settings.gradle.kts`, `gradlew`, and `app/`—in Android Studio.
3. Allow Gradle Sync to finish.
4. Select the `app` run configuration and a connected Android device or emulator.
5. Use **Run** in Android Studio.

For a command-line debug build on Windows:

```bat
gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

The debug APK is normally produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The repository's Gradle wrapper should be used rather than a separately installed Gradle version.

## Repository layout

The exact classes evolve during development. The main Android module is `app/`:

```text
Anniehilation/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/rictharakan/anniehilation/
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── .github/
├── README.md
├── LICENSE
├── NOTICE
├── settings.gradle.kts
├── build.gradle.kts
├── gradlew
└── gradlew.bat
```

Do not commit machine-specific `local.properties`, Gradle caches, build outputs, signing keys, or unrelated nested repositories.

## Permissions and privacy

Review every Android permission and service before publishing a release. In particular, broad package visibility, all-files storage access, and accessibility-service functionality are sensitive capabilities. Explain clearly why each is required, request only what the app genuinely needs, and follow current Android and distribution-platform policies. Do not collect, upload, or expose personal device data without clear user notice and consent.

## Contributing

Bug reports, design feedback, and code contributions are welcome. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md), follow the issue templates, and test changes before opening a pull request. Contributions to the source code and documentation are offered under Apache License 2.0 unless a separate written agreement says otherwise. Do not submit artwork, audio, fonts, or other assets unless you own them or can provide the necessary license and attribution.

## Licensing

- The original source code and project documentation identified as the **Code Work** are licensed under the [Apache License 2.0](LICENSE).
- Character designs, sprite sheets, animations, game artwork, logos, screenshots, music, sound effects, and other game assets are **not automatically covered** by Apache 2.0. See [`ASSETS-LICENSE.md`](ASSETS-LICENSE.md).
- Third-party libraries and assets remain under their respective licenses. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

**Important:** GitHub may identify the repository's root `LICENSE` as Apache-2.0 even though the repository contains separately licensed assets. Read the scope notes above before reusing anything. This is a mixed-licensing repository, not a blanket Apache license for every image, sound, or brand visible in the project.

## Disclaimer

Anniehilation is an independent project. References to other games or genres are descriptive inspirations for general gameplay concepts only. This project is not affiliated with, endorsed by, or sponsored by the creators or publishers of those games. Any real-person likeness, trademark, film/character reference, or third-party audio must be cleared before public distribution. The public antagonist should be presented as an original fictional character unless appropriate rights and permissions have been obtained.
