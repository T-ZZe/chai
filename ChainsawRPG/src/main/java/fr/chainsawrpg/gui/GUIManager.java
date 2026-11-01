package fr.chainsawrpg.gui;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.Settings;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.demon.DemonDef;
import fr.chainsawrpg.demon.SkillDef;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Tous les menus du plugin. Chaque clic dans un menu est annulé ; seules les actions déclarées s'exécutent. */
public final class GUIManager implements Listener {
    public static final class Menu implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

        @Override public Inventory getInventory() { return inv; }
    }

    private final ChainsawPlugin pl;

    public GUIManager(ChainsawPlugin pl) { this.pl = pl; }

    // ---------------- outils ----------------
    private Menu menu(String title, int rows) {
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, rows * 9, pl.msg.component(title));
        return m;
    }

    private void set(Menu m, int slot, ItemStack it, Consumer<InventoryClickEvent> a) {
        m.inv.setItem(slot, it);
        if (a != null) m.actions.put(slot, a);
    }

    private void fill(Menu m) {
        ItemStack f = pl.items.icon(Material.BLACK_STAINED_GLASS_PANE, "<gray> ", null);
        for (int i = 0; i < m.inv.getSize(); i++) if (m.inv.getItem(i) == null) m.inv.setItem(i, f);
    }

    private ItemStack icon(Material mat, String name, List<String> lore) { return pl.items.icon(mat, name, lore); }

    private static List<String> wrap(String text, String prefix, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() + w.length() + 1 > width && line.length() > 0) {
                out.add(prefix + line);
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(prefix + line);
        return out;
    }

    private static String fmt(double v) { return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v); }

    private static String slotLabel(SkillDef s) { return s.isUltimate() ? "ULT" : String.valueOf(s.slot); }

    private void open(Player p, Menu m) { p.openInventory(m.inv); }

    private ItemStack back(Menu m, int slot, Runnable r) {
        ItemStack it = icon(Material.ARROW, "<yellow>Retour", null);
        m.actions.put(slot, e -> r.run());
        return it;
    }

    // ---------------- menu principal ----------------
    public void openMain(Player p) {
        PlayerData pd = pl.players.ensure(p);
        if (pd == null) return;
        Menu m = menu("<dark_red><bold>Chainsaw RPG", 3);
        DemonDef d = pl.demons.get(pd.activeDemon);
        if (d != null) {
            PlayerData.Contract c = pd.active();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Rareté : " + d.rarity.tag());
            lore.add("<gray>Niveau : <white>" + c.level + "<gray>/" + pl.settings.maxLevel);
            lore.add("<gray>XP : <white>" + c.xp + "<gray>/" + (c.level >= pl.settings.maxLevel ? "MAX" : pl.contracts.xpToNext(c.level)));
            lore.add("<gray>Sang : <dark_red>" + pd.blood + "<gray>/" + pl.settings.bloodMax);
            set(m, 10, icon(d.icon, d.rarity.color() + d.name, lore), null);
        } else {
            set(m, 10, icon(Material.BARRIER, "<red>Aucun contrat", List.of("<gray>Rends-toi auprès du", "<gray>Marchand de contrats avec", "<gray>" + pl.settings.headsRequired + " têtes de joueurs.")), null);
        }
        set(m, 11, icon(Material.NETHER_STAR, "<gold>Mes compétences", List.of("<gray>Détails, coûts et cooldowns")), e -> openSkills(p));
        set(m, 12, icon(Material.BOOK, "<aqua>Liste des démons", List.of("<gray>Tous les démons disponibles")), e -> openContracts(p, 0, true));
        set(m, 13, icon(Material.SKELETON_SKULL, "<red>Mes têtes : <white>" + pl.heads.count(p), List.of("<gray>Têtes de joueurs valides", "<gray>dans ton inventaire.")), null);
        set(m, 14, icon(Material.PAPER, "<green>Statistiques", List.of("<gray>Kills, contrats, temps de jeu")), e -> openStats(p));
        if (p.hasPermission("chainsawman.admin")) set(m, 16, icon(Material.COMMAND_BLOCK, "<light_purple>Administration", null), e -> openAdmin(p));
        set(m, 22, icon(Material.BARRIER, "<red>Fermer", null), e -> p.closeInventory());
        fill(m);
        open(p, m);
    }

    // ---------------- contrats / liste des démons ----------------
    private static final int[] INTERIOR = new int[28];

    static {
        int i = 0;
        for (int row = 1; row <= 4; row++) for (int col = 1; col <= 7; col++) INTERIOR[i++] = row * 9 + col;
    }

    private List<String> demonLore(Player p, DemonDef d, boolean shop) {
        List<String> l = new ArrayList<>(wrap(d.description, "<gray>", 36));
        l.add(" ");
        l.add("<gray>Rareté : " + d.rarity.tag());
        SkillDef pas = d.passive();
        if (pas != null) {
            l.add("<gray>Passif : <white>" + pas.name);
            l.addAll(wrap(pas.description, "<dark_gray> ", 36));
        }
        l.add(" ");
        l.add("<gold>Compétences :");
        for (SkillDef s : d.activeSkills()) {
            l.add(" <yellow>" + slotLabel(s) + " <white>" + s.name + " <dark_gray>(" + fmt(s.cooldown) + "s, " + pl.sacrifices.describe(s.cost) + "<dark_gray>)");
        }
        if (shop) {
            l.add(" ");
            l.add("<gray>Prix : <white>" + pl.settings.headsRequired + " têtes <dark_gray>(tu en as <white>" + pl.heads.count(p) + "<dark_gray>)");
            l.add("<green>▶ Clique pour choisir ce démon");
        }
        return l;
    }

    public void openContracts(Player p, int page, boolean browseOnly) {
        PlayerData pd = pl.players.ensure(p);
        if (pd == null) return;
        pl.heads.purgeInvalid(p);
        Menu m = menu(browseOnly ? "<dark_aqua>Démons disponibles" : "<dark_red>Marchand de contrats", 6);
        List<DemonDef> all = new ArrayList<>();
        for (DemonDef d : pl.demons.all()) if (!d.secret) all.add(d);
        int pages = Math.max(1, (all.size() + INTERIOR.length - 1) / INTERIOR.length);
        int pg = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < INTERIOR.length; i++) {
            int idx = pg * INTERIOR.length + i;
            if (idx >= all.size()) break;
            DemonDef d = all.get(idx);
            if (!d.enabled) {
                set(m, INTERIOR[i], icon(Material.GRAY_DYE, "<dark_gray>" + d.name, List.of("<red>Indisponible", "<gray>" + d.disabledReason)), null);
                continue;
            }
            boolean owned = d.id.equals(pd.activeDemon);
            List<String> lore = demonLore(p, d, !browseOnly && !owned);
            if (owned) lore.add("<green>✔ Contrat actuel");
            set(m, INTERIOR[i], icon(d.icon, d.rarity.color() + d.name, lore), browseOnly || owned ? null : e -> {
                if (pl.settings.confirmPurchase) openConfirm(p, d);
                else {
                    p.closeInventory();
                    pl.contracts.confirm(p, d);
                }
            });
        }
        if (pg > 0) set(m, 45, icon(Material.ARROW, "<yellow>Page précédente", null), e -> openContracts(p, pg - 1, browseOnly));
        if (pg < pages - 1) set(m, 53, icon(Material.ARROW, "<yellow>Page suivante", null), e -> openContracts(p, pg + 1, browseOnly));
        set(m, 49, icon(Material.BARRIER, browseOnly ? "<yellow>Retour" : "<red>Fermer", null), e -> {
            if (browseOnly) openMain(p);
            else p.closeInventory();
        });
        fill(m);
        open(p, m);
    }

    public void openConfirm(Player p, DemonDef d) {
        PlayerData pd = pl.players.ensure(p);
        if (pd == null) return;
        Settings s = pl.settings;
        boolean change = pd.activeDemon != null && !pd.activeDemon.equals(d.id);
        Menu m = menu("<dark_red>Confirmer le contrat", 3);
        List<String> lore = demonLore(p, d, false);
        lore.add(" ");
        if (change) {
            lore.add("<red>Rituel de changement :");
            lore.add("<gray>• <white>" + (s.headsRequired + s.changeExtraHeads) + " têtes");
            lore.add("<gray>• <white>" + s.changeExtraLevels + " niveaux d'expérience");
            lore.add("<gray>• Rester immobile <white>" + s.ritualSeconds + "s");
            lore.add("<gray>Ton niveau sur l'ancien contrat est conservé.");
        } else {
            lore.add("<red>Coût : <white>" + s.headsRequired + " têtes <gray>(consommées)");
        }
        set(m, 13, icon(d.icon, d.rarity.color() + d.name, lore), null);
        set(m, 11, icon(Material.LIME_CONCRETE, "<green><bold>Confirmer", List.of("<gray>Signer le contrat")), e -> {
            p.closeInventory();
            pl.contracts.confirm(p, d);
        });
        set(m, 15, icon(Material.RED_CONCRETE, "<red><bold>Annuler", null), e -> openContracts(p, 0, false));
        fill(m);
        open(p, m);
    }

    // ---------------- compétences ----------------
    public void openSkills(Player p) {
        PlayerData pd = pl.players.ensure(p);
        DemonDef d = pd == null ? null : pl.demons.get(pd.activeDemon);
        if (d == null) {
            pl.msg.send(p, "no-contract");
            return;
        }
        PlayerData.Contract c = pd.active();
        Menu m = menu("<gold>Compétences : " + d.rarity.color() + d.name, 3);
        int[] slots = {10, 11, 12, 14};
        for (int i = 1; i <= 4; i++) {
            SkillDef s = d.skill(i);
            if (s == null) continue;
            set(m, slots[i - 1], icon(s.isUltimate() ? Material.NETHER_STAR : Material.AMETHYST_SHARD,
                    (s.isUltimate() ? "<light_purple>" : "<yellow>") + slotLabel(s) + " <white>" + s.name, skillLore(p, s, c.level, true)), null);
        }
        SkillDef pas = d.passive();
        if (pas != null) set(m, 16, icon(Material.HEART_OF_THE_SEA, "<aqua>Passif : <white>" + pas.name, wrap(pas.description, "<gray>", 36)), null);
        set(m, 22, back(m, 22, () -> openMain(p)), null);
        fill(m);
        open(p, m);
    }

    private List<String> skillLore(Player p, SkillDef s, int level, boolean live) {
        double pvp = s.dmgPvp * (1 + Math.max(0, level - 1) * pl.settings.dmgPerLevelPvp);
        double pve = s.dmgPve * (1 + Math.max(0, level - 1) * pl.settings.dmgPerLevelPve);
        List<String> l = new ArrayList<>(wrap(s.description, "<gray>", 36));
        l.add(" ");
        if (pvp > 0 || pve > 0) l.add("<gray>Dégâts : <red>" + fmt(pvp) + " PvP <dark_gray>/ <green>" + fmt(pve) + " PvE");
        l.add("<gray>Cooldown : <white>" + fmt(s.cooldown) + "s");
        l.add("<gray>Portée : <white>" + fmt(s.range) + " <gray>Zone : <white>" + fmt(s.radius) + " <gray>Durée : <white>" + fmt(s.duration) + "s");
        l.add("<gray>Cibles max : <white>" + s.maxTargets);
        l.add("<gray>Coût : " + pl.sacrifices.describe(s.cost));
        if (live) {
            long rem = pl.cooldowns.remainingMs(p.getUniqueId(), s.id);
            l.add(rem > 0 ? "<red>Recharge : " + fmt(rem / 1000.0) + "s" : "<green>Prête");
            l.add("<dark_gray>Lancer : Shift + touche " + (s.slot) + " <dark_gray>(ou Livre)");
        }
        return l;
    }

    // ---------------- statistiques ----------------
    public void openStats(Player p) {
        PlayerData pd = pl.players.ensure(p);
        if (pd == null) return;
        Menu m = menu("<green>Statistiques", 3);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Mobs hostiles tués : <white>" + pd.mobKills);
        lore.add("<gray>Démons éliminés : <white>" + pd.demonKills);
        lore.add("<gray>Victoires PvP : <white>" + pd.pvpWins);
        lore.add("<gray>Boss vaincus : <white>" + pd.bossKills);
        lore.add("<gray>Compétences utilisées : <white>" + pd.skillsUsed);
        lore.add("<gray>Temps de jeu : <white>" + (pd.totalPlaytimeMs() / 3_600_000L) + " h " + ((pd.totalPlaytimeMs() / 60_000L) % 60) + " min");
        lore.add("<gray>Sang : <dark_red>" + pd.blood + "<gray>/" + pl.settings.bloodMax);
        set(m, 11, icon(Material.PAPER, "<green>Tes statistiques", lore), null);
        List<String> cl = new ArrayList<>();
        for (PlayerData.Contract c : pd.contracts.values()) {
            DemonDef d = pl.demons.get(c.demonId);
            String name = d == null ? c.demonId : d.rarity.color() + d.name;
            cl.add((c.demonId.equals(pd.activeDemon) ? "<green>▶ " : "<gray>• ") + name + " <gray>Niv. <white>" + c.level);
        }
        if (cl.isEmpty()) cl.add("<gray>Aucun contrat signé.");
        set(m, 15, icon(Material.ENCHANTED_BOOK, "<gold>Tes contrats", cl), null);
        set(m, 22, back(m, 22, () -> openMain(p)), null);
        fill(m);
        open(p, m);
    }

    // ---------------- admin ----------------
    public void openAdmin(Player p) {
        if (!p.hasPermission("chainsawman.admin")) {
            pl.msg.send(p, "no-permission");
            return;
        }
        Menu m = menu("<light_purple>Administration", 3);
        set(m, 10, icon(Material.REDSTONE, "<yellow>Recharger la configuration", null), e -> {
            pl.reloadAll();
            pl.msg.send(p, "reloaded");
        });
        set(m, 11, icon(Material.SKELETON_SKULL, "<red>Se donner " + pl.settings.headsRequired + " têtes", null), e -> {
            pl.heads.give(p, pl.settings.headsRequired);
            pl.msg.send(p, "heads-given", "amount", pl.settings.headsRequired, "player", p.getName());
        });
        set(m, 12, icon(Material.VILLAGER_SPAWN_EGG, "<aqua>Placer un Marchand ici", null), e -> {
            p.closeInventory();
            pl.npcs.spawn(p.getLocation());
            pl.msg.send(p, "npc-spawned");
        });
        set(m, 13, icon(Material.ENDER_EYE, "<gold>Tirage aléatoire (aperçu)", List.of("<gray>Tire un démon selon les raretés", "<gray>(ne donne rien)")), e -> {
            DemonDef d = pl.demons.roll();
            if (d != null) pl.msg.send(p, "roll-result", "demon", pl.msg.plain(d.rarity.color() + d.name), "rarity", pl.msg.plain(d.rarity.tag()));
        });
        set(m, 22, back(m, 22, () -> openMain(p)), null);
        fill(m);
        open(p, m);
    }

    // ---------------- evenements ----------------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        Consumer<InventoryClickEvent> a = m.actions.get(e.getSlot());
        if (a != null) a.accept(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
