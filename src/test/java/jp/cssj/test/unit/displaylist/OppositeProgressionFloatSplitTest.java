package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

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
 * A float in a region whose block progression is opposite to the paper's ({@code vertical-rl} in {@code vertical-lr})
 * is split on every page it reaches, also when nothing follows it in the document (2026-10-09, fit seed 12555259).
 * The region keeps the float in its own extent and is split as a whole box by the overflow check of the next event;
 * the rest that a page break carried to the next page was laid out again by the resume, and with no event left
 * nothing checked it: it stayed on that page, past the paper (T9 at x=103.6 on a 60pt page). Chrome keeps everything on
 * the paper (it splits the region in the paper's direction, so the order of the pieces differs).
 *
 * <p>
 * The codex review of 2026-10-10: an empty box closing after the region used the mark up before the box that overflows
 * closed (T10 and T11 stayed off the paper); left and right pages of different sizes; a float of 42 parts.
 * </p>
 */
public class OppositeProgressionFloatSplitTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	private static String document(final String body) {
		return document("", body);
	}

	private static String document(final String style, final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 60pt 60pt; margin: 0 }
				body { margin: 0; font: 7pt/1.2 serif; writing-mode: vertical-lr }
				p { margin: 0 }
				%s
				</style></head><body>%s</body></html>
				""".formatted(style, body);
	}

	/** Blocks of 46, 56 and 56pt in the float: one page each, the last ones too. */
	public void testFloatRestOnEveryPage() throws Exception {
		final List<String> pages = convertPages("blocks", document("""
				<div style="writing-mode: vertical-rl"><div style="float: right">
				<div style="width: 46pt">T1</div>T8
				<div style="width: 56pt">T9</div>
				<div style="width: 56pt">T10</div>
				</div></div>
				"""));
		assertEquals("頁数", 3, pages.size());
		assertOnPaper(pages, "T1", "T8", "T9", "T10");
	}

	/** A column flex as the float (the seed's float is a flex): its items are spread over the pages. */
	public void testColumnFlexFloatRestOnEveryPage() throws Exception {
		final StringBuilder items = new StringBuilder();
		for (int i = 1; i <= 5; ++i) {
			items.append("<div style=\"width: 40pt\">F").append(i).append("</div>");
		}
		final List<String> pages = convertPages("column-flex", document(
				"<div style=\"writing-mode: vertical-rl\">"
						+ "<div style=\"float: right; display: flex; flex-direction: column\">" + items + "</div></div>"));
		assertOnPaper(pages, "F1", "F2", "F3", "F4", "F5");
	}

	/** Content after the region kept breaking already: unchanged. */
	public void testFollowedByContent() throws Exception {
		final List<String> pages = convertPages("followed", document("""
				<div style="writing-mode: vertical-rl"><div style="float: right">
				<div style="width: 46pt">T1</div>T8
				<div style="width: 56pt">T9</div>
				<div style="width: 56pt">T10</div>
				</div></div><p>BODY</p>
				"""));
		assertEquals("頁数", 4, pages.size());
		assertOnPaper(pages, "T1", "T8", "T9", "T10", "BODY");
	}

	/** A float of a 46pt block, a line and four parts (.part): it runs over several pages. */
	private static final String SIX_PARTS = """
			<div style="writing-mode: vertical-rl"><div style="float: right">
			<div style="width: 46pt">T1</div>T8
			<div class="part">T9</div><div class="part">T10</div><div class="part">T11</div><div class="part">T12</div>
			</div></div>
			""";

	/** An empty box after the region changes nothing (it used the mark up before the region's parent closed). */
	public void testTrailingEmptyBox() throws Exception {
		final String style = ".part { width: 56pt }";
		final List<String> pages = convertPages("six", document(style, SIX_PARTS));
		final List<String> trailing = convertPages("six-trailing", document(style, SIX_PARTS + "<div></div>"));
		assertOnPaper(pages, "T1", "T8", "T9", "T10", "T11", "T12");
		assertEquals("後ろの空の箱で組みが変わらない", pages, trailing);
	}

	/** Left and right pages of different sizes (100pt and 60pt, either way round). */
	public void testLeftAndRightPageSizes() throws Exception {
		final String[][] sizes = { { "60pt", "100pt", "60pt" }, { "100pt", "60pt", "100pt" } };
		for (int i = 0; i < sizes.length; ++i) {
			final String style = "@page :first { size: " + sizes[i][0] + " 60pt } @page :left { size: " + sizes[i][1]
					+ " 60pt } @page :right { size: " + sizes[i][2] + " 60pt } .part { width: 40pt }";
			assertOnPaper(convertPages("left-right-" + i, document(style, SIX_PARTS)), "T1", "T8", "T9", "T10", "T11",
					"T12");
		}
	}

	/** A float of 42 parts ends, with every part on the paper. */
	public void testManyParts() throws Exception {
		final StringBuilder parts = new StringBuilder();
		final String[] tokens = new String[42];
		tokens[0] = "T1";
		tokens[1] = "T8";
		for (int i = 9; i <= 48; ++i) {
			parts.append("<div class=\"part\">T").append(i).append("</div>");
			tokens[i - 7] = "T" + i;
		}
		assertOnPaper(convertPages("many", document(".part { width: 40pt }",
				"<div style=\"writing-mode: vertical-rl\"><div style=\"float: right\">"
						+ "<div style=\"width: 46pt\">T1</div>T8" + parts + "</div></div>")), tokens);
	}

	/** Every token is drawn once, inside the paper of its page (a vertical line is 8.4pt thick). */
	private static void assertOnPaper(final List<String> pages, final String... tokens) {
		for (final String token : tokens) {
			int found = 0;
			for (int i = 0; i < pages.size(); ++i) {
				final String page = pages.get(i);
				final double width = Double.parseDouble(page.substring(0, page.indexOf('\n')));
				final Matcher m = TEXT.matcher(page);
				while (m.find()) {
					if (m.group(3).equals(token)) {
						++found;
						final double x = Double.parseDouble(m.group(1));
						assertTrue(token + " が紙の外 (x=" + x + ", 紙の幅 " + width + "):\n" + page,
								x >= -0.5 && x + 8.4 <= width + 0.5);
					}
				}
			}
			assertEquals(token + " の数", 1, found);
		}
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/opposite-progression-float/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "opposite-progression-float-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		Arrays.sort(pages);
		final List<String> result = new ArrayList<>();
		// Each page's display list, after a first line holding the width of its paper.
		try (PDDocument pdf = Loader.loadPDF(new File(dir, "out.pdf"))) {
			assertEquals(name + ": 表示リストと PDF の頁数", pdf.getNumberOfPages(), pages.length);
			for (int i = 0; i < pages.length; ++i) {
				result.add(pdf.getPage(i).getMediaBox().getWidth() + "\n"
						+ java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8));
			}
		}
		return result;
	}
}
