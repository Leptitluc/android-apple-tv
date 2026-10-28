# Accueil TV

**English** · [Français](README.fr.md)

**A fast, ad-free home screen for Android TV / Google TV, Apple TV style.**
A dock of your favorite apps, rotating wallpapers, your latest shows in "Up Next", a Control Center,
and the remote's buttons wired to it all.
Open source (MIT). Nothing is uninstalled, everything can be undone.

![Accueil TV home screen](docs/screenshots/accueil.jpg)

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

<table>
  <tr><td width="50%"><img src="docs/screenshots/a-suivre.jpg" alt="Up Next full screen"><br><sub><b>Up Next</b>: rest on an app in the dock to see its shows</sub></td><td width="50%"><img src="docs/screenshots/grille.jpg" alt="App grid"><br><sub><b>All your apps</b> under the dock, in the order you choose</sub></td></tr>
  <tr><td width="50%"><img src="docs/screenshots/centre-de-controle.jpg" alt="Control Center"><br><sub><b>Control Center</b>: volume, inputs, sleep, network</sub></td><td width="50%"><img src="docs/screenshots/entrees.jpg" alt="Inputs menu"><br><sub><b>Inputs</b>: the ones with nothing switched on are dimmed</sub></td></tr>
  <tr><td width="50%"><img src="docs/screenshots/menu-appli.jpg" alt="App menu"><br><sub><b>Hold OK</b> on a tile: move it, add it to the dock, hide it</sub></td><td width="50%"><img src="docs/screenshots/reglages.jpg" alt="Quick menu"><br><sub><b>Quick menu</b>: wallpapers, Up Next, apps, sounds</sub></td></tr>
  <tr><td width="50%"><img src="docs/screenshots/economiseur.jpg" alt="Screensaver"><br><sub><b>Screensaver</b>: your wallpapers, the time and the place</sub></td></tr>
</table>

- **No ads.** No sponsored rows, no banners.
- **A dock** of your recent apps, with a grid of all your apps lined up below. Hold OK on a tile to add
  it to the dock, remove it, or choose « Déplacer » (Move) to put it where you want, like on Apple TV.
  Once you change it, the dock keeps only your choices (1 to 6 apps, centered).
- **tvOS-style focus**: the selected tile leans slightly toward the arrow you pressed, then settles
  back flat while a soft light sheen glides across it. Turned off when the TV's animations are off.
- **Navigation sounds**: a soft tick when you move, a fuller one when you select, like Apple TV.
  Can be turned off in the home screen's quick menu.
- **Wallpapers** that rotate (nature, art, architecture, night… adjustable), with credits. The
  « Selon l'heure » (by time of day) category shows bright landscapes by day and night skies in the evening.
- **"Up Next"**: rest on a dock app and its latest shows appear full screen, like the tvOS top shelf,
  resumed where you left off.
- **Quick menu** on the home screen: wallpaper categories, duration, which apps feed "Up Next",
  show/hide apps, and "More settings".
- **TV inputs** (HDMI, AV, antenna…) one click away, next to the clock, with the names of
  connected devices when the TV provides them. Inputs with nothing switched on are dimmed.
  The remote's Input button opens the same menu.
  The button is hidden on boxes without inputs.
- **Control Center**, like on Apple TV: hold the remote's settings button (or use the switches icon
  next to the clock) to open a panel over any app, with volume, inputs, a sleep timer, sleep,
  network and settings. Sleep options only appear on TVs that support them.
- **Now playing**: when music or a video is playing, its artwork and title show next to the clock,
  and Control Center gets play/pause, previous and next buttons.
- **An Apple TV-style screensaver**: your wallpapers with a slow zoom, the time, and the place shown
  for a few seconds on each picture, like tvOS aerials. The installer selects it for you.
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

The installer grants Accueil TV notification access: Android only lets an app see what is playing
(title, artwork, play/pause) through it. Accueil TV reads media sessions only, keeps nothing and
sends nothing. It also makes the Accueil TV screensaver the active one. `restaurer.sh` removes the
notification access and puts the original screensaver settings back.

## License

MIT — see [LICENSE](LICENSE).
