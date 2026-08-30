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
 * 画像出力({@code output.type=image/png}等)の契約です(2026-08-02新設)。
 *
 * <p>
 * <b>この出力形式のテストが1つも無かった。</b> 説明書には出力形式として
 * 載っているのに、実際に画像が出るか・寸法や解像度の指定が効くかを
 * 誰も確かめていなかった。
 * </p>
 */
public class ImageOutputTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File DOCUMENT = new File("files/unittest/ioprops/two-pages.html");

	/**
	 * 正体しか持たない書体へ{@code font-weight: bold}を指定した文書。太字は
	 * 輪郭を太らせて作られる({@code FontUtils.drawText}の enlargement 経路)。
	 */
	private static final File SYNTHESIZED_BOLD = new File("files/unittest/ioprops/synthesized-bold.html");

	/** PNGとして出力できること。 */
	public void testPng() throws Exception {
		final File out = this.convert(props("output.type", "image/png",
				"output.page-width", "200pt", "output.page-height", "100pt"));
		assertTrue("PNGとして読めること", isReadableImage(out));
	}

	/** JPEGとして出力できること。 */
	public void testJpeg() throws Exception {
		final File out = this.convert(props("output.type", "image/jpeg",
				"output.page-width", "200pt", "output.page-height", "100pt"));
		assertTrue("JPEGとして読めること", isReadableImage(out));
	}

	/** {@code output.image.resolution}: 解像度が画素数に効くこと。 */
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

	/** 紙面の寸法が画素数へ反映されること。 */
	public void testPageSizeAffectsPixels() throws Exception {
		final File narrow = this.convert(props("output.type", "image/png",
				"output.page-width", "100pt", "output.page-height", "100pt"));
		final File wide = this.convert(props("output.type", "image/png",
				"output.page-width", "200pt", "output.page-height", "100pt"));
		assertTrue("紙面が広いほど画素数が多いこと", width(wide) > width(narrow));
	}

	/**
	 * 擬似ボールドが画像出力で描けること(2026-08-30)。
	 *
	 * <p>
	 * 擬似ボールドは線種と線の色を保存してから塗りつぶし＋線描きへ切り替え、
	 * 終わったら元へ戻す。Java2D出力の{@code G2DGC}は実線の線種を{@code null}で
	 * 返し、線の色も設定前は{@code null}だったので、<b>この復元が必ず落ちて</b>
	 * PNG・JPEG・単一SVGが4001で失敗していた。PDF出力は同じ文書で成功するため、
	 * 出力形式ごとに確かめないと見つからない。
	 * </p>
	 */
	public void testSynthesizedBoldPng() throws Exception {
		final File out = this.convert(SYNTHESIZED_BOLD, props("output.type", "image/png",
				"output.pdf.fonts.policy", "embedded cid-keyed",
				"output.page-width", "300pt", "output.page-height", "300pt"));
		assertTrue("PNGとして読めること", isReadableImage(out));
	}

	/** 擬似ボールドが単一SVG出力(既定=outline、Batik経路)でも描けること。 */
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
