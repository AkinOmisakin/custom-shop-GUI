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
    private Payments payments;

    @Override
    public void onEnable() {
        // Copies the default config.yml (from inside the jar) to the plugin folder,
        // but only if there isn't one yet, so the owner's edits are never overwritten.
        saveDefaultConfig();
        reloadShops();

        payments = new Payments(this);
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

        // Economy plugins register themselves while the server is still starting, so check one tick
        // later, once everything is up, and warn if the config wants Vault money but none is available.
        getServer().getScheduler().runTask(this, () -> {
            if (shopConfig.usesVault()) {
                Currency vaultCurrency = shopConfig.vaultCurrency();
                if (payments.available(vaultCurrency)) {
                    getLogger().info("Vault economy found; Vault currencies are enabled.");
                } else {
                    getLogger().warning("config.yml has a Vault currency but no Vault economy is available. "
                            + "Install Vault and an economy plugin (e.g. EssentialsX), or those items can't be bought.");
                }
            }
        });
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

    public Payments payments() {
        return payments;
    }

    public MerchantManager merchants() {
        return merchants;
    }
}
