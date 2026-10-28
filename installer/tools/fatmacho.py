#!/usr/bin/env python3
# Assemble des exécutables Mach-O (arm64, x86_64) en un binaire universel, comme `lipo -create`.
#   python3 fatmacho.py sortie entree1 entree2 ...
import struct
import sys

out, inputs = sys.argv[1], sys.argv[2:]
slices = []
for path in inputs:
    data = open(path, "rb").read()
    magic, cputype, cpusub = struct.unpack("<III", data[:12])
    if magic != 0xFEEDFACF:
        sys.exit(f"{path} : pas un Mach-O 64 bits")
    align = 14 if cputype == 0x0100000C else 12  # arm64 : pages de 16 Ko
    slices.append((cputype, cpusub & 0x00FFFFFF, align, data))

header = struct.pack(">II", 0xCAFEBABE, len(slices))
offset = 8 + 20 * len(slices)
table, body = b"", b""
for cputype, cpusub, align, data in slices:
    step = 1 << align
    pad = (-(offset + len(body))) % step
    body += b"\0" * pad
    table += struct.pack(">IIIII", cputype, cpusub, offset + len(body), len(data), align)
    body += data
open(out, "wb").write(header + table + body)
