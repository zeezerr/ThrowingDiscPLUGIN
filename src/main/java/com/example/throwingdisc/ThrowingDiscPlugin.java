package com.example.throwingdisc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class ThrowingDiscPlugin extends JavaPlugin {
    /** true while a disc is dealing damage, so DiscListener ignores it. */
    public volatile boolean discDamage;
    private DiscGui gui;
    private final Set<FlyingDisc> flights = new HashSet<>();
    private final Map<UUID, Long> lastThrow = new HashMap<>();

    public Set<FlyingDisc> flights() { return flights; }

    @Override
    public void onEnable() {
        DiscItems.TIER_KEY = new NamespacedKey(this, "tier");
        loadSettings();
        registerRecipes();
        DiscAdvancements.register();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) DiscAdvancements.scan(p);
        }, 40L, 20L);
        Bukkit.getPluginManager().registerEvents(new DiscListener(this), this);
        Bukkit.getPluginManager().registerEvents(new LootListener(), this);
        gui = new DiscGui(this);
        Bukkit.getPluginManager().registerEvents(gui, this);
    }

    @Override
    public void onDisable() {
        // Give every disc that is still in the air back to its owner.
        for (FlyingDisc f : new ArrayList<>(flights)) f.forceReturn();
        flights.clear();
    }

    /** Throw the disc in the given hand (main or off). */
    public void throwDisc(Player p, boolean offhand) {
        long now = System.currentTimeMillis();
        if (now - lastThrow.getOrDefault(p.getUniqueId(), 0L) < 200) return; // click events can fire twice
        ItemStack held = offhand ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        DiscTier tier = DiscItems.tierOf(held);
        if (tier == null) return;
        lastThrow.put(p.getUniqueId(), now);

        ItemStack copy = held.clone();
        if (offhand) p.getInventory().setItemInOffHand(null);
        else p.getInventory().setItemInMainHand(null);

        new FlyingDisc(this, p, copy, tier, offhand).start();
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.8f, 1.4f);
        p.swingMainHand();
    }

    public void registerRecipes() {
        for (DiscTier tier : DiscTier.values()) {
            if (tier.craftMaterial == null) continue;
            NamespacedKey rk = new NamespacedKey(this, tier.id);
            Bukkit.removeRecipe(rk);
            ShapedRecipe r = new ShapedRecipe(rk, DiscItems.create(tier));
            r.shape(" A ", "A A", " A ");
            r.setIngredient('A', tier.craftMaterial);
            Bukkit.addRecipe(r);
        }
        NamespacedKey sk = new NamespacedKey(this, "netherite_throwing_disc");
        Bukkit.removeRecipe(sk);
        SmithingTransformRecipe smith = new SmithingTransformRecipe(
                sk,
                DiscItems.create(DiscTier.NETHERITE),
                new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                new RecipeChoice.MaterialChoice(DiscItems.BASE),   // narrowed to Diamond discs in DiscListener
                new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT));
        Bukkit.addRecipe(smith);
    }

    public void loadSettings() {
        reloadConfig();
        org.bukkit.configuration.file.FileConfiguration c = getConfig();
        for (DiscTier t : DiscTier.values()) {
            String b = "tiers." + t.name().toLowerCase(Locale.ROOT) + ".";
            t.damage = c.getDouble(b + "damage", t.defDamage);
            t.durability = c.getInt(b + "durability", t.defDurability);
            t.speed = c.getDouble(b + "speed", t.defSpeed);
        }
        DiscTier.RANGE = c.getDouble("range", 16.0);
        DiscTier.ARMOR_WEAR = c.getInt("armor-wear", 3);
        DiscTier.UNDERWATER = c.getDouble("underwater-speed", 0.5);
        DiscTier.BOUNCE_SPEED = c.getDouble("bounce-speed", 1.25);
        DiscTier.BOUNCE_RANGE = c.getDouble("bounce-range", 6.0);
        saveSettings();
    }

    public void saveSettings() {
        org.bukkit.configuration.file.FileConfiguration c = getConfig();
        for (DiscTier t : DiscTier.values()) {
            String b = "tiers." + t.name().toLowerCase(Locale.ROOT) + ".";
            c.set(b + "damage", t.damage);
            c.set(b + "durability", t.durability);
            c.set(b + "speed", t.speed);
        }
        c.set("range", DiscTier.RANGE);
        c.set("armor-wear", DiscTier.ARMOR_WEAR);
        c.set("underwater-speed", DiscTier.UNDERWATER);
        c.set("bounce-speed", DiscTier.BOUNCE_SPEED);
        c.set("bounce-range", DiscTier.BOUNCE_RANGE);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("config")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Only players can open the settings GUI."); return true; }
            if (!p.hasPermission("throwingdisc.config")) { p.sendMessage("You do not have permission."); return true; }
            gui.open(p);
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("Usage: /" + label + " config  |  /" + label + " give <copper|iron|golden|diamond|netherite> [player]");
            return true;
        }
        if (!sender.hasPermission("throwingdisc.give")) { sender.sendMessage("You do not have permission."); return true; }
        DiscTier tier;
        try { tier = DiscTier.valueOf(args[1].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { sender.sendMessage("Unknown tier: " + args[1]); return true; }
        Player target = args.length > 2 ? Bukkit.getPlayer(args[2]) : (sender instanceof Player p ? p : null);
        if (target == null) { sender.sendMessage("Player not found."); return true; }
        target.getInventory().addItem(DiscItems.create(tier));
        sender.sendMessage("Gave " + tier.display + " Throwing Disc to " + target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return List.of("give", "config");
        if (args.length == 2) return java.util.Arrays.stream(DiscTier.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList();
        return null;
    }
}
