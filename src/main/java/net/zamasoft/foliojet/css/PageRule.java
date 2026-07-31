package net.zamasoft.foliojet.css;

import java.util.EnumMap;
import java.util.Map;

/**
 * 構造化された{@code @page}規則です(名前付きページN1a、2026-07-31——
 * consult-codex-2026-07-31-named-pages.txt Q1)。従来の4バケット
 * (無名/first/left/right)を置き換える順序付き規則列の1要素。
 * 特異性はCSS Page 3の(f,g,h)=(ページ名, :first, :left/:right)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class PageRule {

	public static final byte PSEUDO_FIRST = 1;

	public static final byte PSEUDO_LEFT = 2;

	public static final byte PSEUDO_RIGHT = 4;

	/** ページ名(null=無名。CSS識別子として大文字小文字を区別)。 */
	final String name;

	/** 要求する擬似ページのビット({@link #PSEUDO_FIRST}等)。 */
	final byte pseudoMask;

	/** 通常宣言(マージン等。無ければnull)。 */
	final Declaration declaration;

	/** マージンボックス宣言(無ければ空)。 */
	final Map<MarginBoxName, Declaration> marginBoxes = new EnumMap<>(MarginBoxName.class);

	/** 出現順(同特異性のタイブレーク)。 */
	final int order;

	PageRule(final String name, final byte pseudoMask, final Declaration declaration, final int order) {
		this.name = name;
		this.pseudoMask = pseudoMask;
		this.declaration = declaration;
		this.order = order;
	}

	/** CSS Page 3の(f,g,h)特異性を単一整数へ符号化して返します。 */
	int specificity() {
		final int f = this.name != null ? 1 : 0;
		final int g = (this.pseudoMask & PSEUDO_FIRST) != 0 ? 1 : 0;
		final int h = Integer.bitCount(this.pseudoMask & (PSEUDO_LEFT | PSEUDO_RIGHT));
		return (f << 16) | (g << 8) | h;
	}

	/**
	 * ページへの適合を判定します。
	 *
	 * @param pageName 現在のページ名(null=無名)
	 * @param pseudo   ページの擬似状態ビット
	 */
	boolean matches(final String pageName, final byte pseudo) {
		if (this.name != null && !this.name.equals(pageName)) {
			return false;
		}
		return (this.pseudoMask & ~pseudo) == 0;
	}
}
