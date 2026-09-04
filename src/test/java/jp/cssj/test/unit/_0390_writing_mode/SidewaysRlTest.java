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
import org.apache.pdfbox.text.TextPosition;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import jp.cssj.cti2.results.SingleResult;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode.PhysicalSide;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.util.SidewaysGeometry;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** sideways-rl を水平 run の +90 度回転として描く Stage 1 の受入検査。 */
public class SidewaysRlTest extends AbstractTestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final File FIXTURE = new File("files/unittest/0390-writing-mode/sideways-rl.html");
	private static final String CW_MATRIX = "[0.00 1.00 -1.00 0.00 ";

	private LineSample mixed, upright, sideways, surface;
	private InlineSample framed;
	private Rect image;

	public SidewaysRlTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		this.assertGeometryContract();
		this.session.property("layout.bidi.paragraph", "true");
		final Path dumpDir = Files.createTempDirectory("foliojet-sideways-rl-");
		try (AutoCloseable dump = DisplayListDumper.scopedDir(dumpDir.toString());
				AutoCloseable geometry = DisplayListDumper.scopedDetailedGeometry(true)) {
			CTISessionHelper.transcodeFile(this.session, FIXTURE, "text/html", "UTF-8");
		}

		this.assertOrientationIndependentMetrics();
		this.assertInlineAndReplacedGeometry();
		this.assertDisplayList(Files.readString(dumpDir.resolve("page-0001.txt"), StandardCharsets.UTF_8));
		this.assertPdf(this.renderPdf());
		this.assertPagedSvg(this.renderPagedSvg());
	}

	private void assertGeometryContract() {
		final AffineTransform cw = SidewaysGeometry.runTransform(WritingModeVariant.SIDEWAYS_CW,
				10, 20, 3, 4, 30);
		assertMatrix(cw, 0, 1, -1, 0, 14, 20);
		assertPoint(cw, 0, 0, 14, 20);
		assertPoint(cw, 30, 0, 14, 50);
		assertPoint(cw, 0, -3, 17, 20);
		assertPoint(cw, 0, 4, 10, 20);
		assertBounds(SidewaysGeometry.bounds(WritingModeVariant.SIDEWAYS_CW, 10, 20, 3, 4, 30),
				10, 20, 7, 30);

		final AffineTransform ccw = SidewaysGeometry.runTransform(WritingModeVariant.SIDEWAYS_CCW,
				10, 20, 3, 4, 30);
		assertMatrix(ccw, 0, -1, 1, 0, 13, 50);
		assertPoint(ccw, 0, 0, 13, 50);
		assertPoint(ccw, 30, 0, 13, 20);
		assertPoint(ccw, 0, -3, 10, 50);
		assertPoint(ccw, 0, 4, 17, 50);
		assertBounds(SidewaysGeometry.bounds(WritingModeVariant.SIDEWAYS_CCW, 10, 20, 3, 4, 30),
				10, 20, 7, 30);
	}

	private void assertOrientationIndependentMetrics() {
		assertNotNull(this.mixed);
		assertNotNull(this.upright);
		assertNotNull(this.sideways);
		assertEquals(this.mixed.line.getAscent(), this.upright.line.getAscent(), .01);
		assertEquals(this.mixed.line.getAscent(), this.sideways.line.getAscent(), .01);
		assertEquals(this.mixed.line.getDescent(), this.upright.line.getDescent(), .01);
		assertEquals(this.mixed.line.getDescent(), this.sideways.line.getDescent(), .01);
		assertEquals(this.mixed.line.getLineSize(), this.upright.line.getLineSize(), .01);
		assertEquals(this.mixed.line.getLineSize(), this.sideways.line.getLineSize(), .01);
	}

	private void assertInlineAndReplacedGeometry() {
		assertNotNull(this.surface);
		assertNotNull(this.framed);
		assertNotNull(this.image);
		final net.zamasoft.foliojet.layout.box.params.AbstractTextParams framedParams = this.framed.box.getTextParams();
		assertSame(PhysicalSide.RIGHT, net.zamasoft.foliojet.layout.box.params.TypesettingMode
				.overSide(framedParams.flow, framedParams.writingModeVariant));
		assertEquals(2, this.framed.box.getFrame().getFrameLeft(), .01);
		assertEquals(4, this.framed.box.getFrame().getFrameRight(), .01);
		assertEquals(this.framed.box.getAscent() + this.framed.box.getDescent(),
				this.framed.box.getWidth(), .01);
		final AffineTransform at = SidewaysGeometry.runTransform(WritingModeVariant.SIDEWAYS_CW,
				this.framed.x, this.framed.y, this.framed.box.getAscent(), this.framed.box.getDescent(),
				this.framed.box.getLineSize());
		assertPoint(at, 0, -this.framed.box.getAscent(), this.framed.x + this.framed.box.getWidth(),
				this.framed.y);
		assertEquals("replaced width must not be rotated", 12, this.image.width, .01);
		assertEquals("replaced height must not be rotated", 20, this.image.height, .01);
		assertTrue("the replaced element must advance on the physical vertical inline axis",
				this.image.y >= this.surface.y);
	}

	private void assertDisplayList(final String dump) {
		// LTR の sideways 行は並べ替え無しなので dump は Text["…"] 形式(logical=/visual= は並べ替え行だけ)
		final List<String> orientationLines = dump.lines()
				.filter(line -> line.contains("\"A\u6F22B\"")).toList();
		assertEquals(3, orientationLines.size());
		for (final String line : orientationLines) {
			assertTrue(line, line.contains("run-tf=" + CW_MATRIX));
			assertTrue(line, line.contains("run-bounds=["));
		}
		assertTrue("decorations must share the run rotation",
				dump.contains("TextDecorationDrawable decoration-tf=" + CW_MATRIX));
		assertTrue("leader glyphs must share the run rotation",
				dump.contains("Leader[") && dump.contains("leader-tf=" + CW_MATRIX));
		assertTrue("ruby must be drawn in the rotated horizontal model",
				dump.contains("RubyUnit[") && dump.contains("ruby-tf=" + CW_MATRIX));
		assertTrue("warichu must be drawn in the rotated horizontal model",
				dump.contains("Warichu[") && dump.contains("warichu-tf=" + CW_MATRIX));
		assertFalse("horizontal child must reset the sideways variant", lineContaining(dump, "RESET").contains("run-tf="));
		assertFalse("vertical-rl/text-orientation:sideways must keep the old drawing path",
				lineContaining(dump, "VERTICALREG").contains("run-tf="));
	}

	private void assertPdf(final byte[] bytes) throws Exception {
		try (PDDocument pdf = Loader.loadPDF(bytes)) {
			assertTrue("PDF must contain a +90 degree run CTM", countCwMatrices(pdf) >= 3);
			final List<TextPosition> positions = new ArrayList<>();
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void processTextPosition(final TextPosition text) {
					positions.add(text);
				}
			};
			stripper.setSuppressDuplicateOverlappingText(false);
			stripper.getText(pdf);
			final StringBuilder contentOrder = new StringBuilder();
			for (final TextPosition position : positions) {
				if (position.getUnicode() != null) {
					contentOrder.append(position.getUnicode());
				}
			}
			assertTrue("PDF TJ order must retain A-kanji-B logical order: " + contentOrder,
					count(contentOrder.toString(), "A\u6F22B") >= 3);
		}
	}

	private void assertPagedSvg(final String svg) {
		// data-copper-text は埋め込み subset 経路だけに付く。core フォント+フォールバックでは run が
		// 文字ごとに分かれるので、<text> の中身で特定する(3 つの div の "A" が最低 3 要素)
		int orientationTags = 0;
		for (final String[] element : textElements(svg)) {
			if ("A".equals(element[1]) || "A\u6F22B".equals(element[1])) {
				++orientationTags;
				assertCwMatrix(element[0]);
			}
		}
		assertTrue("mixed/upright/sideways must all emit a rotated horizontal run: " + orientationTags,
				orientationTags >= 3);

		final String probe = textElements(svg).stream().filter(element -> "XYZ".equals(element[1]))
				.map(element -> element[0]).findFirst()
				.orElseThrow(() -> new AssertionError("Paged SVG XYZ probe was not found"));
		final double[] matrix = assertCwMatrix(probe);
		final double[] xs = numbers(attribute(probe, "x"));
		final double[] ys = numbers(attribute(probe, "y"));
		assertTrue("XYZ must be emitted as one horizontal run", xs.length >= 3);
		double previous = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < xs.length; ++i) {
			final double localY = ys.length == 1 ? ys[0] : ys[i];
			final double physicalY = matrix[1] * xs[i] + matrix[3] * localY + matrix[5];
			assertTrue("local +x must advance down the page", physicalY > previous);
			previous = physicalY;
		}

		final List<String> combine = textElements(svg).stream()
				.filter(element -> "12".equals(element[1]) || "1".equals(element[1]) || "2".equals(element[1]))
				.map(element -> element[0]).toList();
		assertFalse("Paged SVG text-combine probe was not found", combine.isEmpty());
		for (final String tag : combine) {
			assertFalse("text-combine-upright must reset the sideways run transform: " + tag,
					attribute(tag, "transform").startsWith("matrix(0 1 -1 0 "));
		}
	}

	public boolean check_mixed(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		this.assertSidewaysLine(line);
		this.mixed = new LineSample(line, x, y);
		return true;
	}

	public boolean check_upright(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		this.assertSidewaysLine(line);
		this.upright = new LineSample(line, x, y);
		return true;
	}

	public boolean check_sideways(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		this.assertSidewaysLine(line);
		this.sideways = new LineSample(line, x, y);
		return true;
	}

	public boolean check_surface(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		this.assertSidewaysLine(line);
		this.surface = new LineSample(line, x, y);
		return true;
	}

	public boolean check_framed(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof InlineBox inline)) {
			return false;
		}
		assertSame(WritingModeVariant.SIDEWAYS_CW, inline.getTextParams().writingModeVariant);
		this.framed = new InlineSample(inline, x, y);
		return true;
	}

	public boolean check_upright_image(final IBox box, final int page, final double x, final double y) {
		if (box.getType() != BoxType.REPLACED) {
			return false;
		}
		this.image = new Rect(x, y, box.getWidth(), box.getHeight());
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

	public boolean check_emphasis(final IBox box, final int page, final double x, final double y) {
		return this.checkSidewaysSurface(box);
	}

	public boolean check_leader(final IBox box, final int page, final double x, final double y) {
		return this.checkSidewaysSurface(box);
	}

	public boolean check_ellipsis(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		this.assertSidewaysLine(line);
		assertNotNull("sideways overflow line must create an ellipsis", line.getEllipsis());
		return true;
	}

	public boolean check_clip_text(final IBox box, final int page, final double x, final double y) {
		return this.checkSidewaysSurface(box);
	}

	public boolean check_ruby(final IBox box, final int page, final double x, final double y) {
		return this.checkSidewaysSurface(box);
	}

	public boolean check_warichu(final IBox box, final int page, final double x, final double y) {
		return this.checkSidewaysSurface(box);
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

	public boolean check_vertical_regression(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertSame(WritingMode.RL, line.getLineParams().flow);
		assertSame(WritingModeVariant.NORMAL, line.getLineParams().writingModeVariant);
		for (final Text run : textRuns(line)) {
			assertSame(FontStyle.Direction.TB, run.getFontStyle().getDirection());
		}
		return true;
	}

	public boolean check_svg_probe(final IBox box, final int page, final double x, final double y) {
		return this.checkSidewaysSurface(box);
	}

	private boolean checkSidewaysSurface(final IBox box) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertSame(WritingMode.RL, line.getLineParams().flow);
		assertSame(WritingModeVariant.SIDEWAYS_CW, line.getLineParams().writingModeVariant);
		assertTrue(line.getLineParams().isHorizontalTypesetting());
		return true;
	}

	private void assertSidewaysLine(final AbstractLineBox line) {
		assertSame(WritingMode.RL, line.getLineParams().flow);
		assertSame(WritingModeVariant.SIDEWAYS_CW, line.getLineParams().writingModeVariant);
		assertTrue(line.getLineParams().isHorizontalTypesetting());
		final List<Text> runs = textRuns(line);
		assertFalse(runs.isEmpty());
		for (final Text run : runs) {
			assertTrue(run.getFontStyle().getDirection() != FontStyle.Direction.TB);
			assertSame(FontStyle.TextOrientation.MIXED, run.getFontStyle().getTextOrientation());
		}
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

	private String renderPagedSvg() throws Exception {
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
		return results.text("pages/0001.svg");
	}

	private DirectSession newSession() throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
		session.property("input.include", "**");
		session.property("layout.bidi.paragraph", "true");
		return session;
	}

	private static int countCwMatrices(final PDDocument pdf) throws Exception {
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
				if ("cm".equals(operator.getName()) && operands.size() >= 6) {
					final int off = operands.size() - 6;
					// PDF の座標は y 上向きなので、利用者空間の +90°(0 1 -1 0)は content stream では 0 -1 1 0 になる
					if (near(number(operands.get(off)), 0) && near(number(operands.get(off + 1)), -1)
							&& near(number(operands.get(off + 2)), 1) && near(number(operands.get(off + 3)), 0)) {
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

	private static double[] assertCwMatrix(final String tag) {
		final double[] matrix = numbers(attribute(tag, "transform").replace("matrix(", "").replace(")", ""));
		assertEquals(tag, 6, matrix.length);
		assertEquals(0, matrix[0], .0001);
		assertEquals(1, matrix[1], .0001);
		assertEquals(-1, matrix[2], .0001);
		assertEquals(0, matrix[3], .0001);
		return matrix;
	}

	/** {@code <text …>中身</text>} の (開始タグ, 中身) の列。 */
	private static List<String[]> textElements(final String xml) {
		final List<String[]> elements = new ArrayList<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(<text[^>]*>)([^<]*)</text>").matcher(xml);
		while (m.find()) {
			elements.add(new String[] { m.group(1), m.group(2) });
		}
		return elements;
	}

	private static List<String> startTags(final String xml, final String name) {
		final List<String> tags = new ArrayList<>();
		final String prefix = "<" + name;
		for (int at = 0; (at = xml.indexOf(prefix, at)) >= 0;) {
			final int end = xml.indexOf('>', at);
			if (end < 0) {
				break;
			}
			tags.add(xml.substring(at, end + 1));
			at = end + 1;
		}
		return tags;
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
			return this.data.get(uri).toString(StandardCharsets.UTF_8);
		}
	}
}
