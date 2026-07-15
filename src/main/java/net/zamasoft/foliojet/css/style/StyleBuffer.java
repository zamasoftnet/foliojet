package net.zamasoft.foliojet.css.style;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;

/**
 * 再生成のためにスタイルイベント列を保持するバッファです。
 *
 * @author MIYABE Tatsuhiko
 */
class StyleBuffer {
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

	public int getDepth() {
		return this.depth;
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

	public void restyle(StyleBuilder builder) {
		for (Item item : this.items) {
			switch (item) {
			case Start(CSSStyle style) -> {
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
