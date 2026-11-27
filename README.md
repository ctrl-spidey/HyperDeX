# HyperDeX

HyperDeX gives Xiaomi and Redmi tablet users a desktop-style workspace in HyperOS. Switch between the stock Workstation layout and the HyperDeX layout from the companion app or a Quick Settings tile.

**[Download the latest release](https://github.com/spider-man4u/HyperDeX/releases/latest)**

## The desktop layout

- Launch apps from the lower-left corner and switch between them from a full-width taskbar.
- Minimize, maximize, restore, or close floating windows from their title bars.
- Open the clock, notifications, and quick settings from the system tray.

## Installation

HyperDeX targets rooted Xiaomi and Redmi tablets running HyperOS with Workstation Mode. Set up a compatible Zygisk environment and LSPosed before installing. The root module installer recognizes KernelSU, Magisk, and APatch.

1. From the latest release, download the versioned HyperDeX ZIP and install it with your root manager. The ZIP bundles the companion APK. For a standalone LSPosed installation, the same release also includes `HyperDeX.apk`.
2. In LSPosed, enable HyperDeX and check that its scope includes **System Launcher** (`com.miui.home`) and **System UI** (`com.android.systemui`).
3. Reboot the tablet, enter Workstation Mode, and open HyperDeX to select the desktop layout. To switch from the notification shade, add the HyperDeX tile to Quick Settings.

## Build from source

Use JDK 17 and Android SDK Platform 35. From the repository root, run:

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The [release workflow](.github/workflows) builds the release APK, packages the root module, and generates SHA-256 checksums.

## License

[Apache License 2.0](LICENSE)
