package com.mobenhancer;

import org.bukkit.*;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class EndermanControl implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, LivingEntity> trackedEndermen = new HashMap<>();
    // Rastrea el timestamp del último disarm por enderman
    private final Map<UUID, Long> lastDisarmTime = new HashMap<>();
    // Rastrea qué endermans tienen un warning activo para evitar duplicados
    private final Set<UUID> disarmWarningActive = new HashSet<>();
    private final Random random = new Random();
    private static final double PULL_RANGE = 5.0;
    private static final double PULL_CHANCE = 0.15;
    private static final double DISARM_CHANCE = 0.1;
    private static final double DISARM_RANGE = 8.0;
    private static final int    DISARM_WARNING_SECS = 5;
    private static final long   DISARM_COOLDOWN_MS  = 60_000L;

    public EndermanControl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEndermanTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Enderman enderman)) return;
        if (event.getTarget() == null) return;

        // Excluir endermans que sean bosses del sistema MobEnhancer
        if (enderman.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "mob_enhancer_boss"),
                org.bukkit.persistence.PersistentDataType.BYTE)) return;

        trackedEndermen.put(enderman.getUniqueId(), event.getTarget());
        startAbilityCheck(enderman);
    }

    private void startAbilityCheck(Enderman enderman) {
        new BukkitRunnable() {
            public void run() {
                if (!enderman.isValid()
                        || !trackedEndermen.containsKey(enderman.getUniqueId())
                        || enderman.getTarget() != trackedEndermen.get(enderman.getUniqueId())) {
                    trackedEndermen.remove(enderman.getUniqueId());
                    disarmWarningActive.remove(enderman.getUniqueId());
                    cancel();
                    return;
                }

                LivingEntity target = trackedEndermen.get(enderman.getUniqueId());

                if (shouldPull(enderman, target)) {
                    applyPull(enderman, target);
                }

                // Reemplazar applyDisarm directo por el warning previo
                if (shouldDisarm(enderman, target)) {
                    startDisarmWarning(enderman, target);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean shouldPull(Enderman enderman, LivingEntity target) {
        return enderman.getLocation().distance(target.getLocation()) > PULL_RANGE &&
                random.nextDouble() <= PULL_CHANCE &&
                enderman.getTarget() != null &&
                enderman.getTarget() == target &&
                enderman.hasLineOfSight(target) &&
                isClearPath(enderman.getEyeLocation(), target.getEyeLocation());
    }

    private boolean shouldDisarm(Enderman enderman, LivingEntity target) {
        // No iniciar si ya hay un warning activo para este enderman
        if (disarmWarningActive.contains(enderman.getUniqueId())) return false;

        // Comprobar cooldown
        long lastTime = lastDisarmTime.getOrDefault(enderman.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastTime < DISARM_COOLDOWN_MS) return false;

        return enderman.getLocation().distance(target.getLocation()) <= DISARM_RANGE
                && random.nextDouble() <= DISARM_CHANCE
                && enderman.getTarget() != null
                && enderman.getTarget() == target
                && enderman.hasLineOfSight(target)
                && isClearPath(enderman.getEyeLocation(), target.getEyeLocation())
                && hasItemInHand(target);
    }

    private void startDisarmWarning(Enderman enderman, LivingEntity warnTarget) {
        disarmWarningActive.add(enderman.getUniqueId());

        enderman.getWorld().playSound(enderman.getLocation(),
                Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.8f);

        // Aplicar Nausea al target si es jugador
        if (warnTarget instanceof org.bukkit.entity.Player player) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.NAUSEA,
                    DISARM_WARNING_SECS * 20 + 10,
                    0,
                    false,
                    false,
                    false
            ));
        }

        new BukkitRunnable() {
            int ticks = 0;
            final int warningTicks = DISARM_WARNING_SECS * 20;
            double spiralAngle = 0;

            @Override
            public void run() {
                // Cancelar si el enderman o el target ya no son válidos
                if (!enderman.isValid() || enderman.isDead()
                        || !warnTarget.isValid() || warnTarget.isDead()) {
                    cleanup();
                    cancel();
                    return;
                }

                if (ticks >= warningTicks) {
                    try {
                        // Re-verificar que sigue teniendo item en mano
                        if (hasItemInHand(warnTarget)) {
                            applyDisarm(enderman, warnTarget);
                            // Registrar timestamp del disarm ejecutado
                            lastDisarmTime.put(enderman.getUniqueId(),
                                    System.currentTimeMillis());
                        }
                    } finally {
                        cleanup();
                        cancel();
                    }
                    return;
                }

                // Espiral de 3 brazos en tonos rosa alrededor del target
                Location targetLoc = warnTarget.getLocation().clone().add(0, 1, 0);

                for (int i = 0; i < 3; i++) {
                    double armAngle     = spiralAngle + i * (Math.PI * 2.0 / 3.0);
                    double spiralRadius = 0.8 + Math.sin(ticks * 0.2) * 0.2;
                    double heightOffset = ((ticks % 20) / 20.0) * 2.0 - 1.0;

                    double x = targetLoc.getX() + spiralRadius * Math.cos(armAngle);
                    double z = targetLoc.getZ() + spiralRadius * Math.sin(armAngle);
                    double y = targetLoc.getY() + heightOffset;

                    Color color = (i == 0)
                            ? Color.fromRGB(255, 20,  147) // rosa intenso
                            : (i == 1)
                                ? Color.fromRGB(255, 105, 180) // rosa medio
                                : Color.fromRGB(255, 182, 193); // rosa claro

                    targetLoc.getWorld().spawnParticle(Particle.DUST,
                            new Location(targetLoc.getWorld(), x, y, z),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(color, 1.3f));
                }

                // Sonido tenue cada segundo
                if (ticks % 20 == 0) {
                    warnTarget.getWorld().playSound(warnTarget.getLocation(),
                            Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.5f);
                }

                spiralAngle += 0.25;
                ticks++;
            }

            private void cleanup() {
                disarmWarningActive.remove(enderman.getUniqueId());
                if (warnTarget instanceof org.bukkit.entity.Player player
                        && player.isValid() && !player.isDead()) {
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private boolean hasItemInHand(LivingEntity target) {
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null)
            return false;

        ItemStack mainHand = equipment.getItemInMainHand();
        ItemStack offHand = equipment.getItemInOffHand();

        return !mainHand.getType().isAir() || !offHand.getType().isAir();
    }

    private boolean isClearPath(Location start, Location end) {
        Vector direction = end.toVector().subtract(start.toVector());
        double maxDistance = direction.length();
        direction.normalize();

        for (double d = 0; d < maxDistance; d += 0.5) {
            Location checkPoint = start.clone().add(direction.clone().multiply(d));
            if (checkPoint.getBlock().getType().isSolid())
                return false;
        }
        return true;
    }

    private void applyPull(Enderman enderman, LivingEntity target) {
        Vector pullDirection = enderman.getLocation().toVector()
                .subtract(target.getLocation().toVector())
                .normalize()
                .multiply(1.3)
                .setY(0.8);

        target.setVelocity(pullDirection);
        playPullEffects(enderman, target);
    }

    private void applyDisarm(Enderman enderman, LivingEntity target) {
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null)
            return;

        ItemStack itemToDisarm = null;

        // Prioridad: mano izquierda primero
        ItemStack offHandItem = equipment.getItemInOffHand();
        if (!offHandItem.getType().isAir()) {
            itemToDisarm = offHandItem.clone();
            equipment.setItemInOffHand(new ItemStack(Material.AIR));
        }
        // Si no hay item en mano izquierda, usar mano derecha
        else {
            ItemStack mainHandItem = equipment.getItemInMainHand();
            if (!mainHandItem.getType().isAir()) {
                itemToDisarm = mainHandItem.clone();
                equipment.setItemInMainHand(new ItemStack(Material.AIR));
            }
        }

        // Si se encontró un item para desarmar
        if (itemToDisarm != null) {
            // Soltar el item en el mundo
            Item droppedItem = target.getWorld().dropItem(target.getLocation().add(0, 1, 0), itemToDisarm);
            droppedItem.setPickupDelay(40); // 2 segundos de delay para recoger

            // Calcular dirección del lanzamiento (desde el target hacia el enderman)
            Vector throwDirection = enderman.getLocation().toVector()
                    .subtract(target.getLocation().toVector())
                    .normalize()
                    .multiply(1.1)
                    .setY(0.6);

            droppedItem.setVelocity(throwDirection);
            playDisarmEffects(enderman, target, droppedItem);
        }
    }

    private void playPullEffects(Enderman enderman, LivingEntity target) {
        // Efectos en el objetivo
        target.getWorld().spawnParticle(
                Particle.REVERSE_PORTAL,
                target.getLocation().add(0, 1, 0),
                25,
                0.5, 0.5, 0.5,
                0.1);

        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT,
                1.2f,
                0.7f);

        // Efectos en el Enderman
        enderman.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                enderman.getEyeLocation(),
                10,
                0.3, 0.3, 0.3,
                0.05);
    }

    private void playDisarmEffects(Enderman enderman, LivingEntity target, Item droppedItem) {
        // Efectos en el objetivo (desarme)
        target.getWorld().spawnParticle(
                Particle.REVERSE_PORTAL,
                target.getLocation().add(0, 1, 0),
                15,
                0.3, 0.5, 0.3,
                0.1);

        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_ENDERMAN_SCREAM,
                1.0f,
                1.2f);

        // Efectos en el item lanzado
        new BukkitRunnable() {
            int ticks = 0;

            public void run() {
                if (!droppedItem.isValid() || ticks > 20) { // 1 segundo máximo
                    cancel();
                    return;
                }

                droppedItem.getWorld().spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        droppedItem.getLocation(),
                        3,
                        0.1, 0.1, 0.1,
                        0.02);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L); // Efectos cada 2 ticks

        // Efectos en el Enderman
        enderman.getWorld().spawnParticle(
                Particle.ENCHANT,
                enderman.getEyeLocation(),
                8,
                0.2, 0.2, 0.2,
                0.1);
    }
}