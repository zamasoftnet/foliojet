package net.zamasoft.foliojet.layout.text.bidi;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * 段落単位の双方向解決のための論理イベントの保持器です(2026-09-04、
 * bidi-isolation-design.md §2-1〜§2-3。batch A-1a では model と試験だけ——
 * レイアウトへの配線は A-1b、所有者は DocumentBuilder 側の順序付き event queue)。
 *
 * <p>
 * 文字・inline の開始/終了・atomic inline・強制段落区切り・レイアウト barrier
 * (float/absolute 等)を論理順に受け、{@link java.text.Bidi} へ渡す合成 UTF-16 列を
 * 組む。普通の inline 境界は何も出さず UBA に透明、{@code unicode-bidi} を持つ
	 * inline だけ制御文字で囲む({@link BidiResolver})。atomic inline は通常 U+FFFC 1 個
	 * (embed/override の replaced inline だけ要素方向の strong 代替文字)。
 * 解決後は各イベントの合成列上の範囲からレベルを引ける。
 * </p>
 */
public final class BidiParagraphBuffer {
	/** イベントの種類。 */
	public enum Kind {
		TEXT, INLINE_START, INLINE_END, ATOMIC, PARAGRAPH_BREAK, BARRIER
	}

	/**
	 * 論理イベント。{@code start}/{@code limit} は合成列上の範囲(制御文字を
	 * 含まない。TEXT/ATOMIC 以外は幅 0)。
	 */
	public static class Event {
		private final Kind kind;
		private final int start, limit;
		private final byte direction, unicodeBidi;
		private final Object payload;

		public Event(final Kind kind, final int start, final int limit, final byte direction,
				final byte unicodeBidi, final Object payload) {
			this.kind = kind;
			this.start = start;
			this.limit = limit;
			this.direction = direction;
			this.unicodeBidi = unicodeBidi;
			this.payload = payload;
		}

		public Kind kind() { return this.kind; }
		public int start() { return this.start; }
		public int limit() { return this.limit; }
		public byte direction() { return this.direction; }
		public byte unicodeBidi() { return this.unicodeBidi; }
		public Object payload() { return this.payload; }

		public int length() {
			return this.limit - this.start;
		}
	}

	/** 段落跨ぎで開き直す inline の不変 recipe。 */
	public record OpenInline(byte direction, byte unicodeBidi, Object payload) {
	}

	/** 強制段落境界と、次の buffer へ渡す open-inline snapshot。 */
	public static final class ParagraphBreak extends Event {
		private final List<OpenInline> openInlines;

		ParagraphBreak(final int start, final int limit, final Object payload, final List<OpenInline> openInlines) {
			super(Kind.PARAGRAPH_BREAK, start, limit, (byte) 0, (byte) 0, payload);
			this.openInlines = List.copyOf(openInlines);
		}

		public Event event() { return this; }
		public List<OpenInline> openInlines() { return this.openInlines; }
	}

	private final StringBuilder synthetic = new StringBuilder();
	private final List<Event> events = new ArrayList<>();
	private final List<OpenInline> openInlines = new ArrayList<>();
	private final BitSet syntheticControls = new BitSet();
	private final int baseDirectionFlag;
	private final byte blockUnicodeBidi;
	private Bidi bidi;
	private boolean broken;

	/**
	 * @param blockDirection   段落を含むブロックの {@code direction}
	 * @param blockUnicodeBidi 同ブロックの {@code unicode-bidi}(plaintext なら自動判定)
	 */
	public BidiParagraphBuffer(final byte blockDirection, final byte blockUnicodeBidi) {
		this.baseDirectionFlag = BidiResolver.baseDirectionFlag(blockDirection, blockUnicodeBidi);
		this.blockUnicodeBidi = blockUnicodeBidi;
		this.appendSynthetic(BidiResolver.rootOpeningControls(blockDirection, blockUnicodeBidi));
	}

	/** 文字列(正規化・text-transform 後)。 */
	public Event addText(final CharSequence text, final Object payload) {
		this.beforeAdd();
		final int start = this.synthetic.length();
		this.synthetic.append(text);
		return this.add(new Event(Kind.TEXT, start, this.synthetic.length(), (byte) 0, (byte) 0, payload));
	}

	/** inline の開始。{@code unicode-bidi} に応じた制御文字を挿入する。 */
	public Event inlineStart(final byte direction, final byte unicodeBidi, final Object payload) {
		this.beforeAdd();
		this.appendSynthetic(BidiResolver.openingControls(direction, unicodeBidi));
		this.openInlines.add(new OpenInline(direction, unicodeBidi, payload));
		final int at = this.synthetic.length();
		return this.add(new Event(Kind.INLINE_START, at, at, direction, unicodeBidi, payload));
	}

	/** inline の終了({@link #inlineStart}と対)。 */
	public Event inlineEnd(final Object payload) {
		this.beforeAdd();
		if (this.openInlines.isEmpty()) {
			throw new IllegalStateException("inlineEnd without inlineStart");
		}
		final OpenInline open = this.openInlines.remove(this.openInlines.size() - 1);
		final int at = this.synthetic.length();
		this.appendSynthetic(BidiResolver.closingControls(open.unicodeBidi()));
		return this.add(new Event(Kind.INLINE_END, at, at, open.direction(), open.unicodeBidi(), payload));
	}

