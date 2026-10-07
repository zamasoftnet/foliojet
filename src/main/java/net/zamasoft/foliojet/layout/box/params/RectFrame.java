package net.zamasoft.foliojet.layout.box.params;

/**
 * A box enclosed by borders and padding.
 *
 * <p>
 * This box can contain only one box inside it.
 * </p>
 *
 * <p>
 * Also holds box-shadow (shadows) and outline (2026-08-29). Both are decorations that do not affect dimensions.
 * They are drawn with the frame and carried with it into segment replay (BlockParamsFields copies {@code frame} as
 * a whole), so keeping them here is safer than adding separate params fields.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: RectFrame.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class RectFrame {
	public static final RectFrame NULL_FRAME = new RectFrame(Insets.NULL_INSETS, RectBorder.NONE_RECT_BORDER,
			Background.NULL_BACKGROUND, Insets.NULL_INSETS, null, null);
	public final Insets margin;

	public final RectBorder border;

	public final Background background;

	public final Insets padding;

	/** The box-shadow shadows (frontmost first). Null if absent. */
	public final BoxShadow[] shadows;

	/** The outline. Null if absent. */
	public final Outline outline;

	public static RectFrame create(Insets margin, RectBorder border, Background background, Insets padding) {
		return create(margin, border, background, padding, null, null);
	}

	public static RectFrame create(Insets margin, RectBorder border, Background background, Insets padding,
			BoxShadow[] shadows, Outline outline) {
		margin = margin == null ? Insets.NULL_INSETS : margin;
		border = border == null ? RectBorder.NONE_RECT_BORDER : border;
		background = background == null ? Background.NULL_BACKGROUND : background;
		padding = padding == null ? Insets.NULL_INSETS : padding;
		if (shadows != null && shadows.length == 0) {
			shadows = null;
		}
		if (margin.isNull() && border.isNull() && !background.isVisible() & padding.isNull() && shadows == null
				&& outline == null) {
			return NULL_FRAME;
		}
		return new RectFrame(margin, border, background, padding, shadows, outline);
	}

	private RectFrame(Insets margin, RectBorder border, Background background, Insets padding, BoxShadow[] shadows,
			Outline outline) {
		this.margin = margin;
		this.border = border;
		this.background = background;
		this.padding = padding;
		this.shadows = shadows;
		this.outline = outline;
	}

	public boolean isVisible() {
		return this.background.isVisible() || this.border.isVisible() || this.shadows != null
				|| this.outline != null;
	}

	public boolean isNull() {
		return this.margin.isNull() && this.border.isNull() && this.padding.isNull() && !this.background.isVisible()
				&& this.shadows == null && this.outline == null;
	}

	public RectFrame cut(boolean top, boolean right, boolean bottom, boolean left) {
		Insets newMargin = this.margin.cut(top, right, bottom, left);
		RectBorder newBorder = this.border.cut(top, right, bottom, left);
		Insets newPadding = this.padding.cut(top, right, bottom, left);

		// Draw shadows and outlines for each fragment (equivalent to box-decoration-break: clone).
		return RectFrame.create(newMargin, newBorder, this.background, newPadding, this.shadows, this.outline);
	}

	public String toString() {
		return "[margin=" + this.margin + ",border=" + this.border + ",background=" + this.background + ",padding="
				+ this.padding + (this.shadows == null ? "" : ",shadows=" + java.util.Arrays.toString(this.shadows))
				+ (this.outline == null ? "" : ",outline=" + this.outline) + "]";
	}
}
