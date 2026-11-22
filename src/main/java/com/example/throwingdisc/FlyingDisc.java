package com.example.throwingdisc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One disc in flight: an ItemDisplay moved/rotated every tick. */
public final class FlyingDisc extends BukkitRunnable {
    private enum Phase { OUT, SLOWING, RETURNING }

    private final ThrowingDiscPlugin plugin;
    private final UUID ownerId;
    private final DiscTier tier;
    private final boolean offhand;
    private final int mainSlot;
    private ItemStack stack;

    private final ItemDisplay display;
    private final Set<UUID> alreadyHit = new HashSet<>();
    private final Set<UUID> mobsKilled = new HashSet<>(); // for "Olympic Champion"
    private Location pos;
    private Vector vel;
    private Phase phase = Phase.OUT;
    private double speed, range = DiscTier.RANGE, travelled, returnSpeed;
    private int bounces, age;
    private float spin;
    private boolean finished;

    public FlyingDisc(ThrowingDiscPlugin plugin, Player owner, ItemStack stack, DiscTier tier, boolean offhand) {
        this.plugin = plugin;
        this.ownerId = owner.getUniqueId();
        this.stack = stack;
        this.tier = tier;
        this.offhand = offhand;
        this.mainSlot = owner.getInventory().getHeldItemSlot();
        this.speed = tier.speed;

        Vector look = owner.getEyeLocation().getDirection();
        this.pos = owner.getEyeLocation().add(look.clone().multiply(0.6)).subtract(0, 0.15, 0);
        this.vel = look.clone().multiply(speed);

        ItemStack shown = stack.clone();
        this.display = pos.getWorld().spawn(pos, ItemDisplay.class, d -> {
            d.setItemStack(shown);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setViewRange(1.5f);
        });
    }

    public void start() {
        plugin.flights().add(this);
        runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        try {
            step();
        } catch (Exception ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Throwing disc error", ex);
            finish(Bukkit.getPlayer(ownerId), true);
        }
    }

    private void step() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (finished || !display.isValid()) { finish(owner, true); return; }
        if (++age > 300) { finish(owner, false); return; }

