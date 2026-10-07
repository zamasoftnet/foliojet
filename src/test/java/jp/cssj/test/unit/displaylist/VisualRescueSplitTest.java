package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.rescue.RescuePolicy;
import net.zamasoft.foliojet.layout.rescue.RescueStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Behavior tests for visual rescue split (introduced on 2026-07-25, increment 5.
 * Design consultation,
 * development record).
 *
 * <p>
 * Increments 6/7 (2026-07-25) extended coverage to oversized lines (huge fonts and tall inline
 * blocks), blocks whose writing mode differs from the trunk, table cells, multi-column layout, and floats.
 * <b>Only the path that geometrically cuts an entire table ({@code BoxType.TABLE}) remains deferred</b>:
 * tables have their own row, row-group, and cell splitting mechanisms, and the current return values cannot
 * distinguish whether {@code Keep}/{@code Move} means "the internal mechanism handled it" or
 * "no progress is actually possible."
 * </p>
 *
 * <p>
 * The display list (with coordinates) fixes the geometry. {@code VisualRescueBoxTest} separately fixes
 * the clip intersection for each fragment, so these tests check
 * <b>page count, drawing on every page, fragment coordinates, and artifact flags</b>:
 * these three points directly expose the absolute requirement to avoid unintended (effectively blank)
 * pages.
 * </p>
 */
