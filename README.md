# A13 Vision

*A quieter screen for a little box with a hidden engine.*

An Apple TV-inspired Android TV launcher for a Chinese RK3528 box: a cinematic ringed-planet backdrop and a two-row grid of wide app tiles. Each tile uses the app’s own colors and real Android icon, enlarged without inventing a replacement logo. A quick-open card for the last app launched from this home screen sits below the clock. The first app gets a soft focus highlight; there is no recommendation feed.

## The box

- SoC: Rockchip RK3528
- CPU / architecture: quad-core ARM Cortex-A53, ARMv8-A
- GPU: ARM Mali-450
- RAM: 4 GB
- Internal storage: 32 GB
- OS: Android TV 13

The memory and storage figures describe this box’s reported configuration. The exact retail model name was not provided.

## The launcher

TV apps and installed user apps appear in the two-row grid; generic system utilities such as Camera and Settings are hidden. Each tile uses the real icon and name provided by Android, with the app’s background color filling a wide card. The last app launched from this home screen can be opened again with one click; whether playback resumes is up to that app. The first app receives focus on startup unless another app is chosen. Hold **OK/Select** or press **MENU** to arrange apps; use the arrow keys to move one; press **OK/Select** to choose the startup focus; press **BACK** to finish. The order and focus are saved on the box.

## Build

GitHub Actions builds a debug APK from this repository. After a successful run, download the **A13-Vision-debug-APK** artifact and extract `app-debug.apk`. This is a test build, not a Play Store release.
