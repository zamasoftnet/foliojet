package net.zamasoft.foliojet.driver;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

// Split from MyHttpSourceResolver.java on 2026-09-02 (body only moved; design review: 10 classes, 1,560 lines).
/**
 * A wrapper that imposes a stall limit on each read (2026-08-08).
 * The {@code java.net.http} response body ({@code HttpResponseInputStream}) has no mechanism
 * equivalent to a socket timeout, so {@code read} blocks forever if delivery stops.
 * Delegates reading to a virtual thread with a time limit, then closes the underlying stream
 * and raises {@link IOException} if exceeded (otherwise the delegated read would remain pending).
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
				// Ignore cleanup failures for the stopped stream.
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
