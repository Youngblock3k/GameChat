package gxharness;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.net.InetSocketAddress;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandSuggestionsPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundCommandSuggestionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;

/** A headless test player. Records what the server shows it. */
final class Bot {
	final String name;
	private final int port;
	private ClientNetworkSession session;
	final List<String> chat = new CopyOnWriteArrayList<>();
	final List<String> actionbar = new CopyOnWriteArrayList<>();
	final Map<Integer, String[]> suggestions = new ConcurrentHashMap<>();
	final Map<Integer, ItemStack[]> containers = new ConcurrentHashMap<>();
	final AtomicInteger opens = new AtomicInteger();
	volatile int containerId = -1, stateId;
	volatile String containerTitle = "";
	volatile double x, y, z;
	volatile boolean joined;
	volatile String disconnect;
	private final AtomicInteger tx = new AtomicInteger(1);

	Bot(String name, int port) {
		this.name = name;
		this.port = port;
	}

	Bot connect() {
		disconnect = null;
		joined = false;
		MinecraftProtocol proto = new MinecraftProtocol(name);
		session = new ClientNetworkSession(new InetSocketAddress("127.0.0.1", port), proto,
				java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread th = new Thread(r, "bot-" + name); th.setDaemon(true); return th; }), null, null);
		session.addListener(new SessionAdapter() {
			@Override
			public void packetReceived(Session s, Packet p) {
				try {
					handle(p);
				} catch (Exception e) {
					System.out.println("[" + name + "] packet error " + e);
				}
			}

			@Override
			public void disconnected(DisconnectedEvent e) {
				joined = false;
				disconnect = flat(e.getReason());
			}
		});
		session.connect(true);
		Main.until(() -> joined || disconnect != null, 20000);
		return this;
	}

	void quit() {
		session.disconnect(Component.text("bye"));
		Main.until(() -> !joined, 5000);
	}

	static String flat(Component c) {
		if (c == null) return "";
		StringBuilder b = new StringBuilder();
		flat(c, b);
		return b.toString();
	}

	private static void flat(Component c, StringBuilder b) {
		if (c instanceof TextComponent t) b.append(t.content());
		else if (c instanceof TranslatableComponent t) {
			b.append(t.key()).append('(');
			boolean first = true;
			for (TranslationArgument a : t.arguments()) {
				if (!first) b.append(',');
				first = false;
				flat(a.asComponent(), b);
			}
			b.append(')');
		}
		for (Component ch : c.children()) flat(ch, b);
	}

	private void handle(Packet p) {
		switch (p) {
			case ClientboundLoginPacket l -> {
				joined = true;
				session.send(ServerboundPlayerLoadedPacket.INSTANCE);
			}
			case ClientboundPlayerPositionPacket pos -> {
				var rel = pos.getRelatives().stream().map(Enum::name).toList();
				x = (rel.contains("X") ? x : 0) + pos.getPosition().getX();
				y = (rel.contains("Y") ? y : 0) + pos.getPosition().getY();
				z = (rel.contains("Z") ? z : 0) + pos.getPosition().getZ();
				session.send(acceptTeleport(pos.getId(), x, y, z));
				if (!ACCEPT_HAS_POS) session.send(new ServerboundMovePlayerPosPacket(true, false, x, y, z));
				System.out.println("[" + name + "] teleport " + pos.getRelatives() + " " + pos.getPosition() + " -> " + x + "," + y + "," + z);
			}
			case ClientboundSystemChatPacket c -> (c.isOverlay() ? actionbar : chat).add(flat(c.getContent()));
			case ClientboundDisguisedChatPacket c -> chat.add("<" + flat(c.getName()) + "> " + flat(c.getMessage()));
			case ClientboundPlayerChatPacket c -> chat.add(c.getUnsignedContent() != null ? flat(c.getUnsignedContent()) : "<" + flat(c.getName()) + "> " + c.getContent());
			case ClientboundSetActionBarTextPacket a -> actionbar.add(flat(a.getText()));
			case ClientboundOpenScreenPacket o -> {
				containerId = o.getContainerId();
				containerTitle = flat(o.getTitle());
				containers.remove(o.getContainerId());
				opens.incrementAndGet();
			}
			case ClientboundContainerSetContentPacket c -> {
				containers.put(c.getContainerId(), c.getItems().clone());
				stateId = c.getStateId();
			}
			case ClientboundContainerSetSlotPacket c -> {
				ItemStack[] items = containers.get(c.getContainerId());
				if (items != null && c.getSlot() >= 0 && c.getSlot() < items.length) items[c.getSlot()] = c.getItem();
				stateId = c.getStateId();
			}
			case ClientboundContainerClosePacket c -> containerId = -1;
			case ClientboundCommandSuggestionsPacket s -> suggestions.put(s.getTransactionId(), s.getMatches());
			default -> {}
		}
	}

	static final boolean ACCEPT_HAS_POS = java.util.Arrays.stream(ServerboundAcceptTeleportationPacket.class.getConstructors()).anyMatch(c -> c.getParameterCount() == 6);

	/** 26.3 added the position to this packet; older versions only take the id. */
	private static Packet acceptTeleport(int id, double px, double py, double pz) {
		try {
			for (var c : ServerboundAcceptTeleportationPacket.class.getConstructors()) {
				Class<?>[] t = c.getParameterTypes();
				if (t.length == 6 && t[0] == int.class) return (Packet) c.newInstance(id, px, py, pz, 0f, 0f);
				if (t.length == 1 && t[0] == int.class) return (Packet) c.newInstance(id);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		throw new IllegalStateException("no ServerboundAcceptTeleportationPacket constructor");
	}

	void cmd(String command) {
		session.send(new ServerboundChatCommandPacket(command.startsWith("/") ? command.substring(1) : command));
	}

	void say(String text) {
		session.send(new ServerboundChatPacket(text, System.currentTimeMillis(), 0L, null, 0, new BitSet(20), 0));
	}

	void move(double nx, double ny, double nz) {
		x = nx;
		y = ny;
		z = nz;
		session.send(new ServerboundMovePlayerPosPacket(true, false, nx, ny, nz));
	}

	void respawn() {
		session.send(new ServerboundClientCommandPacket(ClientCommand.values()[0]) /* RESPAWN, renamed PERFORM_RESPAWN in 26.x */);
	}

	String[] suggest(String text) {
		int id = tx.incrementAndGet();
		session.send(new ServerboundCommandSuggestionPacket(id, text));
		Main.until(() -> suggestions.containsKey(id), 3000);
		return suggestions.getOrDefault(id, new String[0]);
	}

	void click(int slot, boolean shift) {
		int id = Math.max(0, containerId);
		var type = shift ? ContainerActionType.SHIFT_CLICK_ITEM : ContainerActionType.CLICK_ITEM;
		var param = shift ? ShiftClickItemAction.LEFT_CLICK : ClickItemAction.LEFT_CLICK;
		session.send(new ServerboundContainerClickPacket(id, stateId, slot, type, param, null, new Int2ObjectOpenHashMap<>()));
	}

	void numberKey(int slot, int hotbar) {
		int id = Math.max(0, containerId);
		session.send(new ServerboundContainerClickPacket(id, stateId, slot, ContainerActionType.MOVE_TO_HOTBAR_SLOT,
				ContainerActionType.MOVE_TO_HOTBAR_SLOT.actionFrom(hotbar, 0), null, new Int2ObjectOpenHashMap<>()));
	}

	void closeContainer() {
		session.send(new ServerboundContainerClosePacket(Math.max(0, containerId)));
		containerId = -1;
	}

	ItemStack[] open() {
		return containerId < 0 ? null : containers.get(containerId);
	}

	boolean sawSince(int mark, String part) {
		return chat.subList(Math.min(mark, chat.size()), chat.size()).stream().anyMatch(l -> l.contains(part));
	}

	boolean waitChat(int mark, String part, long ms) {
		return Main.until(() -> sawSince(mark, part), ms);
	}

	int mark() {
		return chat.size();
	}

	String since(int mark) {
		return String.join(" | ", chat.subList(Math.min(mark, chat.size()), chat.size()));
	}
}
