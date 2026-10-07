package net.zamasoft.foliojet.driver;

import java.io.IOException;
import java.io.InputStream;

// Split from MyHttpSourceResolver.java on 2026-09-02 (body only moved; design review: 10 classes, 1,560 lines).
/**
 * Copies bytes as they are read and delivers the copy once EOF is reached (2026-08-28).
 *
 * <p>
 * Discards the copy if the limit is exceeded (only stops retaining; reads still pass through).
 * Does nothing if discarded midway or not read to the end: using an incomplete copy
 * for the next conversion would silently produce broken output.
 * </p>
 */
final class TeeInputStream extends InputStream {
	private final InputStream in;
	private final java.io.ByteArrayOutputStream copy = new java.io.ByteArrayOutputStream(1 << 16);
	private final int limit;
	private final java.util.function.Consumer<byte[]> sink;
	private boolean overflow, done;

	TeeInputStream(final InputStream in, final int limit, final java.util.function.Consumer<byte[]> sink) {
		this.in = in;
		this.limit = limit;
		this.sink = sink;
	}

	@Override
	public int read() throws IOException {
		final int b = this.in.read();
		if (b < 0) {
			this.finish();
		} else {
			this.record(new byte[] { (byte) b }, 0, 1);
		}
		return b;
	}

	@Override
	public int read(final byte[] buffer, final int off, final int len) throws IOException {
		final int n = this.in.read(buffer, off, len);
		if (n < 0) {
			this.finish();
		} else {
			this.record(buffer, off, n);
		}
		return n;
	}

	private void record(final byte[] buffer, final int off, final int len) {
		if (this.overflow) {
			return;
		}
		if (this.copy.size() + len > this.limit) {
			this.overflow = true;
			this.copy.reset();
			return;
		}
		this.copy.write(buffer, off, len);
	}

	private void finish() {
		if (this.done || this.overflow) {
			return;
		}
		this.done = true;
		this.sink.accept(this.copy.toByteArray());
		this.copy.reset();
	}

	@Override
	public int available() throws IOException {
		return this.in.available();
	}

	@Override
	public void close() throws IOException {
		this.in.close();
	}
}
