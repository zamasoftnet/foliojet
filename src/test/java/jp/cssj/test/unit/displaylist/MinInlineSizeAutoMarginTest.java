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
 * Auto inline margins of a block whose min-inline-size raises its size (2026-10-27, codex on the vertical overflow fix):
 * the margins stayed those of the smaller size, so a centered box went off center and a box longer than its containing
 * block did not align to the start. Chrome (print) resolves them for the minimum: in a 60pt container, T1 at 5pt for
 * 40pt or 50% raised to 50pt, at 0 for 100pt; the same in both writing modes.
 */
public class MinInlineSizeAutoMarginTest extends TestCase {
	public void testRaisedHorizontal() throws Exception {
		assertEquals(5, start("horizontal-tb", "inline-size: 40pt; min-inline-size: 50pt"), 0.01);
	}

	public void testRaisedPercentageHorizontal() throws Exception {
		assertEquals(5, start("horizontal-tb", "inline-size: 50%; min-inline-size: 50pt"), 0.01);
	}

	public void testRaisedOverflowHorizontal() throws Exception {
		assertEquals(0, start("horizontal-tb", "inline-size: 40pt; min-inline-size: 100pt"), 0.01);
	}

	public void testRaisedVertical() throws Exception {
		assertEquals(5, start("vertical-rl", "inline-size: 40pt; min-inline-size: 50pt"), 0.01);
	}

	public void testRaisedOverflowVertical() throws Exception {
		assertEquals(0, start("vertical-rl", "inline-size: 40pt; min-inline-size: 100pt"), 0.01);
	}

	/** Not raised: centered as before. */
	public void testNotRaised() throws Exception {
		assertEquals(10, start("horizontal-tb", "inline-size: 40pt; min-inline-size: 20pt"), 0.01);
	}

	/** The inline-start offset of T1 in the content area. */
	private static double start(final String writingMode, final String size) throws Exception {
		final File dir = Files.createTempDirectory("min-inline-size-auto-margin").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 20pt } html { writing-mode: " + writingMode + " } body { margin: 0; "
				+ "font: 10pt/12pt serif } p, div { margin: 0; padding: 0 }</style></head><body><div style=\"inline-size: "
				+ "60pt\"><div style=\"margin-inline: auto; " + size + "\">T1</div></div></body></html>",
				StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final Matcher m = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"T1\"").matcher(page);
		assertTrue(page, m.find());
		return Double.parseDouble(m.group(writingMode.startsWith("vertical") ? 2 : 1));
	}
}
