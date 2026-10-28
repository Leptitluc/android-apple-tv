# Accueil TV

**Un accueil rapide et sans pub pour Android TV / Google TV, façon Apple TV.**
Un dock de tes applis récentes, des fonds d'écran qui tournent, une rangée « À suivre » de tes
derniers programmes, et le bouton réglages de la télécommande qui ouvre directement les paramètres.
Open source (MIT). Rien n'est désinstallé, tout est réversible.

---

## Installation en une commande

Sur **ton ordinateur** (même Wi-Fi que la télé), colle ceci dans un terminal :

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/Leptitluc/android-apple-tv/main/install.sh)
```

L'assistant te guide pas à pas : il explique comment activer le débogage sur la télé, trouve
l'appareil, **fait une sauvegarde**, allège le système (pubs, télémétrie, applis inutiles — en te
demandant pour celles que tu pourrais utiliser), installe Accueil TV, met en place le bouton
réglages et le définit comme accueil par défaut. Environ 5 minutes.

### À faire d'abord sur la télé (une seule fois)

1. **Paramètres → Système → À propos**, puis appuie **7 fois** sur « Version d'Android TV OS » (ou
   « Numéro de build ») jusqu'au message « Vous êtes développeur ».
2. **Paramètres → Système → Options pour les développeurs → Débogage USB** : active-le.
3. Note l'adresse IP : **Paramètres → Réseau et Internet**.

Pendant l'installation, la télé affichera **« Autoriser le débogage USB ? »** : accepte et coche
**« Toujours autoriser »**.

### Sans curl (en clonant le dépôt)

```bash
git clone https://github.com/Leptitluc/android-apple-tv.git
cd android-apple-tv
./install.sh            # ou ./install.sh 192.168.1.20
```

---

## Ce que tu obtiens

- **Pas de pub.** Aucune rangée sponsorisée, aucune bannière.
- **Un dock** de tes applis récentes, et une grille de toutes les applis alignée dessous.
- **Des fonds d'écran** qui tournent (nature, art, architecture, nuit… réglable), avec crédits.
- **« À suivre »** : tes derniers programmes repris là où tu t'étais arrêté, en plein écran façon tvOS.
- **Menu rapide** sur l'accueil : catégories de fond d'écran, durée, quelles applis nourrissent
  « À suivre », afficher/masquer les applis, et « Plus de réglages ».
- **Le bouton réglages** de la télécommande ouvre les paramètres de la télé.
- **Rapide et léger**, et **réversible** : une sauvegarde est faite avant toute modification.

## Compatibilité

Android TV / Google TV, Android 8 ou plus récent. L'installeur reconnaît les marques courantes
(TCL, Vestel/Toshiba…) pour l'allègement ; sur les autres, il applique une base sûre et te demande
pour le reste. Certaines télés protègent le bouton « Source » au niveau système : il restera alors
sur le menu d'origine.

## Tout annuler

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/Leptitluc/android-apple-tv/main/restaurer.sh)
```

ou, depuis un clone : `./restaurer.sh`. Les applis désactivées sont réactivées, les réglages
d'origine rétablis et Accueil TV désinstallé.

## Construire soi-même (facultatif)

```bash
# macOS
brew install --cask temurin android-commandlinetools android-platform-tools
sdkmanager "build-tools;34.0.0" "platforms;android-34"
./build.sh      # produit app/AccueilTV.apk
```

Pas de Gradle : `build.sh` enchaîne aapt2 → javac → d8 → zipalign → apksigner. Garde le fichier
`accueil-tv.keystore` créé au premier build pour signer tes futures versions avec la même clé.

## Sécurité et vie privée

Aucun compte, aucun traqueur. Les modifications passent toutes par `pm disable-user` (réversible),
jamais de désinstallation d'appli système. Les fonds d'écran sont téléchargés sur la télé depuis
Wikimedia Commons.

## Licence

MIT — voir [LICENSE](LICENSE).
