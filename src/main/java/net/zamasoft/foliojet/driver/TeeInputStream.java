package net.zamasoft.foliojet.driver;

import java.io.IOException;
import java.io.InputStream;

// 2026-09-02 に MyHttpSourceResolver.java から分けた(本文は移しただけ。設計レビュー「10クラス 1,560行」)。
/**
 * 読まれたバイトを写し取り、末尾まで読み切ったら渡します(2026-08-28)。
 *
 * <p>
 * 上限を超えたら写しを捨てます(控えないだけで、読み出しは素通し)。
 * 途中で捨てられた・読み切られなかった場合は何もしません——欠けた写しを
 * 次の変換で使うと、黙って壊れた結果が出るためです。
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
