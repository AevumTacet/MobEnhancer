package com.mobenhancer.type;

import com.mobenhancer.CustomType;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public class Hopper implements CustomType {

    private static final String Custom_TEXTURE_HASH = "7383a54661e311f94718ec2d4aa587d224553ee924afbb7d40126c66f600dd60";
  
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
        ItemStack CustomHead = createCustomHead();
        zombie.getEquipment().setHelmet(CustomHead);
        zombie.getEquipment().setHelmetDropChance(0);

        incrAttribute(zombie, Attribute.JUMP_STRENGTH, 1.3);
        incrAttribute(zombie, Attribute.FALL_DAMAGE_MULTIPLIER, -1);
        incrAttribute(zombie, Attribute.FOLLOW_RANGE, 8);
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && p.isBlocking()) return;
        zombie.setVelocity(zombie.getVelocity().multiply(0.25));
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent e) {
        zombie.setVelocity(zombie.getVelocity().setY(3));
    }
}