	/** atomic inline(置換要素・inline-block・ruby・warichu)。通常 U+FFFC 1 個。 */
	public Event atomic(final Object payload) {
		return this.atomic(payload, (byte) 0, net.zamasoft.foliojet.css.value.UnicodeBidiValue.NORMAL);
	}

	/**
	 * atomic inline。embed/override の replaced inline は CSS Writing Modes
	 * §2.4.3 に従い要素方向の strong 代替文字で解決する。
	 */
	public Event atomic(final Object payload, final byte direction, final byte unicodeBidi) {
		this.beforeAdd();
		final int start = this.synthetic.length();
		this.synthetic.append(BidiResolver.atomicCharacter(direction, unicodeBidi));
		return this.add(new Event(Kind.ATOMIC, start, start + 1, direction, unicodeBidi, payload));
	}

	/**
	 * 強制段落区切り(bidi type B)。開いている inline の制御はここで一度閉じ、
	 * 次の段落で開き直す(css-writing-modes-3 §2.4)。開き直しは返した
	 * snapshot を呼び出し側が新しい buffer へ渡して行う。
	 */
	public ParagraphBreak paragraphBreak(final Object payload) {
		this.beforeAdd();
		final List<OpenInline> snapshot = List.copyOf(this.openInlines);
		for (int i = this.openInlines.size() - 1; i >= 0; --i) {
			this.appendSynthetic(BidiResolver.closingControls(this.openInlines.get(i).unicodeBidi()));
		}
		this.appendSynthetic(BidiResolver.rootClosingControls(this.blockUnicodeBidi));
		final int start = this.synthetic.length();
		this.synthetic.append(BidiResolver.PARAGRAPH_SEPARATOR);
		final ParagraphBreak event = new ParagraphBreak(start, start + 1, payload, snapshot);
		this.add(event);
		this.broken = true;
		return event;
	}

	/** 前の buffer の {@link ParagraphBreak#openInlines()} を論理順に開き直す。 */
	public void reopen(final List<OpenInline> snapshot) {
		for (final OpenInline open : snapshot) {
			this.inlineStart(open.direction(), open.unicodeBidi(), open.payload());
		}
	}

	/** レイアウトの順序境界(float・absolute・親 builder への bound 追加等)。合成列には出ない。 */
	public Event barrier(final Object payload) {
		this.beforeAdd();
		final int at = this.synthetic.length();
		return this.add(new Event(Kind.BARRIER, at, at, (byte) 0, (byte) 0, payload));
	}

	private Event add(final Event event) {
		this.events.add(event);
		return event;
	}

	private void beforeAdd() {
		if (this.broken) {
			throw new IllegalStateException("paragraph buffer is closed after paragraphBreak");
		}
		this.bidi = null;
	}

	private void appendSynthetic(final String text) {
		if (text.isEmpty()) {
			return;
		}
		final int start = this.synthetic.length();
		this.synthetic.append(text);
		this.syntheticControls.set(start, this.synthetic.length());
	}

	public List<Event> events() {
		return Collections.unmodifiableList(this.events);
	}

	/** 合成列(制御文字込み)。試験・診断用。 */
	public String synthetic() {
		return this.synthetic.toString();
	}

	public int length() {
		return this.synthetic.length();
	}

	public boolean isEmpty() {
		return this.events.isEmpty();
	}

	/** CSS が合成した制御文字の索引か。本文由来の同値文字は false。 */
	public boolean isSyntheticControl(final int index) {
		if (index < 0 || index >= this.synthetic.length()) {
			throw new IndexOutOfBoundsException(index);
		}
		return this.syntheticControls.get(index);
	}

	/** 段落全体を一度だけ解決した {@link Bidi}(遅延、追加のたびに作り直す)。 */
	public Bidi resolve() {
		if (this.bidi == null) {
			final String closeRoot = this.broken ? "" : BidiResolver.rootClosingControls(this.blockUnicodeBidi);
			this.bidi = new Bidi(this.synthetic.toString() + closeRoot, this.baseDirectionFlag);
		}
		return this.bidi;
	}

	/** 段落レベル(0=LTR、1=RTL)。plaintext の自動判定の結果もここに出る。 */
	public int paragraphLevel() {
		return this.resolve().getBaseLevel();
	}

	/** 合成列の索引 {@code index} の埋め込みレベル。 */
	public int levelAt(final int index) {
		return this.resolve().getLevelAt(index);
	}

	/**
	 * 行 {@code [start, limit)} の {@link Bidi}(UAX #9 L1: 行末の空白を段落レベルへ)。
	 * 行分割後に、その行の視覚順を求めるために使う。
	 */
	public Bidi lineBidi(final int start, final int limit) {
		return this.resolve().createLineBidi(start, limit);
	}

	/** 段落に RTL の文字か右→左の基準があるか(なければ並べ替え不要)。 */
	public boolean requiresVisualReordering() {
		return !this.resolve().isLeftToRight();
	}

	/** @deprecated {@link #requiresVisualReordering()} を使う。 */
	@Deprecated
	public boolean isMixed() {
		return this.requiresVisualReordering();
	}
}
