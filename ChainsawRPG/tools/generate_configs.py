#!/usr/bin/env python3
"""Génère demons.yml, skills.yml, cooldowns.yml et sacrifices.yml (Phase 1).

Usage : python3 tools/generate_configs.py
Les fichiers sont écrits dans src/main/resources/. Modifie ce tableau puis relance le script,
ou édite directement les YAML générés (le plugin ne dépend pas de ce script à l'exécution).
"""
import os
import yaml

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")

# coût par emplacement (compétence 1, 2, 3, ultime) selon la ressource du démon
COST = {
    "HEALTH": [1.0, 2.0, 1.5, 6.0],
    "FOOD": [2, 3, 3, 8],
    "EXP": [15, 25, 20, 150],
    "BLOOD": [8, 12, 14, 60],
}


def S(slot, name, desc, type_, pvp=0.0, pve=0.0, rng=12, rad=0.0, dur=0.0, mt=1, cd=10, tags=None,
      particle=None, sound=None, cost=None, **params):
    return dict(slot=slot, name=name, desc=desc, type=type_, pvp=pvp, pve=pve, rng=rng, rad=rad, dur=dur, mt=mt,
                cd=cd, tags=tags or [], particle=particle, sound=sound, cost=cost, params=params)


def P(name, desc, **params):
    return dict(name=name, desc=desc, params=params)


DEMONS = {}


def demon(id_, name, desc, rarity, icon, cost, particle, sound, skills, passive):
    DEMONS[id_] = dict(name=name, desc=desc, rarity=rarity, icon=icon, cost=cost, particle=particle, sound=sound,
                       skills=skills, passive=passive)


# ----------------------------------------------------------------------------------------------------------------
demon("fox", "Démon Renard", "Un renard affamé qui dévore ce que tu désignes. Ses têtes invoquées frappent à distance.",
      "uncommon", "FOX_SPAWN_EGG", "HEALTH", "DUST:255,140,30:1.4", "entity.fox.bite", [
          S(1, "Morsure", "Invoque une tête de renard qui mord la cible visée.", "SUMMON_BITE", 5, 8, 14, cd=8, tags=["summon"], travel=6, knockback=0.3),
          S(2, "Dévoration", "Capture l'ennemi dans la gueule du renard : il est immobilisé et subit des dégâts.", "ROOT", 0, 0, 10, dur=2.5, cd=16, tags=["summon"], mode="SINGLE", dot=1.6),
          S(3, "Invocation rapide", "Plusieurs morsures successives sur les ennemis proches.", "MULTI_HIT", 3, 5, 12, mt=3, cd=12, tags=["summon"], mode="NEAREST", hits=4, interval=5),
          S(4, "KON", "Une immense tête de renard s'abat sur la zone visée et frappe tous les ennemis.", "AOE_BURST", 11, 20, 20, rad=7, mt=8, cd=110, tags=["summon"], center="AIM", delay=16, style="HEAD", knockback=0.8, up=0.5),
      ], P("Maître des invocations", "Les dégâts de tes invocations augmentent de 25 %.", **{"summon-bonus": 0.25}))

demon("ghost", "Démon Fantôme", "Des mains invisibles obéissent à ton regard.",
      "rare", "PHANTOM_MEMBRANE", "FOOD", "DUST:230,230,255:1.2", "entity.phantom.swoop", [
          S(1, "Main invisible", "Frappe un ennemi à distance.", "RAY_STRIKE", 4.5, 7, 16, cd=7, knockback=0.5),
          S(2, "Étreinte", "Une main fantôme immobilise la cible.", "ROOT", 0, 0, 12, dur=2.5, cd=15, mode="SINGLE"),
          S(3, "Poing fantôme", "Enchaîne plusieurs coups invisibles sur la cible.", "MULTI_HIT", 3, 5, 10, cd=11, mode="TARGET", hits=4, interval=4, **{"knockback-last": 0.6}),
          S(4, "Main fantôme", "Plusieurs mains attaquent tous les ennemis proches.", "MULTI_HIT", 4.5, 8, 9, rad=9, mt=6, cd=100, mode="AREA", hits=6, interval=6, style="COLUMN"),
      ], P("Légèreté spectrale", "Tu ne subis aucun dégât de chute.", **{"resist-causes": {"FALL": 0}}))

