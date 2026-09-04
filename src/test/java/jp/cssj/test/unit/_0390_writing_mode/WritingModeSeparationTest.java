package jp.cssj.test.unit._0390_writing_mode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import jp.cssj.test.unit.AbstractTestCase;
import jp.cssj.test.unit.TestPDFUserAgent;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.WritingModeVariantValue;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.ua.BoundSide;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 標準 writing-mode と direction の分離を、解析値と物理座標の両方で固定する。 */
public class WritingModeSeparationTest extends AbstractTestCase {
	private static final File FIXTURE = new File("files/unittest/0390-writing-mode/writing-mode-separation.html");
	private static final URI TEST_URI = URI.create("file:///writing-mode-separation.html");
	private static final int STD = 0, RTL = 1, LEGACY = 2, SVG = 3, EPUB = 4, CASES = 5;

	private final Rect[] families = new Rect[CASES];
	private final Rect[] margins = new Rect[CASES];
	private final Rect[] startBlocks = new Rect[CASES];
	private final LineSample[] starts = new LineSample[CASES];
	private final Rect[] leftBlocks = new Rect[CASES];
	private final LineSample[] lefts = new LineSample[CASES];
	private final Rect[] overflowBlocks = new Rect[CASES];
	private final LineSample[] overflows = new LineSample[CASES];
	private final Rect[] clampBlocks = new Rect[CASES];
	private final List<LineSample>[] clamps;
	private boolean bidiPass;
	private Rect bidiBlock;
	private LineSample bidiLine;

	@SuppressWarnings("unchecked")
	public WritingModeSeparationTest(final String name) {
		super(name);
		this.clamps = (List<LineSample>[]) new List<?>[CASES];
		for (int i = 0; i < CASES; ++i) {
			this.clamps[i] = new ArrayList<>();
		}
	}

	@Override
	protected void transcode() throws Exception {
		this.assertParsedValues();
		CTISessionHelper.transcodeFile(this.session, FIXTURE, "text/html", "UTF-8");
		this.assertMainCoordinates();
		this.bidiPass = true;
		try {
			this.transcodeBidiFixture();
		} finally {
			this.bidiPass = false;
		}
		this.assertParagraphBidiCoordinate();
		this.assertBoundSides();
	}

	private void assertParsedValues() {
		assertEquals(2, this.parse("writing-mode", CssToken.Keyword.INHERIT).size());
		assertEquals(3, this.parse("-cssj-writing-mode", CssToken.Keyword.INHERIT).size());
		this.assertStandard("horizontal-tb", BlockFlowValue.TB_VALUE, WritingModeVariantValue.NORMAL_VALUE);
		this.assertStandard("vertical-rl", BlockFlowValue.RL_VALUE, WritingModeVariantValue.NORMAL_VALUE);
		this.assertStandard("vertical-lr", BlockFlowValue.LR_VALUE, WritingModeVariantValue.NORMAL_VALUE);
		this.assertStandard("sideways-rl", BlockFlowValue.RL_VALUE, WritingModeVariantValue.SIDEWAYS_RL_VALUE);
		this.assertStandard("sideways-lr", BlockFlowValue.LR_VALUE, WritingModeVariantValue.SIDEWAYS_LR_VALUE);
		for (final String alias : new String[] { "lr", "lr-tb", "rl", "rl-tb" }) {
			this.assertStandard(alias, BlockFlowValue.TB_VALUE, WritingModeVariantValue.NORMAL_VALUE);
		}
		for (final String alias : new String[] { "tb", "tb-rl" }) {
			this.assertStandard(alias, BlockFlowValue.RL_VALUE, WritingModeVariantValue.NORMAL_VALUE);
		}

		final Map<String, Value> epub = this.parse("-epub-writing-mode", "vertical-lr");
		assertEquals(2, epub.size());
		assertSame(BlockFlowValue.LR_VALUE, epub.get(BlockFlow.INFO.getName()));
		assertSame(WritingModeVariantValue.NORMAL_VALUE, epub.get(WritingModeVariant.INFO.getName()));
		assertFalse(epub.containsKey(Direction.INFO.getName()));

		final Map<String, Value> legacy = this.parse("-cssj-writing-mode", "tb-lr");
		assertEquals(3, legacy.size());
		assertSame(DirectionValue.RTL_VALUE, legacy.get(Direction.INFO.getName()));
		assertSame(BlockFlowValue.LR_VALUE, legacy.get(BlockFlow.INFO.getName()));
		assertSame(WritingModeVariantValue.NORMAL_VALUE, legacy.get(WritingModeVariant.INFO.getName()));
	}

