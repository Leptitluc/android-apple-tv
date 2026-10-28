package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"sync"
	"time"
)

// Une tâche (connexion, installation, restauration) tourne en arrière-plan ;
// la page suit son journal d'événements par Server-Sent Events.
type event struct {
	Type   string `json:"type"` // step, log, question, done
	Step   string `json:"step,omitempty"`
	Status string `json:"status,omitempty"` // run, ok, warn, fail
	Text   string `json:"text,omitempty"`
	OK     bool   `json:"ok,omitempty"`
	Data   any    `json:"data,omitempty"`
}

type job struct {
	lang string
}

var (
	mu       sync.Mutex
	running  bool
	journal  []event
	changed  = make(chan struct{})
	answers  chan bool
	lastShot string
)

func jobRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return running
}

func startJob(lang string, fn func(*job)) map[string]any {
	mu.Lock()
	if running {
		mu.Unlock()
		return map[string]any{"ok": false, "busy": true}
	}
	running = true
	journal = nil
	answers = nil
	mu.Unlock()
	j := &job{lang: lang}
	go func() {
		defer func() {
			if r := recover(); r != nil {
				j.emit(event{Type: "done", Text: fmt.Sprint(r)})
			}
			mu.Lock()
			running = false
			mu.Unlock()
		}()
		fn(j)
	}()
	return map[string]any{"ok": true}
}

func (j *job) emit(e event) {
	mu.Lock()
	journal = append(journal, e)
	close(changed)
	changed = make(chan struct{})
	mu.Unlock()
	if e.Type == "log" || e.Type == "done" {
		logLine(e.Text)
	}
}

func (j *job) m(fr, en string) string {
	if j.lang == "en" {
		return en
	}
	return fr
}

func (j *job) step(id, status, text string) {
	j.emit(event{Type: "step", Step: id, Status: status, Text: text})
}
func (j *job) log(text string)  { j.emit(event{Type: "log", Text: text}) }
func (j *job) fail(text string) { j.emit(event{Type: "done", OK: false, Text: text}) }
func (j *job) finish(data any)  { j.emit(event{Type: "done", OK: true, Data: data}) }

// Question oui/non posée dans la page ; la tâche attend la réponse.
func (j *job) ask(text string) bool {
	ch := make(chan bool, 1)
	mu.Lock()
	answers = ch
	mu.Unlock()
	j.emit(event{Type: "question", Text: text})
	select {
	case yes := <-ch:
		return yes
	case <-time.After(30 * time.Minute):
		return false
	}
}

func answer(yes bool) {
	mu.Lock()
	ch := answers
	answers = nil
	mu.Unlock()
	if ch != nil {
		ch <- yes
	}
}

func events(w http.ResponseWriter, r *http.Request) {
	fl, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "stream", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-store")
	from, _ := strconv.Atoi(r.URL.Query().Get("from"))
	for {
		mu.Lock()
		pending := append([]event(nil), journal[min(from, len(journal)):]...)
		wait := changed
		mu.Unlock()
		for _, e := range pending {
			b, _ := json.Marshal(e)
			fmt.Fprintf(w, "data: %s\n\n", b)
			from++
		}
		fl.Flush()
		select {
		case <-wait:
		case <-time.After(15 * time.Second):
			fmt.Fprint(w, ": ping\n\n")
			fl.Flush()
		case <-r.Context().Done():
			return
		}
	}
}

// Capture d'écran de la télé après l'installation, affichée à la fin de l'assistant.
func shot(w http.ResponseWriter) {
	mu.Lock()
	p := lastShot
	mu.Unlock()
	b, err := os.ReadFile(p)
	if p == "" || err != nil {
		http.NotFound(w, nil)
		return
	}
	w.Header().Set("Content-Type", "image/png")
	w.Write(b)
}

// Journal technique à côté de la sauvegarde, pour le support.
var logFile *os.File

func logLine(s string) {
	if logFile == nil {
		dir := filepath.Join(home(), "AccueilTV")
		os.MkdirAll(dir, 0o755)
		logFile, _ = os.OpenFile(filepath.Join(dir, "installateur.log"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	}
	if logFile != nil && s != "" {
		fmt.Fprintf(logFile, "%s %s\n", time.Now().Format("2006-01-02 15:04:05"), s)
	}
}

func home() string {
	h, err := os.UserHomeDir()
	if err != nil {
		return "."
	}
	return h
}
