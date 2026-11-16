package com.example.throwingdisc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.block.data.type.Vault;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

/** Diamond discs in (ominous) vaults, Netherite discs in bastions. */
public final class LootListener implements Listener {

    @EventHandler
    public void onVault(BlockDispenseLootEvent e) {
        if (e.getBlock().getType() != Material.VAULT) return;
        boolean ominous = e.getBlock().getBlockData() instanceof Vault v && v.isOminous();
        if (roll(ominous ? 0.25 : 0.10)) {
            List<ItemStack> loot = new ArrayList<>(e.getDispensedLoot());
            loot.add(DiscItems.create(DiscTier.DIAMOND));
            e.setDispensedLoot(loot);
        }
    }

    @EventHandler
    public void onChest(LootGenerateEvent e) {
        String key = e.getLootTable().getKey().toString();
        double chance = switch (key) {
            case "minecraft:chests/bastion_other" -> 0.04;      // rare
            case "minecraft:chests/bastion_treasure" -> 0.30;   // much higher in the treasure room
            default -> 0;
        };
        if (chance > 0 && roll(chance)) e.getLoot().add(DiscItems.create(DiscTier.NETHERITE));
    }

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }
}
