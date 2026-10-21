# Skyworth SWTV (shibuya) - Android TV 11 Debloat & Apple TV Launcher

This guide documents the debloat, ad-blocking DNS configuration, and custom Apple TV 4K-style launcher for the Skyworth SWTV (`ro.product.model=SWTV`, `ro.product.device=shibuya`, Android 11 API 30).

## Summary of Changes

- **Stock Launcher Disabled**: Replaced `com.google.android.tvlauncher` with custom native launcher `com.gathin.tvhome`.
- **Manufacturer Demos & Bloat Disabled**:
  - `com.smartdevice.common.demonow` (Demo Now shop demo)
  - `fusion.android.tv.demo` (Skyworth Demo)
  - `com.Smartdevice.emanual` (On-screen manual)
  - `com.mstar.android.tv.disclaimercustomization`
- **Telemetry & Extra Background Services Disabled**:
  - `com.google.android.feedback`
  - `com.airoha.ble.ota`
  - `com.coocaa.factory`
  - `com.mediatek.testpq`
  - `com.google.android.partnersetup`
  - `com.google.android.apps.nbu.smartconnect.tv`
  - `com.google.android.leanbacklauncher.partnercustomizer`
  - `com.google.android.tvrecommendations`
- **Unused Services Disabled (User Choice)**:
  - `com.amazon.amazonvideo.livingroom` (Prime Video)
  - `com.ezviz.tv` (EZVIZ)
  - `com.google.android.apps.mediashell` (Chromecast)
  - `com.google.android.katniss` (Google Assistant)
- **Private DNS (AdGuard)**: Configured `dns.adguard-dns.com` for system-wide tracker and ad blocking.

---

## Restoring Back to Stock

To re-enable all disabled apps, restore the stock Google TV launcher, and reset Private DNS:

```bash
TV_HOST=10.20.50.180 ./backup/swtv-shibuya/restore_backup.sh
```

Or using `tools/restore.sh`:

```bash
TV_HOST=10.20.50.180 tools/restore.sh examples/skyworth-swtv-shibuya/debloat.txt com.google.android.tvlauncher
```

To re-enable any individual app manually:

```bash
TV_HOST=10.20.50.180 tools/venv/bin/python tools/tv.py "pm enable <package_name>"
```

For example, to re-enable Prime Video:
```bash
TV_HOST=10.20.50.180 tools/venv/bin/python tools/tv.py "pm enable com.amazon.amazonvideo.livingroom"
```

To revert Private DNS to default opportunistic mode:
```bash
TV_HOST=10.20.50.180 tools/venv/bin/python tools/tv.py "settings put global private_dns_mode opportunistic"
```

---

## Customizing the Launcher

### 1. Changing Dock Apps & Order
Edit [`launcher/app/src/main/res/values/config.xml`](file:///Volumes/Work/project/cleanyourtv/launcher/app/src/main/res/values/config.xml):

```xml
<string-array name="dock_apps">
    <item>com.google.android.youtube.tv</item>
    <item>com.netflix.ninja</item>
    <item>com.disney.disneyplus</item>
    <item>com.vidio.android.tv</item>
</string-array>
```

Then rebuild and install:
```bash
cd launcher
TV_HOST=10.20.50.180 ./build-and-install.sh
```

### 2. Changing the Wallpaper
Push any 1920x1080 JPEG or PNG wallpaper using `tools/tv.py`:

```bash
TV_HOST=10.20.50.180 tools/venv/bin/python tools/tv.py --push /path/to/wallpaper.jpg /sdcard/tvhome/wallpaper.jpg
```
The launcher automatically picks up `/sdcard/tvhome/wallpaper.jpg` on start.

---

## Closing ADB & Securing the TV

Once you are satisfied:
1. Go to **Settings → Device Preferences → Developer options**.
2. Turn **USB debugging** to **OFF**.
3. Select **Revoke USB debugging authorizations** and confirm.
4. Restart the TV.
5. Verify from your computer that port 5555 is closed:
   ```bash
   nc -vz 10.20.50.180 5555
   ```