	private void assertStandard(final String value, final BlockFlowValue flow,
			final WritingModeVariantValue variant) {
		final Map<String, Value> parsed = this.parse("writing-mode", value);
		assertEquals(value, 2, parsed.size());
		assertSame(value, flow, parsed.get(BlockFlow.INFO.getName()));
		assertSame(value, variant, parsed.get(WritingModeVariant.INFO.getName()));
		assertFalse(value, parsed.containsKey(Direction.INFO.getName()));
	}

	private Map<String, Value> parse(final String propertyName, final String value) {
		return this.parse(propertyName, new CssToken.Ident(value));
	}

	private Map<String, Value> parse(final String propertyName, final CssToken token) {
		final Property property = ElementPropertySet.getInstance().parseDeclaration(propertyName,
				List.of(token), this.ua, TEST_URI, false);
		assertNotNull(propertyName + ": " + token, property);
		assertTrue(property instanceof CompositeProperty);
		final Map<String, Value> values = new LinkedHashMap<>();
		for (final CompositeProperty.Entry entry : ((CompositeProperty) property).getEntries()) {
			values.put(entry.getPrimitivePropertyInfo().getName(), entry.getValue());
		}
		return values;
	}

	private void assertMainCoordinates() throws Exception {
		for (int i = 0; i < CASES; ++i) {
			assertNotNull("family " + i, this.families[i]);
			assertNotNull("margin " + i, this.margins[i]);
			assertNotNull("start block " + i, this.startBlocks[i]);
			assertNotNull("start line " + i, this.starts[i]);
			assertNotNull("left block " + i, this.leftBlocks[i]);
			assertNotNull("left line " + i, this.lefts[i]);
			assertNotNull("overflow block " + i, this.overflowBlocks[i]);
			assertNotNull("overflow line " + i, this.overflows[i]);
			assertNotNull("clamp block " + i, this.clampBlocks[i]);
		}

		this.assertTopMargin(STD);
		this.assertBottomMargin(RTL);
		this.assertBottomMargin(LEGACY);
		this.assertLeftMargin(SVG);
		this.assertTopMargin(EPUB);

		// 段落 bidi(既定 ON、2026-09-04)では rtl の家族(明示 rtl と legacy tb-lr)の start は inline-end 側=下端に
		// 寄る(inline-size 50pt − "X" の幅 10pt = 40pt)。LTR の家族は 0
		final double[] startOffsets = { 0, 40, 40, 0, 0 };
		for (int i = 0; i < CASES; ++i) {
			assertEquals("text-align:start " + i, startOffsets[i], inlineOffset(this.startBlocks[i], this.starts[i]),
					.01);
		}
		// `text-align: left` は line-left 辺=縦書きでは上端(css-writing-modes-3 §7.3、direction に依らない)。
		// 段落 bidi 既定 ON(2026-09-04)で rtl でも上端に揃う(旧行単位 bidi は rtl で下端へ寄せていた)
		assertEquals(0, inlineOffset(this.leftBlocks[STD], this.lefts[STD]), .01);
		assertEquals("text-align:left rtl", 0, inlineOffset(this.leftBlocks[RTL], this.lefts[RTL]), .01);
		assertEquals("text-align:left legacy", 0, inlineOffset(this.leftBlocks[LEGACY], this.lefts[LEGACY]), .01);
		assertEquals(0, inlineOffset(this.leftBlocks[SVG], this.lefts[SVG]), .01);
		assertEquals(0, inlineOffset(this.leftBlocks[EPUB], this.lefts[EPUB]), .01);

		final byte[] directions = { AbstractTextParams.DIRECTION_LTR, AbstractTextParams.DIRECTION_RTL,
				AbstractTextParams.DIRECTION_RTL, AbstractTextParams.DIRECTION_LTR,
				AbstractTextParams.DIRECTION_LTR };
		final WritingMode[] flows = { WritingMode.LR, WritingMode.LR, WritingMode.LR, WritingMode.TB,
				WritingMode.LR };
		final boolean[] ellipses = { true, false, false, true, true };
		for (int i = 0; i < CASES; ++i) {
			assertEquals("direction " + i, directions[i], this.starts[i].line.getLineParams().direction);
			assertSame("flow " + i, flows[i], this.starts[i].line.getLineParams().flow);
			this.assertOverflowSide(i, ellipses[i]);
			this.assertClampLastLine(i, ellipses[i]);
		}
	}

