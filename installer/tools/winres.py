#!/usr/bin/env python3
# Crée un objet COFF (.syso) contenant l'icône de l'exe Windows, lu par l'éditeur de liens de Go.
#   python3 winres.py sortie.syso icone256.png icone48.png icone32.png icone16.png
# Chaque PNG devient une entrée RT_ICON, regroupées dans un RT_GROUP_ICON (ID 1).
import struct
import sys

out, pngs = sys.argv[1], sys.argv[2:]
images = [open(p, "rb").read() for p in pngs]
sizes = [struct.unpack(">II", d[16:24])[0] for d in images]  # largeur lue dans l'en-tête PNG

group = struct.pack("<HHH", 0, 1, len(images))
for i, (d, s) in enumerate(zip(images, sizes)):
    b = 0 if s >= 256 else s
    group += struct.pack("<BBBBHHIH", b, b, 0, 0, 1, 32, len(d), i + 1)

# Arborescence : type -> identifiant -> langue -> données
tree = {3: {i + 1: d for i, d in enumerate(images)}, 14: {1: group}}
LANG = 0x0409


def directory(n):
    return struct.pack("<IIHHHH", 0, 0, 0, 0, 0, n)


# Passe 1 : positions des répertoires, puis des descripteurs de données, puis des données.
dirs, pos = [], 0
root = pos
pos += 16 + 8 * len(tree)
type_dirs = {}
for t in sorted(tree):
    type_dirs[t] = pos
    pos += 16 + 8 * len(tree[t])
name_dirs = {}
for t in sorted(tree):
    for n in sorted(tree[t]):
        name_dirs[(t, n)] = pos
        pos += 16 + 8
entries = {}
for t in sorted(tree):
    for n in sorted(tree[t]):
        entries[(t, n)] = pos
        pos += 16
datas = {}
for t in sorted(tree):
    for n in sorted(tree[t]):
        pos = (pos + 7) & ~7
        datas[(t, n)] = pos
        pos += len(tree[t][n])
size = (pos + 7) & ~7

# Passe 2 : écriture
sec = bytearray(size)
relocs = []
sec[root:root + 16] = directory(len(tree))
for i, t in enumerate(sorted(tree)):
    struct.pack_into("<II", sec, root + 16 + 8 * i, t, 0x80000000 | type_dirs[t])
    sec[type_dirs[t]:type_dirs[t] + 16] = directory(len(tree[t]))
    for k, n in enumerate(sorted(tree[t])):
        struct.pack_into("<II", sec, type_dirs[t] + 16 + 8 * k, n, 0x80000000 | name_dirs[(t, n)])
        nd = name_dirs[(t, n)]
        sec[nd:nd + 16] = directory(1)
        struct.pack_into("<II", sec, nd + 16, LANG, entries[(t, n)])
        e = entries[(t, n)]
        struct.pack_into("<IIII", sec, e, datas[(t, n)], len(tree[t][n]), 0, 0)
        relocs.append(e)  # adresse relative à corriger par l'éditeur de liens
        d = datas[(t, n)]
        sec[d:d + len(tree[t][n])] = tree[t][n]

raw_ptr = 20 + 40
reloc_ptr = raw_ptr + size
sym_ptr = reloc_ptr + 10 * len(relocs)
coff = struct.pack("<HHIIIHH", 0x8664, 1, 0, sym_ptr, 1, 0, 0)
coff += struct.pack("<8sIIIIIIHHI", b".rsrc", 0, 0, size, raw_ptr, reloc_ptr, 0, len(relocs), 0, 0x40000040)
coff += bytes(sec)
for r in relocs:
    coff += struct.pack("<IIH", r, 0, 3)  # IMAGE_REL_AMD64_ADDR32NB
coff += struct.pack("<8sIhHBB", b".rsrc", 0, 1, 0, 3, 0)
coff += struct.pack("<I", 4)
open(out, "wb").write(coff)
