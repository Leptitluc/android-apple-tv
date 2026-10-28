#!/usr/bin/env python3
# Génère les sons de navigation d'Accueil TV (synthèse originale, aucun échantillon externe).
#   python3 tools/sons.py
import math
import os
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "sounds")


def tone(partials, ms, decay, attack=0.002, gain=0.5):
    n = int(RATE * ms / 1000)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * math.exp(-t / decay)
        s = sum(a * math.sin(2 * math.pi * f * t) for f, a in partials)
        out.append(gain * env * s)
    return out


def save(name, samples):
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples))


# Déplacement : « tic » bref et feutré.
save("move.wav", tone([(1850, 0.7), (3700, 0.2), (620, 0.25)], 45, 0.009))
# Validation : un peu plus plein et plus long.
save("select.wav", tone([(1180, 0.6), (1770, 0.35), (440, 0.3)], 110, 0.028))
print("ok ->", os.path.normpath(OUT))