        switch (phase) {
            case OUT -> {
                advance(owner, true);
                if (!finished && travelled >= range) phase = Phase.SLOWING;
            }
            case SLOWING -> {
                vel.multiply(0.6);
                advance(owner, false);
                if (!finished && vel.length() < 0.18) {
                    phase = Phase.RETURNING;
                    alreadyHit.clear();
                    returnSpeed = 0.3;
                }
            }
            case RETURNING -> {
                if (owner == null || owner.isDead() || !owner.getWorld().equals(pos.getWorld())) { finish(owner, false); return; }
                Vector toOwner = owner.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(pos.toVector());
                if (toOwner.length() < 1.3) { finish(owner, false); return; }
                returnSpeed = Math.min(returnSpeed + 0.12, Math.max(speed, 1.0));
                vel = toOwner.clone().normalize().multiply(Math.min(returnSpeed, toOwner.length()));
                advance(owner, false);
            }
        }
        if (!finished) render();
    }

    private void advance(Player owner, boolean collideBlocks) {
        World world = pos.getWorld();
        boolean inWater = pos.getBlock().getType() == Material.WATER;
        Vector step = inWater ? vel.clone().multiply(DiscTier.UNDERWATER) : vel; // half speed underwater
        if (inWater) world.spawnParticle(Particle.BUBBLE, pos, 2, 0.1, 0.1, 0.1, 0);
        double len = step.length();
        if (len < 1.0E-6) return;
        Vector dir = step.clone().normalize();
        Vector from = pos.toVector();
        Vector end = from.clone().add(step);

        RayTraceResult blockHit = null;
        if (collideBlocks) {
            RayTraceResult r = world.rayTraceBlocks(pos, dir, len, FluidCollisionMode.NEVER, true);
            if (r != null && r.getHitBlock() != null) {
                Block b = r.getHitBlock();
                if (b.getType() == Material.DECORATED_POT) b.breakNaturally(); // smash pots, keep flying
                else blockHit = r;
            }
        }
        if (blockHit != null) end = blockHit.getHitPosition();

        hitEntities(owner, from, end);
        if (finished) return;
        travelled += from.distance(end);

        if (blockHit != null) {
            BlockFace face = blockHit.getHitBlockFace();
            Vector n = face == null ? dir.clone().multiply(-1) : face.getDirection();
            double dot = vel.dot(n);
            vel = vel.clone().subtract(n.clone().multiply(2 * dot));
            speed = Math.min(speed * DiscTier.BOUNCE_SPEED, 3.0);
            range += DiscTier.BOUNCE_RANGE;
            if (++bounces >= 8) range = travelled;
            vel.normalize().multiply(speed);
            pos = end.clone().add(n.clone().multiply(0.15)).toLocation(world);
            world.playSound(pos, Sound.ITEM_TRIDENT_HIT_GROUND, 0.8f, 1.6f);
            world.spawnParticle(Particle.CRIT, pos, 6, 0.1, 0.1, 0.1, 0.1);
        } else {
            pos = end.toLocation(world);
        }
    }

    private void hitEntities(Player owner, Vector from, Vector to) {
        World world = pos.getWorld();
        Vector delta = to.clone().subtract(from);
        double len = delta.length();
        Vector dir = len < 1.0E-6 ? new Vector(0, 0, 1) : delta.clone().normalize();
        BoundingBox sweep = BoundingBox.of(from, to).expand(0.5);

        // Hitting the thrower catches the disc instantly (ignored right after the throw).
        if (owner != null && !owner.isDead() && (travelled > 2.0 || phase != Phase.OUT)) {
            BoundingBox ob = owner.getBoundingBox().expand(0.3);
            if (ob.contains(from) || ob.contains(to) || (len > 1.0E-6 && ob.rayTrace(from, dir, len) != null)) {
                finish(owner, false);
                return;
            }
        }

        for (Entity e : world.getNearbyEntities(sweep, x -> canHit(owner, x))) {
            BoundingBox box = e.getBoundingBox().expand(0.3);
            if (!box.contains(from) && (len < 1.0E-6 || box.rayTrace(from, dir, len) == null)) continue;

            alreadyHit.add(e.getUniqueId());
            LivingEntity target = (LivingEntity) e;
            Vector velBefore = target.getVelocity().clone();
            double dmg = tier.damage + enchantBonus(target);
            target.setNoDamageTicks(0);
            plugin.discDamage = true; // tells DiscListener not to treat this as a melee hit
            try {
                if (owner != null) target.damage(dmg, owner);
                else target.damage(dmg);
            } catch (Exception ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Disc damage failed", ex);
            } finally {
                plugin.discDamage = false;
            }
            target.setVelocity(velBefore); // no knockback
            int fire = stack.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
            if (fire > 0) target.setFireTicks(80 * fire);
            if (target instanceof Player victim) wearArmor(victim);
            world.playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT, 0.8f, 1.2f);
            if (target.isDead() && target instanceof Mob && mobsKilled.add(target.getUniqueId())
                    && mobsKilled.size() >= 5 && owner != null) {
                DiscAdvancements.award(owner, DiscAdvancements.OLYMPIC);
            }

            if (damageDisc()) { // broke
                world.playSound(pos, Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
                stack = null;
                finish(owner, false);
                return;
            }
        }
    }

    /** Extra damage from Sharpness / Smite / Bane of Arthropods on the disc. */
    private double enchantBonus(LivingEntity t) {
        double bonus = 0;
        int sharp = stack.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharp > 0) bonus += 0.5 * sharp + 0.5;
        int smite = stack.getEnchantmentLevel(Enchantment.SMITE);
        if (smite > 0 && (t instanceof org.bukkit.entity.Zombie || t instanceof org.bukkit.entity.AbstractSkeleton
                || t instanceof org.bukkit.entity.Wither || t instanceof org.bukkit.entity.Phantom
                || t instanceof org.bukkit.entity.ZombieHorse || t instanceof org.bukkit.entity.SkeletonHorse
                || t instanceof org.bukkit.entity.Zoglin)) bonus += 2.5 * smite;
        int bane = stack.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
        if (bane > 0 && (t instanceof org.bukkit.entity.Spider || t instanceof org.bukkit.entity.Silverfish
                || t instanceof org.bukkit.entity.Endermite || t instanceof org.bukkit.entity.Bee)) bonus += 2.5 * bane;
        return bonus;
    }

    /** Discs chew through armor: every worn piece loses extra durability. */
    private void wearArmor(Player victim) {
        if (victim.getGameMode() == org.bukkit.GameMode.CREATIVE || victim.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        PlayerInventory inv = victim.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack a = armor[i];
            if (a == null || a.getType().isAir() || !(a.getItemMeta() instanceof Damageable d) || d.isUnbreakable()) continue;
            int max = d.hasMaxDamage() ? d.getMaxDamage() : a.getType().getMaxDurability();
            if (max <= 0) continue;
            int unbreaking = a.getEnchantmentLevel(Enchantment.UNBREAKING);
            int wear = 0;
            for (int n = 0; n < DiscTier.ARMOR_WEAR; n++) {
                if (unbreaking == 0 || ThreadLocalRandom.current().nextDouble() < 0.6 + 0.4 / (unbreaking + 1)) wear++;
            }
            if (wear == 0) continue;
            d.setDamage(d.getDamage() + wear);
            if (d.getDamage() >= max) {
                armor[i] = null;
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
            } else {
                a.setItemMeta(d);
            }
        }
        inv.setArmorContents(armor);
    }

    private boolean canHit(Player owner, Entity e) {
        if (!(e instanceof LivingEntity) || e.isDead() || e.equals(display) || alreadyHit.contains(e.getUniqueId())) return false;
        if (owner != null && e.getUniqueId().equals(owner.getUniqueId())) return false;
        if (e instanceof Player p && p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        return true;
    }

    /** @return true when the disc broke */
    private boolean damageDisc() {
        int unbreaking = stack.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) return false;
        if (stack.getItemMeta() instanceof Damageable d) {
            d.setDamage(d.getDamage() + 1);
            stack.setItemMeta(d);
            return d.getDamage() >= (d.hasMaxDamage() ? d.getMaxDamage() : tier.durability);
        }
        return false;
    }

    private void render() {
        spin += 0.9f;
        display.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf().rotateY(spin).rotateX((float) (Math.PI / 2)),
                new Vector3f(1.3f, 1.3f, 1.3f), new Quaternionf()));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.teleport(pos);
    }

    /** Hands the disc back to the owner (or drops it) and cleans up. */
    public void finish(Player owner, boolean forced) {
        if (finished && !forced) return;
        finished = true;
        cancelQuietly();
        plugin.flights().remove(this);
        if (display.isValid()) display.remove();

        if (stack == null) return;
        Location at = pos;
        if (owner != null && owner.isOnline() && !owner.isDead()) {
            PlayerInventory inv = owner.getInventory();
            boolean placed = false;
            if (offhand) {
                if (inv.getItemInOffHand().getType().isAir()) { inv.setItemInOffHand(stack); placed = true; }
            } else if (inv.getItem(mainSlot) == null || inv.getItem(mainSlot).getType().isAir()) {
                inv.setItem(mainSlot, stack); placed = true;
            }
            if (!placed) inv.addItem(stack).values().forEach(rest -> owner.getWorld().dropItemNaturally(owner.getLocation(), rest));
            owner.playSound(owner.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.6f);
        } else {
            at.getWorld().dropItemNaturally(at, stack);
        }
        stack = null;
    }

    /** Used on plugin disable: hand the disc back to its owner right now. */
    public void forceReturn() {
        finish(Bukkit.getPlayer(ownerId), true);
    }

    private void cancelQuietly() {
        try { cancel(); } catch (IllegalStateException ignored) { }
    }
}
