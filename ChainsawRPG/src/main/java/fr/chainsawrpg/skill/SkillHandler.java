package fr.chainsawrpg.skill;

/** Un type de compétence. Retourne true si la compétence a été lancée (coût et cooldown appliqués ensuite). */
@FunctionalInterface
public interface SkillHandler {
    boolean cast(Cast c);
}
