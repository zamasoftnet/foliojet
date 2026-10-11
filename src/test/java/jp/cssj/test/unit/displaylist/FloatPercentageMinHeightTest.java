package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
 * A float's percentage min-height resolves against a containing block of definite height while the float's own height
 * is auto (2026-10-10, kept on 2026-10-11 when the continuation sizes of split floats were taken out): it was dropped,
 * and the float fitted its content. Chrome takes 60pt.
 */
public class FloatPercentageMinHeightTest extends TestCase {
	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	public void testPercentageMinHeight() throws Exception {
		for (final String min : new String[] { "60%", "calc(20pt + 40%)" }) {
			final List<double[]> floats = floats("<div style=\"height: 100pt\"><div style=\"float: left; width: 50pt; "
					+ "background: #ccc; min-height: " + min + "\">FL</div></div>");
			assertEquals(min, 1, floats.size());
			assertEquals(min, 60, floats.get(0)[1], 0.01);
		}
	}

	/** Without a definite height on the containing block, the percentage stays unresolved: the float fits its line. */
	public void testIndefiniteContainingBlock() throws Exception {
		final List<double[]> floats = floats(
				"<div><div style=\"float: left; width: 50pt; background: #ccc; min-height: 60%\">FL</div></div>");
		assertEquals(1, floats.size());
		assertEquals(10, floats.get(0)[1], 0.01);
	}

	/** The frames 50pt wide on the first page. */
	private static List<double[]> floats(final String body) throws Exception {
		final File dir = Files.createTempDirectory("float-percentage-min-height").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 200pt 300pt; margin: 10pt } body { margin: 0; font: 8pt/10pt serif }"
				+ "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<double[]> floats = new ArrayList<>();
		for (final String line : Files.readAllLines(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8)) {
			final Matcher m = FRAME.matcher(line);
			if (m.find() && Double.parseDouble(m.group(1)) == 50) {
				floats.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) });
			}
		}
		return floats;
	}
}
