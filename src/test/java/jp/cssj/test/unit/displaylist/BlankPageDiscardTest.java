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
 * Verifies that <b>pages that draw nothing are not output</b>, while preserving author-requested
 * blank pages (added on 2026-07-28, css-break-3 §4.4).
 *
 * <p>
 * While {@link TrailingBlankPageTest} verifies the <b>layout-side</b> measure (avoid splits that would
 * create blank pages in the first place), this test verifies the <b>output-side</b> rule:
 * once page layout is complete, discard a page that draws nothing on paper before creating its
 * PDF page ({@code StyleBuilder.drawPage}).
 * </p>
 *
 * <p>
 * <b>There are four boundaries, and all are checked.</b>
 * </p>
 * <ol>
 * <li><b>Trailing automatic blank pages disappear</b>: content really extends beyond the paper,
 * so a page break is correctly requested, but splitting moves nothing (sweep seed 18717).</li>
 * <li><b>Automatic blank pages in the middle of documents also disappear</b>: blank pages should
 * not exist regardless of position (sweep seed 17726 modified to remove images).</li>
 * <li><b>Blank pages from forced page breaks remain</b>: requesting a trailing page with
 * {@code page-break-after:always} is valid, and this rule must not cross that boundary.</li>
 * <li><b>Discarded pages do not consume a side (recto/verso)</b>: otherwise all subsequent sides flip,
 * silently breaking duplex imposition. Use the {@code @page:left} margin box as a side marker
 * to check <b>both</b> (4) style-selection sides and (4b) left/right page-break sides.</li>
 * </ol>
 *
 * <p>
 * <b>Documents are assembled here</b>: external files risk changing the result through relative
 * image references (lessons §6.9h). That is why these documents have been modified to use no images.
 * </p>
 *
 * <p>
 * <b>Checks token preservation as well as page counts.</b> Any number of blank pages can be
 * eliminated by discarding content, which would not detect regressions.
 * </p>
 */
public class BlankPageDiscardTest extends TestCase {
	/** Timeout. Measured runtime is under one second per case. */
	private static final long WATCHDOG_MS = 60_000L;

	public BlankPageDiscardTest(String name) {
		super(name);
	}

	/**
	 * Boundary 1: an automatic page break creates a trailing blank page (sweep seed 18717).
	 * Before the fix, there were two pages and the second was blank.
	 */
	private static final String AUTO_TRAILING = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="200pt"?>
			<?jp.cssj.property name="output.page-height" value="200pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{margin:0;font:normal 12pt/1.2 serif;writing-mode:horizontal-tb}
			p,div,td{margin:0;padding:0}
			</style></head><body>
			<ul style="list-style-position:outside;list-style-type:decimal">
			<li>T0</li>
			<li>T1</li>
			<li>T2</li>
			<li>T3</li>
			</ul>
			<p>T4 T5 T6 </p>
			<div style="float:right;width:43pt">
			<p><ruby>T7<rt>T8</rt></ruby></p>
			<div style="page-break-inside:avoid;margin:1pt">
			<p>T9 T10 T11 T12 </p>
			</div>
			<div style="column-count:3;column-gap:11pt">
			<div style="clear:both"><span style="font-size:14pt">T13</span></div>
			<p><span style="display:inline-block;width:128pt;height:72pt">T14</span></p>
			<div style="margin:3pt;padding:2pt;border:0pt solid black">
			<p>T15</p>
			<p>T16</p>
			</div>
			</div>
			</div>
			<p><ruby>T17<rt>T18</rt></ruby></p>
			</body></html>
			""";

	/**
	 * Boundary 2: an automatic page break creates a blank page in the middle
	 * (sweep seed 17726 with images replaced by same-sized boxes).
	 * Before the fix, there were five pages and the second was blank.
	 */
	private static final String AUTO_MID_DOCUMENT = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:5pt}
			body{margin:0;font:normal 9pt/1.2 serif;writing-mode:vertical-rl}
			p,div,td{margin:0;padding:0}
			</style></head><body>
			<div style="page-break-inside:avoid;margin:3pt">
			<div style="float:left;width:81pt">
			<p><ruby>T0<rt>T1</rt></ruby></p>
			</div>
			<div style="float:right;width:69pt">
			<ol style="list-style-position:outside;list-style-type:disc">
			<li>T2</li>
			<li>T3</li>
			<li>T4</li>
			<li>T5</li>
			</ol>
			<p>T6 T7 T8 T9 </p>
			<p><ruby>T10<rt>T11</rt></ruby><ruby>T12<rt>T13</rt></ruby></p>
			</div>
			</div>
			<div style="page-break-inside:avoid;margin:2pt">
			<ul style="list-style-position:outside;list-style-type:decimal">
			<li>T14</li>
			<li>T15</li>
			</ul>
			<p><span style="display:block;width:68pt;height:220pt;border:1pt solid black">T19</span></p>
			</div>
			<div style="clear:right"><span style="font-size:25pt">T16</span></div>
			<ul style="list-style-position:outside;list-style-type:disc">
			<li>T17</li>
			<li>T18</li>
			</ul>
			</body></html>
			""";