demon("curse", "Démon Malédiction", "Chaque coup laisse une marque. Assez de marques, et la sentence tombe.",
      "epic", "WITHER_ROSE", "HEALTH", "DUST:140,0,160:1.3", "entity.wither.ambient", [
          S(1, "Marque maudite", "Pose deux marques sur la cible visée (6 maximum).", "MARK", 0, 0, 16, dur=20, cd=6, status="curse", stacks=2, **{"max-stacks": 6}),
          S(2, "Triple frappe", "Trois coups successifs sur la cible.", "MULTI_HIT", 3.5, 6, 12, cd=9, mode="TARGET", hits=3, interval=5),
          S(3, "Malédiction", "Dégâts supplémentaires à une cible marquée (+30 % par marque).", "MARK_STRIKE", 5, 9, 16, cd=10, status="curse", consume=False, **{"min-stacks": 1, "per-stack": 0.3, "base-mult": 1.0}),
          S(4, "Sentence", "Frappe dévastatrice sur une cible portant au moins 5 marques. Consomme les marques.", "MARK_STRIKE", 9, 16, 18, cd=90, status="curse", consume=True, **{"min-stacks": 5, "per-stack": 0.2, "base-mult": 1.2}),
      ], P("Attaques maudites", "Tes attaques appliquent progressivement des marques.", **{"mark-on-hit": 1, "mark-status": "curse"}))

demon("snake", "Démon Serpent", "Un serpent qui avale, écrase et empoisonne.",
      "rare", "SLIME_BALL", "FOOD", "DUST:60,200,60:1.3", "entity.cave_spider.hiss", [
          S(1, "Morsure", "Un serpent mord la cible et l'empoisonne.", "SUMMON_BITE", 4, 7, 12, cd=8, travel=6, effects=["poison:80:0"]),
          S(2, "Avalement", "Avale temporairement un ennemi : immobilisé et blessé.", "ROOT", 0, 0, 10, dur=2.5, cd=16, mode="SINGLE", dot=1.2),
          S(3, "Écrasement", "Le serpent s'abat sur une zone.", "AOE_BURST", 5.5, 9, 14, rad=4, mt=5, cd=12, center="AIM", delay=8, style="SLAM", knockback=0.5, up=0.4),
          S(4, "Serpent géant", "Un serpent colossal traverse le champ de bataille et frappe tout sur son passage.", "PROJECTILE", 9, 16, 30, cd=100, mode="FORWARD", speed=1.1, width=2.2, pierce=99, life=30, knockback=0.8),
      ], P("Peau écailleuse", "Immunité au poison.", **{"immune-effects": ["poison"], "resist-causes": {"POISON": 0}}))

demon("future", "Démon du Futur", "Voir ce qui va arriver, c'est déjà l'avoir évité.",
      "legendary", "CLOCK", "EXP", "END_ROD", "block.amethyst_block.chime", [
          S(1, "Vision future", "Révèle les ennemis proches (visible par toi seul).", "REVEAL", 0, 0, 24, rad=24, dur=8, cd=20),
          S(2, "Esquive prédictive", "Esquive automatiquement la prochaine attaque.", "BUFF_SELF", 0, 0, 0, dur=8, cd=25, dodge=1),
          S(3, "Prémonition", "Détecte les joueurs proches, avec leur direction.", "REVEAL", 0, 0, 40, rad=40, dur=10, cd=30, **{"players-only": True, "arrows": True}),
          S(4, "Le futur m'appartient", "Ralentit fortement les ennemis autour de toi et t'accélère.", "ZONE", 0, 0, 10, rad=10, dur=8, mt=10, cd=140, center="SELF", follow=True, tick=10, style="DOME", effects=["slowness:30:2"], **{"self-effects": ["speed:30:2"]}),
      ], P("Instinct prémonitoire", "8 % de chances d'esquiver une attaque.", **{"dodge-chance": 0.08}))

demon("mold", "Démon Moisissure", "Une infection qui se propage de corps en corps.",
      "common", "FERMENTED_SPIDER_EYE", "FOOD", "DUST:90,150,40:1.3", "entity.slime.squish", [
          S(1, "Infection", "Infecte la cible : poison et dégâts sur la durée.", "EFFECT_AREA", 0, 0, 14, cd=8, effects=["poison:100:0"], status="infected", **{"status-seconds": 8, "status-power": 1.0}),
          S(2, "Propagation", "Transmet l'infection des cibles infectées aux ennemis proches d'elles.", "EFFECT_AREA", 0, 0, 18, mt=4, cd=14, spread=True, status="infected", effects=["poison:80:0"], **{"spread-radius": 5, "status-seconds": 8, "status-power": 1.0}),
          S(3, "Zone contaminée", "Une zone qui ralentit et ronge les ennemis.", "ZONE", 1.0, 2.0, 16, rad=5, dur=8, mt=6, cd=18, center="AIM", tick=10, style="RING", effects=["slowness:30:1"]),
          S(4, "Épidémie", "Infecte tous les ennemis dans une grande zone.", "EFFECT_AREA", 0, 0, 14, rad=14, mt=12, cd=120, center="SELF", status="infected", effects=["poison:120:1", "slowness:80:0"], **{"status-seconds": 10, "status-power": 1.5}),
      ], P("Putréfaction", "Les dégâts d'infection augmentent progressivement.", **{"infect-ramp": 0.15}))

