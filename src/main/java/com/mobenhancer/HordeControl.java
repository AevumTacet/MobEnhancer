package com.mobenhancer;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Random;

public class HordeControl implements Listener {
    @SuppressWarnings("unused")
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private static final double CALL_RADIUS = 80.0;
    private static final double CALL_CHANCE = 0.25;
    private static final int SPEED_DURATION = 120; // 6 segundos (20 ticks/segundo)
    private static final int SPEED_AMPLIFIER = 2; // 30% más de velocidad (Nivel 1)

    public HordeControl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        // Solo aplica a zombies
        if (!(event.getEntity() instanceof Zombie))
            return;

        if (!(event.getTarget() instanceof Player))
            return;

        Zombie caller = (Zombie) event.getEntity();
        Player targetPlayer = (Player) event.getTarget();

        if (random.nextDouble() > CALL_CHANCE)
            return;

        playCallSound(caller);
        alertNearbyMobs(caller, targetPlayer);
    }

    private void playCallSound(Mob caller) {
        caller.getWorld().playSound(
                caller.getLocation(),
                Sound.ENTITY_BLAZE_DEATH,
                2.0f,
                0.3f);
    }

    private void alertNearbyMobs(Zombie caller, Player target) {
        NamespacedKey bossKey = new NamespacedKey(plugin, "boss_type");
        caller.getWorld().getNearbyEntities(caller.getLocation(), CALL_RADIUS, CALL_RADIUS / 2, CALL_RADIUS)
                .stream()
                .filter(e -> e instanceof Zombie)
                .map(e -> (Zombie) e)
                // Excluir zombies que sean bosses (tengan el tag "boss_type")
                .filter(zombie -> !zombie.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING))
                .forEach(zombie -> {
                    if (zombie.getTarget() == null ||
                            (zombie.getTarget() instanceof Player &&
                                    !zombie.getTarget().getUniqueId().equals(target.getUniqueId()))) {

                        zombie.setTarget(target);
                        // Aplicar efecto de velocidad
                        zombie.addPotionEffect(new PotionEffect(
                                PotionEffectType.SPEED,
                                SPEED_DURATION,
                                SPEED_AMPLIFIER,
                                false,
                                false));
                    }
                });
    }
}