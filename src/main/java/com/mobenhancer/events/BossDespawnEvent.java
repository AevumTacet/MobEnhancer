package com.mobenhancer.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Fired by MobEnhancer when a boss is removed from the world for any reason
 * other than being killed (timeout, chunk unload, admin command, etc.).
 *
 * Other plugins (e.g. Reputation) can listen to this event with @EventHandler
 * without importing any MobEnhancer classes — only this event class needs to
 * be shared (via soft-depend + provided scope in pom.xml).
 *
 * If the boss was killed normally, EntityDeathEvent fires instead.
 * This event only covers non-death removals.
 */
public class BossDespawnEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Reason {
        /** Boss had no target for too long and was removed by MobEnhancer. */
        TIMEOUT,
        /** Boss was removed by an admin command. */
        ADMIN,
        /** Other / unspecified reason. */
        OTHER
    }

    private final UUID        bossEntityUuid;
    private final String      bossId;          // e.g. "putrid_colossus"
    private final String      bossDisplayName;
    private final Reason      reason;
    /** May be null if the entity was already invalid when the event fires. */
    private final LivingEntity entity;

    public BossDespawnEvent(UUID bossEntityUuid, String bossId, String bossDisplayName,
                            Reason reason, LivingEntity entity) {
        this.bossEntityUuid  = bossEntityUuid;
        this.bossId          = bossId;
        this.bossDisplayName = bossDisplayName;
        this.reason          = reason;
        this.entity          = entity;
    }

    public UUID        getBossEntityUuid()  { return bossEntityUuid; }
    public String      getBossId()          { return bossId; }
    public String      getBossDisplayName() { return bossDisplayName; }
    public Reason      getReason()          { return reason; }
    /** The living entity, or null if already removed. */
    public LivingEntity getEntity()         { return entity; }

    @Override public @NonNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()          { return HANDLERS; }
}