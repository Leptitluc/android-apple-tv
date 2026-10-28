#!/usr/bin/env bash
# Accueil TV - installeur guidé, universel, pour n'importe quelle Android TV.
# Usage :
#   bash <(curl -fsSL https://raw.githubusercontent.com/Leptitluc/android-apple-tv/main/install.sh)
# ou, depuis un clone du dépôt :
#   ./install.sh [IP_DE_LA_TELE]
# La langue est demandée au démarrage ; ACCUEIL_TV_LANG=fr|en pour la fixer.
set -u

# --- Où trouver l'APK si on n'est pas dans un clone (installeur lancé via curl)
REPO="${ACCUEIL_TV_REPO:-Leptitluc/android-apple-tv}"
RAW_BASE="https://raw.githubusercontent.com/$REPO/main"

PKG=com.lucb.tvhome
HOME_ACTIVITY=$PKG/.HomeActivity
SERVICE=$PKG/$PKG.KeyService

c_b() { printf '\033[1m%s\033[0m\n' "$1"; }
c_g() { printf '\033[32m%s\033[0m\n' "$1"; }
c_y() { printf '\033[33m%s\033[0m\n' "$1"; }
step() { printf '\n\033[1m== %s\033[0m\n' "$1"; }
# Lectures toujours depuis le terminal (marche même en bash <(curl ...))
rd() { local __x=""; { IFS= read -r __x < /dev/tty; } 2>/dev/null || { IFS= read -r __x; } 2>/dev/null || true; printf -v "$1" '%s' "$__x"; }
# Texte selon la langue choisie : m "français" "English"
m() { if [ "$L" = en ]; then printf '%s' "$2"; else printf '%s' "$1"; fi; }
say() { printf '%s\n' "$(m "$1" "$2")"; }
ask() { local a=""; printf '%s %s ' "$1" "$(m '[o/N]' '[y/N]')"; { read -r a < /dev/tty; } 2>/dev/null || { read -r a; } 2>/dev/null || true; case "$a" in o|O|oui|Oui|OUI|y|Y|yes|Yes|YES) return 0;; *) return 1;; esac; }

clear 2>/dev/null || true

# --- Langue / Language
L="${ACCUEIL_TV_LANG:-}"
if [ "$L" != fr ] && [ "$L" != en ]; then
  case "${LC_ALL:-${LC_MESSAGES:-${LANG:-}}}" in fr*) d=1;; *) d=2;; esac
  echo "Langue / Language :"
  echo "  1) Français"
  echo "  2) English"
  printf '[%s] ' "$d"
  rd choice
  case "${choice:-$d}" in 1|fr|FR) L=fr;; *) L=en;; esac
  echo
fi

c_b "────────────────────────────────────────────"
c_b "$(m "   Accueil TV — installation guidée" "   Accueil TV — guided setup")"
c_b "────────────────────────────────────────────"
say "Un accueil rapide et sans pub pour ta télé, façon Apple TV." \
    "A fast, ad-free home screen for your TV, Apple TV style."
say "Rien n'est désinstallé, tout est réversible, et une sauvegarde est faite avant toute modification." \
    "Nothing is uninstalled, everything can be undone, and a backup is made before any change."
echo

# --- adb
ADB="$(command -v adb || true)"
if [ -z "$ADB" ]; then
  for p in "$HOME/Library/Android/sdk/platform-tools/adb" "$HOME/Android/Sdk/platform-tools/adb"; do
    [ -x "$p" ] && ADB="$p" && break
  done
