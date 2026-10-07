package net.zamasoft.foliojet.layout.segment;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.NoSuchElementException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import junit.framework.TestCase;

/**
 * Unit tests for {@link SpillStore} (E-6 increment 2, added 2026-07-24).
 * Verifies append, range cursors, cleanup on close, invalid-data checks, and WARN on deletion failure.
 * The production path is not wired in yet.
 */
public class SpillStoreTest extends TestCase {
	/** append followed by a full-range cursor preserves payload order and content (including empty records). */
	public void testAppendAndCursorRoundTrip() throws Exception {
		try (SpillStore store = SpillStore.create()) {
			final byte[][] records = { "hello".getBytes(StandardCharsets.UTF_8), new byte[0],
					"日本語テキスト".getBytes(StandardCharsets.UTF_8), new byte[] { 0, -1, 127, -128 } };
			for (int i = 0; i < records.length; ++i) {
				assertEquals(i, store.append(records[i]));
			}
			assertEquals(records.length, store.recordCount());

			final SpillStore.Cursor cursor = store.cursor(0, store.recordCount());
			for (int i = 0; i < records.length; ++i) {
				assertTrue("record " + i, cursor.hasNext());
				assertTrue("record " + i, java.util.Arrays.equals(records[i], cursor.next()));
			}
			assertFalse(cursor.hasNext());
			try {
				cursor.next();
				fail("消費済みcursorのnextは失敗するはず");
			} catch (final NoSuchElementException e) {
				// As expected.
			}
		}
	}

	/** Partial ranges, empty ranges, and multiple independent cursors work correctly. */
	public void testPartialRangeAndIndependentCursors() throws Exception {
		try (SpillStore store = SpillStore.create()) {
			for (int i = 0; i < 5; ++i) {
				store.append(new byte[] { (byte) i });
			}
			// Partial range [1, 4).
			final SpillStore.Cursor partial = store.cursor(1, 4);
			// A second, independent cursor (same store, different consumption position).
			final SpillStore.Cursor full = store.cursor(0, 5);
			assertEquals(1, partial.next()[0]);
			assertEquals(0, full.next()[0]);
			assertEquals(2, partial.next()[0]);
			assertEquals(3, partial.next()[0]);
			assertFalse(partial.hasNext());
			assertEquals(1, full.next()[0]);
			// Empty range.
			assertFalse(store.cursor(2, 2).hasNext());
		}
	}

	/** Out-of-bounds and reversed ranges fail at cursor creation. */
	public void testCursorRangeValidation() throws Exception {
		try (SpillStore store = SpillStore.create()) {
			store.append(new byte[] { 1 });
			try {
				store.cursor(-1, 1);
				fail("負のfromIdは失敗するはず");
			} catch (final IllegalArgumentException e) {
			}
			try {
				store.cursor(0, 2);
				fail("recordCount超過のtoIdExclusiveは失敗するはず");
			} catch (final IllegalArgumentException e) {
			}
			try {
				store.cursor(1, 0);
				fail("逆転範囲は失敗するはず");
			} catch (final IllegalArgumentException e) {
			}
		}
	}

	/** close deletes temporary files (both data and index) and is idempotent. */
	public void testCloseDeletesTempFilesAndIsIdempotent() throws Exception {
		final SpillStore store = SpillStore.create();
		final File dataFile = store.dataFileForTest();
		final File indexFile = store.indexFileForTest();
		store.append(new byte[] { 1, 2, 3 });
		assertTrue(dataFile.exists());
		assertTrue(indexFile.exists());
		store.close();
		assertFalse("データファイルが削除されていません", dataFile.exists());
		assertFalse("indexファイルが削除されていません", indexFile.exists());
		// Idempotent (the second close does not throw).
		store.close();
		// Operations after close fail.
		try {
			store.append(new byte[] { 1 });
			fail("close後のappendは失敗するはず");
		} catch (final IllegalStateException e) {
		}
		try {
			store.cursor(0, 0);
			fail("close後のcursorは失敗するはず");
		} catch (final IllegalStateException e) {
		}
	}

	/**
	 * Fault injection: corruption within the data (tampering with a record length field) fails before
	 * cursor traversal begins (at cursor creation), without returning a single payload.
	 */
	public void testCorruptedRecordLengthFailsBeforeIteration() throws Exception {
		try (SpillStore store = SpillStore.create()) {
			final byte[] first = { 1, 2, 3, 4 };
			store.append(first);
			store.append(new byte[] { 5, 6 });
			store.append(new byte[] { 7 });
			// Tamper with record 1's length field (immediately after header 8 + [length 4 + payload 4])
			// by setting it to a huge value.
			try (RandomAccessFile raf = new RandomAccessFile(store.dataFileForTest(), "rw")) {
				raf.seek(8 + 4 + first.length);
				raf.writeInt(Integer.MAX_VALUE);
			}
			try {
				store.cursor(0, 3);
				fail("破損データのcursor作成は失敗するはず");
			} catch (final IOException e) {
				// As expected: fails before traversal starts (no partial replay).
			}
			// Ranges without the corrupt record remain readable.
			assertTrue(java.util.Arrays.equals(first, store.cursor(0, 1).next()));
		}
	}

	/** Fault injection: corruption of the header (magic) also fails at cursor creation. */
	public void testCorruptedMagicFailsBeforeIteration() throws Exception {
		try (SpillStore store = SpillStore.create()) {
			store.append(new byte[] { 1 });
			try (RandomAccessFile raf = new RandomAccessFile(store.dataFileForTest(), "rw")) {
				raf.seek(0);
				raf.writeInt(0xDEADBEEF);
			}
			try {
				store.cursor(0, 1);
				fail("magic破損のcursor作成は失敗するはず");
			} catch (final IOException e) {
			}
		}
	}

	/**
	 * Fault injection: failure to delete a temporary file on close produces a WARN rather than being
	 * silently ignored, while close itself does not propagate the exception (§2.5).
	 */
	public void testDeleteFailureWarnsButDoesNotThrow() throws Exception {
		final SpillStore store = new SpillStore(path -> {
			throw new IOException("injected delete failure: " + path);
		});
		final File dataFile = store.dataFileForTest();
		final File indexFile = store.indexFileForTest();
		final Logger logger = Logger.getLogger(SpillStore.class.getName());
		final java.util.List<LogRecord> warnings = new java.util.ArrayList<>();
		final Handler handler = new Handler() {
			@Override
			public void publish(final LogRecord record) {
				if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
					synchronized (warnings) {
						warnings.add(record);
					}
				}
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		logger.addHandler(handler);
		try {
			store.append(new byte[] { 1 });
			store.close(); // Must not throw.
			synchronized (warnings) {
				assertEquals("削除失敗はデータ・index両ファイル分WARNされるはず", 2, warnings.size());
			}
		} finally {
			logger.removeHandler(handler);
			// Clean up because the injected deleter does not delete anything.
			Files.deleteIfExists(dataFile.toPath());
			Files.deleteIfExists(indexFile.toPath());
		}
	}
}
