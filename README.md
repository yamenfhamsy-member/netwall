# NetWall — جدار ناري محلي لكل تطبيق (Android 10+)

**NetWall** blocks internet access per app (separate **Wi-Fi / mobile-data** switches + **whitelist mode**), with no root and no remote server.

It uses Android's `VpnService` slot as an **on-device filter only**: allowed apps bypass the VPN untouched, everything else is routed into a local TUN interface where packets are discarded (sinkhole). Traffic never leaves the phone.

- UI: Kotlin + Jetpack Compose, strict **black & white** Material 3 theme (no dynamic color), Arabic (RTL) + English.
- Min SDK 29 (Android 10), target/compile SDK 34.
- Rules stored on-device with DataStore. No account, no analytics.

## Install

Download the APK from **Releases** (`netwall-vX.Y.Z.apk`) and install it directly (outside Google Play).

On first launch:
1. Allow the local VPN (system dialog, one time).
2. Exclude NetWall from battery optimization so protection survives restarts.
3. Toggle Wi-Fi / Data per app, or enable *Block all except allowed*.

> Only one VPN slot exists on Android: NetWall cannot run together with a real VPN app. If another VPN is active you will get a *"Another VPN is active"* notice.

## Build (CI)

No local build is needed. Pushing to `main` builds a debug APK via GitHub Actions; pushing a tag `v*` (e.g. `v0.1.0`) publishes it as a GitHub Release automatically.

## Project layout

```
app/src/main/java/com/netwall/
  data/   RulesStore.kt (DataStore rules) · AppInventory.kt (PackageManager listing)
  vpn/    FirewallVpnService.kt (TUN sinkhole) · FirewallController.kt · BootReceiver.kt
  ui/     MainActivity.kt · MainViewModel.kt · MainScreen.kt · OnboardingScreen.kt
          theme/Theme.kt (monochrome) · DrawableUtils.kt
```

## How blocking works (v1)

- `VpnService.Builder.addDisallowedApplication()` excludes allowed apps (plus NetWall itself) → their traffic never enters the tunnel.
- Blocked apps are routed (`0.0.0.0/0`, `::/0`) into the TUN, which is opened in blocking mode and drained in a worker thread; packets are dropped.
- A `ConnectivityManager.NetworkCallback` rebuilds the tunnel on Wi-Fi ↔ mobile switches so the correct rule set applies.

## التثبيت

حمّل ملف APK من صفحة Releases وثبّته مباشرة. عند أول تشغيل اسمح بالـ VPN المحلي (مرة واحدة) واستثنِ التطبيق من توفير البطارية.

## License

MIT — see [LICENSE](LICENSE).
