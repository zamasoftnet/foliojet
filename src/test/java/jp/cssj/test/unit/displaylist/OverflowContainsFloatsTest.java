package jp.cssj.test.unit.displaylist;

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
 * Every overflow other than visible establishes a block formatting context that contains its floats (CSS 2.1 §9.4.1;
 * 2026-10-09). Only hidden did: an {@code overflow: auto} or {@code scroll} box holding only a float stayed 0pt tall and
 * clipped the float away, and the float leaked out and pushed the next box's float aside. Chrome gives each box the
 * height of its float. Each float's first word is at the left edge and is clipped to the height of one line, 12pt.
 */
public class OverflowContainsFloatsTest extends TestCase {
	private static final Pattern TEXT = Pattern
			.compile("x=([-\\d.]+) y=([-\\d.]+) Text\\[\"([A-Z]\\d)\"[^\\n]*?(?:clip=\\[[-\\d.]+ [-\\d.]+ [-\\d.]+ ([-\\d.]+)\\])?\\n");

	public void testAutoAndScrollContainFloats() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 400pt; margin: 0 } body { margin: 0; font: 10pt/12pt serif }"
				+ " div { margin: 0 0 10pt } .f { float: left }</style></head><body>"
				+ "<div style=\"overflow: auto\"><span class=\"f\">A1 auto</span></div>"
				+ "<div style=\"overflow: scroll\"><span class=\"f\">B1 scroll</span></div>"
				+ "<div style=\"overflow: hidden\"><span class=\"f\">C1 hidden</span></div>"
				+ "<div style=\"overflow: auto\"><p style=\"margin: 0\"><span class=\"f\">D1 nested</span></p></div>"
				+ "<div style=\"overflow: auto\"><span class=\"f\">E1<br>E2<br>E3</span></div>"
				+ "<p style=\"margin: 0\">F1 after</p></body></html>";
		final File dir = Files.createTempDirectory("overflow-contains-floats").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final Map<String, double[]> at = new HashMap<>();
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			at.put(m.group(3), new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
					m.group(4) == null ? Double.NaN : Double.parseDouble(m.group(4)) });
		}
		final String[] firsts = { "A1", "B1", "C1", "D1", "E1" };
		final double[] tops = { 0, 22, 44, 66, 88 };
		for (int i = 0; i < firsts.length; ++i) {
			final double[] t = at.get(firsts[i]);
			assertNotNull("not drawn: " + firsts[i] + "\n" + page, t);
			assertEquals(firsts[i] + " x", 0, t[0], 0.1);
			assertEquals(firsts[i] + " y", tops[i], t[1], 0.1);
			if (!Double.isNaN(t[2])) {
				assertTrue(firsts[i] + " clipped to " + t[2], t[2] >= (firsts[i].equals("E1") ? 36 : 12) - 0.1);
			}
		}
		assertEquals("F1 after the boxes", 134, at.get("F1")[1], 0.1);
	}
}
