package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.css.CSSStyle;

/**
 * ボックスの4辺です。
 *
 * <p>
 * 2026-07-20: 独自拡張{@code -cssj-direction-mode}による「物理プロパティの
 * 回転」機構は廃止した(実世界のCSS/ブラウザには存在しない挙動であり、
 * 縦書き対応は標準の論理プロパティ({@link LogicalSide})へ一本化した)。
 * {@link #resolve}は後方互換のため残すが、常に{@code this}を返す
 * (無回転)。
 * </p>
 */
public enum Side {
	TOP("top"), RIGHT("right"), BOTTOM("bottom"), LEFT("left");

	private final String text;

	private Side(String text) {
		this.text = text;
	}

	public String text() {
		return this.text;
	}

	/**
	 * 物理的な辺をそのまま返します(回転なし)。
	 */
	public Side resolve(CSSStyle style) {
		return this;
	}
}
