package fr.chainsawrpg.demon;

/** color = tag MiniMessage (ex: "&lt;gold&gt;" ou "&lt;#ff2d55&gt;"). */
public record Rarity(String id, String name, String color, double chance, int order) {
    public String tag() { return color + name; }
}