demon("stone", "Démon Pierre", "La pierre ne cède pas, et ceux qu'elle touche non plus.",
      "uncommon", "STONE_BRICKS", "FOOD", "BLOCK:STONE", "block.stone.break", [
          S(1, "Pétrification", "Immobilise un ennemi.", "ROOT", 0, 0, 12, dur=2.5, cd=14, mode="SINGLE"),
          S(2, "Prison de pierre", "Enferme la cible dans une cage temporaire.", "CAGE", 0, 0, 12, dur=5, cd=20, material="STONE_BRICKS"),
          S(3, "Éboulement", "Des blocs de pierre s'écrasent sur la zone visée.", "PROJECTILE", 4, 7, 16, rad=4, mt=6, cd=16, mode="SKY", count=8, stagger=3, speed=1.4, height=9, block="STONE", explode=2.0, **{"explode-mult": 1.0}),
          S(4, "Jugement de pierre", "Pétrifie plusieurs ennemis dans une zone.", "ROOT", 6, 10, 20, rad=7, dur=3, mt=8, cd=110, mode="AREA", center="AIM"),
      ], P("Peau de pierre", "Réduit tous les dégâts reçus de 12 %.", **{"resist-all": 0.12}))

demon("octopus", "Démon Pieuvre", "Des tentacules qui saisissent, aveuglent et broient.",
      "rare", "INK_SAC", "FOOD", "DUST:40,40,80:1.4", "entity.squid.squirt", [
          S(1, "Tentacule", "Un tentacule frappe un ennemi à distance.", "RAY_STRIKE", 5, 8, 14, cd=7, knockback=0.2),
          S(2, "Capture", "Attire un ennemi vers toi.", "RAY_STRIKE", 2, 4, 14, cd=13, pull=1.3),
          S(3, "Encre", "Aveugle et ralentit les ennemis dans une zone.", "EFFECT_AREA", 0, 0, 14, rad=6, mt=8, cd=16, center="AIM", particle="SQUID_INK", effects=["blindness:80:0", "slowness:60:0"]),
          S(4, "Kraken", "Une pieuvre immense frappe plusieurs ennemis à la suite.", "MULTI_HIT", 4.5, 9, 10, rad=10, mt=8, cd=110, mode="AREA", hits=6, interval=6, style="COLUMN", **{"knockback-last": 0.7}),
      ], P("Nageur", "Vitesse augmentée dans l'eau.", **{"water-speed-amp": 1}))

demon("darkness", "Démon des Ténèbres", "Le noir n'est pas l'absence de lumière : c'est un appétit.",
      "mythic", "ECHO_SHARD", "EXP", "DUST:25,0,40:1.6", "entity.warden.heartbeat", [
          S(1, "Obscurité", "Plonge une zone dans le noir.", "EFFECT_AREA", 0, 0, 18, rad=8, mt=10, cd=16, center="AIM", effects=["darkness:120:0", "blindness:40:0"]),
          S(2, "Bras des ténèbres", "Des bras d'ombre frappent plusieurs ennemis.", "MULTI_HIT", 4.5, 8, 8, rad=8, mt=5, cd=12, mode="AREA", hits=4, interval=6, style="COLUMN"),
          S(3, "Gravité", "Attire les ennemis vers un point.", "ZONE", 0, 0, 18, rad=6, dur=2.5, mt=8, cd=18, center="AIM", tick=5, style="DARK", pull=0.45),
          S(4, "Dimension noire", "Une zone sombre où les ennemis subissent dégâts et ralentissements.", "ZONE", 2.5, 5, 16, rad=9, dur=8, mt=10, cd=150, center="AIM", tick=10, style="DARK", effects=["slowness:30:1", "darkness:40:0"]),
      ], P("Enfant de la nuit", "Vision nocturne et immunité à la cécité, aux ténèbres et à la nausée.", **{"perm-effects": ["night_vision:100:0"], "immune-effects": ["blindness", "darkness", "nausea"]}))

