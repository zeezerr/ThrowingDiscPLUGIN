package com.example.throwingdisc;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class DiscItems {
    /** Base item. Players without the resource pack just see an iron nugget. */
    public static final Material BASE = Material.IRON_NUGGET;
    public static NamespacedKey TIER_KEY;

    private DiscItems() {}

    public static ItemStack create(DiscTier tier) {
        ItemStack stack = new ItemStack(BASE);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("throwingdisc", tier.id)); // texture from the resource pack
        meta.itemName(Component.text(tier.display + " Throwing Disc"));
        meta.setMaxStackSize(1);
        meta.setEnchantable(tier.enchantability);
        if (meta instanceof Damageable d) d.setMaxDamage(tier.durability);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("When thrown:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" " + (tier.damage % 1 == 0 ? String.valueOf((int) tier.damage) : String.valueOf(tier.damage))
                + " Attack Damage", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(TIER_KEY, PersistentDataType.STRING, tier.name());
        stack.setItemMeta(meta);
        return stack;
    }

    /** @return the tier of a disc item, or null if it is not a throwing disc. */
    public static DiscTier tierOf(ItemStack stack) {
        if (stack == null || stack.getType() != BASE || !stack.hasItemMeta()) return null;
        String s = stack.getItemMeta().getPersistentDataContainer().get(TIER_KEY, PersistentDataType.STRING);
        if (s == null) return null;
        try { return DiscTier.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }
}
