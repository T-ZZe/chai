package fr.chainsawrpg.skill;

import fr.chainsawrpg.ChainsawPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

/** Blocs temporaires (cages). Seuls l'air et les blocs remplaçables sont remplacés ; tout est restauré, y compris à l'arrêt. */
public final class TempBlocks {
    private final ChainsawPlugin pl;
    private final Map<Location, BlockData> originals = new HashMap<>();
    private final Map<Location, Material> placed = new HashMap<>();

    public TempBlocks(ChainsawPlugin pl) { this.pl = pl; }

    public boolean set(Location loc, Material m, long ticks) {
        Block b = loc.getBlock();
        Location key = b.getLocation();
        if (originals.containsKey(key)) return false;
        if (!(b.getType().isAir() || b.isReplaceable()) || b.getState() instanceof org.bukkit.block.TileState) return false;
        if (pl.protection.inForbiddenZone(key)) return false;
        originals.put(key, b.getBlockData());
        placed.put(key, m);
        b.setType(m, false);
        new BukkitRunnable() {
            @Override public void run() { restore(key); }
        }.runTaskLater(pl, Math.max(1, ticks));
        return true;
    }

    public boolean isTemp(Block b) { return originals.containsKey(b.getLocation()); }

    private void restore(Location key) {
        BlockData orig = originals.remove(key);
        Material m = placed.remove(key);
        if (orig == null) return;
        Block b = key.getBlock();
        if (b.getType() == m) b.setBlockData(orig, false);
    }

    public void restoreAll() {
        for (Location l : new java.util.ArrayList<>(originals.keySet())) restore(l);
    }
}
