package com.example.throwingdisc;

import org.bukkit.Material;

/** Stats for every disc variant. Editable in-game with /throwingdisc config. */
public enum DiscTier {
    //          id                       name         damage durability enchant speed  craft material
    COPPER   ("copper_throwing_disc",    "Copper",    5.0,    250,  13, 1.10, Material.COPPER_INGOT),
    IRON     ("iron_throwing_disc",      "Iron",      6.0,    325,  14, 1.20, Material.IRON_INGOT),
    GOLDEN   ("golden_throwing_disc",    "Golden",    4.5,     40,  22, 1.70, Material.GOLD_INGOT),   // fastest flight
    DIAMOND  ("diamond_throwing_disc",   "Diamond",   7.5,   2030,  10, 1.30, Material.DIAMOND),
    NETHERITE("netherite_throwing_disc", "Netherite", 9.0,   2640,  15, 1.40, null);                  // smithing only

    // ----- global settings (editable) -----
    public static double RANGE = 16.0;
    public static int ARMOR_WEAR = 3;
    public static double UNDERWATER = 0.5;
    public static double BOUNCE_SPEED = 1.25;
    public static double BOUNCE_RANGE = 6.0;

    public final String id, display;
    public final int enchantability;
    public final Material craftMaterial;
    public double damage, speed;
    public int durability;          // used for newly created discs
    public final double defDamage, defSpeed;
    public final int defDurability;

    DiscTier(String id, String display, double damage, int durability, int enchantability, double speed, Material craftMaterial) {
        this.id = id; this.display = display; this.enchantability = enchantability; this.craftMaterial = craftMaterial;
        this.damage = this.defDamage = damage;
        this.durability = this.defDurability = durability;
        this.speed = this.defSpeed = speed;
    }

    public void resetToDefaults() { damage = defDamage; durability = defDurability; speed = defSpeed; }

    public static void resetGlobals() {
        RANGE = 16.0; ARMOR_WEAR = 3; UNDERWATER = 0.5; BOUNCE_SPEED = 1.25; BOUNCE_RANGE = 6.0;
    }
}