fi
if [ -z "$ADB" ]; then
  c_y "$(m "adb (les outils Android) n'est pas installé." "adb (the Android tools) is not installed.")"
  if [ "$(uname)" = "Darwin" ]; then
    if command -v brew >/dev/null 2>&1 && ask "$(m "L'installer maintenant avec Homebrew ?" "Install it now with Homebrew?")"; then
      brew install --quiet android-platform-tools && ADB="$(command -v adb)"
    else
      say "Installe-le puis relance :  brew install android-platform-tools" \
          "Install it, then run this again:  brew install android-platform-tools"; exit 1
    fi
  else
    if command -v apt-get >/dev/null 2>&1 && ask "$(m "L'installer maintenant (sudo apt) ?" "Install it now (sudo apt)?")"; then
      sudo apt-get update -qq && sudo apt-get install -y -qq android-tools-adb && ADB="$(command -v adb)"
    else
      say "Installe adb (paquet android-tools-adb ou android-platform-tools) puis relance." \
          "Install adb (package android-tools-adb or android-platform-tools), then run this again."; exit 1
    fi
  fi
fi
[ -n "$ADB" ] && [ -x "$ADB" ] || { say "adb toujours introuvable. Abandon." "adb still not found. Aborting."; exit 1; }

# --- Activer le débogage sur la télé
step "$(m "1. Préparer la télé" "1. Prepare the TV")"
if [ "$L" = en ]; then
cat <<'TXT'
On the TV, just once:
  1. Settings → System → About.
  2. Scroll to "Android TV OS build" (or "Build") and press it 7 times
     until you see "You are now a developer!".
  3. Go back to System → Developer options → turn on "USB debugging".
  4. Note the IP address: Settings → Network & Internet (e.g. 192.168.1.20).
TXT
else
cat <<'TXT'
Sur la télé, une seule fois :
  1. Paramètres → Système → À propos.
  2. Descends sur « Version d'Android TV OS » (ou « Numéro de build ») et appuie 7 fois dessus
     jusqu'au message « Vous êtes développeur ».
  3. Reviens dans Système → Options pour les développeurs → active « Débogage USB ».
  4. Note l'adresse IP : Paramètres → Réseau et Internet (ex. 192.168.1.20).
TXT
fi
echo
printf '%s' "$(m "Adresse IP de la télé : " "TV IP address: ")"
TARGET="${1:-}"
[ -z "$TARGET" ] && rd TARGET
[ -z "$TARGET" ] && { say "Pas d'adresse IP, abandon." "No IP address, aborting."; exit 1; }
[[ "$TARGET" == *:* ]] || TARGET="$TARGET:5555"

# --- Connexion
step "$(m "2. Connexion à la télé" "2. Connecting to the TV")"
"$ADB" disconnect "$TARGET" >/dev/null 2>&1
"$ADB" connect "$TARGET"
c_y "$(m "Si la télé affiche « Autoriser le débogage USB ? », accepte et coche « Toujours autoriser »." \
         "If the TV shows \"Allow USB debugging?\", accept and tick \"Always allow from this computer\".")"
ST=""
for _ in $(seq 1 30); do ST="$("$ADB" -s "$TARGET" get-state 2>/dev/null)"; [ "$ST" = device ] && break; sleep 2; done
[ "$ST" = device ] || { say "Connexion non autorisée ($TARGET). Vérifie l'IP, le Wi-Fi et le débogage, puis relance." \
                            "Connection not authorized ($TARGET). Check the IP, the Wi-Fi and debugging, then run this again."; exit 1; }
S=("$ADB" -s "$TARGET")
sh() { "${S[@]}" shell "$@" | tr -d '\r'; }
cap() { "${S[@]}" exec-out screencap -p | perl -0777 -pe 's/^.*?(?=\x89PNG)//s' 2>/dev/null > "$1"; }
BRAND="$(sh getprop ro.product.brand | tr 'A-Z' 'a-z')"
MANUF="$(sh getprop ro.product.manufacturer | tr 'A-Z' 'a-z')"
MODEL="$(sh getprop ro.product.model)"
VENDOR="$BRAND $MANUF"
c_g "$(m "Connecté : $MODEL" "Connected: $MODEL")"

