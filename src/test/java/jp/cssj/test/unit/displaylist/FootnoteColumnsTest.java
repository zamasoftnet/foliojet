package jp.cssj.test.unit.displaylist;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import junit.framework.TestCase;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.FootnotePageProbe;
import net.zamasoft.foliojet.layout.FootnotePageProbeReport;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.content.ColumnsContainer;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** F-4/F-8: 段組の包含寸法・ページ共通の帯・配置座標・長文の保持窓。 */
public final class FootnoteColumnsTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String AREA = "@footnote { float: bottom; writing-mode: horizontal-tb }";
	private static final double EPSILON = 0.01;

	public void testVerticalColumnBlockEndFootnotes() throws Exception {
		assertColumnBlockEnd("footnote-columns-block-end.html");
	}

	public void testHorizontalColumnBlockEndFootnotes() throws Exception {
		assertColumnBlockEnd("footnote-columns-block-end-horizontal.html");
	}

	private static void assertColumnBlockEnd(final String fixtureName) throws Exception {
		final String html = Files.readString(Path.of("files/unittest/0125-footnote", fixtureName), StandardCharsets.UTF_8);
		final Capture capture = transcode(html);
		assertEquals("注の欠落・重複なし", 3, capture.notes.size());
		assertEquals("callの欠落・重複なし", 3, capture.calls.size());
		assertFalse("図版を含む", capture.floats.isEmpty());
		assertEquals("全注を改段済みの容器へ添付", 3,
				capture.columnNotes.stream().mapToInt(value -> value.attachedIds().size()).sum());
		for (final Column column : capture.columns) {
			final boolean vertical = column.bounds().flow().isVertical();
			final Page page = capture.page(column.page());
			assertEquals("段組ownerは頁容量を保つ", vertical ? page.innerWidth() : page.innerHeight(),
					vertical ? column.bounds().width() : column.bounds().height(), EPSILON);
		}
		for (final Note note : capture.notes) {
			final Label call = capture.calls.stream().filter(value -> value.id() == note.id()).findFirst().orElseThrow();
			final Column column = capture.columns.stream().filter(value -> value.page() == call.page()
					&& columnIndex(value, call.x(), call.y()) >= 0).findFirst().orElseThrow();
			final Placement box = note.placement();
			final boolean vertical = column.bounds().flow().isVertical();
			assertEquals("短い注は呼び出しと同頁", call.page(), box.page());
			assertEquals("呼び出しと同じ段", columnIndex(column, call.x(), call.y()), columnIndex(column, box.x(), box.y()));
			final int index = columnIndex(column, call.x(), call.y());
			final double origin = (vertical ? column.bounds().y() : column.bounds().x()) + index * (column.lineSize() + column.gap());
			assertEquals("注の行軸先頭は段の先頭", origin, vertical ? box.y() : box.x(), EPSILON);
			assertEquals("狭いcaptionの注も段の行長", column.lineSize(), vertical ? box.height() : box.width(), EPSILON);
			final RootBuilder.ColumnFootnotePlacement placed = capture.columnNotes.stream()
					.filter(value -> value.attachedIds().contains(note.id())).findFirst().orElseThrow();
			assertTrue("旧段にcallが残った", placed.retainedIds().contains(note.id()));
			assertEquals("内容限界を占有量+gapだけ縮める", placed.attachedExtent() + 6, placed.reservation(), EPSILON);
			double offset = 0;
			for (final long id : placed.attachedIds()) {
				if (id == note.id()) break;
				final Placement preceding = capture.notes.stream().filter(value -> value.id() == id).findFirst().orElseThrow().placement();
				offset += vertical ? preceding.width() : preceding.height();
			}
			final double start = placed.capacity() - placed.attachedExtent() + offset;
			assertEquals("C-EからFIFO順に段のblock-endへ", vertical
					? column.bounds().x() + column.bounds().width() - start - box.width() : column.bounds().y() + start,
					vertical ? box.x() : box.y(), EPSILON);
			for (final Placement line : bodyLines(capture)) {
				assertFalse("本文と注を重ねない", overlaps(box, line));
				if (line.page() == box.page() && columnIndex(column, line.x(), line.y()) == index) {
					final double end = vertical ? column.bounds().x() + column.bounds().width() - line.x()
							: line.y() + line.height() - column.bounds().y();
					assertTrue("本文は段の内容限界内", end <= placed.capacity() - placed.reservation() + EPSILON);
				}
			}
			for (final Placement floating : capture.floats) assertFalse("図版と注を重ねない", overlaps(box, floating));
			assertTrue("markerもcall頁の番号で解決", box.text().startsWith(call.text().trim() + ". "));
		}
		for (final Placement line : bodyLines(capture)) {
			for (final Placement floating : capture.floats) assertFalse("本文と図版を重ねない", overlaps(line, floating));
		}
		final var nextNumbers = new java.util.HashMap<Integer, Integer>();
		for (final Label call : capture.calls.stream().sorted(java.util.Comparator.comparingLong(Label::id)).toList()) {
			assertEquals("頁内で文書順に通番", Integer.toString(nextNumbers.merge(call.page(), 1, Integer::sum)), call.text().trim());
		}
		final StringBuilder expected = new StringBuilder();
		final var paragraphs = java.util.regex.Pattern.compile("<p(?:\\s[^>]*)?>(.*?)</p>", java.util.regex.Pattern.DOTALL).matcher(html);
		while (paragraphs.find()) expected.append(paragraphs.group(1).replaceAll("<span class=\"note\">.*?</span>", "").replaceAll("<[^>]+>", ""));
		final StringBuilder actual = new StringBuilder();
		bodyLines(capture).stream().sorted(java.util.Comparator.comparingInt(Placement::page)
				.thenComparingDouble(line -> line.flow().isVertical() ? line.y() : line.x())
				.thenComparingDouble(line -> line.flow().isVertical() ? -line.x() : line.y()))
				.forEach(line -> actual.append(line.text()));
		assertEquals("全段の本文を文書順に連結して欠落・重複なし", expected.toString().replaceAll("\\s", ""),
				actual.toString().replaceAll("[\\s0-9]", ""));
		assertTrue("EOF救済で注を捨てない", capture.footnoteWarnings.isEmpty());
		assertTrue("EOFの全pendingを回収", capture.traces.stream().anyMatch(value -> value.event().equals("finish") && value.pendingCount() == 0));
		final String display = capture.displayLists.stream().map(value -> new String(value, StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining());
		assertEquals("注のある段だけに罫線", capture.columnNotes.stream().filter(value -> !value.attachedIds().isEmpty()).count(),
				(long) (display.split("FootnoteSeparator\\[", -1).length - 1));
	}

	/** 増分5: 段組が頁をまたぐ。旧頁の最後の段に添付するか、次頁で最初に開く段へ持ち越す。 */
	public void testColumnNotesCarryAcrossPageBreak() throws Exception {
		final String html = Files.readString(Path.of("files/unittest/0125-footnote", "footnote-columns-block-end-carry.html"), StandardCharsets.UTF_8);
		final Capture capture = transcode(html);
		assertEquals("注の欠落・重複なし", 3, capture.notes.size());
		assertEquals("callの欠落・重複なし", 3, capture.calls.size());
		assertTrue("段組が頁をまたぐ", capture.columns.stream().mapToInt(Column::page).max().orElse(0) > 1);
		for (final Note note : capture.notes) {
			final Label call = capture.calls.stream().filter(value -> value.id() == note.id()).findFirst().orElseThrow();
			final Placement box = note.placement();
			assertTrue("注は呼び出しの頁か次頁", box.page() == call.page() || box.page() == call.page() + 1);
			final Column column = capture.columns.stream().filter(value -> value.page() == box.page()
					&& columnIndex(value, box.x(), box.y()) >= 0).findFirst().orElseThrow();
			final boolean vertical = column.bounds().flow().isVertical();
			final int index = columnIndex(column, box.x(), box.y());
			final double origin = (vertical ? column.bounds().y() : column.bounds().x()) + index * (column.lineSize() + column.gap());
			assertEquals("注の行軸先頭は段の先頭", origin, vertical ? box.y() : box.x(), EPSILON);
			assertEquals("注の幅は段の行長", column.lineSize(), vertical ? box.height() : box.width(), EPSILON);
			if (box.page() == call.page()) {
				assertEquals("同頁なら呼び出しの段", columnIndex(column, call.x(), call.y()), index);
			} else {
				assertEquals("次頁へ持ち越した注は最初の段", 0, index);
				assertTrue("持ち越しは呼び出し確定後(番号は呼び出しの頁)", capture.traces.stream()
						.anyMatch(value -> value.event().equals("column-carry") && value.id() == note.id()));
			}
			assertTrue("番号は呼び出しの頁の通番", box.text().startsWith(call.text().trim() + ". "));
			for (final Placement line : bodyLines(capture)) assertFalse("本文と注を重ねない", overlaps(box, line));
		}
		for (final Column column : capture.columns) {
			final Page page = capture.page(column.page());
			final boolean vertical = column.bounds().flow().isVertical();
			assertEquals("段組ownerは頁容量を保つ", vertical ? page.innerWidth() : page.innerHeight(),
					vertical ? column.bounds().width() : column.bounds().height(), EPSILON);
		}
		assertTrue("EOF救済で注を捨てない: " + capture.footnoteWarnings, capture.footnoteWarnings.isEmpty());
		assertTrue("EOFの全pendingを回収", capture.traces.stream().anyMatch(value -> value.event().equals("finish") && value.pendingCount() == 0));
	}

	/** 増分6: 頁の途中で閉じるbalance段組。段の注はbalance前に回収して頁のblock-endへ。 */
	public void testBalancedColumnsHandNotesToPage() throws Exception {
		final String html = Files.readString(Path.of("files/unittest/0125-footnote", "footnote-columns-block-end-balance.html"), StandardCharsets.UTF_8);
		final Capture capture = transcode(html);
		assertEquals("注の欠落・重複なし", 3, capture.notes.size());
		assertEquals("callの欠落・重複なし", 3, capture.calls.size());
		assertEquals("1頁に収まる", 1, capture.calls.stream().mapToInt(Label::page).max().orElse(0));
		assertTrue("段の添付を回収した", capture.traces.stream().anyMatch(value -> value.event().equals("column-recover")));
		final Column column = capture.columns.get(0);
		final boolean vertical = column.bounds().flow().isVertical();
		for (final Note note : capture.notes) {
			final Placement box = note.placement();
			assertEquals(1, box.page());
			final double columnEnd = vertical ? column.bounds().x() : column.bounds().y() + column.bounds().height();
			assertTrue("注は段組の後(頁のblock-end)", vertical ? box.x() + box.width() <= columnEnd + EPSILON : box.y() >= columnEnd - EPSILON);
			for (final Placement line : bodyLines(capture)) assertFalse("本文と注を重ねない", overlaps(box, line));
			final Label call = capture.calls.stream().filter(value -> value.id() == note.id()).findFirst().orElseThrow();
			assertTrue("番号は頁内通番", box.text().startsWith(call.text().trim() + ". "));
		}
		final String display = capture.displayLists.stream().map(value -> new String(value, StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining());
		assertEquals("段の罫線は外し、頁の罫線だけ", 1, display.split("FootnoteSeparator\\[", -1).length - 1);
		assertTrue("EOF救済で注を捨てない: " + capture.footnoteWarnings, capture.footnoteWarnings.isEmpty());
	}

	/**
	 * codex レビュー 2026-09-08 必須 2: 持ち越し先の owner が次頁で入れ子になって不適格になり、
	 * 別の段組が別の位置で開く。持ち越しは受け取られず、再生の終わりに頁の宿主へ返る(捨てない)。
	 */
	public void testCarryReturnsToPageWhenContinuationBecomesIneligible() throws Exception {
		final StringBuilder html = new StringBuilder("<!doctype html><html><head><style>"
				+ "@page{size:400pt 100pt;margin:0}@page:first{size:200pt 100pt}"
				+ "body{margin:0;font-size:8pt;line-height:10pt}"
				+ ".a{column-width:150pt;column-gap:0;column-fill:auto}"
				+ ".b{column-count:2;column-gap:12pt;column-fill:auto}"
				+ "p{margin:0}.note{float:footnote;font-size:6pt;line-height:8pt}"
				+ "</style></head><body><div class='a'><div class='b'>");
		for (int i = 0; i < 17; ++i) html.append("<p>本文").append(i).append("。</p>");
		html.append("<p>末尾の呼出<span class='note'>持ち越される注。</span>本文。</p>");
		for (int i = 0; i < 12; ++i) html.append("<p>続き").append(i).append("。</p>");
		html.append("</div></div></body></html>");
		final Capture capture = transcode(html.toString());
		assertEquals(1, capture.calls.size());
		assertEquals("持ち越した注を捨てない", 1, capture.notes.size());
		assertTrue("注は呼び出しの頁以降", capture.notes.get(0).placement().page() >= capture.calls.get(0).page());
		assertTrue("EOF救済で注を捨てない: " + capture.footnoteWarnings, capture.footnoteWarnings.isEmpty());
		assertTrue("EOFの全pendingを回収", capture.traces.stream().anyMatch(value -> value.event().equals("finish") && value.pendingCount() == 0));
	}

	private static int columnIndex(final Column column, final double x, final double y) {
		final boolean vertical = column.bounds().flow().isVertical();
		final double relative = (vertical ? y - column.bounds().y() : x - column.bounds().x());
		final int index = (int) Math.floor((relative + EPSILON) / (column.lineSize() + column.gap()));
		return relative >= -EPSILON && index < column.count() ? index : -1;
	}

	private static boolean overlaps(final Placement a, final Placement b) {
		return a.page() == b.page() && Math.min(a.x() + a.width(), b.x() + b.width()) > Math.max(a.x(), b.x()) + EPSILON
				&& Math.min(a.y() + a.height(), b.y() + b.height()) > Math.max(a.y(), b.y()) + EPSILON;
	}

	private static List<Placement> bodyLines(final Capture capture) {
		return capture.lines.stream().filter(line -> capture.notes.stream().noneMatch(note -> overlaps(note.placement(), line))
				&& capture.floats.stream().noneMatch(floating -> overlaps(floating, line))).toList();
	}

	/** 増分3の単体試験。変換・宿主の開閉とは独立して判定を固定する。 */
	public void testVariableFootnoteOwnerAndPageContinuationAreEligible() {
		try (final HostPages pages = new HostPages()) {
			final HostRoot root = pages.root();
			final FlowBlockBox owner = hostColumns(false);
			root.path(owner);
			assertTrue(root.isEligibleFootnoteColumnOwner(root, owner));
			final FlowBlockBox continued = hostColumns(false);
			root.path(continued);
			assertTrue(root.isEligibleFootnoteColumnOwner(root, continued));
			assertFalse(root.isEligibleFootnoteColumnOwner(root, owner));
		}
	}

	public void testFixedHeightFootnoteOwnerIsIneligible() {
		try (final HostPages pages = new HostPages()) {
			final HostRoot root = pages.root();
			final FlowBlockBox owner = hostColumns(true);
			root.path(owner);
			assertFalse(root.isEligibleFootnoteColumnOwner(root, owner));
		}
	}

	public void testNestedFootnoteOwnerIsIneligible() {
		try (final HostPages pages = new HostPages()) {
			final HostRoot root = pages.root();
			final FlowBlockBox outer = hostColumns(false), inner = hostColumns(false);
			root.path(outer, inner);
			assertSame(inner, RootBuilder.footnoteColumnOwner(root));
			assertFalse(root.isEligibleFootnoteColumnOwner(root, inner));
			assertFalse(root.isEligibleFootnoteColumnOwner(root, outer));
		}
	}

	public void testColumnReplayFootnoteOwnerIsIneligible() {
		try (final HostPages pages = new HostPages()) {
			final HostRoot root = pages.root();
			final FlowBlockBox owner = hostColumns(false);
			root.path(owner);
			final ColumnBuilder replay = new ColumnBuilder(root, owner);
			final BlockBuilder child = new BlockBuilder(replay, hostBlock(WritingMode.TB, 30));
			assertFalse(root.isEligibleFootnoteColumnOwner(replay, owner));
			assertFalse(root.isEligibleFootnoteColumnOwner(child, owner));
		}
	}

	public void testFootnoteOwnerAcrossNarrowChildAndLocalMulticolumn() {
		try (final HostPages pages = new HostPages()) {
			final HostRoot root = pages.root();
			final FlowBlockBox owner = hostColumns(false);
			root.path(owner);
			final BlockBuilder child = new BlockBuilder(root, hostBlock(WritingMode.TB, 30));
			assertSame(owner, RootBuilder.footnoteColumnOwner(child));
			assertTrue(root.isEligibleFootnoteColumnOwner(child, owner));
			final FlowBlockBox inner = hostColumns(true);
			final BlockBuilder local = new BlockBuilder(root, inner);
			assertSame(inner, RootBuilder.footnoteColumnOwner(local));
			assertFalse(root.isEligibleFootnoteColumnOwner(local, inner));
			assertFalse(root.isEligibleFootnoteColumnOwner(local, owner));
		}
	}

	public void testAbsentColumnHostKeepsPageMeasurementAndCapacity() {
		try (final HostPages pages = new HostPages()) {
			final HostRoot root = pages.root();
			final FlowBlockBox owner = hostColumns(false);
			root.path(owner);
			assertTrue(LayoutUtils.isNone(root.getFootnoteLineSize(root, owner)));
			assertEquals(root.getPageOwnerLimit(), root.getPageLimit(), 0.0);
		}
	}

	public void testFootnoteColumnLineSizeOverridesNarrowParentAndInlineMax() {
		for (final WritingMode flow : new WritingMode[] { WritingMode.TB, WritingMode.RL }) {
			final BlockBuilder parent = new BlockBuilder(null, hostBlock(flow, 30));
			final BlockParams params = hostParams(flow);
			params.maxSize = flow.isVertical()
					? Dimension.create(0, 10, LengthType.AUTO, LengthType.ABSOLUTE)
					: Dimension.create(10, 0, LengthType.ABSOLUTE, LengthType.AUTO);
			final FloatBlockBox pageNote = new FloatBlockBox(params, new FootnotePos());
			final FloatBlockBox columnNote = new FloatBlockBox(params, new FootnotePos());
			final IntrinsicSizes sizes = new IntrinsicSizes(5, 20, 0);
			pageNote.shrinkToFit(parent, sizes, false);
			columnNote.shrinkToFit(parent, sizes, false, 90);
			assertEquals(10.0, pageNote.getInnerLineExtent(flow), 0.0);
			assertEquals(90.0, columnNote.getInnerLineExtent(flow), 0.0);
		}
	}

	private static FlowBlockBox hostColumns(final boolean fixed) {
		final BlockParams params = hostParams(WritingMode.TB);
		params.columns = new Columns((byte) 2, LayoutUtils.NONE, 10, Border.NONE_BORDER, Columns.FILL_AUTO);
		if (fixed) params.size = Dimension.create(0, 100, LengthType.AUTO, LengthType.ABSOLUTE);
		return new MulticolumnBlockBox(params, new FlowPos());
	}

	private static FlowBlockBox hostBlock(final WritingMode flow, final double lineSize) {
		final BlockParams params = hostParams(flow);
		params.size = flow.isVertical() ? Dimension.create(0, lineSize, LengthType.AUTO, LengthType.ABSOLUTE)
				: Dimension.create(lineSize, 0, LengthType.ABSOLUTE, LengthType.AUTO);
		return new FlowBlockBox(params, new FlowPos()) {
			{ this.width = flow.isVertical() ? 200 : lineSize; this.height = flow.isVertical() ? lineSize : 200; }
		};
	}

	private static BlockParams hostParams(final WritingMode flow) {
		final BlockParams params = new BlockParams();
		params.flow = flow;
		params.fontStyle = new net.zamasoft.pdfg2d.gc.font.FontStyleImpl(
				net.zamasoft.pdfg2d.gc.font.FontFamilyList.SERIF, 12,
				net.zamasoft.pdfg2d.gc.font.FontStyle.Style.NORMAL, net.zamasoft.pdfg2d.gc.font.FontStyle.Weight.W_400,
				net.zamasoft.pdfg2d.gc.font.FontStyle.Direction.LTR,
				net.zamasoft.pdfg2d.gc.font.FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		params.lineHeight = 14;
		return params;
	}

	private static final class HostRoot extends RootBuilder {
		HostRoot(final HostPages pages) { super(pages, BreakableBuilder.MODE_PAGE_BREAK); }
		void path(final FlowBlockBox... boxes) {
			this.flowStack = new ArrayList<>();
			for (final FlowBlockBox box : boxes) this.flowStack.add(new Flow(box, 0, 0));
		}
	}

	private static final class HostPages implements PageGenerator, AutoCloseable {
		private final PDFUserAgent ua = new PDFUserAgent() { };
		HostRoot root() { return new HostRoot(this); }
		public UserAgent getUserAgent() { return this.ua; }
		public PageBreakMode getPageSide() { return PageBreakMode.AUTO; }
		public PageBox nextPage() {
			final BlockParams params = hostParams(WritingMode.TB);
			params.size = Dimension.create(200, 200, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
			return new PageBox(params, this.ua);
		}
		public boolean drawPage(final PageBox page, final boolean last, final boolean forced) { return true; }
		public void close() { this.ua.dispose(); }
	}

	public void testCountAndBalanceUseReservedContainingHeight() throws Exception {
		for (final String fill : List.of("auto", "balance")) {
			final Capture capture = transcode(fixture().replace("column-fill: balance", "column-fill: " + fill));
			assertEquals(2, capture.pages.size());
			assertEquals(2, capture.notes.size());
			assertColumns(capture);
			assertTrue(capture.columns.stream().allMatch(column -> column.count() == 2));
			if (fill.equals("balance")) {
				assertTrue("均等割りで実際に二段を構築", capture.columns.stream().anyMatch(column -> column.actual() == 2));
			}
			for (final Page page : capture.pages) assertTrue("両ページとも帯を予約", page.inset() > 0);
		}
	}

	public void testColumnWidthIsResolvedAfterReservation() throws Exception {
		final String html = fixture().replace("column-count: 2", "column-width: 75pt");
		final Capture capture = transcode(html);
		assertColumns(capture);
		assertEquals(2, capture.notes.size());
		for (final Column column : capture.columns) {
			assertEquals("帯なしなら三段、予約後は二段になる幅", 2, column.count());
		}
		final Capture baseline = transcode(html.replace(AREA, ""));
		assertFalse(baseline.columns.isEmpty());
		assertTrue("column-widthの段数判定を実際に跨ぐ", baseline.columns.stream().allMatch(column -> column.count() == 3));
	}

	public void testTwoCallsShareOneBandAndOneGap() throws Exception {
		final Capture capture = transcode(document(".columns { column-count:2; column-gap:12pt; column-fill:balance }",
				"<div class='columns'><p>一<span class='note'>第一の注。</span>本文。</p>"
				+ "<p>二<span class='note'>第二の注。</span>本文。</p></div>"));
		assertEquals(1, capture.pages.size());
		assertEquals(2, capture.notes.size());
		assertColumns(capture);
		assertTrue(capture.columns.stream().anyMatch(column -> column.actual() == 2));
		assertEquals("ページ共通の帯でgapを一度だけ足す",
				6 + capture.notes.stream().mapToDouble(note -> note.placement().height()).sum(), capture.page(1).inset(), EPSILON);
		for (final Note note : capture.notes) assertEquals(252.0, note.placement().width(), EPSILON);
	}

	public void testColumnWidthCanResolveToOneColumn() throws Exception {
		final Capture capture = transcode(fixture().replace("column-count: 2", "column-width: 130pt"));
		assertColumns(capture);
		assertTrue(capture.columns.stream().allMatch(column -> column.count() == 1));
		assertEquals(2, capture.notes.size());
	}

	public void testBodyColumnsAndSpanUseTheSameContainingHeight() throws Exception {
		final String html = document("body { column-count:2; column-gap:12pt; column-fill:balance }"
				+ ".span { column-span:all }", "<p>先頭<span class='note'>横書きの注。</span>本文。</p>"
				+ "<p>段の本文。</p><p class='span'>ぶち抜きの見出し。</p><p>再開した段。</p><p>最後の段。</p>");
		final Capture capture = transcode(html);
		assertEquals(1, capture.pages.size());
		assertEquals(1, capture.notes.size());
		assertColumns(capture);
		assertTrue("spanの後に段組を開き直す", capture.columns.size() >= 2);
		assertFalse("ぶち抜き箱を実際に配置", capture.spans.isEmpty());
		for (final Placement span : capture.spans) {
			assertEquals("spanは段の行長でなく予約後の全高", capture.page(span.page()).innerHeight(), span.height(), EPSILON);
		}
	}

	public void testContinuousColumnsResizeOnFollowingPage() throws Exception {
		final Capture capture = transcode(document(".columns { column-count:2; column-gap:12pt; column-fill:auto }",
				"<div class='columns'><p>先頭<span class='note'>第一頁の注。</span></p>"
				+ "<p>段組の本文です。</p>".repeat(44) + "</div>"));
		assertTrue("一つの段組が自然改頁する", capture.pages.size() >= 2);
		assertTrue(capture.pages.get(0).inset() > 0);
		assertEquals("同頁に置いた注を次頁で二重予約しない", 0.0, capture.pages.get(1).inset(), 0.0);
		assertTrue("二頁目に継続段がある", capture.columns.stream().anyMatch(column -> column.page() == 2));
		assertColumns(capture);
		assertEquals(1, capture.notes.size());
	}

	public void testFirstNoteOnSecondNaturalPageShrinksContinuingColumns() throws Exception {
		final Capture capture = transcode(document(".columns { column-count:2; column-gap:12pt; column-fill:auto }",
				"<div class='columns'>" + "<p>本文。</p>".repeat(30)
				+ "<p>参照<span class='note'>二頁目の注。</span>続き。</p>" + "<p>末尾。</p>".repeat(4) + "</div>"));
		assertTrue("強制改頁なしで二頁目へ進む", capture.pages.size() >= 2);
		assertEquals(0.0, capture.page(1).inset(), 0.0);
		assertEquals(1, capture.calls.size());
		assertEquals(2, capture.calls.get(0).page());
		assertEquals(1, capture.notes.size());
		assertEquals("初出の注も同頁", 2, capture.notes.get(0).placement().page());
		assertTrue(capture.page(2).inset() > 0);
		assertTrue(capture.columns.stream().anyMatch(column -> column.page() == 2));
		assertColumns(capture);
	}

	public void testPageNumbersRestartAndFooterStaysOutsideBand() throws Exception {
		final Capture capture = transcode(fixture());
		// 規則だけを消すと既定block-endの段組脚注になり、このfixtureは一頁になる。
		// 柱の比較用には注も非表示にし、帯なしの二頁を組む。
		final Capture baseline = transcode(fixture().replace(AREA, "").replace("float: footnote", "display: none"));
		assertEquals(2, capture.pages.size());
		assertEquals("柱の比較対象も二ページ", 2, baseline.pages.size());
		assertTrue("比較対象は脚注なし", baseline.notes.isEmpty());
		assertEquals(2, capture.notes.size());
		assertEquals(2, capture.calls.size());
		for (int pageNumber = 1; pageNumber <= 2; ++pageNumber) {
			final int number = pageNumber;
			final List<Label> calls = capture.calls.stream().filter(call -> call.page() == number).toList();
			final List<Note> notes = capture.notes.stream().filter(note -> note.placement().page() == number).toList();
			assertEquals("各ページのcallを観測", 1, calls.size());
			assertEquals("callと同頁の帯に載る", 1, notes.size());
			assertEquals(calls.get(0).id(), notes.get(0).id());
			assertEquals("callの番号は毎頁1", "1", calls.get(0).text().trim());
			assertTrue("注もcallページの番号", notes.get(0).placement().text().startsWith("1. "));
			final Placement footer = footer(capture, number);
			assertEquals("ノンブルの座標・寸法・文字は帯なしと同じ", footer(baseline, number), footer);
			final Page page = capture.page(number);
			assertEquals(300.0, page.height(), 0.0);
			assertEquals(24.0, page.marginBottom(), 0.0);
			final Placement note = notes.get(0).placement();
			assertEquals("帯は段幅によらず版面全幅", 252.0, note.width(), EPSILON);
			assertEquals(0.0, note.x(), EPSILON);
			assertTrue(note.y() >= page.innerHeight() + 6 - EPSILON);
			assertTrue("脚注が下余白へ出ない", note.y() + note.height() <= 252 + EPSILON);
			assertTrue("段組でも柱と重ならない", note.y() + note.height() <= footer.y() + EPSILON);
		}
	}

	public void testColumnWarningOnlyChangesForVerticalBottom() throws Exception {
		final Logger logger = Logger.getLogger("net.zamasoft.foliojet.css.style.StyleEventMachine");
		final AtomicLong warnings = new AtomicLong(), infos = new AtomicLong();
		final Handler handler = new Handler() {
			public void publish(final LogRecord record) {
				if (record.getMessage().contains("footnote inside a multi-column ancestor")) warnings.incrementAndGet();
				if (record.getMessage().contains("placed at the end of the column containing the call")) infos.incrementAndGet();
			}
			public void flush() { }
			public void close() { }
		};
		logger.addHandler(handler);
		try {
			transcode(fixture());
			assertEquals("縦組みbottomの帯は頁のもの: 段の警告も情報も出ない", 0L, warnings.get() + infos.get());
			// F-8e: 既定block-endの段組の注は呼び出しの段の末尾へ。警告ではなく情報ログ。
			transcode(fixture().replace("float: bottom", "float: block-end"));
			assertEquals("段ごとの配置では従来の警告を出さない", 0L, warnings.get());
			assertTrue("段ごとの配置の情報ログ", infos.get() > 0);
			infos.set(0);
			// 横組みのbottomは従来のblock-end経路(段ごとの配置)なので同じ情報ログ。
			transcode(fixture().replace("writing-mode: vertical-rl", "writing-mode: horizontal-tb"));
			assertEquals(0L, warnings.get());
			assertTrue("横組みbottomも段ごとの配置", infos.get() > 0);
		} finally {
			logger.removeHandler(handler);
		}
	}

	public void testNormalFloatCallIsMeasuredAndPlacedOnSamePage() throws Exception {
		assertHostCall(".host { float:left; writing-mode:horizontal-tb }", "<div class='host'>", "</div>", true);
	}

	public void testTableCellCallIsMeasuredAndPlacedOnSamePage() throws Exception {
		assertHostCall("table { writing-mode:horizontal-tb; border-spacing:0 } td { padding:0 }",
				"<table><tr><td>", "</td></tr></table>", true);
	}

	public void testPageFloatCallIsNotReportedAndUsesLaterBand() throws Exception {
		assertHostCall(".host { float:top; writing-mode:horizontal-tb }", "<div class='host'>", "</div>", false);
	}

	public void testAbsoluteCallIsNotReportedAndUsesLaterBand() throws Exception {
		assertHostCall(".host { position:absolute; top:0; left:0; writing-mode:horizontal-tb }",
				"<div class='host'>", "</div>", false);
	}

	public void testMarginNoteCallIsNotReportedAndUsesLaterBand() throws Exception {
		assertHostCall(".host { float:-cssj-note-start; writing-mode:horizontal-tb }",
				"<div class='host'>", "</div>", false);
	}

	public void testHostCallsInsideColumns() throws Exception {
		final String columns = ".columns { column-count:2; column-gap:12pt; column-fill:auto }";
		for (final String host : List.of("float", "cell", "absolute")) {
			final String css = columns + switch (host) {
			case "float" -> ".host { float:left; writing-mode:horizontal-tb }";
			case "cell" -> "table { writing-mode:horizontal-tb; border-spacing:0 } td { padding:0 }";
			default -> ".host { position:absolute; top:0; left:0; writing-mode:horizontal-tb }";
			};
			final String open = host.equals("cell") ? "<table><tr><td>" : "<div class='host'>";
			final String close = host.equals("cell") ? "</td></tr></table>" : "</div>";
			final Capture capture = assertHostCall(css, "<div class='columns'><p>段の本文。</p>" + open,
					close + "<p>段の末尾。</p></div>", !host.equals("absolute"));
			assertColumns(capture);
		}
	}

	public void testDefaultBlockEndFloatCallsInsideColumnsStayOnCallPage() throws Exception {
		for (final String flow : List.of("vertical-rl", "horizontal-tb")) {
			// 短い本文とfloatの均等割りは実段数を保証しない。明示改段で
			// ColumnsContainerを作り、両書字方向で段内floatの走査を検査する。
			final String html = document(".columns { column-count:2; column-gap:12pt; column-fill:auto }"
					+ ".host { float:left } .next { page-break-before:always } .second { break-before:column }",
					"<p>先行<span class='note'>先行する注。</span>本文。</p><p class='next'>次頁の本文。</p>"
					+ "<div class='columns'><p>段の先頭。</p><div class='host'>呼出<span class='note'>段内の注。</span>続き</div>"
					+ "<p>段の本文。</p><p class='second'>次段の本文。</p></div>")
					.replace(AREA, "").replace("writing-mode:vertical-rl", "writing-mode:" + flow);
			final Capture capture = transcode(html);
			assertTrue("既定block-endではBを作らない", capture.reports.isEmpty());
			assertTrue("実際に複数段へ分ける: " + flow, capture.columns.stream().anyMatch(column -> column.actual() > 1));
			assertEquals(2, capture.calls.size());
			assertEquals(2, capture.notes.size());
			assertTrue("段内callは先行注とは別頁", capture.calls.get(1).page() > capture.calls.get(0).page());
			for (final Label call : capture.calls) {
				final Note note = capture.notes.stream().filter(value -> value.id() == call.id()).findFirst().orElseThrow();
				assertEquals("既定でも段内floatの注をcallと同頁へ: " + flow, call.page(), note.placement().page());
				assertEquals("文書通番でなく各頁の番号", "1", call.text().trim());
				assertTrue(note.placement().text(), note.placement().text().startsWith("1. "));
			}
			assertTrue("EOF救済を使わない: " + capture.footnoteWarnings, capture.footnoteWarnings.isEmpty());
		}
	}

	public void testContinuedPageSidesReserveAlternatingNamedDimensionsOnCallPage() throws Exception {
		// UAの面と表示番号を引き継ぐEPUB後続章相当。最初の面は順に
		// RIGHT_EVEN、SINGLE、RIGHT_ODD、LEFT_ODDとなる。
		final String[] modes = { "right-side", "single-side", "left-side", "double-side" };
		final CSSElement[] previous = { CSSElement.PAGE_FIRST_LEFT, CSSElement.PAGE_SINGLE_FIRST,
				CSSElement.PAGE_LEFT_EVEN, CSSElement.PAGE_RIGHT_EVEN };
		final CSSElement[] first = { CSSElement.PAGE_RIGHT_EVEN, CSSElement.PAGE_SINGLE,
				CSSElement.PAGE_RIGHT_ODD, CSSElement.PAGE_LEFT_ODD };
		final String body = "<p>参照<span class='note'>短い注。</span>本文。</p>"
				+ "<p class='next'>参照<span class='note'>次の注。</span>本文。</p>".repeat(3);
		final String html = document("html { page:named } .next { page-break-before:always }"
				+ "@page named { size:240pt 270pt; margin:18pt; padding:2pt; border:1pt solid }"
				+ "@page named:left { size:260pt 290pt; margin:20pt; padding:3pt }"
				+ "@page named:right { size:320pt 350pt; margin:24pt; padding:4pt; border:2pt solid }", body);
		for (int i = 0; i < modes.length; ++i) {
			final Capture capture = transcode(html, modes[i], previous[i], 42);
			assertEquals(4, capture.calls.size());
			assertEquals(4, capture.notes.size());
			assertEquals("named", capture.reports.get(0).pageName());
			assertTrue("初回名の引継ぎで不要な白紙世代を作らない", capture.reports.get(0).emitted());
			assertSame("指定した途中面から開始: " + modes[i], first[i], capture.pageSides.get(0));
			for (final Label call : capture.calls) {
				final Note note = capture.notes.stream().filter(value -> value.id() == call.id()).findFirst().orElseThrow();
				final FootnotePageProbeReport report = capture.reports.stream()
						.filter(value -> value.callIds().contains(call.id())).findFirst().orElseThrow();
				final Page page = capture.page(call.page());
				final CSSElement side = capture.pageSides.get(call.page() - 1);
				final double expectedWidth = side.isPseudoClass(CSSElement.PC_LEFT) ? 212.0
						: side.isPseudoClass(CSSElement.PC_RIGHT) ? 260.0 : 198.0;
				assertEquals(expectedWidth, page.innerWidth(), EPSILON);
				assertEquals("named", report.pageName());
				assertEquals("Bの照会面はCの実際の面と同じ寸法", page.innerWidth(), report.innerWidth(), EPSILON);
				assertEquals(page.innerHeight() + page.inset(), report.innerHeight(), EPSILON);
				assertEquals("途中面開始でも同頁", call.page(), note.placement().page());
				assertEquals(page.innerWidth(), note.placement().width(), EPSILON);
				assertTrue("左右別寸法の報告を採用", capture.plans.stream().anyMatch(plan -> plan.generation() == report.generation()
						&& plan.usable() && plan.reservedIds().contains(call.id())));
			}
		}
	}

	public void testAlternatingNamedPageDimensionsReserveOnCallPage() throws Exception {
		final String body = "<p>無名の本文。</p>" + ("<section class='small'><p>小<span class='note'>小の注。</span>本文。</p></section>"
				+ "<section class='large'><p>大<span class='note'>大の注。</span>本文。</p></section>").repeat(3);
		final Capture capture = transcode(document("@page small { size:240pt 270pt; margin:18pt; padding:3pt; border:1pt solid }"
				+ "@page large { size:360pt 390pt; margin:30pt; padding:4pt; border:2pt solid }"
				+ ".small { page:small } .large { page:large }", body));
		assertEquals(6, capture.calls.size());
		assertEquals(6, capture.notes.size());
		for (int i = 0; i < capture.notes.size(); ++i) {
			final Label call = capture.calls.get(i);
			final Note note = capture.notes.get(i);
			assertEquals(call.id(), note.id());
			assertEquals("寸法が交互に変わっても同頁", call.page(), note.placement().page());
			final Page page = capture.page(call.page());
			final FootnotePageProbeReport report = capture.reports.stream()
					.filter(value -> value.callIds().contains(call.id())).findFirst().orElseThrow();
			assertEquals(i % 2 == 0 ? "small" : "large", report.pageName());
			assertEquals(i % 2 == 0 ? 196.0 : 288.0, page.innerWidth(), EPSILON);
			assertEquals(i % 2 == 0 ? 226.0 : 318.0, report.innerHeight(), EPSILON);
			assertEquals(page.innerWidth(), report.innerWidth(), EPSILON);
			assertEquals(page.innerHeight() + page.inset(), report.innerHeight(), EPSILON);
			assertEquals("横書き注は遅れているCでなく呼出頁の幅", page.innerWidth(), note.placement().width(), EPSILON);
			assertTrue(capture.plans.stream().anyMatch(plan -> plan.generation() == report.generation()
					&& plan.usable() && plan.reservedIds().contains(call.id())));
		}
	}

	public void testFlexAnonymousItemsWithColumns() throws Exception {
		final AtomicLong boundaries = new AtomicLong();
		try (final Hook hook = new Hook(LayoutSource.class, "appendObserver", (Consumer<LayoutSource.Event>) event -> {
			if (event instanceof LayoutSource.AnonymousItemStart || event instanceof LayoutSource.AnonymousItemEnd) boundaries.incrementAndGet();
		})) {
			final Capture capture = transcode(document(".columns { column-count:2; column-gap:12pt }"
					+ ".flex { display:flex; writing-mode:horizontal-tb }",
					"<div class='columns'><p>本文<span class='note'>横書きの注。</span></p>"
					+ "<div class='flex'>直接文字<span>続き</span><p>要素項目</p>末尾</div></div>"));
			assertTrue("匿名項目を実際に合成し、Cの境界照合を通る", boundaries.get() > 0);
			assertFalse(capture.pages.isEmpty());
			assertEquals(1, capture.notes.size());
		}
	}

	public void testDifferentNamedPagesKeepWindowAndMainLogBounded() throws Exception {
		for (final int initial : List.of(480, 144)) {
			final Capture shortRun = transcode(namedDocument(initial, 4));
			final Capture longRun = transcode(namedDocument(initial, 16));
			assertTrue("名前付きページを数十頁出力", longRun.pages.size() >= 32);
			assertTrue("実際に幅と高さの違うページへ切り替える", longRun.pages.stream().map(Page::innerWidth).distinct().count() >= 2);
			assertTrue(longRun.pages.stream().map(page -> page.innerHeight() + page.inset()).distinct().count() >= 2);
			assertTrue("名前付きページの幾何を採用できる", longRun.plans.stream().anyMatch(plan -> plan.usable()));
			assertEquals("報告保持数は世代差+1以下", 0L, longRun.maxReportExcess);
			assertTrue("page-windowを実際に保持", shortRun.maxWindowBytes > 0);
			assertTrue("主ログの文字とイベントを実際に保持", shortRun.maxSourceBytes > 0 && shortRun.maxSourceEvents > 0);
			assertTrue("文書4倍でもCの文字キューは比例しない: " + shortRun.maxWindowBytes + " -> " + longRun.maxWindowBytes,
					longRun.maxWindowBytes <= shortRun.maxWindowBytes * 2 + 512);
			assertTrue("イベントキューも比例しない", longRun.maxDeliveries <= shortRun.maxDeliveries * 2 + 32);
			assertTrue("主ログも比例しない: " + shortRun.maxSourceEvents + " -> " + longRun.maxSourceEvents,
					longRun.maxSourceEvents <= shortRun.maxSourceEvents * 2 + 32);
			assertTrue("sliceに移した文字も含む保持量", longRun.maxSourceBytes <= shortRun.maxSourceBytes * 2 + 512);
			assertEquals("EOFで未配達文字を残さない", 0L, longRun.lastWindowBytes);
			assertEquals(0, longRun.lastDeliveries);
		}
	}

	public void testReportRetentionWithPersistentGenerationDrift() throws Exception {
		// 無名だけの文書のBは初回幾何を保つ。:firstとの寸法差で双方向の世代差を作る。
		for (final int first : List.of(144, 480)) {
			final int later = first == 144 ? 480 : 144;
			final Capture capture = transcode(document("@page { size:" + later + "pt " + later + "pt }"
					+ "@page :first { size:" + first + "pt " + first + "pt }",
					"<p>世代差のある本文です。</p>".repeat(320)));
			assertTrue(capture.pages.size() >= 10);
			if (first == 144) assertTrue("Bが恒常的に多く改頁", capture.maxProbeLead > 4);
			else assertTrue("Cが先行しても入力位置で配達", capture.windowDeliveries > 0);
			assertTrue("保持報告を実際に観測", capture.maxRetainedReports > 0);
			assertEquals("どの安全点も報告数<=max(0,B-C)+1", 0L, capture.maxReportExcess);
			assertFalse(capture.retention.isEmpty());
			assertTrue("B側Retentionにも報告数を含める", capture.retention.stream().anyMatch(state -> state.retainedReports() > 0));
			assertTrue(capture.retention.stream().allMatch(state ->
					state.retainedReports() <= Math.max(0, state.pages() - state.mainGeneration()) + 1));
			assertEquals(0L, capture.lastWindowBytes);
			assertEquals(0, capture.lastDeliveries);
		}
	}

	private static Capture assertHostCall(final String css, final String open, final String close, final boolean measured) throws Exception {
		final Capture capture = transcode(document(css, "<p>本文。</p>" + open
				+ "呼出<span class='note'>横書きの注。</span>続き" + close + "<p>終わり。</p>"));
		assertEquals("callを一度配置", 1, capture.calls.size());
		assertEquals("注を欠落・重複させない", 1, capture.notes.size());
		final Label call = capture.calls.get(0);
		final Note note = capture.notes.get(0);
		assertEquals(call.id(), note.id());
		assertFalse("Bの報告を観測", capture.reports.isEmpty());
		assertEquals("Bの確定木にcallが載るか: " + css, measured,
				capture.reports.stream().anyMatch(report -> report.callIds().contains(call.id())));
		if (measured) {
			assertTrue(capture.reports.stream().anyMatch(report -> report.measuredHeights().containsKey(call.id())));
			assertEquals("短い通常float・セルの注は同頁", call.page(), note.placement().page());
		} else {
			assertTrue("報告にないcallの注は後続ページの帯", note.placement().page() > call.page());
		}
		assertTrue(note.placement().text().startsWith("1. "));
		return capture;
	}

	private static void assertColumns(final Capture capture) {
		assertFalse("段組箱を実際に構築", capture.columns.isEmpty());
		for (final Column column : capture.columns) {
			final Page page = capture.page(column.page());
			assertEquals("帯をページから一度だけ引く", 252.0, page.innerHeight() + page.inset(), EPSILON);
			assertEquals("段組の包含寸法は予約済みのページ内寸", page.innerHeight(), column.innerHeight(), EPSILON);
			assertEquals("(L-H-(n-1)gap)/n", (252 - page.inset() - (column.count() - 1) * column.gap()) / column.count(),
					column.lineSize(), EPSILON);
		}
		final List<Placement> vertical = capture.lines.stream().filter(line -> line.flow().isVertical()).toList();
		assertFalse("本文行を実際に描画", vertical.isEmpty());
		for (final Placement line : vertical) {
			assertTrue("各段の本文が地の帯の手前で閉じる", line.y() + line.height() <= capture.page(line.page()).innerHeight() + EPSILON);
		}
	}

	private static Placement footer(final Capture capture, final int page) {
		final List<Placement> found = capture.lines.stream().filter(line -> line.page() == page
				&& line.y() >= 252 && line.text().trim().equals(Integer.toString(page))).toList();
		assertEquals("下余白のノンブルを観測: page=" + page + ", pages=" + capture.pages.size()
				+ ", lines=" + capture.lines.stream().filter(line -> line.y() >= 252).toList(), 1, found.size());
		return found.get(0);
	}

	private static String fixture() throws Exception {
		return Files.readString(Path.of("files/unittest/0125-footnote/footnote-bottom-columns.html"), StandardCharsets.UTF_8);
	}

	private static String document(final String css, final String body) {
		return "<!doctype html><?jp.cssj.property name='output.page-width' value='300pt'?>"
				+ "<?jp.cssj.property name='output.page-height' value='300pt'?>"
				+ "<html><head><meta charset='UTF-8'><style>@page { margin:24pt; " + AREA + " }"
				+ "body { margin:0; font-size:12pt; line-height:18pt; writing-mode:vertical-rl }"
				+ "p { margin:0 } .note { float:footnote; font-size:10pt; line-height:14pt }"
				+ css + "</style></head><body>" + body + "</body></html>";
	}

	private static String namedDocument(final int initial, final int repeats) {
		final String paragraphs = "<p>寸法の違う本文を読みます。</p>".repeat(10);
		final String body = "<p>初回の版面。</p>" + ("<section class='small'>" + paragraphs + "</section>"
				+ "<section class='large'>" + paragraphs + "</section>").repeat(repeats);
		return document("@page small { size:144pt 180pt } @page large { size:420pt 480pt }"
				+ ".small { page:small } .large { page:large }", body).replace("value='300pt'", "value='" + initial + "pt'");
	}

	private record Page(double height, double innerWidth, double innerHeight, double inset, double marginBottom) { }
	private record Column(int page, double innerHeight, double lineSize, int count, int actual, double gap, Placement bounds) { }
	private record Placement(int page, double x, double y, double width, double height, WritingMode flow, String text) { }
	private record Note(long id, Placement placement) { }
	private record Label(int page, long id, String text, double x, double y, double width, double height) { }

	/** 可変木を残さず、変換スレッドで値だけを採る。DirectSession終了後に検査する。 */
	private static final class Capture {
		final List<Page> pages = new ArrayList<>();
		final List<CSSElement> pageSides = new ArrayList<>();
		final List<String> footnoteWarnings = new ArrayList<>();
		final List<Column> columns = new ArrayList<>();
		final List<Placement> lines = new ArrayList<>(), spans = new ArrayList<>();
		final List<Placement> floats = new ArrayList<>();
		final List<RootBuilder.FootnoteTrace> traces = new ArrayList<>();
		final List<RootBuilder.ColumnFootnotePlacement> columnNotes = new ArrayList<>();
		final List<byte[]> displayLists = new ArrayList<>();
		final List<Note> notes = new ArrayList<>();
		final List<Label> calls = new ArrayList<>();
		final List<FootnotePageProbeReport> reports = new ArrayList<>();
		final List<RootBuilder.FootnotePlanSnapshot> plans = new ArrayList<>();
		final List<FootnotePageProbe.Retention> retention = new ArrayList<>();
		long maxReportExcess;
		int maxRetainedReports;
		long maxWindowBytes, maxSourceBytes, lastWindowBytes, windowDeliveries, maxProbeLead;
		int maxDeliveries, maxSourceEvents, lastDeliveries;
		Page page(final int number) { return this.pages.get(number - 1); }
		void window(final FootnotePageProbe.WindowRetention window) {
			this.maxRetainedReports = Math.max(this.maxRetainedReports, window.retainedReports());
			this.maxReportExcess = Math.max(this.maxReportExcess,
					window.retainedReports() - Math.max(0, window.reportGeneration() - window.mainGeneration()) - 1);
			this.maxWindowBytes = Math.max(this.maxWindowBytes, window.currentBytes());
			this.maxDeliveries = Math.max(this.maxDeliveries, window.deliveries());
			this.maxSourceEvents = Math.max(this.maxSourceEvents, window.source().retainedEvents() + window.source().slicedEvents());
			this.maxSourceBytes = Math.max(this.maxSourceBytes, window.sourceBytes());
			this.maxProbeLead = Math.max(this.maxProbeLead, window.reportGeneration() - window.mainGeneration());
			this.windowDeliveries = window.windowDeliveries();
			this.lastWindowBytes = window.currentBytes();
			this.lastDeliveries = window.deliveries();
		}
	}

	private static final class Hook implements AutoCloseable {
		private final Field field;
		private final Object saved;
		Hook(final Class<?> type, final String name, final Object value) throws Exception {
			this.field = type.getDeclaredField(name);
			this.field.setAccessible(true);
			this.saved = this.field.get(null);
			this.field.set(null, value);
		}
		public void close() throws Exception { this.field.set(null, this.saved); }
	}

	private static Capture transcode(final String html) throws Exception {
		return transcode(html, null, null, 0);
	}

	private static Capture transcode(final String html, final String printMode,
			final CSSElement previousSide, final int previousPageNumber) throws Exception {
		final Capture capture = new Capture();
		final PDFUserAgent ua = new PDFUserAgent() {
			@Override
			public void prepare(final PrepareMode mode) {
				super.prepare(mode);
				if (printMode != null) {
					this.getPassContext().setPageSide(previousSide);
					this.getPassContext().setPageNumber(previousPageNumber);
				}
				this.visitor = new CaptureVisitor(this, capture);
			}
		};
		ua.getUAContext().setFootnotePageProbeListener(capture.reports::add);
		final Logger logger = Logger.getLogger(RootBuilder.class.getName());
		final Handler warnings = new Handler() {
			public void publish(final LogRecord record) {
				if (record.getMessage().startsWith("giving up on footnotes")) capture.footnoteWarnings.add(record.getMessage());
			}
			public void flush() { }
			public void close() { }
		};
		logger.addHandler(warnings);
		try (final Hook trace = new Hook(RootBuilder.class, "footnoteTraceObserver",
				(Consumer<RootBuilder.FootnoteTrace>) capture.traces::add);
				final Hook columns = new Hook(RootBuilder.class, "columnFootnoteObserver",
						(Consumer<RootBuilder.ColumnFootnotePlacement>) capture.columnNotes::add);
				final AutoCloseable display = DisplayListDumper.observePages((drawer, number) -> {
					final StringBuilder text = new StringBuilder();
					drawer.dump(text, "");
					capture.displayLists.add(text.toString().getBytes(StandardCharsets.UTF_8));
				});
				final Hook window = new Hook(Class.forName("net.zamasoft.foliojet.css.style.RecordingLayoutSink"), "windowObserver",
				(Consumer<FootnotePageProbe.WindowRetention>) capture::window);
				final Hook plan = new Hook(RootBuilder.class, "footnotePlanObserver",
						(Consumer<RootBuilder.FootnotePlanSnapshot>) capture.plans::add);
				final Hook retained = new Hook(FootnotePageProbe.class, "retentionObserver",
						(Consumer<FootnotePageProbe.Retention>) capture.retention::add)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
			try {
				session.setUserAgent(ua);
				session.setResults(new SingleResult(new StreamFragmentedOutput(OutputStream.nullOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.property-pi", "true");
				session.property("processing.pass-count", "1");
				if (printMode != null) session.property("output.print-mode", printMode);
				CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
						URI.create("file:///footnote-columns.html"), "text/html", "UTF-8");
			} finally {
				session.close();
			}
		} finally {
			logger.removeHandler(warnings);
		}
		return capture;
	}

	private static final class CaptureVisitor extends PDFVisitor {
		private final UserAgent ua;
		private final Capture capture;
		private int page;
		private boolean mainPage;
		CaptureVisitor(final UserAgent ua, final Capture capture) { super(ua); this.ua = ua; this.capture = capture; }
		@Override
		public void nextPage(final PDFGC gc) {
			super.nextPage(gc);
			++this.page;
			this.mainPage = true;
		}
		@Override
		public void visitBox(final AffineTransform transform, final IBox box, final Drawer drawer, final double x, final double y) {
			super.visitBox(transform, box, drawer, x, y);
			if (box instanceof PageBox pageBox && this.mainPage) {
				this.mainPage = false;
				this.capture.pageSides.add(this.ua.getPassContext().getPageSide());
				this.capture.pages.add(new Page(pageBox.getHeight(), pageBox.getInnerWidth(), pageBox.getInnerHeight(),
						pageBox.getFootInset(), pageBox.getFrame().margin.bottom));
			} else if (box instanceof MulticolumnBlockBox column) {
				this.capture.columns.add(new Column(this.page, column.getInnerHeight(), column.getLineSize(), column.getColumnCount(),
						column.getContainer() instanceof ColumnsContainer columns ? columns.getColumnCount() : 1, column.getBlockParams().columns.gap,
						this.placement(box, column.getBlockParams().flow, x, y)));
			} else if (box instanceof FlowBlockBox flow && flow.getFlowPos().columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.capture.spans.add(this.placement(box, flow.getBlockParams().flow, x, y));
			} else if (box instanceof FloatBlockBox note && box.getPos() instanceof FootnotePos) {
				this.capture.notes.add(new Note(note.getParams().footnoteId, this.placement(box, note.getBlockParams().flow, x, y)));
			} else if (box instanceof FloatBlockBox floating) {
				this.capture.floats.add(this.placement(box, floating.getBlockParams().flow, x, y));
			} else if (box instanceof AbstractLineBox line) {
				this.capture.lines.add(this.placement(box, line.getLineParams().flow, x, y));
			} else if (box instanceof AbstractReplacedBox replaced
					&& replaced.getReplacedParams().image instanceof FootnoteLabelImage label && !label.isMarker()) {
				this.capture.calls.add(new Label(this.page, label.getFootnoteId(), label.getAltString(), x, y, box.getWidth(), box.getHeight()));
			}
		}
		private Placement placement(final IBox box, final WritingMode flow, final double x, final double y) {
			final StringBuilder text = new StringBuilder();
			box.getText(text);
			return new Placement(this.page, x, y, box.getWidth(), box.getHeight(), flow, text.toString());
		}
	}
}
