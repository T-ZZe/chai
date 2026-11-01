package fr.chainsawrpg.item;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.data.DatabaseManager.HeadRecord;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Objets personnalisés. Identifiés par PDC (type) ; les têtes portent en plus un identifiant unique validé en base. */
public final class ItemManager {
    public static final String T_HEAD = "demon_head";
    public static final String T_BOOK = "contract_book";

    private final ChainsawPlugin pl;
    public final NamespacedKey kType, kHeadId;

    public ItemManager(ChainsawPlugin pl) {
        this.pl = pl;
        this.kType = pl.key("item_type");
        this.kHeadId = pl.key("head_id");
    }

    public String typeOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(kType, PersistentDataType.STRING);
    }

    public boolean is(ItemStack it, String type) { return type.equals(typeOf(it)); }

    /** Identifiant de tête porté par l'objet (même invalide/forgé), ou null. */
    public UUID headId(ItemStack it) {
        if (!is(it, T_HEAD)) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(kHeadId, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Vrai pour tout objet du plugin (empêche craft / placement). */
    public boolean isPluginItem(ItemStack it) { return typeOf(it) != null; }

    public ItemStack createHead(HeadRecord r) {
        YamlConfiguration y = pl.cfg.get("items.yml");
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(r.victim()));
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(r.createdAt()));
        meta.displayName(pl.msg.component(y.getString("items.demon_head.name", "<dark_red>Tête de <victim>"), "victim", r.victimName()));
        List<Component> lore = new ArrayList<>();
        for (String l : y.getStringList("items.demon_head.lore")) lore.add(pl.msg.component(l, "victim", r.victimName(), "date", date));
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kType, PersistentDataType.STRING, T_HEAD);
        pdc.set(kHeadId, PersistentDataType.STRING, r.id().toString());
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack book() {
        YamlConfiguration y = pl.cfg.get("items.yml");
        ItemStack it = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(pl.msg.component(y.getString("items.contract_book.name", "<dark_red>Livre des contrats")));
        List<Component> lore = new ArrayList<>();
        for (String l : y.getStringList("items.contract_book.lore")) lore.add(pl.msg.component(l));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(kType, PersistentDataType.STRING, T_BOOK);
        it.setItemMeta(meta);
        return it;
    }

    /** Icône de menu (non conservable : les menus annulent tous les clics). */
    public ItemStack icon(Material m, String nameMm, List<String> loreMm) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(pl.msg.component(nameMm));
        if (loreMm != null) {
            List<Component> l = new ArrayList<>();
            for (String s : loreMm) l.add(pl.msg.component(s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }
}
