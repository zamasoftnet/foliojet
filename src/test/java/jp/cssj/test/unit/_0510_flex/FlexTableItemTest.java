package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
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
 * A table of width auto that is itself a flex item fills the item (2026-10-09, as a grid item's table does since
 * b8e1b416): its main size in a row ({@code flex: 1}), or the column it is stretched across. It kept its content width
 * inside the item. The distances between the two cells are Chrome's (199.62pt in the column, 198.55pt in the row).
 */
public class FlexTableItemTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	public void testTableItemFillsItsItem() throws Exception {
		final Map<String, Double> x = convert("""
				<div class="w" style="display: flex; flex-direction: column"><table><tr><td>COLA</td><td>COLB</td></tr></table></div>
				<div class="w" style="display: flex"><table style="flex: 1"><tr><td>ROWA</td><td>ROWB</td></tr></table></div>
				<div class="w" style="display: flex"><table><tr><td>AUTOA</td><td>AUTOB</td></tr></table></div>
				<div class="w" style="display: flex; flex-direction: column; align-items: center"><table><tr><td>CTRA</td><td>CTRB</td></tr></table></div>
				""");
		assertEquals("stretched across the column", 199.62, x.get("COLB") - x.get("COLA"), 1);
		assertEquals("flex: 1 in a row", 198.55, x.get("ROWB") - x.get("ROWA"), 1);
		assertTrue("an auto item keeps the table's own width: " + (x.get("AUTOB") - x.get("AUTOA")),
				x.get("AUTOB") - x.get("AUTOA") < 100);
		assertTrue("a centered item keeps the table's own width: " + (x.get("CTRB") - x.get("CTRA")),
				x.get("CTRB") - x.get("CTRA") < 100);
	}

	private static Map<String, Double> convert(final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: A4; margin: 36pt } body { margin: 0; font: 12pt/1.5 sans-serif }"
				+ " table { border-collapse: collapse } td { border: 1px solid #000; padding: 2pt }"
				+ " .w { width: 400pt; border: 1px solid red; margin-bottom: 12pt }</style></head><body>" + body
				+ "</body></html>";
		final File dir = Files.createTempDirectory("flex-table-item").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final Map<String, Double> x = new HashMap<>();
		final Matcher m = TEXT.matcher(Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8));
		while (m.find()) {
			x.putIfAbsent(m.group(3), Double.parseDouble(m.group(1)));
		}
		return x;
	}
}
