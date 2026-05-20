package com.mobenhancer.events;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Fired by MobEnhancer when a boss actually spawns in the world.
 * This fires when a player activates a placeholder (natural or quest),
 * NOT when the placeholder is created.
 *
 * Other plugins can listen to this event via soft-depend.
 */
public class BossSpawnEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Origin {
        /** Spawned via the natural BossSpawnManager cycle. */
        NATURAL,
        /** Spawned via a Reputation BossQuest. */
        QUEST,
        /** Spawned via admin command. */
        ADMIN
    }

    private final UUID        bossEntityUuid;
    private final String      bossId;
    private final String      bossDisplayName;
    private final Location    spawnLocation;
    private final LivingEntity entity;
    private final Origin      origin;

    public BossSpawnEvent(UUID bossEntityUuid,
                          String bossId,
                          String bossDisplayName,
                          Location spawnLocation,
                          LivingEntity entity,
                          Origin origin) {
        this.bossEntityUuid = bossEntityUuid;
        this.bossId         = bossId;
        this.bossDisplayName = bossDisplayName;
        this.spawnLocation  = spawnLocation;
        this.entity         = entity;
        this.origin         = origin;
    }

    public UUID         getBossEntityUuid()  { return bossEntityUuid;  }
    public String       getBossId()          { return bossId;          }
    public String       getBossDisplayName() { return bossDisplayName; }
    public Location     getSpawnLocation()   { return spawnLocation;   }
    public LivingEntity getEntity()          { return entity;          }
    public Origin       getOrigin()          { return origin;          }

    @Override
    public @NonNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}