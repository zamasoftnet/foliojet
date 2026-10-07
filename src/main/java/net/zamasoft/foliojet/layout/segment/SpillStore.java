package net.zamasoft.foliojet.layout.segment;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An append-only spill store for length-prefixed byte records (E-6 increment 2, introduced on
 * 2026-07-24; wired into production for text payload spilling through {@link TextSpill} in increment 3b-2).
 *
 * <p>
 * The minimal component from the codex design consultation (design consultation §2.2–§2.4):
 * appending byte records (append→recordId), a fixed-length disk index mapping recordId→file offset
 * (kept on disk because an on-heap index would return to O(E)), range cursors (one record at a time),
 * and temporary-file deletion on close. Has no codec or layout semantics; handles only {@code byte[]}.
 * </p>
 *
 * <p>
 * <b>Physical format</b>: the data file starts with a magic value and version (4 bytes each),
 * followed by a sequence of {@code [int length][payload]}. The fixed-length index file stores each
 * record's data-file offset (8 bytes) at recordId×8 bytes.
 * Before executing a range, the cursor validates index/record-length consistency (monotonic,
 * contiguous offsets, length bounds, and end consistency). Invalid data causes failure before
 * visiting any record (§2.4).
 * </p>
 *
 * <p>
 * <b>Lifetime</b>: {@link AutoCloseable}. Close is idempotent and deletes both temporary files.
 * Deletion failures produce WARN messages instead of being ignored (the same policy as DirectSession
 * multi-pass temporary-file handling, §2.5). Creates temporary files with {@code File.createTempFile}
 * (under java.io.tmpdir), following the existing DirectSession convention
 * ({@code File.createTempFile("copper", ...)}).
 * </p>
 *
 * <p>
 * Not thread-safe (intended for use within a single layout session).
 * </p>
 */
final class SpillStore implements AutoCloseable {
	private static final Logger LOG = Logger.getLogger(SpillStore.class.getName());

	/** Magic value ("FJSP") at the start of the data file. */
	static final int MAGIC = 0x464A5350;
	/** Version of the store's physical format. */
	static final int STORE_VERSION = 1;

	private static final int HEADER_BYTES = 8;
	private static final int LENGTH_BYTES = 4;
	private static final int INDEX_ENTRY_BYTES = 8;

	/**
	 * Injection point for temporary-file deletion (tests only, to verify the WARN path on deletion
	 * failure independently of the platform). Production always uses the default implementation
	 * of {@link #create()} ({@code Files.deleteIfExists}).
	 */
	interface TempFileDeleter {
		void delete(java.nio.file.Path path) throws IOException;
	}

	private final TempFileDeleter deleter;
	private final File dataFile;
	private final File indexFile;
	private final RandomAccessFile data;
	private final RandomAccessFile index;

	/** Number of appended records (= the next recordId to assign). */
	private long recordCount = 0;
	/** Logical length of the data file (write position of the next record). */
	private long dataLength = HEADER_BYTES;

	private boolean closed = false;

	/** Creates a new store with the default deletion implementation ({@code Files.deleteIfExists}). */
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
			// Leave no temporary files or handles behind even if construction fails (§2.5).
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
	 * Appends a record and returns its recordId (a zero-based sequence number).
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

	/** Returns the number of appended records (= the next recordId to assign). */
	long recordCount() {
		return this.recordCount;
	}

	/**
	 * Reads the payload of recordId directly (E-6 increment 3b-2: a single text-payload read,
	 * accessing recordId directly without opening a sequential cursor).
	 * Checks bounds and fails with {@link IOException} without returning a single byte of invalid data
	 * (the crash-style consistency of §2.4). Returns a fresh array each time.
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
	 * Returns a sequential-read cursor over the half-open range {@code [fromId, toIdExclusive)}.
	 * Validates header, index, and record-length consistency before executing the range;
	 * invalid data causes {@link IOException} before returning any record (§2.4).
	 * Multiple cursors can be opened independently on the same store.
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
	 * Checks range integrity: monotonic, contiguous offsets, record-length bounds, and end consistency,
	 * using only the index and length fields (does not read payloads; validation takes O(range) seeks
	 * and retains nothing on the heap).
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
			// Also check the boundary after the range end (detect tampering with the last record's length).
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
	 * A sequential cursor that reads the range {@code [fromId, toIdExclusive)} one record at a time.
	 * The cursor owns the read position (the store does not hold it).
	 * File handles are shared with the store, so the cursor itself needs no close.
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

		/** Returns the payload of the next record. */
		byte[] next() throws IOException {
			if (!this.hasNext()) {
				throw new NoSuchElementException("cursor exhausted at " + this.nextId);
			}
			SpillStore.this.ensureOpen();
			final long offset = SpillStore.this.offsetOf(this.nextId);
			SpillStore.this.data.seek(offset);
			final int length = SpillStore.this.data.readInt();
			// Guard against corruption after cursor creation (all records were checked at creation,
			// but check bounds once more before a huge allocation or EOF).
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

	/** Do not ignore deletion failures (WARN, following DirectSession's temporary-file handling policy). */
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
