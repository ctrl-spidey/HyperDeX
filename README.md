# HyperDeX

An open-source LSPosed and KernelSU module designed for Xiaomi and Redmi tablets running HyperOS with Workstation Mode. It provides a dual-mode desktop experience: switch freely between the stock Workstation interface and a classic edge-to-edge desktop layout with dedicated window management controls.

## Key Features

- **Dual-Mode Switcher**: Instantly toggle between stock HyperOS Workstation Mode and the Classic Desktop layout via the companion app or a Quick Settings tile.
- **Edge-to-Edge Taskbar**: Expands the dock to span the full screen width with an acrylic backdrop and flat baseline docking.
- **Classic Window Controls**: Unlocks Minimize, Maximize/Restore, and Close action buttons directly within floating window caption bars.
- **Start Menu Anchor**: Repositions the application drawer shortcut to the lower-left corner with a grid launcher icon.
- **Clean Framework Hooks**: Operates in memory via LSPosed, keeping system partitions intact and avoiding APK signature issues.

## Project Structure

```text
HyperDeX/
├── .github/workflows/    # Automated CI/CD workflow for APK and module packaging
├── app/                  # Android application & Xposed module implementation
│   ├── src/main/java/    # Hook implementations and dual-mode configuration
│   └── src/main/res/     # Layouts, vector assets, and themes
├── module/               # KernelSU / Magisk flashable module assets
└── tools/                # Packaging script for flashable ZIP builds
```

## Requirements

- Xiaomi / Redmi Tablet (e.g., Redmi Pad Pro / Xiaomi Pad 6 series)
- HyperOS 1.0 or 2.0 with Workstation Mode enabled
- KernelSU, Magisk, or APatch with Zygisk enabled
- LSPosed Framework

## Installation

1. Install the `HyperDeX.apk` application or flash the `HyperDeX_v1.0.0.zip` module in KernelSU.
2. Open LSPosed and enable the **HyperDeX** module.
3. Ensure both **System Launcher (`com.miui.home`)** and **System UI (`com.android.systemui`)** are checked in the module scope.
4. Reboot the tablet.
5. Open the HyperDeX app or toggle the Quick Settings tile to switch between desktop layouts.

## License

This project is licensed under the Apache License 2.0.
