package org.AkinToDeath.customShopGUI;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * The only class that touches the Vault API. It is created only when the Vault plugin is installed
 * (see {@link Payments}), so servers without Vault never load it and never see a missing-class error.
 */
final class VaultBridge {

    private final CustomShopGUI plugin;

    VaultBridge(CustomShopGUI plugin) {
        this.plugin = plugin;
    }

    /**
     * The economy that is registered right now, or null if none is. It is looked up on every call
     * rather than stored, so it still works if the economy plugin loads after ours or gets swapped.
     */
    private Economy economy() {
        RegisteredServiceProvider<Economy> registration =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    boolean available() {
        return economy() != null;
    }

    double balance(Player player) {
        Economy economy = economy();
        return economy == null ? 0 : economy.getBalance(player);
    }

    /** Takes the money. Returns false (and takes nothing) if there is no economy or the player can't pay. */
    boolean withdraw(Player player, double amount) {
        Economy economy = economy();
        if (economy == null || !economy.has(player, amount)) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
}
