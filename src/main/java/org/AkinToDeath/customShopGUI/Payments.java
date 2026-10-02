package org.AkinToDeath.customShopGUI;

import org.bukkit.entity.Player;

/**
 * Checks and takes payment, whichever kind of {@link Currency} an item is priced in.
 * The shop window talks only to this class, so it doesn't need to know how each currency works.
 */
public final class Payments {

    // Null when the Vault plugin isn't installed. Kept as a separate class so that its
    // Vault imports are only ever loaded on servers that actually have Vault.
    private final VaultBridge vault;

    public Payments(CustomShopGUI plugin) {
        this.vault = plugin.getServer().getPluginManager().getPlugin("Vault") != null
                ? new VaultBridge(plugin) : null;
    }

    /** Can this currency be used right now? Coins always can; Vault money needs Vault and an economy plugin. */
    public boolean available(Currency currency) {
        return currency.type() == Currency.Type.ITEM || (vault != null && vault.available());
    }

    /** How much of the currency the player has (0 if it isn't available). */
    public double balance(Player player, Currency currency) {
        return switch (currency.type()) {
            case ITEM -> currency.count(player.getInventory());
            case VAULT -> vault == null ? 0 : vault.balance(player);
        };
    }

    /** Takes {@code amount} from the player. Returns false, taking nothing, if they can't pay. */
    public boolean withdraw(Player player, Currency currency, double amount) {
        if (balance(player, currency) < amount) {
            return false;
        }
        switch (currency.type()) {
            case ITEM -> currency.take(player.getInventory(), (int) amount);
            case VAULT -> {
                if (vault == null || !vault.withdraw(player, amount)) {
                    return false;
                }
            }
        }
        return true;
    }
}
