package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Hopper implements CustomType {

    private static final String Custom_TEXTURE_HASH = "7383a54661e311f94718ec2d4aa587d224553ee924afbb7d40126c66f600dd60";
    private final Set<UUID> jumpingZombies = new HashSet<>();
    private final Set<UUID> cooldownZombies = new HashSet<>();

    @SuppressWarnings("deprecation")
    private ItemStack createCustomHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "Custom");
        PlayerTextures textures = profile.getTextures();

        try {
            URL skinUrl = new URL("https://textures.minecraft.net/texture/" + Custom_TEXTURE_HASH);
            textures.setSkin(skinUrl);
        } catch (MalformedURLException ex) {
            throw new RuntimeException("URL de textura inválida", ex);
        }

        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);

        return head;
    }

    @Override
    public String getId() {
        return "hopper";
    }

    @Override
    public String getName() {
        return "Leaper";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
        ItemStack CustomHead = createCustomHead();
        zombie.getEquipment().setHelmet(CustomHead);
        zombie.getEquipment().setHelmetDropChance(0);

        incrAttribute(zombie, Attribute.JUMP_STRENGTH, 0.8);
        incrAttribute(zombie, Attribute.FALL_DAMAGE_MULTIPLIER, -1);
        incrAttribute(zombie, Attribute.FOLLOW_RANGE, 8);

        // Iniciar tarea de verificación de objetivos para saltar
        startTargetJumping(zombie);
    }

    private void startTargetJumping(Zombie zombie) {
        new BukkitRunnable() {
            private int jumpCooldown = 0;

            @Override
            public void run() {
                if (!zombie.isValid() || zombie.isDead()) {
                    cancel();
                    return;
                }

                // Reducir cooldown si está activo
                if (jumpCooldown > 0) {
                    jumpCooldown--;
                    return;
                }

                LivingEntity target = zombie.getTarget();

                // Verificar si hay un objetivo válido y condiciones para saltar
                if (target != null &&
                        !jumpingZombies.contains(zombie.getUniqueId()) &&
                        !cooldownZombies.contains(zombie.getUniqueId()) &&
                        zombie.getLocation().distance(target.getLocation()) > 3 &&
                        zombie.getLocation().distance(target.getLocation()) < 12 &&
                        zombie.isOnGround()) {

                    // Aplicar salto dirigido hacia el objetivo
                    jumpTowardsTarget(zombie, target);
                    jumpCooldown = 40; // 2 segundos de cooldown (40 ticks)
                }
            }
        }.runTaskTimer(com.mobenhancer.MobEnhancer.getInstance(), 0L, 1L); // Verificar cada tick
    }

    private void jumpTowardsTarget(Zombie zombie, LivingEntity target) {
        jumpingZombies.add(zombie.getUniqueId());

        // Calcular dirección hacia el objetivo
        Vector direction = target.getLocation().toVector()
                .subtract(zombie.getLocation().toVector())
                .normalize();

        // Ajustar la fuerza del salto
        double distance = zombie.getLocation().distance(target.getLocation());
        double strength = Math.min(1.8, 0.8 + (distance / 10)); // Fuerza ajustada por distancia

        // Aplicar velocidad
        Vector velocity = new Vector(
                direction.getX() * strength,
                0.8, // Fuerza vertical fija
                direction.getZ() * strength);

        zombie.setVelocity(velocity);

        // Efectos de partículas y sonido
        zombie.getWorld().spawnParticle(Particle.CLOUD, zombie.getLocation(), 15, 0.3, 0.3, 0.3, 0.1);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.0f);

        // Programar la remoción del conjunto de saltos
        new BukkitRunnable() {
            @Override
            public void run() {
                jumpingZombies.remove(zombie.getUniqueId());

                // Añadir cooldown después del salto
                cooldownZombies.add(zombie.getUniqueId());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        cooldownZombies.remove(zombie.getUniqueId());
                    }
                }.runTaskLater(com.mobenhancer.MobEnhancer.getInstance(), 60L); // 3 segundos de cooldown
            }
        }.runTaskLater(com.mobenhancer.MobEnhancer.getInstance(), 20L); // Remover después de 1 segundo
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && p.isBlocking())
            return;
        zombie.setVelocity(zombie.getVelocity().multiply(0.25));
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent e) {
        jumpingZombies.remove(zombie.getUniqueId());
        cooldownZombies.remove(zombie.getUniqueId());
        zombie.setVelocity(zombie.getVelocity().setY(3));
    }
}