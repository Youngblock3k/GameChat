package gxharness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

/** GyxerChat in-game test: headless players against a real Paper server. */
public final class Main {
	static final int PORT = 25640;
	static Rcon rcon;
	static final List<String> results = new ArrayList<>();
	static int pass, fail;
	static Bot admin, alice, bob;

	static boolean until(BooleanSupplier c, long ms) {
		long end = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < end) {
			if (c.getAsBoolean()) return true;
			sleep(50);
		}
		return c.getAsBoolean();
	}

	static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** The chat cooldown is 1 s; wait a bit more between two messages of the same player. */
	static void gap() {
		sleep(1300);
	}

	static void check(String name, boolean ok, String detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  <-- " + detail);
		System.out.println(line);
		results.add(line);
		if (ok) pass++;
		else fail++;
	}

	static String r(String c) {
		return rcon.cmd(c).strip();
	}

	static void perm(String player, String... nodes) {
		for (String n : nodes) r("gxt perm " + player + " " + n + " true");
	}

	static double num(String s, String key) {
		Matcher m = Pattern.compile(key + "=(-?[0-9.]+)").matcher(s);
		return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
	}

	static String nameOf(ItemStack s) {
		if (s == null || s.getDataComponentsPatch() == null) return "";
		var c = s.getDataComponentsPatch().get(DataComponentTypes.CUSTOM_NAME);
		if (c == null) c = s.getDataComponentsPatch().get(DataComponentTypes.ITEM_NAME);
		return c == null ? "" : Bot.flat(c);
	}

	/** Exact name first ("Red" must not hit "Dark Red"), then a name that contains it. */
	static int slotNamed(Bot b, String part) {
		ItemStack[] items = b.open();
		if (items == null) return -1;
		int size = Math.min(items.length, 54);
		for (int i = 0; i < size; i++) if (nameOf(items[i]).equals(part)) return i;
		for (int i = 0; i < size; i++) if (nameOf(items[i]).contains(part)) return i;
		return -1;
	}

	/** The plugin's small caps letters, to look for styled messages. */
	static String sc(String s) {
		String normal = "abcdefghijklmnopqrstuvwxyz", small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡхʏᴢ";
		StringBuilder b = new StringBuilder();
		for (char c : s.toCharArray()) {
			int i = normal.indexOf(Character.toLowerCase(c));
			b.append(i >= 0 ? small.charAt(i) : c);
		}
		return b.toString();
	}

	static boolean openMenu(Bot b, String command, String titlePart) {
		int before = b.opens.get();
		b.cmd(command);
		return until(() -> b.opens.get() > before && b.containerTitle.contains(titlePart) && b.open() != null, 5000);
	}

	/** Click a button and wait for the menu to be redrawn or closed. */
	static void clickNamed(Bot b, String part) {
		int slot = slotNamed(b, part);
		if (slot < 0) throw new IllegalStateException("no button '" + part + "' in " + b.containerTitle);
		b.click(slot, false);
		sleep(700);
	}

	public static void main(String[] args) throws Exception {
		String phase = args[0];
		try {
			rcon = new Rcon(PORT + 1, "gxtest");
			switch (phase) {
				case "main" -> mainPhase();
				case "restart" -> restartPhase();
				default -> throw new IllegalArgumentException(phase);
			}
		} catch (Throwable t) {
			check("harness ran without crashing (" + phase + ")", false, t.toString());
			t.printStackTrace(System.out);
		}
		results.add("TOTAL " + pass + " passed, " + fail + " failed");
		System.out.println(results.get(results.size() - 1));
		Files.write(Path.of("results-" + phase + ".txt"), results);
		System.exit(0);
	}

	static void mainPhase() {
		check("RCON works and the plugin is enabled", r("plugins").contains("GyxerChat"), r("plugins"));
		admin = new Bot("Admin", PORT).connect();
		perm("Admin", "everlynx.chat.admin", "everlynx.chat.staff", "everlynx.spy", "gyxerchat.nick", "gyxerchat.nick.colors");
		alice = new Bot("Alice", PORT).connect();
		int bobJoinMark = alice.mark();
		bob = new Bot("Bob", PORT).connect();
		check("three bots joined", admin.joined && alice.joined && bob.joined, admin.disconnect + " / " + alice.disconnect + " / " + bob.disconnect);
		check("join message (small caps) shown to others", alice.waitChat(bobJoinMark, "Bob", 4000) && alice.sawSince(bobJoinMark, "ᴊᴏɪɴᴇᴅ"), alice.since(bobJoinMark));
		sleep(1500);

		// ---------------- basic chat
		int m = bob.mark();
		alice.say("hello world");
		check("chat: others see the formatted line", bob.waitChat(m, "hello world", 4000) && bob.sawSince(m, "Alice") && bob.sawSince(m, "»"), bob.since(m));
		check("chat: missing PlaceholderAPI does not show %luckperms_prefix%", !bob.sawSince(m, "%luckperms"), bob.since(m));
		gap();

		m = bob.mark();
		alice.say("I LOVE THIS SERVER");
		check("anti-caps: shouting is calmed down", bob.waitChat(m, "I love this server", 4000), bob.since(m));
		gap();

		m = bob.mark();
		alice.say("Grüße aus Köln ✿");
		check("unicode filter keeps umlauts, removes odd symbols", bob.waitChat(m, "Grüße aus Köln", 4000) && !bob.sawSince(m, "✿"), bob.since(m));
		gap();

		m = bob.mark();
		int am = alice.mark();
		alice.say("hey bob how are you");
		check("mention: @Bob is highlighted", bob.waitChat(m, "@Bob", 4000), bob.since(m));
		gap();

		m = bob.mark();
		alice.say("color &ctest");
		check("& color codes in chat work (code itself hidden)", bob.waitChat(m, "color test", 4000) && !bob.sawSince(m, "&c"), bob.since(m));
		gap();

		m = bob.mark();
		alice.say("<click:run_command:/op Alice>click me</click>");
		check("MiniMessage typed by a player is NOT run (shown as text)", bob.waitChat(m, "<click:run_command", 4000), bob.since(m));
		gap();

		m = bob.mark();
		am = alice.mark();
		alice.say("well same");
		gap();
		alice.say("well same");
		check("repeat block: same message twice is stopped", alice.waitChat(am, "same message twice", 4000), alice.since(am));
		gap();

		// ---------------- swear filter + strikes + auto punish
		m = bob.mark();
		am = alice.mark();
		alice.say("oh shit man");
		check("swear filter censors the word", bob.waitChat(m, "oh *** man", 4000), bob.since(m));
		check("swear filter gives a strike (1/3)", alice.waitChat(am, "(1/3)", 4000), alice.since(am));
		gap();
		am = alice.mark();
		alice.say("spam one");
		alice.say("spam two");
		check("cooldown blocks fast messages and gives a strike (2/3)", alice.waitChat(am, "(2/3)", 4000), alice.since(am));
		gap();
		am = alice.mark();
		alice.say("what the f u c k");
		check("third strike runs the punish command (tempmute)", alice.waitChat(am, "muted", 5000), alice.since(am));
		gap();
		m = bob.mark();
		am = alice.mark();
		alice.say("can anyone hear me");
		check("muted player can't talk and is told why", alice.waitChat(am, "Max Violations", 4000) && !bob.waitChat(m, "can anyone hear me", 1500), alice.since(am) + " // bob: " + bob.since(m));
		int adm = admin.mark();
		admin.cmd("unmute Alice");
		check("/unmute works", admin.waitChat(adm, "unmuted", 4000), admin.since(adm));
		gap();

		// ---------------- mute commands
		adm = admin.mark();
		am = alice.mark();
		admin.cmd("tempmute Alice 1m testing mutes");
		check("/tempmute: admin gets confirmation with time", admin.waitChat(adm, "1m", 4000), admin.since(adm));
		check("/tempmute: target is told", alice.waitChat(am, "testing mutes", 4000), alice.since(am));
		adm = admin.mark();
		admin.cmd("mute zz_no_such_9 test");
		check("/mute of a name that never joined is refused", admin.waitChat(adm, "never joined", 8000), admin.since(adm));
		admin.cmd("unmute Alice");
		sleep(500);
		String mutes = r("gxt pdc Alice nickname");
		check("helper reachable", mutes.startsWith("value="), mutes);

		// ---------------- private messages, reply, spy, ignore
		admin.cmd("socialspy");
		sleep(400);
		int bm = bob.mark();
		adm = admin.mark();
		alice.cmd("msg Bob secret one");
		check("/msg: receiver sees it", bob.waitChat(bm, "[Alice -> Me] secret one", 4000), bob.since(bm));
		check("/socialspy: staff sees the private message", admin.waitChat(adm, "Alice -> Bob: secret one", 4000), admin.since(adm));
		am = alice.mark();
		bob.cmd("r answer two");
		check("/r: reply reaches the sender", alice.waitChat(am, "[Bob -> Me] answer two", 4000), alice.since(am));
		bm = bob.mark();
		alice.cmd("r third");
		check("/r: replying back works too", bob.waitChat(bm, "[Alice -> Me] third", 4000), bob.since(bm));
		admin.cmd("socialspy");

		bm = bob.mark();
		bob.cmd("ignore add Alice");
		check("/ignore add", bob.waitChat(bm, "now ignoring", 4000), bob.since(bm));
		gap();
		bm = bob.mark();
		am = alice.mark();
		alice.say("can you see this bob");
		alice.cmd("msg Bob hello?");
		check("ignored player's chat is hidden", !bob.waitChat(bm, "can you see this", 2000), bob.since(bm));
		check("ignored player can't /msg", alice.waitChat(am, "cannot message", 3000) && !bob.sawSince(bm, "hello?"), alice.since(am));
		bm = bob.mark();
		bob.cmd("ignore list");
		check("/ignore list shows the name", bob.waitChat(bm, "Alice", 3000), bob.since(bm));
		bob.cmd("ignore remove Alice");
		sleep(500);

		// ---------------- staff chat
		adm = admin.mark();
		bm = bob.mark();
		admin.cmd("sc staff only text");
		check("/staffchat: staff see it", admin.waitChat(adm, "staff only text", 3000), admin.since(adm));
		check("/staffchat: normal players don't", !bob.waitChat(bm, "staff only text", 1500), bob.since(bm));
		admin.cmd("sc");
		sleep(400);
		bm = bob.mark();
		adm = admin.mark();
		admin.say("toggled staff line");
		check("staff chat toggle: normal chat goes to staff only (fixed format key)", admin.waitChat(adm, "toggled staff line", 3000) && admin.sawSince(adm, "[") && !bob.waitChat(bm, "toggled staff line", 1500), admin.since(adm));
		admin.cmd("sc");
		sleep(400);

		// ---------------- global chat lock
		admin.cmd("mutechat");
		sleep(500);
		gap();
		am = alice.mark();
		bm = bob.mark();
		alice.say("locked?");
		check("/mutechat: players can't talk", alice.waitChat(am, sc("disabled"), 3000), alice.since(am));
		admin.say("admins still can");
		check("/mutechat: admins can", bob.waitChat(bm, "admins still can", 3000), bob.since(bm));
		admin.cmd("mutechat");
		sleep(500);

		// ---------------- nick
		perm("Alice", "gyxerchat.nick");
		gap();
		am = alice.mark();
		alice.cmd("nick Bob");
		check("/nick: another player's name is refused", alice.waitChat(am, "belongs to another player", 3000), alice.since(am));
		am = alice.mark();
		alice.cmd("nick &cAli");
		check("/nick: colors are removed without gyxerchat.nick.colors", alice.waitChat(am, "nickname has been set", 3000), alice.since(am));
		check("/nick: saved (same key as version 1)", r("gxt pdc Alice nickname").equals("value=Ali"), r("gxt pdc Alice nickname"));
		check("/nick: tab list shows the nickname", r("gxt listname Alice").equals("listname=Ali"), r("gxt listname Alice"));
		bm = bob.mark();
		alice.say("my new name");
		check("/nick: chat shows the nickname", bob.waitChat(bm, "Ali", 3000) && bob.sawSince(bm, "my new name") && !bob.sawSince(bm, "Alice"), bob.since(bm));
		am = alice.mark();
		alice.cmd("nick Waytoolongname123");
		check("/nick: too long is refused", alice.waitChat(am, "too long", 3000), alice.since(am));
		alice.cmd("unnick");
		sleep(500);
		check("/unnick clears it", r("gxt pdc Alice nickname").equals("value=none"), r("gxt pdc Alice nickname"));

		// ---------------- tags
		adm = admin.mark();
		admin.cmd("createtag VIP &6[VIP]");
		check("/createtag", admin.waitChat(adm, "Created tag", 3000) && admin.sawSince(adm, "vip"), admin.since(adm));
		perm("Alice", "tag.equip.vip");
		sleep(300);
		check("/tags opens the menu", openMenu(alice, "tags", "Tags"), alice.containerTitle);
		check("tags menu lists the old tag from tags.yml (id lowercased)", slotNamed(alice, "[OLD]") >= 0, "no [OLD] button");
		am = alice.mark();
		clickNamed(alice, "[VIP]");
		check("tag equipped by clicking", alice.waitChat(am, "equipped", 3000), alice.since(am));
		gap();
		bm = bob.mark();
		alice.say("tag check");
		check("chat shows the tag", bob.waitChat(bm, "[VIP]", 3000), bob.since(bm));
		admin.cmd("deletetag vip");
		sleep(500);
		gap();
		bm = bob.mark();
		alice.say("after delete");
		check("deleted tag disappears from chat", bob.waitChat(bm, "after delete", 3000) && !bob.sawSince(bm, "[VIP]"), bob.since(bm));

		// ---------------- settings menu + menu safety
		check("/settings opens", openMenu(bob, "settings", "My Settings"), bob.containerTitle);
		int slot = slotNamed(bob, "Global Chat Visibility");
		bob.click(slot, true);
		sleep(400);
		bob.numberKey(slot, 0);
		sleep(400);
		check("menu items can't be taken (shift-click / number key)", r("gxt inv Bob").equals("items=0"), r("gxt inv Bob"));
		clickNamed(bob, "Global Chat Visibility");
		check("settings toggle saved (chat_disabled)", r("gxt pdc Bob chat_disabled").startsWith("value=byte"), r("gxt pdc Bob chat_disabled"));
		bob.closeContainer();
		gap();
		bm = bob.mark();
		alice.say("bob has chat off");
		check("chat off: Bob doesn't see public chat", !bob.waitChat(bm, "bob has chat off", 2000), bob.since(bm));
		openMenu(bob, "settings", "My Settings");
		clickNamed(bob, "Global Chat Visibility");
		bob.closeContainer();
		check("settings toggle back on", r("gxt pdc Bob chat_disabled").equals("value=none"), r("gxt pdc Bob chat_disabled"));

		// ---------------- chat color
		perm("Bob", "gyxerchat.chatcolor.red");
		sleep(300);
		check("/chatcolor opens", openMenu(bob, "chatcolor", "Chat Color"), bob.containerTitle);
		clickNamed(bob, "Red");
		check("chat color saved", r("gxt pdc Bob chatcolor").equals("value=&c"), r("gxt pdc Bob chatcolor"));
		openMenu(bob, "chatcolor", "Chat Color");
		clickNamed(bob, "Blue");
		check("locked color can't be picked", r("gxt pdc Bob chatcolor").equals("value=&c"), r("gxt pdc Bob chatcolor"));
		bob.closeContainer();

		// ---------------- admin menu: start a chat game
		check("/chatadmin opens", openMenu(admin, "chatadmin", "Admin"), admin.containerTitle);
		am = alice.mark();
		clickNamed(admin, "Start Chat Game");
		check("chat game question is broadcast", alice.waitChat(am, "ᴄʜᴀᴛ ɢᴀᴍᴇ", 4000), alice.since(am));
		String q = alice.since(am);
		String answer = q.contains("mniearctf") ? "minecraft" : q.contains("15 + 25") ? "40" : q.contains("GyxerChat Elite") ? "GyxerChat Elite" : "bedrock";
		gap();
		am = alice.mark();
		bm = bob.mark();
		alice.say(answer);
		check("right answer wins", bob.waitChat(bm, "ᴡᴏɴ", 5000) && bob.sawSince(bm, "Alice"), bob.since(bm));

		// ---------------- menu: mute from the player list
		check("admin settings menu opens", openMenu(admin, "chatadmin", "Admin"), admin.containerTitle);
		clickNamed(admin, "Manage Players");
		clickNamed(admin, "Bob");
		check("manage player menu", admin.containerTitle.contains("Managing: Bob"), admin.containerTitle);
		bm = bob.mark();
		clickNamed(admin, "Mute Player");
		check("mute from menu tells the player", bob.waitChat(bm, "muted", 3000), bob.since(bm));
		clickNamed(admin, "Unmute Player");
		check("unmute from menu", bob.waitChat(bm, "talk again", 3000), bob.since(bm));
		admin.closeContainer();

		// ---------------- TPA
		r("gxt tp Alice 40 -60 40");
		r("gxt tp Bob 0 -60 0");
		sleep(800);
		bm = bob.mark();
		alice.cmd("tpa Bob");
		check("/tpa request arrives", bob.waitChat(bm, "to teleport to you", 3000), bob.since(bm));
		bob.cmd("tpaccept");
		sleep(7500);
		String ap = r("gxt pos Alice");
		check("/tpa: Alice is teleported to Bob", Math.abs(num(ap, "x")) < 2 && Math.abs(num(ap, "z")) < 2, ap);

		r("gxt tp Alice 40 -60 40");
		r("gxt tp Bob 0 -60 0");
		sleep(800);
		bm = bob.mark();
		alice.cmd("tpahere Bob");
		check("/tpahere request says 'teleport you to them'", bob.waitChat(bm, "teleport you to them", 3000), bob.since(bm));
		bob.cmd("tpaccept");
		sleep(7500);
		String bp = r("gxt pos Bob");
		ap = r("gxt pos Alice");
		check("/tpahere: Bob moves to Alice (was broken in v1)", Math.abs(num(bp, "x") - 40) < 2 && Math.abs(num(ap, "x") - 40) < 2, "bob " + bp + " alice " + ap);

		r("gxt tp Alice 40 -60 40");
		r("gxt tp Bob 0 -60 0");
		sleep(800);
		alice.cmd("tpa Bob");
		sleep(500);
		am = alice.mark();
		bob.cmd("tpaccept");
		sleep(1500);
		alice.move(44, -60, 44);
		check("moving cancels the teleport", alice.waitChat(am, "you moved", 4000), alice.since(am));

		r("gxt tp Alice 40 -60 40");
		sleep(800);
		alice.cmd("tpa Bob");
		sleep(500);
		am = alice.mark();
		bob.cmd("tpaccept");
		sleep(1500);
		r("gxt damage Alice");
		check("damage cancels the teleport", alice.waitChat(am, "took damage", 4000), alice.since(am));

		bm = bob.mark();
		bob.cmd("tpatoggle");
		check("/tpatoggle on", bob.waitChat(bm, "automatically accept", 3000), bob.since(bm));
		am = alice.mark();
		alice.cmd("tpa Bob");
		check("auto-accept starts the teleport", alice.waitChat(am, "auto-accept", 3000), alice.since(am));
		bob.cmd("tpatoggle");
		sleep(7000);

		String[] sug = alice.suggest("/tpa B");
		check("tab complete /tpa", Arrays.asList(sug).contains("Bob"), Arrays.toString(sug));
		sug = admin.suggest("/tempmute Alice ");
		check("tab complete times", Arrays.asList(sug).contains("1h"), Arrays.toString(sug));

		// ---------------- death message
		am = alice.mark();
		r("gxt kill Bob");
		check("death message (with small caps)", alice.waitChat(am, "Bob", 4000) && alice.sawSince(am, "ᴅɪᴇᴅ"), alice.since(am));
		bob.respawn();
		sleep(1000);

		// ---------------- broadcaster (interval set to 10 s by the old config)
		am = alice.mark();
		check("auto broadcast arrives", alice.waitChat(am, "BROADCAST-TEST", 40000), alice.since(am));

		// ---------------- clear chat
		bm = bob.mark();
		admin.cmd("clearchat");
		check("/clearchat sends empty lines and a notice", bob.waitChat(bm, "ᴄʜᴀᴛ ᴄʟᴇᴀʀᴇᴅ", 3000) && bob.chat.size() - bm >= 100, "lines: " + (bob.chat.size() - bm));

		// ---------------- ban
		admin.cmd("tempban Bob 1m testing bans");
		check("/tempban kicks with the reason", until(() -> bob.disconnect != null, 4000) && bob.disconnect.contains("testing bans"), String.valueOf(bob.disconnect));
		bob.connect();
		check("banned player can't rejoin", !bob.joined && bob.disconnect != null && bob.disconnect.toLowerCase().contains("ban"), String.valueOf(bob.disconnect));
		adm = admin.mark();
		admin.cmd("unban Bob");
		check("/unban", admin.waitChat(adm, "Unbanned", 4000), admin.since(adm));
		bob.connect();
		check("unbanned player can rejoin", bob.joined, String.valueOf(bob.disconnect));

		// ---------------- Discord with a bad token must not freeze the server
		r("gxt cfg discord.enabled true");
		r("gxt cfg discord.bot-token str:not.a.real.token");
		long t0 = System.currentTimeMillis();
		String reload = r("chatreload");
		long took = System.currentTimeMillis() - t0;
		check("/chatreload with Discord on returns fast (bot connects in background)", took < 3000 && reload.contains("reloaded"), took + " ms: " + reload);
		r("gxt cfg discord.enabled false");
		r("chatreload");

		alice.quit();
		bob.quit();
		admin.quit();
	}

	/** After a restart: data is still there. */
	static void restartPhase() {
		alice = new Bot("Alice", PORT).connect();
		bob = new Bot("Bob", PORT).connect();
		check("bots rejoin after restart", alice.joined && bob.joined, alice.disconnect + " / " + bob.disconnect);
		check("chat color survived the restart", r("gxt pdc Bob chatcolor").equals("value=&c"), r("gxt pdc Bob chatcolor"));
		sleep(1500);
		int bm = bob.mark();
		bob.cmd("ignore add Alice");
		check("/ignore after restart", bob.waitChat(bm, "now ignoring", 3000), bob.since(bm));
		alice.quit();
		bob.quit();
	}
}
