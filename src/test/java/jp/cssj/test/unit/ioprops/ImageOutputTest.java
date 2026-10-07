package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Contract for image output ({@code output.type=image/png}, etc.) (introduced on 2026-08-02).
 *
 * <p>
 * <b>There were no tests for this output format.</b> Although the manual listed it as an output format,
 * nobody had checked whether images were actually emitted or whether dimensions and resolution took effect.
 * </p>
 */
public class ImageOutputTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File DOCUMENT = new File("files/unittest/ioprops/two-pages.html");

	/**
	 * A document specifying {@code font-weight: bold} for a font with only a regular face.
	 * Bold is synthesized by thickening outlines (the enlargement path in {@code FontUtils.drawText}).
	 */
	private static final File SYNTHESIZED_BOLD = new File("files/unittest/ioprops/synthesized-bold.html");

	/** PNG output works. */
	public void testPng() throws Exception {
		final File out = this.convert(props("output.type", "image/png",
				"output.page-width", "200pt", "output.page-height", "100pt"));
		assertTrue("PNGとして読めること", isReadableImage(out));
	}

	/** JPEG output works. */
	public void testJpeg() throws Exception {
		final File out = this.convert(props("output.type", "image/jpeg",
				"output.page-width", "200pt", "output.page-height", "100pt"));
		assertTrue("JPEGとして読めること", isReadableImage(out));
	}

	/** {@code output.image.resolution}: resolution affects pixel count. */
	public void testImageResolution() throws Exception {
		final File low = this.convert(props("output.type", "image/png",
				"output.page-width", "100pt", "output.page-height", "100pt",
				"output.image.resolution", "72"));
		final int lowWidth = width(low);
		final File high = this.convert(props("output.type", "image/png",
				"output.page-width", "100pt", "output.page-height", "100pt",
				"output.image.resolution", "144"));
		final int highWidth = width(high);
		assertTrue("解像度を上げると画素数が増えること(" + lowWidth + " → " + highWidth + ")",
				highWidth > lowWidth);
	}

	/** Page dimensions are reflected in pixel count. */
	public void testPageSizeAffectsPixels() throws Exception {
		final File narrow = this.convert(props("output.type", "image/png",
				"output.page-width", "100pt", "output.page-height", "100pt"));
		final File wide = this.convert(props("output.type", "image/png",
				"output.page-width", "200pt", "output.page-height", "100pt"));
		assertTrue("紙面が広いほど画素数が多いこと", width(wide) > width(narrow));
	}

	/**
	 * Synthetic bold renders in image output (2026-08-30).
	 *
	 * <p>
	 * Synthetic bold saves the stroke style and color before switching to fill plus stroke,
	 * then restores them afterward. Java2D output's {@code G2DGC} returned {@code null} for a solid
	 * stroke style and {@code null} for the stroke color before initialization, so <b>restoration always failed</b>,
	 * causing PNG, JPEG, and single SVG to fail with 4001. PDF output succeeded with the same document,
	 * so this could only be found by checking each output format.
	 * </p>
	 */
	public void testSynthesizedBoldPng() throws Exception {
		final File out = this.convert(SYNTHESIZED_BOLD, props("output.type", "image/png",
				"output.pdf.fonts.policy", "embedded cid-keyed",
				"output.page-width", "300pt", "output.page-height", "300pt"));
		assertTrue("PNGとして読めること", isReadableImage(out));
	}

	/** Synthetic bold also renders in single SVG output (default=outline, Batik path). */
	public void testSynthesizedBoldSvg() throws Exception {
		final File out = this.convert(SYNTHESIZED_BOLD, props("output.type", "image/svg+xml",
				"output.pdf.fonts.policy", "embedded cid-keyed",
				"output.page-width", "300pt", "output.page-height", "300pt"));
		final String svg = Files.readString(out.toPath());
		assertTrue("SVGとして書き出されていること", svg.contains("<svg"));
	}

	private static boolean isReadableImage(final File file) throws Exception {
		return file.isFile() && file.length() > 0 && ImageIO.read(file) != null;
	}

	private static int width(final File file) throws Exception {
		final var image = ImageIO.read(file);
		assertNotNull("画像として読めること", image);
		return image.getWidth();
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private File convert(final Map<String, String> properties) throws Exception {
		return this.convert(DOCUMENT, properties);
	}

	private File convert(final File document, final Map<String, String> properties) throws Exception {
		final File out = File.createTempFile("image-output", ".bin");
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} finally {
				session.close();
			}
		}
		assertTrue("出力が空でないこと", Files.size(out.toPath()) > 0);
		return out;
	}
}
