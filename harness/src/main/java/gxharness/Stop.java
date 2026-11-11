package gxharness;

/** Sends "stop" over RCON. */
public final class Stop {
	public static void main(String[] a) throws Exception {
		try (Rcon r = new Rcon(25641, "gxtest")) {
			r.cmd("stop");
		} catch (Exception ignored) {
		}
	}
}
