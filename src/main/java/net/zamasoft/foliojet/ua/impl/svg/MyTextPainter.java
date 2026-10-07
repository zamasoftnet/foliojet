package net.zamasoft.foliojet.ua.impl.svg;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.text.AttributedCharacterIterator;
import java.util.List;

import java.awt.font.TextAttribute;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.batik.bridge.DefaultFontFamilyResolver;
import org.apache.batik.bridge.FontFace;
import org.apache.batik.bridge.FontFamilyResolver;
import org.apache.batik.bridge.StrokingTextPainter;
import org.apache.batik.bridge.TextSpanLayout;
import org.apache.batik.gvt.font.GVTFontFamily;
import org.apache.batik.gvt.text.TextPaintInfo;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.PageLayoutGlyphHandler;

class MyTextPainter extends StrokingTextPainter {
	private static final Logger LOG = Logger.getLogger(MyTextPainter.class.getName());

	protected final UserAgent ua;
	protected final FontManager fm;
	protected final TextBreakingRules rules;

	public MyTextPainter(UserAgent ua) {
		this.ua = ua;
		this.fm = ua.getFontManager();
		this.rules = TextBreakingRulesBundle.getRules(null);
	}

	/**
	 * Assigns characters that none of the specified fonts can display to the chunk's resolved
	 * fonts (first entry = {@link MyGVTFont}), instead of using AWT system font resolution
	 * (2026-08-07).
	 *
	 * <p>
	 * The previously bundled batik-all 1.14 included a patch changing this part of
	 * {@code StrokingTextPainter} (product/lib/batik-patch.txt). Batik 1.19 exposes a protected
	 * method to replace this resolver, so remove the jar patch and achieve the same result here:
	 * when {@code getFamilyThatCanDisplay} returns null, the caller falls back to defaultFont
	 * (the first resolved font in the chunk). Only characters unsupported by every font
	 * in the resolved list (including UA defaults) remain unassigned, so any chosen font
	 * would produce a missing-glyph box. Always returning null is correct:
	 * mixing in an AWT-derived GVTFont makes {@code paintTextRuns} replace the entire run
	 * with the default font via {@code fallbackFontStyle}.
	 * </p>
	 */
	@Override
	protected FontFamilyResolver getFontFamilyResolver() {
		return NO_SYSTEM_FONT_RESOLVER;
	}

	private static final FontFamilyResolver NO_SYSTEM_FONT_RESOLVER = new FontFamilyResolver() {
		public GVTFontFamily resolve(String familyName) {
			return DefaultFontFamilyResolver.SINGLETON.resolve(familyName);
		}

		public GVTFontFamily resolve(String familyName, FontFace fontFace) {
			return DefaultFontFamilyResolver.SINGLETON.resolve(familyName, fontFace);
		}

		public GVTFontFamily loadFont(java.io.InputStream in, FontFace fontFace) throws Exception {
			return DefaultFontFamilyResolver.SINGLETON.loadFont(in, fontFace);
		}

		public GVTFontFamily getDefault() {
			return DefaultFontFamilyResolver.SINGLETON.getDefault();
		}

		public GVTFontFamily getFamilyThatCanDisplay(char c) {
			return null;
		}
	};