demon("gun", "Démon Pistolet", "Une balle n'attend pas qu'on la prépare.",
      "legendary", "CROSSBOW", "FOOD", "DUST:255,255,190:0.8", "entity.firework_rocket.blast", [
          S(1, "Tir rapide", "Tire un projectile très rapide.", "PROJECTILE", 5.5, 9, 40, cd=5, mode="FORWARD", speed=4.0, width=0.3, life=15),
          S(2, "Rafale", "Tire sur plusieurs cibles à la fois.", "PROJECTILE", 3, 5, 24, mt=4, cd=12, mode="AUTO", count=5, stagger=3, speed=3.5, width=0.3, life=15),
          S(3, "Verrouillage", "Marque automatiquement les ennemis proches : ils subissent +20 % de tes dégâts.", "MARK", 0, 0, 20, rad=20, dur=8, mt=6, cd=18, status="lock", stacks=1, **{"max-stacks": 1}),
          S(4, "Pluie de balles", "Une grande rafale de projectiles.", "PROJECTILE", 2.5, 4.5, 26, mt=6, cd=120, mode="AUTO", count=24, stagger=2, speed=3.5, width=0.35, life=14, spread=8, **{"allow-empty": True}),
      ], P("Tireur d'élite", "Portée +20 %, visée assistée élargie, +20 % de dégâts sur les cibles verrouillées.", **{"range-bonus": 0.2, "aim-bonus": 3, "marked-bonus": 0.2, "marked-status": "lock"}))

demon("punishment", "Démon Punition", "Chaque crime a son verdict.",
      "rare", "GOLDEN_SWORD", "FOOD", "DUST:255,215,0:1.2", "block.bell.use", [
          S(1, "Jugement", "Marque un ennemi comme coupable.", "MARK", 0, 0, 16, dur=15, cd=8, status="judge", stacks=1, **{"max-stacks": 1}),
          S(2, "Sentence", "Dégâts supplémentaires sur une cible marquée.", "MARK_STRIKE", 5, 9, 16, cd=9, status="judge", consume=False, **{"min-stacks": 1, "per-stack": 0.5, "base-mult": 1.0}),
          S(3, "Zone punitive", "Ralentit les ennemis proches.", "ZONE", 0, 0, 14, rad=6, dur=6, mt=6, cd=16, center="AIM", tick=10, style="RING", effects=["slowness:30:1"]),
          S(4, "Verdict", "Frappe tous les ennemis marqués à portée.", "MARK_STRIKE", 8, 15, 24, mt=8, cd=100, status="judge", consume=True, all=True, **{"min-stacks": 1, "per-stack": 0.0, "base-mult": 1.2}),
      ], P("Loi implacable", "Les ennemis marqués subissent 20 % de dégâts en plus.", **{"marked-bonus": 0.2, "marked-status": "judge"}))

demon("claw", "Démon Griffes", "Rapide, sauvage, imprévisible.",
      "uncommon", "IRON_SWORD", "FOOD", "CRIT", "entity.player.attack.sweep", [
          S(1, "Griffure", "Attaque rapide en arc devant toi.", "MELEE_ARC", 4.5, 7, 4.5, mt=3, cd=4, angle=110, knockback=0.3),
          S(2, "Déchiquetage", "Combo de trois attaques.", "MULTI_HIT", 3, 5, 5, cd=9, mode="TARGET", hits=3, interval=4),
          S(3, "Bond", "Saute sur un ennemi et le frappe.", "LEAP", 5, 8, 12, cd=10, mode="TARGET", speed=1.5, knockback=0.4),
          S(4, "Prédateur", "Enchaîne de nombreuses attaques rapides sur les ennemis proches.", "MULTI_HIT", 2.5, 5, 6, rad=6, mt=4, cd=90, mode="AREA", hits=8, interval=4),
      ], P("Instinct de chasseur", "Vitesse d'attaque +25 %.", attributes={"attack_speed": 0.25}))

demon("knife", "Démon Couteau", "Des lames qui n'existent que pour trancher.",
      "uncommon", "FLINT", "FOOD", "CRIT", "item.trident.throw", [
          S(1, "Lame spectrale", "Lance une lame magique.", "PROJECTILE", 4.5, 8, 28, cd=5, mode="FORWARD", speed=2.2, width=0.5, life=20, item="IRON_SWORD"),
          S(2, "Double lame", "Lance deux lames successives.", "PROJECTILE", 3.5, 6.5, 28, cd=8, mode="FORWARD", count=2, stagger=4, speed=2.2, width=0.5, life=20, item="IRON_SWORD"),
          S(3, "Danse des lames", "Éventail de projectiles.", "PROJECTILE", 2.5, 5, 20, cd=13, mode="FORWARD", count=7, fan=True, spread=35, speed=2.0, width=0.4, life=16),
          S(4, "Tempête de couteaux", "Une pluie de lames s'abat sur la zone visée.", "PROJECTILE", 2.5, 4.5, 20, rad=7, mt=8, cd=100, mode="SKY", count=28, stagger=1, speed=1.6, height=10, item="IRON_SWORD", explode=1.6, **{"explode-mult": 1.0}),
      ], P("Maître du corps à corps", "+20 % de dégâts à moins de 4,5 blocs.", **{"close-range-bonus": 0.2, "close-range-distance": 4.5}))

