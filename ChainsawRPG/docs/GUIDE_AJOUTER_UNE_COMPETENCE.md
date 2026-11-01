# Ajouter une compétence / un nouveau type de compétence

## A. Nouvelle compétence avec un type existant
Ajoute une entrée dans `skills.yml` (ou un `S(...)` dans le générateur), un cooldown dans `cooldowns.yml`
et un coût dans `sacrifices.yml`, puis référence son identifiant dans `skills:` du démon. Rien à compiler.

## B. Nouveau type (mécanique inédite)
1. Crée une méthode dans une classe de `fr.chainsawrpg.skill.types` :

```java
static boolean thunder(Cast c) {
    LivingEntity t = c.aim();                       // cible visée (murs vérifiés, aide à la visée)
    if (t == null) return c.fail("no-target");      // clé de messages.yml ; rien n'est consommé si on retourne false
    c.castFx(c.p.getLocation());                    // son + particules de la compétence
    c.hit(t);                                       // dégâts PvP/PvE avec niveau, plafonds et anti-combo
    c.effects(t);                                   // effets de potion (params: effects), durée plafonnée en PvP
    double bonus = c.d("bonus", 0);                 // lit params.bonus (c.d, c.i, c.s, c.b)
    return true;                                    // true = coût payé + cooldown lancé
}
```
2. Enregistre-la dans la méthode `register` de la classe : `m.register("THUNDER", Foo::thunder);`
3. Utilise `type: THUNDER` dans `skills.yml`.
4. Lance `python3 tools/validate_configs.py` : il lit les clés `c.d/i/s/b("...")` de ta méthode et refuse tout paramètre YAML inconnu.

## Règles à respecter (sécurité / performance)
* Tout se passe sur le **thread principal** ; n'appelle jamais l'API Bukkit depuis un thread asynchrone.
* Passe par `c.hit(...)` pour les dégâts, `c.enemies(...)` / `c.aim()` pour les cibles (validité, murs, protections).
* Pas de téléportation de joueurs ; utilise la vélocité (voir `LEAP`).
* Toute entité temporaire : `c.pl.temps.add(entity)` et suppression explicite ; tout bloc temporaire : `c.pl.tempBlocks.set(...)`.
* Tâche répétée : `BukkitRunnable` qui se termine seul (vérifie `p.isOnline()`), durée bornée.
