package jp.cssj.test.unit._0300_min_height;

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
 * A min-height above the max-height wins, as in CSS 2.1 §10.7 and Chrome; in vertical writing the same holds for the
 * width (2026-11-19, sweep triage §238, user decision). The block axis took the max: height: 116pt; min-height: 88pt;
 * max-height: 80.1pt came out 80.1pt, for a block, a float, an inline-block, a multicol and a column flex item alike.
 * The inline axis and tables already took the min.
 */
public class BlockAxisMinWinsOverMaxTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"(T[01])\"");

	private static final String MM = "min-%1$s:88pt;max-%1$s:80.1pt";

	/** The distance along the block axis from T0, which starts the box, to T1, which follows it. */
	private static double blockDistance(final String writingMode, final String body) throws Exception {
		final String doc = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page{size:300pt 300pt;margin:0pt}"
				+ "html{writing-mode:" + writingMode + "} body{margin:0;font:10pt/12pt serif} p{margin:0}</style></head>"
				+ "<body>" + body + "</body></html>";
		final File dir = Files.createTempDirectory("block-axis-min-wins").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), doc, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final Matcher m = TEXT.matcher(Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8));
		final double[][] at = new double[2][];
		while (m.find()) {
			at[m.group(3).charAt(1) - '0'] = new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
		}
		assertNotNull(body, at[0]);
		assertNotNull(body, at[1]);
		return writingMode.equals("horizontal-tb") ? at[1][1] - at[0][1] : at[0][0] - at[1][0];
	}

	private static void assertMinWins(final String writingMode, final String body) throws Exception {
		assertEquals(writingMode + " " + body, 88.0, blockDistance(writingMode, body), 0.01);
	}

	public void testHorizontalHeight() throws Exception {
		final String mm = String.format(MM, "height");
		assertMinWins("horizontal-tb", "<div style=\"height:116pt;" + mm + "\">T0</div><p>T1</p>");
		assertMinWins("horizontal-tb", "<div style=\"" + mm + "\">T0</div><p>T1</p>");
		assertMinWins("horizontal-tb",
				"<div style=\"box-sizing:border-box;padding:0 5pt;height:116pt;" + mm + "\">T0</div><p>T1</p>");
		assertMinWins("horizontal-tb", "<div style=\"column-count:2;height:116pt;" + mm + "\">T0</div><p>T1</p>");
		assertMinWins("horizontal-tb", "<div style=\"display:flex;flex-direction:column\"><div style=\"height:116pt;"
				+ mm + "\">T0</div></div><p>T1</p>");
	}

	public void testHorizontalFloatAndInlineBlock() throws Exception {
		final String mm = String.format(MM, "height");
		assertMinWins("horizontal-tb",
				"<div style=\"float:left;width:50pt;height:116pt;" + mm + "\">T0</div><p style=\"clear:both\">T1</p>");
		assertMinWins("horizontal-tb", "<div><span style=\"display:inline-block;vertical-align:top;width:50pt;height:116pt;"
				+ mm + "\">T0</span></div><p>T1</p>");
	}

	public void testVerticalWidth() throws Exception {
		final String mm = String.format(MM, "width");
		assertMinWins("vertical-rl", "<div style=\"width:116pt;" + mm + "\">T0</div><p>T1</p>");
		assertMinWins("vertical-rl", "<div style=\"" + mm + "\">T0</div><p>T1</p>");
		assertMinWins("vertical-rl",
				"<div style=\"float:left;height:50pt;width:116pt;" + mm + "\">T0</div><p style=\"clear:both\">T1</p>");
	}

	/** Without a conflict the max still caps the size. */
	public void testMaxStillCapsWithoutConflict() throws Exception {
		assertEquals(80.1, blockDistance("horizontal-tb",
				"<div style=\"height:116pt;min-height:40pt;max-height:80.1pt\">T0</div><p>T1</p>"), 0.01);
		assertEquals(80.1, blockDistance("vertical-rl",
				"<div style=\"width:116pt;min-width:40pt;max-width:80.1pt\">T0</div><p>T1</p>"), 0.01);
	}
}
