package net.zamasoft.foliojet.layout.box;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.ImageFont;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextClip;

/**
 * Receives the text runs that {@link IBox#textShape} walks (2026-10-09).
 */
@FunctionalInterface
public interface TextShapeSink {
	/**
	 * Receives one run.
	 *
	 * @param text      the text
	 * @param transform the transform from the run's space, where the text would be drawn at (0, 0), to the space
	 *                  of the walk
	 */
	public void text(Text text, AffineTransform transform);

	/**
	 * A sink that adds the glyph outlines of fonts that have them to {@code path} and leaves out the rest.
	 *
	 * @param path the path to add to
	 * @return the sink
	 */
	public static TextShapeSink outlines(final GeneralPath path) {
		return (text, transform) -> {
			if (((FontMetricsImpl) text.getFontMetrics()).getFont() instanceof ShapedFont font) {
				FontUtils.addTextPath(path, font, text, transform);
			}
		};
	}

	/**
	 * A sink that adds the runs to {@code clip} for {@code background-clip: text}. Glyphs drawn as images (emoji
	 * images) have no shape to clip with: they are left out with a warning.
	 *
	 * @param pageBox the page, for the warning
	 * @param clip    the clip to add to
	 * @return the sink
	 */
	public static TextShapeSink backgroundClip(final PageBox pageBox, final TextClip clip) {
		return (text, transform) -> {
			if (((FontMetricsImpl) text.getFontMetrics()).getFont() instanceof ImageFont) {
				final String c = new String(text.getChars(), 0, text.getCharCount());
				final StringBuilder codes = new StringBuilder();
				for (int j = 0; j < c.length(); ++j) {
					codes.append("[").append(Integer.toHexString(c.charAt(j))).append("]");
				}
				pageBox.getUserAgent().message(MessageCodes.WARN_MISSING_FONT_OUTLINE, c + codes);
				return;
			}
			clip.add(text, transform);
		};
	}
}
