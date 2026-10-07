package net.zamasoft.foliojet.objects.mathml;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.StyleAwareInlineObject;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.util.XMLParsers;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.sourceforge.jeuclid.MathMLParserSupport;
import net.sourceforge.jeuclid.context.LayoutContextImpl;
import net.sourceforge.jeuclid.context.Parameter;
import net.sourceforge.jeuclid.layout.JEuclidView;

import org.apache.batik.dom.util.SAXDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;

/**
 * Lays out MathML in a document with JEuclid.
 *
 * <p>
 * <b>Formula size, color, and font come from the math element's CSS</b> (2026-10-04).
 * Previously, JEuclid defaults (12 pt, black, JEuclid fonts) were used, so a formula stayed at
 * 12 pt even with 9 pt body text, and CSS settings could only be changed through
 * {@code mathsize} on {@code mstyle}. {@code mathsize}/{@code mathcolor} inside the formula
 * continue to apply relative to the CSS values.
 * </p>
 */
public class MathMLInlineObject extends SAXDocumentFactory implements StyleAwareInlineObject {
	private CSSStyle hostStyle = null;

	public MathMLInlineObject() throws ParserConfigurationException {
		super(MathMLParserSupport.createDocumentBuilder().getDOMImplementation(),
				XMLResourceDescriptor.getXMLParserClassName());
		try {
			this.parser = XMLParsers.createXMLReader();
		} catch (Exception e) {
			// ignore
		}
		this.setValidating(false);
	}

	public void setHostStyle(CSSStyle style) {
		this.hostStyle = style;
	}

	@SuppressWarnings("unchecked")
	public Image getImage(UserAgent ua) throws IOException {
		final java.awt.Image tempimage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D) tempimage.getGraphics();
		final LayoutContextImpl context = new LayoutContextImpl(LayoutContextImpl.getDefaultLayoutContext());
		final double scale;
		final boolean sideways = this.hostStyle != null && sideways(this.hostStyle);
		if (this.hostStyle == null) {
			// Text scaling is already applied to the CSS size during parsing. Apply it to
			// JEuclid's default size only when CSS is absent
			scale = ua.getFontMagnification();
		} else {
			scale = 1.0;
			final FontStyle fontStyle = this.hostStyle.getFontStyle();
			context.setParameter(Parameter.MATHSIZE, (float) fontStyle.getSize());
			final net.zamasoft.pdfg2d.gc.paint.Color color = net.zamasoft.foliojet.css.impl.property.text.CSSColor
					.get(this.hostStyle);
			context.setParameter(Parameter.MATHCOLOR, new java.awt.Color(color.getRed(), color.getGreen(),
					color.getBlue(), color.getAlpha()));
			context.setParameter(Parameter.FONTS_SERIF, MathFonts.families(ua.getFontManager(), fontStyle,
					(List<String>) context.getParameter(Parameter.FONTS_SERIF)));
		}
		JEuclidView view = new JEuclidView(this.document, context, g);
		return new MathMLImage(view, scale, sideways);
	}

	/**
	 * Whether to rotate the formula sideways like Latin text in vertical writing lines (2026-10-05).
	 * {@code text-orientation: upright} keeps it upright.
	 * {@code sideways-rl/lr} rotates the entire line, so do not rotate the formula.
	 */
	private static boolean sideways(final CSSStyle style) {
		return net.zamasoft.foliojet.css.impl.property.text.BlockFlow.get(style).isVertical()
				&& net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant
						.get(style) == net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL
				&& net.zamasoft.foliojet.css.impl.property.text.TextOrientation
						.get(style) != FontStyle.TextOrientation.UPRIGHT;
	}

}
