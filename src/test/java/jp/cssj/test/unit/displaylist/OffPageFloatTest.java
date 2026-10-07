package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify that <b>contents of floats with explicit page-axis sizes are not placed off the paper</b>
 * (introduced 2026-07-28).
 *
 * <p>
 * This defect remained in seeds 100,000–200,000 under {@code RandomDocumentFuzzTest}'s
 * <b>invariant 6</b> (unexplained off-paper placement)
 * (minimal case: {@code local/shrink/strict-149858-min.html}).
 * </p>
 *
 * <h2>Mechanism</h2>
 *
 * <p>
 * When a float <b>specifies its page-axis size</b> ({@code width} in vertical writing,
 * {@code height} in horizontal writing), {@code overflow} defaults to {@code visible}, drawing contents
 * exceeding that size outside the box. However, {@code BreakableBuilder.classifyFloatPlacement()}
 * checked only <b>box geometry</b> ({@code IBox.getPageExtent()}), considered it to fit,
 * and reserved no cut. <b>No page break ever occurred</b>, and content continued
 * straight off the paper along the page axis.
 * </p>
 *
 * <p>
 * Cutting made the same geometric assumption: {@code FloatMeasurement.of()} measured with
 * {@code getPageExtent()}, satisfying {@code FloatSplitPlan}'s decision-table case 1
 * (everything before the cut line), so <b>no split occurred</b>. Fixing only reservations
 * triggers a page break but leaves the float unsplit: contents remain off-paper and the output
 * layer discards the blank next page. <b>Both</b> must use actual measurements.
 * </p>
 *
 * <p>
 * Normal flow already has this correction: {@code FlowContainer.computeFlowBottoms()}'s
 * {@code Math.max(inner size, getContentSize())}. The defect was that <b>only floats lacked it</b>.
 * </p>
 *
 * <h2>Independent of writing direction</h2>
 *
 * <p>
 * The original used vertical writing ({@code writing-mode:vertical-rl} with {@code width:0pt}),
 * but <b>its horizontal mirror ({@code height:0pt}) reproduces the same shape without even
 * a 1 pt difference</b>. Before the fix, both laid out all content on one page
 * (vertical {@code x=-145.28}/paper width 120 pt; horizontal {@code y=254.30}/paper height 120 pt).
 * This is not vertical-specific, so cover both.
 * </p>
 *
 * <h2>Criteria</h2>
 *
 * <p>
 * Use <b>the same criteria as fuzz invariant 6</b>: count only overflow beyond one entire paper
 * dimension and beyond twice the largest explicit document size (same reason as {@code OffPageColumnTest}).
 * This document specifies {@code 0pt}, so the allowance is zero and all overflow beyond one paper
 * dimension fails.
 * </p>
 *
 * <p>
 * Also require <b>at least two pages</b>. This directly states the fix's core, "paginate if it does
 * not fit", detecting breakage closer to the mechanism than overflow measurements.
 * <b>Dropping content also eliminates off-paper placement</b>, so check token survival too.
 * </p>
 */
public class OffPageFloatTest extends TestCase {
	/** Timeout. Measured execution is under one second per case. */
	private static final long WATCHDOG_MS = 60_000L;

