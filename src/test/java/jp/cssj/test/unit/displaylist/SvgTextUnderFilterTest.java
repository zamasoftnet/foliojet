package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Text inside an SVG group with a filter Batik paints into an image of its own, an AWT Graphics2D (feTurbulence and
 * feDisplacementMap; 2026-11-26, a test with local SVG files, copperpdf4 docs/history/2026-11-25-realweb-test.md):
 * the SVG text painter cast it to the PDF bridge, and the whole conversion failed.
 */
public class SvgTextUnderFilterTest extends TestCase {
	public void testBlurredTextConverts() throws Exception {
		final File dir = new File("local/svg-text-under-filter-in");
		dir.mkdirs();
		Files.writeString(new File(dir, "blur.svg").toPath(),
				"<svg xmlns='http://www.w3.org/2000/svg' width='200' height='60'><defs><filter id='f' "
						+ "filterUnits='userSpaceOnUse' x='0' y='0' width='200' height='60'><feTurbulence "
						+ "type='fractalNoise' baseFrequency='1.6' numOctaves='2' seed='7' result='n'/>"
						+ "<feDisplacementMap in='SourceGraphic' in2='n' scale='0.5' xChannelSelector='R' "
						+ "yChannelSelector='G'/></filter></defs><g filter='url(#f)'>"
						+ "<text x='10' y='30' font-size='20'>BLURRED</text></g><text x='10' y='55'>PLAIN</text></svg>",
				StandardCharsets.UTF_8);
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset='utf-8'></head><body><p>BEFORE</p>"
				+ "<img src='blur.svg'><svg xmlns='http://www.w3.org/2000/svg' width='200' height='60'><defs>"
				+ "<filter id='g'><feTurbulence baseFrequency='1.6' result='n'/><feDisplacementMap "
				+ "in='SourceGraphic' in2='n' scale='0.5'/></filter></defs><g filter='url(#g)'>"
				+ "<text x='10' y='30'>INLINE</text></g></svg><p>AFTER</p></body></html>", StandardCharsets.UTF_8);
		final Map<String, double[]> texts = EndTagReopenTest.texts(input.getPath(), "svg-text-under-filter");
		assertTrue(texts.keySet().toString(), texts.containsKey("BEFORE"));
		assertTrue(texts.keySet().toString(), texts.containsKey("AFTER"));
	}

	/**
	 * An SVG part Batik cannot paint leaves the rest of the document (Inkscape's feConvolveMatrix icon: AWT's
	 * ConvolveOp threw "Unable to convolve src image" and the whole conversion failed, 2026-11-26).
	 */
	public void testConvolveMatrixConverts() throws Exception {
		final File dir = new File("local/svg-convolve-in");
		dir.mkdirs();
		Files.writeString(new File(dir, "convolve.svg").toPath(),
				"<svg xmlns='http://www.w3.org/2000/svg' width='48' height='48'><defs><filter id='f'>"
						+ "<feConvolveMatrix order='3 3' kernelMatrix='6 10 3 10 -4 -15 3 -5 -6' in='SourceGraphic' "
						+ "divisor='1.6333333333333333' bias='0' targetX='1' targetY='1'/></filter></defs>"
						+ "<rect x='4' y='4' width='40' height='40' fill='#4a90d9' filter='url(#f)'/></svg>",
				StandardCharsets.UTF_8);
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset='utf-8'></head><body><p>BEFORE</p>"
				+ "<img src='convolve.svg'><p>AFTER</p></body></html>", StandardCharsets.UTF_8);
		final Map<String, double[]> texts = EndTagReopenTest.texts(input.getPath(), "svg-convolve");
		assertTrue(texts.keySet().toString(), texts.containsKey("BEFORE"));
		assertTrue(texts.keySet().toString(), texts.containsKey("AFTER"));
	}
}
