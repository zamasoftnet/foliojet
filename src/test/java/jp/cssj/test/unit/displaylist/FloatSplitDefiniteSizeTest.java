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
 * A float with a definite page-axis size keeps the rest of it after a break (2026-10-10). Its continuation was laid out
 * again with neither the size nor the min-size that the split left it, and fitted its content: a float with
 * {@code height: 60pt} split at 30pt went on 0pt tall on the next page, where Chrome keeps the other 30pt (as Copper
 * already did for a block). The float sits after 70pt of a 100pt page.
 */
public class FloatSplitDefiniteSizeTest extends TestCase {
	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	public void testHeight() throws Exception {
		final List<double[]> floats = floats(false, HORIZONTAL, horizontal("height: 60pt"));
		assertEquals(describe(floats), 2, floats.size());
		assertEquals(30, floats.get(0)[1], 0.01);
		// Chrome 29.25pt (it was 0)
		assertEquals(30, floats.get(1)[1], 0.01);
	}

	public void testMinHeight() throws Exception {
		final List<double[]> floats = floats(false, HORIZONTAL, horizontal("min-height: 60pt"));
		assertEquals(describe(floats), 2, floats.size());
		assertEquals(30, floats.get(1)[1], 0.01);
	}

	/**
	 * A percentage min-height (or a calc() with one) against a containing block of definite height (codex's review):
	 * it resolved only when the float's own height was definite, and its rest went to the split as the ratio, 0.
	 */
	public void testPercentageMinHeight() throws Exception {
		for (final String min : new String[] { "60%", "calc(20pt + 40%)" }) {
			final List<double[]> floats = floats(false,
					"@page { size: 200pt 120pt; margin: 10pt } body { margin: 0; font: 8pt/10pt serif }",
					"<div style=\"height: 100pt\"><div style=\"height: 70pt\">PRE</div><div style=\"float: left; "
							+ "width: 50pt; background: #ccc; min-height: " + min + "\">FL</div></div>");
			// Chrome 30.75 and 29.25 (it was 10 on one page)
			assertEquals(min + describe(floats), 2, floats.size());
			assertEquals(min, 30, floats.get(0)[1], 0.01);
			assertEquals(min, 30, floats.get(1)[1], 0.01);
		}
	}

	/** A border-box height over three pages: 30, 100 and the 20pt left (Chrome 30.75, 100.5 and 18.75). */
	public void testBorderBoxOverThreePages() throws Exception {
		final List<double[]> floats = floats(false, HORIZONTAL,
				horizontal("height: 150pt; box-sizing: border-box; border: 2pt solid #000"));
		assertEquals(describe(floats), 3, floats.size());
		assertEquals(150, floats.get(0)[1] + floats.get(1)[1] + floats.get(2)[1], 0.01);
		assertEquals(100, floats.get(1)[1], 0.01);
	}

	/** The width of a float in vertical-rl, the page axis there. */
	public void testVerticalWidth() throws Exception {
		final List<double[]> floats = floats(true,
				"@page { size: 120pt 400pt; margin: 10pt } body { margin: 0; font: 8pt/1.2 serif; writing-mode: vertical-rl }",
				"""
				<div style="width: 70pt">PRE</div><div style="display: flow-root"><div style="float: right; width: 60pt;
				height: 50pt; background: #ccc">FL</div></div><p>END</p>
				""");
		assertEquals(describe(floats), 2, floats.size());
		// Chrome 29.25pt (it was 0)
		assertEquals(30, floats.get(1)[0], 0.01);
	}

	/**
	 * The rest is a floor, not a cap (codex's review): a float whose content outgrows the rest of its width, in an
	 * opposite-progression region whose overflow checks measure the box, goes on over as many pages as it takes. Capped,
	 * T4 to T6 ran past the 60pt paper on page 2.
	 */
	public void testLongerContentStillBreaks() throws Exception {
		final File dir = convert("@page { size: 60pt 60pt; margin: 0 } body { margin: 0; font: 7pt/8.4pt serif; "
				+ "writing-mode: vertical-lr } .region { writing-mode: vertical-rl } .f { float: right; width: 90pt; "
				+ "height: 40pt; background: #ddd } .f > div { width: 40pt }",
				"<div class=\"region\"><div class=\"f\"><div>T1</div><div>T2</div><div>T3</div><div>T4</div><div>T5</div>"
						+ "<div>T6</div></div></div>");
		final Pattern text = Pattern.compile("x=(-?[\\d.]+) y=-?[\\d.]+ Text\\[\"(T\\d)\"");
		final java.util.Set<String> seen = new java.util.HashSet<>();
		for (int page = 1;; ++page) {
			final File f = new File(dir, String.format("page-%04d.txt", page));
			if (!f.exists()) {
				break;
			}
			final Matcher m = text.matcher(Files.readString(f.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				final double x = Double.parseDouble(m.group(1));
				assertTrue(m.group(2) + " at x=" + x + " on page " + page, x >= 0 && x < 60);
				assertTrue(m.group(2) + " twice", seen.add(m.group(2)));
			}
		}
		assertEquals(6, seen.size());
	}

	private static final String HORIZONTAL ="@page { size: 400pt 120pt; margin: 10pt } body { margin: 0; "
			+ "font: 8pt/1.2 serif }";

	private static String horizontal(final String size) {
		return """
				<div style="height: 70pt">PRE</div><div style="display: flow-root"><div style="float: right; width: 50pt;
				background: #ccc; %s">FL</div></div><p>END</p>
				""".formatted(size);
	}

	private static String describe(final List<double[]> floats) {
		final StringBuilder sb = new StringBuilder();
		for (final double[] f : floats) {
			sb.append(String.format(" [w=%.2f h=%.2f]", f[0], f[1]));
		}
		return sb.toString();
	}

	/** The frames of the float, page by page: the frames 50pt wide (50pt tall in vertical writing). */
	private static List<double[]> floats(final boolean vertical, final String style, final String body)
			throws Exception {
		final File dir = convert(style, body);
		final List<double[]> floats = new ArrayList<>();
		for (int page = 1;; ++page) {
			final File f = new File(dir, String.format("page-%04d.txt", page));
			if (!f.exists()) {
				return floats;
			}
			for (final String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
				final Matcher m = FRAME.matcher(line);
				if (m.find()) {
					final double w = Double.parseDouble(m.group(1)), h = Double.parseDouble(m.group(2));
					if ((vertical ? h : w) == 50) {
						floats.add(new double[] { w, h });
					}
				}
			}
		}
	}

	/** The directory of the display lists. */
	private static File convert(final String style, final String body) throws Exception {
		final File dir = Files.createTempDirectory("float-split-definite-size").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>" + style
				+ "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return dir;
	}
}
