package net.zamasoft.foliojet.css.style;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.ua.CounterScope;

/**
 * スタイルイベント列(セグメント)です。
 *
 * <p>
 * レイアウト前・スタイル適用後のソースイベント(Start/Chars/End の3語彙)を
 * 保持し、StyleBuilder へ再生(restyle)できます。従来は run-in と
 * ページ内容(PageContent)の再生成専用バッファ(旧名 StyleBuffer)でしたが、
 * M6 では本流の再レイアウト源に一般化します(ARCHITECTURE.md §5.4):
 * 改ページ時の再開を「構築済みボックスの再生」から「セグメント+
 * BreakToken からの再駆動」へ置き換えるための土台です。
 * </p>
 *
 * <p>
 * <b>窓の不変条件</b>: 本流の記録はページ境界ごとに
 * {@link #trimToOpenElements()} で刈り込まれ、保持量は
 * O(現在ページのイベント+開いている要素スタック)に保たれます
 * (ストリーミング不変条件)。M6b では刈り込みの起点が BreakToken
 * (切断位置に対応するイベント位置)に精密化されます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class Segment {
	sealed interface Item permits Start, Chars, End {
	}

	/**
	 * 要素の開始です。counters は開始時点のカウンタ状態
	 * (ページカウンタ以外、なければ null)で、この位置からの再駆動時に
	 * 巻き戻すために保持します(M6b)。
	 */
	record Start(CSSStyle style, CounterScope[] counters) implements Item {
	}

	record Chars(int charOffset, char[] ch) implements Item {
	}

	/**
	 * 要素の終了です。counters は終了時点のカウンタ状態(M6b、
	 * 本流のみ。閉じた兄弟要素のカウンタ効果を尾部再生の巻き戻しで
	 * 拾うために保持します)。
	 */
	record End(CSSStyle style, CounterScope[] counters) implements Item {
	}

	protected final List<Item> items = new ArrayList<Item>();

	/**
	 * 直前の刈り込みで退避した旧窓です(M6b)。改ページの残余再生は
	 * 旧窓(切断前のイベントとアンカーが対応する)から読み、再記録は
	 * 現行窓へ追記されます。次の刈り込みで破棄されます。
	 */
	protected List<Item> previousItems = null;

	/**
	 * 旧窓の世代(= 現行世代 - 1)です。
	 */
	protected int previousEpoch = -1;

	protected int depth = 0;

	/**
	 * 窓の世代です。刈り込みのたびに進み、旧世代のソースアンカー
	 * (Params.sourceEpoch/sourceIndex)を無効として識別できます。
	 */
	protected int epoch = 0;

	/**
	 * 窓の世代を返します。
	 */
	public int getEpoch() {
		return this.epoch;
	}

	/**
	 * 旧窓(直前の刈り込みで退避した窓)の世代を返します。
	 */
	public int getPreviousEpoch() {
		return this.previousEpoch;
	}

	public int getDepth() {
		return this.depth;
	}

	/**
	 * 保持しているイベント数を返します。
	 */
	public int size() {
		return this.items.size();
	}

	/**
	 * 旧窓のイベント数を返します(なければ 0)。
	 */
	public int previousSize() {
		return this.previousItems == null ? 0 : this.previousItems.size();
	}

	public void startStyle(CSSStyle style) {
		this.startStyle(style, null);
	}

	/**
	 * カウンタスナップショット付きで開始イベントを記録します(M6b)。
	 */
	public void startStyle(CSSStyle style, CounterScope[] counters) {
		this.items.add(new Start(style, counters));
		++this.depth;
	}

	public void characters(int offset, char[] ch, int off, int len) {
		char[] chars = new char[len];
		System.arraycopy(ch, off, chars, 0, len);
		this.items.add(new Chars(offset, chars));
	}

	public void endStyle(CSSStyle style) {
		this.endStyle(style, null);
	}

	/**
	 * カウンタスナップショット付きで終了イベントを記録します(M6b)。
	 */
	public void endStyle(CSSStyle style, CounterScope[] counters) {
		this.items.add(new End(style, counters));
		--this.depth;
	}

	/**
	 * 閉じられていない(開いている)要素の Start イベントだけを残して
	 * 刈り込みます。ページ境界での窓の更新に使います(M6a)。
	 * depth は変化しません。
	 */
	public void trimToOpenElements() {
		// open は常に「未対応の Start」のスタックになる
		final List<Item> open = new ArrayList<Item>();
		for (final Item item : this.items) {
			switch (item) {
			case Start start -> open.add(start);
			case End end -> {
				if (!open.isEmpty()) {
					open.remove(open.size() - 1);
				}
			}
			case Chars chars -> {
				// 文字は保持しない
			}
			}
		}
		this.previousItems = new ArrayList<Item>(this.items);
		this.previousEpoch = this.epoch;
		this.items.clear();
		this.items.addAll(open);
		++this.epoch;
	}

	/**
	 * 指定位置が指定要素の Start イベントであるかを検査します
	 * (M6b の再開位置アンカーの診断用)。
	 *
	 * @param index   窓内のイベント位置
	 * @param element 期待される要素
	 * @return 位置が窓内にあり、要素の Start であれば true
	 */
	public boolean isStartOf(final int index, final net.zamasoft.foliojet.css.CSSElement element) {
		if (this.previousItems == null || index < 0 || index >= this.previousItems.size()) {
			return false;
		}
		return this.previousItems.get(index) instanceof Start(final CSSStyle style, final CounterScope[] counters)
				&& style.getCSSElement() == element;
	}

	/**
	 * 指定位置の Start に対応する End の位置を返します(M6b)。
	 *
	 * @param startIndex Start イベントの位置
	 * @return 対応する End の位置。部分木が窓内で閉じていなければ -1
	 */
	public int endOf(final int startIndex) {
		if (this.previousItems == null || startIndex < 0 || startIndex >= this.previousItems.size()
				|| !(this.previousItems.get(startIndex) instanceof Start)) {
			return -1;
		}
		int depth = 0;
		for (int i = startIndex; i < this.previousItems.size(); ++i) {
			switch (this.previousItems.get(i)) {
			case Start start -> ++depth;
			case End end -> {
				if (--depth == 0) {
					return i;
				}
			}
			case Chars chars -> {
			}
			}
		}
		return -1;
	}

	/**
	 * 窓内の閉じた部分木 [from, to] を builder へ再駆動します(M6b)。
	 * カウンタは from 時点のスナップショットへ巻き戻してから再生し、
	 * 再生後に元の状態へ戻します(後続ストリームの整合のため)。
	 * 疑似要素・匿名内容は再生中に builder が再合成します。
	 *
	 * @param builder 再生先
	 * @param from    Start イベントの位置
	 * @param to      対応する End イベントの位置(endOf の結果)
	 */
	public void replaySubtree(final StyleBuilder builder, final int from, final int to) {
		assert this.previousItems.get(from) instanceof Start;
		final net.zamasoft.foliojet.ua.PassContext pc = builder.getUserAgent().getPassContext();
		final CounterScope[] headCounters = pc.snapshotNonPageCounters();
		pc.restoreNonPageCounters(((Start) this.previousItems.get(from)).counters());
		try {
			for (int i = from; i <= to; ++i) {
				switch (this.previousItems.get(i)) {
				case Start(CSSStyle style, CounterScope[] counters) -> builder.startStyle(style);
				case Chars(int charOffset, char[] ch) -> builder.characters(charOffset, ch, 0, ch.length);
				case End end -> builder.endStyle();
				}
			}
		} finally {
			pc.restoreNonPageCounters(headCounters);
		}
	}

	/**
	 * 指定要素の「開いている」Start の位置を返します(M6b Phase B)。
	 * 同じ要素が入れ子の場合は最も深い(最後の)ものを返します。
	 * 窓の世代に依存しないアンカー解決です。
	 *
	 * @param element 探す要素
	 * @return 未対応 Start の位置。なければ -1
	 */
	public int findOpenStart(final net.zamasoft.foliojet.css.CSSElement element) {
		if (this.previousItems == null) {
			return -1;
		}
		final java.util.List<Integer> open = new ArrayList<Integer>();
		for (int i = 0; i < this.previousItems.size(); ++i) {
			switch (this.previousItems.get(i)) {
			case Start start -> open.add(i);
			case End end -> {
				if (!open.isEmpty()) {
					open.remove(open.size() - 1);
				}
			}
			case Chars chars -> {
			}
			}
		}
		for (int i = open.size() - 1; i >= 0; --i) {
			if (this.previousItems.get(open.get(i)) instanceof Start(final CSSStyle style,
					final CounterScope[] counters) && style.getCSSElement() == element) {
				return open.get(i);
			}
		}
		return -1;
	}

	/**
	 * 指定位置より前の最後の Start/End のカウンタスナップショットを
	 * 返します(M6b Phase B の尾部再生の巻き戻し用)。
	 */
	private CounterScope[] countersBefore(final int index) {
		for (int i = index - 1; i >= 0; --i) {
			switch (this.previousItems.get(i)) {
			case Start(CSSStyle style, CounterScope[] counters) -> {
				return counters;
			}
			case End(CSSStyle style, CounterScope[] counters) -> {
				return counters;
			}
			case Chars chars -> {
			}
			}
		}
		return null;
	}

	/**
	 * 切断された段落の尾部(charOffset 以降)を builder へ再駆動します
	 * (M6b Phase B)。切断点で開いていたインライン要素を再オープンし、
	 * charOffset を含む Chars から endExclusive まで再生します。
	 * 再オープンの Start は窓へ再記録しません(元の開いている Start と
	 * 二重になるため。実 SAX の End は元の Start と対になる)。
	 *
	 * @param builder      再生先
	 * @param afterStart   段落を含むチェーンボックスの Start の位置
	 * @param charOffset   再開位置のソース文字オフセット
	 * @param endExclusive 再生の終端(このイベントの手前まで)
	 * @return 再開位置が特定できて再生した場合 true
	 */
	public boolean replayTextTail(final StyleBuilder builder, final int afterStart, final int charOffset,
			final int endExclusive) {
		// 切断位置を含む Chars と、その時点で開いているインラインを探す
		int position = -1;
		int skip = 0;
		final java.util.List<Start> opens = new ArrayList<Start>();
		SCAN: for (int i = afterStart + 1; i < endExclusive; ++i) {
			switch (this.previousItems.get(i)) {
			case Start start -> opens.add(start);
			case End end -> {
				if (!opens.isEmpty()) {
					opens.remove(opens.size() - 1);
				}
			}
			case Chars(int off, char[] ch) -> {
				if (off >= 0 && charOffset >= off && charOffset < off + ch.length) {
					position = i;
					skip = charOffset - off;
					break SCAN;
				}
			}
			}
		}
		if (position < 0) {
			return false;
		}
		final net.zamasoft.foliojet.ua.PassContext pc = builder.getUserAgent().getPassContext();
		final CounterScope[] headCounters = pc.snapshotNonPageCounters();
		pc.restoreNonPageCounters(this.countersBefore(position));
		try {
			for (final Start open : opens) {
				builder.replayOpenStart(open.style());
			}
			final Chars cut = (Chars) this.previousItems.get(position);
			if (skip < cut.ch().length) {
				builder.characters(cut.charOffset() + skip, cut.ch(), skip, cut.ch().length - skip);
			}
			for (int i = position + 1; i < endExclusive; ++i) {
				switch (this.previousItems.get(i)) {
				case Start(CSSStyle style, CounterScope[] counters) -> builder.startStyle(style);
				case Chars(int off, char[] ch) -> builder.characters(off, ch, 0, ch.length);
				case End end -> builder.endStyle();
				}
			}
		} finally {
			pc.restoreNonPageCounters(headCounters);
		}
		return true;
	}

	public void restyle(StyleBuilder builder) {
		for (Item item : this.items) {
			switch (item) {
			case Start(CSSStyle style, CounterScope[] counters) -> {
				// 上位の匿名スタイルを除去する
				for (;;) {
					CSSStyle parentStyle = style.getParentStyle();
					if (parentStyle != null && parentStyle.isAnonStyle()) {
						style.removeAnonStyle();
						continue;
					}
					break;
				}
				builder.startStyle(style);
			}
			case Chars(int charOffset, char[] ch) -> builder.characters(charOffset, ch, 0, ch.length);
			case End end -> builder.endStyle();
			}
		}
	}
}
