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
 * A block or a table longer than its containing block with an auto inline margin, in vertical writing (2026-10-27,
 * the sweep's writing-mode mirror check, seed 43750257): the auto margin went negative and put the box's start above
 * the paper, while horizontal writing aligns it to the start (CSS 2.1 §10.3.3). Chrome (print) puts T1 at the top of
 * the content area in both vertical modes.
 */
public class VerticalOverflowAutoMarginTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"T1\"");

	private static final String BOTH = "margin-inline-start: auto; margin-inline-end: auto";

	public void testBlockBothAuto() throws Exception {
		assertEquals(0, y("vertical-rl", "<div style=\"" + BOTH + "; inline-size: 100pt\">T1</div>"), 0.01);
	}

	public void testBlockStartAuto() throws Exception {
		assertEquals(0, y("vertical-rl", "<div style=\"margin-inline-start: auto; inline-size: 100pt\">T1</div>"), 0.01);
	}

	public void testTableBothAuto() throws Exception {
		assertEquals(0, y("vertical-rl", "<table style=\"" + BOTH + "\"><tr><td><div style=\"inline-size: 100pt\">T1"
				+ "</div></td></tr></table>"), 0.01);
	}

	public void testTableStartAutoVerticalLr() throws Exception {
		assertEquals(0, y("vertical-lr", "<table style=\"margin-inline-start: auto\"><tr><td><div style=\"inline-size: "
				+ "100pt\">T1</div></td></tr></table>"), 0.01);
	}

	/** A box that fits is still centered. */
	public void testFittingBlockCentered() throws Exception {
		assertEquals(10, y("vertical-rl", "<div style=\"" + BOTH + "; inline-size: 40pt\">T1</div>"), 0.01);
	}

	public void testFittingTableCentered() throws Exception {
		assertEquals(10, y("vertical-rl", "<table style=\"" + BOTH + "\"><tr><td><div style=\"inline-size: 40pt\">T1"
				+ "</div></td></tr></table>"), 0.01);
	}

	private static double y(final String writingMode, final String box) throws Exception {
		final File dir = Files.createTempDirectory("vertical-overflow-auto-margin").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 20pt } html { writing-mode: " + writingMode + " } body { margin: 0; "
				+ "font: 10pt/12pt serif } p, div { margin: 0; padding: 0 } table { border-spacing: 0 } td { padding: 0 }"
				+ "</style></head><body><div style=\"inline-size: 60pt\">" + box + "</div></body></html>",
				StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final Matcher m = TEXT.matcher(page);
		assertTrue(page, m.find());
		return Double.parseDouble(m.group(2));
	}
}
