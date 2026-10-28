# A13 Vision

*A quieter home screen for a little box with a hidden engine.*

A minimalist Android TV launcher for a Chinese RK3528 box: a cinematic sunset home screen with a cleaner Apple TV-inspired feel. It shows launchable apps already installed on the box, uses their real names and icons, and supports remote-controlled rearranging. No pretend app icons or recommendation feed. It is designed to feel lighter; real speed gains still need testing on the box.

## The box

- SoC: Rockchip RK3528
- CPU / architecture: quad-core ARM Cortex-A53, ARMv8-A
- GPU: ARM Mali-450
- RAM: 4 GB
- Internal storage: 32 GB
- OS: Android TV 13

The memory and storage figures describe this box’s reported configuration. The exact retail model name was not provided.

## The launcher

The home screen discovers installed apps with Android launcher shortcuts and uses each app’s own name and icon. The rounded tiles use a soft brand-color gradient, larger icons, and readable labels. The last app card has its own space above the scrollable app grid so it does not cover app buttons. Hold **OK/Select** or press **MENU** to arrange apps; use the arrows to move one; press **OK/Select** to choose the startup focus; press **BACK** to finish. The order and focus are saved on the box.

The **Settings** button opens an A13 menu that can be navigated with the remote. Its categories open the corresponding system settings pages. The behavior inside those pages is still provided by the box firmware.

## Build

GitHub Actions builds a debug APK from this repository. After a successful run, download the **A13-Vision-debug-APK** artifact and extract `app-debug.apk`. This is a test build, not a Play Store release.
