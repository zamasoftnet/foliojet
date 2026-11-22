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
import net.zamasoft.foliojet.css.value.css3.GradientStops;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A repeating gradient is expanded to at most {@link GradientStops#MAX_REPEATS} periods (and one more), also when
 * its first stop is far from the start of the gradient line in periods, or when the area to cover is very many
 * periods long. The range of periods was computed in {@code int}: beyond that range the cap was skipped and the
 * expansion went on until the heap was full ({@code OutOfMemoryError}).
 */
public class RepeatingGradientRangeTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	private static final Color[] RED_BLUE = { RGBColor.create(1f, 0f, 0f), RGBColor.create(0f, 0f, 1f) };

	/** {@code red -100000000%, blue -99999999.99%}: the first period is the ten-thousand-millionth below zero. */
	public void testFirstStopFarBelowZero() {
		assertExpansionIsCapped(stops(-1000000, -999999.9999).resolve(1, true, 1));
	}

	/** A cover of some 10<sup>11</sup> periods, as a circle of 0.0001px has over a box of 100px. */
	public void testCoverOfVeryManyPeriods() {
		assertExpansionIsCapped(stops(0.01, 0.01002).resolve(0.000075, true, 1900000));
	}

	/** An ordinary repeating gradient is expanded as before: all its periods, not capped. */
	public void testOrdinaryRepetition() {
		final GradientStops.Resolved resolved = stops(0, 0.25).resolve(100, true, 1);
		assertFalse("four periods are not capped", resolved.capped());
		// Four periods of two stops each over 0..1, and the stops of the period that starts at the end
		assertTrue("stops: " + resolved.fractions().length, resolved.fractions().length >= 8);
		assertIncreasing(resolved.fractions());
	}

	/** The documents with these gradients are converted, the gradient box and what follows it are on the page. */
	public void testConversionFinishes() throws Exception {
		final List<String> texts = texts("<div style=\"width:100px;height:100px;background-image:"
				+ "repeating-conic-gradient(red -100000000%, blue -99999999.99%)\">KEEP</div>"
				+ "<div style=\"width:100px;height:100px;background-image:"
				+ "repeating-linear-gradient(red -100000000%, blue -99999999.99%)\">LINEAR</div>"
				+ "<div style=\"width:100px;height:100px;background-image:"
				+ "repeating-radial-gradient(circle 0.0001px, red 1%, blue 1.002%)\">RADIAL</div><p>AFTER</p>");
		assertTrue("KEEP: " + texts, texts.contains("KEEP"));
		assertTrue("LINEAR: " + texts, texts.contains("LINEAR"));
		assertTrue("RADIAL: " + texts, texts.contains("RADIAL"));
		assertTrue("AFTER: " + texts, texts.contains("AFTER"));
	}

	/** Red at the first position and blue at the second, as ratios of the gradient line (negative ones too). */
	private static GradientStops stops(final double red, final double blue) {
		return new GradientStops(RED_BLUE, new double[] { red, blue }, new double[2], new boolean[2]);
	}

	private static void assertExpansionIsCapped(final GradientStops.Resolved resolved) {
		assertTrue("reported as capped", resolved.capped());
		// Two stops for each of the MAX_REPEATS + 1 periods, and the two ends
		final int most = (GradientStops.MAX_REPEATS + 1) * 2 + 2;
		assertTrue("stops: " + resolved.fractions().length, resolved.fractions().length <= most);
		assertEquals(resolved.fractions().length, resolved.colors().length);
		assertIncreasing(resolved.fractions());
	}

	private static void assertIncreasing(final double[] fractions) {
		for (int i = 0; i < fractions.length; ++i) {
			assertTrue("within 0..1: " + fractions[i], fractions[i] >= 0 && fractions[i] <= 1);
			if (i > 0) {
				assertTrue("increasing at " + i + ": " + fractions[i - 1] + ", " + fractions[i],
						fractions[i] > fractions[i - 1]);
			}
		}
	}

	/** Converts the document and returns the texts drawn, in the order of the display lists of its pages. */
	private static List<String> texts(final String body) throws Exception {
		final File dir = Files.createTempDirectory("repeating-gradient").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html>" + body, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<String> texts = new ArrayList<>();
		for (int i = 1;; ++i) {
			final File page = new File(dir, String.format("page-%04d.txt", i));
			if (!page.exists()) {
				break;
			}
			final Matcher m = TEXT.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				texts.add(m.group(1));
			}
		}
		return texts;
	}
}
