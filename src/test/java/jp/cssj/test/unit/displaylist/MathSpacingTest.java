package jp.cssj.test.unit.displaylist;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import junit.framework.TestCase;
import net.sourceforge.jeuclid.MathMLParserSupport;
import net.sourceforge.jeuclid.context.LayoutContextImpl;
import net.sourceforge.jeuclid.context.Parameter;
import net.sourceforge.jeuclid.layout.JEuclidView;
import net.sourceforge.jeuclid.layout.LayoutStage;
import net.sourceforge.jeuclid.layout.LayoutableNode;

/**
 * Verify spacing between mathematical characters and operators (2026-10-04, TECH-20261003-004 items ⑪–⑭;
 * commas touched preceding characters in the Jigen Ango book). These are changes to JEuclid (third_party/jeuclid-core).
 *
 * <ul>
 * <li>⑪ Width measurement used glyph ink bounds, discarding right-side space (both sides for mo).
 * Measure using character advances.</li>
 * <li>⑫ em was 0.8389 times the font size. An em is the font size.</li>
 * <li>⑬ U+2212 (−) and U+00D7 (×) were absent from the operator dictionary and used the default
 * thickmathspace. Their dictionary value is mediummathspace (4/18 em).</li>
 * <li>⑭ Prefix form did not work (− was missing from the dictionary; the first element directly under math
 * was not treated as prefix). Prefix − has 0 on the left and veryverythinmathspace (1/18 em) on the right.</li>
 * </ul>
 */
public class MathSpacingTest extends TestCase {
	private static final float SIZE = 20f;

	private static final String MATH = "http://www.w3.org/1998/Math/MathML";

	private static JEuclidView layout(final String body) throws Exception {
		final Document doc = MathMLParserSupport
				.parseString("<math xmlns=\"" + MATH + "\">" + body + "</math>");
		final LayoutContextImpl context = new LayoutContextImpl(LayoutContextImpl.getDefaultLayoutContext());
		context.setParameter(Parameter.MATHSIZE, SIZE);
		final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		final JEuclidView view = new JEuclidView(doc, context, g);
		view.getWidth();
		return view;
	}

	private static float width(final String body) throws Exception {
		return layout(body).getWidth();
	}

	/** Width of the nth body element (zero-based, directly under math). */
	private static float childWidth(final String body, final int index) throws Exception {
		final JEuclidView view = layout(body);
		Node child = ((Node) view.getDocument()).getFirstChild();
		while (child != null && !(child instanceof Element)) {
			child = child.getNextSibling();
		}
		int i = 0;
		for (Node n = child.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof LayoutableNode node) {
				if (i++ == index) {
					return view.getInfo(node).getWidth(LayoutStage.STAGE2);
				}
			}
		}
		throw new AssertionError("no child " + index);
	}

	/** ⑪ Character width is advance width: "12" has the same width as "1" plus "2". */
	public void testTokenWidthIsTheAdvance() throws Exception {
		assertEquals(width("<mn>12</mn>"), width("<mn>1</mn><mn>2</mn>"), 0.01f);
	}

	/** ⑪ A zero-space operator takes only its advance: "1+2" has the same width as "1", "+", and "2" together. */
	public void testOperatorKeepsItsSideBearings() throws Exception {
		assertEquals(width("<mn>1+2</mn>"), width("<mn>1</mn><mo lspace=\"0\" rspace=\"0\">+</mo><mn>2</mn>"),
				0.01f);
	}

	/** ⑫ 1em is the font size. */
	public void testEmIsTheFontSize() throws Exception {
		assertEquals(SIZE, childWidth("<mn>1</mn><mspace width=\"1em\"/><mn>2</mn>", 1), 0.01f);
	}

	/** ⑬ Both sides of − and × use mediummathspace (4/18 em). */
	public void testMinusAndTimesUseMediumMathSpace() throws Exception {
		final float medium = SIZE * 4 / 18;
		for (final String op : new String[] { "−", "×", "+" }) {
			final float spaced = width("<mn>1</mn><mo>" + op + "</mo><mn>2</mn>");
			final float tight = width("<mn>1</mn><mo lspace=\"0\" rspace=\"0\">" + op + "</mo><mn>2</mn>");
			assertEquals("U+" + Integer.toHexString(op.charAt(0)), 2 * medium, spaced - tight, 0.01f);
		}
	}

	/**
	 * A trailing − is postfix, but U+2212 has no postfix form (MathML Core), so use infix spacing
	 * (a trailing − in a formula split across lines was cramped in the Jigen Ango book).
	 */
	public void testTrailingMinusKeepsInfixSpacing() throws Exception {
		final float medium = SIZE * 4 / 18;
		final float spaced = width("<mi>y</mi><mo>−</mo>");
		final float tight = width("<mi>y</mi><mo lspace=\"0\" rspace=\"0\">−</mo>");
		assertEquals(2 * medium, spaced - tight, 0.01f);
	}

	/** ⑭ A leading − is prefix: 0 on the left, veryverythinmathspace (1/18 em) on the right. */
	public void testLeadingMinusIsPrefix() throws Exception {
		final float veryverythin = SIZE / 18;
		final float bare = childWidth("<mo lspace=\"0\" rspace=\"0\">−</mo><mn>3</mn>", 0);
		assertEquals("math 直下の先頭", bare + veryverythin, childWidth("<mo>−</mo><mn>3</mn>", 0), 0.01f);
		assertEquals("form=\"prefix\"", bare + veryverythin,
				childWidth("<mi>a</mi><mo>=</mo><mo form=\"prefix\">−</mo><mn>3</mn>", 2), 0.01f);
	}
}
