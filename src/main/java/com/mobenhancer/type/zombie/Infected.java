package com.mobenhancer.type.zombie;

import com.mobenhancer.ZombieCustomType;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public class Infected implements ZombieCustomType {

    private static final String Custom_TEXTURE_HASH = "8427435f1ecde67688cd7baad9ef0fd5bb384976e8142e644f98ce74d5d026b6";

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
        return "infected";
    }

    @Override
    public String getName() {
        return "Infected";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
        ItemStack CustomHead = createCustomHead();
        zombie.getEquipment().setHelmet(CustomHead);
        zombie.getEquipment().setHelmetDropChance(0);

        // zombie.addPotionEffect(new PotionEffect(PotionEffectType.WEAVING,
        // PotionEffect.INFINITE_DURATION, 2, false, false));
        zombie.addPotionEffect(
                new PotionEffect(PotionEffectType.INFESTED, PotionEffect.INFINITE_DURATION, 6, false, true));
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity l))
            return;
        if (e.getEntity() instanceof Player p && p.isBlocking())
            return;

        l.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 1, false, true));
        l.addPotionEffect(new PotionEffect(PotionEffectType.OOZING, 400, 1, false, true));
    }
}
