package jp.cssj.test.unit.displaylist;

import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import junit.framework.TestCase;
import net.sourceforge.jeuclid.MathMLParserSupport;
import net.sourceforge.jeuclid.context.LayoutContextImpl;
import net.sourceforge.jeuclid.context.Parameter;
import net.sourceforge.jeuclid.font.MathTable;
import net.sourceforge.jeuclid.layout.JEuclidView;
import net.sourceforge.jeuclid.layout.LayoutInfo;
import net.sourceforge.jeuclid.layout.LayoutStage;
import net.sourceforge.jeuclid.layout.LayoutableNode;

/**
 * 添字の位置とイタリック補正を、書体の MATH 表で決めることを固定します(2026-10-04、
 * TECH-20261003-004 の⑮⑯)。JEuclid は添字を土台と添字のインクの高さだけで置いていたので、
 * y₁ は x₁ より沈み、P³ は x³ より高かった。MATH 表のある書体では TeX の規則 18 で置き、
 * 字 1 つの土台は高さ・深さで動かさない。上付きは土台の字のイタリック補正だけ右へずらす。
 */
public class MathScriptTest extends TestCase {
	private static final float SIZE = 20f;

	private static final String MATH = "http://www.w3.org/1998/Math/MathML";

	/** 単位は 1000/em。添字の定数とイタリック補正(全部の字に 100)。 */
	private static ByteBuffer syntheticFont() {
		final int glyphs = 16000; // 補正の表の位置は 16 ビット(数式用の英数字は実物の STIX で確かめる)
		final int constantsSize = 214;
		final int italicsSize = 4 + glyphs * 4 + 10;
		final int mathSize = 10 + constantsSize + 8 + italicsSize;
		final int head = 44, math = 100;
		final ByteBuffer b = ByteBuffer.allocate(math + mathSize);
		b.putInt(0, 0x00010000);
		b.putShort(4, (short) 2);
		b.putInt(12, 0x68656164); // head
		b.putInt(20, head);
		b.putInt(24, 54);
		b.putInt(28, 0x4d415448); // MATH
		b.putInt(36, math);
		b.putInt(40, mathSize);
		b.putShort(head + 18, (short) 1000);
		b.putShort(math, (short) 1);
		b.putShort(math + 4, (short) 10); // constants
		b.putShort(math + 6, (short) (10 + constantsSize)); // glyph info
		final int c = math + 10;
		b.putShort(c + 24, (short) 250); // SubscriptShiftDown
		b.putShort(c + 28, (short) 400); // SubscriptTopMax
		b.putShort(c + 32, (short) 50); // SubscriptBaselineDropMin
		b.putShort(c + 36, (short) 400); // SuperscriptShiftUp
		b.putShort(c + 44, (short) 100); // SuperscriptBottomMin
		b.putShort(c + 48, (short) 380); // SuperscriptBaselineDropMax
		b.putShort(c + 52, (short) 150); // SubSuperscriptGapMin
		b.putShort(c + 56, (short) 400); // SuperscriptBottomMaxWithSubscript
		b.putShort(c + 60, (short) 50); // SpaceAfterScript
		final int glyphInfo = math + 10 + constantsSize;
		b.putShort(glyphInfo, (short) 8); // italics correction info
		final int italics = glyphInfo + 8;
		b.putShort(italics, (short) (4 + glyphs * 4)); // coverage
		b.putShort(italics + 2, (short) glyphs);
		for (int i = 0; i < glyphs; ++i) {
			b.putShort(italics + 4 + i * 4, (short) 100);
		}
		final int coverage = italics + 4 + glyphs * 4;
		b.putShort(coverage, (short) 2);
		b.putShort(coverage + 2, (short) 1);
		b.putShort(coverage + 4, (short) 0);
		b.putShort(coverage + 6, (short) (glyphs - 1));
		b.putShort(coverage + 8, (short) 0);
		return b;
	}

	private final List<String> registered = new ArrayList<>();

	/** 試験の間だけ、どの書体にも合成の MATH 表を当てる。 */
	@Override
	protected void setUp() {
		final MathTable table = MathTable.parse(syntheticFont());
		assertNotNull(table);
		for (final String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
			MathTable.register(family, table);
			this.registered.add(family);
		}
		for (final String family : new String[] { "Serif", "SansSerif", "Dialog", "Monospaced", "DialogInput" }) {
			MathTable.register(family, table);
			this.registered.add(family);
		}
	}

