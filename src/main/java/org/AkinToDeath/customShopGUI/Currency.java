package org.AkinToDeath.customShopGUI;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Something players pay with. Two kinds exist:
 * <ul>
 *   <li>{@link Type#ITEM}: a "coin" item in the player's inventory, e.g. the Zombie Coin dropped by
 *       RPGKillRewardSystem. There is no balance stored anywhere.</li>
 *   <li>{@link Type#VAULT}: the player's money in whichever economy plugin is installed through Vault.</li>
 * </ul>
 * Checking and taking the money itself is done by {@link Payments}; this class only describes the currency.
 *
 * @param id        the name of the entry in the config (e.g. "zombie_coin")
 * @param type      item coin or Vault money
 * @param material  ITEM only: what the coin is (e.g. GOLD_NUGGET), null for Vault
 * @param plainName ITEM only, nullable: the coin's custom name without colours (e.g. "Zombie Coin").
 *                  If set, a stack only counts as money when its name matches exactly (ignoring case)
 * @param display   MiniMessage text used when showing prices (e.g. "<gold>Zombie Coin")
 */
public record Currency(String id, Type type, Material material, String plainName, String display) {

    public enum Type { ITEM, VAULT }

    /** Only the 36 main inventory slots count (hotbar + backpack), not armour or off-hand. */
    private static final int STORAGE_SLOTS = 36;

    /** Writes an amount the way players should see it: "15" for coins, "1,250.5" for Vault money. */
    public String format(double amount) {
        if (type == Type.ITEM) {
            return String.valueOf((long) amount);
        }
        // Up to two decimals, no trailing zeros, always "." as the decimal point.
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(amount);
    }

    /** ITEM only: is this stack one of our coins? */
    public boolean matches(ItemStack stack) {
        if (type != Type.ITEM || stack == null || stack.getType() != material) {
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

    /** ITEM only: how many coins the inventory holds in total. */
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

    /** ITEM only: removes {@code amount} coins. The caller must have checked {@link #count} first. */
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
