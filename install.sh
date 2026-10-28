#!/usr/bin/env bash
# Accueil TV - installeur guidé, universel, pour n'importe quelle Android TV.
# Usage :
#   bash <(curl -fsSL https://raw.githubusercontent.com/Leptitluc/android-apple-tv/main/install.sh)
# ou, depuis un clone du dépôt :
#   ./install.sh [IP_DE_LA_TELE]
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
ask() { local a=""; printf '%s [o/N] ' "$1"; { read -r a < /dev/tty; } 2>/dev/null || { read -r a; } 2>/dev/null || true; case "$a" in o|O|oui|Oui|OUI) return 0;; *) return 1;; esac; }

clear 2>/dev/null || true
c_b "────────────────────────────────────────────"
c_b "   Accueil TV — installation guidée"
c_b "────────────────────────────────────────────"
echo "Un accueil rapide et sans pub pour ta télé, façon Apple TV."
echo "Rien n'est désinstallé, tout est réversible, et une sauvegarde est faite avant toute modification."
echo

# --- adb
ADB="$(command -v adb || true)"
if [ -z "$ADB" ]; then
  for p in "$HOME/Library/Android/sdk/platform-tools/adb" "$HOME/Android/Sdk/platform-tools/adb"; do
    [ -x "$p" ] && ADB="$p" && break
  done
fi
if [ -z "$ADB" ]; then
  c_y "adb (les outils Android) n'est pas installé."
  if [ "$(uname)" = "Darwin" ]; then
    if command -v brew >/dev/null 2>&1 && ask "L'installer maintenant avec Homebrew ?"; then
      brew install --quiet android-platform-tools && ADB="$(command -v adb)"
    else
      echo "Installe-le puis relance :  brew install android-platform-tools"; exit 1
    fi
  else
    if command -v apt-get >/dev/null 2>&1 && ask "L'installer maintenant (sudo apt) ?"; then
      sudo apt-get update -qq && sudo apt-get install -y -qq android-tools-adb && ADB="$(command -v adb)"
    else
      echo "Installe adb (paquet android-tools-adb ou android-platform-tools) puis relance."; exit 1
    fi
  fi
fi
[ -n "$ADB" ] && [ -x "$ADB" ] || { echo "adb toujours introuvable. Abandon."; exit 1; }

# --- Activer le débogage sur la télé
step "1. Préparer la télé"
cat <<'TXT'
Sur la télé, une seule fois :
  1. Paramètres → Système → À propos.
  2. Descends sur « Version d'Android TV OS » (ou « Numéro de build ») et appuie 7 fois dessus
     jusqu'au message « Vous êtes développeur ».
  3. Reviens dans Système → Options pour les développeurs → active « Débogage USB ».
  4. Note l'adresse IP : Paramètres → Réseau et Internet (ex. 192.168.1.20).
TXT
echo
printf "Adresse IP de la télé : "
TARGET="${1:-}"
[ -z "$TARGET" ] && rd TARGET
[ -z "$TARGET" ] && { echo "Pas d'adresse IP, abandon."; exit 1; }
[[ "$TARGET" == *:* ]] || TARGET="$TARGET:5555"

# --- Connexion
step "2. Connexion à la télé"
"$ADB" disconnect "$TARGET" >/dev/null 2>&1
"$ADB" connect "$TARGET"
c_y "Si la télé affiche « Autoriser le débogage USB ? », accepte et coche « Toujours autoriser »."
ST=""
for _ in $(seq 1 30); do ST="$("$ADB" -s "$TARGET" get-state 2>/dev/null)"; [ "$ST" = device ] && break; sleep 2; done
[ "$ST" = device ] || { echo "Connexion non autorisée ($TARGET). Vérifie l'IP, le Wi-Fi et le débogage, puis relance."; exit 1; }
S=("$ADB" -s "$TARGET")
sh() { "${S[@]}" shell "$@" | tr -d '\r'; }
cap() { "${S[@]}" exec-out screencap -p | perl -0777 -pe 's/^.*?(?=\x89PNG)//s' 2>/dev/null > "$1"; }
BRAND="$(sh getprop ro.product.brand | tr 'A-Z' 'a-z')"
MANUF="$(sh getprop ro.product.manufacturer | tr 'A-Z' 'a-z')"
MODEL="$(sh getprop ro.product.model)"
VENDOR="$BRAND $MANUF"
c_g "Connecté : $MODEL"

