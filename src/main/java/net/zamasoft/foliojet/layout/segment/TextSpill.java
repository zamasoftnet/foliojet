package net.zamasoft.foliojet.layout.segment;

import java.io.File;
import java.io.IOException;

/**
 * A spill facade dedicated to text payloads (E-6 increment 3b-2, introduced on 2026-07-24).
 *
 * <p>
 * Provides the minimal interface ({@code append}/{@code read}/{@code close}) for spilling the
 * char[] payload of {@code LayoutSource.Chars} to disk when the budget is exceeded.
 * {@link SpillStore} handles physical storage (a minimal package-private component; the codex decision
 * keeps the store itself private). This class handles only UTF-16BE encoding/decoding and direct reads
 * by recordId. It has no layout semantics.
 * </p>
 *
 * <p>
 * <b>Lifetime</b>: created lazily when {@code LayoutSource} first needs to spill and reliably closed
 * by {@code LayoutSource#close()} (in the finally block on the conversion completion path).
 * Close is idempotent and deletes both temporary files.
 * </p>
 *
 * <p>
 * Not thread-safe (intended for use within a single layout session, the same contract as {@code SpillStore}).
 * </p>
 */
public final class TextSpill implements AutoCloseable {
	/**
	 * Injection point for spill I/O failures (tests only, following {@link SpillStore.TempFileDeleter}).
	 * E-6 endurance tests (2026-07-24) use it to verify typed failures ({@code TextSpillException}) on spill
	 * write/read errors, temporary-file cleanup, and successful subsequent conversions.
	 * Always null on the production path; behavior with null is exactly the same as before this hook was added.
	 */
	interface IOFaultInjector {
		/** Called just before {@link TextSpill#append} writes to the store. */
		void beforeAppend() throws IOException;

		/** Called just before {@link TextSpill#read} reads from the store. */
		void beforeRead() throws IOException;
	}

	/** Failure-injection hook for tests only (set through TextSpillTestHooks). */
	static volatile IOFaultInjector faultInjector = null;

	private final SpillStore store;

	private TextSpill(final SpillStore store) {
		this.store = store;
	}

	/** Opens a new spill store (temporary files). */
	public static TextSpill open() throws IOException {
		return new TextSpill(SpillStore.create());
	}

	/**
	 * Appends {@code ch[off..off+len)} as one UTF-16BE record and returns its recordId
	 * (a zero-based sequence number).
	 */
	public long append(final char[] ch, final int off, final int len) throws IOException {
		final IOFaultInjector injector = faultInjector;
		if (injector != null) {
			injector.beforeAppend();
		}
		final byte[] record = new byte[len * 2];
		for (int i = 0; i < len; ++i) {
			final char c = ch[off + i];
			record[i * 2] = (byte) (c >>> 8);
			record[i * 2 + 1] = (byte) c;
		}
		return this.store.append(record);
	}

	/**
	 * Decodes and returns the record at recordId. The returned array is <b>always fresh on each call</b>.
	 * Downstream replay processing transforms it in place, so no cached array is shared (the 3b-1 policy).
	 * If the expected length in heap metadata differs from the actual record length, treats this as
	 * corruption and fails with {@link IOException}.
	 */
	public char[] read(final long recordId, final int expectedUtf16Length) throws IOException {
		final IOFaultInjector injector = faultInjector;
		if (injector != null) {
			injector.beforeRead();
		}
		final byte[] record = this.store.read(recordId);
		if (record.length != expectedUtf16Length * 2) {
			throw new IOException("spilled text record corrupted (length " + record.length + " != "
					+ (expectedUtf16Length * 2) + ") at record " + recordId);
		}
		final char[] ch = new char[expectedUtf16Length];
		for (int i = 0; i < ch.length; ++i) {
			ch[i] = (char) (((record[i * 2] & 0xFF) << 8) | (record[i * 2 + 1] & 0xFF));
		}
		return ch;
	}

	/** Closes the store and deletes temporary files (idempotent). */
	@Override
	public void close() {
		this.store.close();
	}

	File dataFileForTest() {
		return this.store.dataFileForTest();
	}

	File indexFileForTest() {
		return this.store.indexFileForTest();
	}

	/** Returns whether both temporary files have been deleted (for test observation). */
	public boolean tempFilesDeletedForTest() {
		return !this.store.dataFileForTest().exists() && !this.store.indexFileForTest().exists();
	}
}