	/**
	 * Boundary 3: an explicitly author-requested trailing blank page. It must not be discarded.
	 *
	 * <p>
	 * <b>This engine does not create a trailing page even with {@code page-break-after:always} on the
	 * document's last element</b> (measured on 2026-07-28; unchanged before/after the fix, existing behavior
	 * unrelated to this rule). A trailing page is requested by putting {@code page-break-before:always}
	 * on an empty box. This is the form used by
	 * {@code files/unittest/0120-float/float-break-always.html} and recorded as a blank page in the
	 * characterization values ({@code files/unittest/blank-page-characterization.txt}).
	 * </p>
	 */
	private static final String FORCED_TRAILING = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="200pt"?>
			<?jp.cssj.property name="output.page-height" value="200pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 12pt/1.2 serif}
			p,div{margin:0;padding:0}
			</style></head><body>
			<p>T0</p>
			<div style="page-break-before:always;width:50pt;height:50pt"> </div>
			</body></html>
			""";

	/**
	 * Boundary 3b: an empty table box wider than the remaining width at the end of vertical-writing content.
	 *
	 * <p>
	 * Reduced case of fuzz seed 5141. Before the fix, the empty table returned the conservative default
	 * from {@code IBox.paintsAnything()}, leaving page 2 blank.
	 * Discard the table only when it has no content, background, or borders; preserve preceding body text.
	 * </p>
	 */
	private static final String EMPTY_TABLE_TRAILING = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="595pt"?>
			<?jp.cssj.property name="output.page-height" value="842pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{font:normal 11pt/1.2 serif;writing-mode:vertical-rl}
			</style></head><body>
			<div style="width:502pt">T0</div>
			<div style="display:table;width:74pt"><div></div></div>
			</body></html>
			""";

	/**
	 * Boundary 4: the same document as boundary 2, with only <b>side markers</b> added.
	 *
	 * <p>
	 * This document uses vertical writing (vertical-rl) and right binding (page 1 is verso).
	 * On 2026-09-04, standard writing-mode was separated from direction, making vertical-lr left-bound,
	 * so the old fixture's vertical-lr was changed to vertical-rl.
	 * The {@code @page:left} margin box prints "VERSO" only on verso pages, directly revealing
	 * <b>which pages are verso</b> in the display list. Margin boxes do not change the type area
	 * (content area), so boundary 2's layout is unchanged, including the location of the blank page.
	 * </p>
	 *
	 * <p>
	 * The marker goes on the {@code :left} side because the discarded blank page is {@code :right}.
	 * A page with a margin-box declaration is considered to have something to print and is not discarded.
	 * Putting it on {@code :right} would prevent the very discard this test needs to inspect.
	 * </p>
	 */
	private static final String MID_DOCUMENT_WITH_SIDE_MARK = AUTO_MID_DOCUMENT.replace("@page{margin:5pt}",
			"@page{margin:5pt}\n@page:left{@top-center{content:\"VERSO\"}}");