# --- Dossier de travail (sauvegarde + journaux)
DIR="${ACCUEIL_TV_DIR:-$HOME/AccueilTV}"
SD=""; __src="${BASH_SOURCE[0]:-}"; [ -n "$__src" ] && [ -f "$__src" ] && SD="$(cd "$(dirname "$__src")" 2>/dev/null && pwd || true)"
[ -n "$SD" ] && [ -f "$SD/app/AccueilTV.apk" ] && DIR="$SD"
mkdir -p "$DIR"

# --- Sauvegarde
step "3. Sauvegarde (aucune modification pour l'instant)"
BACKUP=""
for d in $(ls -d "$DIR"/sauvegarde-*/ 2>/dev/null | sort); do [ -f "$d/capture-avant.png" ] && { BACKUP="${d%/}"; break; }; done
if [ -n "$BACKUP" ]; then
  c_g "Sauvegarde existante réutilisée : $(basename "$BACKUP")"
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
  c_g "Sauvegarde faite : $BACKUP"
fi
LOG="$BACKUP/journal-install.txt"; : > "$LOG"
INSTALLED="$(sh pm list packages | sed 's/^package://')"
has() { echo "$INSTALLED" | grep -qx "$1"; }

# --- Allègement
step "4. Alléger : pubs, télémétrie, applis inutiles (réversible)"
NEVER='launcher|leanbacklauncher|tvlauncher|setupwraith|systemui|inputmethod|packageinstaller|vending|gms|webview|providers|tvinput|tv\.service|tvsettings|com\.android\.tv\.settings|katniss|mediashell|keychain|permission|wallpaper'
AUTO=(com.google.android.feedback com.android.printspooler com.google.android.tvrecommendations)
for p in $(echo "$INSTALLED" | grep -iE 'demo|retail|reporter|logkit|salestool|shopmode|\.bi$|oobe' | grep -ivE "$NEVER"); do AUTO+=("$p"); done
case "$VENDOR" in
  *tcl*) AUTO+=(com.tcl.bi com.tcl.logkit com.tcl.overseasappshow com.tcl.waterfall.overseas com.tcl.esticker com.tcl.messagebox com.tcl.ocean.instructions com.tcl.smartalexa com.tcl.miracast com.tcl.MultiScreenInteraction_TV com.tcl.channelplus com.tcl.gallery com.tcl.ui_mediaCenter com.tcl.gamebar com.martsnew.tv) ;;
  *vestel*|*toshiba*) AUTO+=(com.vestel.vestelreporter fusion.android.tv.demo android.autoinstalls.config.vestel.cosmos com.vestel.tv.eib com.mediatek.tv.freeviewplay) ;;
esac
ASK=("com.cltv.fast|Chaînes gratuites avec pub (FAST)" "com.google.android.youtube.tvmusic|YouTube Music" "com.google.android.videos|Google TV / Play Films" "com.amazon.amazonvideo.livingroom|Prime Video" "com.google.android.play.games|Play Jeux")
off() { echo "$1" | grep -qiE "$NEVER" && return; has "$1" || return; echo "$1 -> $(sh pm disable-user --user 0 "$1" 2>&1)" >> "$LOG"; printf '  désactivé : %s\n' "$1"; }
for p in $(printf '%s\n' "${AUTO[@]}" | awk 'NF && !s[$0]++'); do off "$p"; done
echo "Applis que tu pourrais utiliser (réponds o pour désactiver, Entrée pour garder) :"
for e in "${ASK[@]}"; do p="${e%%|*}"; l="${e#*|}"; has "$p" || continue; ask "  Désactiver $l ?" && off "$p"; done
for k in window_animation_scale transition_animation_scale animator_duration_scale; do sh settings put global "$k" 0.5; done
for k in screensaver_enabled screensaver_activate_on_sleep screensaver_activate_on_dock; do sh settings put secure "$k" 0; done
for p in com.google.android.backdrop com.google.android.apps.tv.dreamx; do has "$p" && sh pm disable-user --user 0 "$p" >/dev/null 2>&1; done
c_g "Allègement et économiseur d'écran : faits."

# --- APK
step "5. Installer Accueil TV"
APK="$DIR/app/AccueilTV.apk"
[ -f "$APK" ] || APK="$(ls -t "$DIR"/AccueilTV*.apk 2>/dev/null | head -1)"
if [ -z "${APK:-}" ] || [ ! -f "$APK" ]; then
  APK="$(mktemp -d)/AccueilTV.apk"
  echo "Téléchargement de l'application..."
  curl -fsSL "$RAW_BASE/app/AccueilTV.apk" -o "$APK" || { echo "Téléchargement impossible depuis $RAW_BASE"; exit 1; }
