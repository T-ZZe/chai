#!/usr/bin/env python3
"""Valide la cohérence entre les YAML (src/main/resources) et le code Java. Code de sortie 1 si erreur.

Contrôles : types de compétences enregistrés, clés de params réellement lues par le handler du type,
références demon -> compétences/passif, cooldowns/coûts présents, raretés, clés de messages utilisées.
"""
import glob
import os
import re
import sys

import yaml

ROOT = os.path.join(os.path.dirname(__file__), "..")
RES = os.path.join(ROOT, "src", "main", "resources")
SRC = os.path.join(ROOT, "src", "main", "java", "fr", "chainsawrpg")
errors, warnings = [], []


def load(name):
    with open(os.path.join(RES, name), encoding="utf-8") as f:
        return yaml.safe_load(f)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


demons = load("demons.yml")["demons"]
skills = load("skills.yml")["skills"]
cooldowns = load("cooldowns.yml")["cooldowns"]
costs = load("sacrifices.yml")["costs"]
rarities = load("rarities.yml")["rarities"]
messages = load("messages.yml")
for n in ("config.yml", "items.yml"):
    load(n)  # simple contrôle de syntaxe

# ---------- types enregistrés + clés lues par handler ----------
registered = {}  # TYPE -> (fichier, fonction)
for f in glob.glob(os.path.join(SRC, "skill", "types", "*.java")):
    for m in re.finditer(r'register\("([A-Z_]+)",\s*\w+::(\w+)\)', read(f)):
        registered[m.group(1)] = (f, m.group(2))

KEY_RE = re.compile(r'(?:c\.[disb]|c\.def\.(?:d|i|s|b|list|effects))\(\s*"([a-z0-9-]+)"')
EFFECT_DEFAULT = re.compile(r'c\.effects\(\w+\)')


def handler_keys(path, func):
    src = read(path)
    m = re.search(r'static boolean ' + func + r'\(Cast c\) \{', src)
    if not m:
        return None
    start = m.end()
    nxt = re.search(r'\n    (?:public |private )?static ', src[start:])
    body = src[start:start + nxt.start()] if nxt else src[start:]
    keys = set(KEY_RE.findall(body))
    if EFFECT_DEFAULT.search(body):
        keys.add("effects")
    # fonctions utilitaires appelées par le handler (même fichier) : on inclut leurs clés
    for helper in re.findall(r'\b(\w+)\((?:c|cast)\b', body):
        if helper != func:
            hm = re.search(r'static \w+ ' + helper + r'\(Cast c[^)]*\) \{', src)
            if hm:
                hs = hm.end()
                hn = re.search(r'\n    (?:public |private )?static ', src[hs:])
                hb = src[hs:hs + hn.start()] if hn else src[hs:]
                keys |= set(KEY_RE.findall(hb))
                if EFFECT_DEFAULT.search(hb):
                    keys.add("effects")
    return keys


type_keys = {}
for t, (path, func) in registered.items():
    k = handler_keys(path, func)
    if k is None:
        errors.append(f"handler introuvable pour {t} ({func})")
    else:
        type_keys[t] = k

# ---------- passifs : clés lues par PassiveProfile ----------
pp_src = read(os.path.join(SRC, "demon", "PassiveProfile.java"))
passive_keys = set(re.findall(r'\.get(?:Double|Int|String|StringList|ConfigurationSection)\("([a-z0-9-]+)"', pp_src))

# ---------- demons ----------
for did, d in demons.items():
    if d["rarity"] not in rarities:
        errors.append(f"{did}: rareté inconnue {d['rarity']}")
    if not d.get("enabled", True):
        continue
    if len(d["skills"]) != 4:
        errors.append(f"{did}: 4 compétences requises")
    for i, sid in enumerate(d["skills"], 1):
        s = skills.get(sid)
        if s is None:
            errors.append(f"{did}: compétence introuvable {sid}")
            continue
        if s["slot"] != i:
            errors.append(f"{sid}: slot {s['slot']} != position {i}")
        if s["demon"] != did:
            errors.append(f"{sid}: demon={s['demon']} != {did}")
    if d["passive"] not in skills:
        errors.append(f"{did}: passif introuvable {d['passive']}")

for sid, s in skills.items():
    if s["slot"] == 0:
        for k in s.get("params", {}):
            if k not in passive_keys:
                errors.append(f"{sid}: clé de passif inconnue '{k}'")
        continue
    t = s["type"]
    if t not in registered:
        errors.append(f"{sid}: type non enregistré {t}")
        continue
    for k in s.get("params", {}):
        if k not in type_keys[t]:
            errors.append(f"{sid} ({t}): paramètre '{k}' non lu par le handler (lus : {sorted(type_keys[t])})")
    if sid not in cooldowns:
        errors.append(f"{sid}: cooldown manquant")
    if sid not in costs:
        errors.append(f"{sid}: coût manquant")
    elif costs[sid]["type"] not in ("HEALTH", "FOOD", "EXP", "BLOOD", "DURABILITY", "NONE"):
        errors.append(f"{sid}: type de coût inconnu {costs[sid]['type']}")
    if s["duration"] <= 0 and t in ("ZONE", "REVEAL", "BUFF_SELF") and not s.get("params", {}).get("launch"):
        warnings.append(f"{sid}: durée 0 pour un type {t}")
    if t == "ZONE" and s["duration"] <= 0:
        errors.append(f"{sid}: ZONE sans durée")
    # PvP : plafond de dégâts par coup (config.combat.max-hit-damage-pvp)
    cfg = load("config.yml")["combat"]
    if s["damage-pvp"] > cfg["max-hit-damage-pvp"]:
        warnings.append(f"{sid}: damage-pvp {s['damage-pvp']} > plafond {cfg['max-hit-damage-pvp']}")

# ---------- messages ----------
used = set()
for f in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
    src = read(f)
    for pat in (r'msg\.(?:send|feedback)\(\s*[\w.()]+,\s*"([a-z0-9-]+)"', r'msg\.(?:get|raw)\("([a-z0-9-]+)"', r'\.fail\("([a-z0-9-]+)"\)',
                r'"((?:ritual|skill|cost)-[a-z-]+)"', r'fail\([^"]*"([a-z-]+)"\s*:\s*"([a-z-]+)"'):
        for m in re.finditer(pat, src):
            used.update(g for g in m.groups() if g)
for reason in ("world", "spawn", "same-ip", "new-account", "victim-cooldown", "pair-cooldown", "daily-cap"):
    used.add("head-denied-" + reason)
for k in sorted(used):
    if k not in messages and k != "head-denied-":
        errors.append(f"message manquant dans messages.yml : {k}")

print(f"Types enregistrés : {len(registered)} | compétences : {len(skills)} | démons : {len(demons)} "
      f"(actifs : {sum(1 for d in demons.values() if d.get('enabled', True))})")
for w in warnings:
    print("AVERTISSEMENT:", w)
for e in errors:
    print("ERREUR:", e)
print("OK" if not errors else f"{len(errors)} erreur(s)")
sys.exit(1 if errors else 0)
