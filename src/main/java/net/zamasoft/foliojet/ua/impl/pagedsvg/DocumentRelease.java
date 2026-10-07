package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import jp.cssj.cti2.message.MessageHandler;
import net.zamasoft.foliojet.message.MessageCodes;

/**
 * A stage that releases results and messages from EPUB items (child UAs) <b>in spine order</b>
 * (2026-09-02).
 *
 * <p>
 * Items undergo layout in parallel, but the result sink (CTIP connection or result set) is a single,
 * ordered channel. Thus <b>only the first unfinished item writes directly</b>; later items spool
 * to their own temporary files. When the first finishes, flush the next item's spool and let
 * that item write directly. The consumer receives the same sequence with sequential execution
 * (parallelism 1) or parallel execution.
 * </p>
 *
 * <p>
 * Page-number messages ({@code page-number:N}) accumulate with item-local numbers.
 * When released, the total for previous items is known, so add it then.
 * The consumer receives exactly the same sequence as before, without protocol or client changes
 * (design §4).
 * </p>
 *
 * <p>
 * <b>Lock order.</b> Acquire the global lock ({@code this}) for item completion and promotion.
 * Acquire the item lock ({@code Unit.lock}) while writing one result for that item or flushing
 * its spool during promotion. Child threads acquire only their own item lock; they acquire
 * the global lock only to notify completion ({@link Unit#done}), without holding the item lock.
 * Promotion acquires the next item's lock while holding the global lock.
 * There is no reverse order, so no deadlock.
 * </p>
 */
final class DocumentRelease {
	private static final Logger LOG = Logger.getLogger(DocumentRelease.class.getName());

	private static final byte RECORD_RESULT = 1;
	private static final byte RECORD_MESSAGE = 2;

	private final ResultSink out;
	private final MessageHandler messages;
	private final List<Unit> units = new ArrayList<>();
	/** Index of the item writing directly now (or next). {@code units.size()} if all items have been released. */
	private int head = 0;
	/** Total page count of released items. */
	private int releasedPages = 0;

	DocumentRelease(final ResultSink out, final MessageHandler messages) {
		this.out = out;
		this.messages = messages;
	}

	/** Opens an item. Call order determines release order. */
	synchronized Unit open(final String prefix) {
		final Unit unit = new Unit(prefix);
		this.units.add(unit);
		if (this.units.size() - 1 == this.head) {
			// All preceding items have finished, so this item writes directly from the start.
			unit.direct = true;
			unit.pageOffset = this.releasedPages;
		}
		return unit;
	}

	/** Number of released pages (the total page count after all items finish). */
	synchronized int releasedPages() {
		return this.releasedPages;
	}

	/** Item completion. If it is the first item, promote the next. */
	private synchronized void done(final Unit unit, final int pageCount) throws IOException {
		unit.done = true;
		unit.pageCount = pageCount;
		if (this.head >= this.units.size() || this.units.get(this.head) != unit) {
			// Not the first item. Flush its spool when the first finishes.
			return;
		}
		this.releasedPages += pageCount;
		++this.head;
		while (this.head < this.units.size()) {
			final Unit next = this.units.get(this.head);
			next.lock.lock();
			try {
				next.pageOffset = this.releasedPages;
				next.replay();
				next.direct = true;
				if (!next.done) {
					// Write directly from here.
					return;
				}
				this.releasedPages += next.pageCount;
			} finally {
				next.lock.unlock();
			}
			++this.head;
		}
	}

	/** Cleans up remaining spools (on abort). */
	synchronized void close() {
		for (final Unit unit : this.units) {
			unit.discardSpill();
		}
	}

	/** Sink for one item. The child UA's results and messages pass through here. */
	final class Unit {
		final String prefix;
		final ReentrantLock lock = new ReentrantLock();
		/** Whether it is this item's turn to write directly. Spool if false. */
		private boolean direct;
		private boolean done;
		private int pageCount;
		/** Page count preceding this item. Add it to page-number messages. */
		private int pageOffset;
		private File spillFile;
		private DataOutputStream spill;

		Unit(final String prefix) {
			this.prefix = prefix;
		}

		/** Opens one result. Holds this item's lock until it closes. */
		OutputStream open(final String uri, final String mimeType) throws IOException {
			this.lock.lock();
			try {
				final OutputStream raw = this.direct ? DocumentRelease.this.out.open(this.prefix + uri, mimeType)
						: this.openSpillResult(this.prefix + uri, mimeType);
				return new OutputStream() {
					private boolean closed;

					@Override
					public void write(final int b) throws IOException {
						raw.write(b);
					}

					@Override
					public void write(final byte[] b, final int off, final int len) throws IOException {
						raw.write(b, off, len);
					}

					@Override
					public void close() throws IOException {
						if (this.closed) {
							return;
						}
						this.closed = true;
						try {
							raw.close();
						} finally {
							Unit.this.lock.unlock();
						}
					}
				};
			} catch (final IOException | RuntimeException | Error e) {
				this.lock.unlock();
				throw e;
			}
		}