fi
OUT="$("${S[@]}" install -r -d "$APK" 2>&1)"; echo "$OUT" >> "$LOG"
if echo "$OUT" | grep -qi "Success"; then :
elif echo "$OUT" | grep -qiE "UPDATE_INCOMPATIBLE|signatures do not match|INCONSISTENT_CERTIFICATE"; then
  c_y "Une version d'Accueil TV signée différemment est déjà installée sur la télé."
  if ask "La remplacer ? (seuls les réglages de l'accueil sont réinitialisés, le reste de la télé est intact)"; then
    sh pm uninstall "$PKG" >/dev/null 2>&1
    "${S[@]}" install "$APK" >>"$LOG" 2>&1 || { echo "Échec de l'installation. Détails : $LOG"; exit 1; }
  else echo "Installation annulée."; exit 1; fi
elif echo "$OUT" | grep -qiE "Risky app blocked|FAILED_VERIFICATION"; then
  c_y "Play Protect bloque sur la télé : Plus de détails → Installer quand même, puis relance ce script."; exit 1
else
  echo "Échec de l'installation :"; echo "$OUT" | tail -3; echo "Détails : $LOG"; exit 1
fi
sh pm grant $PKG android.permission.READ_TV_LISTINGS >/dev/null 2>&1
sh pm grant $PKG android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1
sh appops set $PKG APP_AUTO_START allow >/dev/null 2>&1
c_g "Installé : $(sh dumpsys package $PKG | grep -m1 versionName | tr -d ' ')"

# --- Bouton réglages
step "6. Bouton réglages de la télécommande"
CUR="$(sh settings get secure enabled_accessibility_services)"
case "$CUR" in *"$SERVICE"*) NEW="$CUR";; ""|null) NEW="$SERVICE";; *) NEW="$CUR:$SERVICE";; esac
sh settings delete secure enabled_accessibility_services >/dev/null; sleep 1
sh settings put secure enabled_accessibility_services "$NEW"
sh settings put secure accessibility_enabled 1
ACTIF=0; for _ in $(seq 1 15); do sh dumpsys accessibility | grep -E "Bound services" | grep -qE "KeyService|Accueil TV" && { ACTIF=1; break; }; sleep 1; done
[ "$ACTIF" = 1 ] && c_g "Bouton réglages : actif" || c_y "Bouton réglages : inactif (il restera sur le menu d'origine)"

# --- Accueil par défaut
step "7. Mettre Accueil TV par défaut"
ORIG_HOME="$(sed -n '2p' "$BACKUP/launcher-actuel.txt")"
[ -z "$ORIG_HOME" ] && ORIG_HOME="$(sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | sed -n '2p')"
ORIG_PKG="${ORIG_HOME%%/*}"
DISABLED=()
resumed() { sh dumpsys activity activities | grep -m1 mResumedActivity; }
try_home() { sh cmd package set-home-activity "$HOME_ACTIVITY" >/dev/null 2>&1; sh input keyevent KEYCODE_HOME; sleep 7; resumed | grep -q "$PKG"; }
revert() { for p in "${DISABLED[@]+"${DISABLED[@]}"}"; do sh pm enable --user 0 "$p" >/dev/null; done; [ -n "$ORIG_HOME" ] && sh cmd package set-home-activity "$ORIG_HOME" >/dev/null; sh input keyevent KEYCODE_HOME; c_y "Accueil d'origine rétabli."; }
if try_home; then c_g "Accueil TV est l'accueil par défaut."
else
  echo "L'accueil d'origine ($ORIG_PKG) reprend le bouton Accueil."
  if ask "Le désactiver pour imposer Accueil TV ? (réversible)"; then
    sh pm disable-user --user 0 "$ORIG_PKG" >>"$LOG" 2>&1; DISABLED+=("$ORIG_PKG")
    if ! try_home; then
      SETUP="$(resumed | grep -oE '[a-zA-Z0-9._]+setupwraith')"
      if [ -n "$SETUP" ] && ask "L'écran de config Google bloque l'accueil. Le désactiver aussi ?"; then
        sh pm disable-user --user 0 "$SETUP" >>"$LOG" 2>&1; DISABLED+=("$SETUP")
        try_home && c_g "Accueil TV est l'accueil par défaut." || revert
      else revert; fi
    else c_g "Accueil TV est l'accueil par défaut."; fi
  else revert; fi
fi

sh input keyevent KEYCODE_HOME; sleep 4; cap "$BACKUP/capture-apres.png"
step "Terminé 🎉"
echo "Accueil TV est installé. Sauvegarde et journal : $BACKUP"
echo "Pour tout annuler : relance avec  ACCUEIL_TV_DIR=\"$DIR\"  et le script restaurer.sh,"
echo "ou télécharge-le :  bash <(curl -fsSL $RAW_BASE/restaurer.sh)"
