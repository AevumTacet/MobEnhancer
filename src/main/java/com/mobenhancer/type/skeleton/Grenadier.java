package com.mobenhancer.type.skeleton;

import com.mobenhancer.SkeletonCustomType;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

public class Grenadier implements SkeletonCustomType {

    private static final double MORTAR_CHANCE = 0.20; // 20% de probabilidad de disparo mortero
    private static final double MIN_DISTANCE = 8.0; // Distancia mínima para mortero
    private static final double MAX_DISTANCE = 12.0; // Distancia máxima para mortero
    private static final float EXPLOSION_POWER = 2.0f;
    private static final double KNOCKBACK_STRENGTH = 1.5;
    private static final double BASE_VELOCITY = 0.6; // 60% de velocidad normal

    private final Random random = new Random();

    @Override
    public String getId() {
        return "grenadier";
    }

    @Override
    public String getName() {
        return "Grenadier";
    }

    @Override
    public void onShootBow(Skeleton skeleton, EntityShootBowEvent e) {
        LivingEntity target = skeleton.getTarget();

        if (target != null && target.isValid()) {
            double distance = skeleton.getLocation().distance(target.getLocation());

            // Verificar condiciones para disparo mortero
            if (distance >= MIN_DISTANCE && distance <= MAX_DISTANCE &&
                    random.nextDouble() < MORTAR_CHANCE) {

                Arrow arrow = (Arrow) e.getProjectile();

                arrow.getWorld().playSound(arrow.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.7f, 1.2f);

                // Marcar la flecha como granada mortero
                NamespacedKey grenadeKey = new NamespacedKey(
                        org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class),
                        "mortar_arrow");
                arrow.getPersistentDataContainer().set(grenadeKey, PersistentDataType.BYTE, (byte) 1);

                // Configurar flecha con trayectoria mortero
                configureMortarShot(skeleton, arrow, target, distance);
            }
        }
    }

    private void configureMortarShot(Skeleton shooter, Arrow arrow, LivingEntity target, double distance) {
        // Cancelar velocidad original
        arrow.setVelocity(new Vector(0, 0, 0));

        // Calcular ángulo basado en la distancia (entre 55° y 85°)
        double angle = calculateOptimalAngle(distance);

        // Aplicar trayectoria mortero
        launchMortarTrajectory(shooter, arrow, target, angle);

        // Efectos visuales y sonoros
        arrow.setGlowing(true);
        arrow.setColor(Color.RED);
        arrow.getWorld().playSound(arrow.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);

        // Iniciar efectos de vuelo
        startMortarParticles(arrow);
    }

    private double calculateOptimalAngle(double distance) {
        // Ángulo base de 70° ajustado por distancia
        // Distancias cortas: ángulo más alto (hasta 85°)
        // Distancias largas: ángulo más bajo (hasta 55°)
        double normalizedDistance = (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE);
        double baseAngle = 70.0; // Ángulo base
        double angleRange = 15.0; // Rango de ajuste ±15°

        // A mayor distancia, menor ángulo (dentro del rango 55°-85°)
        return baseAngle + angleRange * (0.5 - normalizedDistance);
    }

    private void launchMortarTrajectory(Skeleton shooter, Arrow arrow, LivingEntity target, double angle) {
        Location shooterLoc = shooter.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, 1, 0); // Apuntar al cuerpo

        // Calcular dirección horizontal hacia el target
        Vector toTarget = targetLoc.toVector().subtract(shooterLoc.toVector());
        Vector horizontalDir = new Vector(toTarget.getX(), 0, toTarget.getZ()).normalize();

        // Convertir ángulo a radianes
        double angleRad = Math.toRadians(angle);

        // Calcular componentes de velocidad
        double horizontalSpeed = BASE_VELOCITY * Math.cos(angleRad);
        double verticalSpeed = BASE_VELOCITY * Math.sin(angleRad);

        // Aplicar velocidad final
        Vector finalVelocity = horizontalDir.multiply(horizontalSpeed).setY(verticalSpeed);
        arrow.setVelocity(finalVelocity);

        // Sistema de corrección de trayectoria para mayor precisión
        startMortarGuidance(arrow, target, shooterLoc);
    }

    private void startMortarGuidance(Arrow arrow, LivingEntity target, Location startLocation) {
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 80; // Máximo 4 segundos de guía

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isOnGround() || arrow.isDead() ||
                        !target.isValid() || ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // Corrección suave de trayectoria cada 3 ticks
                if (ticks % 3 == 0) {
                    adjustMortarTrajectory(arrow, target);
                }

                ticks++;
            }
        }.runTaskTimer(org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class), 0L, 1L);
    }

    private void adjustMortarTrajectory(Arrow arrow, LivingEntity target) {
        Location currentLoc = arrow.getLocation();
        Location targetLoc = target.getLocation().add(0, 1, 0);

        // Solo corregir dirección horizontal durante el vuelo
        Vector currentVel = arrow.getVelocity();
        Vector toTarget = targetLoc.toVector().subtract(currentLoc.toVector());
        Vector horizontalToTarget = new Vector(toTarget.getX(), 0, toTarget.getZ()).normalize();

        // Mantener la velocidad vertical (componente Y) sin cambios
        double currentSpeed = currentVel.length();
        Vector horizontalCurrent = new Vector(currentVel.getX(), 0, currentVel.getZ()).normalize();

        // Suavizar la corrección (15% de ajuste)
        double correctionStrength = 0.15;
        Vector correctedHorizontal = horizontalCurrent.multiply(1.0 - correctionStrength)
                .add(horizontalToTarget.multiply(correctionStrength))
                .normalize();

        // Reconstruir velocidad manteniendo magnitud original
        Vector newVelocity = correctedHorizontal.multiply(currentSpeed * 0.95) // Ligera reducción por corrección
                .setY(currentVel.getY() * 0.98); // Ligera reducción vertical

        arrow.setVelocity(newVelocity);
    }

    private void startMortarParticles(Arrow arrow) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isOnGround() || arrow.isDead()) {
                    cancel();
                    return;
                }

                // Partículas de mortero (humo denso)
                arrow.getWorld().spawnParticle(Particle.SPIT, arrow.getLocation(), 3, 0.1, 0.1, 0.1, 0.03);

                // Partículas de fuego en la fase de ascenso
                if (arrow.getVelocity().getY() > 0) {
                    arrow.getWorld().spawnParticle(Particle.SMOKE, arrow.getLocation(), 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }.runTaskTimer(org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class), 0L, 2L);
    }

    @Override
    public void onProjectileHit(Skeleton skeleton, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow))
            return;

        NamespacedKey mortarKey = new NamespacedKey(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class),
                "mortar_arrow");

        if (arrow.getPersistentDataContainer().has(mortarKey, PersistentDataType.BYTE)) {
            handleMortarExplosion(arrow, e);
        }
    }

    private void handleMortarExplosion(Arrow arrow, ProjectileHitEvent e) {
        createGrenadeExplosion(arrow.getLocation(), arrow.getShooter());
        arrow.remove();
    }

    private void createGrenadeExplosion(Location location, org.bukkit.projectiles.ProjectileSource shooter) {
        World world = location.getWorld();

        // Explosión controlada
        world.createExplosion(
                location.getX(), location.getY(), location.getZ(),
                EXPLOSION_POWER, false, false,
                shooter instanceof LivingEntity ? (LivingEntity) shooter : null);

        applyKnockbackToNearbyEntities(location);
        playExplosionEffects(location);
    }

    private void applyKnockbackToNearbyEntities(Location center) {
        double radius = 3.0;

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity livingEntity && !(entity instanceof ArmorStand)) {
                Vector direction = entity.getLocation().toVector().subtract(center.toVector()).normalize();
                double distance = entity.getLocation().distance(center);
                double strength = KNOCKBACK_STRENGTH * (1.0 - (distance / radius));

                if (strength > 0) {
                    Vector velocity = direction.multiply(strength).setY(strength * 0.5);
                    livingEntity.setVelocity(velocity);
                }
            }
        }
    }

    private void playExplosionEffects(Location location) {
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION, location, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.SMOKE, location, 15, 1.0, 1.0, 1.0, 0.2);
    }
}