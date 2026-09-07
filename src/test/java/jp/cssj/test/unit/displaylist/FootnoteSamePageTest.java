package jp.cssj.test.unit.displaylist;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.FootnotePageProbeReport;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.ua.FootnoteArea;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/** ページ分割の形に依存せず、予約不足・FIFO・後着・停滞の境界条件を固定します。 */
public final class FootnoteSamePageTest extends TestCase {
	public void testUnreservedHeadCannotBeReplacedByReservedLaterId() throws Exception {
		try (final Pages pages = new Pages(Map.of(20L, 20.0), false)) {
			final Root root = pages.root();
			root.addFootnote(note(19, 20));
			root.addFootnote(note(20, 20));
			call(root, 19);
			call(root, 20);
			root.finish();
			assertEquals("予約した20の件数で未予約の19を置かない", List.of(), pages.notes.get(0));
			assertEquals(List.of(19L, 20L), pages.notes.get(1));
			assertEquals("空いた予約は縮めない", 26.0, pages.insets.get(0), 0.0);
		}
	}

	public void testActualHeightOverrunKeepsReservationAndCarriesNote() throws Exception {
		try (final Pages pages = new Pages(Map.of(0L, 10.0), false)) {
			final Root root = pages.root();
			root.addFootnote(note(0, 30));
			call(root, 0);
			root.finish();
			assertEquals(16.0, pages.insets.get(0), 0.0);
			assertEquals(List.of(), pages.notes.get(0));
			assertEquals(List.of(0L), pages.notes.get(1));
			assertEquals(36.0, pages.insets.get(1), 0.0);
		}
	}

	public void testMissingReportAndLateBodyKeepCallPageNumbers() throws Exception {
		for (final boolean finished : List.of(false, true)) {
			try (final Pages pages = new Pages(null, finished)) {
				final Root root = pages.root();
				call(root, 0);
				call(root, 1);
				root.advance();
				root.addFootnote(note(0, 20));
				root.addFootnote(note(1, 20));
				assertEquals("本文未着のcallも文書順で採番して残す", List.of(1, 2), pendingNumbers(root));
				assertEquals("報告なしのページ途中でHを増やさない", 0.0, root.getCurrentPageBox().getFootInset(), 0.0);
				root.finish();
				assertEquals(List.of(), pages.notes.get(0));
				assertEquals(List.of(), pages.notes.get(1));
				assertEquals("Bが未確定でも終端済みでも持ち越しで前進", List.of(0L, 1L), pages.notes.get(2));
			}
		}
	}

	public void testDeferredHeadIsNotRevivedByLaterReport() throws Exception {
		try (final Pages pages = new Pages(Map.of(0L, 40.0), false)) {
			final Root root = pages.root();
			root.addFootnote(note(0, 40));
			root.advance();
			root.advance();
			assertEquals("callの無い空ページが続けば予約を外す", 0.0, root.getCurrentPageBox().getFootInset(), 0.0);
			call(root, 0);
			root.advance();
			assertEquals("call確定後は持ち越し予約へ戻る", 46.0, root.getCurrentPageBox().getFootInset(), 0.0);
			root.finish();
			assertEquals(List.of(0L), pages.notes.get(3));
		}
	}

	public void testEofForcesMissingCallAndOversizedNote() throws Exception {
		try (final Pages pages = new Pages(Map.of(0L, 400.0), true)) {
			final Root root = pages.root();
			root.addFootnote(note(0, 400));
			root.finish();
			assertEquals("呼び出しが見つからなくてもEOFで一度だけ配置", List.of(0L),
					pages.notes.stream().flatMap(List::stream).toList());
			assertTrue(pages.notes.size() <= 3);
			assertEquals("巨大注の予約だけを0.6に抑える", 120.0, pages.insets.get(pages.insets.size() - 1), 0.0);
		}
	}

	public void testIneligibleReportDoesNotReserve() throws Exception {
		try (final Pages pages = new Pages(Map.of(0L, 20.0), false)) {
			pages.emitted = false;
			assertEquals(0.0, pages.root().getCurrentPageBox().getFootInset(), 0.0);
		}
	}

