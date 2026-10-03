package net.zamasoft.foliojet.layout.box.params;

/**
 * CSS の{@code bookmark-level}・{@code bookmark-label}(css-gcpm-3)の計算値です
 * (2026-10-04、TECH-20261003-004 の⑤)。どちらも既定のままなら箱には付けず
 * ({@code Params.bookmark == null})、しおりは従来どおり h1〜h6 の段数と
 * 見出しの文字から作る。
 *
 * @param level しおりの段数。0 なら作らない({@code none})、-1 なら文書の
 *              見出しの段数(h1〜h6)に従う({@code auto})
 * @param label しおりの文字の部品。{@code null}の要素はその要素の文字
 *              ({@code content()})。配列が{@code null}なら要素の文字だけ
 */
public record BookmarkSpec(int level, String[] label) implements java.io.Serializable {
	/** 段数を文書の見出しに任せる値。 */
	public static final int LEVEL_AUTO = -1;

	/** しおりの文字を組み立てます。 */
	public String title(final String elementText) {
		if (this.label == null) {
			return elementText;
		}
		final StringBuilder buff = new StringBuilder();
		for (final String part : this.label) {
			buff.append(part == null ? (elementText == null ? "" : elementText) : part);
		}
		return buff.length() == 0 ? null : buff.toString();
	}
}
