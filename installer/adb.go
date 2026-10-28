package main

import (
	"archive/zip"
	"bytes"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

const (
	repoRaw = "https://raw.githubusercontent.com/Leptitluc/android-apple-tv/main"
	pkg     = "com.lucb.tvhome"
)

var (
	adbPath string
	target  string // ip:5555 de la télé connectée
)

func cacheDir() string {
	d, err := os.UserCacheDir()
	if err != nil {
		d = os.TempDir()
	}
	d = filepath.Join(d, "AccueilTV")
	os.MkdirAll(d, 0o755)
	return d
}

// adb déjà présent sur l'ordinateur, sinon les « platform-tools » officiels de Google.
func (j *job) ensureAdb() bool {
	if adbPath != "" {
		return true
	}
	exe := "adb"
	if runtime.GOOS == "windows" {
		exe = "adb.exe"
	}
	local := filepath.Join(cacheDir(), "platform-tools", exe)
	if _, err := os.Stat(local); err == nil {
		adbPath = local
		return true
	}
	if p, err := exec.LookPath("adb"); err == nil {
		adbPath = p
		return true
	}
	j.step("tools", "run", j.m("Téléchargement des outils Android (adb)…", "Downloading the Android tools (adb)…"))
	plat := map[string]string{"windows": "windows", "darwin": "darwin"}[runtime.GOOS]
	if plat == "" {
		plat = "linux"
	}
	data, err := download("https://dl.google.com/android/repository/platform-tools-latest-" + plat + ".zip")
	if err == nil {
		err = unzip(data, cacheDir())
	}
	if err != nil {
		j.step("tools", "fail", j.m("Téléchargement d'adb impossible : ", "Could not download adb: ")+err.Error())
		return false
	}
	adbPath = local
	j.step("tools", "ok", j.m("Outils Android prêts", "Android tools ready"))
	return true
}

func download(url string) ([]byte, error) {
	c := &http.Client{Timeout: 5 * time.Minute}
	res, err := c.Get(url)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()
	if res.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP %d", res.StatusCode)
	}
	return io.ReadAll(res.Body)
}

func unzip(data []byte, dest string) error {
	zr, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return err
	}
	for _, f := range zr.File {
		p := filepath.Join(dest, f.Name)
		if !strings.HasPrefix(p, filepath.Clean(dest)+string(os.PathSeparator)) {
			continue
		}
		if f.FileInfo().IsDir() {
			os.MkdirAll(p, 0o755)
			continue
		}
		os.MkdirAll(filepath.Dir(p), 0o755)
		rc, err := f.Open()
		if err != nil {
			return err
		}
		mode := f.Mode().Perm() | 0o600
		if strings.HasSuffix(f.Name, "/adb") || strings.HasSuffix(f.Name, ".exe") {
			mode |= 0o755
		}
		out, err := os.OpenFile(p, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, mode)
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(out, rc)
		out.Close()
		rc.Close()
		if err != nil {
			return err
		}
	}
	return nil
}

// Commande adb brute (sortie standard + erreur).
func adb(args ...string) (string, error) {
	cmd := exec.Command(adbPath, args...)
	hide(cmd)
	out, err := cmd.CombinedOutput()
	return strings.ReplaceAll(string(out), "\r", ""), err
}

// Lecture sur la télé (toujours exécutée).
func sh(args ...string) string {
	out, _ := adb(append([]string{"-s", target, "shell"}, args...)...)
	return strings.TrimRight(out, "\n")
}

// Modification de la télé : en mode essai, seulement journalisée.
func (j *job) act(args ...string) string {
	if *dryRun {
		logLine("[essai] adb shell " + strings.Join(args, " "))
		return ""
	}
	return sh(args...)
}

func screencap(path string) bool {
	cmd := exec.Command(adbPath, "-s", target, "exec-out", "screencap", "-p")
	hide(cmd)
	out, err := cmd.Output()
	if err != nil {
		return false
	}
	if i := bytes.Index(out, []byte("\x89PNG")); i >= 0 {
		out = out[i:]
	} else {
		return false
	}
	return os.WriteFile(path, out, 0o644) == nil
}

// Télés de la maison qui acceptent adb sur le réseau (port 5555).
func scan() []string {
	var hosts []string
	ifaces, _ := net.Interfaces()
	for _, ifc := range ifaces {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := ifc.Addrs()
		for _, a := range addrs {
			ipn, ok := a.(*net.IPNet)
			if !ok || ipn.IP.To4() == nil || !ipn.IP.IsPrivate() {
				continue
			}
			ip := ipn.IP.To4()
			for i := 1; i < 255; i++ {
				h := net.IPv4(ip[0], ip[1], ip[2], byte(i)).String()
				if h != ip.String() {
					hosts = append(hosts, h)
				}
			}
		}
	}
	found := []string{}
	var wg sync.WaitGroup
	var fm sync.Mutex
	sem := make(chan struct{}, 128)
	for _, h := range hosts {
		wg.Add(1)
		sem <- struct{}{}
		go func(h string) {
			defer wg.Done()
			defer func() { <-sem }()
			c, err := net.DialTimeout("tcp", h+":5555", 600*time.Millisecond)
			if err == nil {
				c.Close()
				fm.Lock()
				found = append(found, h)
				fm.Unlock()
			}
		}(h)
	}
	wg.Wait()
	return found
}
