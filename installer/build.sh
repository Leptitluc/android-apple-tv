#!/usr/bin/env bash
# Construit les installateurs graphiques :
#   dist/AccueilTV-Installer-macOS.dmg   (appli universelle Intel + Apple Silicon)
#   dist/AccueilTV-Installer-Windows.exe
# Besoin : Go 1.22+ (brew install go), sur macOS (sips, iconutil, qlmanage, codesign, hdiutil).
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${1:-1.0.0}"
APP="Installer Accueil TV"
rm -rf dist build && mkdir -p dist build

# --- Icône : SVG -> PNG 1024 -> .icns (macOS) et .png (Windows)
qlmanage -t -s 1024 -o build web/icon.svg >/dev/null 2>&1
mv build/icon.svg.png build/icon.png
mkdir -p build/AppIcon.iconset
for s in 16 32 128 256 512; do
  sips -z $s $s build/icon.png --out build/AppIcon.iconset/icon_${s}x${s}.png >/dev/null
  sips -z $((s * 2)) $((s * 2)) build/icon.png --out build/AppIcon.iconset/icon_${s}x${s}@2x.png >/dev/null
done
iconutil -c icns build/AppIcon.iconset -o build/AppIcon.icns

# --- Windows : icône intégrée à l'exe, sans fenêtre de console
for s in 256 48 32 16; do sips -z $s $s build/icon.png --out build/icon$s.png >/dev/null; done
python3 -I tools/winres.py rsrc_windows_amd64.syso build/icon256.png build/icon48.png build/icon32.png build/icon16.png
trap 'rm -f rsrc_windows_amd64.syso' EXIT
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w -H=windowsgui" \
  -o dist/AccueilTV-Installer-Windows.exe .
rm -f rsrc_windows_amd64.syso

# --- macOS : binaire universel dans une appli, puis image disque
for arch in arm64 amd64; do
  GOOS=darwin GOARCH=$arch CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o build/mac-$arch .
done
BUNDLE="build/dmg/$APP.app"
mkdir -p "$BUNDLE/Contents/MacOS" "$BUNDLE/Contents/Resources"
python3 -I tools/fatmacho.py "$BUNDLE/Contents/MacOS/installer" build/mac-arm64 build/mac-amd64
chmod +x "$BUNDLE/Contents/MacOS/installer"
cp build/AppIcon.icns "$BUNDLE/Contents/Resources/"
cat > "$BUNDLE/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>$APP</string>
  <key>CFBundleDisplayName</key><string>$APP</string>
  <key>CFBundleIdentifier</key><string>com.lucb.tvhome.installer</string>
  <key>CFBundleExecutable</key><string>installer</string>
  <key>CFBundleIconFile</key><string>AppIcon</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>$VERSION</string>
  <key>CFBundleVersion</key><string>$VERSION</string>
  <key>LSMinimumSystemVersion</key><string>12.0</string>
  <key>LSUIElement</key><true/>
</dict>
</plist>
PLIST
# Signature ad hoc (obligatoire sur Apple Silicon) ; sans compte développeur Apple, pas de notarisation.
codesign --force --deep -s - "$BUNDLE"
hdiutil create -quiet -volname "Accueil TV" -srcfolder build/dmg -ov -format UDZO dist/AccueilTV-Installer-macOS.dmg

ls -lh dist
