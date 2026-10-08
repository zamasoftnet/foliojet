package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * A later line of a wrapping flex at the top of a page moves to the next page when it crosses the page bottom
 * (2026-10-09, flexgal2; smolcss's card grid). Its items took FLAGS_FIRST as if they started the page, and an item
 * whose first line (a 230pt inline block) did not fit kept it there: the third line stayed at 471pt of a 500pt page
 * and ran off the paper. Chrome moves it to the next page, as Copper does for grid rows and table rows.
 */
public class FlexWrapLaterLineTest extends TestCase {
	private static final Pattern TEXT_Y = Pattern.compile("y=([-\\d.]+) Text\\[\"([A-Z]+)\"");

	public void testLaterLineMovesToTheNextPage() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 400pt 500pt; margin: 0 } body { margin: 0; font: 10pt/12pt serif }"
				+ " .f { display: flex; flex-wrap: wrap; width: 300pt }"
				+ " .f > div { flex: 1 1 140pt; background: #ccc; border: 1pt solid }"
				+ " .m { display: inline-block; width: 20pt; background: #888 }</style></head><body>"
				+ "<div style=\"height: 300pt\">SPACER</div><div class=\"f\">"
				+ "<div>ONE<span class=\"m\" style=\"height: 230pt\"></span></div>"
				+ "<div style=\"align-self: flex-start\">TWO<span class=\"m\" style=\"height: 100pt\"></span></div>"
				+ "<div>THREE<span class=\"m\" style=\"height: 230pt\"></span></div>"
				+ "<div>FOUR<span class=\"m\" style=\"height: 230pt\"></span></div>"
				+ "<div>FIVE<span class=\"m\" style=\"height: 230pt\"></span></div></div><p>AFTER</p></body></html>";
		final File dir = Files.createTempDirectory("flex-wrap-later-line").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		java.util.Arrays.sort(pages);
		final java.util.Map<String, double[]> at = new java.util.HashMap<>();
		for (int i = 0; i < pages.length; ++i) {
			final Matcher m = TEXT_Y.matcher(Files.readString(pages[i].toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				final double y = Double.parseDouble(m.group(1));
				assertTrue(pages[i].getName() + " " + m.group(2) + " below the page at " + y, y + 242 <= 500 + 0.01
						|| m.group(2).equals("AFTER") || m.group(2).equals("SPACER"));
				at.put(m.group(2), new double[] { i, y });
			}
		}
		assertEquals("THREE and FOUR share a page", at.get("THREE")[0], at.get("FOUR")[0]);
		assertEquals("FIVE on the next page", at.get("THREE")[0] + 1, at.get("FIVE")[0]);
		assertEquals("FIVE at the top", 1, at.get("FIVE")[1], 0.1);
		assertEquals("AFTER after FIVE", at.get("FIVE")[0], at.get("AFTER")[0]);
	}
}
