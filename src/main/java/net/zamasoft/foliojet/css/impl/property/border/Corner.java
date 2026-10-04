package net.zamasoft.foliojet.css.impl.property.border;

/**
 * ボックスの4隅です。
 *
 * <p>
 * 2026-07-20: 独自拡張{@code -cssj-direction-mode}による「物理プロパティの
 * 回転」機構は廃止した(実世界のCSS/ブラウザには存在しない挙動であり、
 * 縦書き対応は標準の論理プロパティへ一本化した)。恒等写像として残していた
 * {@code resolve}は2026-10-04に削除した。
 * </p>
 */
public enum Corner {
	TOP_LEFT("top-left"), TOP_RIGHT("top-right"), BOTTOM_RIGHT("bottom-right"), BOTTOM_LEFT("bottom-left");

	private final String text;

	private Corner(String text) {
		this.text = text;
	}

	public String text() {
		return this.text;
	}
}
