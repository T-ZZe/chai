# Guide de configuration

| Fichier | Contenu |
|---|---|
| `config.yml` | base de données, têtes, contrats, progression, combat/équilibrage, protection, sang |
| `demons.yml` | liste des démons (nom, description, rareté, icône, 4 compétences, passif, `enabled`) |
| `skills.yml` | définition de chaque compétence (type, dégâts PvP/PvE, portée, zone, durée, cibles max, particules, sons, `params`) |
| `cooldowns.yml` | cooldown (secondes) par compétence |
| `sacrifices.yml` | coût par compétence : `HEALTH` (PV), `FOOD`, `EXP` (points), `BLOOD`, `DURABILITY`, `NONE` |
| `rarities.yml` | nom, couleur MiniMessage et poids de tirage des 6 raretés |
| `items.yml` | noms et descriptions des objets |
| `messages.yml` | tous les messages (MiniMessage) |

`demons.yml`, `skills.yml`, `cooldowns.yml` et `sacrifices.yml` sont **générés** par `tools/generate_configs.py` ;
tu peux soit modifier ce script puis le relancer, soit éditer les YAML directement. Vérifie toujours avec
`python3 tools/validate_configs.py`.

## Têtes : protections

* Chaque tête a un identifiant unique **enregistré en base**. Seule une tête présente dans le registre « active » est valable.
  Une copie dupliquée, forgée ou déjà consommée est **purgée** de l'inventaire (message en console).
* Les têtes ne peuvent être ni posées ni utilisées en craft.
* Une victime ne donne qu'**une tête par `victim-cooldown-minutes`** (tous tueurs confondus) ; un même couple tueur/victime
  ne donne qu'une tête par `pair-cooldown-hours` ; plafond de `max-per-killer-per-day` ; refus si même IP
  (`block-same-ip`), si la victime a moins de `min-victim-playtime-minutes` de jeu, ou si le kill a lieu au spawn.

## Combat : garde-fous

`hit-gap-ms` (anti-combo), `max-hit-damage-pvp/pve` (plafond par coup), `max-root-seconds-*` + `root-immunity-seconds`
(pas d'immobilisation permanente), `max-effect-seconds-pvp`, `max-zones-per-player`, `max-projectiles-per-cast`,
`require-line-of-sight` (pas d'attaque à travers les murs), `protection.spawn` / `protection.zones` / `protection.disabled-worlds`.

## Dégâts : conventions

* `damage-pvp` / `damage-pve` : dégâts **par coup**, ou **par seconde** pour le type `ZONE`.
* Multiplicateur de niveau : `1 + (niveau-1) × damage-per-level-(pvp|pve)`.
* Les passifs (bonus de proximité, marques, feu…) multiplient ensuite les dégâts. Le plafond `max-hit-damage-*` s'applique avant les passifs.

## Types de compétences et paramètres (`params`)

Paramètres communs lus depuis la compétence : `range`, `radius`, `duration`, `max-targets`, `particle`, `sound`.
Effets de potion : liste de `"id:ticks:amplificateur"` (ex. `"slowness:60:1"`).

| Type | Description | `params` |
|---|---|---|
| `RAY_STRIKE` | frappe instantanée sur la cible visée | `knockback`, `pull`, `lift`, `effects` ; `radius`>0 = éclaboussure à 60 % |
| `SUMMON_BITE` | tête invoquée qui voyage puis mord | `travel` (ticks), `knockback`, `effects` |
| `MULTI_HIT` | coups espacés | `mode` TARGET\|NEAREST\|AREA, `hits`, `interval` (≥4), `style` BURST\|COLUMN, `knockback-last`, `effects` |
| `MELEE_ARC` | arc de mêlée | `angle`, `knockback`, `up`, `lunge`, `effects` |
| `LEAP` | bond physique (vélocité) | `mode` TARGET\|FORWARD, `speed`, `lift`, `max-ticks`, `burst-start`, `land-radius`, `knockback` |
| `AOE_BURST` | explosion de zone | `center` SELF\|TARGET\|AIM, `delay`, `style` RING\|PILLAR\|SLAM\|EXPLOSION\|HEAD\|SPIN, `knockback`, `up`, `fire-ticks`, `self-damage`, `marker`, `effects` |
| `EFFECT_AREA` | effets/statut sur cible(s) | `center`, `effects`, `status`, `status-seconds`, `status-power`, `spread`, `spread-radius` ; `radius: 0` = cible visée |
| `ZONE` | zone persistante (dégâts/s) | `center`, `follow`, `boundary`, `tick`, `pull`, `lift`, `style` RING\|VORTEX\|DOME\|THREADS\|DARK, `effects`, `self-effects` |
| `PROJECTILE` | projectiles (collisions blocs/entités) | `mode` FORWARD\|AUTO\|SKY, `count`, `stagger`, `spread`, `fan`, `speed`, `gravity`, `pierce`, `width`, `life`, `explode`, `explode-mult`, `hit-mult`, `homing`, `fire-ticks`, `knockback`, `item`, `block`, `height`, `allow-empty`, `effects` |
| `ROOT` | immobilisation plafonnée + immunité | `mode` SINGLE\|AREA, `center`, `dot` (dégâts/s), `effects` |
| `MARK` | pose de marques | `status` (curse\|judge\|lock), `stacks`, `max-stacks` ; `radius`>0 = autour de soi |
| `MARK_STRIKE` | frappe les cibles marquées | `status`, `min-stacks`, `consume`, `all`, `base-mult`, `per-stack`, `effects` |
| `REVEAL` | détection visible par le lanceur seul | `players-only`, `arrows` |
| `CAGE` | cage de blocs temporaire (restaurée) | `material` |
| `BUFF_SELF` | bonus sur soi | `effects`, `absorption`, `heal`, `dodge`, `weapon-items`, `weapon-bonus`, `launch`, `launch-forward`, `aura-damage`, `aura-radius`, `armor`, `armor-color` |

Les paramètres que le code ne lit pas sont **refusés par le validateur** (faute de frappe détectée).

## Passifs (`slot: 0`, `type: PASSIVE`, clés dans `params`)

`resist-causes` (cause de dégâts → multiplicateur, 0 = immunité), `resist-all`, `dodge-chance`, `heal-on-kill`,
`close-range-bonus` + `close-range-distance`, `air-bonus`, `summon-bonus` (compétences taguées `summon`),
`marked-bonus` + `marked-status`, `fire-bonus` (compétences taguées `fire`), `weapon-bonus` (taguées `weapon`),
`range-bonus`, `aim-bonus`, `regen-per-cycle` (toutes les 2 s), `knockback-resist`, `infect-ramp`,
`water-speed-amp`, `mark-on-hit` + `mark-status`, `slow-on-hit-ticks` + `slow-on-hit-amp`, `slow-resist-max-amp`,
`immune-effects`, `attributes` (id → valeur), `perm-effects`, `effect-duration-mult`, `neutral-chance` (réservé, phase 1B).

## Base de données

SQLite par défaut (`plugins/ChainsawRPG/chainsawrpg.db`). Pour MySQL : `database.type: mysql` + section `mysql`, puis redémarre.
Tables : `cs_players`, `cs_contracts` (un enregistrement par démon et par joueur), `cs_heads`, `cs_head_log`.
Toutes les écritures passent par un thread dédié ; les données sont sauvegardées à la déconnexion, toutes les
`autosave-minutes` minutes et à l'arrêt.
