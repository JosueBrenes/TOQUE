package com.josuebrenes.toquemobspawner.difficulty;

import com.josuebrenes.toquemobspawner.ToqueMobSpawner;
import com.josuebrenes.toquemobspawner.config.SpawnSettings;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Makes every hostile mob tougher, everywhere: harder hits, more health and a
 * higher hostile cap. Vanilla's hardest setting is Hard; this goes past it.
 *
 * <p>The multipliers come from the config file and are read by the mixins on
 * every call, so {@code /toquemobs reload} applies them without a restart. Until
 * the file is loaded they are all 1.0, which is vanilla.
 */
public final class MobDifficulty {
    private static final Identifier HEALTH_MODIFIER =
            Identifier.fromNamespaceAndPath(ToqueMobSpawner.MOD_ID, "hard_health");

    private static volatile double damage = 1.0;
    private static volatile double health = 1.0;
    private static volatile double monsterCap = 1.0;

    private MobDifficulty() {
    }

    public static void update(SpawnSettings settings) {
        damage = settings.mobDamageMultiplier();
        health = settings.mobHealthMultiplier();
        monsterCap = settings.monsterCapMultiplier();
    }

    /** A hit on a player, scaled when a hostile mob is behind it, arrows and blasts included. */
    public static float scaleDamage(Entity attacker, float amount) {
        return attacker instanceof Enemy ? (float) (amount * damage) : amount;
    }

    public static int scaleCap(MobCategory category, int max) {
        return category == MobCategory.MONSTER ? Math.max(1, (int) Math.round(max * monsterCap)) : max;
    }

    /**
     * Gives a hostile mob its extra health when it enters the world. The modifier
     * is saved with the mob, so a mob loaded again keeps the health it had; it is
     * only replaced when the multiplier in the file changes.
     */
    public static void onLoad(Entity entity) {
        if (!(entity instanceof Enemy) || !(entity instanceof LivingEntity living)) {
            return;
        }
        AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        double bonus = health - 1.0;
        AttributeModifier current = maxHealth.getModifier(HEALTH_MODIFIER);
        if (bonus == 0.0) {
            if (current != null) {
                maxHealth.removeModifier(HEALTH_MODIFIER);
            }
            return;
        }
        if (current != null && current.amount() == bonus) {
            return;
        }

        boolean wasFull = living.getHealth() >= living.getMaxHealth();
        maxHealth.addOrReplacePermanentModifier(new AttributeModifier(
                HEALTH_MODIFIER, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        // A fresh mob is at full health and should stay full; a hurt one keeps its wounds.
        if (current == null || wasFull) {
            living.setHealth(living.getMaxHealth());
        }
    }
}
