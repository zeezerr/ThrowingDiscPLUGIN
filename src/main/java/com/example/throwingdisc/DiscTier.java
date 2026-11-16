package com.example.throwingdisc;

import org.bukkit.Material;

public enum DiscTier {
    //          id                       name         damage durability enchant speed  craft material
    COPPER   ("copper_throwing_disc",    "Copper",    4.0,   190,  13, 1.10, Material.COPPER_INGOT),
    IRON     ("iron_throwing_disc",      "Iron",      5.0,   250,  14, 1.20, Material.IRON_INGOT),
    GOLDEN   ("golden_throwing_disc",    "Golden",    3.5,    30,  22, 1.70, Material.GOLD_INGOT),   // fastest flight
    DIAMOND  ("diamond_throwing_disc",   "Diamond",   6.0,  1560,  10, 1.30, Material.DIAMOND),
    NETHERITE("netherite_throwing_disc", "Netherite", 7.0,  2030,  15, 1.40, null);                  // smithing only

    public static final double RANGE = 16.0;

    public final String id, display;
    public final double damage, speed;
    public final int durability, enchantability;
    public final Material craftMaterial;

    DiscTier(String id, String display, double damage, int durability, int enchantability, double speed, Material craftMaterial) {
        this.id = id; this.display = display; this.damage = damage; this.durability = durability;
        this.enchantability = enchantability; this.speed = speed; this.craftMaterial = craftMaterial;
    }
}
