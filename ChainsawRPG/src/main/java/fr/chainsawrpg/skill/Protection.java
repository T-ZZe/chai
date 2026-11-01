package fr.chainsawrpg.skill;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.Settings;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataType;

/** Zones interdites, protection du spawn et validité des cibles. */
public final class Protection {
    private final ChainsawPlugin pl;

    public Protection(ChainsawPlugin pl) { this.pl = pl; }

    public boolean inSpawn(Location l) {
        Settings s = pl.settings;
        if (!s.spawnProtect || l == null || l.getWorld() == null) return false;
        if (!l.getWorld().equals(pl.getServer().getWorlds().get(0))) return false;
        Location sp = l.getWorld().getSpawnLocation();
        double dx = sp.getX() - l.getX(), dz = sp.getZ() - l.getZ();
        return dx * dx + dz * dz <= s.spawnRadius * s.spawnRadius;
    }

    public boolean inForbiddenZone(Location l) {
        Settings s = pl.settings;
        if (l == null || l.getWorld() == null) return false;
        if (s.protectedWorlds.contains(l.getWorld().getName())) return true;
        for (int i = 0; i < s.zoneCircles.size(); i++) {
            if (!s.zoneWorlds.get(i).equals(l.getWorld().getName())) continue;
            double[] z = s.zoneCircles.get(i);
            double dx = l.getX() - z[0], dy = l.getY() - z[1], dz = l.getZ() - z[2];
            if (dx * dx + dy * dy + dz * dz <= z[3] * z[3]) return true;
        }
        return false;
    }

    public boolean canCast(Player p) {
        if (p.hasPermission("chainsawman.bypass.protection")) return true;
        if (inForbiddenZone(p.getLocation())) return false;
        return !(pl.settings.spawnProtect && inSpawn(p.getLocation()));
    }

    /** Une cible est valide si le lanceur peut légitimement lui infliger des effets. */
    public boolean valid(Player caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e.equals(caster) || le.isDead() || !le.isValid()) return false;
        if (e instanceof ArmorStand) return false;
        if (e.getPersistentDataContainer().has(pl.kNpc, PersistentDataType.STRING)) return false;
        String owner = e.getPersistentDataContainer().get(pl.kOwner, PersistentDataType.STRING);
        if (owner != null && owner.equals(caster.getUniqueId().toString())) return false;
        if (e instanceof Player tp) {
            GameMode gm = tp.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return false;
            if (inSpawn(tp.getLocation()) && !caster.hasPermission("chainsawman.bypass.protection")) return false;
        } else {
            if (e instanceof Tameable t && t.getOwner() != null && t.getOwner().getUniqueId().equals(caster.getUniqueId())) return false;
            if (pl.settings.excludedTypes.contains(e.getType().name())) return false;
            boolean hostile = e instanceof Enemy;
            if (!hostile && !pl.settings.targetPassive) {
                boolean targetsCaster = e instanceof Mob m && m.getTarget() != null && m.getTarget().equals(caster);
                if (!targetsCaster) return false;
            }
        }
        return true;
    }
}
