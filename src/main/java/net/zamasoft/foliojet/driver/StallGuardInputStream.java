package net.zamasoft.foliojet.driver;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

// 2026-09-02 に MyHttpSourceResolver.java から分けた(本文は移しただけ。設計レビュー「10クラス 1,560行」)。
/**
 * 読み取り毎にストール上限を課すラッパーです(2026-08-08)。
 * {@code java.net.http}の応答ボディ({@code HttpResponseInputStream})は
 * ソケットタイムアウトに相当する仕組みを持たず、配信が止まると
 * {@code read}が永久にブロックする。読み取りを仮想スレッドへ委ねて
 * 時限を課し、超過時は下位ストリームを閉じて{@link IOException}にする
 * (閉じないと委ねた読み取りが残り続ける)。
 */
final class StallGuardInputStream extends InputStream {
	private final InputStream delegate;
	private final ExecutorService executor;
	private final long timeoutMillis;
	private final byte[] one = new byte[1];

	StallGuardInputStream(final InputStream delegate, final ExecutorService executor, final long timeoutMillis) {
		this.delegate = delegate;
		this.executor = executor;
		this.timeoutMillis = timeoutMillis;
	}

	@Override
	public int read() throws IOException {
		final int n = this.read(this.one, 0, 1);
		return n <= 0 ? -1 : (this.one[0] & 0xFF);
	}

	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		final java.util.concurrent.Future<Integer> f = this.executor.submit(() -> this.delegate.read(b, off, len));
		try {
			return f.get(this.timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
		} catch (final java.util.concurrent.TimeoutException e) {
			f.cancel(true);
			try {
				this.delegate.close();
			} catch (final IOException ignore) {
				// 停止したストリームの後始末失敗は握りつぶす
			}
			throw new IOException("応答の読み取りが " + this.timeoutMillis + "ms 停止しました", e);
		} catch (final InterruptedException e) {
			f.cancel(true);
			Thread.currentThread().interrupt();
			throw new java.io.InterruptedIOException();
		} catch (final java.util.concurrent.ExecutionException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof IOException ioe) {
				throw ioe;
			}
			if (cause instanceof RuntimeException re) {
				throw re;
			}
			throw new IOException(cause);
		}
	}

	@Override
	public int available() throws IOException {
		return this.delegate.available();
	}

	@Override
	public void close() throws IOException {
		this.delegate.close();
	}
}
