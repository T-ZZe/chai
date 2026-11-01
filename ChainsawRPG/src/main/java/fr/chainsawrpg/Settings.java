package fr.chainsawrpg;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Copie typée de config.yml (évite les lectures YAML sur les chemins chauds). */
public final class Settings {
    public final String dbType, sqliteFile, myHost, myDb, myUser, myPass, myParams;
    public final int myPort, autosaveMinutes, hudInterval;
    public final boolean hud;
    public final double fxDensity;

    public final boolean headsEnabled, headsDirect, headsBlockSameIp, headsNotifyDenied;
    public final int headsRequired, headsVictimCooldownMin, headsPairCooldownH, headsMaxPerDay, headsMinVictimMinutes;
    public final Set<String> headsDisabledWorlds = new HashSet<>();

    public final boolean requireNpc, confirmPurchase, ritualCancelOnMove;
    public final int changeExtraHeads, changeExtraLevels, ritualSeconds;

    public final int maxLevel, xpPerSkill, xpPerMob;
    public final double xpBase, xpExp, dmgPerLevelPve, dmgPerLevelPvp;

    public final long gcdMs, hitGapMs;
    public final double maxHitPvp, maxHitPve, aimAssist;
    public final int maxRootPvp, maxRootPve, rootImmunity, maxEffectPvp, maxZones, maxProjectiles;
    public final boolean targetPassive, requireLos, castSneak, castBook;
    public final Set<String> excludedTypes = new HashSet<>();

    public final boolean spawnProtect;
    public final double spawnRadius;
    public final Set<String> protectedWorlds = new HashSet<>();
    public final List<double[]> zoneCircles = new ArrayList<>();
    public final List<String> zoneWorlds = new ArrayList<>();

    public final int bloodMax, bloodPerMob, bloodPerKill, bloodLossDeathPct;

    public Settings(FileConfiguration c) {
        dbType = c.getString("database.type", "sqlite");
        sqliteFile = c.getString("database.sqlite-file", "chainsawrpg.db");
        myHost = c.getString("database.mysql.host", "localhost");
        myPort = c.getInt("database.mysql.port", 3306);
        myDb = c.getString("database.mysql.database", "chainsawrpg");
        myUser = c.getString("database.mysql.user", "root");
        myPass = c.getString("database.mysql.password", "");
        myParams = c.getString("database.mysql.params", "useSSL=false&characterEncoding=utf8");
        autosaveMinutes = Math.max(1, c.getInt("autosave-minutes", 5));
        hud = c.getBoolean("hud.enabled", true);
        hudInterval = Math.max(5, c.getInt("hud.interval-ticks", 10));
        fxDensity = Math.max(0.1, Math.min(3.0, c.getDouble("fx-density", 1.0)));

        headsEnabled = c.getBoolean("heads.enabled", true);
        headsRequired = Math.max(1, c.getInt("heads.required-for-contract", 9));
        headsDirect = c.getBoolean("heads.give-directly", true);
        headsVictimCooldownMin = c.getInt("heads.victim-cooldown-minutes", 120);
        headsPairCooldownH = c.getInt("heads.pair-cooldown-hours", 24);
        headsMaxPerDay = c.getInt("heads.max-per-killer-per-day", 15);
        headsBlockSameIp = c.getBoolean("heads.block-same-ip", true);
        headsMinVictimMinutes = c.getInt("heads.min-victim-playtime-minutes", 60);
        headsNotifyDenied = c.getBoolean("heads.notify-denied", true);
        headsDisabledWorlds.addAll(c.getStringList("heads.disabled-worlds"));

        requireNpc = c.getBoolean("contracts.require-npc", true);
        confirmPurchase = c.getBoolean("contracts.confirm-purchase", true);
        changeExtraHeads = c.getInt("contracts.change.extra-heads", 3);
        changeExtraLevels = c.getInt("contracts.change.extra-exp-levels", 5);
        ritualSeconds = Math.max(1, c.getInt("contracts.change.ritual-seconds", 6));
        ritualCancelOnMove = c.getBoolean("contracts.change.cancel-on-move", true);

        maxLevel = c.getInt("progression.max-level", 50);
        xpBase = c.getDouble("progression.xp-base", 100);
        xpExp = c.getDouble("progression.xp-exponent", 1.6);
        xpPerSkill = c.getInt("progression.xp-per-skill", 3);
        xpPerMob = c.getInt("progression.xp-per-mob-kill", 5);
        dmgPerLevelPve = c.getDouble("progression.damage-per-level-pve", 0.012);
        dmgPerLevelPvp = c.getDouble("progression.damage-per-level-pvp", 0.006);

        gcdMs = c.getLong("combat.global-cooldown-ms", 400);
        hitGapMs = c.getLong("combat.hit-gap-ms", 120);
        maxHitPvp = c.getDouble("combat.max-hit-damage-pvp", 14);
        maxHitPve = c.getDouble("combat.max-hit-damage-pve", 40);
        maxRootPvp = c.getInt("combat.max-root-seconds-pvp", 3);
        maxRootPve = c.getInt("combat.max-root-seconds-pve", 6);
        rootImmunity = c.getInt("combat.root-immunity-seconds", 6);
        maxEffectPvp = c.getInt("combat.max-effect-seconds-pvp", 8);
        maxZones = c.getInt("combat.max-zones-per-player", 2);
        maxProjectiles = c.getInt("combat.max-projectiles-per-cast", 40);
        aimAssist = c.getDouble("combat.aim-assist-degrees", 10);
        targetPassive = c.getBoolean("combat.target-passive-mobs", false);
        requireLos = c.getBoolean("combat.require-line-of-sight", true);
        castSneak = c.getBoolean("combat.cast-sneak-hotbar", true);
        castBook = c.getBoolean("combat.cast-with-book", true);
        for (String s : c.getStringList("combat.excluded-types")) excludedTypes.add(s.toUpperCase(Locale.ROOT));

        spawnProtect = c.getBoolean("protection.spawn.enabled", true);
        spawnRadius = c.getDouble("protection.spawn.radius", 24);
        protectedWorlds.addAll(c.getStringList("protection.disabled-worlds"));
        List<Map<?, ?>> zones = c.getMapList("protection.zones");
        for (Map<?, ?> z : zones) {
            try {
                zoneWorlds.add(String.valueOf(z.get("world")));
                zoneCircles.add(new double[]{num(z.get("x")), num(z.get("y")), num(z.get("z")), num(z.get("radius"))});
            } catch (RuntimeException ignored) {
                // zone mal formee : ignoree
            }
        }

        bloodMax = c.getInt("blood.max", 1000);
        bloodPerMob = c.getInt("blood.per-hostile-mob", 3);
        bloodPerKill = c.getInt("blood.per-player-kill", 25);
        bloodLossDeathPct = c.getInt("blood.lose-on-death-percent", 10);
    }

    private static double num(Object o) { return o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o)); }
}