	/**
	 * 訪問者が報告する矩形は margin box(inline 軸に 10pt の margin が乗って 20pt)。inline-start が上なら
	 * 箱は家族の上端に、下なら下端に付く(`inset-inline-start: 0`)。
	 */
	private void assertTopMargin(final int index) {
		assertEquals(this.families[index].x, this.margins[index].x, .01);
		assertEquals("inline-start must be the top edge", this.families[index].y, this.margins[index].y, .01);
		assertEquals("margin-inline-start must be applied along the inline (vertical) axis", 20,
				this.margins[index].height, .01);
	}

	private void assertBottomMargin(final int index) {
		assertEquals(this.families[index].x, this.margins[index].x, .01);
		assertEquals("inline-start must be the bottom edge", this.families[index].y + this.families[index].height,
				this.margins[index].y + this.margins[index].height, .01);
		assertEquals("margin-inline-start must be applied along the inline (vertical) axis", 20,
				this.margins[index].height, .01);
	}

	private void assertLeftMargin(final int index) {
		assertEquals("inline-start must be the left edge", this.families[index].x, this.margins[index].x, .01);
		assertEquals(this.families[index].y, this.margins[index].y, .01);
		assertEquals("margin-inline-start must be applied along the inline (horizontal) axis", 20,
				this.margins[index].width, .01);
	}

	private void assertOverflowSide(final int index, final boolean expected) throws Exception {
		final LineSample sample = this.overflows[index];
		assertEquals("text-overflow ellipsis " + index, expected, sample.line.getEllipsis() != null);
		if (!expected) {
			return;
		}
		final double clip = ellipsisClipExtent(sample.line);
		final double advance = sample.line.getEllipsis().getAdvance();
		if (sample.line.getLineParams().flow.isVertical()) {
			assertEquals("vertical ellipsis must occupy the bottom/end side " + index,
					this.overflowBlocks[index].y + this.overflowBlocks[index].height,
					sample.y + clip + advance, .01);
		} else {
			assertEquals("horizontal ellipsis must occupy the right/end side " + index,
					this.overflowBlocks[index].x + this.overflowBlocks[index].width,
					sample.x + clip + advance, .01);
		}
	}

	private void assertClampLastLine(final int index, final boolean expectedEllipsis) {
		final List<LineSample> lines = this.clamps[index];
		assertEquals("line-clamp line count " + index, 2, lines.size());
		final LineSample first = lines.get(0);
		final LineSample last = lines.get(1);
		if (last.line.getLineParams().flow.isVertical()) {
			assertTrue("vertical line-clamp last line must follow on the block axis " + index,
					last.x > first.x + 5);
		} else {
			assertTrue("horizontal line-clamp last line must follow on the block axis " + index,
					last.y > first.y + 5);
		}
		assertNull("first clamped line must not have ellipsis " + index, first.line.getEllipsis());
		assertEquals("last clamped line ellipsis " + index, expectedEllipsis, last.line.getEllipsis() != null);
	}

