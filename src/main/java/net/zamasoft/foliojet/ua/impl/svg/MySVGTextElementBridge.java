package net.zamasoft.foliojet.ua.impl.svg;

import java.awt.font.TextAttribute;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.batik.bridge.Bridge;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.CSSUtilities;
import org.apache.batik.bridge.SVGTextElementBridge;
import org.apache.batik.bridge.TextUtilities;
import org.apache.batik.css.engine.SVGCSSEngine;
import org.apache.batik.css.engine.value.Value;
import org.apache.batik.gvt.font.GVTFont;
import org.w3c.dom.Element;

import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

public class MySVGTextElementBridge extends SVGTextElementBridge {
	protected final UserAgent ua;
	protected final FontManager fm;
	protected final FontPolicyList defaultPolicy;
	protected final FontFamilyValue defaultFamilies;

	public MySVGTextElementBridge(UserAgent ua) {
		this.ua = ua;
		this.fm = ua.getFontManager();
		this.defaultPolicy = ua.getDefaultFontPolicy().asFontPolicyList();
		this.defaultFamilies = ua.getDefaultFontFamily();
	}

	public Bridge getInstance() {
		return new MySVGTextElementBridge(this.ua);
	}

	/**
	 * This method adds all the font related properties to <tt>result</tt> It also
	 * builds a List of the GVTFonts and returns it.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected List getFontList(BridgeContext ctx, Element element, Map result) {

		// Unique value for text element - used for run identification.
		result.put(TEXT_COMPOUND_ID, new WeakReference(element));

		// Font size.
		Float fontSizeFloat = TextUtilities.convertFontSize(element);
		float fontSize = fontSizeFloat.floatValue();
		result.put(TextAttribute.SIZE, fontSizeFloat);

		// Font stretch
		result.put(TextAttribute.WIDTH, TextUtilities.convertFontStretch(element));

		// Font style
		Float postureFloat = TextUtilities.convertFontStyle(element);
		result.put(TextAttribute.POSTURE, postureFloat);

		// Font weight
		Float weightFloat = TextUtilities.convertFontWeight(element);
		result.put(TextAttribute.WEIGHT, weightFloat);

		FontStyle.Direction direction = FontStyle.Direction.LTR;
		Value v = CSSUtilities.getComputedStyle(element, SVGCSSEngine.WRITING_MODE_INDEX);
		String s = v.getStringValue();
		switch (s.charAt(0)) {
		case 'r':
			direction = FontStyle.Direction.RTL;
			break;
		case 't':
			direction = FontStyle.Direction.TB;
			break;
		}

		// Needed for SVG fonts (also for dynamic documents).
		result.put(TEXT_COMPOUND_DELIMITER, element);

		// make a list of GVTFont objects
		Value val = CSSUtilities.getComputedStyle(element, SVGCSSEngine.FONT_FAMILY_INDEX);
		int len = val.getLength();
		FontFamily[] families = new FontFamily[len + this.defaultFamilies.getLength()];
		for (int i = 0; i < len; i++) {
			Value it = val.item(i);
			String fontFamilyName = it.getStringValue();
			families[i] = FontFamily.create(fontFamilyName);
		}
		for (int i = 0; i < this.defaultFamilies.getLength(); ++i) {
			families[i + len] = this.defaultFamilies.get(i);
		}

		FontStyle.Style style;
		if (postureFloat.equals(TextAttribute.POSTURE_OBLIQUE)) {
			style = FontStyle.Style.ITALIC;
		} else {
			style = FontStyle.Style.NORMAL;
		}
		final int cssWeight = Math.round(
				CSSUtilities.getComputedStyle(element, SVGCSSEngine.FONT_WEIGHT_INDEX).getFloatValue());
		final int weightIndex = Math.max(0, Math.min(8, cssWeight / 100 - 1));
		final FontStyle.Weight weight = FontStyle.Weight.values()[weightIndex];

		// glyph-orientation-vertical (SvgCompat maps text-orientation to it): 0 stands Latin letters upright, 90 lays
		// every glyph on its side, auto mixes (2026-11-26: upright letters were drawn on their side, and the next run,
		// placed by Batik after upright advances, left a gap)
		FontStyle.TextOrientation orientation = FontStyle.TextOrientation.MIXED;
		if (direction == FontStyle.Direction.TB) {
			final Value gov = CSSUtilities.getComputedStyle(element, SVGCSSEngine.GLYPH_ORIENTATION_VERTICAL_INDEX);
			final short type = gov.getPrimitiveType();
			if (type != org.w3c.dom.css.CSSPrimitiveValue.CSS_IDENT) {
				// The computed value keeps its unit. Converted and rounded to a right angle exactly as Batik's layout does
				// (SVGTextElementBridge, GlyphLayout.getGlyphOrientationAngle; its grad is x 9/5), so that the glyphs
				// are drawn the way Batik advanced them
				float degrees = gov.getFloatValue();
				if (type == org.w3c.dom.css.CSSPrimitiveValue.CSS_RAD) {
					degrees = (float) Math.toDegrees(degrees);
				} else if (type == org.w3c.dom.css.CSSPrimitiveValue.CSS_GRAD) {
					degrees = degrees * 9 / 5;
				}
				final int angle = (((int) degrees % 360) + 360) % 360;
				if (angle <= 45 || angle > 315) {
					orientation = FontStyle.TextOrientation.UPRIGHT;
				} else if (angle <= 135) {
					orientation = FontStyle.TextOrientation.SIDEWAYS;
				}
			}
		}
		FontStyle fontStyle = new FontStyleImpl(new FontFamilyList(families), fontSize, style, weight, direction,
				this.defaultPolicy, null, true, true, orientation, net.zamasoft.pdfg2d.font.FontSource.NORMAL_WIDTH_CLASS,
				null);
		List fontList = new ArrayList();

		FontListMetrics ms = this.fm.getFontListMetrics(fontStyle);
		for (int i = 0; i < ms.getLength(); ++i) {
			FontMetricsImpl m = (FontMetricsImpl) ms.getFontMetrics(i);
			GVTFont font = new MyGVTFont(m, fontStyle, ms);
			fontList.add(font);
		}

		if (!ctx.isDynamic()) {
			// Only leave this in the map for dynamic documents.
			// Otherwise it will cause the whole DOM to stay when
			// we don't really need it.
			result.remove(TEXT_COMPOUND_DELIMITER);
		}
		return fontList;
	}
}