demon("needle", "Démon Aiguille", "Fines, nombreuses, impossibles à ignorer.",
      "uncommon", "END_ROD", "FOOD", "DUST:230,230,230:0.7", "block.chain.hit", [
          S(1, "Épinglage", "Cloue un ennemi sur place.", "ROOT", 2, 4, 14, dur=2, cd=12, mode="SINGLE"),
          S(2, "Pluie d'aiguilles", "Une averse d'aiguilles sur une zone.", "ZONE", 3.5, 6, 16, rad=5, dur=3, mt=6, cd=14, center="AIM", tick=5, style="RING"),
          S(3, "Fil invisible", "Un réseau de fils ralentit ceux qui le traversent.", "ZONE", 0, 0, 14, rad=6, dur=8, mt=6, cd=18, center="AIM", tick=10, style="THREADS", effects=["slowness:30:1"]),
          S(4, "Mille aiguilles", "Une quantité immense d'aiguilles.", "PROJECTILE", 2, 3.5, 22, rad=8, mt=8, cd=100, mode="SKY", count=36, stagger=1, speed=1.8, height=10, explode=1.2, **{"explode-mult": 1.0}),
      ], P("Paralysie", "Tes attaques ralentissent brièvement l'ennemi.", **{"slow-on-hit-ticks": 40, "slow-on-hit-amp": 0}))

demon("mantis", "Démon Mante", "Une faux à la place du bras.",
      "common", "IRON_HOE", "FOOD", "DUST:120,255,120:1.0", "item.axe.strip", [
          S(1, "Faux", "Attaque en arc de cercle.", "MELEE_ARC", 5, 8, 5, mt=4, cd=6, angle=160, knockback=0.3),
          S(2, "Découpe", "Puissante attaque tranchante devant toi.", "MELEE_ARC", 8, 13, 8, mt=3, cd=11, angle=25, knockback=0.5, lunge=0.6),
          S(3, "Saut prédateur", "Bondit sur une cible.", "LEAP", 6, 10, 14, cd=12, mode="TARGET", speed=1.6, knockback=0.4),
          S(4, "Faucheuse", "Attaque circulaire autour de toi.", "AOE_BURST", 9, 16, 6, rad=6, mt=8, cd=90, center="SELF", style="SPIN", knockback=0.7, up=0.4),
      ], P("Bond de la mante", "+25 % de dégâts quand tu es en l'air ou après un saut.", **{"air-bonus": 0.25}))

demon("fire", "Démon Feu", "Une flamme qui devient toi.",
      "epic", "BLAZE_POWDER", "HEALTH", "FLAME", "entity.blaze.shoot", [
          S(1, "Flammes", "Lance une boule de feu.", "PROJECTILE", 5, 8, 30, cd=6, tags=["fire"], mode="FORWARD", speed=1.3, width=0.7, life=30, explode=2.0, **{"explode-mult": 0.6, "fire-ticks": 60}),
          S(2, "Explosion", "Une explosion de flammes.", "AOE_BURST", 6, 10, 16, rad=5, mt=6, cd=12, tags=["fire"], center="AIM", style="EXPLOSION", knockback=0.7, up=0.4, **{"fire-ticks": 80}),
          S(3, "Transformation", "Ton corps s'embrase : aura de feu, vitesse, nouvelle apparence.", "BUFF_SELF", 0, 0, 0, rad=3.5, dur=12, mt=4, cd=30, tags=["fire"], effects=["speed:240:0"], **{"aura-damage": 3, "aura-radius": 3.5, "armor": ["LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"], "armor-color": "255,70,0"}),
          S(4, "Éveil du feu", "Forme démoniaque : aura brûlante, force, vitesse et résistance.", "BUFF_SELF", 0, 0, 0, rad=5, dur=20, mt=8, cd=150, tags=["fire"], effects=["speed:400:1", "strength:400:0", "resistance:400:0"], **{"aura-damage": 6, "aura-radius": 5, "armor": ["LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"], "armor-color": "255,20,0"}),
      ], P("Corps ardent", "Immunité au feu et à la lave ; +20 % de dégâts de feu.", **{"resist-causes": {"FIRE": 0, "FIRE_TICK": 0, "LAVA": 0, "HOT_FLOOR": 0}, "fire-bonus": 0.2}))

