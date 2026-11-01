package fr.chainsawrpg.demon;

/** type : HEALTH, FOOD, EXP, BLOOD, DURABILITY ou NONE. */
public record Cost(String type, double amount) {
    public static final Cost NONE = new Cost("NONE", 0);
}
