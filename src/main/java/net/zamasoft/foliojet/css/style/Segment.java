package net.zamasoft.foliojet.css.style;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;

/**
 * スタイルイベント列(セグメント)です。
 *
 * <p>
 * レイアウト前・スタイル適用後のソースイベント(Start/Chars/End の3語彙)を
 * 保持します(旧名 StyleBuffer。run-in / PageContent の再生成バッファ用途は
 * 4で廃止)。M6 では本流の再レイアウト源です(ARCHITECTURE.md §5.4):
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

	record Start(CSSStyle style) implements Item {
	}

	record Chars(int charOffset, char[] ch) implements Item {
	}

	record End(CSSStyle style) implements Item {
	}

	protected final List<Item> items = new ArrayList<Item>();

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

	public int getDepth() {
		return this.depth;
	}

	/**
	 * 保持しているイベント数を返します。
	 */
	public int size() {
		return this.items.size();
	}

	public void startStyle(CSSStyle style) {
		this.items.add(new Start(style));
		++this.depth;
	}

	public void characters(int offset, char[] ch, int off, int len) {
		char[] chars = new char[len];
		System.arraycopy(ch, off, chars, 0, len);
		this.items.add(new Chars(offset, chars));
	}

	public void endStyle(CSSStyle style) {
		this.items.add(new End(style));
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
		this.items.clear();
		this.items.addAll(open);
		++this.epoch;
	}
}
