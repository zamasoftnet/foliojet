package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * The rows after a grid row broken across pages stay where its remainder ends, page after page (2026-10-09, stage 0 of
 * copperpdf4/docs/design/retained-container-relayout-design.md).
 */
public class GridRemainderGrowthTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	/**
	 * A remainder that keeps coming out taller than the ledger page after page does not accumulate: the row it pushes
	 * down gives the extra to the remainder's row, not to the gap between them (godoc-pkg's main grid ran away to a
	 * livelock). Chrome: 12 pages, B10 alone on page 11, B11 FOOTER END on page 12.
	 */
	public void testRemainderGrowthDoesNotAccumulate() throws Exception {
		final StringBuilder blocks = new StringBuilder();
		for (int n = 0; n < 12; ++n) {
			blocks.append("<div class=\"i\"><div>A1<br>A2<br>A3<br>A4<br>A5<br>A6<br>A7<br>A8</div><div>B").append(n)
					.append("<span class=\"m\"></span></div></div>");
		}
		final List<String> pages = convertPages("remainder-accumulates", """
				<!DOCTYPE html>
				<html><head><meta charset="UTF-8">
				<style>
				@page { size: 300pt 300pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 15pt }
				p { margin: 0 }
				.o { display: grid }
				.i { display: grid; grid-template-columns: 1fr 1fr }
				.m { display: inline-block; width: 50pt; height: 200pt; vertical-align: top }
				</style></head><body><main class="o"><article>%s</article><footer>FOOTER</footer></main><p>END</p>
				</body></html>
				""".formatted(blocks));
		assertEquals("頁数", 12, pages.size());
		for (int i = 0; i < pages.size(); ++i) {
			final Matcher m = TEXT.matcher(pages.get(i));
			while (m.find()) {
				assertTrue((i + 1) + " 頁目の字が紙面の外:\n" + pages.get(i), Double.parseDouble(m.group(2)) < 300);
			}
		}
		final String last = pages.get(11);
		assertTrue("B11 FOOTER END は 12 頁目:\n" + last,
				last.contains("Text[\"B11\"") && last.contains("Text[\"FOOTER\"") && last.contains("Text[\"END\""));
	}

	/**
	 * The trailing margin of the last paragraph kept before each break is not counted in the remainder again: the row
	 * after a grid item of 200 paragraphs ({@code margin-bottom: 4pt}) broken on 25 pages fell 4pt behind a page and
	 * END came 96pt late. Chrome: END at 32pt on the last page (the 60pt bottom margin of the item less the 28pt left
	 * on the page before).
	 */
	public void testTrailingMarginDoesNotAccumulate() throws Exception {
		final StringBuilder paras = new StringBuilder();
		for (int n = 0; n < 200; ++n) {
			paras.append("<p>P").append(n).append(" lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do"
					+ " eiusmod tempor.</p>");
		}
		final List<String> pages = convertPages("trailing-margin", """
				<!DOCTYPE html>
				<html><head><meta charset="UTF-8">
				<style>
				@page { size: 300pt 300pt; margin: 0 }
				body { margin: 0; font: 10pt/15pt serif } p { margin: 0 0 4pt }
				.o { display: grid; grid-template: repeat(2, min-content) / 100%%; grid-template-areas: "article" "footer" }
				article { grid-area: article; margin: 12pt 0 60pt 0 } footer { grid-area: footer }
				</style></head><body><main class="o"><article>%s</article><footer></footer></main><p>END</p>
				</body></html>
				""".formatted(paras));
		final Matcher m = TEXT.matcher(pages.get(pages.size() - 1));
		double end = Double.NaN;
		while (m.find()) {
			if (m.group(3).equals("END")) {
				end = Double.parseDouble(m.group(2));
			}
		}
		assertEquals("END on the last page", 32, end, 0.1);
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = Files.createTempDirectory("grid-remainder-" + name).toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(name + ": no pages", pages);
		Arrays.sort(pages);
		final List<String> result = new ArrayList<>();
		for (final File page : pages) {
			result.add(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return result;
	}
}
