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
 * A later line of a wrapping flex keeps its gap after a line broken across pages (2026-10-09, stage 0 of
 * copperpdf4/docs/design/retained-container-relayout-design.md). The first line holds a 30-line {@code pre} with a
 * background on 200pt pages of nine 21pt lines; each break leaves 11pt that the ledger counted as consumed, so the line
 * came out taller than its ledger when laid out again. The push-down after the restyle inferred the gap from the ledger
 * and turned that difference into a gap: the next line came 33pt late (eurekalert: 19.57pt between lines placed edge to
 * edge). The ledger now keeps each line's gap. The last 3 lines of the {@code pre} are on page 4, ending at 63pt.
 */
public class FlexLineGapTest extends TestCase {
	private static final Pattern TEXT_Y = Pattern.compile("y=([-\\d.]+) Text\\[\"([A-Za-z-]+)\"");

	public void testRowGap() throws Exception {
		final Map<String, double[]> at = this.convert(20, "<div>SECOND</div>");
		assertEquals("SECOND page", 3, at.get("SECOND")[0], 0);
		assertEquals("SECOND after the line and the gap", 63 + 20, at.get("SECOND")[1], 0.1);
	}

	public void testNoGap() throws Exception {
		final Map<String, double[]> at = this.convert(0, "<div>SECOND</div>");
		assertEquals("SECOND page", 3, at.get("SECOND")[0], 0);
		assertEquals("SECOND right after the line", 63, at.get("SECOND")[1], 0.1);
	}

	/** The form of eurekalert's sidebar: relatively positioned links in a narrow item beside nothing. */
	public void testSidebarOfRelativeLinks() throws Exception {
		final Map<String, double[]> at = this.convert(0, "<aside style=\"width: 60pt\"><ul style=\"margin: 0; padding: 0;"
				+ " list-style: none\"><li style=\"position: relative\"><a style=\"position: relative; display: block\">"
				+ "Peer- Reviewed Publication</a></li><li style=\"position: relative\"><a style=\"position: relative;"
				+ " display: block\">Reports</a></li></ul></aside> ");
		assertEquals("Peer- page", 3, at.get("Peer-")[0], 0);
		assertEquals("Peer- right after the line", 63, at.get("Peer-")[1], 0.1);
		assertEquals("END page", 3, at.get("END")[0], 0);
	}

	private Map<String, double[]> convert(final int gap, final String second) throws Exception {
		final StringBuilder lines = new StringBuilder();
		for (int i = 1; i <= 30; ++i) {
			lines.append("line ").append(i).append('\n');
		}
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 200pt 200pt; margin: 0 } body { margin: 0; font: 10pt/21pt serif }"
				+ " pre { margin: 0; font: inherit; background: #ddd }"
				+ " .f { display: flex; flex-wrap: wrap; row-gap: " + gap + "pt; width: 200pt } .main { width: 200pt }"
				+ "</style></head><body><div class=\"f\"><div class=\"main\"><pre>" + lines + "</pre></div>" + second
				+ "</div><p style=\"margin: 0\">END</p></body></html>";
		final File dir = Files.createTempDirectory("flex-line-gap").toFile();
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
		final Map<String, double[]> at = new HashMap<>();
		for (int i = 0; i < pages.length; ++i) {
			final Matcher m = TEXT_Y.matcher(Files.readString(pages[i].toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				at.putIfAbsent(m.group(2), new double[] { i, Double.parseDouble(m.group(1)) });
			}
		}
		return at;
	}
}
