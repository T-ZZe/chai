package fr.chainsawrpg.util;

import fr.chainsawrpg.ChainsawPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Messages MiniMessage. Placeholders : &lt;nom&gt; fournis par paires (clé, valeur) ; valeurs jamais interprétées. */
public final class Msg {
    private final ChainsawPlugin pl;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration yml;

    public Msg(ChainsawPlugin pl) {
        this.pl = pl;
        reload();
    }

    public void reload() { yml = pl.cfg.get("messages.yml"); }

    public String raw(String key) {
        String s = yml == null ? null : yml.getString(key);
        return s != null ? s : "<red>[" + key + "]";
    }

    private TagResolver res(Object... kv) {
        List<TagResolver> l = new ArrayList<>();
        for (int i = 0; i + 1 < kv.length; i += 2) l.add(Placeholder.unparsed(String.valueOf(kv[i]), String.valueOf(kv[i + 1])));
        return TagResolver.resolver(l);
    }

    public Component component(String text, Object... kv) {
        return mm.deserialize(text, res(kv)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component get(String key, Object... kv) { return component(raw(key), kv); }

    public String plain(String text, Object... kv) {
        return PlainTextComponentSerializer.plainText().serialize(component(text, kv));
    }

    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(component((yml == null ? "" : yml.getString("prefix", "")) + raw(key), kv));
    }

    /** Message court sur l'action bar ; masque brièvement le HUD pour qu'il reste lisible. */
    public void feedback(Player p, String key, Object... kv) {
        p.sendActionBar(get(key, kv));
        pl.hudMute(p.getUniqueId(), 1500);
    }
}