demon("blood", "Démon Sang", "Le sang est une arme, un bouclier, un remède.",
      "epic", "REDSTONE", "BLOOD", "DUST:170,0,0:1.3", "entity.generic.splash", [
          S(1, "Lance de sang", "Une lance de sang perce la cible.", "PROJECTILE", 5.5, 9, 36, cd=6, mode="FORWARD", speed=2.2, width=0.5, life=24, pierce=1, homing=0.08, item="BLAZE_ROD"),
          S(2, "Bouclier de sang", "Une protection d'absorption temporaire.", "BUFF_SELF", 0, 0, 0, dur=10, cd=18, cost=12, absorption=6),
          S(3, "Régénération", "Restaure une partie de ta vie.", "BUFF_SELF", 0, 0, 0, dur=5, cd=20, cost=15, heal=6, effects=["regeneration:100:1"]),
          S(4, "Tempête de sang", "Plusieurs lances de sang visent les ennemis proches.", "PROJECTILE", 4.5, 8, 26, mt=5, cd=110, mode="AUTO", count=8, stagger=3, speed=2.2, width=0.5, life=24, homing=0.1, item="BLAZE_ROD", **{"allow-empty": True}),
      ], P("Soif de sang", "Tu récupères 3 PV à chaque ennemi éliminé.", **{"heal-on-kill": 3}))

demon("war", "Démon Guerre", "Toute arme est la tienne.",
      "epic", "NETHERITE_SWORD", "EXP", "CRIT", "item.trident.hit", [
          S(1, "Arme improvisée", "Transforme l'objet autorisé que tu tiens en arme magique (+50 % de dégâts).", "BUFF_SELF", 0, 0, 0, dur=15, cd=25, tags=["weapon"], **{"weapon-items": ["STICK", "BONE", "BLAZE_ROD", "*_SWORD", "*_AXE", "*_SHOVEL", "*_PICKAXE", "*_HOE"], "weapon-bonus": 0.5}),
          S(2, "Épée de guerre", "Invoque une épée puissante qui traverse les ennemis.", "PROJECTILE", 8, 14, 32, cd=12, tags=["weapon"], mode="FORWARD", speed=1.6, width=0.8, life=25, pierce=1, item="NETHERITE_SWORD"),
          S(3, "Lance de guerre", "Lance une arme sur un ennemi.", "PROJECTILE", 6, 11, 40, cd=10, tags=["weapon"], mode="FORWARD", speed=2.4, width=0.5, life=22, pierce=2, item="TRIDENT"),
          S(4, "Arsenal de guerre", "Une pluie d'armes s'abat sur la zone visée.", "PROJECTILE", 4, 7, 22, rad=7, mt=8, cd=120, tags=["weapon"], mode="SKY", count=20, stagger=2, speed=1.6, height=10, item="NETHERITE_SWORD", explode=2.0, **{"explode-mult": 1.0}),
      ], P("Maître d'armes", "+20 % de dégâts avec les armes invoquées.", **{"weapon-bonus": 0.2}))

demon("eternity", "Démon Éternité", "Un instant qui ne finit pas.",
      "legendary", "RECOVERY_COMPASS", "EXP", "DUST:200,160,255:1.2", "block.beacon.ambient", [
          S(1, "Boucle temporelle", "Une zone où le temps ralentit.", "ZONE", 0, 0, 16, rad=6, dur=7, mt=8, cd=20, center="AIM", tick=10, style="DOME", effects=["slowness:30:2"]),
          S(2, "Piège dimensionnel", "Enferme les ennemis présents dans une petite zone.", "ZONE", 0, 0, 16, rad=3.5, dur=4, mt=4, cd=22, center="AIM", tick=5, style="DOME", boundary=True, effects=["slowness:15:0"]),
          S(3, "Régénération", "Restaure progressivement ta vie.", "BUFF_SELF", 0, 0, 0, dur=8, cd=24, heal=4, effects=["regeneration:160:1"]),
          S(4, "Boucle éternelle", "Une arène temporaire où les ennemis sont piégés et ralentis.", "ZONE", 1.5, 3, 18, rad=10, dur=6, mt=8, cd=140, center="AIM", tick=5, style="DOME", boundary=True, effects=["slowness:20:1"]),
      ], P("Flux temporel", "Récupération de vie accélérée.", **{"regen-per-cycle": 0.5}))

