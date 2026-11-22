package com.example.throwingdisc;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Chest settings GUI. Left-click +, right-click -, shift-click = bigger steps. */
public final class DiscGui implements Listener {
    private final ThrowingDiscPlugin plugin;

    public DiscGui(ThrowingDiscPlugin plugin) { this.plugin = plugin; }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Component.text("Throwing Disc Settings"));
        draw(h.inv);
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!e.getWhoClicked().hasPermission("throwingdisc.config")) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        ClickType c = e.getClick();
        int dir; boolean big;
        switch (c) {
            case LEFT -> { dir = 1; big = false; }
            case RIGHT -> { dir = -1; big = false; }
            case SHIFT_LEFT -> { dir = 1; big = true; }
            case SHIFT_RIGHT -> { dir = -1; big = true; }
            default -> { return; }
        }
        if (handle(e.getSlot(), dir, big)) {
            plugin.saveSettings();
            plugin.registerRecipes();
            draw(top);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private boolean handle(int slot, int dir, boolean big) {
        if (slot == 53) {
            for (DiscTier t : DiscTier.values()) t.resetToDefaults();
            DiscTier.resetGlobals();
            return true;
        }
        if (slot >= 45 && slot <= 49) {
            switch (slot - 45) {
                case 0 -> DiscTier.RANGE = clamp(DiscTier.RANGE + dir * (big ? 10 : 1), 2, 64);
                case 1 -> DiscTier.ARMOR_WEAR = (int) clamp(DiscTier.ARMOR_WEAR + dir * (big ? 5 : 1), 0, 50);
                case 2 -> DiscTier.UNDERWATER = clamp(DiscTier.UNDERWATER + dir * (big ? 0.25 : 0.05), 0.05, 1.0);
                case 3 -> DiscTier.BOUNCE_SPEED = clamp(DiscTier.BOUNCE_SPEED + dir * (big ? 0.25 : 0.05), 1.0, 2.0);
                case 4 -> DiscTier.BOUNCE_RANGE = clamp(DiscTier.BOUNCE_RANGE + dir * (big ? 5 : 1), 0, 30);
                default -> { return false; }
            }
            return true;
        }
        int row = slot / 9, col = slot % 9;
        if (row > 4 || col < 1 || col > 3) return false;
        DiscTier t = DiscTier.values()[row];
        switch (col) {
            case 1 -> t.damage = clamp(t.damage + dir * (big ? 5 : 0.5), 0.5, 100);
            case 2 -> t.speed = clamp(t.speed + dir * (big ? 0.5 : 0.05), 0.1, 4.0);
            case 3 -> t.durability = (int) clamp(t.durability + dir * (big ? 100 : 10), 1, 32000);
            default -> { return false; }
        }
        return true;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.round(Math.max(lo, Math.min(hi, v)) * 100.0) / 100.0;
    }

    private void draw(Inventory inv) {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = 0; i < 54; i++) inv.setItem(i, pane.clone());

        inv.setItem(8, icon(Material.BOOK, "How to use", NamedTextColor.YELLOW,
                "Left-click: increase", "Right-click: decrease", "Shift-click: bigger steps", "Changes are saved automatically."));

        for (DiscTier t : DiscTier.values()) {
            int row = t.ordinal() * 9;
            ItemStack disc = DiscItems.create(t);
            ItemMeta m = disc.getItemMeta();
            m.displayName(Component.text(t.display + " Disc", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            m.lore(null);
            disc.setItemMeta(m);
            inv.setItem(row, disc);
            inv.setItem(row + 1, icon(Material.IRON_SWORD, "Damage: " + fmt(t.damage), NamedTextColor.RED, "Step 0.5 (shift 5)"));
            inv.setItem(row + 2, icon(Material.FEATHER, "Speed: " + fmt(t.speed), NamedTextColor.GREEN, "Blocks per tick", "Step 0.05 (shift 0.5)"));
            inv.setItem(row + 3, icon(Material.ANVIL, "Durability: " + t.durability, NamedTextColor.GOLD, "Step 10 (shift 100)", "Applies to newly created discs"));
        }

        inv.setItem(45, icon(Material.SPYGLASS, "Range: " + fmt(DiscTier.RANGE) + " blocks", NamedTextColor.AQUA, "Distance before it returns", "Step 1 (shift 10)"));
        inv.setItem(46, icon(Material.IRON_CHESTPLATE, "Armor wear: " + DiscTier.ARMOR_WEAR, NamedTextColor.RED, "Extra durability lost per armor piece", "Step 1 (shift 5)"));
        inv.setItem(47, icon(Material.WATER_BUCKET, "Underwater speed: x" + fmt(DiscTier.UNDERWATER), NamedTextColor.BLUE, "Step 0.05 (shift 0.25)"));
        inv.setItem(48, icon(Material.SLIME_BALL, "Bounce speed: x" + fmt(DiscTier.BOUNCE_SPEED), NamedTextColor.GREEN, "Speed gained per ricochet", "Step 0.05 (shift 0.25)"));
        inv.setItem(49, icon(Material.ENDER_PEARL, "Bounce range: +" + fmt(DiscTier.BOUNCE_RANGE), NamedTextColor.LIGHT_PURPLE, "Extra blocks per ricochet", "Step 1 (shift 5)"));
        inv.setItem(53, icon(Material.BARRIER, "Reset everything", NamedTextColor.DARK_RED, "Click to restore defaults"));
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(Math.round(v * 100.0) / 100.0);
    }

    private static ItemStack icon(Material mat, String name, NamedTextColor color, String... lore) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            m.lore(lines);
        }
        s.setItemMeta(m);
        return s;
    }
}
