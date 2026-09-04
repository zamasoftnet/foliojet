package jp.cssj.test.unit._0390_writing_mode;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import jp.cssj.cti2.results.SingleResult;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.box.LogicalSide;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.text.TextAlign;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.WritingModeVariantValue;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlexItemBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.GridItemBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode.PhysicalSide;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.SidewaysGeometry;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** sideways-lr の論理順を保ったまま物理行内軸だけを反転する Stage 2 の受入検査。 */
public class SidewaysLrTest extends AbstractTestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final File FIXTURE = new File("files/unittest/0390-writing-mode/sideways-lr.html");
	private static final String CCW_MATRIX = "[0.00 -1.00 1.00 0.00 ";

	private Rect basicBox, startBox, endBox, surfaceBox, logicalHost, floatHost, staticHost;
	private Rect cwRtlBox, ccwRtlBox, cwRtlIndentBox;
	private Rect leftBox, rightBox, centerBox, justifyBox, indentBox;
	private LineSample basic, start, end, surface, left, right, center, justify, indent;
	private InlineSample framed;
	private Rect image, absolute, staticBlock, floating;
	private Rect cell11, cell12, cell21, cell22;
	private Rect flex1, flex2, flexReverse1, flexReverse2, grid1, grid2;
	private LineSample orientationMixed, orientationUpright, orientationSideways, column1, column2;
	private LineSample cwRtl, ccwRtl, cwRtlIndent;
	private final int[] fragmentPages = new int[3];

	public SidewaysLrTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		this.assertGeometryContract();
		this.assertReversalContract();
		this.assertLogicalSides();
		this.session.property("layout.bidi.paragraph", "true");
		final Path dumpDir = Files.createTempDirectory("foliojet-sideways-lr-");
		try (AutoCloseable dump = DisplayListDumper.scopedDir(dumpDir.toString());
				AutoCloseable geometry = DisplayListDumper.scopedDetailedGeometry(true)) {
			CTISessionHelper.transcodeFile(this.session, FIXTURE, "text/html", "UTF-8");
		}

		this.assertLinePlacement();
		this.assertInlinePlacement();
		this.assertLayoutPlacement();
		this.assertFragmentation();
		this.assertDisplayList(readDumps(dumpDir));
		this.assertPdf(this.renderPdf());
		this.assertPagedSvg(this.renderPagedSvg());
	}

	private void assertGeometryContract() {
		final AffineTransform ccw = SidewaysGeometry.runTransform(WritingModeVariant.SIDEWAYS_CCW,
				10, 20, 3, 4, 30);
		assertMatrix(ccw, 0, -1, 1, 0, 13, 50);
		assertPoint(ccw, 0, 0, 13, 50);
		assertPoint(ccw, 30, 0, 13, 20);
		assertPoint(ccw, 0, -3, 10, 50);
		assertPoint(ccw, 0, 4, 17, 50);
		assertBounds(SidewaysGeometry.bounds(WritingModeVariant.SIDEWAYS_CCW, 10, 20, 3, 4, 30),
				10, 20, 7, 30);
		assertSame(PhysicalSide.LEFT, TypesettingMode.overSide(WritingMode.LR,
				WritingModeVariant.SIDEWAYS_CCW));
	}

	private void assertReversalContract() {
		final BlockParams ccw = params(WritingMode.LR, WritingModeVariant.SIDEWAYS_CCW,
				AbstractTextParams.DIRECTION_LTR);
		assertEquals("[start,end] must be mirrored inside H", 70,
				LayoutUtils.inlineToPhysical(ccw, 100, 10, 30), .0001);
		assertEquals("a point is its own zero-length interval", 80,
				LayoutUtils.inlineToPhysical(ccw, 100, 20, 20), .0001);
		assertEquals("the point mapping is also the physical-to-logical inverse", 20,
				LayoutUtils.inlineToPhysical(ccw, 100, 80, 80), .0001);

		final BlockParams cwRtl = params(WritingMode.RL, WritingModeVariant.SIDEWAYS_CW,
				AbstractTextParams.DIRECTION_RTL);
		assertEquals("CW x RTL is also bottom-to-top", 70,
				LayoutUtils.inlineToPhysical(cwRtl, 100, 10, 30), .0001);

		final BlockParams cw = params(WritingMode.RL, WritingModeVariant.SIDEWAYS_CW,
				AbstractTextParams.DIRECTION_LTR);
		assertEquals("ordinary sideways-rl must retain Stage 1 placement", 10,
				LayoutUtils.inlineToPhysical(cw, 100, 10, 30), .0001);
		final BlockParams normal = params(WritingMode.LR, WritingModeVariant.NORMAL,
				AbstractTextParams.DIRECTION_RTL);
		assertEquals("legacy vertical-lr placement must remain byte-identical", 10,
				LayoutUtils.inlineToPhysical(normal, 100, 10, 30), .0001);
	}

	private void assertLogicalSides() {
		final CSSStyle ccw = style(BlockFlowValue.LR_VALUE, WritingModeVariantValue.SIDEWAYS_LR_VALUE,
				DirectionValue.LTR_VALUE);
		assertSame(Side.BOTTOM, LogicalSide.INLINE_START.toPhysical(ccw));
		assertSame(Side.TOP, LogicalSide.INLINE_END.toPhysical(ccw));
		assertSame(Side.LEFT, LogicalSide.BLOCK_START.toPhysical(ccw));
		assertSame(Side.RIGHT, LogicalSide.BLOCK_END.toPhysical(ccw));

		final CSSStyle cwRtl = style(BlockFlowValue.RL_VALUE, WritingModeVariantValue.SIDEWAYS_RL_VALUE,
				DirectionValue.RTL_VALUE);
		assertSame(Side.BOTTOM, LogicalSide.INLINE_START.toPhysical(cwRtl));
		assertSame(Side.TOP, LogicalSide.INLINE_END.toPhysical(cwRtl));
		assertTextAlign(cwRtl, TextAlignValue.START_VALUE, AbstractLineParams.TEXT_ALIGN_START);
		assertTextAlign(cwRtl, TextAlignValue.END_VALUE, AbstractLineParams.TEXT_ALIGN_END);
		assertTextAlign(cwRtl, TextAlignValue.LEFT_VALUE, AbstractLineParams.TEXT_ALIGN_START);
		assertTextAlign(cwRtl, TextAlignValue.RIGHT_VALUE, AbstractLineParams.TEXT_ALIGN_END);
		assertTextAlign(cwRtl, TextAlignValue.CENTER_VALUE, AbstractLineParams.TEXT_ALIGN_CENTER);
		assertTextAlign(cwRtl, TextAlignValue.JUSTIFY_VALUE, AbstractLineParams.TEXT_ALIGN_JUSTIFY);

		final CSSStyle ccwRtl = style(BlockFlowValue.LR_VALUE, WritingModeVariantValue.SIDEWAYS_LR_VALUE,
				DirectionValue.RTL_VALUE);
		assertTextAlign(ccwRtl, TextAlignValue.LEFT_VALUE, AbstractLineParams.TEXT_ALIGN_START);
		assertTextAlign(ccwRtl, TextAlignValue.RIGHT_VALUE, AbstractLineParams.TEXT_ALIGN_END);

		final CSSStyle verticalLr = style(BlockFlowValue.LR_VALUE, WritingModeVariantValue.NORMAL_VALUE,
				DirectionValue.LTR_VALUE);
		assertSame("normal vertical-lr must retain its top inline-start",
				Side.TOP, LogicalSide.INLINE_START.toPhysical(verticalLr));

		final CSSStyle verticalLrRtl = style(BlockFlowValue.LR_VALUE, WritingModeVariantValue.NORMAL_VALUE,
				DirectionValue.RTL_VALUE);
		assertSame("normal vertical-lr RTL must retain its bottom inline-start",
				Side.BOTTOM, LogicalSide.INLINE_START.toPhysical(verticalLrRtl));
		assertTextAlign(verticalLrRtl, TextAlignValue.LEFT_VALUE, AbstractLineParams.TEXT_ALIGN_END);
		assertTextAlign(verticalLrRtl, TextAlignValue.RIGHT_VALUE, AbstractLineParams.TEXT_ALIGN_START);
	}

	private void assertLinePlacement() {
		assertNotNull(this.basicBox);
		assertNotNull(this.basic);
		assertNotNull(this.startBox);
		assertNotNull(this.start);
		assertNotNull(this.endBox);
		assertNotNull(this.end);
		assertEquals("A starts at the physical bottom of the line box",
				this.basicBox.y + this.basicBox.height, this.basic.y + this.basic.line.getLineSize(), .01);
		assertEquals("text-align:start is the physical bottom",
				this.startBox.y + this.startBox.height, this.start.y + this.start.line.getLineSize(), .01);
		assertEquals("text-align:end is the physical top", this.endBox.y, this.end.y, .01);
		assertTrue("start and end must be physically reversed", this.start.y > this.end.y);
		assertNotNull(this.left);
		assertNotNull(this.right);
		assertNotNull(this.center);
		assertNotNull(this.justify);
		assertNotNull(this.indent);
		assertNotNull(this.leftBox);
		assertNotNull(this.rightBox);
		assertNotNull(this.centerBox);
		assertNotNull(this.justifyBox);
		assertNotNull(this.indentBox);
		assertEquals("line-left is the physical bottom for sideways-lr",
				this.leftBox.y + this.leftBox.height, this.left.y + this.left.line.getLineSize(), .01);
		assertEquals("line-right is the physical top for sideways-lr", this.rightBox.y, this.right.y, .01);
		assertEquals("center alignment is mirrored about the same physical center",
				this.centerBox.y + (this.centerBox.height - this.center.line.getLineSize()) / 2,
				this.center.y, .01);
		assertEquals("justify consumes the whole physical inline extent", this.justifyBox.y, this.justify.y, .01);
		assertEquals(this.justifyBox.height, this.justify.line.getLineSize(), .01);
		assertEquals("text-indent moves logical start upward from the physical bottom",
				this.indentBox.y + this.indentBox.height - 10,
				this.indent.y + this.indent.line.getLineSize(), .01);
		assertNotNull(this.cwRtlBox);
		assertNotNull(this.cwRtl);
		assertEquals("sideways-rl x RTL also starts at the physical bottom",
				this.cwRtlBox.y + this.cwRtlBox.height, this.cwRtl.y + this.cwRtl.line.getLineSize(), .01);
		assertNotNull(this.ccwRtlBox);
		assertNotNull(this.ccwRtl);
		assertEquals("sideways-lr x RTL has top-to-bottom inline progression",
				this.ccwRtlBox.y, this.ccwRtl.y, .01);
		assertNotNull(this.cwRtlIndentBox);
		assertNotNull(this.cwRtlIndent);
		assertEquals("CW x RTL keeps text-indent in logical coordinates", 10,
				this.cwRtlIndent.line.getLineAlign(), .01);
		assertEquals("CW x RTL text-indent moves upward from the physical bottom",
				this.cwRtlIndentBox.y + this.cwRtlIndentBox.height - 10,
				this.cwRtlIndent.y + this.cwRtlIndent.line.getLineSize(), .01);
	}

	private void assertInlinePlacement() {
		assertNotNull(this.surfaceBox);
		assertNotNull(this.surface);
		assertNotNull(this.framed);
		assertNotNull(this.image);
		assertSame(WritingModeVariant.SIDEWAYS_CCW, this.framed.box.getTextParams().writingModeVariant);
		assertEquals("margin-inline-start maps to the physical bottom", 8,
				this.framed.box.getFrame().margin.bottom, .01);
		assertEquals("padding-inline-start maps to the physical bottom", 3,
				this.framed.box.getFrame().padding.bottom, .01);
		assertEquals(2, this.framed.box.getFrame().getFrameLeft(), .01);
		assertEquals(4, this.framed.box.getFrame().getFrameRight(), .01);
		assertEquals(this.framed.box.getAscent() + this.framed.box.getDescent(),
				this.framed.box.getWidth(), .01);

		final double logicalStart = firstTextAdvance(this.surface.line);
		assertEquals("inline interval [s,s+h] must map to H-(s+h)",
				this.surface.y + this.surface.line.getLineSize()
						- (logicalStart + this.framed.box.getHeight()),
				this.framed.y, .01);
		assertEquals("replaced width must not be rotated", 12, this.image.width, .01);
		assertEquals("replaced height must not be rotated", 20, this.image.height, .01);
		assertTrue("later logical content must be placed farther toward the physical top",
				this.image.y < this.framed.y);

		final AffineTransform at = SidewaysGeometry.runTransform(WritingModeVariant.SIDEWAYS_CCW,
				this.framed.x, this.framed.y, this.framed.box.getAscent(), this.framed.box.getDescent(),
				this.framed.box.getLineSize());
		assertPoint(at, 0, -this.framed.box.getAscent(), this.framed.x,
				this.framed.y + this.framed.box.getLineSize());
	}

	private void assertLayoutPlacement() {
		assertNotNull(this.logicalHost);
		assertNotNull(this.absolute);
		assertEquals("inset-inline-start:0 maps to the physical bottom",
				this.logicalHost.y + this.logicalHost.height, this.absolute.y + this.absolute.height, .01);
		assertNotNull(this.staticHost);
		assertNotNull(this.staticBlock);
		assertEquals("an auto-inset absolute static point is mirrored to H-p",
				this.staticHost.y + this.staticHost.height, this.staticBlock.y, .01);
		assertNotNull(this.floatHost);
		assertNotNull(this.floating);
		assertEquals("float:inline-start maps to the physical bottom",
				this.floatHost.y + this.floatHost.height, this.floating.y + this.floating.height, .01);

		assertNotNull(this.cell11);
		assertNotNull(this.cell12);
		assertNotNull(this.cell21);
		assertNotNull(this.cell22);
		assertTrue("the first table column must be below the second", this.cell11.y > this.cell12.y);
		assertTrue("the first table column must be below the second in every row", this.cell21.y > this.cell22.y);
		assertTrue("table rows must retain left-to-right page progression", this.cell21.x > this.cell11.x);

		assertNotNull(this.flex1);
		assertNotNull(this.flex2);
		assertTrue("flex source order remains logical while physical row order is mirrored",
				this.flex1.y > this.flex2.y);
		assertNotNull(this.flexReverse1);
		assertNotNull(this.flexReverse2);
		assertTrue("row-reverse is resolved logically before physical mirroring",
				this.flexReverse1.y < this.flexReverse2.y);
		assertNotNull(this.grid1);
		assertNotNull(this.grid2);
		assertTrue("grid source order remains logical while physical column order is mirrored",
				this.grid1.y > this.grid2.y);
		assertNotNull(this.column1);
		assertNotNull(this.column2);
		assertTrue("the first multicol column must be on the physical bottom side",
				this.column1.y > this.column2.y);

		assertNotNull(this.orientationMixed);
		assertNotNull(this.orientationUpright);
		assertNotNull(this.orientationSideways);
		assertEquals(this.orientationMixed.line.getAscent(), this.orientationUpright.line.getAscent(), .01);
		assertEquals(this.orientationMixed.line.getAscent(), this.orientationSideways.line.getAscent(), .01);
		assertEquals(this.orientationMixed.line.getLineSize(), this.orientationUpright.line.getLineSize(), .01);
		assertEquals(this.orientationMixed.line.getLineSize(), this.orientationSideways.line.getLineSize(), .01);
	}

	private void assertFragmentation() {
		assertEquals(1, this.fragmentPages[0]);
		assertEquals(2, this.fragmentPages[1]);
		assertEquals(3, this.fragmentPages[2]);
	}

	private void assertDisplayList(final String dump) {
		for (final String marker : new String[] { "\"AB\"", "\"START\"", "\"END\"", "\"CLIP\"" }) {
			final String line = lineContaining(dump, marker);
			assertTrue(line, line.contains("run-tf=" + CCW_MATRIX));
			assertTrue(line, line.contains("run-bounds=["));
		}
		final List<String> orientationLines = dump.lines()
				.filter(line -> line.contains("\"A\u6F22B\"")).toList();
		assertEquals("text-orientation must not change sideways glyph geometry", 3, orientationLines.size());
		for (final String line : orientationLines) {
			assertTrue(line, line.contains("run-tf=" + CCW_MATRIX));
		}
		assertTrue("decorations must use the CCW run rotation",
				dump.contains("TextDecorationDrawable decoration-tf=" + CCW_MATRIX));
		assertTrue("leader glyphs must use the CCW run rotation",
				dump.contains("Leader[") && dump.contains("leader-tf=" + CCW_MATRIX));
		assertTrue("ruby must use the CCW horizontal model",
				dump.contains("RubyUnit[") && dump.contains("ruby-tf=" + CCW_MATRIX));
		assertTrue("warichu must use the CCW horizontal model",
				dump.contains("Warichu[") && dump.contains("warichu-tf=" + CCW_MATRIX));
		assertFalse("horizontal child must reset the sideways variant",
				lineContaining(dump, "RESET").contains("run-tf="));
		assertTrue("sideways-rl x RTL keeps the CW glyph rotation",
				lineContaining(dump, "\"CWRTL\"").contains("run-tf=[0.00 1.00 -1.00 0.00 "));
	}

	private void assertPdf(final byte[] bytes) throws Exception {
		try (PDDocument pdf = Loader.loadPDF(bytes)) {
			assertEquals("the fixture must fragment into three pages", 3, pdf.getNumberOfPages());
			assertTrue("PDF user-space CCW must be emitted as 0 1 -1 0 in y-up coordinates",
					countCcwMatrices(pdf) >= 4);
			// pdfg2d は Tm を書かず(Td で送る)、回転は cm に置く。回転した Tm が無いことを確認する
			assertEquals("sideways text must not carry the rotation in Tm", 0, countRotatedTextMatrices(pdf));
			final String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", "");
			assertTrue("logical AB order must survive PDF extraction: " + text, text.contains("AB"));
			assertTrue("table source order must survive PDF extraction: " + text, text.contains("11122122"));
			assertTrue("flex source order must survive PDF extraction: " + text, text.contains("F1F2"));
			assertTrue("grid source order must survive PDF extraction: " + text, text.contains("G1G2"));
			// PDF の抽出順は描画(視覚)順なので row-reverse は F4F3 になる。両方あることだけ見る
			assertTrue("row-reverse items must survive PDF extraction: " + text,
					text.contains("F3") && text.contains("F4"));
			assertTrue("all text-orientation values must retain logical text: " + text,
					count(text, "A\u6F22B") >= 3);
			assertTrue("page source order must survive PDF extraction: " + text,
					text.indexOf("PAGEONE") < text.indexOf("PAGETWO")
							&& text.indexOf("PAGETWO") < text.indexOf("PAGETHREE"));
		}
	}

	private void assertPagedSvg(final CapturingResults results) {
		assertEquals("Paged SVG must retain three-page progression", 3, results.pageCount());
		final String svg = results.text("pages/0001.svg");
		final String probe = textElements(svg).stream().filter(element -> "XYZ".equals(element[1]))
				.map(element -> element[0]).findFirst()
				.orElseThrow(() -> new AssertionError("Paged SVG XYZ probe was not found"));
		final double[] matrix = assertCcwMatrix(probe);
		final double[] xs = numbers(attribute(probe, "x"));
		final double[] ys = numbers(attribute(probe, "y"));
		assertTrue("XYZ must be emitted as one logical horizontal run", xs.length >= 3);
		double previous = Double.POSITIVE_INFINITY;
		double firstPhysicalY = Double.NaN;
		for (int i = 0; i < xs.length; ++i) {
			final double localY = ys.length == 1 ? ys[0] : ys[i];
			final double physicalY = matrix[1] * xs[i] + matrix[3] * localY + matrix[5];
			assertTrue("logical +x must advance upward on the physical page", physicalY < previous);
			if (i == 0) {
				firstPhysicalY = physicalY;
			}
			previous = physicalY;
		}
		assertEquals("B must start at A minus advance(A)", firstPhysicalY - (xs[1] - xs[0]),
				matrix[1] * xs[1] + matrix[3] * (ys.length == 1 ? ys[0] : ys[1]) + matrix[5], .01);

		final List<String> combine = textElements(svg).stream()
				.filter(element -> "89".equals(element[1]) || "8".equals(element[1]) || "9".equals(element[1]))
				.map(element -> element[0]).toList();
		assertFalse("Paged SVG text-combine probe was not found", combine.isEmpty());
		for (final String tag : combine) {
			assertFalse("text-combine-upright must reset the sideways run transform: " + tag,
					attribute(tag, "transform").startsWith("matrix(0 -1 1 0 "));
		}

		// CJK はウェブフォント subset 経路で <text> の中身が PUA 符号になるので、data-copper-text でも探す
		final String rubyBase = textElements(svg).stream()
				.filter(element -> "\u89AA".equals(element[1])
						|| "\u89AA".equals(attribute(element[0], "data-copper-text")))
				.map(element -> element[0]).findFirst()
				.orElseThrow(() -> new AssertionError("Paged SVG ruby base was not found"));
		final String rubyText = textElements(svg).stream()
				.filter(element -> "\u304A\u3084".equals(element[1]) || "\u304A".equals(element[1])
						|| "\u304A\u3084".equals(attribute(element[0], "data-copper-text"))
						|| "\u304A".equals(attribute(element[0], "data-copper-text")))
				.map(element -> element[0]).findFirst()
				.orElseThrow(() -> new AssertionError("Paged SVG ruby annotation was not found"));
		assertTrue("ruby over annotation must be on the physical left",
				physicalTextX(rubyText) < physicalTextX(rubyBase));
	}

	public boolean check_basic(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			this.basicBox = preferContainer(this.basicBox, box, x, y);
			return false;
		}
		this.basic = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_align_start(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			this.startBox = preferContainer(this.startBox, box, x, y);
			return false;
		}
		this.start = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_align_end(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			this.endBox = preferContainer(this.endBox, box, x, y);
			return false;
		}
		this.end = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_surface(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			this.surfaceBox = preferContainer(this.surfaceBox, box, x, y);
			return false;
		}
		this.surface = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_align_left(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			this.leftBox = preferContainer(this.leftBox, box, x, y);
			return false;
		}
		this.left = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_align_right(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			this.rightBox = preferContainer(this.rightBox, box, x, y);
			return false;
		}
		this.right = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_align_center(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			this.centerBox = preferContainer(this.centerBox, box, x, y);
			return false;
		}
		this.center = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_align_justify(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			this.justifyBox = preferContainer(this.justifyBox, box, x, y);
			return false;
		}
		this.justify = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_indent(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			this.indentBox = preferContainer(this.indentBox, box, x, y);
			return false;
		}
		this.indent = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_cw_rtl(final IBox box, final int page, final double x, final double y) {
		if (box instanceof AbsoluteBlockBox) {
			this.cwRtlBox = rect(box, x, y);
			return false;
		}
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertSame(WritingMode.RL, line.getLineParams().flow);
		assertSame(WritingModeVariant.SIDEWAYS_CW, line.getLineParams().writingModeVariant);
		assertEquals(AbstractTextParams.DIRECTION_RTL, line.getLineParams().direction);
		this.cwRtl = new LineSample(line, x, y);
		return true;
	}

	public boolean check_ccw_rtl(final IBox box, final int page, final double x, final double y) {
		if (box instanceof AbsoluteBlockBox) {
			this.ccwRtlBox = rect(box, x, y);
			return false;
		}
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertSame(WritingMode.LR, line.getLineParams().flow);
		assertSame(WritingModeVariant.SIDEWAYS_CCW, line.getLineParams().writingModeVariant);
		assertEquals(AbstractTextParams.DIRECTION_RTL, line.getLineParams().direction);
		this.ccwRtl = new LineSample(line, x, y);
		return true;
	}

	public boolean check_cw_rtl_indent(final IBox box, final int page, final double x, final double y) {
		if (box instanceof AbsoluteBlockBox) {
			this.cwRtlIndentBox = rect(box, x, y);
			return false;
		}
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertSame(WritingMode.RL, line.getLineParams().flow);
		assertSame(WritingModeVariant.SIDEWAYS_CW, line.getLineParams().writingModeVariant);
		assertEquals(AbstractTextParams.DIRECTION_RTL, line.getLineParams().direction);
		this.cwRtlIndent = new LineSample(line, x, y);
		return true;
	}

	public boolean check_framed(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof InlineBox inline)) {
			return false;
		}
		this.framed = new InlineSample(inline, x, y);
		return true;
	}

	public boolean check_upright_image(final IBox box, final int page, final double x, final double y) {
		if (box.getType() != BoxType.REPLACED) {
			return false;
		}
		this.image = rect(box, x, y);
		return true;
	}

	public boolean check_combine(final IBox box, final int page, final double x, final double y) {
		if (!(box.getParams() instanceof AbstractTextParams params)) {
			return false;
		}
		assertSame(WritingMode.TB, params.flow);
		assertSame(WritingModeVariant.NORMAL, params.writingModeVariant);
		return true;
	}

	public boolean check_ellipsis(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		this.sidewaysLine(line, x, y);
		assertNotNull("sideways-lr overflow line must create an ellipsis", line.getEllipsis());
		return true;
	}

	public boolean check_logical_host(final IBox box, final int page, final double x, final double y) {
		this.logicalHost = preferContainer(this.logicalHost, box, x, y);
		return this.logicalHost != null;
	}

	public boolean check_logical_absolute(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbsoluteBlockBox)) {
			return false;
		}
		this.absolute = rect(box, x, y);
		return true;
	}

	public boolean check_static_host(final IBox box, final int page, final double x, final double y) {
		this.staticHost = preferContainer(this.staticHost, box, x, y);
		return this.staticHost != null;
	}

	public boolean check_static_block(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbsoluteBlockBox)) {
			return false;
		}
		this.staticBlock = rect(box, x, y);
		return true;
	}

	public boolean check_float_host(final IBox box, final int page, final double x, final double y) {
		this.floatHost = preferContainer(this.floatHost, box, x, y);
		return this.floatHost != null;
	}

	public boolean check_inline_float(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof FloatBlockBox)) {
			return false;
		}
		this.floating = rect(box, x, y);
		return true;
	}

	public boolean check_cell_11(final IBox box, final int page, final double x, final double y) {
		return (this.cell11 = tableCell(box, x, y)) != null;
	}

	public boolean check_cell_12(final IBox box, final int page, final double x, final double y) {
		return (this.cell12 = tableCell(box, x, y)) != null;
	}

	public boolean check_cell_21(final IBox box, final int page, final double x, final double y) {
		return (this.cell21 = tableCell(box, x, y)) != null;
	}

	public boolean check_cell_22(final IBox box, final int page, final double x, final double y) {
		return (this.cell22 = tableCell(box, x, y)) != null;
	}

	public boolean check_flex_1(final IBox box, final int page, final double x, final double y) {
		return (this.flex1 = item(box, FlexItemBox.class, x, y)) != null;
	}

	public boolean check_flex_2(final IBox box, final int page, final double x, final double y) {
		return (this.flex2 = item(box, FlexItemBox.class, x, y)) != null;
	}

	public boolean check_grid_1(final IBox box, final int page, final double x, final double y) {
		return (this.grid1 = item(box, GridItemBox.class, x, y)) != null;
	}

	public boolean check_grid_2(final IBox box, final int page, final double x, final double y) {
		return (this.grid2 = item(box, GridItemBox.class, x, y)) != null;
	}

	public boolean check_flex_reverse_1(final IBox box, final int page, final double x, final double y) {
		return (this.flexReverse1 = item(box, FlexItemBox.class, x, y)) != null;
	}

	public boolean check_flex_reverse_2(final IBox box, final int page, final double x, final double y) {
		return (this.flexReverse2 = item(box, FlexItemBox.class, x, y)) != null;
	}

	public boolean check_orientation_mixed(final IBox box, final int page, final double x, final double y) {
		return this.captureOrientation(box, x, y, 0);
	}

	public boolean check_orientation_upright(final IBox box, final int page, final double x, final double y) {
		return this.captureOrientation(box, x, y, 1);
	}

	public boolean check_orientation_sideways(final IBox box, final int page, final double x, final double y) {
		return this.captureOrientation(box, x, y, 2);
	}

	private boolean captureOrientation(final IBox box, final double x, final double y, final int index) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		final LineSample sample = this.sidewaysLine(line, x, y);
		switch (index) {
		case 0 -> this.orientationMixed = sample;
		case 1 -> this.orientationUpright = sample;
		case 2 -> this.orientationSideways = sample;
		default -> throw new IllegalArgumentException();
		}
		return true;
	}

	public boolean check_column_1(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		this.column1 = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_column_2(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		this.column2 = this.sidewaysLine(line, x, y);
		return true;
	}

	public boolean check_reset(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertSame(WritingMode.TB, line.getLineParams().flow);
		assertSame(WritingModeVariant.NORMAL, line.getLineParams().writingModeVariant);
		for (final Text run : textRuns(line)) {
			assertSame(FontStyle.Direction.LTR, run.getFontStyle().getDirection());
		}
		return true;
	}

	public boolean check_page_one(final IBox box, final int page, final double x, final double y) {
		return this.capturePage(box, page, 0);
	}

	public boolean check_page_two(final IBox box, final int page, final double x, final double y) {
		return this.capturePage(box, page, 1);
	}

	public boolean check_page_three(final IBox box, final int page, final double x, final double y) {
		return this.capturePage(box, page, 2);
	}

	private boolean capturePage(final IBox box, final int page, final int index) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		this.sidewaysLine(line, 0, 0);
		assertSame(WritingMode.LR, line.getLineParams().flow);
		this.fragmentPages[index] = page;
		return true;
	}

	private LineSample sidewaysLine(final AbstractLineBox line, final double x, final double y) {
		assertSame(WritingMode.LR, line.getLineParams().flow);
		assertSame(WritingModeVariant.SIDEWAYS_CCW, line.getLineParams().writingModeVariant);
		assertTrue(line.getLineParams().isHorizontalTypesetting());
		final List<Text> runs = textRuns(line);
		assertFalse(runs.isEmpty());
		for (final Text run : runs) {
			assertTrue(run.getFontStyle().getDirection() != FontStyle.Direction.TB);
			assertSame(FontStyle.TextOrientation.MIXED, run.getFontStyle().getTextOrientation());
		}
		return new LineSample(line, x, y);
	}

	private static BlockParams params(final WritingMode flow, final WritingModeVariant variant,
			final byte direction) {
		final BlockParams params = new BlockParams();
		params.flow = flow;
		params.writingModeVariant = variant;
		params.direction = direction;
		return params;
	}

	private CSSStyle style(final BlockFlowValue flow, final WritingModeVariantValue variant,
			final DirectionValue direction) {
		final CSSStyle style = CSSStyle.getCSSStyle(this.ua, null, CSSElement.ANON);
		style.set(BlockFlow.INFO, flow);
		style.set(net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant.INFO, variant);
		style.set(Direction.INFO, direction);
		return style;
	}

	private static void assertTextAlign(final CSSStyle style, final TextAlignValue value, final byte expected) {
		style.set(TextAlign.INFO, value);
		assertEquals(expected, TextAlign.get(style));
	}

	private static Rect preferContainer(final Rect current, final IBox box, final double x, final double y) {
		if (box instanceof AbstractLineBox || box.getType() == BoxType.REPLACED) {
			return current;
		}
		final Rect candidate = rect(box, x, y);
		return current == null || candidate.height > current.height ? candidate : current;
	}

	/** 訪問者は td 自体では check_ を呼ばないので、セル内の span(INLINE)で位置を取る。 */
	private static Rect tableCell(final IBox box, final double x, final double y) {
		return box instanceof TableCellBox || box.getType() == net.zamasoft.foliojet.layout.box.BoxType.INLINE
				? rect(box, x, y)
				: null;
	}

	/** 訪問者は flex/grid 項目の箱には check_ を届けないので、項目内の span(INLINE)で位置を取る。 */
	private static Rect item(final IBox box, final Class<?> type, final double x, final double y) {
		return type.isInstance(box) || box.getType() == net.zamasoft.foliojet.layout.box.BoxType.INLINE
				? rect(box, x, y)
				: null;
	}

	private static Rect rect(final IBox box, final double x, final double y) {
		return new Rect(x, y, box.getWidth(), box.getHeight());
	}

	private static double firstTextAdvance(final AbstractLineBox line) {
		for (final Object content : line.getLogicalContents()) {
			if (content instanceof Text text) {
				return text.getAdvance();
			}
		}
		throw new AssertionError("the leading text run was not found");
	}

	private static List<Text> textRuns(final AbstractLineBox line) {
		final List<Text> runs = new ArrayList<>();
		collectTextRuns(line.getLogicalContents(), runs);
		return runs;
	}

	private static void collectTextRuns(final List<Object> contents, final List<Text> runs) {
		for (final Object content : contents) {
			if (content instanceof Text text) {
				runs.add(text);
			} else if (content instanceof AbstractTextBox.Inline inline && inline.box instanceof InlineBox nested) {
				collectTextRuns(nested.getLogicalContents(), runs);
			}
		}
	}

	private byte[] renderPdf() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = this.newSession();
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, FIXTURE, "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return out.toByteArray();
	}

	private CapturingResults renderPagedSvg() throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = this.newSession();
		try {
			session.setResults(results);
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeFile(session, FIXTURE, "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results;
	}

	private DirectSession newSession() throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
		session.property("input.include", "**");
		session.property("layout.bidi.paragraph", "true");
		return session;
	}

	private static int countCcwMatrices(final PDDocument pdf) throws Exception {
		return countMatrices(pdf, "cm", 0, 1, -1, 0);
	}

	private static int countRotatedTextMatrices(final PDDocument pdf) throws Exception {
		return countMatrices(pdf, "Tm", 0, 1, -1, 0) + countMatrices(pdf, "Tm", 0, -1, 1, 0);
	}

	private static int countMatrices(final PDDocument pdf, final String operatorName,
			final double a, final double b, final double c, final double d) throws Exception {
		int count = 0;
		for (final var page : pdf.getPages()) {
			final PDFStreamParser parser = new PDFStreamParser(page);
			final List<Object> operands = new ArrayList<>();
			Object token;
			while ((token = parser.parseNextToken()) != null) {
				if (!(token instanceof Operator operator)) {
					operands.add(token);
					continue;
				}
				if (operatorName.equals(operator.getName()) && operands.size() >= 6) {
					final int off = operands.size() - 6;
					if (near(number(operands.get(off)), a) && near(number(operands.get(off + 1)), b)
							&& near(number(operands.get(off + 2)), c)
							&& near(number(operands.get(off + 3)), d)) {
						++count;
					}
				}
				operands.clear();
			}
		}
		return count;
	}

	private static double number(final Object value) {
		return value instanceof COSNumber number ? number.floatValue() : Double.NaN;
	}

	private static boolean near(final double actual, final double expected) {
		return Math.abs(actual - expected) < .0001;
	}

	private static double[] assertCcwMatrix(final String tag) {
		final double[] matrix = numbers(attribute(tag, "transform").replace("matrix(", "").replace(")", ""));
		assertEquals(tag, 6, matrix.length);
		assertEquals(0, matrix[0], .0001);
		assertEquals(-1, matrix[1], .0001);
		assertEquals(1, matrix[2], .0001);
		assertEquals(0, matrix[3], .0001);
		return matrix;
	}

	private static double physicalTextX(final String tag) {
		final double[] matrix = assertCcwMatrix(tag);
		final double[] xs = numbers(attribute(tag, "x"));
		final double[] ys = numbers(attribute(tag, "y"));
		assertTrue("text x coordinate is missing: " + tag, xs.length > 0);
		assertTrue("text y coordinate is missing: " + tag, ys.length > 0);
		return matrix[0] * xs[0] + matrix[2] * ys[0] + matrix[4];
	}

	private static List<String[]> textElements(final String xml) {
		final List<String[]> elements = new ArrayList<>();
		final Matcher matcher = Pattern.compile("(<text[^>]*>)([^<]*)</text>").matcher(xml);
		while (matcher.find()) {
			elements.add(new String[] { matcher.group(1), matcher.group(2) });
		}
		return elements;
	}

	private static String attribute(final String tag, final String name) {
		final Matcher matcher = Pattern.compile(Pattern.quote(name) + "=\"([^\"]*)\"").matcher(tag);
		return matcher.find() ? matcher.group(1) : "";
	}

	private static double[] numbers(final String values) {
		if (values == null || values.isBlank()) {
			return new double[0];
		}
		final String[] tokens = values.trim().split("\\s+");
		final double[] numbers = new double[tokens.length];
		for (int i = 0; i < tokens.length; ++i) {
			numbers[i] = Double.parseDouble(tokens[i]);
		}
		return numbers;
	}

	private static String lineContaining(final String value, final String needle) {
		return value.lines().filter(line -> line.contains(needle)).findFirst()
				.orElseThrow(() -> new AssertionError("display-list line was not found: " + needle));
	}

	private static int count(final String value, final String needle) {
		int count = 0;
		for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) {
			++count;
		}
		return count;
	}

	private static String readDumps(final Path dumpDir) throws Exception {
		final StringBuilder value = new StringBuilder();
		try (var files = Files.list(dumpDir)) {
			for (final Path file : files.sorted().toList()) {
				value.append(Files.readString(file, StandardCharsets.UTF_8));
			}
		}
		return value.toString();
	}

	private static void assertMatrix(final AffineTransform at, final double a, final double b,
			final double c, final double d, final double e, final double f) {
		final double[] matrix = new double[6];
		at.getMatrix(matrix);
		assertEquals(a, matrix[0], .0001);
		assertEquals(b, matrix[1], .0001);
		assertEquals(c, matrix[2], .0001);
		assertEquals(d, matrix[3], .0001);
		assertEquals(e, matrix[4], .0001);
		assertEquals(f, matrix[5], .0001);
	}

	private static void assertPoint(final AffineTransform at, final double x, final double y,
			final double expectedX, final double expectedY) {
		final Point2D point = at.transform(new Point2D.Double(x, y), null);
		assertEquals(expectedX, point.getX(), .0001);
		assertEquals(expectedY, point.getY(), .0001);
	}

	private static void assertBounds(final Rectangle2D bounds, final double x, final double y,
			final double width, final double height) {
		assertEquals(x, bounds.getX(), .0001);
		assertEquals(y, bounds.getY(), .0001);
		assertEquals(width, bounds.getWidth(), .0001);
		assertEquals(height, bounds.getHeight(), .0001);
	}

	private record LineSample(AbstractLineBox line, double x, double y) {
	}

	private record InlineSample(InlineBox box, double x, double y) {
	}

	private record Rect(double x, double y, double width, double height) {
	}

	private static final class CapturingResults implements Results {
		private final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(metadata.getURI().toString(), out);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// no-op
		}

		String text(final String uri) {
			final ByteArrayOutputStream out = this.data.get(uri);
			if (out == null) {
				throw new AssertionError("Paged SVG fragment was not found: " + uri + " in " + this.data.keySet());
			}
			return out.toString(StandardCharsets.UTF_8);
		}

		int pageCount() {
			return (int) this.data.keySet().stream().filter(uri -> uri.matches("pages/\\d+\\.svg")).count();
		}
	}
}