demon("typhoon", "Démon Typhon", "Le vent décide de ce qui reste debout.",
      "rare", "FEATHER", "FOOD", "CLOUD", "item.elytra.flying", [
          S(1, "Rafale", "Projette les ennemis devant toi.", "MELEE_ARC", 2.5, 4, 9, mt=5, cd=8, angle=80, knockback=1.7, up=0.6),
          S(2, "Tornade", "Une petite tornade qui soulève et blesse.", "ZONE", 2.5, 5, 16, rad=3.5, dur=5, mt=5, cd=18, center="AIM", tick=5, style="VORTEX", lift=0.25, pull=0.2),
          S(3, "Tempête", "Ralentit les ennemis et les attire.", "ZONE", 0, 0, 16, rad=7, dur=5, mt=8, cd=20, center="AIM", tick=5, style="VORTEX", pull=0.4, effects=["slowness:20:1"]),
          S(4, "Catastrophe", "Une immense tornade ravage la zone.", "ZONE", 3, 6, 20, rad=11, dur=8, mt=10, cd=140, center="AIM", tick=5, style="VORTEX", lift=0.3, pull=0.45, effects=["slowness:20:1"]),
      ], P("Œil du cyclone", "Forte résistance au recul.", **{"knockback-resist": 0.6, "attributes": {"knockback_resistance": 0.8}}))

demon("bat", "Démon Chauve-souris", "Il voit avec ses oreilles et frappe avec sa voix.",
      "common", "BAT_SPAWN_EGG", "FOOD", "DUST:150,150,255:1.0", "entity.bat.takeoff", [
          S(1, "Sonar", "Détecte les ennemis proches.", "REVEAL", 0, 0, 20, rad=20, dur=6, cd=14),
          S(2, "Onde sonore", "Repousse les ennemis autour de toi.", "AOE_BURST", 3, 5, 6, rad=6, mt=6, cd=10, center="SELF", style="RING", knockback=1.3, up=0.3),
          S(3, "Bond aérien", "Un bond puissant suivi d'un vol plané.", "BUFF_SELF", 0, 0, 0, dur=8, cd=14, launch=0.9, effects=["slow_falling:160:0"], **{"launch-forward": 0.7}),
          S(4, "Cri du prédateur", "Une puissante onde sonore qui projette tout le monde.", "AOE_BURST", 8, 14, 11, rad=11, mt=10, cd=100, center="SELF", delay=8, style="RING", sound="entity.warden.sonic_boom", knockback=1.8, up=0.5, effects=["slowness:40:0"]),
      ], P("Ailes agiles", "Vitesse de déplacement +10 %.", attributes={"movement_speed": 0.10}))

demon("bomb", "Démon Bombe", "Tout ce qu'il touche finit en poussière.",
      "epic", "TNT", "HEALTH", "FLAME", "entity.generic.explode", [
          S(1, "Explosion", "Provoque une explosion contrôlée (sans dégâts au terrain).", "AOE_BURST", 6, 10, 14, rad=4, mt=5, cd=9, center="AIM", style="EXPLOSION", knockback=1.0, up=0.5),
          S(2, "Dash explosif", "Te propulse vers l'avant en laissant une explosion derrière toi.", "LEAP", 4, 7, 0, mt=4, cd=12, mode="FORWARD", speed=1.8, lift=0.25, knockback=0.8, **{"burst-start": 3, "max-ticks": 12}),
          S(3, "Bombe à retardement", "Place une bombe qui explose après 3 secondes.", "AOE_BURST", 10, 16, 14, rad=5, mt=6, cd=20, center="AIM", delay=60, marker="TNT", style="EXPLOSION", knockback=1.2, up=0.6),
          S(4, "Détonation", "Une immense explosion autour de toi (tu en subis une partie).", "AOE_BURST", 11, 20, 9, rad=9, mt=10, cd=120, center="SELF", delay=10, style="EXPLOSION", knockback=1.8, up=0.7, **{"self-damage": 4}),
      ], P("Corps ignifuge", "Dégâts d'explosion réduits de 35 %.", **{"resist-causes": {"BLOCK_EXPLOSION": 0.65, "ENTITY_EXPLOSION": 0.65}}))

