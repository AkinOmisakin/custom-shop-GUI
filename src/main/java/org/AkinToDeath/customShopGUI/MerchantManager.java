package org.AkinToDeath.customShopGUI;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.function.Consumer;

/**
 * Creates and finds merchant NPCs. An NPC is an ordinary mob with its AI switched off,
 * made invulnerable, and stamped with the merchant's id in its persistent data. That stamp is
 * saved with the world, so the NPCs are still there (and still recognised) after a restart.
 */
public final class MerchantManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final CustomShopGUI plugin;
    private final NamespacedKey merchantKey;

    public MerchantManager(CustomShopGUI plugin) {
        this.plugin = plugin;
        this.merchantKey = new NamespacedKey(plugin, "merchant");
    }

    /** The merchant id stamped on this entity, or null if it isn't one of our NPCs. */
    public String merchantId(Entity entity) {
        return entity.getPersistentDataContainer().get(merchantKey, PersistentDataType.STRING);
    }

    /** Places a new NPC at the given spot, facing the way the player is looking back at them. */
    @SuppressWarnings("unchecked")
    public Entity spawn(Merchant merchant, Location where, float facingYaw) {
        // Centre it on the block so it doesn't stand on an edge.
        Location spot = new Location(where.getWorld(), where.getBlockX() + 0.5, where.getY(),
                where.getBlockZ() + 0.5, facingYaw, 0f);
        World world = spot.getWorld();
        Class<? extends Entity> entityClass = (Class<? extends Entity>) merchant.entityType().getEntityClass();
        // The consumer runs before the mob appears in the world, so it never shows up half set up.
        return world.spawn(spot, entityClass, (Consumer<Entity>) entity -> setup(entity, merchant, facingYaw));
    }

    /** Turns a freshly spawned mob into a merchant. */
    private void setup(Entity entity, Merchant merchant, float yaw) {
        entity.getPersistentDataContainer().set(merchantKey, PersistentDataType.STRING, merchant.id());
        entity.setRotation(yaw, 0f);
        if (entity instanceof LivingEntity living) {
            living.setAI(false);              // stands still, doesn't wander or flee
            living.setInvulnerable(true);
            living.setSilent(true);
            living.setCollidable(false);      // players can't shove it around
            living.setPersistent(true);       // never despawns
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
            living.setBodyYaw(yaw);
        }
        applyLook(entity, merchant);
    }

    /** Sets the parts of the NPC that come from config, so /rpgshop reload can update existing NPCs too. */
    private void applyLook(Entity entity, Merchant merchant) {
        entity.customName(MINI.deserialize(merchant.npcName()));
        entity.setCustomNameVisible(true);
        if (entity instanceof Villager villager) {
            if (merchant.profession() != null) {
                villager.setProfession(merchant.profession());
                // A little experience locks the profession in, so it never resets to "unemployed".
                villager.setVillagerExperience(1);
            }
            if (merchant.villagerType() != null) {
                villager.setVillagerType(merchant.villagerType());
            }
        }
    }

    /** Re-applies names and looks to every loaded NPC. Returns how many were updated. */
    public int refreshAll() {
        int updated = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                String id = merchantId(entity);
                Merchant merchant = id == null ? null : plugin.shopConfig().merchant(id);
                if (merchant != null) {
                    applyLook(entity, merchant);
                    updated++;
                }
            }
        }
        return updated;
    }

    /** The NPC the player is looking at (within 6 blocks), or null. */
    public Entity targetedMerchant(Player player) {
        Entity target = player.getTargetEntity(6);
        return target != null && merchantId(target) != null ? target : null;
    }
}
