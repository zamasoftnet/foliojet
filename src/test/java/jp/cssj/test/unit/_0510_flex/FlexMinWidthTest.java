package jp.cssj.test.unit._0510_flex;

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
 * The automatic minimum size of a flex item survives {@code min-width: auto}, and {@code min-width: max-content} keeps
 * the item at its content (2026-10-09). The first was parsed to 0 and the second resolved to 0 in a row, so both let a
 * row too narrow for its items shrink them below their words, where an item without min-width stopped at its
 * min-content size (primer-css' buttons, {@code min-width: max-content}). Chrome lays the three rows out alike.
 */
public class FlexMinWidthTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	public void testMinWidthAutoAndMaxContent() throws Exception {
		final List<double[]> rows = new ArrayList<>();
		for (final String min : new String[] { "", "min-width: auto", "min-width: max-content" }) {
			rows.add(xs(convert("<div class=\"r\"><div style=\"" + min + "\">AAAAA</div><div style=\"" + min
					+ "\">BBBBBB</div><div style=\"" + min + "\">CCCCC</div></div>")));
		}
		for (int i = 1; i < rows.size(); ++i) {
			for (int k = 0; k < 3; ++k) {
				assertEquals("row " + i + " item " + k, rows.get(0)[k], rows.get(i)[k], 0.01);
			}
		}
		// min-width: 0 still lets the items shrink below their words
		final double[] zero = xs(convert("<div class=\"r\"><div style=\"min-width: 0\">AAAAA</div>"
				+ "<div style=\"min-width: 0\">BBBBBB</div><div style=\"min-width: 0\">CCCCC</div></div>"));
		assertTrue("min-width: 0 shrinks: " + zero[1] + " vs " + rows.get(0)[1], zero[1] < rows.get(0)[1] - 1);
	}

	private static double[] xs(final String page) {
		final double[] x = new double[3];
		final Matcher m = TEXT.matcher(page);
		int k = 0;
		while (m.find() && k < 3) {
			x[k++] = Double.parseDouble(m.group(1));
		}
		assertEquals("three items:\n" + page, 3, k);
		return x;
	}

	private static String convert(final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: A4; margin: 36pt } body { margin: 0; font: 14px sans-serif }"
				+ " .r { display: flex; width: 60px } .r > div { border: 1px solid blue }</style></head><body>" + body
				+ "</body></html>";
		final File dir = Files.createTempDirectory("flex-min-width").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
	}
}
