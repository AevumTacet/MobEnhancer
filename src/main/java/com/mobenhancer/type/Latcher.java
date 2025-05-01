package com.mobenhancer.type;

import com.mobenhancer.CustomType;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public class Latcher implements CustomType {

    private static final String Custom_TEXTURE_HASH = "74ca669667f3319ab5d8d5347d7a4afcf6dd7c06d21224fc36ea9bc07c47e25d";
  
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
        return "latcher";
    }

    @Override
    public String getName() {
        return "Latcher";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
        ItemStack CustomHead = createCustomHead();
        zombie.getEquipment().setHelmet(CustomHead);
        zombie.getEquipment().setHelmetDropChance(0);
        incrAttribute(zombie, Attribute.SCALE, -0.3);
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity l)) return;

        l.addPassenger(zombie);
        if (!l.getPassengers().contains(zombie)) zombie.getWorld().playSound(zombie.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1, 1);
    }
}
