package org.AkinToDeath.customShopGUI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Handles /rpgshop (alias /rshop). Only players with the rpgshop.admin permission can use it;
 * that check is done by the server based on plugin.yml.
 *
 * /rpgshop spawn &lt;merchant&gt;         - place an NPC where you stand
 * /rpgshop remove                    - remove the NPC you are looking at
 * /rpgshop open &lt;merchant&gt; [player] - open a shop window without an NPC
 * /rpgshop list                      - show every configured merchant
 * /rpgshop reload                    - re-read config.yml and update existing NPCs
 */
public final class ShopCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("spawn", "remove", "open", "list", "reload");

    private final CustomShopGUI plugin;

    public ShopCommand(CustomShopGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadShops();
                int updated = plugin.merchants().refreshAll();
                sender.sendMessage(Component.text("Loaded " + plugin.shopConfig().merchants().size()
                        + " merchant(s) selling " + plugin.shopConfig().itemCount() + " item(s); updated "
                        + updated + " NPC(s). Check console for warnings.", NamedTextColor.GREEN));
            }
            case "list" -> {
                if (plugin.shopConfig().merchants().isEmpty()) {
                    sender.sendMessage(Component.text("No merchants configured.", NamedTextColor.RED));
                    return true;
                }
                plugin.shopConfig().merchants().values().forEach(m -> sender.sendMessage(Component.text(
                        "- " + m.id() + ": " + m.entityType().name() + ", " + m.items().size() + " item(s)",
                        NamedTextColor.GRAY)));
            }
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can place NPCs.", NamedTextColor.RED));
                    return true;
                }
                Merchant merchant = args.length < 2 ? null : plugin.shopConfig().merchant(args[1].toLowerCase(Locale.ROOT));
                if (merchant == null) {
                    sender.sendMessage(Component.text("Usage: /" + label + " spawn <merchant>  (see /" + label
                            + " list)", NamedTextColor.YELLOW));
                    return true;
                }
                // Face back towards the player: their yaw turned around by 180 degrees.
                plugin.merchants().spawn(merchant, player.getLocation(), player.getLocation().getYaw() + 180f);
                sender.sendMessage(Component.text("Placed merchant '" + merchant.id() + "'.", NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can remove NPCs.", NamedTextColor.RED));
                    return true;
                }
                Entity target = plugin.merchants().targetedMerchant(player);
                if (target == null) {
                    sender.sendMessage(Component.text("Look at a merchant (within 6 blocks) to remove it.",
                            NamedTextColor.RED));
                    return true;
                }
                target.remove();
                sender.sendMessage(Component.text("Merchant removed.", NamedTextColor.GREEN));
            }
            case "open" -> {
                Merchant merchant = args.length < 2 ? null : plugin.shopConfig().merchant(args[1].toLowerCase(Locale.ROOT));
                Player viewer = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                        : (sender instanceof Player p ? p : null);
                if (merchant == null || viewer == null) {
                    sender.sendMessage(Component.text("Usage: /" + label + " open <merchant> [player]",
                            NamedTextColor.YELLOW));
                    return true;
                }
                new ShopMenu(plugin, merchant, viewer).open(viewer);
            }
            default -> sender.sendMessage(Component.text(
                    "Usage: /" + label + " <spawn|remove|open|list|reload>", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("spawn") || sub.equals("open"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.shopConfig().merchants().keySet().stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
