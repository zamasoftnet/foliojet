package net.zamasoft.foliojet.layout.segment;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * length-prefixedなbyte recordの追記専用spillストアです(E-6増分2、
 * 2026-07-24新設。増分3b-2で{@link TextSpill}経由のtext payload spillに
 * production配線された)。
 *
 * <p>
 * codex設計相談(docs/consultations/consult-e6-spillable-tape-codex.md
 * §2.2〜§2.4)の最小部品: byte recordの追記(append→recordId)、
 * recordId→file offsetの固定長disk index(indexをheapへ載せると
 * O(E)へ逆戻りするためディスク上に置く)、範囲cursor(1レコードずつ
 * 読む)、closeでの一時ファイル削除。codecやレイアウト意味論は
 * 一切持たない——{@code byte[]}のみを扱う。
 * </p>
 *
 * <p>
 * <b>物理形式</b>: データファイルは先頭にmagic+版(各4byte)、以降は
 * {@code [int length][payload]}の連続。indexファイルはrecordId×8byte
 * 位置に、そのrecordのデータファイル内offset(8byte)を持つ固定長。
 * cursorは範囲の駆動開始前にindex/record長の整合(offsetの単調連続・
 * 長さの境界・末尾整合)を検査し、不正データでは1件もvisitせず
 * 失敗する(§2.4)。
 * </p>
 *
 * <p>
 * <b>寿命</b>: {@link AutoCloseable}。closeは冪等で、両一時ファイルを
 * 削除する。削除失敗は黙殺せずWARN(DirectSessionの複数パス一時
 * ファイル処理と同じ方針、§2.5)。一時ファイルは
 * {@code File.createTempFile}(java.io.tmpdir配下)で作る——
 * DirectSessionの既存流儀({@code File.createTempFile("copper", ...)})
 * に合わせる。
 * </p>
 *
 * <p>
 * スレッド安全ではない(単一レイアウトセッション内での利用が前提)。
 * </p>
 */
final class SpillStore implements AutoCloseable {
	private static final Logger LOG = Logger.getLogger(SpillStore.class.getName());

	/** データファイル先頭のmagic("FJSP")。 */
	static final int MAGIC = 0x464A5350;
	/** ストア物理形式の版。 */
	static final int STORE_VERSION = 1;

	private static final int HEADER_BYTES = 8;
	private static final int LENGTH_BYTES = 4;
	private static final int INDEX_ENTRY_BYTES = 8;

	/**
	 * 一時ファイル削除の注入点です(テスト専用——削除失敗時のWARN経路を
	 * プラットフォーム非依存に検証するため)。production利用は常に
	 * {@link #create()}の既定実装({@code Files.deleteIfExists})。
	 */
	interface TempFileDeleter {
		void delete(java.nio.file.Path path) throws IOException;
	}

	private final TempFileDeleter deleter;
	private final File dataFile;
	private final File indexFile;
	private final RandomAccessFile data;
	private final RandomAccessFile index;

	/** 追記済みrecord数(= 次に付与されるrecordId)。 */
	private long recordCount = 0;
	/** データファイルの論理長(次のrecordの書き込み位置)。 */
	private long dataLength = HEADER_BYTES;

	private boolean closed = false;

	/** 既定の削除実装({@code Files.deleteIfExists})で新しいストアを作ります。 */
	static SpillStore create() throws IOException {
		return new SpillStore(path -> Files.deleteIfExists(path));
	}

	SpillStore(final TempFileDeleter deleter) throws IOException {
		this.deleter = deleter;
		File dataFile = null, indexFile = null;
		RandomAccessFile data = null, index = null;
		try {
			dataFile = File.createTempFile("copper", ".spill");
			indexFile = File.createTempFile("copper", ".spillidx");
			data = new RandomAccessFile(dataFile, "rw");
			index = new RandomAccessFile(indexFile, "rw");
			data.writeInt(MAGIC);
			data.writeInt(STORE_VERSION);
		} catch (IOException | RuntimeException e) {
			// 構築失敗時も一時ファイル・ハンドルを残さない(§2.5)
			closeQuietly(data, dataFile);
			closeQuietly(index, indexFile);
			this.deleteQuietly(dataFile);
			this.deleteQuietly(indexFile);
			throw e;
		}
		this.dataFile = dataFile;
		this.indexFile = indexFile;
		this.data = data;
		this.index = index;
	}