# --- Dossier de travail (sauvegarde + journaux)
DIR="${ACCUEIL_TV_DIR:-$HOME/AccueilTV}"
SD=""; __src="${BASH_SOURCE[0]:-}"; [ -n "$__src" ] && [ -f "$__src" ] && SD="$(cd "$(dirname "$__src")" 2>/dev/null && pwd || true)"
[ -n "$SD" ] && [ -f "$SD/app/AccueilTV.apk" ] && DIR="$SD"
mkdir -p "$DIR"

# --- Sauvegarde
step "$(m "3. Sauvegarde (aucune modification pour l'instant)" "3. Backup (nothing is changed yet)")"
BACKUP=""
for d in $(ls -d "$DIR"/sauvegarde-*/ 2>/dev/null | sort); do [ -f "$d/capture-avant.png" ] && { BACKUP="${d%/}"; break; }; done
if [ -n "$BACKUP" ]; then
  c_g "$(m "Sauvegarde existante réutilisée : $(basename "$BACKUP")" "Reusing existing backup: $(basename "$BACKUP")")"
else
  BACKUP="$DIR/sauvegarde-$(date +%Y%m%d-%H%M%S)"; mkdir -p "$BACKUP"
  echo "$TARGET" > "$BACKUP/cible.txt"
  sh getprop > "$BACKUP/props.txt"
  sh pm list packages -f > "$BACKUP/paquets-tous.txt"
  sh pm list packages -d > "$BACKUP/paquets-deja-desactives.txt"
  for ns in global secure system; do sh settings list "$ns" > "$BACKUP/reglages-$ns.txt"; done
  sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$BACKUP/launcher-actuel.txt"
  cap "$BACKUP/capture-avant.png"; : > "$BACKUP/capture-avant.png.ok" 2>/dev/null || true
  [ -s "$BACKUP/capture-avant.png" ] || echo "ok" > "$BACKUP/capture-avant.png"
  c_g "$(m "Sauvegarde faite : $BACKUP" "Backup done: $BACKUP")"
fi
echo "$L" > "$BACKUP/langue.txt"
LOG="$BACKUP/journal-install.txt"; : > "$LOG"
INSTALLED="$(sh pm list packages | sed 's/^package://')"
has() { echo "$INSTALLED" | grep -qx "$1"; }

# --- Allègement
step "$(m "4. Alléger : pubs, télémétrie, applis inutiles (réversible)" "4. Slim down: ads, telemetry, useless apps (reversible)")"
NEVER='launcher|leanbacklauncher|tvlauncher|setupwraith|systemui|inputmethod|packageinstaller|vending|gms|webview|providers|tvinput|tv\.service|tvsettings|com\.android\.tv\.settings|katniss|mediashell|keychain|permission|wallpaper'
AUTO=(com.google.android.feedback com.android.printspooler com.google.android.tvrecommendations)
for p in $(echo "$INSTALLED" | grep -iE 'demo|retail|reporter|logkit|salestool|shopmode|\.bi$|oobe' | grep -ivE "$NEVER"); do AUTO+=("$p"); done
case "$VENDOR" in
  *tcl*) AUTO+=(com.tcl.bi com.tcl.logkit com.tcl.overseasappshow com.tcl.waterfall.overseas com.tcl.esticker com.tcl.messagebox com.tcl.ocean.instructions com.tcl.smartalexa com.tcl.miracast com.tcl.MultiScreenInteraction_TV com.tcl.channelplus com.tcl.gallery com.tcl.ui_mediaCenter com.tcl.gamebar com.martsnew.tv) ;;
  *vestel*|*toshiba*) AUTO+=(com.vestel.vestelreporter fusion.android.tv.demo android.autoinstalls.config.vestel.cosmos com.vestel.tv.eib com.mediatek.tv.freeviewplay) ;;
