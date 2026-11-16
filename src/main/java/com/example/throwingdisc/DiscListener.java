package com.example.throwingdisc;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class DiscListener implements Listener {
    private final ThrowingDiscPlugin plugin;

    public DiscListener(ThrowingDiscPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();

        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            if (a == Action.RIGHT_CLICK_BLOCK && !p.isSneaking() && e.getClickedBlock() != null
                    && e.getClickedBlock().getType().isInteractable()) return; // let chests/doors work

            EquipmentSlot hand = e.getHand();
            if (hand == EquipmentSlot.HAND && DiscItems.tierOf(main) != null) {
                e.setCancelled(true);
                plugin.throwDisc(p, false);
            } else if (hand == EquipmentSlot.HAND && p.isSneaking() && isTool(main) && DiscItems.tierOf(off) != null) {
                // Crouch + use with a tool in hand -> throw the off-hand disc
                e.setCancelled(true);
                plugin.throwDisc(p, true);
            } else if (hand == EquipmentSlot.OFF_HAND && DiscItems.tierOf(off) != null && main.getType().isAir()) {
                e.setCancelled(true);
                plugin.throwDisc(p, true);
            }
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            // Crouch + attack (any main hand) -> throw the off-hand disc
            if (p.isSneaking() && DiscItems.tierOf(off) != null) {
                e.setCancelled(true);
                plugin.throwDisc(p, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (plugin.discDamage) return;
        if (e.getDamager() instanceof Player p && p.isSneaking() && DiscItems.tierOf(p.getInventory().getItemInOffHand()) != null) {
            e.setCancelled(true);
            plugin.throwDisc(p, true);
        }
    }

    /** Discs are iron nuggets underneath, so stop them being used as crafting ingredients. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack s : e.getInventory().getMatrix()) {
            if (DiscItems.tierOf(s) != null) { e.getInventory().setResult(null); return; }
        }
    }

    /** Only a Diamond disc may be upgraded to Netherite. */
    @EventHandler
    public void onSmithing(PrepareSmithingEvent e) {
        ItemStack result = e.getResult();
        if (result == null || DiscItems.tierOf(result) != DiscTier.NETHERITE) return;
        if (DiscItems.tierOf(e.getInventory().getInputEquipment()) != DiscTier.DIAMOND) e.setResult(null);
    }

    private static boolean isTool(ItemStack s) {
        if (s == null || s.getType().isAir() || DiscItems.tierOf(s) != null) return false;
        return s.getType().getMaxDurability() > 0;
    }
}
