package org.AkinToDeath.customShopGUI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shop window one player sees when they talk to a merchant.
 *
 * Layout (6 rows, slots numbered left to right, top to bottom):
 * <pre>
 *  ▮ ▮ ▮ ▮ P ▮ ▮ ▮ ▮     P = purse (the player's coins)
 *  ▮ . . . . . . . ▮     . = up to 28 items for sale per page
 *  ▮ . . . . . . . ▮
 *  ▮ . . . . . . . ▮
 *  ▮ . . . . . . . ▮
 *  ◀ ▮ ▮ ▮ i ▮ ▮ ▮ ▶     ◀ ▶ = previous/next page, i = page number (click to close)
 * </pre>
 * Implementing InventoryHolder is how the click listener recognises "this is one of our windows".
 */
public final class ShopMenu implements InventoryHolder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final int SIZE = 54;
    private static final int ITEMS_PER_PAGE = 28; // 4 rows x 7 columns
    private static final int PURSE_SLOT = 4;
    private static final int PREVIOUS_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final CustomShopGUI plugin;
    private final Merchant merchant;
    private final Inventory inventory;
    // Which shop item sits in which slot right now, so a click can be turned into a purchase.
    private final Map<Integer, ShopItem> itemSlots = new HashMap<>();
    private int page;

    public ShopMenu(CustomShopGUI plugin, Merchant merchant, Player viewer) {
        this.plugin = plugin;
        this.merchant = merchant;
        this.inventory = Bukkit.createInventory(this, SIZE, MINI.deserialize(merchant.title()));
        render(viewer);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.2f);
        if (merchant.greeting() != null && !merchant.greeting().isBlank()) {
            player.sendMessage(MINI.deserialize(merchant.greeting()));
        }
    }

    /** Called by the listener for every click inside the shop window's top half. */
    public void handleClick(Player player, int slot, ClickType click) {
        if (slot == INFO_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            click(player);
            render(player);
            return;
        }
        if (slot == NEXT_SLOT && page < pageCount() - 1) {
            page++;
            click(player);
            render(player);
            return;
        }
        ShopItem item = itemSlots.get(slot);
        if (item != null && (click.isLeftClick() || click.isRightClick())) {
            purchase(player, item);
        }
    }

    /** Tries to buy one item: check the money, take it, hand over the goods. */
    private void purchase(Player player, ShopItem item) {
        Payments payments = plugin.payments();
        Currency currency = item.currency();
        double have = payments.balance(player, currency);
        TagResolver tags = tags(item, have);

        // Vault money needs Vault and an economy plugin; say so instead of "you can't afford it".
        if (!payments.available(currency)) {
            player.sendMessage(MINI.deserialize(plugin.shopConfig().message("economy-unavailable"), tags));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        // withdraw() checks the balance again and takes nothing if the player can't pay.
        if (have < item.price() || !payments.withdraw(player, currency, item.price())) {
            player.sendMessage(MINI.deserialize(plugin.shopConfig().message("not-enough"), tags));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // addItem returns whatever didn't fit; drop that at the player's feet so nothing is lost.
        player.getInventory().addItem(item.create())
                .values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));

        player.sendMessage(MINI.deserialize(plugin.shopConfig().message("bought"), tags));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        render(player); // coin counts changed, so refresh the "can afford" lines
    }

    /** Redraws the whole window for this viewer (page, prices, affordability, purse). */
    public void render(Player viewer) {
        ShopConfig config = plugin.shopConfig();
        inventory.clear();
        itemSlots.clear();

        ItemStack border = pane(config.border());
        ItemStack filler = pane(config.filler());
        for (int slot = 0; slot < SIZE; slot++) {
            boolean frame = slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
            inventory.setItem(slot, frame ? border : filler);
        }

        // Items: 7 per row, starting at slot 10 (row 1, column 1), skipping the frame columns.
        page = Math.min(page, pageCount() - 1);
        List<ShopItem> items = merchant.items();
        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && start + i < items.size(); i++) {
            ShopItem item = items.get(start + i);
            int slot = 10 + (i / 7) * 9 + (i % 7);
            inventory.setItem(slot, forSale(item, viewer));
            itemSlots.put(slot, item);
        }

        inventory.setItem(PURSE_SLOT, purse(viewer));
        inventory.setItem(INFO_SLOT, button(Material.BARRIER, "<red>Leave shop",
                "<gray>Page " + (page + 1) + "/" + pageCount(), "<dark_gray>Click to close"));
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "<yellow>« Previous page"));
        }
        if (page < pageCount() - 1) {
            inventory.setItem(NEXT_SLOT, button(Material.ARROW, "<yellow>Next page »"));
        }
    }

    private int pageCount() {
        return Math.max(1, (merchant.items().size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    /** The item as shown in the window: its normal look plus price and affordability lines. */
    private ItemStack forSale(ShopItem item, Player viewer) {
        ShopConfig config = plugin.shopConfig();
        double have = plugin.payments().balance(viewer, item.currency());
        TagResolver tags = tags(item, have);

        ItemStack stack = item.create();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.empty());
        lore.add(line(config.message("price-line"), tags));
        lore.add(line(config.message(have >= item.price() ? "can-buy-line" : "cannot-buy-line"), tags));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** A gold ingot listing how much of each currency this merchant accepts the player has. */
    private ItemStack purse(Player viewer) {
        // A merchant may accept several coins; list each one once, in the order they appear.
        Set<Currency> accepted = new LinkedHashSet<>();
        merchant.items().forEach(item -> accepted.add(item.currency()));

        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line("<gold><bold>Your Purse", TagResolver.empty()));
        List<Component> lore = new ArrayList<>();
        for (Currency currency : accepted) {
            lore.add(line("<white><count> <currency>", TagResolver.resolver(
                    Placeholder.unparsed("count", currency.format(plugin.payments().balance(viewer, currency))),
                    Placeholder.component("currency", MINI.deserialize(currency.display())))));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Fills the <item>, <price>, <currency> and <have> tags used in the config texts. */
    private TagResolver tags(ShopItem item, double have) {
        // Insert the item's name as a finished component so its colours can't leak into the rest of the message.
        ItemStack made = item.create();
        Component itemName = made.getItemMeta().hasDisplayName()
                ? made.getItemMeta().displayName()
                : Component.translatable(made.getType().translationKey());
        return TagResolver.resolver(
                Placeholder.component("item", itemName),
                Placeholder.component("currency", MINI.deserialize(item.currency().display())),
                Placeholder.unparsed("price", item.currency().format(item.price())),
                Placeholder.unparsed("have", item.currency().format(have)));
    }

    private static Component line(String text, TagResolver tags) {
        return MINI.deserialize(text, tags).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** A named button; every extra string becomes a lore line. */
    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(name, TagResolver.empty()));
        if (lore.length > 0) {
            meta.lore(java.util.Arrays.stream(lore).map(l -> line(l, TagResolver.empty())).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** A decoration pane with no hover tooltip at all. */
    private static ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }
}
