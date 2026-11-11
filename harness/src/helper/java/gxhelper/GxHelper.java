package gxhelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** TEST ONLY. RCON commands the GyxerChat bot test uses to set up and read the game. */
public final class GxHelper extends JavaPlugin implements Listener {
	private final Map<UUID, PermissionAttachment> perms = new HashMap<>();
	private final Map<String, Map<String, Boolean>> wanted = new HashMap<>();

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoin(PlayerJoinEvent e) {
		Map<String, Boolean> w = wanted.get(e.getPlayer().getName());
		if (w != null) attach(e.getPlayer(), w);
	}

	private void attach(Player p, Map<String, Boolean> nodes) {
		PermissionAttachment old = perms.remove(p.getUniqueId());
		if (old != null) old.remove();
		PermissionAttachment a = p.addAttachment(this);
		nodes.forEach(a::setPermission);
		perms.put(p.getUniqueId(), a);
		p.updateCommands();
	}

	@Override
	public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
		if (!(s instanceof RemoteConsoleCommandSender) && !(s instanceof org.bukkit.command.ConsoleCommandSender)) return true;
		try {
			s.sendMessage(run(a));
		} catch (Exception e) {
			s.sendMessage("ERR " + e);
		}
		return true;
	}

	private String run(String[] a) {
		Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
		switch (a[0]) {
			case "perm" -> {
				wanted.computeIfAbsent(a[1], k -> new HashMap<>()).put(a[2], Boolean.parseBoolean(a[3]));
				if (p != null) attach(p, wanted.get(a[1]));
				return "ok";
			}
			case "pos" -> {
				Location l = p.getLocation();
				return String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f z=%.2f world=%s", l.getX(), l.getY(), l.getZ(), l.getWorld().getName());
			}
			case "tp" -> {
				p.teleport(new Location(p.getWorld(), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4])));
				return "ok";
			}
			case "pdc" -> {
				NamespacedKey k = new NamespacedKey("gyxerchat", a[2]);
				var pdc = p.getPersistentDataContainer();
				if (pdc.has(k, PersistentDataType.STRING)) return "value=" + pdc.get(k, PersistentDataType.STRING);
				if (pdc.has(k, PersistentDataType.BYTE)) return "value=byte" + pdc.get(k, PersistentDataType.BYTE);
				return "value=none";
			}
			case "kill" -> {
				p.setHealth(0);
				return "ok";
			}
			case "damage" -> {
				p.damage(1.0);
				return "ok";
			}
			case "inv" -> {
				int n = 0;
				for (ItemStack i : p.getInventory().getContents()) if (i != null && !i.getType().isAir()) n++;
				ItemStack cursor = p.getItemOnCursor();
				if (cursor != null && !cursor.getType().isAir()) n++;
				return "items=" + n;
			}
			case "listname" -> {
				var c = p.playerListName();
				return "listname=" + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
			}
			case "cfg" -> {
				Plugin g = Bukkit.getPluginManager().getPlugin("GyxerChat");
				String v = a[2];
				Object val = v.equals("true") || v.equals("false") ? Boolean.valueOf(v) : v.matches("-?\\d+") ? (Object)Integer.valueOf(v) : v.replace('_', ' ');
				if (v.startsWith("str:")) val = v.substring(4);
				((JavaPlugin)g).getConfig().set(a[1], val);
				((JavaPlugin)g).saveConfig();
				return "ok";
			}
			case "tick" -> {
				return "tick=" + Bukkit.getCurrentTick();
			}
			default -> {
				return "unknown";
			}
		}
	}
}
