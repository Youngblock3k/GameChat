package me.ryxer.gyxerchat.listeners;

import me.ryxer.gyxerchat.menu.Menu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Menus can't be taken items from or put items into (also not with shift-click, number keys or dragging). */
public class MenuListener implements Listener {
   @EventHandler(priority = EventPriority.HIGHEST)
   public void onMenuClick(InventoryClickEvent event) {
      InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
      if (!(holder instanceof Menu menu)) {
         return;
      }
      event.setCancelled(true);
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      if (event.getClickedInventory() != event.getView().getTopInventory()) {
         return;
      }
      int slot = event.getRawSlot();
      if (menu.hasAction(slot)) {
         // run after the click is finished, so opening another menu is safe
         me.ryxer.gyxerchat.GyxerChat plugin = me.ryxer.gyxerchat.GyxerChat.getInstance();
         plugin.playAudio(player, "click");
         plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) == menu) {
               menu.click(player, slot);
            }
         });
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onMenuDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
         event.setCancelled(true);
      }
   }
}
