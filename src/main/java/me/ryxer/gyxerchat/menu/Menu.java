package me.ryxer.gyxerchat.menu;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import me.ryxer.gyxerchat.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A chest menu that knows its own buttons. Clicks are found by slot, not by reading the item's name or the
 * window title (version 1 did that, so renaming an item or opening any other "...Settings" chest broke it).
 */
public final class Menu implements InventoryHolder {
   private final Inventory inventory;
   private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

   public Menu(int rows, String title) {
      this.inventory = Bukkit.createInventory(this, rows * 9, Text.item(title));
   }

   @Override
   public Inventory getInventory() {
      return this.inventory;
   }

   public int size() {
      return this.inventory.getSize();
   }

   public Menu set(int slot, ItemStack item, Consumer<Player> action) {
      this.inventory.setItem(slot, item);
      if (action != null) {
         this.actions.put(slot, action);
      } else {
         this.actions.remove(slot);
      }
      return this;
   }

   /** Puts an item into the next empty slot inside the border. Returns false when the menu is full. */
   public boolean add(ItemStack item, Consumer<Player> action) {
      for (int i = 0; i < this.size(); i++) {
         if (this.inventory.getItem(i) == null) {
            this.set(i, item, action);
            return true;
         }
      }
      return false;
   }

   public void click(Player player, int slot) {
      Consumer<Player> a = this.actions.get(slot);
      if (a != null) {
         a.accept(player);
      }
   }

   public boolean hasAction(int slot) {
      return this.actions.containsKey(slot);
   }

   public void open(Player player) {
      player.openInventory(this.inventory);
   }

   /** Black corners, gray edges (same look as version 1). */
   public Menu borders() {
      ItemStack black = item(Material.BLACK_STAINED_GLASS_PANE, " ");
      ItemStack gray = item(Material.GRAY_STAINED_GLASS_PANE, " ");
      int size = this.size();
      for (int i = 0; i < size; i++) {
         if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
            boolean corner = i == 0 || i == 8 || i == size - 9 || i == size - 1;
            this.inventory.setItem(i, corner ? black : gray);
         }
      }
      return this;
   }

   public static ItemStack item(Material mat, String name, String... lore) {
      return item(mat, name, Arrays.asList(lore));
   }

   public static ItemStack item(Material mat, String name, List<String> lore) {
      ItemStack item = new ItemStack(mat);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.displayName(Text.item(name));
         meta.lore(lore.stream().map(Text::item).toList());
         meta.addItemFlags(ItemFlag.values());
         item.setItemMeta(meta);
      }
      return item;
   }

   public static ItemStack named(ItemStack item, Component name, List<Component> lore) {
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.displayName(name);
         meta.lore(lore);
         item.setItemMeta(meta);
      }
      return item;
   }
}
