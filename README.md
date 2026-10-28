# Accueil TV

**English** · [Français](README.fr.md)

**A fast, ad-free home screen for Android TV / Google TV, Apple TV style.**
A dock of your recent apps, rotating wallpapers, an "Up Next" row with your latest shows, and the
remote's settings button opening the TV settings directly.
Open source (MIT). Nothing is uninstalled, everything can be undone.

---

## One-command install

On **your computer** (same Wi-Fi as the TV), paste this into a terminal:

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/Leptitluc/android-apple-tv/main/install.sh)
```

The assistant first asks for your language (English or French), then walks you through each step:
it explains how to enable debugging on the TV, finds the device, **makes a backup**, slims down the
system (ads, telemetry, useless apps — asking you about the ones you might use), installs
Accueil TV, sets up the settings button and makes it the default home screen. About 5 minutes.

### Do this on the TV first (just once)

1. **Settings → System → About**, then press **"Android TV OS build"** (or "Build") **7 times**
   until you see "You are now a developer!".
2. **Settings → System → Developer options → USB debugging**: turn it on.
3. Note the IP address: **Settings → Network & Internet**.

During the install, the TV will show **"Allow USB debugging?"**: accept and tick
**"Always allow from this computer"**.

### Without curl (by cloning the repo)

```bash
git clone https://github.com/Leptitluc/android-apple-tv.git
cd android-apple-tv
./install.sh            # or ./install.sh 192.168.1.20
```

To skip the language question, set `ACCUEIL_TV_LANG=en` (or `fr`) before the command.

---

## What you get

- **No ads.** No sponsored rows, no banners.
- **A dock** of your recent apps, with a grid of all your apps lined up below.
- **Wallpapers** that rotate (nature, art, architecture, night… adjustable), with credits.
- **"Up Next"**: your latest shows, resumed where you left off, full screen like tvOS.
- **Quick menu** on the home screen: wallpaper categories, duration, which apps feed "Up Next",
  show/hide apps, and "More settings".
- **TV inputs** (HDMI, AV, antenna…) one click away, next to the clock, with the names of
  connected devices when the TV provides them. Inputs with nothing switched on are dimmed.
  The remote's Input button opens the same menu.
  The button is hidden on boxes without inputs.
- **The remote's settings button** opens the TV settings.
- **Fast and light**, and **reversible**: a backup is made before any change.

The installer speaks English and French. The home screen itself is currently in French.

## Compatibility

Android TV / Google TV, Android 8 or newer. The installer recognizes common brands (TCL,
Vestel/Toshiba…) for the slimming step; on others, it applies a safe baseline and asks you about
the rest. Some TVs protect the "Source" button at system level: it will then keep the original
menu.

## Undo everything

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/Leptitluc/android-apple-tv/main/restaurer.sh)
```

or, from a clone: `./restaurer.sh`. Disabled apps are re-enabled, original settings restored and
Accueil TV uninstalled. It reuses the language chosen during install.

## Build it yourself (optional)

```bash
# macOS
brew install --cask temurin android-commandlinetools android-platform-tools
sdkmanager "build-tools;34.0.0" "platforms;android-34"
./build.sh      # produces app/AccueilTV.apk
```

No Gradle: `build.sh` chains aapt2 → javac → d8 → zipalign → apksigner. Keep the
`accueil-tv.keystore` file created on the first build to sign future versions with the same key.

## Security and privacy

No account, no trackers. All changes go through `pm disable-user` (reversible), never
uninstalling a system app. Wallpapers are downloaded on the TV from Wikimedia Commons.

## License

MIT — see [LICENSE](LICENSE).
