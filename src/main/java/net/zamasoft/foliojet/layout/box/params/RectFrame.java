package net.zamasoft.foliojet.layout.box.params;

/**
 * 境界線とパディングで囲まれたボックスです。
 *
 * <p>
 * このボックスの内部には1つだけのボックスを含むことができます。
 * </p>
 *
 * <p>
 * box-shadow(影)とoutline(アウトライン)もここに持つ(2026-08-29)。どちらも
 * 寸法に影響しない装飾で、枠と一緒に描かれ、枠と一緒にsegment再生へ
 * 運ばれる(BlockParamsFieldsは{@code frame}を丸ごと写す)ので、別の
 * paramsフィールドを増やすより安全。
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

	/** box-shadowの影(先頭が最前面)。無ければnull。 */
	public final BoxShadow[] shadows;

	/** outline。無ければnull。 */
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

		// 影とアウトラインは断片ごとに描く(box-decoration-break: cloneに相当)
		return RectFrame.create(newMargin, newBorder, this.background, newPadding, this.shadows, this.outline);
	}

	public String toString() {
		return "[margin=" + this.margin + ",border=" + this.border + ",background=" + this.background + ",padding="
				+ this.padding + (this.shadows == null ? "" : ",shadows=" + java.util.Arrays.toString(this.shadows))
				+ (this.outline == null ? "" : ",outline=" + this.outline) + "]";
	}
}