	/**
	 * recordを追記し、そのrecordId(0起点の連番)を返します。
	 */
	long append(final byte[] record) throws IOException {
		this.ensureOpen();
		final long id = this.recordCount;
		final long offset = this.dataLength;
		this.data.seek(offset);
		this.data.writeInt(record.length);
		this.data.write(record);
		this.index.seek(id * INDEX_ENTRY_BYTES);
		this.index.writeLong(offset);
		this.dataLength = offset + LENGTH_BYTES + record.length;
		this.recordCount = id + 1;
		return id;
	}

	/** 追記済みrecord数(= 次に付与されるrecordId)を返します。 */
	long recordCount() {
		return this.recordCount;
	}

	/**
	 * recordIdのrecord payloadを直接読み出します(E-6増分3b-2: text
	 * payloadの単発読み——順次cursorを開くまでもないrecordId直接read)。
	 * 境界検査つきで、不正データは1バイトも返さず{@link IOException}で
	 * 失敗します(§2.4のクラッシュ型一貫性)。返す配列は毎回新しい。
	 */
	byte[] read(final long recordId) throws IOException {
		this.ensureOpen();
		if (recordId < 0 || recordId >= this.recordCount) {
			throw new IllegalArgumentException("record id out of bounds: " + recordId + ", count=" + this.recordCount);
		}
		final long offset = this.offsetOf(recordId);
		if (offset < HEADER_BYTES || offset + LENGTH_BYTES > this.dataLength) {
			throw new IOException("spill index corrupted (offset " + offset + " out of bounds) at record " + recordId);
		}
		this.data.seek(offset);
		final int length = this.data.readInt();
		if (length < 0 || offset + LENGTH_BYTES + length > this.dataLength) {
			throw new IOException("spill record corrupted (bad length " + length + ") at record " + recordId);
		}
		final byte[] payload = new byte[length];
		this.data.readFully(payload);
		return payload;
	}

	/**
	 * 半開範囲{@code [fromId, toIdExclusive)}の順次読み出しcursorを
	 * 返します。範囲の駆動開始前にヘッダ・index・record長の整合を検査し、
	 * 不正データでは1件も返さず{@link IOException}で失敗します(§2.4)。
	 * 同じストアから複数のcursorを独立に開けます。
	 */
	Cursor cursor(final long fromId, final long toIdExclusive) throws IOException {
		this.ensureOpen();
		if (fromId < 0 || toIdExclusive < fromId || toIdExclusive > this.recordCount) {
			throw new IllegalArgumentException(
					"cursor range out of bounds: [" + fromId + ", " + toIdExclusive + "), count=" + this.recordCount);
		}
		this.validateRange(fromId, toIdExclusive);
		return new Cursor(fromId, toIdExclusive);
	}

