package jp.cssj.test.unit._0390_writing_mode;

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
 * A box whose block direction runs against its parent's (vertical-lr in vertical-rl) establishes an independent
 * formatting context and avoids the outer floats (2026-11-19, sweep triage §246, regression-check fit seed 11542199).
 * It sealed its own floats but was laid over the outer ones, so its lines went past the float the way its own blocks
 * run, to the right: off the 120pt paper at x=160 after a 100pt float as long as the line.
 */
public class ReversedBlockDirectionAvoidsFloatsTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"(T\\d+)\"");

	/** {page, x} of every word, in drawing order. */
	private static java.util.Map<String, double[]> words(final String body) throws Exception {
		final String doc = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page{size:120pt 400pt;margin:0pt}"
				+ "body{margin:0;font:12pt/1.2 serif;writing-mode:vertical-rl} p,div{margin:0;padding:0}</style></head>"
				+ "<body>" + body + "</body></html>";
		final File dir = Files.createTempDirectory("reversed-block-direction").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), doc, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final java.util.Map<String, double[]> words = new java.util.LinkedHashMap<>();
		for (int page = 1;; ++page) {
			final File f = new File(dir, String.format("page-%04d.txt", page));
			if (!f.exists()) {
				break;
			}
			final Matcher m = TEXT.matcher(Files.readString(f.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				words.put(m.group(3), new double[] { page, Double.parseDouble(m.group(1)) });
			}
		}
		return words;
	}

	/** Past a float as long as the line, the box goes after it, on the paper. */
	public void testBoxAfterAFloatAsLongAsTheLineStaysOnThePaper() throws Exception {
		for (final int floatWidth : new int[] { 100, 200 }) {
			final java.util.Map<String, double[]> words = words("<div style=\"float:right;width:" + floatWidth
					+ "pt;height:400pt\"></div><div style=\"writing-mode:vertical-lr;width:60pt\"><p>T0</p></div>");
			final double[] t0 = words.get("T0");
			assertNotNull(t0);
			assertTrue("float " + floatWidth + "pt: T0 at x=" + t0[1], t0[1] >= 0 && t0[1] <= 120 - 12);
		}
	}

	/** Beside a shorter float, the box narrows along the line and its lines start at its left, as in Chrome. */
	public void testBoxBesideAFloatNarrows() throws Exception {
		final java.util.Map<String, double[]> words = words("<div style=\"float:right;width:50pt;height:300pt\"></div>"
				+ "<div style=\"writing-mode:vertical-lr;width:60pt\"><p>T0 T1 T2 T3 T4 T5 T6 T7 T8 T9</p></div>");
		assertEquals(60.0, words.get("T0")[1], 0.01);
		assertEquals(74.4, words.get("T6")[1], 0.01);
	}
}
