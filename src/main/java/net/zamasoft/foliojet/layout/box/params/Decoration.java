package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * Drawing attributes of inherited/propagated text-decoration lines.
 *
 * <p>
 * Since 2026-08-29, carries line style, thickness, and underline position as well as color per line
 * ({@link Line}). CSS decoration lines are "owned" by the element specifying them; even when they
 * propagate to descendant text, they use the owning element's style, thickness, and color
 * (css-text-decoration-3 §2.2). Therefore, values created from the params of the element setting
 * the flag are passed to children via {@code AbstractTextBox.setDecoration}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Decoration.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Decoration {
	/**
	 * Drawing attributes of one decoration line.
	 *
	 * @param color     line color
	 * @param style     line style ({@code AbstractTextParams.DECORATION_STYLE_*})
	 * @param thickness absolute thickness (0 means automatic = font size × decorationThickness)
	 * @param offset    underline offset (NaN means automatic; ignored for other decorations)
	 * @param position  underline position ({@code AbstractTextParams.UNDERLINE_POSITION_*};
	 *                  ignored for other decorations)
	 */
	public record Line(Color color, byte style, double thickness, double offset, byte position) {
		/** Creates line attributes from the owning element's params. */
		public static Line of(final Color color, final AbstractTextParams params) {
			return new Line(color, params.decorationStyle, params.decorationThicknessLength, params.underlineOffset,
					params.underlinePosition);
		}
	}

	public final Line underline;

	public final Line overline;

	public final Line lineThrough;

	public Decoration(Line underline, Line overline, Line lineThrough) {
		this.underline = underline;
		this.overline = overline;
		this.lineThrough = lineThrough;
	}
}