esac
ASK=("com.cltv.fast|$(m "Chaînes gratuites avec pub (FAST)" "Free ad-supported channels (FAST)")" "com.google.android.youtube.tvmusic|YouTube Music" "com.google.android.videos|Google TV / Play Films" "com.amazon.amazonvideo.livingroom|Prime Video" "com.google.android.play.games|$(m "Play Jeux" "Play Games")")
off() { echo "$1" | grep -qiE "$NEVER" && return; has "$1" || return; echo "$1 -> $(sh pm disable-user --user 0 "$1" 2>&1)" >> "$LOG"; printf '  %s %s\n' "$(m "désactivé :" "disabled:")" "$1"; }
for p in $(printf '%s\n' "${AUTO[@]}" | awk 'NF && !s[$0]++'); do off "$p"; done
say "Applis que tu pourrais utiliser (réponds o pour désactiver, Entrée pour garder) :" \
    "Apps you might use (answer y to disable, Enter to keep):"
for e in "${ASK[@]}"; do p="${e%%|*}"; l="${e#*|}"; has "$p" || continue; ask "  $(m "Désactiver $l ?" "Disable $l?")" && off "$p"; done
for k in window_animation_scale transition_animation_scale animator_duration_scale; do sh settings put global "$k" 0.5; done
for k in screensaver_enabled screensaver_activate_on_sleep screensaver_activate_on_dock; do sh settings put secure "$k" 0; done
for p in com.google.android.backdrop com.google.android.apps.tv.dreamx; do has "$p" && sh pm disable-user --user 0 "$p" >/dev/null 2>&1; done
c_g "$(m "Allègement et économiseur d'écran : faits." "Slimming down and screensaver: done.")"

# --- APK
step "$(m "5. Installer Accueil TV" "5. Install Accueil TV")"
APK="$DIR/app/AccueilTV.apk"
[ -f "$APK" ] || APK="$(ls -t "$DIR"/AccueilTV*.apk 2>/dev/null | head -1)"
if [ -z "${APK:-}" ] || [ ! -f "$APK" ]; then
  APK="$(mktemp -d)/AccueilTV.apk"
  say "Téléchargement de l'application..." "Downloading the app..."
  curl -fsSL "$RAW_BASE/app/AccueilTV.apk" -o "$APK" || { say "Téléchargement impossible depuis $RAW_BASE" "Download failed from $RAW_BASE"; exit 1; }
fi
inst() { "${S[@]}" install -r -d "$APK" >>"$LOG" 2>&1; }
blocked() { c_y "$(m "Si la télé bloque (Play Protect) : Plus de détails → Installer quand même, puis relance." \
                     "If the TV blocks it (Play Protect): More details → Install anyway, then run this again.")"; exit 1; }
if ! inst; then
  # Version précédente signée avec une autre clé : il faut désinstaller avant de réinstaller.
  tail -3 "$LOG" | grep -q UPDATE_INCOMPATIBLE || blocked
  ask "$(m "Une ancienne version d'Accueil TV est installée et doit être remplacée (ses réglages seront remis à zéro). Continuer ?" \
           "An older Accueil TV is installed and must be replaced (its settings will be reset). Continue?")" \
    || { say "Installation annulée." "Install cancelled."; exit 1; }
  "${S[@]}" uninstall "$PKG" >>"$LOG" 2>&1
  inst || blocked
fi
sh pm grant $PKG android.permission.READ_TV_LISTINGS >/dev/null 2>&1
sh pm grant $PKG android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1
sh appops set $PKG APP_AUTO_START allow >/dev/null 2>&1
c_g "$(m "Installé" "Installed"): $(sh dumpsys package $PKG | grep -m1 versionName | tr -d ' ')"