	/** Display-list drawing positions. Same format as {@code RandomDocumentFuzzTest}. */
	private static final Pattern POS_IN_DUMP = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+)");

	/** Tokens in this document (listed explicitly because they are not consecutive). */
	private static final String[] TOKENS = { "T4", "T5", "T6", "T8", "T9", "T10", "T14", "T18", "T22", "T25", "T33" };

	public OffPageFloatTest(String name) {
		super(name);
	}

	/**
	 * Vertical writing. {@code width} is the page axis, so {@code width:0pt} means
	 * <b>zero page-axis size</b>. Before the fix, all content appeared on one page;
	 * worst position {@code x=-145.28} (120 pt paper width), invariant 6 excess 25 pt.
	 */
	private static final String VERTICAL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:vertical-rl}
			</style></head><body>
			<ul style="list-style-position:inside">
			<li></li>
			<li></li>
			<li></li>
			<li></li>
			</ul>
			<div style="float:right;width:0pt">
			T4
			<p>T5</p>
			T6
			<table>
			T8
			<tr><td>T9</td></tr>
			<td>T10</td>
			</table>
			<table>
			T14
			<tr><td>T18</td></tr>
			<td>T22</td>
			<tr><td>T25</td></tr>
			</table>
			T33
			</div>
			</body></html>
			""";

	public void testVerticalFloatWithSpecifiedPageExtentStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("vertical", VERTICAL, 120, 400);
	}

	/**
	 * Horizontal mirror. {@code height} is the page axis, so {@code height:0pt}
	 * has the same meaning. Before the fix, all content appeared on one page;
	 * worst position {@code y=254.30} (120 pt paper height), excess 14 pt.
	 */
	private static final String HORIZONTAL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="400pt"?>
			<?jp.cssj.property name="output.page-height" value="120pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:horizontal-tb}
			</style></head><body>
			<ul style="list-style-position:inside">
			<li></li>
			<li></li>
			<li></li>
			<li></li>
			</ul>
			<div style="float:right;height:0pt">
			T4
			<p>T5</p>
			T6
			<table>
			T8
			<tr><td>T9</td></tr>
			<td>T10</td>
			</table>
			<table>
			T14
			<tr><td>T18</td></tr>
			<td>T22</td>
			<tr><td>T25</td></tr>
			</table>
			T33
			</div>
			</body></html>
			""";

	public void testHorizontalFloatWithSpecifiedPageExtentStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("horizontal", HORIZONTAL, 400, 120);
	}

	/**
	 * A <b>float at the page start</b> (added 2026-10-02, sweep seed 11065158).
	 *
	 * <p>
	 * The two cases above have {@code <ul>} before the float, so request cuts with {@code FLAGS_SPLIT}.
	 * At page start, {@code FloatSplitPlan.classify} passed only {@code FLAGS_FIRST};
	 * {@code FlowCutter.preDecide} retained the float on the previous page because the cut line
	 * lay beyond its geometric size (here, beyond 0 pt). Ignoring overflowing contents left the float
	 * unsplit with all content on one page (before the fix: vertical {@code x=-73.84}/paper width 120 pt,
	 * horizontal {@code y=180.64}/paper height 120 pt). Placement classification was already fixed
	 * (2026-07-28), so cut reservation was reached. This amount does not trigger the zero-allowance
	 * off-paper criterion; the page-count assertion fails instead.
	 * </p>
	 */
	private static final String VERTICAL_AT_PAGE_HEAD = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:vertical-rl}
			</style></head><body>
			<div style="float:right;width:0pt">
			T4
			<p>T5</p>
			T6
			<table>
			T8
			<tr><td>T9</td></tr>
			<td>T10</td>
			</table>
			<table>
			T14
			<tr><td>T18</td></tr>
			<td>T22</td>
			<tr><td>T25</td></tr>
			</table>
			T33
			</div>
			</body></html>
			""";

	public void testVerticalFloatWithSpecifiedPageExtentAtPageHeadStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("vertical-head", VERTICAL_AT_PAGE_HEAD, 120, 400);
	}

	/** Horizontal mirror of {@link #VERTICAL_AT_PAGE_HEAD}: replace {@code width} with {@code height}. */
	private static final String HORIZONTAL_AT_PAGE_HEAD = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="400pt"?>
			<?jp.cssj.property name="output.page-height" value="120pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:horizontal-tb}
			</style></head><body>
			<div style="float:right;height:0pt">
			T4
			<p>T5</p>
			T6
			<table>
			T8
			<tr><td>T9</td></tr>
			<td>T10</td>
			</table>
			<table>
			T14
			<tr><td>T18</td></tr>
			<td>T22</td>
			<tr><td>T25</td></tr>
			</table>
			T33
			</div>
			</body></html>
			""";

	public void testHorizontalFloatWithSpecifiedPageExtentAtPageHeadStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("horizontal-head", HORIZONTAL_AT_PAGE_HEAD, 400, 120);
	}

	/**
	 * A float <b>with a border</b> (added 2026-10-02).
	 *
	 * <p>
	 * {@code AbstractContainerBox.paintedPageExtent} stopped at the box extent when a visible border
	 * existed. Placement ({@code FloatMeasurement.occupiedPageExtent}) thus ignored overflowing
	 * contents and reserved no cut <b>even away from page start</b> (one page before the fix).
	 * The page-start variant exercises both placement and cutting fixes.
	 * </p>
	 */
	public void testVerticalBorderedFloatWithSpecifiedPageExtentStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("vertical-border", document(120, 400, "vertical-rl", LEADING_LIST,
				"float:right;width:0pt;border:1pt solid black", "", ""), 120, 400);
	}

	public void testHorizontalBorderedFloatWithSpecifiedPageExtentAtPageHeadStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("horizontal-border-head", document(400, 120, "horizontal-tb", "",
				"float:right;height:0pt;border:1pt solid black", "", ""), 400, 120);
	}

	/**
	 * A float contains a <b>normal block with an explicit page-axis size</b>, whose contents overflow
	 * (added 2026-10-02). A cut decision considering only direct-child geometry ({@code getContentSize()})
	 * ignores the overflow and retains it on the previous page.
	 */
	public void testVerticalFloatWithNestedSpecifiedPageExtentAtPageHeadStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("vertical-nested-head", document(120, 400, "vertical-rl", "",
				"float:right;width:0pt", "<div style=\"width:0pt\">", "</div>"), 120, 400);
	}

	/** Content preceding the floats in the two cases above, occupying only page-axis space. */
	private static final String LEADING_LIST = """
			<ul style="list-style-position:inside">
			<li></li>
			<li></li>
			<li></li>
			<li></li>
			</ul>
			""";

	/**
	 * Build a document with a float containing the same contents as {@link #VERTICAL}.
	 *
	 * @param pageWidth   paper width (pt)
	 * @param pageHeight  paper height (pt)
	 * @param writingMode body's writing direction
	 * @param before      content before the float
	 * @param floatStyle  float style
	 * @param innerOpen   opening tag wrapping the float's contents
	 * @param innerClose  corresponding closing tag
	 */
	private static String document(final int pageWidth, final int pageHeight, final String writingMode,
			final String before, final String floatStyle, final String innerOpen, final String innerClose) {
		return """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="%dpt"?>
				<?jp.cssj.property name="output.page-height" value="%dpt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:0pt}
				body{font:normal 11pt/1.2 serif;writing-mode:%s}
				</style></head><body>
				%s<div style="%s">%s
				T4
				<p>T5</p>
				T6
				<table>
				T8
				<tr><td>T9</td></tr>
				<td>T10</td>
				</table>
				<table>
				T14
				<tr><td>T18</td></tr>
				<td>T22</td>
				<tr><td>T25</td></tr>
				</table>
				T33
				%s</div>
				</body></html>
				""".formatted(pageWidth, pageHeight, writingMode, before, floatStyle, innerOpen, innerClose);
	}

	/**
	 * Convert and check (1) at least two pages, (2) no unexplained off-paper placement,
	 * and (3) all tokens appear on some page. This document's maximum explicit size
	 * is {@code 0pt}, so the allowance is zero.
	 *
	 * @param name       working directory name
	 * @param html       document
	 * @param pageWidth  paper width (pt)
	 * @param pageHeight paper height (pt)
	 */
	private static void assertNoUnexplainedOffPage(final String name, final String html, final double pageWidth,
			final double pageHeight) throws Exception {
		final File[] pages = convert(name, html);

		// Direct statement of the fix's core: a float that does not fit triggers pagination.
		assertTrue(name + ": 浮動体が切断されず1ページに収まってしまっている(ページ数=" + pages.length + ")", pages.length >= 2);

		// Count only overflow beyond one entire paper dimension (same criterion as invariant 6).
		// This document's maximum explicit size is 0 pt, so the allowance is zero.
		double worst = 0;
		String worstAt = null;
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			final String dump = java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8);
			all.append(dump);
			final Matcher m = POS_IN_DUMP.matcher(dump);
			while (m.find()) {
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				final double over = Math.max(Math.max(-x - pageWidth, x - 2 * pageWidth),
						Math.max(-y - pageHeight, y - 2 * pageHeight));
				if (over > worst) {
					worst = over;
					worstAt = "x=" + x + " y=" + y + " " + page.getName();
				}
			}
		}
		assertTrue(name + ": 紙面外への配置 " + Math.round(worst) + "pt (紙面" + Math.round(pageWidth) + "x"
				+ Math.round(pageHeight) + "pt, 最大明示サイズ0pt, " + worstAt + ", 全" + pages.length + "ページ)", worst <= 0);

		// Dropping content also eliminates off-paper placement. Make that visible as a regression.
		final List<String> lost = new ArrayList<>();
		for (final String t : TOKENS) {
			if (all.indexOf(t) < 0) {
				lost.add(t);
			}
		}
		assertTrue(name + ": 内容が失われた " + lost, lost.isEmpty());
	}

	/**
	 * Floats clipped with {@code clip-path} <b>do not split</b>, even if contents overflow
	 * (added 2026-10-02).
	 *
	 * <p>
	 * Overflowing contents are invisible, so off-paper placement is harmless. Splitting reclips each
	 * fragment to its own reference box (extended to the page end), <b>revealing hidden content</b>
	 * (before the fix, the borderless variant split mid-page, expanding the clip from 96 pt to 148 pt).
	 * Treat overflow measurement like {@code overflow:hidden} through
	 * {@code BlockParams.clipsOverflowPaint()}.
	 * </p>
	 */
	public void testClipPathFloatIsNotSplitByHiddenOverflow() throws Exception {
		final String[] styles = { "float:right;height:96pt;clip-path:inset(0)",
				"float:right;height:96pt;border:1pt solid black;clip-path:inset(0)" };
		for (int i = 0; i < styles.length; ++i) {
			final StringBuilder html = new StringBuilder("""
					<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
					<?jp.cssj.property name="output.page-width" value="400pt"?>
					<?jp.cssj.property name="output.page-height" value="160pt"?>
					<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
					<style>
					@page{margin:0pt}
					body{margin:0;font:normal 10pt/1.2 serif}
					p{margin:0}
					</style></head><body>
					""");
			html.append(i == 0 ? "<p>X0</p>" : "").append("<div style=\"").append(styles[i]).append("\">");
			for (int j = 1; j <= 25; ++j) {
				html.append("<p>P").append(j).append("</p>");
			}
			html.append("</div></body></html>");
			final String name = "clip-path-" + i;
			final File[] pages = convert(name, html.toString());
			assertEquals(name + ": 切り抜く浮動体が分かれた", 1, pages.length);
			final String dump = java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
			final Matcher m = CLIP_IN_DUMP.matcher(dump);
			int clips = 0;
			while (m.find()) {
				++clips;
				assertTrue(name + ": 切り抜きが箱より大きい " + m.group(), Double.parseDouble(m.group(1)) <= 98.01);
			}
			assertTrue(name + ": 切り抜きが無い", clips > 0);
		}
	}

	/** Height of the display-list clip ({@code clip=[x y w h]}). */
	private static final Pattern CLIP_IN_DUMP = Pattern.compile("clip=\\[[-\\d.]+ [-\\d.]+ [-\\d.]+ ([-\\d.]+)\\]");

	/**
	 * Convert a document and return per-page display lists.
	 *
	 * @param name working directory name
	 * @param html document
	 * @return page display lists (in page order)
	 */
	private static File[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/off-page-float/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}

		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "off-page-float-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}

		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		java.util.Arrays.sort(pages);
		return pages;
	}
}
