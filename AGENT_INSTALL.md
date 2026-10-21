# AGENT_INSTALL.md — AI Agent Execution Guide

> **Prompt for LLMs / AI Coding Agents** (Claude Code, Antigravity, Cursor, Codex, Aider):
> *"You are tasked with debloating and configuring an Android TV, installing the lightweight CleanYourTV launcher (with embedded REST API), and running the tvhub management dashboard. Follow the instructions below step-by-step."*

---

## 📋 Pre-Flight Checklist & Variables

Before executing commands, determine or ask the user for:
- `TV_IP`: The local IP address of the Android TV (e.g. `10.20.50.138` or `192.168.1.50`).
- Ensure the TV and your machine are on the **same Wi-Fi / local network**.
- Ensure **Developer Options** and **Network Debugging / USB Debugging** are enabled on the TV:
  *(Settings → Device Preferences → About → Click "Build" 7 times → Developer Options → Enable "USB Debugging")*.

---

## 🛠️ Step 1: Connect & Audit TV

### 1.1 Connect via ADB
```bash
adb connect <TV_IP>:5555
adb devices
```
*(If prompted on the TV screen, click "Always allow from this computer" and press OK).*

### 1.2 Identify TV Hardware & Specs
```bash
adb -s <TV_IP>:5555 shell "getprop ro.product.manufacturer && getprop ro.product.model && getprop ro.build.version.release"
```

### 1.3 List Installed Packages
```bash
adb -s <TV_IP>:5555 shell "pm list packages -f" > /tmp/tv_packages.txt
```

---

## 🛡️ Step 2: Safe Non-Destructive Debloat

> [!IMPORTANT]
> **CRITICAL RULE**: NEVER run `pm uninstall`. Only use `pm disable-user --user 0`.
> Disabling packages is 100% reversible (`pm enable <pkg>`) and prevents bootloops.

### Known Safe Packages to Disable:

#### Common Google TV / Android TV Bloat:
```bash
# Ad-heavy stock launcher (Android TV Home)
adb -s <TV_IP>:5555 shell "pm disable-user --user 0 com.google.android.tvlauncher"
adb -s <TV_IP>:5555 shell "pm disable-user --user 0 com.google.android.tvrecommendations"

# Setup wraith / ads
adb -s <TV_IP>:5555 shell "pm disable-user --user 0 com.google.android.apps.setupwraith"
```

#### Xiaomi (Mi Box / Mi TV PatchWall):
```bash
adb -s <TV_IP>:5555 shell "pm disable-user --user 0 com.mitv.tvhome"
adb -s <TV_IP>:5555 shell "pm disable-user --user 0 com.xiaomi.mitv.upgrade"
adb -s <TV_IP>:5555 shell "pm disable-user --user 0 com.xiaomi.devicereport"
adb -s <TV_IP>:5555 shell "pm disable-user --user 0 com.xiaomi.mitv.advertise"
```

#### Skyworth / Coocaa:
```bash
adb -s <TV_IP>:5555 shell "pm disable-user --user 0 com.skyworth.tv.launcher"
adb -s <TV_IP>:5555 shell "pm disable-user --user 0 com.coocaa.mall"
```

---

## 🚀 Step 3: Install CleanYourTV Launcher (with REST API + mDNS)

The launcher APK (`com.gathin.tvhome`) includes an embedded HTTP server (Port 8080) and mDNS broadcaster (`_tvhub._tcp.`) for real-time control via `tvhub`.

### 3.1 Download & Install the APK
Download the precompiled APK directly from GitHub Releases:
```bash
# Download precompiled APK
curl -L -o app-release.apk https://github.com/reyhanfahlevi/cleanyourtv/releases/download/v1.0.0/app-release.apk

# Install to TV
adb -s <TV_IP>:5555 install -r app-release.apk
```
*(Alternative for stubborn TV adbd: push via base64 chunker `python3 tools/b64push.py <apk> /data/local/tmp/app.apk && adb shell pm install -r /data/local/tmp/app.apk`).*

### 3.2 Set as Default Home Launcher
```bash
adb -s <TV_IP>:5555 shell "cmd package set-home-activity com.gathin.tvhome/.MainActivity"
```

### 3.3 Push Initial Theme & Wallpapers
```bash
# Push baseline config.json
adb -s <TV_IP>:5555 shell "mkdir -p /storage/emulated/0/Android/data/com.gathin.tvhome/files/wallpapers"
adb -s <TV_IP>:5555 push themes/collection/config.json /storage/emulated/0/Android/data/com.gathin.tvhome/files/config.json

# Push sample wallpapers
adb -s <TV_IP>:5555 push themes/collection/wallpapers/* /storage/emulated/0/Android/data/com.gathin.tvhome/files/wallpapers/

# Start the launcher
adb -s <TV_IP>:5555 shell "am start -n com.gathin.tvhome/.MainActivity"
```

### 3.4 Verify Embedded REST API
Test if the TV's embedded server is active:
```bash
curl http://<TV_IP>:8080/api/status
```
Expected output:
```json
{"id":"...","model":"...","uptime_ms":...,"memory":{...},"storage":{...},"wallpapers_count":...}
```

---

## 🖥️ Step 4: Run the `tvhub` Dashboard

`tvhub` is the central web dashboard that automatically scans your local subnet and listens for mDNS services broadcasted by the TVs.

### 4.1 Clone & Install
```bash
git clone git@github.com:reyhanfahlevi/tvhub.git
cd tvhub
npm install
```

### 4.2 Start tvhub
```bash
# Production mode:
npm run build
npm start

# Or development mode:
npm run dev
```

### 4.3 Open Dashboard
Open `http://localhost:3000` in your browser.
- **Auto-Discovery**: tvhub automatically finds all TVs running on the subnet.
- **Glass & Dock Control**: Adjust dock opacity (0-100%) and glass specular rim live without restarting.
- **Wallpaper Manager**: Drag & drop wallpapers with instant push or rotation timer.
- **Virtual Remote**: Control navigation, OK, Back, and Home from your phone/browser.
- **Quick Launch**: Launch any installed app with a single click.
