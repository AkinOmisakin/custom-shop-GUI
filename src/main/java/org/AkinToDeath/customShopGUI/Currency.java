package org.AkinToDeath.customShopGUI;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A "money" item that players pay with, e.g. the Zombie Coin dropped by RPGKillRewardSystem.
 * There is no balance stored anywhere: the coins are simply items in the player's inventory.
 *
 * @param id        the name of the entry in the config (e.g. "zombie_coin")
 * @param material  what the item is (e.g. GOLD_NUGGET)
 * @param plainName nullable; the item's custom name without colours (e.g. "Zombie Coin").
 *                  If set, a stack only counts as money when its name matches exactly (ignoring case)
 * @param display   MiniMessage text used when showing prices (e.g. "<gold>Zombie Coin")
 */
public record Currency(String id, Material material, String plainName, String display) {

    /** Only the 36 main inventory slots count (hotbar + backpack), not armour or off-hand. */
    private static final int STORAGE_SLOTS = 36;

    /** Is this stack one of our coins? */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getType() != material) {
            return false;
        }
        if (plainName == null) {
            return true; // any item of this material counts
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false; // a plain gold nugget is not a Zombie Coin
        }
        String actual = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        return actual.equalsIgnoreCase(plainName);
    }

    /** How many coins the inventory holds in total. */
    public int count(Inventory inventory) {
        int total = 0;
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matches(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Removes {@code amount} coins. The caller must have checked {@link #count} first. */
    public void take(Inventory inventory, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < STORAGE_SLOTS && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matches(stack)) {
                continue;
            }
            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                inventory.setItem(slot, null); // whole stack used up
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                inventory.setItem(slot, stack);
                remaining = 0;
            }
        }
    }
}
