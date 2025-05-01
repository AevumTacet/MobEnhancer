package com.mobenhancer.type;

import com.mobenhancer.CustomType;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public class WindBuster implements CustomType {

    private static final String Custom_TEXTURE_HASH = "53dfc75c84d138f7e3b0a5b04dfea8a7f99e157d776ca988d5b31e0036131e";
  
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
        return "busted";
    }

    @Override
    public String getName() {
        return "Wind Busted";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
        ItemStack CustomHead = createCustomHead();
        zombie.getEquipment().setHelmet(CustomHead);
        zombie.getEquipment().setHelmetDropChance(0);
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.WIND_CHARGED, PotionEffect.INFINITE_DURATION, 1, false, false));

        incrAttribute(zombie, Attribute.GRAVITY, -.04);
        incrAttribute(zombie, Attribute.STEP_HEIGHT, 0.4);
        incrAttribute(zombie, Attribute.SAFE_FALL_DISTANCE, 3);
    }

    @Override
    public void whenAttacked(Zombie zombie, EntityDamageByEntityEvent e) {
        zombie.getWorld().spawn(zombie.getLocation().add(0, 0.25, 0), WindCharge.class, AbstractWindCharge::explode);
    }
}
