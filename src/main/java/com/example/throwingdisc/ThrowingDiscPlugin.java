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
    private final Set<FlyingDisc> flights = new HashSet<>();
    private final Map<UUID, Long> lastThrow = new HashMap<>();

    public Set<FlyingDisc> flights() { return flights; }

    @Override
    public void onEnable() {
        DiscItems.TIER_KEY = new NamespacedKey(this, "tier");
        registerRecipes();
        DiscAdvancements.register();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) DiscAdvancements.scan(p);
        }, 40L, 20L);
        Bukkit.getPluginManager().registerEvents(new DiscListener(this), this);
        Bukkit.getPluginManager().registerEvents(new LootListener(), this);
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

    private void registerRecipes() {
        for (DiscTier tier : DiscTier.values()) {
            if (tier.craftMaterial == null) continue;
            ShapedRecipe r = new ShapedRecipe(new NamespacedKey(this, tier.id), DiscItems.create(tier));
            r.shape(" A ", "A A", " A ");
            r.setIngredient('A', tier.craftMaterial);
            Bukkit.addRecipe(r);
        }
        SmithingTransformRecipe smith = new SmithingTransformRecipe(
                new NamespacedKey(this, "netherite_throwing_disc"),
                DiscItems.create(DiscTier.NETHERITE),
                new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                new RecipeChoice.MaterialChoice(DiscItems.BASE),   // narrowed to Diamond discs in DiscListener
                new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT));
        Bukkit.addRecipe(smith);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("Usage: /" + label + " give <copper|iron|golden|diamond|netherite> [player]");
            return true;
        }
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
        if (args.length == 1) return List.of("give");
        if (args.length == 2) return java.util.Arrays.stream(DiscTier.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList();
        return null;
    }
}
