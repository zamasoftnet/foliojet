package jp.cssj.test.unit.displaylist;

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
 * Media queries measure the page area of the configured paper (2026-10-11): output.page-width and output.page-height
 * less output.page-margins, left and right and top and bottom each, as Chrome's print measures its print settings'
 * paper and margins. The document's {@code @page} is not consulted. They measured the whole paper: on A4 (page area
 * 697.7px) furo's {@code @media (max-width: 48em)} never held.
 */
public class MediaPageAreaTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=([-0-9.]+) y=[-0-9.]+ Text\\[\"A\"");

	/** The text's x: 30pt where the condition holds, 60pt where it does not. */
	private static final String RULES = "#a { margin-left: 60pt } @media %s { #a { margin-left: 30pt } }";

	public void testA4PageArea() throws Exception {
		// A4 less the default margins (12.7mm): 697.7px
		assertEquals(30, x("(min-width: 697px) and (max-width: 698px)", null), 0.01);
		assertEquals(60, x("(min-width: 699px)", null), 0.01);
	}

	public void testMarginsEachSide() throws Exception {
		// 210mm less 20mm and 40mm is 150mm (566.93px); 297mm less 10mm and 30mm is 257mm (971.34px)
		assertEquals(30, x("(min-width: 566px) and (max-width: 567px) and (min-height: 971px) and (max-height: 972px)",
				"10mm 20mm 30mm 40mm"), 0.01);
	}

	public void testThreeValues() throws Exception {
		// Top, left and right, bottom: 210mm less 20mm twice is 170mm (642.52px), 297mm less 10mm and 30mm is 257mm (the
		// pages read a fourth value that was not there: an exception)
		assertEquals(30, x("(min-width: 642px) and (max-width: 643px) and (min-height: 971px) and (max-height: 972px)",
				"10mm 20mm 30mm"), 0.01);
	}

	public void testDocumentPageNotConsulted() throws Exception {
		// The document's @page (A5, no margins) does not change what media queries measure
		assertEquals(30, x("(min-width: 697px) and (max-width: 698px)", null, "@page { size: A5; margin: 0 }"), 0.01);
	}

	private static double x(final String condition, final String margins) throws Exception {
		return x(condition, margins, "");
	}

	private static double x(final String condition, final String margins, final String page) throws Exception {
		final File dir = Files.createTempDirectory("media-page-area").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>" + page
				+ " body { margin: 0 } " + String.format(RULES, condition) + "</style></head><body><div id=\"a\">A</div>"
				+ "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			if (margins != null) {
				session.property("output.page-margins", margins);
			}
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final Matcher m = TEXT.matcher(Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8));
		assertTrue(m.find());
		return Double.parseDouble(m.group(1));
	}
}
