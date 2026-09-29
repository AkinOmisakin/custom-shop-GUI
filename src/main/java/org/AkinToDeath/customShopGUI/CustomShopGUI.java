package org.AkinToDeath.customShopGUI;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main class of the plugin. Its job is just to wire everything together: load the config,
 * register the listener and register the /rpgshop command.
 */
public final class CustomShopGUI extends JavaPlugin {

    // The parsed version of config.yml. "volatile" makes sure that when /rpgshop reload
    // swaps in a new one, everything immediately sees it.
    private volatile ShopConfig shopConfig;
    private MerchantManager merchants;

    @Override
    public void onEnable() {
        // Copies the default config.yml (from inside the jar) to the plugin folder,
        // but only if there isn't one yet, so the owner's edits are never overwritten.
        saveDefaultConfig();
        reloadShops();

        merchants = new MerchantManager(this);
        getServer().getPluginManager().registerEvents(new MerchantListener(this), this);

        PluginCommand command = getCommand("rpgshop");
        if (command != null) {
            ShopCommand handler = new ShopCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        getLogger().info("Loaded " + shopConfig.merchants().size() + " merchant(s) selling "
                + shopConfig.itemCount() + " item(s).");
    }

    /** Re-reads config.yml from disk and rebuilds all merchants. Used on startup and by /rpgshop reload. */
    public void reloadShops() {
        reloadConfig();
        shopConfig = ShopConfig.load(getConfig(), getLogger());
    }

    /** The currently active currencies, merchants and texts. */
    public ShopConfig shopConfig() {
        return shopConfig;
    }

    public MerchantManager merchants() {
        return merchants;
    }
}