	@Override
	protected void tearDown() {
		for (final String family : this.registered) {
			MathTable.unregister(family);
		}
	}

	private static JEuclidView layout(final String body) throws Exception {
		final var doc = MathMLParserSupport.parseString("<math xmlns=\"" + MATH + "\">" + body + "</math>");
		final LayoutContextImpl context = new LayoutContextImpl(LayoutContextImpl.getDefaultLayoutContext());
		context.setParameter(Parameter.MATHSIZE, SIZE);
		final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		final JEuclidView view = new JEuclidView(doc, context, g);
		view.getWidth();
		return view;
	}

	/** math 直下の index 番目の要素の、子(0=土台、1=添字…)の配置。 */
	private static LayoutInfo script(final JEuclidView view, final int index, final int child) {
		Node math = ((Node) view.getDocument()).getFirstChild();
		while (!(math instanceof Element)) {
			math = math.getNextSibling();
		}
		final Node script = nth(math, index);
		return view.getInfo((LayoutableNode) nth(script, child));
	}

	private static Node nth(final Node parent, final int index) {
		int i = 0;
		for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof LayoutableNode && i++ == index) {
				return n;
			}
		}
		throw new AssertionError("no child " + index);
	}

	/** ⑯ 字 1 つの土台の下付きは同じ高さ(土台のディセンダーで沈まない)。SubscriptShiftDown が下限。 */
	public void testSubscriptsOfCharactersShareTheBaseline() throws Exception {
		final JEuclidView view = layout("<msub><mi>x</mi><mn>1</mn></msub><msub><mi>y</mi><mn>1</mn></msub>"
				+ "<msub><mi>g</mi><mn>1</mn></msub><msub><mi>P</mi><mn>1</mn></msub>");
		final float x = script(view, 0, 1).getPosY(LayoutStage.STAGE2);
		assertEquals("SubscriptShiftDown", SIZE * 0.25f, x, 0.01f);
		for (int i = 1; i < 4; ++i) {
			assertEquals("subscript " + i, x, script(view, i, 1).getPosY(LayoutStage.STAGE2), 0.01f);
		}
	}

	/** ⑯ 字 1 つの土台の上付きも同じ高さ(土台の高さで上がらない)。SuperscriptShiftUp が下限。 */
	public void testSuperscriptsOfCharactersShareTheBaseline() throws Exception {
		final JEuclidView view = layout("<msup><mi>x</mi><mn>3</mn></msup><msup><mi>P</mi><mn>3</mn></msup>"
				+ "<msup><mi>f</mi><mn>3</mn></msup>");
		final float x = script(view, 0, 1).getPosY(LayoutStage.STAGE2);
		assertEquals("SuperscriptShiftUp", -SIZE * 0.4f, x, 0.01f);
		for (int i = 1; i < 3; ++i) {
			assertEquals("superscript " + i, x, script(view, i, 1).getPosY(LayoutStage.STAGE2), 0.01f);
		}
	}

	/** ⑯ 字でない土台(mrow)は、土台の高さに合わせて上付きを上げる(SuperscriptBaselineDropMax)。 */
	public void testNonCharacterBaseRaisesTheSuperscript() throws Exception {
		final JEuclidView view = layout("<msup><mi>x</mi><mn>3</mn></msup>"
				+ "<msup><mrow><mfrac><mi>a</mi><mi>b</mi></mfrac></mrow><mn>3</mn></msup>");
		final float character = script(view, 0, 1).getPosY(LayoutStage.STAGE2);
		final float fraction = script(view, 1, 1).getPosY(LayoutStage.STAGE2);
		assertTrue("fraction superscript is higher: " + fraction + " vs " + character, fraction < character - 1);
	}

	/**
	 * ⑮ 下付きは土台の字の送り幅の終わり(斜体の字の張り出しの下へ入る)、上付きはそこから
	 * イタリック補正(0.1em)だけ右(TeX の規則 18。19093 では下付きも字のインクの右端から置いていた)。
	 */
	public void testScriptsStartAtTheAdvanceAndTheSuperscriptAfterTheItalicCorrection() throws Exception {
		final String body = "<msubsup><mi mathvariant=\"normal\">f</mi><mn>1</mn><mn>2</mn></msubsup>";
		final JEuclidView view = layout(body);
		final var math = ((Node) view.getDocument()).getFirstChild();
		Node m = math;
		while (!(m instanceof Element)) {
			m = m.getNextSibling();
		}
		final var base = (net.sourceforge.jeuclid.elements.presentation.token.AbstractTokenWithTextLayout) nth(nth(m, 0), 0);
		final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		final LayoutContextImpl context = new LayoutContextImpl(LayoutContextImpl.getDefaultLayoutContext());
		context.setParameter(Parameter.MATHSIZE, SIZE);
		final float advanceEnd = base.getAdvanceEnd(g, context);
		assertTrue(advanceEnd > 0);
		assertEquals("subscript at the end of the advance", advanceEnd,
				script(view, 0, 1).getPosX(LayoutStage.STAGE2), 0.01f);
		assertEquals("superscript after the italic correction", advanceEnd + SIZE * 0.1f,
				script(view, 0, 2).getPosX(LayoutStage.STAGE2), 0.01f);
	}

	/** ⑯ 1 字の演算子も字の土台: (−x)³ の「)」の上付きは x³ と同じ高さ(括弧の高さで上がらない)。 */
	public void testSingleCharacterOperatorIsACharacterBase() throws Exception {
		final JEuclidView view = layout(
				"<msup><mi>x</mi><mn>3</mn></msup><mo>(</mo><mo>−</mo><mi>x</mi><msup><mo>)</mo><mn>3</mn></msup>");
		assertEquals(script(view, 0, 1).getPosY(LayoutStage.STAGE2), script(view, 4, 1).getPosY(LayoutStage.STAGE2),
				0.01f);
	}

	/**
	 * 実物の STIX Two Math で: 斜体の V は数式用の英数字(U+1D449)の字形で組まれ、上付きは下付きより
	 * その字のイタリック補正(0.1em)だけ右(書体パックが手元にあるときだけ)。斜体の面の無い数式用の書体で、
	 * 立体の字を機械的に傾けて補正の無いまま組んでいた(19093)。
	 */
	public void testStixItalicUsesMathAlphanumerics() throws Exception {
		final File dir = new File("/tmp/copper-pack/truetype/free");
		final File[] stix = dir.listFiles((d, n) -> n.contains("STIXTwoMath"));
		if (stix == null || stix.length == 0) {
			return;
		}
		net.sourceforge.jeuclid.font.FontFactory.getInstance().getFont(java.awt.Font.SERIF, java.awt.Font.PLAIN, 12f);
		final java.awt.Font font = net.sourceforge.jeuclid.font.FontFactory.getInstance()
				.registerFont(java.awt.Font.TRUETYPE_FONT, stix[0]);
		MathTable.register(font.getFamily(), MathTable.read(stix[0]));
		final var doc = MathMLParserSupport.parseString("<math xmlns=\"" + MATH + "\">"
				+ "<msub><mi>V</mi><mn>1</mn></msub><msup><mi>V</mi><mn>1</mn></msup></math>");
		final LayoutContextImpl context = new LayoutContextImpl(LayoutContextImpl.getDefaultLayoutContext());
		context.setParameter(Parameter.MATHSIZE, SIZE);
		context.setParameter(Parameter.FONTS_SERIF, List.of(font.getFamily()));
		final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		final JEuclidView view = new JEuclidView(doc, context, g);
		view.getWidth();
		final float sub = script(view, 0, 1).getPosX(LayoutStage.STAGE2);
		final float sup = script(view, 1, 1).getPosX(LayoutStage.STAGE2);
		assertEquals("superscript after the italic correction of V", SIZE * 0.1f, sup - sub, 0.05f);
	}

	/** 実物の STIX Two Math の MATH 表を読める(書体パックが手元にあるときだけ)。 */
	public void testReadsStixTwoMath() throws Exception {
		final File dir = new File("/tmp/copper-pack/truetype/free");
		final File[] stix = dir.listFiles((d, n) -> n.contains("STIXTwoMath"));
		if (stix == null || stix.length == 0) {
			return;
		}
		final MathTable table = MathTable.read(stix[0]);
		assertNotNull(table);
		final float down = table.get(MathTable.Constant.SUBSCRIPT_SHIFT_DOWN, 1000f);
		final float up = table.get(MathTable.Constant.SUPERSCRIPT_SHIFT_UP, 1000f);
		assertTrue("SubscriptShiftDown " + down, down > 100 && down < 400);
		assertTrue("SuperscriptShiftUp " + up, up > 250 && up < 500);
	}
}
