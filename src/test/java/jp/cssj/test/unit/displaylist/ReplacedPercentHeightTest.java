package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * A replaced element's percentage height has a basis only in a containing block of definite height; in one of auto
 * height it counts as auto, and the element takes its height from the ratio (2026-11-26, batch 6 of the real-site
 * tests, BBC Sport: the search icon, an svg of {@code height:100%; width:100%} in a flex item of
 * {@code align-items:flex-start}, took the height of a box of {@code height:100vh} far above it, and the table after
 * the search box went off the page). Expected heights measured in Chrome. Each svg has its own width, 1:1 ratio.
 */
public class ReplacedPercentHeightTest extends TestCase {
	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static String svg(final int width) {
		return "<svg viewBox='0 0 32 32' style='display:block;width:" + width + "pt;height:100%'>"
				+ "<circle cx='16' cy='16' r='14'/></svg>";
	}

	private static String page;

	@Override
	protected void setUp() throws Exception {
		if (page != null) {
			return;
		}
		final File dir = new File("local/replaced-percent-height-in");
		dir.mkdirs();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><style>@page{size:500pt 2000pt;margin:0}"
				+ "body{margin:0} .h{height:300pt}</style></head><body>"
				// in an auto-high block
				+ "<div class='h'><div><span style='display:block'>" + svg(20) + "</span></div></div>"
				// in a flex item that is not stretched
				+ "<div class='h'><div style='display:flex;align-items:flex-start'><span>" + svg(21)
				+ "</span><span>x</span></div></div>"
				// in a stretched flex item of a flex container of auto height
				+ "<div class='h'><div style='display:flex'><span>" + svg(22) + "</span><span>x</span></div></div>"
				// in a box of definite height
				+ "<div class='h'>" + svg(23) + "</div>"
				// in a stretched flex item of a flex container of definite height
				+ "<div style='display:flex;height:200pt'><span>" + svg(24) + "</span><span>x</span></div>"
				// in a block of percentage height in a box of definite height
				+ "<div class='h'><div style='height:50%'>" + svg(25) + "</div></div>"
				// in a block of percentage height in an auto-high block (codex)
				+ "<div class='h'><div><div style='height:50%'>" + svg(26) + "</div></div></div>"
				// in a stretched flex item of a flex container of percentage height of a definite box (codex)
				+ "<div style='height:200pt'><div style='display:flex;height:100%'><span>" + svg(27)
				+ "</span><span>x</span></div></div>"
				+ "</body></html>", StandardCharsets.UTF_8);
		page = String.join("\n", DisplayListPages.of(input.getPath(), "replaced-percent-height"));
	}

	private static double height(final int width) {
		final Matcher m = FRAME.matcher(page);
		while (m.find()) {
			if (Math.abs(Double.parseDouble(m.group(1)) - width) < 0.01) {
				return Double.parseDouble(m.group(2));
			}
		}
		throw new AssertionError("no svg of width " + width + ": " + page);
	}

	public void testAutoHighBlockGivesNoBasis() {
		assertEquals(20, height(20), 0.01);
	}

	public void testUnstretchedFlexItemGivesNoBasis() {
		assertEquals(21, height(21), 0.01);
	}

	public void testFlexItemOfAutoHighContainerGivesNoBasis() {
		assertEquals(22, height(22), 0.01);
	}

	public void testDefiniteBoxIsTheBasis() {
		assertEquals(300, height(23), 0.01);
	}

	public void testStretchedItemOfDefiniteContainerIsTheBasis() {
		assertEquals(200, height(24), 0.01);
	}

	public void testPercentageBlockInDefiniteBoxIsTheBasis() {
		assertEquals(150, height(25), 0.01);
	}

	public void testPercentageBlockInAutoHighBlockGivesNoBasis() {
		assertEquals(26, height(26), 0.01);
	}

	public void testStretchedItemOfPercentageContainerIsTheBasis() {
		assertEquals(200, height(27), 0.01);
	}
}
