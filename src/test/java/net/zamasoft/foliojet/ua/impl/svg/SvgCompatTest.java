package net.zamasoft.foliojet.ua.impl.svg;

import java.awt.Color;
import java.awt.Paint;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.CompositeGraphicsNode;
import org.apache.batik.gvt.CompositeShapePainter;
import org.apache.batik.gvt.FillShapePainter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.ShapeNode;
import org.apache.batik.gvt.ShapePainter;
import org.apache.batik.gvt.StrokeShapePainter;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;

import junit.framework.TestCase;

/**
 * SVG 2's {@code href} and CSS3 colours rewritten into what Batik 1.19 reads (2026-11-26, {@link SvgCompat}). Each
 * test builds Batik's graphics tree from the rewritten document, so it also shows that Batik reads the result; an
 * error Batik reports fails the test.
 */
public class SvgCompatTest extends TestCase {
	private static final String HEAD = "<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100'>";

	private Document doc;

	/** The shapes Batik draws, in document order. */
	private List<ShapeNode> build(final String svg) throws Exception {
		this.doc = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName())
				.createDocument("file:///test.svg", new StringReader(svg));
		SvgCompat.apply(this.doc);
		final BridgeContext ctx = new BridgeContext(new UserAgentAdapter() {
			@Override
			public void displayError(final Exception e) {
				throw new AssertionError(e);
			}
		});
		final List<ShapeNode> shapes = new ArrayList<ShapeNode>();
		collect(new GVTBuilder().build(ctx, this.doc), shapes);
		return shapes;
	}

	private static void collect(final GraphicsNode node, final List<ShapeNode> shapes) {
		if (node instanceof ShapeNode s) {
			shapes.add(s);
		} else if (node instanceof CompositeGraphicsNode c) {
			for (final Object child : c.getChildren()) {
				collect((GraphicsNode) child, shapes);
			}
		}
	}

	private static Paint paint(final ShapeNode node, final Class<?> type) {
		final ShapePainter p = node.getShapePainter();
		final List<ShapePainter> painters = new ArrayList<ShapePainter>();
		if (p instanceof CompositeShapePainter c) {
			for (int i = 0; i < c.getShapePainterCount(); ++i) {
				painters.add(c.getShapePainter(i));
			}
		} else if (p != null) {
			painters.add(p);
		}
		for (final ShapePainter q : painters) {
			if (type.isInstance(q)) {
				return q instanceof FillShapePainter f ? f.getPaint() : ((StrokeShapePainter) q).getPaint();
			}
		}
		return null;
	}

	private static Paint fill(final ShapeNode node) {
		return paint(node, FillShapePainter.class);
	}

	private static Paint stroke(final ShapeNode node) {
		return paint(node, StrokeShapePainter.class);
	}

	/** Graphviz's background polygon: white, with a transparent outline (a black frame before). */
	public void testTransparentStrokeIsNone() throws Exception {
		final List<ShapeNode> s = build(
				HEAD + "<polygon fill='white' stroke='transparent' points='0,0 90,0 90,90 0,90'/></svg>");
		assertEquals(Color.WHITE, fill(s.get(0)));
		assertNull(stroke(s.get(0)));
	}

	public void testAlphaGoesToOpacity() throws Exception {
		final List<ShapeNode> s = build(HEAD + "<g fill='rgba(255, 0, 0, 0.5)'><rect width='10' height='10'/></g>"
				+ "<rect width='10' height='10' fill='#00ff0080' fill-opacity='0.5'/>"
				+ "<rect width='10' height='10' fill='hsl(240 100% 50% / 25%)'/>"
				+ "<rect width='10' height='10' fill='transparent' stroke='rgb(0 0 255)'/></svg>");
		assertEquals(new Color(255, 0, 0, 128), fill(s.get(0)));
		assertEquals(new Color(0, 255, 0, 64), fill(s.get(1)));
		assertEquals(new Color(0, 0, 255, 64), fill(s.get(2)));
		assertNull(fill(s.get(3)));
		assertEquals(new Color(0, 0, 255), stroke(s.get(3)));
	}

	/** One value Batik refuses made it drop the whole sheet. */
	public void testStyleSheetAndStyleAttribute() throws Exception {
		final List<ShapeNode> s = build(HEAD
				+ "<style>.a{fill:rgba(255,0,0,.5) !important}@media all{.b{stroke:transparent;fill:#00f}}</style>"
				+ "<rect class='a' width='10' height='10' fill='green'/>"
				+ "<rect class='b' width='10' height='10' stroke='red'/>"
				+ "<rect width='10' height='10' style='font-family:&quot;x;y&quot;;fill:#f008'/></svg>");
		assertEquals(new Color(255, 0, 0, 128), fill(s.get(0)));
		assertEquals(Color.BLUE, fill(s.get(1)));
		assertNull(stroke(s.get(1)));
		assertEquals(new Color(255, 0, 0, 136), fill(s.get(2)));
	}

	/** SVG 2's href without xlink: Batik failed the whole SVG at the use and the gradient. */
	public void testHrefWithoutXlink() throws Exception {
		final List<ShapeNode> s = build(HEAD + "<defs><linearGradient id='g0'><stop offset='0' stop-color='blue'/>"
				+ "<stop offset='1' stop-color='rgba(0,0,255,0)'/></linearGradient>"
				+ "<linearGradient id='g' href='#g0'/><rect id='r' width='10' height='10' fill='url(#g)'/></defs>"
				+ "<use href='#r'/></svg>");
		assertEquals(1, s.size());
		// the gradient's stops come through the href (a gradient paint, not a plain colour)
		assertNotNull(fill(s.get(0)));
		assertFalse(String.valueOf(fill(s.get(0))), fill(s.get(0)) instanceof Color);
	}

	/** The alpha goes where the colour goes: a child with a colour of its own keeps its opacity (codex). */
	public void testAlphaFollowsInheritance() throws Exception {
		final List<ShapeNode> s = build(HEAD + "<g fill='rgba(255,0,0,.5)'><rect width='10' height='10' fill='blue'/>"
				+ "<rect width='10' height='10' style='fill:#0f0'/><rect width='10' height='10'/></g>"
				+ "<g fill-opacity='.5'><rect width='10' height='10' fill='rgba(255,0,0,.5)'/>"
				+ "<rect width='10' height='10' style='fill:rgba(255,0,0,.5)'/>"
				+ "<rect width='10' height='10' style='fill-opacity:.5;fill:rgba(0,0,255,.5)'/></g></svg>");
		assertEquals(Color.BLUE, fill(s.get(0)));
		assertEquals(Color.GREEN, fill(s.get(1)));
		assertEquals(new Color(255, 0, 0, 128), fill(s.get(2)));
		assertEquals(new Color(255, 0, 0, 64), fill(s.get(3)));
		assertEquals(new Color(255, 0, 0, 64), fill(s.get(4)));
		assertEquals(new Color(0, 0, 255, 64), fill(s.get(5)));
	}

	/** Braces in strings and comments, comments and line breaks in declarations, color: transparent (codex). */
	public void testSheetsWrittenAnyHow() throws Exception {
		final List<ShapeNode> s = build(HEAD + "<style>rect[data-k=\"{fill:transparent}\"]{fill:blue}"
				+ ".a{/* note: x; y */fill:rgba(255,0,0,\n.5)} .c{color:transparent} .d{fill:#00f}</style>"
				+ "<rect data-k='{fill:transparent}' width='10' height='10'/>"
				+ "<rect class='a' width='10' height='10'/><rect class='c d' width='10' height='10'/></svg>");
		assertEquals(Color.BLUE, fill(s.get(0)));
		assertEquals(new Color(255, 0, 0, 128), fill(s.get(1)));
		assertEquals(Color.BLUE, fill(s.get(2)));
	}

	/** draw.io's light-dark() in every style attribute, bulma's color(display-p3) after a fallback. */
	public void testLightDarkAndColorFunction() throws Exception {
		final List<ShapeNode> s = build(HEAD + "<rect width='10' height='10' style='fill: light-dark(rgb(0, 0, 255), "
				+ "rgb(255, 255, 255)); stroke: light-dark(#ffffff, var(--ge-dark-color, #121212));'/>"
				+ "<rect width='10' height='10' style='fill:#121212;fill:color(display-p3 0 0 1 / 50%)'/></svg>");
		assertEquals(Color.BLUE, fill(s.get(0)));
		assertEquals(Color.WHITE, stroke(s.get(0)));
		assertEquals(new Color(0, 0, 255, 128), fill(s.get(1)));
		assertNull(SvgCompat.convert("fill", "light-dark(var(--a), red)"));
		assertNull(SvgCompat.convert("fill", "color(xyz 0.1 0.2 0.3)"));
	}

	public void testRoundingAndRange() {
		assertEquals("rgb(128,0,0)", SvgCompat.convert("fill", "rgba(50%,0%,0%,1)").value());
		assertEquals("rgb(255,0,0)", SvgCompat.convert("fill", "rgba(2147483648,0,0,1)").value());
	}

	public void testValuesBatikReadsStayAsWritten() {
		assertNull(SvgCompat.convert("fill", "rgb(1,2,3)"));
		assertNull(SvgCompat.convert("fill", "rgb(10%, 20%, 30%)"));
		assertNull(SvgCompat.convert("fill", "#abc"));
		assertNull(SvgCompat.convert("fill", "url(#g) red"));
		assertNull(SvgCompat.convert("fill", "rgb(var(--x))"));
		// no transparent colour in SVG 1.1: the declaration goes, so that the sheet stays
		assertNull(SvgCompat.convert("color", "transparent").value());
		final String sheet = ".a{fill:red;stroke:rgb(1,2,3)} .b{background:url(a;b.png)}";
		assertSame(sheet, SvgCompat.rewriteSheet(sheet));
	}

	public void testRunningTwiceChangesNothingMore() throws Exception {
		build(HEAD + "<rect id='a' width='10' height='10' fill='rgba(255,0,0,0.5)' style='stroke:#0f08'/></svg>");
		final org.w3c.dom.Element a = this.doc.getElementById("a");
		final String fill = a.getAttribute("fill"), opacity = a.getAttribute("fill-opacity"),
				style = a.getAttribute("style");
		SvgCompat.apply(this.doc);
		assertEquals(fill, a.getAttribute("fill"));
		assertEquals(opacity, a.getAttribute("fill-opacity"));
		assertEquals(style, a.getAttribute("style"));
	}
}
