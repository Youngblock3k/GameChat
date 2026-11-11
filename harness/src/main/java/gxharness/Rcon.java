package gxharness;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal RCON client (console commands with synchronous replies). */
final class Rcon implements AutoCloseable {
	private final Socket s;
	private final DataInputStream in;
	private final OutputStream out;
	private int id = 1;

	Rcon(int port, String password) throws IOException {
		s = new Socket("127.0.0.1", port);
		in = new DataInputStream(s.getInputStream());
		out = s.getOutputStream();
		send(3, password);
		if (read().id == -1) throw new IOException("rcon login failed");
	}

	private record Reply(int id, String body) {}

	private synchronized void send(int type, String body) throws IOException {
		byte[] b = body.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(14 + b.length).order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(10 + b.length).putInt(++id).putInt(type).put(b).put((byte) 0).put((byte) 0);
		out.write(buf.array());
		out.flush();
	}

	private Reply read() throws IOException {
		byte[] lenB = new byte[4];
		in.readFully(lenB);
		int len = ByteBuffer.wrap(lenB).order(ByteOrder.LITTLE_ENDIAN).getInt();
		byte[] data = new byte[len];
		in.readFully(data);
		ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		int rid = bb.getInt();
		bb.getInt();
		return new Reply(rid, new String(data, 8, Math.max(0, len - 10), StandardCharsets.UTF_8));
	}

	synchronized String cmd(String command) {
		try {
			send(2, command);
			return read().body.replaceAll("\u00a7.", "");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void close() throws IOException {
		s.close();
	}
}