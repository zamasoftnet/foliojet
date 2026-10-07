package net.zamasoft.foliojet.css.impl.part;

import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Default substitute (broken-image=none) for an {@code <img>} that fails to load
 * (2026-08-06).
 *
 * <p>
 * Previously, a failed image load set no {@link Image} at all, so the element degraded
 * to a normal inline box instead of a replaced box. This caused CSS
 * {@code width}/{@code height} (including both HTML attribute hints and author CSS overrides)
 * to be ignored entirely. The defect appeared on an actual woocommerce.com documentation page:
 * when an image failed to load inside a {@code display:table} figure (the WordPress image caption
 * pattern), no element remained to determine the width. The entire table collapsed to
 * min-content (the longest word's width), and the figcaption became a tall column
 * with one word per line.
 * </p>
 *
 * <p>
 * Unlike {@link NullImage}, this draws the alt string (drawing nothing would simply
 * lose the alternative text that was previously visible). Unlike {@link BrokenImage},
 * it does not draw a red cross (broken-image=none means "show only the alt string
 * without decoration").
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