	/**
	 * Boundary 4b: {@code page-break-before:left} (requesting verso) at the end of boundary 4's document.
	 *
	 * <p>
	 * Page sides are tracked in <b>two places</b>: the side in {@code PassContext}
	 * (style selection for {@code @page:left/:right}) and {@code RootBuilder}'s
	 * {@code pageSide} (left/right page-break decisions).
	 * Boundary 4 checks the former; this checks the latter. If a discarded page advances
	 * {@code pageSide}, a break requesting verso lands on a <b>recto</b> page.
	 * </p>
	 */
	private static final String MID_DOCUMENT_FORCED_VERSO = MID_DOCUMENT_WITH_SIDE_MARK.replace("</body></html>",
			"<div style=\"page-break-before:left\"><p>T20</p></div>\n</body></html>");

	public void testAutomaticTrailingBlankPageIsNotEmitted() throws Exception {
		final Pages pages = convert("auto-trailing", AUTO_TRAILING);
		pages.assertNoBlank();
		pages.assertTokens(19);
		assertEquals("auto-trailing: ページ数", 1, pages.count());
	}

	public void testAutomaticMidDocumentBlankPageIsNotEmitted() throws Exception {
		// Before the fix, there were four pages and the second was blank (measured).
		final Pages pages = convert("auto-mid", AUTO_MID_DOCUMENT);
		pages.assertNoBlank();
		pages.assertTokens(20);
		// 2026-08-20: Introducing "ignore avoid when it cannot fit even in a whole fragmentainer"
		// made this document's oversized avoid div split in place,
		// compacting three pages to two (intent unchanged: no blank pages, 20 tokens).
		assertEquals("auto-mid: ページ数", 2, pages.count());
	}

	public void testForcedTrailingBlankPageIsKept() throws Exception {
		final Pages pages = convert("forced-trailing", FORCED_TRAILING);
		pages.assertTokens(1);
		assertEquals("forced-trailing: ページ数", 2, pages.count());
		assertEquals("forced-trailing: 意図した白紙が消えた", List.of(2), pages.blanks());
	}

	public void testTrailingEmptyTableDoesNotCreateBlankPage() throws Exception {
		final Pages pages = convert("empty-table-trailing", EMPTY_TABLE_TRAILING);
		pages.assertNoBlank();
		pages.assertTokens(1);
		assertEquals("empty-table-trailing: ページ数", 1, pages.count());
	}

	/**
	 * Boundary 4: <b>discarded pages do not consume a side (recto/verso).</b>
	 *
	 * <p>
	 * The default is duplex printing ({@code output.print-mode=double-side}), so output page sides
	 * must <b>alternate for every page</b>. If a discarded page consumes a side, all subsequent sides
	 * flip and duplex imposition <b>silently</b> breaks: nobody notices until printing finishes.
	 * </p>
	 *
	 * <p>
	 * Measured on 2026-07-28: removing the {@code setPageSide} line from
	 * {@code StyleBuilder.discardPage()} prints "VERSO" on <b>pages 1 and 2</b>
	 * (two consecutive verso pages). The correct pages are <b>1 and 3</b>.
	 * </p>
	 */
	public void testDiscardedPageDoesNotConsumeAPageSide() throws Exception {
		final Pages pages = convert("mid-side-mark", MID_DOCUMENT_WITH_SIDE_MARK);
		pages.assertNoBlank();
		pages.assertTokens(20);
		// 2026-08-20: Splitting non-fitting avoid in place reduced three pages to two (see auto-mid).
		assertEquals("mid-side-mark: ページ数", 2, pages.count());
		final List<Integer> verso = pages.pagesContaining("VERSO");
		assertEquals("mid-side-mark: 出力されたページの面が交互になっていない"
				+ " ——落としたページが面を消費している", List.of(1), verso);
	}

