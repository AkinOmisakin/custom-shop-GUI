package org.AkinToDeath.customShopGUI;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

/**
 * Connects the game to the shop: right-clicking a merchant opens their window, clicks inside
 * the window are turned into purchases, and merchants are protected from anything that could
 * change or kill them.
 */
public final class MerchantListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final CustomShopGUI plugin;

    public MerchantListener(CustomShopGUI plugin) {
        this.plugin = plugin;
    }

    /** Right-click on a merchant NPC: open their shop instead of the normal villager trading screen. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        String id = plugin.merchants().merchantId(event.getRightClicked());
        if (id == null) {
            return; // an ordinary mob
        }
        // Always cancel, so trading, name tags, leads, saddles etc. never apply to a merchant.
        event.setCancelled(true);
        // A right-click can be reported more than once (once per hand). This listener also receives the
        // "at entity" variant, which comes first and, once cancelled, stops the plain event from firing.
        // So open on whichever arrives first, main hand only, and never if a shop is already open.
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND
                || player.getOpenInventory().getTopInventory().getHolder(false) instanceof ShopMenu) {
            return;
        }
        Merchant merchant = plugin.shopConfig().merchant(id);
        if (merchant == null) {
            player.sendMessage(MINI.deserialize(plugin.shopConfig().message("merchant-gone")));
            return;
        }
        new ShopMenu(plugin, merchant, player).open(player);
    }

    /** Nothing may hurt a merchant (invulnerability alone doesn't stop creative-mode players). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.merchants().merchantId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** Stops lightning turning a villager merchant into a witch, and similar transformations. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTransform(EntityTransformEvent event) {
        if (plugin.merchants().merchantId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** A click anywhere while a shop window is open: block all item movement, then handle buying. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof ShopMenu menu)) {
            return;
        }
        // Cancelling everything, including shift-clicks from the player's own inventory
        // and number-key swaps, means items can't be taken from or put into the window.
        event.setCancelled(true);
        if (event.getClickedInventory() != top || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        menu.handleClick(player, event.getSlot(), event.getClick());
    }

    /** Dragging items across the window would also move items into it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ShopMenu) {
            event.setCancelled(true);
        }
    }
}
