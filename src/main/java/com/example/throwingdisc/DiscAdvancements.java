package com.example.throwingdisc;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Registers the advancements at runtime (no datapack needed) and awards them. */
public final class DiscAdvancements {
    public static final String ROOT = "root", OLYMPIC = "olympic_champion", DARK_ORBIT = "dark_orbit";

    private DiscAdvancements() {}

    private static NamespacedKey key(String name) { return new NamespacedKey("throwingdisc", name); }

    @SuppressWarnings("deprecation")
    public static void register() {
        load(ROOT, """
            {"display":{"icon":{"id":"minecraft:iron_nugget","components":{"minecraft:item_model":"throwingdisc:iron_throwing_disc"}},
              "title":"Throwing Discs","description":"Get your hands on a throwing disc",
              "background":"minecraft:gui/advancements/backgrounds/adventure",
              "frame":"task","show_toast":false,"announce_to_chat":false},
             "criteria":{"hit":{"trigger":"minecraft:impossible"}},"requirements":[["hit"]]}""");
        load(OLYMPIC, challenge("golden_throwing_disc", "Olympic Champion",
                "Kill 5 different mobs with a single throw of a disc"));
        load(DARK_ORBIT, challenge("netherite_throwing_disc", "Spin to Win", "Obtain a Netherite Throwing Disc"));
    }

    private static String challenge(String icon, String title, String desc) {
        return """
            {"parent":"throwingdisc:root",
             "display":{"icon":{"id":"minecraft:iron_nugget","components":{"minecraft:item_model":"throwingdisc:%s"}},
               "title":"%s","description":"%s","frame":"challenge","show_toast":true,"announce_to_chat":true,"hidden":false},
             "criteria":{"hit":{"trigger":"minecraft:impossible"}},"requirements":[["hit"]],
             "rewards":{"experience":100}}""".formatted(icon, title, desc);
    }

    @SuppressWarnings("deprecation")
    private static void load(String name, String json) {
        if (Bukkit.getAdvancement(key(name)) != null) return;
        Bukkit.getUnsafe().loadAdvancement(key(name), json);
    }

    public static void award(Player p, String name) {
        Advancement adv = Bukkit.getAdvancement(key(name));
        if (adv == null) return;
        AdvancementProgress progress = p.getAdvancementProgress(adv);
        if (!progress.isDone()) progress.awardCriteria("hit");
    }

    /** Called every second: gives "Throwing Discs" / "Spin to Win" when the items are in the inventory. */
    public static void scan(Player p) {
        boolean anyDisc = false, netherite = false;
        for (ItemStack s : p.getInventory().getContents()) {
            DiscTier t = DiscItems.tierOf(s);
            if (t == null) continue;
            anyDisc = true;
            if (t == DiscTier.NETHERITE) netherite = true;
        }
        if (anyDisc) award(p, ROOT);
        if (netherite) { award(p, ROOT); award(p, DARK_ORBIT); }
    }
}