	protected void paintTextRuns(@SuppressWarnings("rawtypes") List textRuns, Graphics2D g2d) {
		// TODO Outline-only drawing
		// TODO SVG embedded fonts (Batik appears to impose limitations)
		GC gc = ((BridgeGraphics2D) g2d).getGC();
		for (int i = 0; i < textRuns.size(); i++) {
			TextRun textRun = (TextRun) textRuns.get(i);
			AttributedCharacterIterator aci = textRun.getACI();
			// **Read attributes at the start of the run** (2026-10-04). The ACI's current position
			// may remain where the previous scan left it. Reading there picked another run's
			// (the last tspan's) font and paint, making italics and bold appear swapped
			// between the first and last tspans. Batik's StrokingTextPainter also calls first() first.
			aci.first();

			// Set paint.
			TextPaintInfo tpi = (TextPaintInfo) aci.getAttribute(StrokingTextPainter.PAINT_INFO);
			if (tpi != null) {
				if (tpi.composite != null) {
					g2d.setComposite(tpi.composite);
				}
				if (tpi.fillPaint != null) {
					g2d.setPaint(tpi.fillPaint);
				}
			}

			// Get font information. If none of the specified font-family values resolve,
			// Batik may fall back to its default font resolution (AWTGVTFont)
			// (2026-07-18, an SVG specifying 'MS-Mincho' actually caused ClassCastException).
			// This is outside the application's font system, so instead of crashing,
			// render with Copper PDF's default font as a substitute.
			FontStyle fontStyle;
			Object gvtFont = aci.getAttribute(GVT_FONT);
			if (gvtFont instanceof MyGVTFont myFont) {
				fontStyle = myFont.fontStyle;
			} else {
				fontStyle = this.fallbackFontStyle(aci);
			}

			// Extract text.
			char[] ch = new char[aci.getEndIndex() - aci.getBeginIndex()];
			aci.first();
			for (int j = 0; aci.getIndex() < aci.getEndIndex(); ++j) {
				ch[j] = aci.current();
				aci.next();
			}

			// Draw.
			TextSpanLayout layout = textRun.getLayout();
			// In horizontal writing, draw from the first character's position (2026-10-04). getOffset()
			// is the position before dx, dy, and baseline-shift (as GlyphLayout documents);
			// those adjustments appear only in glyph positions. Drawing from offset ignored tspan dy
			// until it carried over to the next run's start, taking effect "one run late",
			// and baseline-shift had no effect. Vertical and right-to-left writing remain as before.
			Point2D position = layout.getOffset();
			if (fontStyle.getDirection() == FontStyle.Direction.LTR && layout.getGlyphVector().getNumGlyphs() > 0) {
				position = layout.getGlyphVector().getGlyphPosition(0);
			}
			try (final var gcState = gc.begin()) {
				double x = position.getX();
				double y = position.getY();
				AffineTransform at = AffineTransform.getTranslateInstance(x, y - fontStyle.getSize());
				gc.transform(at);
				PageLayoutGlyphHandler lineHandler = new PageLayoutGlyphHandler(gc);
			lineHandler.setDirection(fontStyle.getDirection());
			lineHandler.setLineAdvance(Double.MAX_VALUE);
			TextLayoutHandler tlf = new TextLayoutHandler(gc, this.rules, lineHandler);
			tlf.setDirection(fontStyle.getDirection());
				tlf.fontStyle(fontStyle);
				tlf.characters(-1, ch, 0, ch.length);
				tlf.flush();
				lineHandler.close();
			}
		}
	}

	/**
	 * Builds a fallback FontStyle when GVT_FONT is not MyGVTFont
	 * (i.e., could not be resolved within this application's font system).
	 * Restore font size and other settings as far as possible from attributes remaining
	 * in the ACI (always set by MySVGTextElementBridge.getFontList),
	 * replacing only the font family with the user agent's default.
	 */
	private FontStyle fallbackFontStyle(AttributedCharacterIterator aci) {
		LOG.log(Level.WARNING, "SVG中のfont-familyを解決できませんでした。既定フォントで代替します。");
		Float sizeAttr = (Float) aci.getAttribute(TextAttribute.SIZE);
		double size = sizeAttr != null ? sizeAttr.doubleValue() : 10d;
		Float postureAttr = (Float) aci.getAttribute(TextAttribute.POSTURE);
		FontStyle.Style style = postureAttr != null && postureAttr.equals(TextAttribute.POSTURE_OBLIQUE)
				? FontStyle.Style.ITALIC
				: FontStyle.Style.NORMAL;
		Float weightAttr = (Float) aci.getAttribute(TextAttribute.WEIGHT);
		final float weightValue = weightAttr == null ? TextAttribute.WEIGHT_REGULAR.floatValue()
				: weightAttr.floatValue();
		final FontStyle.Weight weight = weightValue >= 2.75f ? FontStyle.Weight.W_900
				: weightValue >= 2.25f ? FontStyle.Weight.W_800
						: weightValue >= 2.0f ? FontStyle.Weight.W_700
								: weightValue >= 1.5f ? FontStyle.Weight.W_600
										: weightValue >= 1.25f ? FontStyle.Weight.W_500 : FontStyle.Weight.W_400;
		return new FontStyleImpl(this.ua.getDefaultFontFamily().asFontFamilyList(), size, style, weight,
				FontStyle.Direction.LTR, this.ua.getDefaultFontPolicy().asFontPolicyList());
	}
}