# --- Bouton réglages
step "$(m "6. Bouton réglages de la télécommande" "6. Remote's settings button")"
CUR="$(sh settings get secure enabled_accessibility_services)"
case "$CUR" in *"$SERVICE"*) NEW="$CUR";; ""|null) NEW="$SERVICE";; *) NEW="$CUR:$SERVICE";; esac
sh settings delete secure enabled_accessibility_services >/dev/null; sleep 1
sh settings put secure enabled_accessibility_services "$NEW"
sh settings put secure accessibility_enabled 1
ACTIF=0; for _ in $(seq 1 15); do sh dumpsys accessibility | grep -E "Bound services" | grep -qE "KeyService|Accueil TV" && { ACTIF=1; break; }; sleep 1; done
[ "$ACTIF" = 1 ] && c_g "$(m "Bouton réglages : actif" "Settings button: active")" \
                 || c_y "$(m "Bouton réglages : inactif (il restera sur le menu d'origine)" "Settings button: inactive (it will keep the original menu)")"

# --- Accueil par défaut
step "$(m "7. Mettre Accueil TV par défaut" "7. Make Accueil TV the default home")"
ORIG_HOME="$(sed -n '2p' "$BACKUP/launcher-actuel.txt")"
[ -z "$ORIG_HOME" ] && ORIG_HOME="$(sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | sed -n '2p')"
ORIG_PKG="${ORIG_HOME%%/*}"
DISABLED=()
DONE_MSG="$(m "Accueil TV est l'accueil par défaut." "Accueil TV is the default home screen.")"
resumed() { sh dumpsys activity activities | grep -m1 mResumedActivity; }
try_home() { sh cmd package set-home-activity "$HOME_ACTIVITY" >/dev/null 2>&1; sh input keyevent KEYCODE_HOME; sleep 7; resumed | grep -q "$PKG"; }
revert() { for p in "${DISABLED[@]+"${DISABLED[@]}"}"; do sh pm enable --user 0 "$p" >/dev/null; done; [ -n "$ORIG_HOME" ] && sh cmd package set-home-activity "$ORIG_HOME" >/dev/null; sh input keyevent KEYCODE_HOME; c_y "$(m "Accueil d'origine rétabli." "Original home screen restored.")"; }
if try_home; then c_g "$DONE_MSG"
else
  say "L'accueil d'origine ($ORIG_PKG) reprend le bouton Accueil." \
      "The original home screen ($ORIG_PKG) takes back the Home button."
  if ask "$(m "Le désactiver pour imposer Accueil TV ? (réversible)" "Disable it to force Accueil TV? (reversible)")"; then
    sh pm disable-user --user 0 "$ORIG_PKG" >>"$LOG" 2>&1; DISABLED+=("$ORIG_PKG")
    if ! try_home; then
      SETUP="$(resumed | grep -oE '[a-zA-Z0-9._]+setupwraith')"
      if [ -n "$SETUP" ] && ask "$(m "L'écran de config Google bloque l'accueil. Le désactiver aussi ?" "Google's setup screen is blocking the home screen. Disable it too?")"; then
        sh pm disable-user --user 0 "$SETUP" >>"$LOG" 2>&1; DISABLED+=("$SETUP")
        try_home && c_g "$DONE_MSG" || revert
      else revert; fi
    else c_g "$DONE_MSG"; fi
  else revert; fi
fi

sh input keyevent KEYCODE_HOME; sleep 4; cap "$BACKUP/capture-apres.png"
step "$(m "Terminé 🎉" "Done 🎉")"
say "Accueil TV est installé. Sauvegarde et journal : $BACKUP" \
    "Accueil TV is installed. Backup and log: $BACKUP"
say "Pour tout annuler : relance avec  ACCUEIL_TV_DIR=\"$DIR\"  et le script restaurer.sh," \
    "To undo everything: run restaurer.sh with  ACCUEIL_TV_DIR=\"$DIR\" ,"
say "ou télécharge-le :  bash <(curl -fsSL $RAW_BASE/restaurer.sh)" \
    "or download it:  bash <(curl -fsSL $RAW_BASE/restaurer.sh)"
