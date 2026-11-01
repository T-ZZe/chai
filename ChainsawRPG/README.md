# ChainsawRPG — Phase 1 (v0.1.0)

Plugin RPG inspiré de *Chainsaw Man* pour **Paper 26.3** (Java 25).
Phase 1 : architecture, base de données, têtes de joueurs, contrats, démons, compétences, cooldowns, menus.

## ⚠️ État réel de ce livrable (à lire)

| Élément | État |
|---|---|
| Code source complet de la Phase 1 (41 classes, ~5 000 lignes) | ✅ livré |
| Syntaxe Java (javac 25, analyse syntaxique) | ✅ vérifiée |
| Cohérence interne entre mes classes (méthodes/signatures) | ✅ vérifiée par compilation partielle |
| Cohérence YAML ↔ code (types, paramètres, références, messages) | ✅ vérifiée par `tools/validate_configs.py` |
| **Compilation complète contre l'API Paper 26.3** | ❌ **NON effectuée** — mon environnement n'a pas accès à `repo.papermc.io` |
| **JAR final** | ❌ **non produit** (même raison) → utilise `./build.sh` ou le workflow GitHub fourni |
| **Test sur un serveur en fonctionnement** | ❌ **jamais exécuté** |

Conséquence honnête : il est **probable** qu'il reste quelques erreurs de compilation ou de comportement liées
à l'API Paper 26.x que je n'ai pas pu vérifier. Si `mvn package` signale des erreurs, **envoie-les moi telles quelles** :
ce sera une correction de quelques minutes, pas un problème d'architecture.

Points d'API que j'ai identifiés comme les plus susceptibles de poser problème (par ordre de risque) :
`Player#sendEquipmentChange`, `Display#setTeleportDuration`, `AttributeModifier(NamespacedKey, double, Operation)`,
`AsyncPlayerPreLoginEvent#disallow(Result, Component)`, `Mob#setAware`, pilotes JDBC fournis par Paper.
Les identifiants configurables (effets, attributs, particules) sont résolus par réflexion : un nom renommé dans
l'API produit un avertissement en console, pas une erreur de compilation.

## Ce qui fonctionne dans cette phase

* **22 démons sur 29** entièrement définis (4 compétences + passif chacun, soit 88 compétences actives + 22 passifs).
  Les 7 autres (Peau, Poupée, Enfer, Contrôle, Vieillesse, Zombie, Requin) sont **présents mais désactivés** (affichés
  « indisponible » dans le menu) : leurs mécaniques — copie d'apparence, invocations/contrôle de mobs, arènes
  dimensionnelles, plongeon à travers les blocs — arrivent en **Phase 1B**. Aucune compétence affichée n'est factice.
* Têtes uniques, enregistrées en base, anti-duplication, anti-farm (voir `docs/GUIDE_CONFIGURATION.md`).
* Contrats : achat (9 têtes), rituel de changement avec coût supplémentaire, niveaux par contrat, sauvegarde.
* Marchand de contrats (PNJ), menus (principal, contrats, confirmation, compétences, stats, admin), HUD action bar.
* Coûts (PV, faim, XP, sang, durabilité), cooldowns serveur, sang (gain sur kills, perte à la mort configurable).
* Moteur de compétences : 15 types réutilisables, plafonds anti-abus (combo, immobilisation, dégâts, zones, projectiles),
  vérification des murs, protection du spawn et zones interdites.

## Prérequis et installation

1. Serveur **Paper 26.3** (Java **25**). SQLite et MySQL sont fournis par Paper.
2. Compiler (au choix) :
   * Local : JDK 25 + Maven 3.9+ → `./build.sh` → `target/ChainsawRPG-0.1.0.jar`
   * Sans rien installer : pousse ce dossier sur GitHub → onglet *Actions* → télécharge l'artefact `ChainsawRPG-jar`
3. Copie le JAR dans `plugins/`, démarre le serveur : les fichiers YAML sont créés dans `plugins/ChainsawRPG/`.
4. En jeu (op) : `/chainsaw npc spawn` à l'endroit voulu pour placer le **Marchand de contrats**.
5. Pour tester seul : `/chainsaw giveheads <toi> 9` puis clic droit sur le Marchand.

Si le pom ne résout pas `paper-api`, remplace `[26.3.build,)` par une version exacte (ex. `26.3.build.231-stable`,
à vérifier sur https://repo.papermc.io/#browse/browse:maven-public:io%2Fpapermc%2Fpaper%2Fpaper-api).

## Lancer les compétences

* **Shift + touche 1/2/3/4** : compétences 1, 2, 3, ultime (désactivable : `combat.cast-sneak-hotbar`).
  ⚠️ Shift + molette parcourt aussi ces emplacements : désactive l'option si ça gêne.
* **Livre des contrats** (donné à la signature) : clic droit = lancer, Shift + clic droit = changer de compétence, clic gauche = menu.
* `/chainsaw cast <1-4>` (utile pour une macro).

## Documentation

* `docs/GUIDE_COMMANDES.md`, `docs/GUIDE_CONFIGURATION.md`
* `docs/GUIDE_AJOUTER_UN_DEMON.md`, `docs/GUIDE_AJOUTER_UNE_COMPETENCE.md`

## Feuille de route

| Phase | Contenu | État |
|---|---|---|
| 1 | Socle, têtes, contrats, démons, compétences, cooldowns, menus | ✅ ce livrable (22/29 démons) |
| 1B | Types manquants : DISGUISE, MINIONS, CONTROL_MOB, ARENA, DIVE → 7 démons restants | à faire |
| 2 | Sacrifices permanents + confirmation, scoreboard du sang, paliers de niveaux, éveils, fragments, menu de fabrication | à faire |
| 3 | Boss, missions, événements, factions, autres PNJ | à faire |
| 4 | Hybrides, démons secrets, objets restants, resource pack | à faire |
| 5 | Classements, optimisation, tests | à faire |
