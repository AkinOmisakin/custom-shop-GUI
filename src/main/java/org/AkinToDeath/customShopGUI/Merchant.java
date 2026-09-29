package org.AkinToDeath.customShopGUI;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.util.List;

/**
 * One entry under "merchants:" in config.yml: an NPC type with its own name, look and stock.
 * The config only describes the merchant; actual NPCs are placed in the world with /rpgshop spawn.
 *
 * @param id          the name of the entry in the config (e.g. "blacksmith")
 * @param npcName     MiniMessage name floating above the NPC
 * @param entityType  the kind of mob the NPC is (VILLAGER by default)
 * @param profession  nullable; villager outfit (only used when the NPC is a villager)
 * @param villagerType nullable; villager biome look (only used when the NPC is a villager)
 * @param title       MiniMessage title of the shop window
 * @param greeting    nullable MiniMessage line sent to the player when they open the shop
 * @param items       everything the merchant sells, in display order
 */
public record Merchant(String id, String npcName, EntityType entityType, Villager.Profession profession,
                       Villager.Type villagerType, String title, String greeting, List<ShopItem> items) {
}