	public void testMismatchedReportGeometryStillDoesNotReserve() throws Exception {
		try (final Pages pages = new Pages(Map.of(0L, 20.0), false)) {
			pages.reportWidth = 201;
			assertEquals("別の幾何の報告は安全弁で不採用", 0.0, pages.root().getCurrentPageBox().getFootInset(), 0.0);
		}
	}

	public void testTextTailDoesNotReplayUndeliveredInlineOrReplacedEvents() throws Exception {
		try (final Pages pages = new Pages(null, true);
				final var source = new net.zamasoft.foliojet.layout.fragment.LayoutSource();
				final var fonts = new net.zamasoft.pdfg2d.pdf.font.FontManagerImpl(
						net.zamasoft.pdfg2d.pdf.font.ConfigurablePDFFontSourceManager.getDefaultFontSourceManager())) {
			pages.fonts = fonts;
			pages.visibleEnd = 1;
			final Root root = pages.root();
			source.append(new net.zamasoft.foliojet.layout.fragment.LayoutSource.Chars(0, "ab".toCharArray(), false));
			final var inline = new net.zamasoft.foliojet.layout.box.params.InlineParams();
			inline.fontStyle = params(WritingMode.TB).fontStyle;
			inline.fontManager = fonts;
			// InlineBox は fontStyle/lineBreakRules/fontManager の未設定を assert する
			inline.lineBreakRules = new net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules() {
				public boolean atomic(final char before, final char after) { return false; }
				public boolean canSeparate(final char before, final char after) { return false; }
			};
			inline.element = new net.zamasoft.foliojet.layout.segment.StructureToken(1, "span", null,
					new org.xml.sax.helpers.AttributesImpl());
			source.append(new net.zamasoft.foliojet.layout.fragment.LayoutSource.Start(
					net.zamasoft.foliojet.layout.segment.BoxRecipe.freeze(
							net.zamasoft.foliojet.layout.fragment.LayoutSource.BoxKind.INLINE,
							new net.zamasoft.foliojet.layout.box.impl.InlineBox(inline,
									new net.zamasoft.foliojet.layout.box.params.InlinePos()))));
			final var image = new net.zamasoft.foliojet.layout.box.params.ReplacedParams();
			image.fontStyle = inline.fontStyle;
			image.element = net.zamasoft.foliojet.css.CSSElement.FOOTNOTE_CALL;
			image.footnoteId = 77;
			image.image = new net.zamasoft.pdfg2d.g2d.image.RasterImageImpl(
					new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB));
			source.append(new net.zamasoft.foliojet.layout.fragment.LayoutSource.Replaced(
					net.zamasoft.foliojet.layout.segment.ReplacedRecipe.freeze(
							new net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox(image,
									new net.zamasoft.foliojet.layout.box.params.InlinePos())).orElseThrow()));
			source.append(new net.zamasoft.foliojet.layout.fragment.LayoutSource.EndBlock());
			assertTrue(net.zamasoft.foliojet.layout.SourceReplayer.replayTextTail(source, 0, -1, false, root, pages));
			final StringBuilder text = new StringBuilder();
			root.getCurrentPageBox().getText(text);
			assertEquals("配達済み文字だけを再生", "ab", text.toString());
			assertTrue("後続の置換callを先に再生しない", RootBuilder.collectFootnoteCalls(root.getCurrentPageBox()).isEmpty());
			assertEquals("尾部sliceのリースも解放", 0, source.retentionSnapshot().leases());
		}
	}

	private static BlockParams params(final WritingMode flow) {
		final BlockParams params = new BlockParams();
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		params.lineHeight = 14;
		params.flow = flow;
		return params;
	}

	/** 完成済み箱の計測値を与える。テキスト組版の挙動は実変換試験で検査します。 */
	private static FloatBlockBox note(final long id, final double extent) {
		final BlockParams params = params(WritingMode.TB);
		params.footnoteId = id;
		return new FloatBlockBox(params, new FootnotePos()) {
			{ this.width = 200; this.height = extent; }
		};
	}

	private static void call(final Root root, final long id) {
		final BlockParams params = params(WritingMode.RL);
		params.element = net.zamasoft.foliojet.css.CSSElement.FOOTNOTE_CALL;
		params.footnoteId = id;
		root.getCurrentPageBox().getContainer().addFlow(new FlowBlockBox(params, new FlowPos()), 0);
	}

	private static List<Integer> pendingNumbers(final Root root) throws Exception {
		final Field pending = RootBuilder.class.getDeclaredField("pendingFootnotes");
		pending.setAccessible(true);
		final List<Integer> numbers = new ArrayList<>();
		for (final Object entry : (Iterable<?>) pending.get(root)) {
			final Field number = entry.getClass().getDeclaredField("assignedNumber");
			number.setAccessible(true);
			numbers.add(number.getInt(entry));
		}
		return numbers;
	}

	private static final class Root extends RootBuilder {
		Root(final Pages pages) { super(pages, BreakableBuilder.MODE_PAGE_BREAK); }

		/** 既存のページ確定・開始を駆動し、分割アルゴリズムから独立してページ境界を与える。 */
		void advance() throws Exception {
			this.finishLayout();
			this.getPageGenerator().drawPage(this.getCurrentPageBox(), false, false);
			final Method next = RootBuilder.class.getDeclaredMethod("nextPage");
			next.setAccessible(true);
			final PageBox page = (PageBox) next.invoke(this);
			final Field current = RootBuilder.class.getDeclaredField("pageBox");
			current.setAccessible(true);
			current.set(this, page);
			final Method begin = RootBuilder.class.getDeclaredMethod("beginPage");
			begin.setAccessible(true);
			begin.invoke(this);
			this.contextFlow = new Flow(page, 0, 0);
		}
	}

	private static final class Pages implements PageGenerator, AutoCloseable {
		private final PDFUserAgent ua = new PDFUserAgent() { };
		private final Map<Long, Double> heights;
		private final boolean finished;
		private boolean emitted = true;
		private double reportWidth = 200;
		private net.zamasoft.pdfg2d.gc.font.FontManager fonts;
		private long visibleEnd = Long.MAX_VALUE;
		private final List<List<Long>> notes = new ArrayList<>();
		private final List<Double> insets = new ArrayList<>();

		Pages(final Map<Long, Double> heights, final boolean finished) {
			this.heights = heights;
			this.finished = finished;
			this.ua.getUAContext().setFootnoteArea(FootnoteArea.DEFAULT.withPosition(FootnoteArea.Position.BOTTOM));
		}

		Root root() {
			final Root root = new Root(this);
			root.startFootnoteInput();
			return root;
		}

		public UserAgent getUserAgent() { return this.ua; }
		public PageBreakMode getPageSide() { return PageBreakMode.AUTO; }
		public boolean isFootnotePageProbeEnabled() { return true; }
		public boolean isFootnotePageProbeFinished() { return this.finished; }
		public long getDeliveredEventEnd() { return this.visibleEnd; }
		public int getDeliveredCharEnd() { return 2; }
		public FootnotePageProbeReport getFootnotePageProbeReport(final long generation) {
			return this.heights == null ? null : new FootnotePageProbeReport(generation, null, this.emitted, 0, 0, this.reportWidth, 200,
					WritingMode.RL, this.heights.keySet(), this.heights, Set.of(), 0, 1,
					FootnotePageProbeReport.Completion.DRAW_PAGE, 0);
		}
		public PageBox nextPage() {
			final BlockParams params = params(WritingMode.RL);
			params.fontManager = this.fonts;
			params.lineBreakRules = new net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules() {
				public boolean atomic(final char before, final char after) { return false; }
				public boolean canSeparate(final char before, final char after) { return false; }
			};
			params.color = net.zamasoft.pdfg2d.gc.paint.GrayColor.BLACK;
			params.size = Dimension.create(200, 200, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
			return new PageBox(params, this.ua);
		}
		public boolean drawPage(final PageBox page, final boolean lastPage, final boolean forced) {
			final List<Long> ids = new ArrayList<>();
			page.getContainer().eachFloatingBox(box -> {
				if (box.getPos() instanceof FootnotePos) ids.add(box.getParams().footnoteId);
			});
			this.notes.add(List.copyOf(ids));
			this.insets.add(page.getFootInset());
			return true;
		}
		public void close() { this.ua.dispose(); }
	}
}
