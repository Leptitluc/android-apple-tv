#!/usr/bin/env bash
# Accueil TV - tout remettre comme avant (réversible).
#   bash <(curl -fsSL https://raw.githubusercontent.com/Leptitluc/android-apple-tv/main/restaurer.sh)
# ou depuis un clone :  ./restaurer.sh [IP]
# Reprend la langue choisie à l'installation ; ACCUEIL_TV_LANG=fr|en pour la fixer.
set -u
PKG=com.lucb.tvhome

DIR="${ACCUEIL_TV_DIR:-$HOME/AccueilTV}"
SD=""; __src="${BASH_SOURCE[0]:-}"; [ -n "$__src" ] && [ -f "$__src" ] && SD="$(cd "$(dirname "$__src")" 2>/dev/null && pwd || true)"
[ -n "$SD" ] && ls -d "$SD"/sauvegarde-*/ >/dev/null 2>&1 && DIR="$SD"
BACKUP=""
for d in $(ls -d "$DIR"/sauvegarde-*/ 2>/dev/null | sort); do [ -f "$d/capture-avant.png" ] && { BACKUP="${d%/}"; break; }; done

# --- Langue / Language
L="${ACCUEIL_TV_LANG:-}"
[ -z "$L" ] && [ -n "$BACKUP" ] && [ -f "$BACKUP/langue.txt" ] && L="$(cat "$BACKUP/langue.txt")"
if [ "$L" != fr ] && [ "$L" != en ]; then
  case "${LC_ALL:-${LC_MESSAGES:-${LANG:-}}}" in fr*) d=1;; *) d=2;; esac
  printf 'Langue / Language :  1) Français  2) English  [%s] ' "$d"
  choice=""; { IFS= read -r choice < /dev/tty; } 2>/dev/null || { IFS= read -r choice; } 2>/dev/null || true
  case "${choice:-$d}" in 1|fr|FR) L=fr;; *) L=en;; esac
fi
m() { if [ "$L" = en ]; then printf '%s' "$2"; else printf '%s' "$1"; fi; }
say() { printf '%s\n' "$(m "$1" "$2")"; }

ADB="$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")"
[ -x "$ADB" ] || { say "adb introuvable." "adb not found."; exit 1; }
[ -z "$BACKUP" ] && { say "Aucune sauvegarde trouvée dans $DIR." "No backup found in $DIR."; exit 1; }
TARGET="${1:-$(cat "$BACKUP/cible.txt")}"
say "Restauration depuis $(basename "$BACKUP")" "Restoring from $(basename "$BACKUP")"
"$ADB" connect "$TARGET" >/dev/null 2>&1
S=("$ADB" -s "$TARGET")
[ "$("${S[@]}" get-state 2>/dev/null)" = "device" ] || { say "Télé non connectée ($TARGET)." "TV not connected ($TARGET)."; exit 1; }
sh() { "${S[@]}" shell "$@" | tr -d '\r'; }

TMP="$(mktemp -d)"
sed 's/^package://' "$BACKUP/paquets-deja-desactives.txt" | sort > "$TMP/avant"
sh pm list packages -d | sed 's/^package://' | sort > "$TMP/maintenant"
for p in $(comm -13 "$TMP/avant" "$TMP/maintenant"); do echo "$(m "réactive" "re-enabling") $p -> $(sh pm enable --user 0 "$p" 2>&1)"; done
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  v="$(grep "^$k=" "$BACKUP/reglages-global.txt" | cut -d= -f2)"
  [ -n "$v" ] && sh settings put global "$k" "$v" || sh settings delete global "$k" >/dev/null
done
for k in screensaver_enabled screensaver_activate_on_sleep screensaver_activate_on_dock screensaver_components; do
  v="$(grep "^$k=" "$BACKUP/reglages-secure.txt" | cut -d= -f2-)"
  if [ -n "$v" ]; then sh settings put secure "$k" "$v"; else sh settings delete secure "$k" >/dev/null; fi
done
v="$(grep "^enabled_accessibility_services=" "$BACKUP/reglages-secure.txt" | cut -d= -f2-)"
if [ -n "$v" ] && [ "$v" != "null" ]; then sh settings put secure enabled_accessibility_services "$v"; else sh settings delete secure enabled_accessibility_services >/dev/null; fi
sh settings put secure accessibility_enabled "$(grep "^accessibility_enabled=" "$BACKUP/reglages-secure.txt" | cut -d= -f2- || echo 0)"
HOME_ACT="$(sed -n '2p' "$BACKUP/launcher-actuel.txt")"
if sh pm list packages | grep -qx "package:$PKG"; then
  sh cmd notification disallow_listener "$PKG/$PKG.MediaListener" >/dev/null 2>&1
  [ -n "$HOME_ACT" ] && sh cmd package set-home-activity "$HOME_ACT" >/dev/null
  sh pm uninstall "$PKG" >/dev/null
fi
rm -rf "$TMP"
sh input keyevent KEYCODE_HOME
say "Restauration terminée : accueil d'origine rétabli, Accueil TV désinstallé." \
    "Restore complete: original home screen back, Accueil TV uninstalled."
