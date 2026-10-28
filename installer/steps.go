package main

import (
	"bytes"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

// Mêmes règles que install.sh : ce qui n'est jamais désactivé, ce qui l'est d'office, et ce qu'on propose.
var (
	never   = regexp.MustCompile(`(?i)launcher|leanbacklauncher|tvlauncher|setupwraith|systemui|inputmethod|packageinstaller|vending|gms|webview|providers|tvinput|tv\.service|tvsettings|com\.android\.tv\.settings|katniss|mediashell|keychain|permission|wallpaper`)
	junk    = regexp.MustCompile(`(?i)demo|retail|reporter|logkit|salestool|shopmode|\.bi$|oobe`)
	autoOff = []string{"com.google.android.feedback", "com.android.printspooler", "com.google.android.tvrecommendations"}
	vendors = map[string][]string{
		"tcl":     {"com.tcl.bi", "com.tcl.logkit", "com.tcl.overseasappshow", "com.tcl.waterfall.overseas", "com.tcl.esticker", "com.tcl.messagebox", "com.tcl.ocean.instructions", "com.tcl.smartalexa", "com.tcl.miracast", "com.tcl.MultiScreenInteraction_TV", "com.tcl.channelplus", "com.tcl.gallery", "com.tcl.ui_mediaCenter", "com.tcl.gamebar", "com.martsnew.tv"},
		"vestel":  {"com.vestel.vestelreporter", "fusion.android.tv.demo", "android.autoinstalls.config.vestel.cosmos", "com.vestel.tv.eib", "com.mediatek.tv.freeviewplay"},
		"toshiba": {"com.vestel.vestelreporter", "fusion.android.tv.demo", "android.autoinstalls.config.vestel.cosmos", "com.vestel.tv.eib", "com.mediatek.tv.freeviewplay"},
	}
	optional = [][3]string{
		{"com.cltv.fast", "Chaînes gratuites avec pub (FAST)", "Free ad-supported channels (FAST)"},
		{"com.google.android.youtube.tvmusic", "YouTube Music", "YouTube Music"},
		{"com.google.android.videos", "Google TV / Play Films", "Google TV / Play Movies"},
		{"com.amazon.amazonvideo.livingroom", "Prime Video", "Prime Video"},
		{"com.google.android.play.games", "Play Jeux", "Play Games"},
	}
)

type device struct {
	Model, Brand, Serial, Version string
	Installed                     map[string]bool
}

var dev device

func packages(flag string) map[string]bool {
	set := map[string]bool{}
	args := []string{"pm", "list", "packages"}
	if flag != "" {
		args = append(args, flag)
	}
	for _, l := range strings.Split(sh(args...), "\n") {
		if p := strings.TrimPrefix(strings.TrimSpace(l), "package:"); p != "" {
			set[p] = true
		}
	}
	return set
}

func (j *job) connect(ip string) {
	ip = strings.TrimSpace(ip)
	if ip == "" {
		j.fail(j.m("Indique l'adresse IP de la télé.", "Enter the TV's IP address."))
		return
	}
	if !strings.Contains(ip, ":") {
		ip += ":5555"
	}
	if !j.ensureAdb() {
		j.fail(j.m("Vérifie la connexion Internet de l'ordinateur, puis réessaie.", "Check this computer's Internet connection, then try again."))
		return
	}
	target = ip
	j.step("connect", "run", j.m("Connexion à la télé…", "Connecting to the TV…"))
	adb("disconnect", target)
	out, _ := adb("connect", target)
	if strings.Contains(out, "failed") || strings.Contains(out, "cannot") || strings.Contains(out, "unable") {
		j.step("connect", "fail", j.m("La télé ne répond pas à cette adresse.", "The TV doesn't answer at this address."))
		j.fail(j.m("Vérifie l'adresse IP, que la télé et l'ordinateur sont sur le même Wi-Fi, et que le débogage USB est activé.",
			"Check the IP address, that the TV and this computer are on the same Wi-Fi, and that USB debugging is on."))
		return
	}
	asked := false
	for i := 0; i < 60; i++ {
		state, _ := adb("-s", target, "get-state")
		state = strings.TrimSpace(state)
		if state == "device" {
			break
		}
		if !asked && (strings.Contains(state, "unauthorized") || i >= 2) {
			asked = true
			j.step("connect", "wait", j.m("Sur la télé, accepte « Autoriser le débogage USB ? » et coche « Toujours autoriser ».",
				"On the TV, accept \"Allow USB debugging?\" and tick \"Always allow from this computer\"."))
		}
		if i > 0 && i%10 == 0 {
			adb("connect", target)
		}
		time.Sleep(2 * time.Second)
		if i == 59 {
			j.step("connect", "fail", j.m("Connexion non autorisée.", "Connection not authorized."))
			j.fail(j.m("La télé n'a pas autorisé la connexion. Recommence et accepte le message sur la télé.",
				"The TV did not allow the connection. Try again and accept the message on the TV."))
			return
		}
	}
	dev = device{
		Model:   sh("getprop", "ro.product.model"),
		Brand:   strings.ToLower(sh("getprop", "ro.product.brand") + " " + sh("getprop", "ro.product.manufacturer")),
		Serial:  sh("getprop", "ro.serialno"),
		Version: sh("getprop", "ro.build.version.release"),
	}
	dev.Installed = packages("")
	var opts []map[string]string
	for _, o := range optional {
		if dev.Installed[o[0]] {
			opts = append(opts, map[string]string{"pkg": o[0], "label": j.m(o[1], o[2])})
		}
	}
	current := ""
	if dev.Installed[pkg] {
		if m := regexp.MustCompile(`versionName=(\S+)`).FindStringSubmatch(sh("dumpsys", "package", pkg)); m != nil {
			current = m[1]
		}
	}
	j.step("connect", "ok", j.m("Connecté : ", "Connected: ")+dev.Model)
	j.finish(map[string]any{"model": dev.Model, "android": dev.Version, "optional": opts,
		"current": current, "backup": findBackup() != ""})
}

// Sauvegarde de cette télé (même numéro de série), au format de install.sh / restaurer.sh.
func findBackup() string {
	dirs, _ := filepath.Glob(filepath.Join(home(), "AccueilTV", "sauvegarde-*"))
	sort.Strings(dirs)
	for _, d := range dirs {
		if _, err := os.Stat(filepath.Join(d, "capture-avant.png")); err != nil {
			continue
		}
		props, _ := os.ReadFile(filepath.Join(d, "props.txt"))
		if dev.Serial != "" && bytes.Contains(props, []byte("[ro.serialno]: ["+dev.Serial+"]")) {
			return d
		}
		if dev.Serial == "" {
			cible, _ := os.ReadFile(filepath.Join(d, "cible.txt"))
			if strings.TrimSpace(string(cible)) == target {
				return d
			}
		}
	}
	return ""
}

func connected() bool {
	if target == "" || adbPath == "" {
		return false
	}
	state, _ := adb("-s", target, "get-state")
	return strings.TrimSpace(state) == "device"
}

func (j *job) install(disable []string) {
	if !connected() {
		j.fail(j.m("La télé n'est plus connectée. Reviens à l'étape précédente.", "The TV is no longer connected. Go back to the previous step."))
		return
	}

	// 1. Sauvegarde
	j.step("backup", "run", j.m("Sauvegarde des réglages de la télé…", "Backing up the TV's settings…"))
	backup := findBackup()
	if backup == "" {
		backup = filepath.Join(home(), "AccueilTV", "sauvegarde-"+time.Now().Format("20060102-150405"))
		os.MkdirAll(backup, 0o755)
		write := func(name, s string) { os.WriteFile(filepath.Join(backup, name), []byte(s+"\n"), 0o644) }
		write("cible.txt", target)
		write("props.txt", sh("getprop"))
		write("paquets-tous.txt", sh("pm", "list", "packages", "-f"))
		write("paquets-deja-desactives.txt", sh("pm", "list", "packages", "-d"))
		for _, ns := range []string{"global", "secure", "system"} {
			write("reglages-"+ns+".txt", sh("settings", "list", ns))
		}
		write("launcher-actuel.txt", sh("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"))
		if !screencap(filepath.Join(backup, "capture-avant.png")) {
			write("capture-avant.png", "ok")
		}
		j.step("backup", "ok", j.m("Sauvegarde faite", "Backup done"))
	} else {
		j.step("backup", "ok", j.m("Sauvegarde existante réutilisée", "Existing backup reused"))
	}
	os.WriteFile(filepath.Join(backup, "langue.txt"), []byte(j.lang+"\n"), 0o644)
	journalPath := filepath.Join(backup, "journal-install.txt")
	os.WriteFile(journalPath, nil, 0o644)
	note := func(s string) {
		f, err := os.OpenFile(journalPath, os.O_APPEND|os.O_WRONLY, 0o644)
		if err == nil {
			f.WriteString(s + "\n")
			f.Close()
		}
	}

	// 2. Allègement
	j.step("slim", "run", j.m("Allègement : pubs, télémétrie, applis inutiles…", "Slimming down: ads, telemetry, useless apps…"))
	list := append([]string{}, autoOff...)
	for p := range dev.Installed {
		if junk.MatchString(p) && !never.MatchString(p) {
			list = append(list, p)
		}
	}
	for brand, pkgs := range vendors {
		if strings.Contains(dev.Brand, brand) {
			list = append(list, pkgs...)
		}
	}
	allowed := map[string]bool{}
	for _, o := range optional {
		allowed[o[0]] = true
	}
	for _, p := range disable {
		if allowed[p] {
			list = append(list, p)
		}
	}
	done := map[string]bool{}
	count := 0
	for _, p := range list {
		if done[p] || never.MatchString(p) || !dev.Installed[p] {
			continue
		}
		done[p] = true
		note(p + " -> " + j.act("pm", "disable-user", "--user", "0", p))
		count++
	}
	for _, k := range []string{"window_animation_scale", "transition_animation_scale", "animator_duration_scale"} {
		j.act("settings", "put", "global", k, "0.5")
	}
	// Économiseur d'écran Accueil TV à la place de celui de Google.
	j.act("settings", "put", "secure", "screensaver_components", pkg+"/"+pkg+".Dream")
	j.act("settings", "put", "secure", "screensaver_enabled", "1")
	j.act("settings", "put", "secure", "screensaver_activate_on_sleep", "1")
	j.act("settings", "put", "secure", "screensaver_activate_on_dock", "0")
	for _, p := range []string{"com.google.android.backdrop", "com.google.android.apps.tv.dreamx"} {
		if dev.Installed[p] {
			j.act("pm", "disable-user", "--user", "0", p)
		}
	}
	j.step("slim", "ok", j.m("Allègement fait", "Slimming down done")+" ("+strconv.Itoa(count)+")")

	// 3. Téléchargement
	apk := *apkFlag
	if apk == "" {
		j.step("download", "run", j.m("Téléchargement d'Accueil TV…", "Downloading Accueil TV…"))
		data, err := download(repoRaw + "/app/AccueilTV.apk")
		if err != nil || !bytes.HasPrefix(data, []byte("PK")) {
			j.step("download", "fail", j.m("Téléchargement impossible", "Download failed"))
			j.fail(j.m("Vérifie la connexion Internet de l'ordinateur, puis réessaie.", "Check this computer's Internet connection, then try again."))
			return
		}
		apk = filepath.Join(cacheDir(), "AccueilTV.apk")
		os.WriteFile(apk, data, 0o644)
	}
	j.step("download", "ok", j.m("Accueil TV téléchargé", "Accueil TV downloaded"))

	// 4. Installation
	j.step("install", "run", j.m("Installation sur la télé…", "Installing on the TV…"))
	if !*dryRun {
		out, _ := adb("-s", target, "install", "-r", "-d", apk)
		note(out)
		if strings.Contains(out, "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
			if !j.ask(j.m("Une ancienne version d'Accueil TV est installée et doit être remplacée (ses réglages seront remis à zéro). Continuer ?",
				"An older Accueil TV is installed and must be replaced (its settings will be reset). Continue?")) {
				j.step("install", "fail", j.m("Installation annulée", "Install cancelled"))
				j.fail(j.m("Rien n'a été remplacé.", "Nothing was replaced."))
				return
			}
			adb("-s", target, "uninstall", pkg)
			out, _ = adb("-s", target, "install", "-r", "-d", apk)
			note(out)
		}
		if !strings.Contains(out, "Success") {
			j.step("install", "fail", j.m("La télé a refusé l'installation", "The TV refused the install"))
			j.fail(j.m("Si la télé affiche Play Protect : « Plus de détails » → « Installer quand même », puis relance l'installation.",
				"If the TV shows Play Protect: \"More details\" → \"Install anyway\", then run the install again."))
			return
		}
	} else {
		logLine("[essai] adb install -r -d " + apk)
	}
	j.act("pm", "grant", pkg, "android.permission.READ_TV_LISTINGS")
	j.act("pm", "grant", pkg, "android.permission.WRITE_SECURE_SETTINGS")
	j.act("appops", "set", pkg, "APP_AUTO_START", "allow")
	j.act("cmd", "notification", "allow_listener", pkg+"/"+pkg+".MediaListener")
	j.step("install", "ok", j.m("Accueil TV installé", "Accueil TV installed"))

	// 5. Bouton réglages et touche Entrées (service d'accessibilité)
	j.step("keys", "run", j.m("Boutons de la télécommande…", "Remote buttons…"))
	service := pkg + "/" + pkg + ".KeyService"
	cur := sh("settings", "get", "secure", "enabled_accessibility_services")
	next := cur
	switch {
	case strings.Contains(cur, service):
	case cur == "" || cur == "null":
		next = service
	default:
		next = cur + ":" + service
	}
	j.act("settings", "delete", "secure", "enabled_accessibility_services")
	time.Sleep(time.Second)
	j.act("settings", "put", "secure", "enabled_accessibility_services", next)
	j.act("settings", "put", "secure", "accessibility_enabled", "1")
	bound := false
	for i := 0; i < 15 && !*dryRun; i++ {
		for _, l := range strings.Split(sh("dumpsys", "accessibility"), "\n") {
			if strings.Contains(l, "Bound services") && (strings.Contains(l, "KeyService") || strings.Contains(l, "Accueil TV")) {
				bound = true
			}
		}
		if bound {
			break
		}
		time.Sleep(time.Second)
	}
	if bound || *dryRun {
		j.step("keys", "ok", j.m("Bouton réglages et touche Entrées actifs", "Settings button and Input key active"))
	} else {
		j.step("keys", "warn", j.m("Boutons de la télécommande inactifs (ils garderont le menu d'origine)", "Remote buttons inactive (they will keep the original menu)"))
	}

	// 6. Accueil par défaut
	j.step("home", "run", j.m("Accueil TV par défaut…", "Making Accueil TV the default…"))
	ok := j.makeHome(backup, note)
	if ok {
		j.step("home", "ok", j.m("Accueil TV est l'accueil par défaut", "Accueil TV is the default home screen"))
	} else {
		j.step("home", "warn", j.m("L'accueil d'origine a été gardé", "The original home screen was kept"))
	}

	sh("input", "keyevent", "KEYCODE_HOME")
	time.Sleep(4 * time.Second)
	after := filepath.Join(backup, "capture-apres.png")
	if screencap(after) {
		mu.Lock()
		lastShot = after
		mu.Unlock()
	}
	j.finish(map[string]any{"home": ok, "backup": backup, "shot": lastShot != ""})
}

func (j *job) makeHome(backup string, note func(string)) bool {
	if *dryRun {
		return true
	}
	lines := strings.Split(readFile(filepath.Join(backup, "launcher-actuel.txt")), "\n")
	orig := ""
	if len(lines) > 1 {
		orig = strings.TrimSpace(lines[1])
	}
	origPkg := strings.Split(orig, "/")[0]
	var disabled []string
	resumed := func() string {
		for _, l := range strings.Split(sh("dumpsys", "activity", "activities"), "\n") {
			if strings.Contains(l, "mResumedActivity") {
				return l
			}
		}
		return ""
	}
	try := func() bool {
		sh("cmd", "package", "set-home-activity", pkg+"/.HomeActivity")
		sh("input", "keyevent", "KEYCODE_HOME")
		time.Sleep(7 * time.Second)
		return strings.Contains(resumed(), pkg)
	}
	revert := func() bool {
		for _, p := range disabled {
			sh("pm", "enable", "--user", "0", p)
		}
		if orig != "" {
			sh("cmd", "package", "set-home-activity", orig)
		}
		sh("input", "keyevent", "KEYCODE_HOME")
		return false
	}
	if try() {
		return true
	}
	if origPkg == "" || !j.ask(j.m("L'accueil d'origine ("+origPkg+") reprend le bouton Accueil. Le désactiver pour imposer Accueil TV ? (réversible)",
		"The original home screen ("+origPkg+") takes back the Home button. Disable it to force Accueil TV? (reversible)")) {
		return revert()
	}
	note(origPkg + " -> " + sh("pm", "disable-user", "--user", "0", origPkg))
	disabled = append(disabled, origPkg)
	if try() {
		return true
	}
	setup := regexp.MustCompile(`[a-zA-Z0-9._]+setupwraith`).FindString(resumed())
	if setup != "" && j.ask(j.m("L'écran de configuration de Google bloque l'accueil. Le désactiver aussi ?",
		"Google's setup screen is blocking the home screen. Disable it too?")) {
		note(setup + " -> " + sh("pm", "disable-user", "--user", "0", setup))
		disabled = append(disabled, setup)
		if try() {
			return true
		}
	}
	return revert()
}

// Tout remettre comme avant, à partir de la sauvegarde (équivalent de restaurer.sh).
func (j *job) restore() {
	if !connected() {
		j.fail(j.m("La télé n'est plus connectée. Reviens à l'étape précédente.", "The TV is no longer connected. Go back to the previous step."))
		return
	}
	backup := findBackup()
	if backup == "" {
		j.fail(j.m("Aucune sauvegarde de cette télé sur cet ordinateur : il faut lancer la restauration depuis l'ordinateur qui a fait l'installation.",
			"No backup of this TV on this computer: run the restore from the computer that did the install."))
		return
	}
	j.step("apps", "run", j.m("Réactivation des applis…", "Re-enabling apps…"))
	before := map[string]bool{}
	for _, l := range strings.Split(readFile(filepath.Join(backup, "paquets-deja-desactives.txt")), "\n") {
		before[strings.TrimPrefix(strings.TrimSpace(l), "package:")] = true
	}
	n := 0
	for p := range packages("-d") {
		if !before[p] {
			j.act("pm", "enable", "--user", "0", p)
			n++
		}
	}
	j.step("apps", "ok", j.m("Applis réactivées", "Apps re-enabled")+" ("+strconv.Itoa(n)+")")

	j.step("settings", "run", j.m("Réglages d'origine…", "Original settings…"))
	setting := func(ns, key string) string {
		for _, l := range strings.Split(readFile(filepath.Join(backup, "reglages-"+ns+".txt")), "\n") {
			if strings.HasPrefix(l, key+"=") {
				return strings.TrimSpace(strings.TrimPrefix(l, key+"="))
			}
		}
		return ""
	}
	put := func(ns, key string) {
		if v := setting(ns, key); v != "" && v != "null" {
			j.act("settings", "put", ns, key, v)
		} else {
			j.act("settings", "delete", ns, key)
		}
	}
	for _, k := range []string{"window_animation_scale", "transition_animation_scale", "animator_duration_scale"} {
		put("global", k)
	}
	for _, k := range []string{"screensaver_enabled", "screensaver_activate_on_sleep", "screensaver_activate_on_dock", "screensaver_components", "enabled_accessibility_services"} {
		put("secure", k)
	}
	acc := setting("secure", "accessibility_enabled")
	if acc == "" {
		acc = "0"
	}
	j.act("settings", "put", "secure", "accessibility_enabled", acc)
	j.step("settings", "ok", j.m("Réglages d'origine rétablis", "Original settings restored"))

	j.step("uninstall", "run", j.m("Désinstallation d'Accueil TV…", "Uninstalling Accueil TV…"))
	if packages("")[pkg] {
		j.act("cmd", "notification", "disallow_listener", pkg+"/"+pkg+".MediaListener")
		lines := strings.Split(readFile(filepath.Join(backup, "launcher-actuel.txt")), "\n")
		if len(lines) > 1 && strings.TrimSpace(lines[1]) != "" {
			j.act("cmd", "package", "set-home-activity", strings.TrimSpace(lines[1]))
		}
		j.act("pm", "uninstall", pkg)
	}
	j.act("input", "keyevent", "KEYCODE_HOME")
	j.step("uninstall", "ok", j.m("Accueil d'origine rétabli", "Original home screen back"))
	j.finish(map[string]any{"restored": true})
}

func readFile(p string) string {
	b, _ := os.ReadFile(p)
	return strings.ReplaceAll(string(b), "\r", "")
}
