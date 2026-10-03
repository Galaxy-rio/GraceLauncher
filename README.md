# Grace Launcher

Grace Launcher is an open-source Android launcher inspired by the design of [Niagara Launcher](https://niagaralauncher.app/). It is built with Kotlin and Jetpack Compose, with support for Material You and dynamic color.

## Features

- A list-based home screen and app drawer, inspired by Niagara Launcher
- A home screen music player with media controls
- Notifications directly on the home screen
- An integrated calendar agenda with weather forecasts
- Weather information on the home screen

Weather data is provided through [Breezy Weather](https://github.com/breezy-weather/breezy-weather), which must be installed and configured separately.

## Screenshots

| Home | Agenda & weather | Music controls | App list |
| --- | --- | --- | --- |
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01-home.png" alt="Home screen with favorites and current weather" width="190"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02-agenda.png" alt="Calendar agenda with hourly weather forecasts and events" width="190"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03-music.png" alt="Home screen with music playback controls" width="190"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04-app-list-c.png" alt="Alphabetical app list while browsing the letter C" width="190"> |

## Build from source

Install OpenJDK 21, Android SDK Platform 37 and Build Tools 36.1.0, then build the debug APK with the included Gradle wrapper:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Privacy

Grace Launcher does not request network access. Notification access is required to display notifications and control active media sessions on the home screen. All launcher data is stored locally on your device.

## Contributing

Contributions are welcome. Feel free to open an issue to report a bug or suggest an improvement, or submit a pull request with your changes.

Translations are managed through [Weblate](https://hosted.weblate.org/engage/grace_launcher/). You can help translate Grace Launcher into your language there.

Keep all UI strings in one `strings.xml` per language: `app/src/main/res/values/strings.xml` for the source text and `values-<locale>/strings.xml` for translations. Use section comments instead of splitting strings into feature-specific resource files.

Code is grouped by responsibility: `data` contains models and storage, `platform` integrates Android services, and `ui` contains feature screens with reusable elements in `ui/components`. Keep related small helpers with their feature rather than adding extra layers.

Generated build outputs, temporary captures, local release packages and `.local` maintainer drafts are ignored by Git. Only shared code styles and inspections from `.idea` are versioned; device selections and other local IDE state stay on your machine.

<a href="https://hosted.weblate.org/engage/grace_launcher/">
  <img src="https://hosted.weblate.org/widget/grace_launcher/multi-auto.svg" alt="Translation status">
</a>

## Acknowledgements

Special thanks to [Niagara Launcher](https://niagaralauncher.app/) for the design inspiration.

Grace Launcher is an independent project and is not affiliated with or endorsed by Niagara Launcher.

## License

Grace Launcher is licensed under the [GNU General Public License v3.0](LICENSE).
