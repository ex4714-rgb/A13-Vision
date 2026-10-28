# A13 Vision

*A quieter home screen for a little box with a hidden engine.*

A minimalist Android TV launcher for a Chinese RK3528 box: an Apple TV-inspired look, only the apps already on the device, and remote-controlled rearranging. No pretend app icons or recommendation feed. Designed to feel lighter; real speed gains still need testing on the box.

## The box

- SoC: Rockchip RK3528
- CPU / architecture: quad-core ARM Cortex-A53, ARMv8-A
- GPU: ARM Mali-450
- RAM: 4 GB
- Internal storage: 32 GB
- OS: Android TV 13

The memory and storage figures describe this box’s reported configuration. The exact retail model name was not provided.

## The launcher

The home row discovers launchable apps and uses each installed app’s own name and icon. Hold **OK/Select** or press **MENU** to arrange apps; use **LEFT/RIGHT** to move one; press **OK/Select** to choose the startup focus; press **BACK** to finish. The order and focus are saved on the box.

## Build

GitHub Actions builds a debug APK from this repository. After a successful run, download the **A13-Vision-debug-APK** artifact and extract `app-debug.apk`. This is a test build, not a Play Store release.