	private void transcodeBidiFixture() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setUserAgent(new TestPDFUserAgent(this));
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("layout.bidi.paragraph", "true");
			CTISessionHelper.transcodeFile(session, FIXTURE, "text/html", "UTF-8");
		} finally {
			session.close();
		}
	}

	private void assertParagraphBidiCoordinate() {
		assertNotNull(this.bidiBlock);
		assertNotNull(this.bidiLine);
		assertTrue(this.bidiLine.line.isParagraphBidiEnabled());
		assertEquals(AbstractTextParams.DIRECTION_LTR, this.bidiLine.line.getLineParams().direction);
		assertSame(WritingMode.LR, this.bidiLine.line.getLineParams().flow);
		assertEquals("paragraph bidi: default-direction vertical-lr start must be at the top",
				0, inlineOffset(this.bidiBlock, this.bidiLine), .01);
	}

	private void assertBoundSides() throws Exception {
		assertSame(BoundSide.LEFT, this.boundSide("writing-mode: vertical-lr"));
		assertSame(BoundSide.LEFT, this.boundSide("writing-mode: vertical-lr; direction: rtl"));
		assertSame(BoundSide.LEFT, this.boundSide("-cssj-writing-mode: tb-lr"));
		assertSame(BoundSide.LEFT, this.boundSide("writing-mode: rl-tb"));
		assertSame(BoundSide.LEFT, this.boundSide("-epub-writing-mode: vertical-lr"));
		assertSame(BoundSide.RIGHT, this.boundSide("writing-mode: vertical-rl"));
		assertSame(BoundSide.RIGHT, this.boundSide("writing-mode: horizontal-tb; direction: rtl"));
	}

	private BoundSide boundSide(final String rootStyle) throws Exception {
		final String source = Files.readString(FIXTURE.toPath(), StandardCharsets.UTF_8)
				.replaceFirst("<html>", "<html style=\"" + rootStyle + "\">");
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final PDFUserAgent ua = new PDFUserAgent() {
			// protected コンストラクタなので匿名サブクラスで生成する
		};
		ua.setBoundSide(BoundSide.LEFT);
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setUserAgent(ua);
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), TEST_URI, "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return ua.getBoundSide();
	}

	private static double inlineOffset(final Rect block, final LineSample line) {
		return line.line.getLineParams().flow.isVertical() ? line.y - block.y : line.x - block.x;
	}

	private static double ellipsisClipExtent(final AbstractLineBox line) throws Exception {
		final Field field = AbstractLineBox.class.getDeclaredField("ellipsisClipExtent");
		field.setAccessible(true);
		return field.getDouble(line);
	}

	private boolean family(final int index, final IBox box, final double x, final double y) {
		if (this.bidiPass) {
			return true;
		}
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		this.families[index] = new Rect(x, y, box.getWidth(), box.getHeight());
		return true;
	}

	private boolean margin(final int index, final IBox box, final double x, final double y) {
		if (this.bidiPass) {
			return true;
		}
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		this.margins[index] = new Rect(x, y, box.getWidth(), box.getHeight());
		return true;
	}

	private boolean line(final int index, final Rect[] blocks, final LineSample[] lines, final IBox box,
			final double x, final double y) {
		if (this.bidiPass) {
			return true;
		}
		if (box.getType() == BoxType.BLOCK) {
			blocks[index] = new Rect(x, y, box.getWidth(), box.getHeight());
			return false;
		}
		if (!(box instanceof AbstractLineBox value)) {
			return false;
		}
		lines[index] = new LineSample(value, x, y);
		return true;
	}

	private boolean clamp(final int index, final IBox box, final double x, final double y) {
		if (this.bidiPass) {
			return true;
		}
		if (box.getType() == BoxType.BLOCK) {
			this.clampBlocks[index] = new Rect(x, y, box.getWidth(), box.getHeight());
			return false;
		}
		if (!(box instanceof AbstractLineBox value)) {
			return false;
		}
		this.clamps[index].add(new LineSample(value, x, y));
		return true;
	}

	public boolean check_std_family(final IBox box, final int page, final double x, final double y) {
		return this.family(STD, box, x, y);
	}

	public boolean check_rtl_family(final IBox box, final int page, final double x, final double y) {
		return this.family(RTL, box, x, y);
	}

	public boolean check_legacy_family(final IBox box, final int page, final double x, final double y) {
		return this.family(LEGACY, box, x, y);
	}

	public boolean check_svg_family(final IBox box, final int page, final double x, final double y) {
		return this.family(SVG, box, x, y);
	}

	public boolean check_epub_family(final IBox box, final int page, final double x, final double y) {
		return this.family(EPUB, box, x, y);
	}

	public boolean check_std_margin(final IBox box, final int page, final double x, final double y) {
		return this.margin(STD, box, x, y);
	}

	public boolean check_rtl_margin(final IBox box, final int page, final double x, final double y) {
		return this.margin(RTL, box, x, y);
	}

	public boolean check_legacy_margin(final IBox box, final int page, final double x, final double y) {
		return this.margin(LEGACY, box, x, y);
	}

	public boolean check_svg_margin(final IBox box, final int page, final double x, final double y) {
		return this.margin(SVG, box, x, y);
	}

	public boolean check_epub_margin(final IBox box, final int page, final double x, final double y) {
		return this.margin(EPUB, box, x, y);
	}

	public boolean check_std_start(final IBox box, final int page, final double x, final double y) {
		return this.line(STD, this.startBlocks, this.starts, box, x, y);
	}

	public boolean check_rtl_start(final IBox box, final int page, final double x, final double y) {
		return this.line(RTL, this.startBlocks, this.starts, box, x, y);
	}

	public boolean check_legacy_start(final IBox box, final int page, final double x, final double y) {
		return this.line(LEGACY, this.startBlocks, this.starts, box, x, y);
	}

	public boolean check_svg_start(final IBox box, final int page, final double x, final double y) {
		return this.line(SVG, this.startBlocks, this.starts, box, x, y);
	}

	public boolean check_epub_start(final IBox box, final int page, final double x, final double y) {
		return this.line(EPUB, this.startBlocks, this.starts, box, x, y);
	}

	public boolean check_std_left(final IBox box, final int page, final double x, final double y) {
		return this.line(STD, this.leftBlocks, this.lefts, box, x, y);
	}

	public boolean check_rtl_left(final IBox box, final int page, final double x, final double y) {
		return this.line(RTL, this.leftBlocks, this.lefts, box, x, y);
	}

	public boolean check_legacy_left(final IBox box, final int page, final double x, final double y) {
		return this.line(LEGACY, this.leftBlocks, this.lefts, box, x, y);
	}

	public boolean check_svg_left(final IBox box, final int page, final double x, final double y) {
		return this.line(SVG, this.leftBlocks, this.lefts, box, x, y);
	}

	public boolean check_epub_left(final IBox box, final int page, final double x, final double y) {
		return this.line(EPUB, this.leftBlocks, this.lefts, box, x, y);
	}

	public boolean check_std_overflow(final IBox box, final int page, final double x, final double y) {
		return this.line(STD, this.overflowBlocks, this.overflows, box, x, y);
	}

	public boolean check_rtl_overflow(final IBox box, final int page, final double x, final double y) {
		return this.line(RTL, this.overflowBlocks, this.overflows, box, x, y);
	}

	public boolean check_legacy_overflow(final IBox box, final int page, final double x, final double y) {
		return this.line(LEGACY, this.overflowBlocks, this.overflows, box, x, y);
	}

	public boolean check_svg_overflow(final IBox box, final int page, final double x, final double y) {
		return this.line(SVG, this.overflowBlocks, this.overflows, box, x, y);
	}

	public boolean check_epub_overflow(final IBox box, final int page, final double x, final double y) {
		return this.line(EPUB, this.overflowBlocks, this.overflows, box, x, y);
	}

	public boolean check_std_clamp(final IBox box, final int page, final double x, final double y) {
		return this.clamp(STD, box, x, y);
	}

	public boolean check_rtl_clamp(final IBox box, final int page, final double x, final double y) {
		return this.clamp(RTL, box, x, y);
	}

	public boolean check_legacy_clamp(final IBox box, final int page, final double x, final double y) {
		return this.clamp(LEGACY, box, x, y);
	}

	public boolean check_svg_clamp(final IBox box, final int page, final double x, final double y) {
		return this.clamp(SVG, box, x, y);
	}

	public boolean check_epub_clamp(final IBox box, final int page, final double x, final double y) {
		return this.clamp(EPUB, box, x, y);
	}

	public boolean check_bidi_start(final IBox box, final int page, final double x, final double y) {
		if (!this.bidiPass) {
			return box instanceof AbstractLineBox;
		}
		if (box.getType() == BoxType.BLOCK) {
			this.bidiBlock = new Rect(x, y, box.getWidth(), box.getHeight());
			return false;
		}
		if (!(box instanceof AbstractLineBox value)) {
			return false;
		}
		this.bidiLine = new LineSample(value, x, y);
		return true;
	}

	private record Rect(double x, double y, double width, double height) {
	}

	private record LineSample(AbstractLineBox line, double x, double y) {
	}
}
