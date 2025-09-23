package com.mobenhancer.type.skeleton;

import com.mobenhancer.SkeletonCustomType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class SpiderJockey implements SkeletonCustomType {

    @Override
    public String getId() {
        return "spiderjockey";
    }

    @Override
    public String getName() {
        return "SpiderJockey";
    }

    @Override
    public void onSpawn(Skeleton skeleton, CreatureSpawnEvent e) {
        // Pequeño delay para asegurar que el esqueleto esté completamente spawneado
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!skeleton.isValid())
                    return;

                spawnSpiderAndMount(skeleton);
            }
        }.runTaskLater(org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class), 2L);
    }

    private void spawnSpiderAndMount(Skeleton skeleton) {
        try {
            // Spawnear la araña ligeramente ajustada para evitar colisiones
            Spider spider = (Spider) skeleton.getWorld().spawnEntity(
                    skeleton.getLocation().add(0, 0.1, 0),
                    EntityType.SPIDER);

            // Configurar la araña antes de montar
            configureSpider(spider);

            // Montar el esqueleto en la araña
            if (spider.isValid() && skeleton.isValid()) {
                spider.addPassenger(skeleton);
            }

        } catch (Exception ex) {
            // Log error si es necesario
            ex.printStackTrace();
        }
    }

    private void configureSpider(Spider spider) {
        // 30% más de velocidad
        double originalSpeed = spider.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
        spider.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(originalSpeed * 1.3);

        // No desaparecer cuando esté lejos
        spider.setRemoveWhenFarAway(false);
    }

    @Override
    public void onTarget(Skeleton skeleton, EntityTargetEvent e) {
        // Sincronizar el target de la araña con el del esqueleto
        if (e.getTarget() instanceof LivingEntity target) {
            syncSpiderTarget(skeleton, target);
        }
    }

    private void syncSpiderTarget(Skeleton skeleton, LivingEntity target) {
        // Buscar la araña que monta este esqueleto
        if (skeleton.isInsideVehicle() && skeleton.getVehicle() instanceof Spider) {
            Spider spider = (Spider) skeleton.getVehicle();

            // Establecer el mismo target para la araña
            if (spider.isValid() && target.isValid()) {
                spider.setTarget(target);
            }
        }
    }
}