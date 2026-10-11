package jp.cssj.test.unit._0120_float;

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
 * A float or an inline-block shrinks to a fixed-width block with its margins counted once (2026-10-11; the widths are
 * Chrome's): the measured max-content took the block's used start margin and its specified margins both, so a 24px
 * block with margin-left: 8px made its float 40px wide (Chrome 32px), and margin-left: -8px made it 8px (Chrome 16px).
 */
public class FixedWidthMarginTest extends TestCase {
	/** The grey box: the float's or inline-block's border box. */
	private static final Pattern GREY = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=7\\.50\\]");

	public void testStartMargin() throws Exception {
		assertEquals(List.of(24.0), widths("float: left", "margin-left: 6pt"));
	}

	public void testEndMargin() throws Exception {
		assertEquals(List.of(24.0), widths("float: left", "margin-right: 6pt"));
	}

	public void testBothMargins() throws Exception {
		assertEquals(List.of(27.0), widths("float: left", "margin-left: 6pt; margin-right: 3pt"));
	}

	public void testNegativeStartMargin() throws Exception {
		assertEquals(List.of(12.0), widths("float: left", "margin-left: -6pt"));
	}

	public void testPaddingAndBorderBox() throws Exception {
		// content-box: 18pt + 3pt padding; border-box: 18pt in all
		assertEquals(List.of(27.0), widths("float: left", "margin-left: 6pt; padding-left: 3pt"));
		assertEquals(List.of(24.0), widths("float: left", "margin-left: 6pt; padding-left: 3pt; box-sizing: border-box"));
	}

	public void testInlineBlock() throws Exception {
		assertEquals(List.of(24.0), widths("display: inline-block", "margin-left: 6pt"));
	}

	/** Border-box widths of the grey boxes holding an 18pt block with {@code margins}. */
	private static List<Double> widths(final String outer, final String margins) throws Exception {
		final Matcher m = GREY.matcher(convert("<div style=\"" + outer + "; height: 7.5pt; background: #ddd\">"
				+ "<div style=\"width: 18pt; height: 6pt; background: #c00; " + margins + "\"></div></div>"));
		final List<Double> w = new ArrayList<>();
		while (m.find()) {
			w.add(Double.parseDouble(m.group(1)));
		}
		return w;
	}

	private static String convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("fixed-width-margin").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 7.5pt } body { margin: 0; font-size: 0 }"
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
