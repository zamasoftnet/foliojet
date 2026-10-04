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
 * 数式の字と演算子の間隔を固定します(2026-10-04、TECH-20261003-004 の⑪〜⑭。時限暗号の本で
 * カンマが前の字に接していた)。JEuclid(third_party/jeuclid-core)に手を入れた箇所です。
 *
 * <ul>
 * <li>⑪ 字の幅を字形のインクの範囲で測り、右の余白(mo は左右とも)を捨てていた。字の送り幅で測る</li>
 * <li>⑫ em を字の大きさの 0.8389 倍にしていた。em は字の大きさ</li>
 * <li>⑬ U+2212(−)・U+00D7(×)が演算子の辞書に無く、既定の thickmathspace になっていた。
 * 辞書の値は mediummathspace(4/18 em)</li>
 * <li>⑭ 前置形が効かなかった(−が辞書に無い、math 直下の先頭を前置形と見ない)。前置形の − は
 * 左 0・右 veryverythinmathspace(1/18 em)</li>
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

	/** 本文の n 番目の要素(0 始まり、math 直下)の幅。 */
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

	/** ⑪ 字の幅は送り幅: 「12」と「1」「2」の幅は同じ。 */
	public void testTokenWidthIsTheAdvance() throws Exception {
		assertEquals(width("<mn>12</mn>"), width("<mn>1</mn><mn>2</mn>"), 0.01f);
	}

	/** ⑪ 余白 0 の演算子は字の送り幅だけを取る: 「1+2」と「1」「+」「2」の幅は同じ。 */
	public void testOperatorKeepsItsSideBearings() throws Exception {
		assertEquals(width("<mn>1+2</mn>"), width("<mn>1</mn><mo lspace=\"0\" rspace=\"0\">+</mo><mn>2</mn>"),
				0.01f);
	}

	/** ⑫ 1em は字の大きさ。 */
	public void testEmIsTheFontSize() throws Exception {
		assertEquals(SIZE, childWidth("<mn>1</mn><mspace width=\"1em\"/><mn>2</mn>", 1), 0.01f);
	}

	/** ⑬ −・× の左右は mediummathspace(4/18 em)。 */
	public void testMinusAndTimesUseMediumMathSpace() throws Exception {
		final float medium = SIZE * 4 / 18;
		for (final String op : new String[] { "−", "×", "+" }) {
			final float spaced = width("<mn>1</mn><mo>" + op + "</mo><mn>2</mn>");
			final float tight = width("<mn>1</mn><mo lspace=\"0\" rspace=\"0\">" + op + "</mo><mn>2</mn>");
			assertEquals("U+" + Integer.toHexString(op.charAt(0)), 2 * medium, spaced - tight, 0.01f);
		}
	}

	/** ⑭ 先頭の − は前置形: 左 0・右 veryverythinmathspace(1/18 em)。 */
	public void testLeadingMinusIsPrefix() throws Exception {
		final float veryverythin = SIZE / 18;
		final float bare = childWidth("<mo lspace=\"0\" rspace=\"0\">−</mo><mn>3</mn>", 0);
		assertEquals("math 直下の先頭", bare + veryverythin, childWidth("<mo>−</mo><mn>3</mn>", 0), 0.01f);
		assertEquals("form=\"prefix\"", bare + veryverythin,
				childWidth("<mi>a</mi><mo>=</mo><mo form=\"prefix\">−</mo><mn>3</mn>", 2), 0.01f);
	}
}
