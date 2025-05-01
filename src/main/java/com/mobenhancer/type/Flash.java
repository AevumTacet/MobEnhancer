package com.mobenhancer.type;

import com.mobenhancer.CustomType;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public class Flash implements CustomType {

    private static final String Custom_TEXTURE_HASH = "9a428a141a3983cdc9f821f34793d92faae7b085d26008384787058e2d93e350";
  
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
        return "flash";
    }

    @Override
    public String getName() {
        return "Flash";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
        if (zombie instanceof PigZombie) {
            e.setCancelled(true);
            return;
        }

        ItemStack CustomHead = createCustomHead();
        zombie.getEquipment().setHelmet(CustomHead);
        zombie.getEquipment().setHelmetDropChance(0);

        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 4, false, false));
        incrAttribute(zombie, Attribute.WATER_MOVEMENT_EFFICIENCY, 1.5);
        incrAttribute(zombie, Attribute.SCALE, -0.15);
    }
}
