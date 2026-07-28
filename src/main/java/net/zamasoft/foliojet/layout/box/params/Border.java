package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Border.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public class Border implements Comparable<Border> {
	public static final short NONE = 0;

	public static final short HIDDEN = 1;

	public static final short DOUBLE = 2;

	public static final short SOLID = 3;

	public static final short DASHED = 4;

	public static final short DOTTED = 5;

	public static final short RIDGE = 6;

	public static final short OUTSET = 7;

	public static final short GROOVE = 8;

	public static final short INSET = 9;

	public static final Border NONE_BORDER;

	public static final Border HIDDEN_BORDER;

	static {
		NONE_BORDER = new Border(Border.NONE, 0, null);
		HIDDEN_BORDER = new Border(Border.HIDDEN, 0, null);
	}

	/**
	 * 幅(太さ)です。
	 */
	public final double width;

	/**
	 * スタイルです。
	 */
	public final short style;

	/**
	 * 色です。 透明の場合はnullです。
	 */
	public final Color color;

	/**
	 * 枠線の太さの<b>使用値</b>の上限(ポイント)です。
	 *
	 * <p>
	 * CSSは上限を定めていませんが、実ブラウザはどれも内部表現の飽和値で
	 * 頭打ちにします(Chromeの{@code LayoutUnit}は約3,355万px)。
	 * ここも同じ立場を取ります——{@code colspan}/{@code rowspan}をHTML
	 * Standardの上限へ丸めたのと同じ理由です。
	 * </p>
	 *
	 * <p>
	 * 1e6pt は約35m。PDFのページ寸法の上限14,400pt(200インチ)の
	 * 70倍近くあり、正当な枠線が届く値ではありません。一方
	 * {@link net.zamasoft.foliojet.layout.util.LayoutUtils#DRAWABLE_LIMIT}
	 * (1e8pt)の100分の1なので、4辺ぶんを足しても入れ子で重ねても
	 * 描画可能な範囲に収まります。
	 * </p>
	 *
	 * <p>
	 * 丸めないと{@code border-bottom:4294967295px}(=3.22e9pt)のような
	 * 指定がそのまま寸法になり、{@code BackgroundBorderDrawable}の
	 * 「描画高が異常」assertで<b>変換が失敗</b>します
	 * (WPT {@code css-break/grid/grid-large-end-border-crash.html})。
	 * assertを無効にした本番でも、3,500kmの枠を描こうとするだけで
	 * 正しくはなりません。<b>入力を正気な範囲へ丸めるのが正しい層</b>です。
	 * </p>
	 */
	public static final double MAX_WIDTH = 1e6;

	public static Border create(short style, double width, Color color) {
		// 使用値の上限({@link #MAX_WIDTH}参照)。NaNはここで0へ落ちる
		// (NaNはどの比較もfalseなので、下のisNull()で拾えない)
		if (Double.isNaN(width)) {
			width = 0;
		} else if (width > MAX_WIDTH) {
			width = MAX_WIDTH;
		}
		// SPEC CSS2.1 8.5.3
		switch (style) {
		case Border.NONE:
			if (color == null) {
				return NONE_BORDER;
			}
			width = 0;
			break;
		case Border.HIDDEN:
			if (color == null) {
				return HIDDEN_BORDER;
			}
			width = 0;
			break;
		default:
			break;
		}
		return new Border(style, width, color);
	}

	private Border(short style, double width, Color color) {
		this.style = style;
		this.width = width;
		this.color = color;
	}

	public boolean isVisible() {
		if (this.isNull() || this.color == null) {
			return false;
		}
		return true;
	}

	public boolean isNull() {
		return this.width <= 0;
	}

	public String toString() {
		return "[style=" + this.style + ",width=" + this.width + ",color=" + this.color + "]";
	}

	public int compareTo(Border o) {
		Border next = (Border) o;
		if (next == null) {
			return -1;
		}
		// rule 1
		if (this.style == Border.HIDDEN) {
			if (next.style == Border.HIDDEN) {
				return 0;
			}
			return -1;
		}
		if (next.style == Border.HIDDEN) {
			return 1;
		}
		// rule 2
		if (this.style == Border.NONE) {
			if (next.style == Border.NONE) {
				return 0;
			}
			return 1;
		}
		if (next.style == Border.NONE) {
			return -1;
		}
		// rule 3
		if (next.width > this.width) {
			return 1;
		}
		if (next.width < this.width) {
			return -1;
		}
		if (next.style < this.style) {
			return 1;
		}
		if (next.style > this.style) {
			return -1;
		}
		return 0;
	}

	public boolean equals(Object o) {
		Border b = (Border) o;
		return this.style == b.style && this.width == b.width && this.color.equals(b.color);
	}
}