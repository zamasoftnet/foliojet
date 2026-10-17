package jp.cssj.test.unit.displaylist;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import junit.framework.TestCase;
import net.sourceforge.jeuclid.MathMLParserSupport;
import net.sourceforge.jeuclid.context.LayoutContextImpl;
import net.sourceforge.jeuclid.context.Parameter;
import net.sourceforge.jeuclid.elements.presentation.token.Mo;
import net.sourceforge.jeuclid.layout.JEuclidView;

/**
 * An infix operator between two operands is not a large operator (2026-10-17, TECH-20261017-004, from the publishing
 * department's book). The MathML 2 dictionary of JEuclid gives the circled operators an infix entry and a largeop
 * prefix entry (the n-ary forms of the MathML 2 entities, {@code &bigoplus;} is U+2295), and the infix entry, which
 * leaves largeop out, borrowed the prefix value: a ⊕ b was drawn with an n-ary sized ⊕. These are changes to JEuclid
 * (third_party/jeuclid-core).
 */
public class MathLargeOperatorTest extends TestCase {
	private static final String MATH = "http://www.w3.org/1998/Math/MathML";

	private static JEuclidView layout(final String body) throws Exception {
		final Document doc = MathMLParserSupport
				.parseString("<math xmlns=\"" + MATH + "\" display=\"block\">" + body + "</math>");
		final LayoutContextImpl context = new LayoutContextImpl(LayoutContextImpl.getDefaultLayoutContext());
		context.setParameter(Parameter.MATHSIZE, 20f);
		final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		final JEuclidView view = new JEuclidView(doc, context, g);
		view.getWidth();
		return view;
	}

	/** The operators of the expression in document order. */
	private static List<Mo> operators(final String body) throws Exception {
		final List<Mo> out = new ArrayList<>();
		collect((Node) layout(body).getDocument(), out);
		return out;
	}

	private static void collect(final Node node, final List<Mo> out) {
		if (node instanceof Mo mo) {
			out.add(mo);
		}
		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
			collect(child, out);
		}
	}

	public void testInfixCircledOperatorsAreNotLarge() throws Exception {
		for (final String op : new String[] { "⊕", "⊗", "⊖", "⊙", "⊎" }) {
			final Mo mo = operators("<mi>a</mi><mo>" + op + "</mo><mi>b</mi>").get(0);
			assertEquals(op + " largeop", "false", mo.getLargeop());
			assertEquals(op + " movablelimits", "false", mo.getMovablelimits());
		}
	}

	/** a ⊕ b is as wide as with largeop="false" written out, the workaround of the book. */
	public void testInfixWidthMatchesExplicitFalse() throws Exception {
		assertEquals(layout("<mi>a</mi><mo largeop=\"false\" stretchy=\"false\">⊕</mo><mi>b</mi>").getWidth(),
				layout("<mi>a</mi><mo>⊕</mo><mi>b</mi>").getWidth(), 0.01f);
	}

	/**
	 * The n-ary operators stay large between other elements (codex review): the MathML 2 dictionary has an infix entry
	 * for the n-ary union and its kin too, but MathML 3 lists them as prefix only.
	 */
	public void testNaryOperatorsBetweenElementsStayLarge() throws Exception {
		for (final String op : new String[] { "⋃", "⋂", "⋀", "⋁", "∐" }) {
			assertEquals(op + " largeop", "true",
					operators("<mi>A</mi><mo>=</mo><mo>" + op + "</mo><mi>B</mi>").get(1).getLargeop());
		}
	}

	/** The n-ary uses keep their size: a prefix ⊕, the base of munderover, and ∑ anywhere (it has no infix entry). */
	public void testNaryUsesStayLarge() throws Exception {
		assertEquals("prefix", "true", operators("<mo>⊕</mo><mi>V</mi>").get(0).getLargeop());
		assertEquals("munderover base", "true",
				operators("<munderover><mo>⊕</mo><mi>i</mi><mi>n</mi></munderover><mi>V</mi>").get(0)
						.getLargeop());
		assertEquals("sum between operands", "true",
				operators("<mi>y</mi><mo>∑</mo><mi>z</mi>").get(0).getLargeop());
	}
}
