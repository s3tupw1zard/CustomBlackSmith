package me.allync.horizonblacksmith.utils;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public class ItemUtil {

    public static boolean hasIngredients(Player player, List<ItemStack> ingredients) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> inventoryClone = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                inventoryClone.add(item.clone());
            }
        }

        for (ItemStack requiredItem : ingredients) {
            int amountNeeded = requiredItem.getAmount();
            for (int i = 0; i < inventoryClone.size(); i++) {
                ItemStack inventoryItem = inventoryClone.get(i);
                if (inventoryItem == null || inventoryItem.getAmount() <= 0) continue;

                if (areItemsSimilar(requiredItem, inventoryItem)) {
                    int amountFound = inventoryItem.getAmount();
                    if (amountFound >= amountNeeded) {
                        inventoryItem.setAmount(amountFound - amountNeeded);
                        amountNeeded = 0;
                        break; 
                    } else {
                        amountNeeded -= amountFound;
                        inventoryItem.setAmount(0); 
                    }
                }
            }
            if (amountNeeded > 0) {
                return false; 
            }
        }
        return true; 
    }

    public static void takeIngredients(Player player, List<ItemStack> ingredients) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack requiredItem : ingredients) {
            int amountToRemove = requiredItem.getAmount();
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack inventoryItem = inventory.getItem(i);
                if (inventoryItem == null || inventoryItem.getAmount() <= 0) continue;

                if (areItemsSimilar(requiredItem, inventoryItem)) {
                    int currentAmount = inventoryItem.getAmount();
                    if (currentAmount >= amountToRemove) {
                        inventoryItem.setAmount(currentAmount - amountToRemove);
                        amountToRemove = 0;
                        break;
                    } else {
                        amountToRemove -= currentAmount;
                        inventory.setItem(i, null);
                    }
                }
            }
        }
    }

    private static boolean areItemsSimilar(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;

        HorizonBlacksmith plugin = HorizonBlacksmith.getInstance();
        boolean isItem1Custom = plugin.getCustomItemsHook().isMMOItemsItem(item1) || plugin.getCustomItemsHook().isItemsAdderItem(item1);
        boolean isItem2Custom = plugin.getCustomItemsHook().isMMOItemsItem(item2) || plugin.getCustomItemsHook().isItemsAdderItem(item2);

        if (isItem1Custom != isItem2Custom) {
            return false;
        }

        if (isItem1Custom) {
            String id1 = plugin.getCustomItemsHook().getMMOItemsId(item1);
            if (id1 == null) id1 = plugin.getCustomItemsHook().getItemsAdderId(item1);

            String id2 = plugin.getCustomItemsHook().getMMOItemsId(item2);
            if (id2 == null) id2 = plugin.getCustomItemsHook().getItemsAdderId(item2);

            return id1 != null && id1.equals(id2);
        }

        return item1.isSimilar(item2);
    }
}