	/**
	 * 範囲の完全性検査です。offsetの単調連続・record長の境界・末尾整合を
	 * indexとlengthフィールドだけで検査する(payloadは読まない——検査は
	 * O(範囲)のseekで済ませ、heapには何も保持しない)。
	 */
	private void validateRange(final long fromId, final long toIdExclusive) throws IOException {
		this.data.seek(0);
		final int magic = this.data.readInt();
		if (magic != MAGIC) {
			throw new IOException("spill data file corrupted (bad magic 0x" + Integer.toHexString(magic) + "): "
					+ this.dataFile);
		}
		final int version = this.data.readInt();
		if (version != STORE_VERSION) {
			throw new IOException("unsupported spill store version " + version + ": " + this.dataFile);
		}
		long expectedNext = -1;
		for (long id = fromId; id < toIdExclusive; ++id) {
			final long offset = this.offsetOf(id);
			if (offset < HEADER_BYTES || offset + LENGTH_BYTES > this.dataLength) {
				throw new IOException("spill index corrupted (offset " + offset + " out of bounds) at record " + id);
			}
			if (expectedNext >= 0 && offset != expectedNext) {
				throw new IOException("spill index corrupted (offset discontinuity: expected " + expectedNext
						+ ", got " + offset + ") at record " + id);
			}
			this.data.seek(offset);
			final int length = this.data.readInt();
			if (length < 0 || offset + LENGTH_BYTES + length > this.dataLength) {
				throw new IOException("spill record corrupted (bad length " + length + ") at record " + id);
			}
			expectedNext = offset + LENGTH_BYTES + length;
		}
		if (expectedNext >= 0) {
			// 範囲末尾の次の境界も整合していること(末尾recordの長さ改竄検出)
			final long bound = toIdExclusive == this.recordCount ? this.dataLength : this.offsetOf(toIdExclusive);
			if (expectedNext != bound) {
				throw new IOException("spill data corrupted (dangling tail: expected next offset " + bound + ", got "
						+ expectedNext + ")");
			}
		}
	}

	private long offsetOf(final long id) throws IOException {
		this.index.seek(id * INDEX_ENTRY_BYTES);
		return this.index.readLong();
	}

	/**
	 * 範囲{@code [fromId, toIdExclusive)}を1レコードずつ読む順次cursor
	 * です。消費位置はcursorが所有する(ストア側は位置を持たない——
	 * 既存{@code SegmentReplaySession}と同じ契約)。ファイルハンドルは
	 * ストア共有のため、cursor自体はclose不要。
	 */
	final class Cursor {
		private long nextId;
		private final long toIdExclusive;

		private Cursor(final long fromId, final long toIdExclusive) {
			this.nextId = fromId;
			this.toIdExclusive = toIdExclusive;
		}

		boolean hasNext() {
			return this.nextId < this.toIdExclusive;
		}

		/** 次のrecordのpayloadを返します。 */
		byte[] next() throws IOException {
			if (!this.hasNext()) {
				throw new NoSuchElementException("cursor exhausted at " + this.nextId);
			}
			SpillStore.this.ensureOpen();
			final long offset = SpillStore.this.offsetOf(this.nextId);
			SpillStore.this.data.seek(offset);
			final int length = SpillStore.this.data.readInt();
			// cursor作成後の破損に対する保険(作成時に全数検査済みだが、
			// 巨大確保やEOFの前にもう一度だけ境界を見る)
			if (length < 0 || offset + LENGTH_BYTES + length > SpillStore.this.dataLength) {
				throw new IOException("spill record corrupted (bad length " + length + ") at record " + this.nextId);
			}
			final byte[] payload = new byte[length];
			SpillStore.this.data.readFully(payload);
			++this.nextId;
			return payload;
		}
	}

	private void ensureOpen() {
		if (this.closed) {
			throw new IllegalStateException("SpillStore is closed: " + this.dataFile);
		}
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		closeQuietly(this.data, this.dataFile);
		closeQuietly(this.index, this.indexFile);
		this.deleteQuietly(this.dataFile);
		this.deleteQuietly(this.indexFile);
	}

	private static void closeQuietly(final RandomAccessFile file, final File name) {
		if (file == null) {
			return;
		}
		try {
			file.close();
		} catch (IOException | RuntimeException e) {
			LOG.log(Level.WARNING, "Failed to close temporary spill file: " + name, e);
		}
	}

	/** 削除失敗を黙殺しない(WARN。DirectSessionの一時ファイル処理と同じ方針)。 */
	private void deleteQuietly(final File file) {
		if (file == null) {
			return;
		}
		try {
			this.deleter.delete(file.toPath());
		} catch (IOException | RuntimeException e) {
			LOG.log(Level.WARNING, "Failed to delete temporary spill file: " + file, e);
		}
	}

	File dataFileForTest() {
		return this.dataFile;
	}

	File indexFileForTest() {
		return this.indexFile;
	}
}
