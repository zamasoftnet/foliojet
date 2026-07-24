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
 * {@link SpillStore}の単体テストです(E-6増分2、2026-07-24新設)。
 * append/範囲cursor/close時cleanup/不正データ検査/削除失敗WARNを
 * 検証する。production経路は未配線。
 */
public class SpillStoreTest extends TestCase {
	/** append→全範囲cursorでpayloadが順序・内容とも一致する(空recordを含む)。 */
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
				// 期待どおり
			}
		}
	}

	/** 部分範囲・空範囲・複数の独立cursorが正しく動く。 */
	public void testPartialRangeAndIndependentCursors() throws Exception {
		try (SpillStore store = SpillStore.create()) {
			for (int i = 0; i < 5; ++i) {
				store.append(new byte[] { (byte) i });
			}
			// 部分範囲 [1, 4)
			final SpillStore.Cursor partial = store.cursor(1, 4);
			// 独立した2本目のcursor(同じストア、別の消費位置)
			final SpillStore.Cursor full = store.cursor(0, 5);
			assertEquals(1, partial.next()[0]);
			assertEquals(0, full.next()[0]);
			assertEquals(2, partial.next()[0]);
			assertEquals(3, partial.next()[0]);
			assertFalse(partial.hasNext());
			assertEquals(1, full.next()[0]);
			// 空範囲
			assertFalse(store.cursor(2, 2).hasNext());
		}
	}

	/** 範囲外・逆転範囲はcursor作成時に失敗する。 */
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

	/** closeは一時ファイル(データ・index両方)を削除し、冪等である。 */
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
		// 冪等(2回目のcloseで例外にならない)
		store.close();
		// close後の操作は失敗する
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
	 * 例外注入: 途中データ破損(record長フィールドの改竄)は、cursorの
	 * 駆動開始前(cursor作成時)に失敗する——1件もpayloadを返さない。
	 */
	public void testCorruptedRecordLengthFailsBeforeIteration() throws Exception {
		try (SpillStore store = SpillStore.create()) {
			final byte[] first = { 1, 2, 3, 4 };
			store.append(first);
			store.append(new byte[] { 5, 6 });
			store.append(new byte[] { 7 });
			// record 1の長さフィールド(ヘッダ8 + [長さ4+payload4] の直後)を
			// 巨大値へ改竄する
			try (RandomAccessFile raf = new RandomAccessFile(store.dataFileForTest(), "rw")) {
				raf.seek(8 + 4 + first.length);
				raf.writeInt(Integer.MAX_VALUE);
			}
			try {
				store.cursor(0, 3);
				fail("破損データのcursor作成は失敗するはず");
			} catch (final IOException e) {
				// 期待どおり: 駆動開始前に失敗(部分再生しない)
			}
			// 破損recordを含まない範囲は引き続き読める
			assertTrue(java.util.Arrays.equals(first, store.cursor(0, 1).next()));
		}
	}

	/** 例外注入: ヘッダ(magic)破損もcursor作成時に失敗する。 */
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
	 * 例外注入: close時の一時ファイル削除失敗は黙殺せずWARNし、
	 * closeそのものは例外を伝播させない(§2.5)。
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
			store.close(); // 例外を投げないこと
			synchronized (warnings) {
				assertEquals("削除失敗はデータ・index両ファイル分WARNされるはず", 2, warnings.size());
			}
		} finally {
			logger.removeHandler(handler);
			// 注入deleterは削除しないため後始末
			Files.deleteIfExists(dataFile.toPath());
			Files.deleteIfExists(indexFile.toPath());
		}
	}
}
