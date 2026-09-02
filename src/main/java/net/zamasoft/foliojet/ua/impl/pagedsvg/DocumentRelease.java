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
 * EPUBの項目(子UA)の結果とメッセージを、<b>spine順に</b>解放する段です
 * (2026-09-02)。
 *
 * <p>
 * 項目は並列に組まれるが、結果の受け口(CTIPの接続・結果集合)は1本で
 * 順序を持つ。そこで<b>先頭の未完了項目だけが直接書き</b>、後続の項目は
 * 自分の一時ファイルへ控える。先頭が終わると次の項目の控えを流し、その
 * 項目が直接書く番になる。逐次(並列度1)でも並列でも、受け手に届く列は
 * 同じである。
 * </p>
 *
 * <p>
 * ページ番号のメッセージ({@code page-number:N})は項目内の番号で溜まる。
 * 流す瞬間には前の項目の合計が確定しているので、そこで足す。受け手には
 * 今日とまったく同じ列が届き、プロトコルもクライアントも変えない
 * (設計 §4)。
 * </p>
 *
 * <p>
 * <b>錠の順序。</b>全体の錠({@code this})は項目の完了と昇格で取る。項目の錠
 * ({@code Unit.lock})は、その項目が結果1件を書いている間と、昇格で控えを
 * 流している間に取る。子のスレッドは自分の項目の錠しか取らず、全体の錠は
 * 完了の通知({@link Unit#done})でだけ取る(そのとき項目の錠は持っていない)。
 * 昇格は全体の錠を持ったまま次の項目の錠を取る。逆順は無いので詰まらない。
 * </p>
 */
final class DocumentRelease {
	private static final Logger LOG = Logger.getLogger(DocumentRelease.class.getName());

	private static final byte RECORD_RESULT = 1;
	private static final byte RECORD_MESSAGE = 2;

	private final ResultSink out;
	private final MessageHandler messages;
	private final List<Unit> units = new ArrayList<>();
	/** いま直接書いている(または次に直接書く)項目の位置。全部解放済みなら{@code units.size()}。 */
	private int head = 0;
	/** 解放が済んだ項目のページ数の合計。 */
	private int releasedPages = 0;

	DocumentRelease(final ResultSink out, final MessageHandler messages) {
		this.out = out;
		this.messages = messages;
	}

	/** 項目を1つ開きます。呼び出し順が解放順。 */
	synchronized Unit open(final String prefix) {
		final Unit unit = new Unit(prefix);
		this.units.add(unit);
		if (this.units.size() - 1 == this.head) {
			// 前がすべて済んでいるので、この項目は最初から直接書く
			unit.direct = true;
			unit.pageOffset = this.releasedPages;
		}
		return unit;
	}

	/** 解放が済んだページ数(すべての項目が済んだ後は総ページ数)。 */
	synchronized int releasedPages() {
		return this.releasedPages;
	}

	/** 項目の完了。先頭なら次を昇格させる。 */
	private synchronized void done(final Unit unit, final int pageCount) throws IOException {
		unit.done = true;
		unit.pageCount = pageCount;
		if (this.head >= this.units.size() || this.units.get(this.head) != unit) {
			// 先頭ではない。先頭が済んだときに控えを流す
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
					// ここからは直接書く
					return;
				}
				this.releasedPages += next.pageCount;
			} finally {
				next.lock.unlock();
			}
			++this.head;
		}
	}

	/** 残った控えを片付けます(中断時)。 */
	synchronized void close() {
		for (final Unit unit : this.units) {
			unit.discardSpill();
		}
	}

	/** 項目1つの受け口。子UAの結果とメッセージはここを通る。 */
	final class Unit {
		final String prefix;
		final ReentrantLock lock = new ReentrantLock();
		/** 直接書く番か。偽なら控える。 */
		private boolean direct;
		private boolean done;
		private int pageCount;
		/** この項目の前までのページ数。ページ番号のメッセージに足す。 */
		private int pageOffset;
		private File spillFile;
		private DataOutputStream spill;

		Unit(final String prefix) {
			this.prefix = prefix;
		}

		/** 結果1件を開きます。閉じるまでこの項目の錠を持ちます。 */
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

		/** メッセージ1件。直接書く番ならそのまま(ページ番号は足して)、そうでなければ控える。 */
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

		/** 項目の完了。{@code pageCount}はこの項目のページ数。 */
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
		 * 控えへ結果1件を書き始めます。長さは前もって分からないので、
		 * 塊ごとの長さで区切り、負の長さで終える。
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

		/** 控えを受け口へ流します。項目の錠を持って呼ぶこと。 */
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

	/** メッセージを受け手へ渡します。ページ番号には項目の前までのページ数を足す。 */
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
