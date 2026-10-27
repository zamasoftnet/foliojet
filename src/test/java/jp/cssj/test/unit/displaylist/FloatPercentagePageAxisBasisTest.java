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
 * A float with a length for its page-axis size resolves a percentage min or max page-axis size only against a
 * containing block of definite page-axis size (2026-10-27, sweep seed 43632324): the length made the basis count as
 * definite, so a vertical float with width: 8em and max-width: 90% in an auto-sized block of a table resolved the
 * percentage against the cell's 0 and came out 0 wide, its content running off the paper from its block start. Out of
 * a table the percentage resolved against the nearest ancestor of definite width. Chrome treats it as none (or 0 for
 * a min) in an auto-sized block, and resolves it in a block of definite width. Containing blocks definite by their
 * context keep resolving it: the page, a stretched flex or grid item, a block of percentage size and the page area.
 */
public class FloatPercentagePageAxisBasisTest extends TestCase {
	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final String FLOAT_LR = "float: right; writing-mode: vertical-lr; background: #ccc; ";

	/** The seed's shape: in a table, through an auto-sized block. Chrome keeps the float 64pt wide. */
	public void testMaxInTable() throws Exception {
		final double[] f = floatFrame("vertical-rl", "<div style=\"display: table; width: 88pt\"><div><div style=\""
				+ FLOAT_LR + "width: 64pt; max-width: 50%\"><div>FL</div></div></div></div>");
		assertEquals(64, f[2], 0.01);
		assertEquals(180 - 64, f[0], 0.01);
	}

	/** The reverse: a vertical-rl float in a vertical-lr document. */
	public void testMaxInTableReversed() throws Exception {
		final double[] f = floatFrame("vertical-lr",
				"<div style=\"display: table; width: 88pt\"><div><div style=\"float: right; writing-mode: vertical-rl; "
						+ "background: #ccc; width: 64pt; max-width: 50%\"><div>FL</div></div></div></div>");
		assertEquals(64, f[2], 0.01);
		assertEquals(0, f[0], 0.01);
	}

	/** Out of a table, the auto-sized block is the basis, not the 88pt ancestor: none, as in Chrome (it was 44pt). */
	public void testMaxInAutoBlock() throws Exception {
		final double[] f = floatFrame("vertical-rl", "<div style=\"width: 88pt\"><div><div style=\"" + FLOAT_LR
				+ "width: 64pt; max-width: 50%\">FL</div></div></div>");
		assertEquals(64, f[2], 0.01);
	}

	/** A percentage min-width counts as 0 there (it was 44pt; Chrome 16pt). */
	public void testMinInAutoBlock() throws Exception {
		final double[] f = floatFrame("vertical-rl", "<div style=\"width: 88pt\"><div><div style=\"" + FLOAT_LR
				+ "width: 16pt; min-width: 50%\">FL</div></div></div>");
		assertEquals(16, f[2], 0.01);
	}

	/** A containing block of definite width still resolves both, as in Chrome (44pt). */
	public void testDefiniteContainingBlock() throws Exception {
		assertEquals(44, floatFrame("vertical-rl", "<div style=\"width: 88pt\"><div style=\"" + FLOAT_LR
				+ "width: 64pt; max-width: 50%\">FL</div></div>")[2], 0.01);
		assertEquals(44, floatFrame("vertical-rl", "<div style=\"width: 88pt\"><div style=\"" + FLOAT_LR
				+ "width: 16pt; min-width: 50%\">FL</div></div>")[2], 0.01);
	}

	private static final String BOX = "width: 10pt; background: #ccc; ";

	/**
	 * Containing blocks definite by their context keep the basis (codex 2026-10-27): a stretched flex or grid item
	 * (Chrome 44pt for both).
	 */
	public void testStretchedItem() throws Exception {
		assertEquals(44, frame("", "<div style=\"display: flex; height: 88pt\"><div style=\"flex: 1\"><span style=\""
				+ "display: inline-block; " + BOX + "height: 64pt; max-height: 50%\"></span></div></div>")[3], 0.01);
		assertEquals(44, frame("", "<div style=\"display: flex; height: 88pt\"><div style=\"flex: 1\"><div style=\""
				+ "float: left; " + BOX + "height: 16pt; min-height: 50%\"></div></div></div>")[3], 0.01);
		assertEquals(44, frame("", "<div style=\"display: grid; height: 88pt; grid-template-rows: 88pt\"><div><span "
				+ "style=\"display: inline-block; " + BOX + "height: 64pt; max-height: 50%\"></span></div></div>")[3], 0.01);
	}

	/** So is a percentage-height block whose basis is (codex 2026-10-27; Chrome 44pt). */
	public void testPercentageHeightBlock() throws Exception {
		assertEquals(44, frame("", "<div style=\"display: flex; height: 88pt\"><div style=\"flex: 1\"><div style=\""
				+ "height: 100%\"><span style=\"display: inline-block; " + BOX
				+ "height: 64pt; max-height: 50%\"></span></div></div></div>")[3], 0.01);
		assertEquals(140, frame("html, body { height: 100% }", "<span style=\"display: inline-block; " + BOX
				+ "height: 200pt; max-height: 50%\"></span>")[3], 0.01);
	}

	/** ... but an auto-sized block in one is not (Chrome 64pt; it was 44pt). */
	public void testAutoBlockInFlexItem() throws Exception {
		assertEquals(64, frame("", "<div style=\"display: flex; height: 88pt\"><div style=\"flex: 1\"><div><span style=\""
				+ "display: inline-block; " + BOX + "height: 64pt; max-height: 50%\"></span></div></div></div>")[3], 0.01);
	}

	/** The page area is the basis of a shrink-to-fit root and of a page float (280pt high). */
	public void testPageArea() throws Exception {
		assertEquals(142, frame("html { width: max-content; height: 200pt; max-height: 50%; border: 1pt solid }",
				"x")[3], 0.01);
		assertEquals(140, frame("", "<div style=\"float: top; " + BOX + "height: 200pt; max-height: 50%\">x</div>")[3],
				0.01);
	}

	/** x, y, w and h of the one frame on the first page, in the page area (only the float has a background). */
	private static double[] floatFrame(final String writingMode, final String body) throws Exception {
		return frame("body { writing-mode: " + writingMode + " }", body);
	}

	private static double[] frame(final String css, final String body) throws Exception {
		final File dir = Files.createTempDirectory("float-percentage-page-axis").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 200pt 300pt; margin: 10pt } body { margin: 0; font: 8pt/10pt serif } div { margin: 0; "
				+ "padding: 0 } " + css + "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		double[] frame = null;
		for (final String line : Files.readAllLines(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8)) {
			final Matcher m = FRAME.matcher(line);
			if (m.find()) {
				assertNull(body, frame);
				frame = new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
						Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) };
			}
		}
		assertNotNull(body, frame);
		return frame;
	}
}
