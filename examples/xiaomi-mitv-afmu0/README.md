# Xiaomi TV Box S 3rd Gen (MiTV-AFMU0 / twilight) - Google TV 14 Debloat & Apple TV Launcher

This guide documents the debloat, ad-blocking DNS configuration, and Apple TV 4K-style launcher installation on the **Xiaomi TV Box S 3rd Gen** (`ro.product.model=MiTV-AFMU0`, `ro.product.device=twilight`, Google TV on Android 14 API 34, firmware `UKG3.250826.001.V816.0.11.0.UZFAABX`).

---

## Summary of Changes

- **Stock Google TV Launcher Disabled**: Replaced `com.google.android.apps.tv.launcherx` with custom native launcher `com.gathin.tvhome`.
- **Setup Wizard Disabled**: `com.google.android.tungsten.setupwraith` disabled to prevent it from hijacking Home.
- **Xiaomi Telemetry & Diagnostics Disabled**:
  - `com.miui.tv.analytics`
  - `com.xiaomi.statistic`
  - `com.xiaomi.tvqs.overseas.y24`
  - `com.google.android.feedback`
  - `com.android.tv.feedbackconsent`
  - `android.autoinstalls.config.soc.amlogic`
- **PatchWall & Xiaomi Content Promos Disabled**:
  - `com.mitv.tvhome.atv`
  - `com.mitv.tvhome.michannel`
  - `com.mitv.tvhome.mitvplus` (Xiaomi TV+)
  - `com.mitv.tvhome.oemtab`
  - `com.xm.webcontent`
  - `com.xiaomo.tv.milegal`
  - `com.mitv.videoplayer`
  - `com.xiaomi.mitv.mediaexplorer`
  - `com.xiaomi.mimusic2`
  - `com.mitv.gallery`
- **Unused Preloaded Apps Disabled**:
  - `com.tencent.qqlivei18n` (WeTV)
  - `com.dailymotion.dailymotion`
  - `com.android.printspooler`
  - `com.google.android.play.games`
  - `com.google.android.marvin.talkback`
- **Kept Enabled (User Preferences)**:
  - YouTube, Netflix, Disney+ Hotstar (`in.startv.hotstar.dplus.tv`), Vidio, Viu, Vision+, YouTube Kids, YouTube Music.
  - Chromecast (`com.google.android.apps.mediashell`).
  - Google Assistant (`com.google.android.katniss`).
  - Xiaomi Update Service (`com.xiaomi.mitv.updateservice`).
  - Core keyboard, Google Play Store, Play Services, Bluetooth.
- **Private DNS (AdGuard)**: Configured `dns.adguard-dns.com` for ad and tracker blocking.

---

## Restoring Back to Stock Google TV

To revert everything back to the stock Google TV launcher and re-enable all original apps:

```bash
TV_HOST=10.20.50.138 ./backup/xiaomi-mitv-afmu0/restore_backup.sh
```

Or using `tools/restore.sh`:
```bash
TV_HOST=10.20.50.138 tools/restore.sh examples/xiaomi-mitv-afmu0/debloat.txt com.google.android.apps.tv.launcherx
```

To re-enable any individual app manually:
```bash
TV_HOST=10.20.50.138 tools/venv/bin/python tools/tv.py "pm enable <package_name>"
```

To revert Private DNS:
```bash
TV_HOST=10.20.50.138 tools/venv/bin/python tools/tv.py "settings put global private_dns_mode opportunistic"
```

---

## Closing ADB & Securing the TV

Once you are satisfied:
1. Go to **Settings → System → Developer options**.
2. Turn **USB debugging** to **OFF**.
3. Select **Revoke USB debugging authorizations**.
4. Restart the TV.
5. Check from your computer:
   ```bash
   nc -vz 10.20.50.138 5555
   ```
