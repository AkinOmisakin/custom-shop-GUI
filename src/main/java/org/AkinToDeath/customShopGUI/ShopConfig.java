package org.AkinToDeath.customShopGUI;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads config.yml and converts it into the Java objects (Currency, Merchant, ShopItem)
 * the rest of the plugin uses. Anything invalid is skipped with a console warning,
 * so a typo in the config never stops the plugin from loading.
 */
public final class ShopConfig {

    /** Fallback texts, used for any key missing from the "messages:" section. */
    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            "price-line", "<gray>Price: <gold><price> <currency>",
            "can-buy-line", "<green>» Click to buy",
            "cannot-buy-line", "<red>✖ You need <price> <currency>",
            "bought", "<green>You bought <item><green> for <gold><price> <currency><green>.",
            "not-enough", "<red>You need <gold><price> <currency><red> but only have <gold><have><red>.",
            "merchant-gone", "<red>This merchant has left town.");

    private final Material border;
    private final Material filler;
    private final Map<String, Currency> currencies;
    private final Map<String, Merchant> merchants;
    private final Map<String, String> messages;

    private ShopConfig(Material border, Material filler, Map<String, Currency> currencies,
                       Map<String, Merchant> merchants, Map<String, String> messages) {
        this.border = border;
        this.filler = filler;
        this.currencies = currencies;
        this.merchants = merchants;
        this.messages = messages;
    }

    public Material border() { return border; }
    public Material filler() { return filler; }
    public Map<String, Merchant> merchants() { return merchants; }
    public Merchant merchant(String id) { return merchants.get(id); }

    /** A configurable text, always non-null. */
    public String message(String key) {
        return messages.getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, ""));
    }

    public int itemCount() {
        return merchants.values().stream().mapToInt(m -> m.items().size()).sum();
    }

    /** Builds a ShopConfig from the raw YAML config. Entry point for parsing. */
    public static ShopConfig load(FileConfiguration config, Logger log) {
        Material border = material(config.getString("settings.border", "BLACK_STAINED_GLASS_PANE"),
                Material.BLACK_STAINED_GLASS_PANE, "settings.border", log);
        Material filler = material(config.getString("settings.filler", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE, "settings.filler", log);

        Map<String, String> messages = new LinkedHashMap<>(DEFAULT_MESSAGES);
        ConfigurationSection ms = config.getConfigurationSection("messages");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                messages.put(key, ms.getString(key, ""));
            }
        }

        Map<String, Currency> currencies = parseCurrencies(config.getConfigurationSection("currencies"), log);
        String defaultCurrency = config.getString("settings.default-currency", "");

        Map<String, Merchant> merchants = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("merchants");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection ms2 = section.getConfigurationSection(id);
                if (ms2 == null) {
                    continue;
                }
                Merchant merchant = parseMerchant(id, ms2, currencies, defaultCurrency, log);
                if (merchant != null) {
                    merchants.put(id.toLowerCase(Locale.ROOT), merchant);
                }
            }
        }
        return new ShopConfig(border, filler, currencies, merchants, messages);
    }

    private static Map<String, Currency> parseCurrencies(ConfigurationSection section, Logger log) {
        Map<String, Currency> currencies = new LinkedHashMap<>();
        if (section == null) {
            log.warning("No 'currencies:' section found, merchants will have nothing to charge in.");
            return currencies;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection cs = section.getConfigurationSection(id);
            if (cs == null) {
                continue;
            }
            Material material = Material.matchMaterial(cs.getString("material", ""));
            if (material == null || !material.isItem() || material.isAir()) {
                log.warning("currencies." + id + ": unknown item material '" + cs.getString("material") + "', skipping.");
                continue;
            }
            String name = cs.getString("name");
            String plainName = (name == null || name.isBlank()) ? null : name;
            // How the coin is shown next to prices. Defaults to its name, or the material if it has none.
            String fallbackDisplay = plainName != null ? plainName : material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            currencies.put(id.toLowerCase(Locale.ROOT),
                    new Currency(id, material, plainName, cs.getString("display", fallbackDisplay)));
        }
        return currencies;
    }

    private static Merchant parseMerchant(String id, ConfigurationSection ms, Map<String, Currency> currencies,
                                          String defaultCurrency, Logger log) {
        String typeName = ms.getString("entity", "VILLAGER").toUpperCase(Locale.ROOT).replace(' ', '_');
        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            log.warning("merchants." + id + ": unknown entity type '" + typeName + "', skipping.");
            return null;
        }
        // Must be something we can actually spawn, and alive (so it can have AI switched off, a name, etc.).
        Class<?> entityClass = type.getEntityClass();
        if (!type.isSpawnable() || entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
            log.warning("merchants." + id + ": '" + typeName + "' can't be used as a merchant, skipping.");
            return null;
        }

        Villager.Profession profession = null;
        String professionName = ms.getString("profession");
        if (professionName != null) {
            profession = RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_PROFESSION)
                    .get(NamespacedKey.fromString(professionName.toLowerCase(Locale.ROOT)));
            if (profession == null) {
                log.warning("merchants." + id + ": unknown profession '" + professionName + "', ignoring it.");
            }
        }
        Villager.Type villagerType = null;
        String villagerTypeName = ms.getString("villager-type");
        if (villagerTypeName != null) {
            villagerType = RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_TYPE)
                    .get(NamespacedKey.fromString(villagerTypeName.toLowerCase(Locale.ROOT)));
            if (villagerType == null) {
                log.warning("merchants." + id + ": unknown villager-type '" + villagerTypeName + "', ignoring it.");
            }
        }

        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection is = ms.getConfigurationSection("items");
        if (is != null) {
            for (String itemId : is.getKeys(false)) {
                ConfigurationSection section = is.getConfigurationSection(itemId);
                if (section == null) {
                    continue;
                }
                ShopItem item = parseItem("merchants." + id + ".items." + itemId, itemId, section,
                        currencies, defaultCurrency, log);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        if (items.isEmpty()) {
            log.warning("merchants." + id + ": has no valid items for sale.");
        }

        String npcName = ms.getString("npc-name", id);
        return new Merchant(id.toLowerCase(Locale.ROOT), npcName, type, profession, villagerType,
                ms.getString("title", npcName), ms.getString("greeting"), List.copyOf(items));
    }

    private static ShopItem parseItem(String path, String id, ConfigurationSection is,
                                      Map<String, Currency> currencies, String defaultCurrency, Logger log) {
        // matchMaterial accepts "IRON_SWORD", "iron_sword" and "minecraft:iron_sword".
        String materialName = is.getString("material", "");
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem() || material.isAir()) {
            log.warning(path + ": unknown item material '" + materialName + "', skipping.");
            return null;
        }

        int price = is.getInt("price", 0);
        if (price < 1) {
            log.warning(path + ": 'price' must be a whole number of at least 1, skipping.");
            return null;
        }

        String currencyId = is.getString("currency", defaultCurrency).toLowerCase(Locale.ROOT);
        Currency currency = currencies.get(currencyId);
        if (currency == null) {
            log.warning(path + ": unknown currency '" + currencyId + "' (set 'currency:' or settings.default-currency), skipping.");
            return null;
        }

        // Keep the amount between 1 and the item's max stack size (e.g. 64, or 1 for swords).
        int amount = Math.max(1, Math.min(is.getInt("amount", 1), material.getMaxStackSize()));

        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        ConfigurationSection es = is.getConfigurationSection("enchantments");
        if (es != null) {
            for (String key : es.getKeys(false)) {
                Enchantment enchantment = findEnchantment(key);
                if (enchantment == null) {
                    log.warning(path + ": unknown enchantment '" + key + "', ignoring it.");
                    continue;
                }
                enchantments.put(enchantment, Math.max(1, es.getInt(key, 1)));
            }
        }
        return new ShopItem(id, material, amount, is.getString("name"), List.copyOf(is.getStringList("lore")),
                Map.copyOf(enchantments), price, currency);
    }

    /** Looks a material up, falling back (with a warning) if the name is wrong. */
    private static Material material(String name, Material fallback, String path, Logger log) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir()) {
            log.warning(path + ": unknown material '" + name + "', using " + fallback + ".");
            return fallback;
        }
        return material;
    }

    /** Looks an enchantment up by its name (e.g. "sharpness" or "minecraft:sharpness"), or null. */
    private static Enchantment findEnchantment(String key) {
        NamespacedKey namespaced = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
        return namespaced == null ? null
                : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(namespaced);
    }
}
