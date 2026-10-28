package main

import (
	"os/exec"
	"syscall"
)

// Pas de fenêtre de console qui clignote à chaque appel d'adb.
func hide(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
}
