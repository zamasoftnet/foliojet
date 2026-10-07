package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * Verify that <b>column-direction flex paginates by lines instead of visual rescue splitting
 * (band clipping)</b> (introduced 2026-08-18).
 *
 * <p>
 * Previously, column flex ineligible under {@code FlexBuilderLifecycle.eligible}
 * (page axis not an absolute length; typically an app shell with {@code min-height:100vh})
 * fell back to F0 single-column normal flow but still asserted the {@code PageAtomicBox}
 * atomic contract. As a result, all content became one atomic unit and fell into rescue splitting,
 * <b>slicing lines vertically at band boundaries</b> (87 of 235 real corpus documents, or 37%,
 * were flagged for "translation of a box larger than the paper"; bbc-japan and others).
 * </p>
 *
 * <p>
 * Two fixes: (1) Drop the atomic contract on F0 fallback
 * ({@code FlexBox.isPageAtomicNow}; the same design decision as {@code GridBox}'s trackLayout).
 * Since the content is normal flow, it paginates by lines as an ordinary block.
 * (2) For single-column placement of eligible columns (absolute-length main axis) and
 * the F4c fallback path, <b>synthesize page-axis bookkeeping with one item per line</b>
 * and apply the same {@code FlexBox.split} (line splitting mechanism) as in the row direction.
 * </p>
 */
public class FlexColumnPaginationTest extends TestCase {
	/** Timeout. Measured execution is under 5 seconds per case. */
	private static final long WATCHDOG_MS = 120_000L;

	/**
	 * App-shell column flex (min-height:100vh+space-between) paginates at line boundaries
	 * without band slicing (drawing translated to negative coordinates).
	 */
	public void testAppShellColumnFlexBreaksAtLineBoundaries() throws Exception {
		this.assertCleanPagination("app-shell",
				"min-height:100vh;display:flex;flex-direction:column;justify-content:space-between");
	}

	/** Plain column flex (no specified height) also paginates at line boundaries. */
	public void testPlainColumnFlexBreaksAtLineBoundaries() throws Exception {
		this.assertCleanPagination("plain", "display:flex;flex-direction:column");
	}

	private void assertCleanPagination(final String name, final String wrapperStyle) throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>@page{margin:10pt} body{font:normal 9pt/1.2 serif;margin:0}</style>
				</head><body>
				""");
		html.append("<div style=\"").append(wrapperStyle).append("\">\n");
		html.append("<header>HEAD</header>\n<main>\n");
		for (int i = 0; i < 120; ++i) {
			html.append("<p>Paragraph ").append(i).append(" text that wraps a bit more here.</p>\n");
		}
		html.append("</main>\n<footer>FOOT</footer>\n</div></body></html>\n");

		final File dir = prepareDir("flex-column-pagination/" + name);
		final int pages = convert(name, dir, html.toString());
		assertTrue(name + ": 最後まで組まれていない(ページ数=" + pages + ")", pages >= 5);
		// Visual rescue splitting draws page 2 onward by translating the entire content to negative
		// coordinates and clipping it to a band (all display-list entries carry artifact markers).
		// With line splitting, page 2 contains only that page's content at positive coordinates.
		for (int p = 2; p <= pages; ++p) {
			final File dump = new File(dir, String.format("page-%04d.txt", p));
			final List<String> lines = Files.readAllLines(dump.toPath(), StandardCharsets.UTF_8);
			for (final String line : lines) {
				assertFalse(name + ": p" + p + "が救済分割の帯描画になっている: " + line,
						line.contains("artifact") || line.contains("y=-"));
			}
		}
		// No content loss: footer on the final page.
		final File last = new File(dir, String.format("page-%04d.txt", pages));
		assertTrue(name + ": 最終ページにFOOTが無い",
				Files.readString(last.toPath(), StandardCharsets.UTF_8).contains("FOOT"));
	}

	/**
	 * Eligible column flex (absolute-length main axis) with defined item heights splits via
	 * {@code FlexBox.split} using synthesized one-item-per-line bookkeeping
	 * (200 pt paper, 180 pt content area, three 150 pt items = 450 pt). The boundary item (B)
	 * keeps its label on the retained side (p1), and carries its remainder (120 pt of empty space)
	 * to the next page, where C starts at 120 pt. No visual rescue splitting (translated band clipping).
	 */
	public void testDefiniteColumnFlexSplitsBetweenItems() throws Exception {
		final String html = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>@page{margin:10pt} body{font:normal 9pt/1.2 serif;margin:0}
				.wrap{height:450pt;display:flex;flex-direction:column}
				.wrap div{height:150pt;flex:none}</style>
				</head><body><div class="wrap">
				<div>ITEM-A</div><div>ITEM-B</div><div>ITEM-C</div>
				</div></body></html>
				""";
		final File dir = prepareDir("flex-column-pagination/definite");
		final int pages = convert("definite", dir, html);
		assertTrue("分割されていない(ページ数=" + pages + ")", pages >= 2);
		final String p1 = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final String p2 = Files.readString(new File(dir, "page-0002.txt").toPath(), StandardCharsets.UTF_8);
		assertTrue("definite: p1にITEM-A/Bが無い", p1.contains("ITEM-A") && p1.contains("ITEM-B"));
		assertFalse("definite: p2が救済分割の帯描画になっている", p2.contains("artifact") || p2.contains("y=-"));
		assertTrue("definite: p2にITEM-Cが無い", p2.contains("ITEM-C"));
	}

	private static File prepareDir(final String name) {
		final File dir = new File("local/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		return dir;
	}

	private static int convert(final String name, final File dir, final String html) throws Exception {
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
		}, name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が失敗", failure[0]);
		}
		final File[] dumps = dir.listFiles((d, f) -> f.startsWith("page-") && f.endsWith(".txt"));
		return dumps == null ? 0 : dumps.length;
	}
}
