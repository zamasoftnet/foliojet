package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import junit.framework.TestCase;

/**
 * SVG 2's {@code href} in an SVG image and in an inline SVG converts (2026-11-26, net.zamasoft.foliojet.ua.impl.svg
 * .SvgCompat, called by SVGImageLoader): Batik failed the whole SVG at a {@code <use href>}, which then took the
 * broken image's place (a 40pt box) instead of its own size.
 */
public class SvgCompatInConversionTest extends TestCase {
	public void testUseHrefConverts() throws Exception {
		final File dir = new File("local/svg-compat-in-conversion-in");
		dir.mkdirs();
		final String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='80' height='80'><defs>"
				+ "<rect id='r' width='80' height='80' fill='rgba(255,0,0,.5)' stroke='transparent'/></defs>"
				+ "<use href='#r'/></svg>";
		Files.writeString(new File(dir, "use.svg").toPath(), svg, StandardCharsets.UTF_8);
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><body style='margin:0'><p>BEFORE</p>"
				+ "<img src='use.svg' alt=''><div>" + svg.replace("'r'", "'r2'").replace("#r'", "#r2'")
				+ "</div><p>AFTER</p></body></html>", StandardCharsets.UTF_8);
		final List<String> pages = DisplayListPages.of(input.getPath(), "svg-compat-in-conversion");
		final String all = String.join("\n", pages);
		// 80px = 60pt, the image and the inline SVG; a failed SVG is a 40pt box
		assertEquals(all, 2, all.split("AbsoluteRectFrame\\[w=60\\.00 h=60\\.00\\]", -1).length - 1);
		assertFalse(all, all.contains("w=40.00 h=40.00"));
	}
}
