package me.allync.horizonblacksmith.api;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;
import me.allync.horizonblacksmith.HorizonBlacksmith;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public class CustomItemsHook {
    private boolean itemsAdderEnabled = false;
    private boolean mmoItemsEnabled = false;

    public void setupHooks() {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            itemsAdderEnabled = true;
            HorizonBlacksmith.getInstance().getLogger().info("Successfully hooked into ItemsAdder.");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            mmoItemsEnabled = true;
            HorizonBlacksmith.getInstance().getLogger().info("Successfully hooked into MMOItems.");
        }
    }

    public ItemStack getItemStack(ConfigurationSection section) {
        if (section == null) {
            return new ItemStack(Material.STONE);
        }

        if (itemsAdderEnabled && section.contains("itemsadder")) {
            String iaId = section.getString("itemsadder");
            CustomStack customStack = CustomStack.getInstance(iaId);
            if (customStack != null) {
                return customStack.getItemStack();
            }
        }

        if (mmoItemsEnabled && section.contains("mmoitems")) {
            String[] mmoIdParts = section.getString("mmoitems", "").split("\\.");
            if (mmoIdParts.length == 2) {
                String type = mmoIdParts[0];
                String id = mmoIdParts[1];
                ItemStack item = MMOItems.plugin.getItem(type, id);
                if (item != null) {
                    return item;
                }
            }
        }

        try {
            Material material = Material.valueOf(section.getString("material", "STONE").toUpperCase());
            return new ItemStack(material);
        } catch (IllegalArgumentException e) {
            HorizonBlacksmith.getInstance().getLogger().warning("Invalid vanilla material in config: '" + section.getString("material") + "'");
            return new ItemStack(Material.STONE);
        }
    }

    public boolean isItemsAdderItem(ItemStack item) {
        if (!itemsAdderEnabled || item == null) return false;
        return CustomStack.byItemStack(item) != null;
    }

    public String getItemsAdderId(ItemStack item) {
        if (!isItemsAdderItem(item)) return null;
        return CustomStack.byItemStack(item).getId();
    }

    public boolean isMMOItemsItem(ItemStack item) {
        if (!mmoItemsEnabled || item == null) return false;
        NBTItem nbt = NBTItem.get(item);
        return nbt.hasType();
    }

    public String getMMOItemsId(ItemStack item) {
        if (!isMMOItemsItem(item)) return null;
        NBTItem nbt = NBTItem.get(item);
        return nbt.getType() + "." + nbt.getString("MMOITEMS_ITEM_ID");
    }
}