		/** One message. Forward it if writing directly (adding the page offset); otherwise spool it. */
		void message(final short code, final String[] args, final String mes) {
			this.lock.lock();
			try {
				if (this.direct) {
					DocumentRelease.this.forward(this, code, args, mes);
					return;
				}
				final DataOutputStream out = this.requireSpill();
				out.writeByte(RECORD_MESSAGE);
				out.writeShort(code);
				if (args == null) {
					out.writeInt(-1);
				} else {
					out.writeInt(args.length);
					for (final String arg : args) {
						writeText(out, arg);
					}
				}
				writeText(out, mes);
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			} finally {
				this.lock.unlock();
			}
		}

		/** Item completion. {@code pageCount} is this item's page count. */
		void done(final int pageCount) throws IOException {
			DocumentRelease.this.done(this, pageCount);
		}

		private DataOutputStream requireSpill() throws IOException {
			if (this.spill == null) {
				this.spillFile = File.createTempFile("copper-epub-item", ".spill");
				this.spill = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(this.spillFile), 1 << 16));
			}
			return this.spill;
		}

		/**
		 * Starts writing one result to the spool. Its length is unknown in advance,
		 * so delimit chunks by their lengths and terminate with a negative length.
		 */
		private OutputStream openSpillResult(final String uri, final String mimeType) throws IOException {
			final DataOutputStream out = this.requireSpill();
			out.writeByte(RECORD_RESULT);
			writeText(out, uri);
			writeText(out, mimeType);
			return new OutputStream() {
				@Override
				public void write(final int b) throws IOException {
					out.writeInt(1);
					out.writeByte(b);
				}

				@Override
				public void write(final byte[] b, final int off, final int len) throws IOException {
					if (len <= 0) {
						return;
					}
					out.writeInt(len);
					out.write(b, off, len);
				}

				@Override
				public void close() throws IOException {
					out.writeInt(-1);
				}
			};
		}

		/** Flushes the spool to the sink. Call with the item lock held. */
		private void replay() throws IOException {
			if (this.spill == null) {
				return;
			}
			this.spill.close();
			this.spill = null;
			try (DataInputStream in = new DataInputStream(
					new BufferedInputStream(new FileInputStream(this.spillFile), 1 << 16))) {
				final byte[] buffer = new byte[1 << 16];
				for (;;) {
					final int kind;
					try {
						kind = in.readByte();
					} catch (final EOFException e) {
						break;
					}
					switch (kind) {
					case RECORD_RESULT -> {
						final String uri = readText(in);
						final String mimeType = readText(in);
						try (OutputStream out = DocumentRelease.this.out.open(uri, mimeType)) {
							for (int len = in.readInt(); len >= 0; len = in.readInt()) {
								int remaining = len;
								while (remaining > 0) {
									final int n = in.read(buffer, 0, Math.min(buffer.length, remaining));
									if (n < 0) {
										throw new EOFException("truncated spill: " + uri);
									}
									out.write(buffer, 0, n);
									remaining -= n;
								}
							}
						}
					}
					case RECORD_MESSAGE -> {
						final short code = in.readShort();
						final int count = in.readInt();
						String[] args = null;
						if (count >= 0) {
							args = new String[count];
							for (int i = 0; i < count; ++i) {
								args[i] = readText(in);
							}
						}
						final String mes = readText(in);
						DocumentRelease.this.forward(this, code, args, mes);
					}
					default -> throw new IOException("corrupt spill record kind " + kind);
					}
				}
			} finally {
				this.discardSpill();
			}
		}

		private void discardSpill() {
			if (this.spill != null) {
				try {
					this.spill.close();
				} catch (final IOException e) {
					LOG.log(Level.FINE, "closing spill", e);
				}
				this.spill = null;
			}
			if (this.spillFile != null) {
				if (!this.spillFile.delete() && this.spillFile.exists()) {
					LOG.log(Level.WARNING, "Failed to delete temporary file: " + this.spillFile);
				}
				this.spillFile = null;
			}
		}
	}

	/** Passes a message to the consumer. Adds the count of preceding pages to page numbers. */
	private void forward(final Unit unit, final short code, final String[] args, final String mes) {
		if (this.messages == null) {
			return;
		}
		String[] forwarded = args;
		if (code == MessageCodes.INFO_PAGE_NUMBER && args != null && args.length > 0 && unit.pageOffset != 0) {
			try {
				forwarded = args.clone();
				forwarded[0] = String.valueOf(Integer.parseInt(args[0]) + unit.pageOffset);
			} catch (final NumberFormatException e) {
				forwarded = args;
			}
		}
		this.messages.message(code, forwarded, mes);
	}

	private static void writeText(final DataOutputStream out, final String text) throws IOException {
		if (text == null) {
			out.writeInt(-1);
			return;
		}
		final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		out.writeInt(bytes.length);
		out.write(bytes);
	}

	private static String readText(final DataInputStream in) throws IOException {
		final int length = in.readInt();
		if (length < 0) {
			return null;
		}
		final byte[] bytes = new byte[length];
		in.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}
}