public class VisualRescueSplitTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	public VisualRescueSplitTest(final String name) {
		super(name);
	}

	// ------------------------------------------------------------------
	// Horizontal writing
	// ------------------------------------------------------------------

	/**
	 * An image taller than the page is covered exactly by its upper and lower fragments.
	 * Each fragment shifts the entire original box by the consumed amount and clips it, so
	 * the drawing origin y advances monotonically through 0, -200, -400.
	 * This fixes the requirement that the fragment seams join exactly.
	 */
	public void testTallImageIsSlicedAcrossPages() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-tall.html", RescuePolicy.ENABLED);
		assertEquals("画像500pt / ページ200pt = 3ページ(+後続は3ページ目に収まる)", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
		// Subsequent content follows immediately after the last fragment.
		assertTrue("3ページ目に後続テキストがある: " + pages.get(2), pages.get(2).contains("Text["));
	}

	/** Disabling rescue draws the image overflowing on one page, as before. */
	public void testDisabledPolicyKeepsLegacyOverflow() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-tall.html", RescuePolicy.DISABLED);
		assertEquals("従来の挙動: 画像ははみ出したまま1ページ目に描かれ、後続だけが2ページ目へ送られる", 2,
				pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
	}

	/**
	 * Progress continues across three or more pages and terminates (1000 pt = 5 fragments).
	 * Also check that no extra blank pages appear between them.
	 */
	public void testHugeImageAdvancesAndTerminates() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-huge.html", RescuePolicy.ENABLED);
		assertEquals("画像1000pt / ページ200pt = 5断片 + 後続1ページ", 6, pages.size());
		assertNoBlankPage(pages);
		for (int i = 0; i < 5; ++i) {
			assertDrawableAt(pages.get(i), 0, 0.0, i == 0 ? 0.0 : -200.0 * i, i > 0);
		}
		assertTrue("6ページ目は後続テキストだけ: " + pages.get(5), pages.get(5).contains("Text["));
		assertFalse("6ページ目に画像断片は残らない: " + pages.get(5), pages.get(5).contains("AbsoluteRectFrame"));
	}

	/**
	 * An image whose height is an exact multiple of the page height produces no zero-remainder
	 * fragment page (i.e., an effectively blank page).
	 */
	public void testExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-exact.html", RescuePolicy.ENABLED);
		assertEquals("画像400pt / ページ200pt = ちょうど2ページ", 2, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
	}

	// ------------------------------------------------------------------
	// Vertical writing
	// ------------------------------------------------------------------

	/**
	 * In vertical writing, the page axis advances from right to left. The fragment drawing origin x
	 * <b>increases</b> through -300, -100, 100 because consumption proceeds from the right edge of
	 * the original box (the start edge in the page direction).
	 */
	public void testVerticalWritingSlicesAlongTheRightToLeftPageAxis() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-tall-vert.html", RescuePolicy.ENABLED);
		assertEquals("画像500pt / ページ200pt = 3ページ", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, -300.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, -100.0, 0.0, true);
		assertDrawableAt(pages.get(2), 0, 100.0, 0.0, true);
	}

	// ------------------------------------------------------------------
	// Increment 6: oversized lines (huge fonts and tall inline blocks)
	// ------------------------------------------------------------------

	/**
	 * Cut a paragraph whose single line is taller than the page (huge font). Line splitting
	 * <b>makes no progress</b> because it always leaves one line at the fragment start:
	 * replace only that point where progress stops.
	 */
	public void testHugeFontLineIsSliced() throws Exception {
		final List<String> pages = render("0480-rescue-split/huge-font-line.html", RescuePolicy.ENABLED);
		assertEquals("行500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
		assertTrue("最終断片の後に後続が続く: " + pages.get(2), pages.get(2).contains("y=100.00 Text["));
	}

	/** A line whose height is an exact multiple of the page height produces no extra page. */
	public void testHugeFontLineExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/huge-font-exact.html", RescuePolicy.ENABLED);
		assertEquals("行400pt / ページ200pt = ちょうど2断片", 2, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
	}

	/**
	 * Tall inline blocks are caught by <b>the same path</b> (oversized lines).
	 * There is no branch specific to inline blocks.
	 */
	public void testTallInlineBlockIsSlicedAsAHugeLine() throws Exception {
		final List<String> pages = render("0480-rescue-split/tall-inline-block.html", RescuePolicy.ENABLED);
		assertEquals("インラインブロック500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
	}

	/**
	 * <b>Do not rescue paragraphs with multiple lines</b>. Line splitting actually advances
	 * (leaving the first line and sending the rest to the next fragment), so this is not a point
	 * where progress stops. Geometrically cutting the entire paragraph here causes a clear regression:
	 * bands of every line appear on every page.
	 */
	public void testMultiLineParagraphIsSplitByLinesNotSliced() throws Exception {
		final List<String> enabled = render("2010-LIMIT/image-line.html", RescuePolicy.ENABLED);
		final List<String> disabled = render("2010-LIMIT/image-line.html", RescuePolicy.DISABLED);
		// Split the first page at a line boundary (leave only the first line); rescue does not change this.
		assertEquals("1ページ目は行分割", disabled.get(0), enabled.get(0));
		assertFalse("1ページ目は切り分けていない: " + enabled.get(0), enabled.get(0).contains("clip="));
		assertNoBlankPage(enabled);
		// Rescue the remaining line (one image taller than the page), just like any line containing a tall image.
		// (2026-10-04. Previously, an invalid break opportunity between the image and the end of its enclosing span
		// allowed it to escape rescue as a "line that can still be split." Treating atomic inlines
		// as U+FFFC during word segmentation removed this opportunity: TECH-20261003-004, item ⑧.)
		for (int i = 1; i < enabled.size(); ++i) {
			assertEquals("2ページ目以降は画像1つの断片: " + enabled.get(i), 2,
					enabled.get(i).lines().filter(l -> l.contains("AbsoluteRectFrame")).count());
		}
	}

	// ------------------------------------------------------------------
	// Increment 6: blocks whose writing mode differs from the trunk
	// ------------------------------------------------------------------

	/**
	 * The engine itself <b>classifies blocks whose writing mode differs from the trunk as atomic</b>
	 * and routes them to the same terminal path as replaced elements. That is a point where progress
	 * stops, so cut them using the same rule.
	 */
	public void testOrthogonalBlockIsSliced() throws Exception {
		final List<String> pages = render("0480-rescue-split/orthogonal-block.html", RescuePolicy.ENABLED);
		assertEquals("ブロック500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		// The frame (background) is drawn as a "frame path." Even with fragments, the first is real content,
		// and continuations are emitted as artifacts.
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
	}

	/** A block with a different writing mode whose height divides exactly produces no extra page. */
	public void testOrthogonalBlockExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/orthogonal-block-exact.html", RescuePolicy.ENABLED);
		assertEquals("ブロック400pt / ページ200pt = ちょうど2断片", 2, pages.size());
		assertNoBlankPage(pages);
	}

	// ------------------------------------------------------------------
	// Increment 6: table cells and multi-column layout (when the fragmentainer is not a page)
	// ------------------------------------------------------------------

	/** Use the same decision and transport inside table cells (only the fragmentainer changes to a cell). */
	public void testTallImageInTableCellIsSliced() throws Exception {
		final List<String> pages = render("0480-rescue-split/cell-tall-image.html", RescuePolicy.ENABLED);
		assertEquals("セル内の画像500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(1), 1, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 1, 0.0, -400.0, true);
	}

	/** An exactly divisible height produces no extra page inside a table cell either. */
	public void testTallImageInTableCellExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/cell-tall-image-exact.html", RescuePolicy.ENABLED);
		assertEquals("セル内の画像400pt / ページ200pt = ちょうど2断片", 2, pages.size());
		assertNoBlankPage(pages);
	}

	/**
	 * In multi-column layout, cut at <b>the current fragmentainer capacity</b>, not the page:
	 * advance to the next column, then to the next page when columns run out. A 500 pt block occupies
	 * two columns on the first page (200+200) and two on the second (55+45 after column balancing).
	 */
	public void testTallBlockInColumnsUsesTheColumnAsFragmentainer() throws Exception {
		final List<String> pages = render("0480-rescue-split/column-tall-block.html", RescuePolicy.ENABLED);
		assertEquals("段(200pt)を単位に切るので2ページ", 2, pages.size());
		assertNoBlankPage(pages);
		// First page: column 1 contains the first fragment (real content), column 2 a continuation (artifact).
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(0), 1, 160.0, -200.0, true);
	}

	/**
	 * A block whose height is an exact multiple of the column height fits in two columns on one page
	 * and produces no extra page.
	 */
	public void testTallBlockInColumnsExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/column-tall-block-exact.html", RescuePolicy.ENABLED);
		assertEquals("ブロック400pt = 段200pt × 2段でちょうど1ページ", 1, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(0), 1, 160.0, -200.0, true);
	}

	// ------------------------------------------------------------------
	// Increment 7: floats
	// ------------------------------------------------------------------

	/**
	 * Cut an unsplittable float (an image taller than the page). Each fragment's exclusion area
	 * matches its occupied size, so <b>body text flows around the float on continuation pages too</b>
	 * (before rescue, body text slipped under the float).
	 */
	public void testTallFloatIsSliced() throws Exception {
		final List<String> pages = render("0480-rescue-split/float-tall.html", RescuePolicy.ENABLED);
		assertEquals("浮動体500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
		// Body text flows to the right of the float on continuation pages too (the exclusion area remains active).
		assertTrue("2ページ目の本文が排除域を避けている: " + pages.get(1), pages.get(1).contains("x=100.00 y=0.00 Text["));
	}

	/** Disabling rescue loses the float continuation, and body text flows from the left edge (previous behavior). */
	public void testTallFloatWithoutRescueLosesTheRemainder() throws Exception {
		final List<String> pages = render("0480-rescue-split/float-tall.html", RescuePolicy.DISABLED);
		assertEquals(2, pages.size());
		assertTrue("2ページ目に浮動体の続きはない: " + pages.get(1), pages.get(1).contains("x=0.00 y=0.00 Text["));
	}

	/** An exactly divisible float produces no extra page. */
	public void testFloatExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/float-exact.html", RescuePolicy.ENABLED);
		assertEquals("浮動体400pt / ページ200pt = ちょうど2断片(3ページ目は作らない)", 2, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
	}

	// ------------------------------------------------------------------
	// Out of scope
	// ------------------------------------------------------------------

	/**
	 * Do not rescue absolutely positioned images (agreed behavior: preserve intentional overflow
	 * such as watermarks and bleed). Identical output with ENABLED/DISABLED fixes the requirement
	 * that the wiring leaves this path untouched.
	 */
	public void testAbsoluteImageIsNotRescued() throws Exception {
		final List<String> enabled = render("3050-IMG/rescue-absolute.html", RescuePolicy.ENABLED);
		final List<String> disabled = render("3050-IMG/rescue-absolute.html", RescuePolicy.DISABLED);
		assertEquals("従来どおり1ページではみ出す", 1, enabled.size());
		assertEquals(disabled, enabled);
		assertNoBlankPage(enabled);
	}

	/**
	 * The rescue check itself never runs except at a point where progress stops
	 * (no interference with normal paths). Use one golden document as a representative of the existing corpus.
	 */
	public void testNormalDocumentNeverReachesTheRescuePoint() throws Exception {
		RescueStats.reset();
		render("0120-float/auto-width.html", RescuePolicy.ENABLED);
		assertEquals("通常文書では非進行点に到達しない", 0, RescueStats.CANDIDATES.get());
	}

	// ------------------------------------------------------------------
	// Tagged PDF
	// ------------------------------------------------------------------

	/**
	 * Continuation fragments are emitted as {@code /Artifact}, and only the first fragment opens
	 * a single structure element ({@code Figure}) (recommendation §3: prevent duplicate text extraction,
	 * read-aloud content, and structure tags).
	 */
	public void testTaggedPdfOpensOneFigureAndMarksContinuationsAsArtifact() throws Exception {
		final File pdf = new File("local/unittest/rescue/tagged.pdf");
		pdf.getParentFile().mkdirs();
		try (RescuePolicy.Scope scope = RescuePolicy.ENABLED.scoped();
				OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				session.property("output.pdf.version", "1.7UA-1");
				session.property("output.pdf.tagged.lang", "ja");
				// Leave content streams uncompressed so marked content can be inspected directly.
				session.property("output.pdf.compression", "none");
				CTISessionHelper.transcodeFile(session, new File("files/unittest/3050-IMG/rescue-tall.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		}
		final String bytes = new String(Files.readAllBytes(pdf.toPath()), StandardCharsets.ISO_8859_1);
		assertEquals("画像の構造要素(Figure)は1個だけ", 1, count(bytes, "/S /Figure"));
		assertTrue("継続断片はartifactとして出力される", bytes.contains("/Artifact"));
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	/** Each page contains drawing (no effectively blank page is produced). */
	private static void assertNoBlankPage(final List<String> pages) {
		for (int i = 0; i < pages.size(); ++i) {
			final String page = pages.get(i);
			assertTrue("ページ" + (i + 1) + "の表示リストが空です(意図しない白紙):\n" + page, page.contains("  x="));
		}
	}

	/**
	 * Fix the coordinates and artifact flag of the drawing instruction at {@code index} on the page.
	 */
	private static void assertDrawableAt(final String page, final int index, final double x, final double y,
			final boolean artifact) {
		final List<String> drawables = new ArrayList<>();
		for (final String line : page.split("\n")) {
			if (line.startsWith("  x=")) {
				drawables.add(line);
			}
		}
		assertTrue("描画命令が足りません(index=" + index + "):\n" + page, index < drawables.size());
		final String line = drawables.get(index);
		final String expected = String.format(java.util.Locale.ROOT, "  x=%.2f y=%.2f %s", x, y,
				artifact ? "artifact " : "");
		assertTrue("座標/artifact印が一致しません: expected prefix=[" + expected + "] actual=[" + line + "]",
				line.startsWith(expected));
	}

	private static int count(final String haystack, final String needle) {
		int n = 0;
		for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
			++n;
		}
		return n;
	}

	/**
	 * Convert the document and return a display-list dump for each page.
	 */
	private static List<String> render(final String doc, final RescuePolicy policy) throws Exception {
		final String name = doc.replace('/', '_').replace(".html", "") + "-" + policy;
		final File outDir = new File("local/unittest/rescue/" + name);
		outDir.mkdirs();
		final File[] old = outDir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try (RescuePolicy.Scope scope = policy.scoped()) {
			final File pdf = new File("local/unittest/rescue/" + name + ".pdf");
			pdf.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, new File("files/unittest/" + doc), "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}
		final File[] files = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + doc, files);
		Arrays.sort(files);
		final List<String> pages = new ArrayList<>();
		for (final File f : files) {
			pages.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
