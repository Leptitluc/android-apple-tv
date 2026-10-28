// Accueil TV - installateur graphique pour macOS et Windows.
// Ouvre un assistant dans le navigateur (serveur local sur 127.0.0.1, protégé par un jeton),
// télécharge adb et l'APK, puis installe Accueil TV sur la télé comme install.sh.
package main

import (
	"crypto/rand"
	"embed"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"os"
	"os/exec"
	"runtime"
	"sync"
	"time"
)

//go:embed web
var webFiles embed.FS

var (
	token    string
	apkFlag  = flag.String("apk", os.Getenv("ACCUEIL_TV_APK"), "APK local à installer (sinon téléchargé)")
	dryRun   = flag.Bool("dry-run", false, "n'exécute aucune commande qui modifie la télé (test)")
	noOpen   = flag.Bool("no-browser", false, "n'ouvre pas le navigateur")
	lastPing = time.Now()
	pingMu   sync.Mutex
)

func main() {
	flag.Parse()
	b := make([]byte, 16)
	rand.Read(b)
	token = hex.EncodeToString(b)

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		fmt.Println("Impossible de démarrer :", err)
		os.Exit(1)
	}
	host := ln.Addr().String()
	url := "http://" + host + "/?t=" + token

	ui, _ := fs.Sub(webFiles, "web")
	static := http.FileServer(http.FS(ui))
	mux := http.NewServeMux()
	mux.HandleFunc("/api/", func(w http.ResponseWriter, r *http.Request) {
		// Seule la page de l'installateur peut appeler l'API (jeton + hôte exact, contre le DNS rebinding).
		if r.Host != host || (r.Header.Get("X-Token") != token && r.URL.Query().Get("t") != token) {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		api(w, r)
	})
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.Host != host {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		w.Header().Set("Cache-Control", "no-store")
		static.ServeHTTP(w, r)
	})

	go watchdog()
	fmt.Println("Accueil TV - installateur :", url)
	if !*noOpen {
		openBrowser(url)
	}
	http.Serve(ln, mux)
}

// Quitte quand la page est fermée (plus de signe de vie), sauf pendant une installation.
func watchdog() {
	for range time.Tick(5 * time.Second) {
		pingMu.Lock()
		idle := time.Since(lastPing)
		pingMu.Unlock()
		if idle > 45*time.Second && !jobRunning() {
			os.Exit(0)
		}
	}
}

func openBrowser(url string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	case "darwin":
		cmd = exec.Command("open", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	hide(cmd)
	cmd.Start()
}

func api(w http.ResponseWriter, r *http.Request) {
	pingMu.Lock()
	lastPing = time.Now()
	pingMu.Unlock()
	var in map[string]any
	if r.Method == http.MethodPost {
		json.NewDecoder(r.Body).Decode(&in)
	}
	str := func(k string) string { s, _ := in[k].(string); return s }
	switch r.URL.Path {
	case "/api/ping":
		reply(w, map[string]any{"ok": true, "os": runtime.GOOS})
	case "/api/scan":
		reply(w, map[string]any{"devices": scan()})
	case "/api/connect":
		reply(w, startJob(str("lang"), func(j *job) { j.connect(str("ip")) }))
	case "/api/install":
		disable, _ := in["disable"].([]any)
		var pkgs []string
		for _, p := range disable {
			if s, ok := p.(string); ok {
				pkgs = append(pkgs, s)
			}
		}
		reply(w, startJob(str("lang"), func(j *job) { j.install(pkgs) }))
	case "/api/restore":
		reply(w, startJob(str("lang"), func(j *job) { j.restore() }))
	case "/api/answer":
		yes, _ := in["yes"].(bool)
		answer(yes)
		reply(w, map[string]any{"ok": true})
	case "/api/events":
		events(w, r)
	case "/api/shot":
		shot(w)
	case "/api/quit":
		reply(w, map[string]any{"ok": true})
		go func() {
			time.Sleep(300 * time.Millisecond)
			os.Exit(0)
		}()
	default:
		http.NotFound(w, r)
	}
}

func reply(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}