	/**
	 * Boundary 4b: discarded pages also do not advance <b>side accounting for left/right page breaks</b>.
	 *
	 * <p>
	 * Measured on 2026-07-28: removing {@code emitted &&} from {@code RootBuilder.pageBreak}
	 * makes content with {@code page-break-before:left} land on <b>page 4</b>,
	 * a recto page without "VERSO" (four pages total).
	 * It should land on page 5 (verso), with page 4 blank for collation.
	 * </p>
	 */
	public void testForcedVersoBreakStillLandsOnAVerso() throws Exception {
		final Pages pages = convert("mid-forced-verso", MID_DOCUMENT_FORCED_VERSO);
		pages.assertTokens(21);
		// 2026-08-20: Splitting non-fitting avoid in place compacted the body to two pages;
		// the next left side (page 3) happens to follow immediately, so the collation blank
		// is no longer needed (see auto-mid). The VERSO landing assertion below
		// continues to verify correct side accounting.
		assertEquals("mid-forced-verso: ページ数", 3, pages.count());
		assertEquals("mid-forced-verso: 不要な白紙が入った", List.of(), pages.blanks());
		final int landed = pages.pagesContaining("T20").get(0);
		assertTrue("mid-forced-verso: page-break-before:left が recto のページ(" + landed + ")へ着いた"
				+ " ——落としたページが面を消費している", pages.pagesContaining("VERSO").contains(landed));
	}

	/** Page display lists for one document. */
	private record Pages(String name, List<String> dumps) {
		int count() {
			return this.dumps.size();
		}

		/** Page numbers (1-based) with empty display lists (zero drawing commands). */
		List<Integer> blanks() {
			final List<Integer> blanks = new ArrayList<>();
			for (int i = 0; i < this.dumps.size(); ++i) {
				boolean drew = false;
				for (final String line : this.dumps.get(i).split("\n")) {
					final String t = line.trim();
					if (!t.isEmpty() && !t.startsWith("drawer")) {
						drew = true;
						break;
					}
				}
				if (!drew) {
					blanks.add(i + 1);
				}
			}
			return blanks;
		}

		void assertNoBlank() {
			final List<Integer> blanks = this.blanks();
			assertTrue(this.name + ": 白紙ページ " + blanks + " (全" + this.count() + "ページ)", blanks.isEmpty());
		}

		/** All T0..T(n-1) appear on some page (detects regressions that eliminate blank pages by dropping content). */
		void assertTokens(final int tokenCount) {
			final String all = String.join("", this.dumps);
			final List<String> lost = new ArrayList<>();
			for (int i = 0; i < tokenCount; ++i) {
				if (!containsToken(all, "T" + i)) {
					lost.add("T" + i);
				}
			}
			assertTrue(this.name + ": 内容が失われた " + lost, lost.isEmpty());
		}

		/** Returns all page numbers (1-based) containing the string. */
		List<Integer> pagesContaining(final String text) {
			final List<Integer> found = new ArrayList<>();
			for (int i = 0; i < this.dumps.size(); ++i) {
				if (this.dumps.get(i).contains(text)) {
					found.add(i + 1);
				}
			}
			return found;
		}

		/** Checks following digits too, so {@code T1} does not match {@code T19}. */
		private static boolean containsToken(final String text, final String token) {
			for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + 1)) {
				final int end = at + token.length();
				if (end >= text.length() || !Character.isDigit(text.charAt(end))) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Converts a document and returns per-page display lists.
	 *
	 * <p>
	 * Conversion runs on a <b>separate thread</b> with a timeout. A regression that prevents page-break
	 * progress would hang the test itself, so it must be exposed as a failure.
	 * </p>
	 */
	private static Pages convert(final String name, final String html) throws Exception {
		final File dir = new File("local/blank-page-discard/" + name);
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
		}, "blank-page-discard-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}

		final File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", files);
		assertTrue(name + ": ページが1枚も出ていない", files.length > 0);
		java.util.Arrays.sort(files);
		final List<String> dumps = new ArrayList<>(files.length);
		for (final File f : files) {
			dumps.add(java.nio.file.Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return new Pages(name, dumps);
	}
}
