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
 * A wrapping column of auto width stays one column (2026-10-11), as in the manual (CSS-SUPPORT §8): Chrome breaks it into
 * columns. 33528938 did so where its width filled its block, and was taken back the same day: a block builder found
 * that width known up to its root, also when it replayed the content of a box sized from its content, which the
 * recording had streamed, and the conversion failed (Regression19132Test).
 */
public class FlexColumnWrapFillTest extends TestCase {
	private static final Pattern ITEM = Pattern.compile("x=([\\d.]+) y=([\\d.]+) AbsoluteRectFrame\\[w=60.00 h=15.00\\]");

	public void testOneColumnInAFilledBlock() throws Exception {
		// Two 15pt items with a 3.75pt gap in an 18.75pt-high column 135pt wide: one column, the second item under the
		// first (Chrome makes two columns, the second at 69.375pt)
		final Matcher m = Pattern.compile("x=([\\d.]+) y=([\\d.]+) AbsoluteRectFrame\\[w=[\\d.]+ h=15.00\\]")
				.matcher(convert("<div style=\"display: flex; flex-flow: column wrap; height: 18.75pt; gap: 3.75pt; "
						+ "align-items: flex-start\"><div></div><div></div></div>"));
		final List<Double> xy = new ArrayList<>();
		while (m.find()) {
			xy.add(Double.parseDouble(m.group(1)));
			xy.add(Double.parseDouble(m.group(2)));
		}
		assertEquals(List.of(0.0, 0.0, 0.0, 18.75), xy);
	}

	public void testShrinkToFitHostKeepsOneColumn() throws Exception {
		// In an inline-block of auto width, the column's width follows its content, measured as one column: it stays one
		// column, the box after the inline-block at 60pt, not over a second column (Chrome makes two columns, the box at
		// 120pt; not yet)
		final Matcher m = Pattern.compile("x=([\\d.]+) y=([\\d.]+) AbsoluteRectFrame\\[w=7.50 h=7.50\\]")
				.matcher(convert("<span style=\"display: inline-block; vertical-align: top\"><div style=\"display: flex; "
						+ "flex-flow: column wrap; height: 15pt; align-items: flex-start\"><div></div><div></div></div></span>"
						+ "<i style=\"display: inline-block; vertical-align: top; width: 7.5pt; height: 7.5pt; "
						+ "background: #000\"></i>"));
		assertTrue(m.find());
		assertEquals(60, Double.parseDouble(m.group(1)), 0.01);
	}

	public void testIntrinsicWidthHostKeepsOneColumn() throws Exception {
		// In a block of width: max-content, the column's width follows its content, measured as one column: it stays one
		// column, not a second column outside the block (Chrome makes two columns in a 120pt block; not yet)
		final Matcher m = ITEM.matcher(convert("<span style=\"display: block; width: max-content\"><div style=\"display: "
				+ "flex; flex-flow: column wrap; height: 15pt; align-items: flex-start\"><div></div><div></div></div></span>"));
		final List<Double> xy = new ArrayList<>();
		while (m.find()) {
			xy.add(Double.parseDouble(m.group(1)));
			xy.add(Double.parseDouble(m.group(2)));
		}
		assertEquals(List.of(0.0, 0.0, 0.0, 15.0), xy);
	}

	private static String convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("flex-column-wrap-fill").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 150pt 225pt; margin: 7.5pt } body { margin: 0 }"
				+ " div div { flex: 0 0 15pt; min-width: 60pt; background: #ccc }"
				+ "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
	}
}