# Démons dont les types de compétences arrivent en phase 1B (définis dans demons.yml, désactivés, sans compétences).
PLANNED = {
    "skin": ("Démon Peau", "Voler l'apparence d'un autre.", "rare", "LEATHER_HELMET", "Phase 1B : apparence / pseudo copiés"),
    "doll": ("Démon Poupée", "Des poupées combattantes et des mobs marionnettes.", "rare", "ARMOR_STAND", "Phase 1B : invocations et contrôle de mobs"),
    "hell": ("Démon Enfer", "Une main géante, un portail, l'Enfer lui-même.", "epic", "NETHERRACK", "Phase 1B : arènes dimensionnelles"),
    "control": ("Démon Contrôle", "Dominer les mobs, immobiliser les ennemis.", "legendary", "LEAD", "Phase 1B : contrôle de mobs"),
    "aging": ("Démon Vieillesse", "Le temps ronge tout, même les dimensions.", "mythic", "SAND", "Phase 1B : arènes dimensionnelles"),
    "zombie": ("Démon Zombie", "Une horde qui ne cesse de grandir.", "common", "ROTTEN_FLESH", "Phase 1B : invocations de hordes"),
    "shark": ("Démon Requin", "Il nage dans le sable comme dans l'eau.", "common", "PRISMARINE_SHARD", "Phase 1B : plongeon à travers les blocs"),
}


def main():
    skills, demons, cooldowns, costs = {}, {}, {}, {}
    for did, d in DEMONS.items():
        ids = []
        base = did
        slot_names = {1: "a", 2: "b", 3: "c", 4: "ult"}
        for s in d["skills"]:
            sid = f"{base}_{slot_names[s['slot']]}"
            ids.append(sid)
            e = {
                "demon": did, "slot": s["slot"], "type": s["type"], "name": s["name"], "description": s["desc"],
                "damage-pvp": s["pvp"], "damage-pve": s["pve"], "range": s["rng"], "radius": s["rad"],
                "duration": s["dur"], "max-targets": s["mt"],
                "particle": s["particle"] or d["particle"], "sound": s["sound"] or d["sound"],
            }
            if s["tags"]:
                e["tags"] = s["tags"]
            params = dict(s["params"])
            if s["type"] == "PROJECTILE":
                mode = params.get("mode", "FORWARD")
                # durée de vie = portée / vitesse : le projectile ne dépasse jamais la portée affichée
                params["life"] = 80 if mode == "SKY" else max(5, int(-(-s["rng"] // params.get("speed", 1.2))))
            if params:
                e["params"] = params
            skills[sid] = e
            cooldowns[sid] = s["cd"]
            amount = s["cost"] if s["cost"] is not None else COST[d["cost"]][s["slot"] - 1]
            costs[sid] = {"type": d["cost"], "amount": amount}
        pid = f"{did}_passive"
        skills[pid] = {
            "demon": did, "slot": 0, "type": "PASSIVE", "name": d["passive"]["name"], "description": d["passive"]["desc"],
            "params": d["passive"]["params"],
        }
        demons[did] = {
            "name": d["name"], "description": d["desc"], "rarity": d["rarity"], "icon": d["icon"], "enabled": True,
            "secret": False, "skills": ids, "passive": pid,
        }
    for did, (name, desc, rarity, icon, reason) in PLANNED.items():
        demons[did] = {"name": name, "description": desc, "rarity": rarity, "icon": icon, "enabled": False,
                       "secret": False, "disabled-reason": reason, "skills": [], "passive": ""}

    def dump(name, data, header):
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
            f.write(header + "\n")
            yaml.dump(data, f, sort_keys=False, allow_unicode=True, width=200, default_flow_style=False)

    # ordre d'affichage : celui de la spec (raretés regroupées par le menu via l'ordre de ce fichier)
    order = ["fox", "ghost", "curse", "snake", "future", "mold", "skin", "doll", "stone", "octopus", "hell", "darkness", "control",
             "gun", "punishment", "claw", "knife", "needle", "mantis", "fire", "aging", "blood", "war", "eternity", "zombie",
             "typhoon", "bat", "shark", "bomb"]
    demons = {k: demons[k] for k in order}
    dump("demons.yml", {"demons": demons},
         "# Démons. enabled:false = masqué/indisponible. Les identifiants (clés) sont stables : ne les renomme pas.\n"
         "# Un démon est automatiquement désactivé si une de ses compétences utilise un type inconnu.")
    dump("skills.yml", {"skills": skills},
         "# Compétences. slot 1-3 = normales, 4 = ultime, 0 = passif. damage-* : dégâts PAR COUP (par SECONDE pour les ZONE).\n"
         "# range en blocs, radius en blocs, duration en secondes. params : voir docs/GUIDE_CONFIGURATION.md")
    dump("cooldowns.yml", {"cooldowns": cooldowns}, "# Cooldowns en secondes, par identifiant de compétence.")
    dump("sacrifices.yml", {"costs": costs},
         "# Coût de chaque compétence. type : HEALTH (PV) | FOOD (faim) | EXP (points d'XP) | BLOOD (sang) | DURABILITY | NONE")
    print(f"{len(DEMONS)} démons actifs, {len(PLANNED)} planifiés, {len(skills)} compétences.")


if __name__ == "__main__":
    main()
