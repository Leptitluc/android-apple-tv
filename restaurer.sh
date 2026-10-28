#!/usr/bin/env bash
# Accueil TV - tout remettre comme avant (réversible).
#   bash <(curl -fsSL https://raw.githubusercontent.com/Leptitluc/android-apple-tv/main/restaurer.sh)
# ou depuis un clone :  ./restaurer.sh [IP]
set -u
PKG=com.lucb.tvhome
ADB="$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")"
[ -x "$ADB" ] || { echo "adb introuvable."; exit 1; }

DIR="${ACCUEIL_TV_DIR:-$HOME/AccueilTV}"
SD=""; __src="${BASH_SOURCE[0]:-}"; [ -n "$__src" ] && [ -f "$__src" ] && SD="$(cd "$(dirname "$__src")" 2>/dev/null && pwd || true)"
[ -n "$SD" ] && ls -d "$SD"/sauvegarde-*/ >/dev/null 2>&1 && DIR="$SD"
BACKUP=""
for d in $(ls -d "$DIR"/sauvegarde-*/ 2>/dev/null | sort); do [ -f "$d/capture-avant.png" ] && { BACKUP="${d%/}"; break; }; done
[ -z "$BACKUP" ] && { echo "Aucune sauvegarde trouvée dans $DIR."; exit 1; }
TARGET="${1:-$(cat "$BACKUP/cible.txt")}"
echo "Restauration depuis $(basename "$BACKUP")"
"$ADB" connect "$TARGET" >/dev/null 2>&1
S=("$ADB" -s "$TARGET")
[ "$("${S[@]}" get-state 2>/dev/null)" = "device" ] || { echo "Télé non connectée ($TARGET)."; exit 1; }
sh() { "${S[@]}" shell "$@" | tr -d '\r'; }

TMP="$(mktemp -d)"
sed 's/^package://' "$BACKUP/paquets-deja-desactives.txt" | sort > "$TMP/avant"
sh pm list packages -d | sed 's/^package://' | sort > "$TMP/maintenant"
for p in $(comm -13 "$TMP/avant" "$TMP/maintenant"); do echo "réactive $p -> $(sh pm enable --user 0 "$p" 2>&1)"; done
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  v="$(grep "^$k=" "$BACKUP/reglages-global.txt" | cut -d= -f2)"
  [ -n "$v" ] && sh settings put global "$k" "$v" || sh settings delete global "$k" >/dev/null
done
for k in screensaver_enabled screensaver_activate_on_sleep screensaver_activate_on_dock screensaver_components; do
  v="$(grep "^$k=" "$BACKUP/reglages-secure.txt" | cut -d= -f2-)"; [ -n "$v" ] && sh settings put secure "$k" "$v"
done
v="$(grep "^enabled_accessibility_services=" "$BACKUP/reglages-secure.txt" | cut -d= -f2-)"
if [ -n "$v" ] && [ "$v" != "null" ]; then sh settings put secure enabled_accessibility_services "$v"; else sh settings delete secure enabled_accessibility_services >/dev/null; fi
sh settings put secure accessibility_enabled "$(grep "^accessibility_enabled=" "$BACKUP/reglages-secure.txt" | cut -d= -f2- || echo 0)"
HOME_ACT="$(sed -n '2p' "$BACKUP/launcher-actuel.txt")"
if sh pm list packages | grep -qx "package:$PKG"; then
  [ -n "$HOME_ACT" ] && sh cmd package set-home-activity "$HOME_ACT" >/dev/null
  sh pm uninstall "$PKG" >/dev/null
fi
rm -rf "$TMP"
sh input keyevent KEYCODE_HOME
echo "Restauration terminée : accueil d'origine rétabli, Accueil TV désinstallé."
