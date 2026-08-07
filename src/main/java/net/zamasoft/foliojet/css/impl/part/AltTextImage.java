package net.zamasoft.foliojet.css.impl.part;

import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * 読み込みに失敗した{@code <img>}の既定(broken-image=none)の代替物です
 * (2026-08-06)。
 *
 * <p>
 * 以前は画像が読み込めないと{@link Image}をまったく設定せず、要素が
 * 置換ボックスではない普通のinlineボックスへ縮退していた——CSSの
 * {@code width}/{@code height}(HTML属性由来のヒントも著者CSSの上書きも
 * 含む)が完全に無視される欠陥だった。実物のwoocommerce.comのドキュメント
 * ページで発覚: {@code display:table}の図(WordPressの画像キャプション
 * パターン)の中で画像が読み込めないと、幅を決める要素が無くなり、
 * table全体がmin-content(最長単語幅)まで潰れ、figcaptionが単語ごとに
 * 1行の縦長の列になっていた。
 * </p>
 *
 * <p>
 * {@link NullImage}と違いalt文字列を描画する(何も描かないと、以前は
 * 見えていた代替テキストが単に消えてしまう)。{@link BrokenImage}と違い
 * 赤いバツ印は描かない(broken-image=noneの意図は「装飾なしでalt文字列
 * だけ見せる」)。
 * </p>
 */
public class AltTextImage implements Image {
	protected static final double WIDTH = 40, HEIGHT = 40;

	protected final UserAgent ua;

	protected final String alt;

	public AltTextImage(UserAgent ua, String alt) {
		this.ua = ua;
		this.alt = alt;
	}

	public double getWidth() {
		return WIDTH;
	}

	public double getHeight() {
		return HEIGHT;
	}

	public String getAltString() {
		return this.alt;
	}

	public void drawTo(GC gc) throws GraphicsException {
		if (this.alt != null && this.alt.length() > 0) {
			LayoutUtils.drawText(gc, this.ua.getDefaultFontPolicy().asFontPolicyList(), 5, this.alt, 3, 3, WIDTH - 6);
		}
	}
}
