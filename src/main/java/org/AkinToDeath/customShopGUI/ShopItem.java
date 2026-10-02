package org.AkinToDeath.customShopGUI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * One thing a merchant sells: a blueprint for the item plus its price.
 * A brand new ItemStack is built from it on every purchase.
 *
 * @param id           the name of the entry in the config (only used for readable warnings)
 * @param material     what the item is (e.g. IRON_SWORD)
 * @param amount       how many the player gets per purchase
 * @param name         nullable MiniMessage display name
 * @param lore         MiniMessage lines shown under the item name (may be empty)
 * @param enchantments enchantment -> level
 * @param price        how much it costs; a whole number for coin currencies, may have decimals for Vault money
 * @param currency     what pays for it
 */
public record ShopItem(String id, Material material, int amount, String name, List<String> lore,
                       Map<Enchantment, Integer> enchantments, double price, Currency currency) {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Builds a fresh item to hand to the player (no price text on it). */
    public ItemStack create() {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (name != null) {
            meta.displayName(noItalic(MINI.deserialize(name)));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> noItalic(MINI.deserialize(line))).toList());
        }
        // The "true" allows levels above vanilla's normal max (e.g. Sharpness 10).
        enchantments.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
        item.setItemMeta(meta);
        return item;
    }

    /** Custom names/lore are italic by default in-game; turn that off unless the config asks for it. */
    private static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
