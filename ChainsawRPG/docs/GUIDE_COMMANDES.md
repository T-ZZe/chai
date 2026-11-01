# Guide des commandes

Alias : `/cs`, `/csm`. Sans argument : menu principal.

## Joueurs (permission `chainsawman.use`, accordée par défaut)

| Commande | Effet |
|---|---|
| `/chainsaw` | Menu principal |
| `/chainsaw contrat` | Contrat actuel (démon, rareté, niveau, XP) |
| `/chainsaw demons` | Liste de tous les démons (consultation) |
| `/chainsaw skills` | Compétences du contrat actif (dégâts, cooldown, coût, état) |
| `/chainsaw heads` | Nombre de têtes **valides** dans l'inventaire |
| `/chainsaw stats` | Statistiques et contrats possédés |
| `/chainsaw shop` | Menu des contrats (réservé aux détenteurs de `chainsawman.bypass.npc` si `contracts.require-npc: true`) |
| `/chainsaw cast <1-4>` | Lance la compétence 1, 2, 3 ou l'ultime |
| `/chainsaw book` | Récupère le Livre des contrats |
| `/chainsaw help` | Aide |

## Administration (permission `chainsawman.admin`, op par défaut)

| Commande | Effet |
|---|---|
| `/chainsaw admin` | Menu d'administration |
| `/chainsaw giveheads <joueur> <qté>` | Donne des têtes **enregistrées** (max 64 par appel) |
| `/chainsaw removeheads <joueur> <qté>` | Retire des têtes et les invalide |
| `/chainsaw givecontract <joueur> <démon\|random>` | Attribue un contrat sans coût (`random` = tirage pondéré par rareté) |
| `/chainsaw removecontract <joueur>` | Retire le contrat actif (le niveau est conservé) |
| `/chainsaw reset <joueur>` | Efface contrats et sang du joueur |
| `/chainsaw setlevel <joueur> <niveau>` | Fixe le niveau du contrat actif (1–50) |
| `/chainsaw npc spawn` / `remove` | Place un Marchand à ta position / supprime ceux à moins de 4 blocs |
| `/chainsaw reload` | Recharge les YAML (sauf la section `database`) |

Les commandes ciblant un joueur exigent qu'il soit **en ligne** (phase 1).
Commandes prévues dans les phases suivantes : `missions`, `boss`, `factions`, `event <nom>`, `spawn <boss>`.

## Permissions

| Permission | Défaut | Rôle |
|---|---|---|
| `chainsawman.use` | tous | menus et compétences |
| `chainsawman.admin` | op | commandes d'administration |
| `chainsawman.bypass.npc` | op | ouvrir le menu des contrats sans PNJ |
| `chainsawman.bypass.cooldown` | personne | ignorer les cooldowns (tests) |
| `chainsawman.bypass.protection` | personne | lancer des compétences au spawn / en zone interdite |
