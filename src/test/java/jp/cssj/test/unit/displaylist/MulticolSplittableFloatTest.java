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
 * A float in a multicol that a column cannot hold is split over the columns, as Chrome does (2026-10-10, the rest of
 * sweep defect R2). The floats of a multicol in the page's flow are placed in the band before the columns exist, and
 * only the balancing that builds them classifies them against the column; nothing split what it reserved, balancing
 * did not count the floats, and a column that took a line of text kept a float whole. The float stayed in the first
 * column past the paper. The page is 400pt by 120pt with 10pt margins; 70pt of it is taken before the multicol.
 */
public class MulticolSplittableFloatTest extends TestCase {
	private static final String HORIZONTAL = "@page { size: 400pt 120pt; margin: 10pt } body { margin: 0; "
			+ "font: 8pt/1.2 serif }";

	private static final Pattern FRAME = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	/** A float alone in two columns: 30pt in each column of the 30pt left (Chrome the same; it ran 30pt off the page). */
	public void testFloatAloneOverTwoColumns() throws Exception {
		final List<String> pages = convert(HORIZONTAL, "<div style=\"height: 70pt\">PRE</div><div style=\"column-count: 2\">"
				+ "<div style=\"float: right; height: 60pt; width: 50pt; background: #ccc\">FL</div></div><p>END</p>");
		final List<double[]> floats = frames(pages.get(0), 50);
		assertEquals(describe(floats), 2, floats.size());
		assertEquals(30, floats.get(0)[3], 0.01);
		assertEquals(30, floats.get(1)[3], 0.01);
		assertTrue(floats.get(0)[0] != floats.get(1)[0]);
	}

	/** Beside a line of text, which a column cut kept whole with the float in it. */
	public void testFloatBesideText() throws Exception {
		final List<String> pages = convert(HORIZONTAL, "<div style=\"height: 70pt\">PRE</div><div style=\"column-count: 2\">"
				+ "<div style=\"float: right; height: 60pt; width: 50pt; background: #ccc\">FL</div>A B C D E F G H I J</div>"
				+ "<p>END</p>");
		final List<double[]> floats = frames(pages.get(0), 50);
		assertEquals(describe(floats), 2, floats.size());
		assertEquals(30, floats.get(1)[3], 0.01);
	}

	/** In vertical-rl, where the page axis is the width. */
	public void testVertical() throws Exception {
		final List<String> pages = convert("@page { size: 120pt 400pt; margin: 10pt } body { margin: 0; "
				+ "font: 8pt/1.2 serif; writing-mode: vertical-rl }", "<div style=\"width: 70pt\">PRE</div>"
						+ "<div style=\"column-count: 2\"><div style=\"float: right; width: 60pt; height: 50pt; "
						+ "background: #ccc\">FL</div></div><p>END</p>");
		final List<double[]> floats = new ArrayList<>();
		for (final double[] f : frames(pages.get(0), -1)) {
			if (f[3] == 50) {
				floats.add(f);
			}
		}
		assertEquals(describe(floats), 2, floats.size());
		assertEquals(30, floats.get(0)[2], 0.01);
		assertEquals(30, floats.get(1)[2], 0.01);
	}

	private static String describe(final List<double[]> frames) {
		final StringBuilder sb = new StringBuilder();
		for (final double[] f : frames) {
			sb.append(String.format(" [x=%.2f w=%.2f h=%.2f]", f[0], f[2], f[3]));
		}
		return sb.toString();
	}

	/** The frames of a page with the given width (any for -1): {x, y, w, h}. */
	private static List<double[]> frames(final String page, final double width) {
		final List<double[]> frames = new ArrayList<>();
		final Matcher m = FRAME.matcher(page);
		while (m.find()) {
			final double w = Double.parseDouble(m.group(3));
			if (width < 0 || w == width) {
				frames.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), w,
						Double.parseDouble(m.group(4)) });
			}
		}
		return frames;
	}

	/** The display lists of the pages. */
	private static List<String> convert(final String style, final String body) throws Exception {
		final File dir = Files.createTempDirectory("multicol-splittable-float").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>" + style
				+ "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<String> pages = new ArrayList<>();
		for (int page = 1;; ++page) {
			final File f = new File(dir, String.format("page-%04d.txt", page));
			if (!f.exists()) {
				return pages;
			}
			pages.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
	}
}
