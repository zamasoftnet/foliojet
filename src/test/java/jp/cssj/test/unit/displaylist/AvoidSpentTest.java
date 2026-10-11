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
 * Avoids that hold no more at the start of a page (2026-10-11, FLAGS_AVOID_SPENT): a heading kept with a list by
 * {@code break-before: avoid} at the top of a page, the list of {@code break-inside: avoid} not fitting the rest of the
 * page. Every break from the top of the page breaks an avoid; Chrome takes the last of them and breaks inside the list.
 * Copper moved the list and left the heading alone on the page, page after page for nested lists (weasyprint-doc with
 * Furo's print style sheet: "EXTRA INFORMATION" and "Changelog" each on a page of its own). Pages of 180pt, 10pt lines;
 * a 140pt block before the heading on the first page.
 */
public class AvoidSpentTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static String document(final String list) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 200pt 200pt; margin: 10pt }
				body { margin: 0; font: 10pt/10pt serif }
				ul { page-break-inside: avoid; page-break-before: avoid; margin: 0; padding: 0; list-style: none }
				p { margin: 0; page-break-inside: avoid }
				</style></head><body><div style="height: 140pt; background: #ccc"></div>
				<p>CAPTION</p>%s<p>after</p></body></html>
				""".formatted(list);
	}

	private static String items(final int count) {
		final StringBuilder b = new StringBuilder();
		for (int i = 1; i <= count; ++i) {
			b.append("<li>V").append(i).append("</li>");
		}
		return b.toString();
	}

	/** A list longer than a page: the heading and V1-V17 on the second page (Chrome the same). */
	public void testLongList() throws Exception {
		final List<String> pages = convertPages("long", document("<ul>" + items(40) + "</ul>"));
		assertPage(pages, 0, "", "CAPTION V1");
		assertPage(pages, 1, "CAPTION V1 V17", "V18");
		assertAllOnce(pages, 40);
	}

	/** A list in a list, as Sphinx's toctree: the heading, "Changelog" and V1-V16 on the second page (Chrome the same). */
	public void testNestedList() throws Exception {
		final List<String> pages = convertPages("nested",
				document("<ul><li>Changelog<ul>" + items(40) + "</ul></li></ul>"));
		assertPage(pages, 1, "CAPTION Changelog V1 V16", "V17");
		assertAllOnce(pages, 40);
	}

	/** A list that would fit a page of its own breaks the same (Chrome: V1-V17, then V18). */
	public void testListFittingAPage() throws Exception {
		final List<String> pages = convertPages("fitting", document("<ul>" + items(18) + "</ul>"));
		assertPage(pages, 1, "CAPTION V1 V17", "V18");
		assertAllOnce(pages, 18);
	}

	/** A list that fits the next page with the heading moves with it, as before. */
	public void testShortListMovesWithHeading() throws Exception {
		final List<String> pages = convertPages("short", document("<ul>" + items(5) + "</ul>"));
		assertPage(pages, 0, "", "CAPTION V1");
		assertPage(pages, 1, "CAPTION V1 V5 after", "");
	}

	/** The words on the page (index) and the words not on it. */
	private static void assertPage(final List<String> pages, final int index, final String on, final String off) {
		assertTrue("頁数 " + pages.size(), pages.size() > index);
		final String page = pages.get(index);
		for (final String word : on.split(" ")) {
			if (!word.isEmpty()) {
				assertTrue(word + " が " + (index + 1) + " 頁目に無い:\n" + page, page.contains("Text[\"" + word + "\""));
			}
		}
		for (final String word : off.split(" ")) {
			if (!word.isEmpty()) {
				assertFalse(word + " が " + (index + 1) + " 頁目にある:\n" + page, page.contains("Text[\"" + word + "\""));
			}
		}
	}

	private static void assertAllOnce(final List<String> pages, final int count) {
		final String all = String.join("\n", pages);
		for (int i = 1; i <= count; ++i) {
			final String text = "Text[\"V" + i + "\"";
			assertTrue("V" + i + " が無い", all.contains(text));
			assertEquals("V" + i + " の回数", all.indexOf(text), all.lastIndexOf(text));
		}
		for (int i = 0; i < pages.size(); ++i) {
			assertTrue((i + 1) + " 頁目が白紙", i == 0 || pages.get(i).contains("Text["));
		}
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/avoid-spent/" + name);
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
		}, "avoid-spent-" + name, 64L * 1024 * 1024);
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
		for (final File page : pages) {
			result.add(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return result;
	}
}
