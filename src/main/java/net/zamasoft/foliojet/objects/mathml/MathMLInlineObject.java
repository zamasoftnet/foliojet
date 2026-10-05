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
 * 文書の中の MathML を JEuclid で組みます。
 *
 * <p>
 * <b>数式の大きさ・色・書体は、その math 要素の CSS から取る</b>(2026-10-04)。
 * 以前は JEuclid の既定(12pt・黒・JEuclid の書体)で組んでいたので、本文が
 * 9pt でも式は 12pt になり、CSS の指定は{@code mstyle}の{@code mathsize}
 * でしか変えられなかった。式の中の{@code mathsize}・{@code mathcolor}は
 * これまでどおり CSS の値に対して効く。
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
			// 文字の拡大は CSS の大きさに解析の時点で掛かっている。CSS が
			// 無いときだけ JEuclid の既定の大きさに掛ける
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
	 * 縦組みの行で、式を欧文と同じく横倒しにするか(2026-10-05)。{@code text-orientation: upright} は正立のまま。
	 * {@code sideways-rl/lr} は行ごと回すので、式は回さない。
	 */
	private static boolean sideways(final CSSStyle style) {
		return net.zamasoft.foliojet.css.impl.property.text.BlockFlow.get(style).isVertical()
				&& net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant
						.get(style) == net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL
				&& net.zamasoft.foliojet.css.impl.property.text.TextOrientation
						.get(style) != FontStyle.TextOrientation.UPRIGHT;
	}

}
