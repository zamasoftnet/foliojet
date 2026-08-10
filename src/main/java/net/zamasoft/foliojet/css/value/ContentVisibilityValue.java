package net.zamasoft.foliojet.css.value;

/**
 * content-visibilityの値です(css-contain-2、2026-08-10)。
 *
 * <p>
 * {@code hidden}は要素自身のボックスは残して<b>中身をレイアウトから
 * 省く</b>(実サイトではオフキャンバスのメガメニュー等が
 * {@code position:static}のまま{@code content-visibility:hidden}+
 * {@code opacity:0}で畳まれており、未対応だと巨大な透明ボックスが
 * 紙面を占有する——yomiuri.co.jpトップで実測)。{@code auto}は画面の
 * 可視域に応じた遅延レンダリングの指示であり、印刷では全て描画される
 * (Chromeの印刷と同じ)ため{@code visible}と同じ扱いにする。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public enum ContentVisibilityValue implements Value {
	VISIBLE_VALUE(ContentVisibilityValue.VISIBLE),

	HIDDEN_VALUE(ContentVisibilityValue.HIDDEN),

	AUTO_VALUE(ContentVisibilityValue.AUTO);

	public static final byte VISIBLE = 0;

	public static final byte HIDDEN = 1;

	public static final byte AUTO = 2;

	private final byte contentVisibility;

	private ContentVisibilityValue(byte contentVisibility) {
		this.contentVisibility = contentVisibility;
	}

	public byte getContentVisibility() {
		return this.contentVisibility;
	}

	public String toString() {
		switch (this.contentVisibility) {
		case VISIBLE:
			return "visible";

		case HIDDEN:
			return "hidden";

		case AUTO:
			return "auto";

		default:
			throw new IllegalStateException();
		}
	}
}
