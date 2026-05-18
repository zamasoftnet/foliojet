package net.zamasoft.foliojet.style.box.params;

/**
 * 境界線とパディングで囲まれたボックスです。
 * 
 * <p>
 * このボックスの内部には1つだけのボックスを含むことができます。
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: RectFrame.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class RectFrame {
	public static final RectFrame NULL_FRAME = new RectFrame(Insets.NULL_INSETS, RectBorder.NONE_RECT_BORDER,
			Background.NULL_BACKGROUND, Insets.NULL_INSETS);
	public final Insets margin;

	public final RectBorder border;

	public final Background background;

	public final Insets padding;

	public static RectFrame create(Insets margin, RectBorder border, Background background, Insets padding) {
		margin = margin == null ? Insets.NULL_INSETS : margin;
		border = border == null ? RectBorder.NONE_RECT_BORDER : border;
		background = background == null ? Background.NULL_BACKGROUND : background;
		padding = padding == null ? Insets.NULL_INSETS : padding;
		if (margin.isNull() && border.isNull() && !background.isVisible() & padding.isNull()) {
			return NULL_FRAME;
		}
		return new RectFrame(margin, border, background, padding);
	}

	private RectFrame(Insets margin, RectBorder border, Background background, Insets padding) {
		this.margin = margin;
		this.border = border;
		this.background = background;
		this.padding = padding;
	}

	public boolean isVisible() {
		return this.background.isVisible() || this.border.isVisible();
	}

	public boolean isNull() {
		return this.margin.isNull() && this.border.isNull() && this.padding.isNull() && !this.background.isVisible();
	}

	public RectFrame cut(boolean top, boolean right, boolean bottom, boolean left) {
		Insets newMargin = this.margin.cut(top, right, bottom, left);
		RectBorder newBorder = this.border.cut(top, right, bottom, left);
		Insets newPadding = this.padding.cut(top, right, bottom, left);

		return RectFrame.create(newMargin, newBorder, this.background, newPadding);
	}

	public String toString() {
		return "[margin=" + this.margin + ",border=" + this.border + ",background=" + this.background + ",padding="
				+ this.padding + "]";
	}
}
