# Ajouter un nouveau démon

Un démon = 1 entrée dans `demons.yml` + 4 compétences + 1 passif dans `skills.yml` + leurs cooldowns et coûts.
Utilise des **identifiants stables** (minuscules, sans espaces) : ils sont stockés en base.

1. **Choisis les types** de compétences parmi ceux du tableau de `GUIDE_CONFIGURATION.md` (aucun code Java nécessaire).
2. **Ajoute le démon** dans `tools/generate_configs.py` (recommandé) :

```python
demon("lightning", "Démon Foudre", "Description affichée dans le menu.", "rare", "LIGHTNING_ROD", "FOOD",
      "END_ROD", "entity.lightning_bolt.thunder", [
    S(1, "Éclair", "Frappe la cible.", "RAY_STRIKE", 5, 8, 20, cd=7, knockback=0.3),
    S(2, "Chaîne", "...", "MULTI_HIT", 3, 5, 12, mt=3, cd=11, mode="NEAREST", hits=4, interval=5),
    S(3, "Orage", "...", "ZONE", 2, 4, 16, rad=6, dur=5, mt=6, cd=18, center="AIM", tick=10, style="RING"),
    S(4, "Jugement céleste", "...", "AOE_BURST", 10, 18, 20, rad=8, mt=8, cd=110, center="AIM", delay=14, style="PILLAR"),
], P("Peau conductrice", "Description du passif.", **{"resist-all": 0.1}))
```

   Arguments de `S` : `(slot, nom, description, type, dégâtsPvP, dégâtsPvE, portée, rad=, dur=, mt=, cd=, ...params)`.
   Ajoute l'identifiant dans la liste `order` de `main()` pour fixer sa position dans le menu.
3. **Raretés** : `common`, `uncommon`, `rare`, `epic`, `legendary`, `mythic` (modifiables dans `rarities.yml`).
4. **Coût** : le 6ᵉ argument de `demon()` (`HEALTH`, `FOOD`, `EXP`, `BLOOD`) applique le barème de `COST` ; `cost=<n>` le surcharge par compétence.
5. Lance `python3 tools/generate_configs.py` puis `python3 tools/validate_configs.py`, puis `/chainsaw reload` en jeu.

Si une compétence utilise un type inconnu du code, le démon est **désactivé automatiquement** avec un avertissement en console.
