package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>{@code clip-path}の基本形状</b>を固定します(css-shapes-1、2026-08-22)。
 *
 * <p>
 * 100pt角の赤いボックスをcircle(30pt at 50pt 50pt)で切り抜き、描画結果を
 * 画素で検査する: 円の中心は赤、円の外(ボックスの四隅)は白。
 * </p>
 */
public class ClipPathTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testCircleClip() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3080-MODERN-CSS/clip-path-circle.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final java.awt.image.BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			// 紙200pt角・margin10pt。box左上は(10,10)、circle(30pt at 50,50)
			// →中心(60,60)は赤、box四隅(15,15)/(105,105)は白(切り抜き)
			assertTrue("円の中心が塗られていません", isRed(img.getRGB(60, 60)));
			assertTrue("円の内側(中心+20pt)が塗られていません", isRed(img.getRGB(60, 80)));
			assertFalse("円の外(ボックス左上)が切り抜かれていません", isRed(img.getRGB(15, 15)));
			assertFalse("円の外(ボックス右下)が切り抜かれていません", isRed(img.getRGB(105, 105)));
			assertFalse("円の外(ボックス右上)が切り抜かれていません", isRed(img.getRGB(105, 15)));
		}
	}

	/**
	 * <b>置換要素({@code <img>})の{@code clip-path}</b>(2026-08-29)。
	 *
	 * <p>
	 * 利用者報告で「divでは効くが{@code <img>}では効かない」と指摘された経路。
	 * 置換要素は{@code AbstractContainerBox}を通らないため、clip-pathが
	 * {@code BlockParams}にしか無く黙って捨てられていた。100pt角の赤い画像を
	 * 同じ円で切り抜き、中心は赤・四隅は白であることを画素で固定する。
	 * </p>
	 */
	public void testCircleClipOnImage() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3080-MODERN-CSS/clip-path-image.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final java.awt.image.BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			assertTrue("円の中心に画像が出ていません", isRed(img.getRGB(60, 60)));
			assertTrue("円の内側(中心+20pt)に画像が出ていません", isRed(img.getRGB(60, 80)));
			assertFalse("円の外(画像の左上)が切り抜かれていません", isRed(img.getRGB(15, 15)));
			assertFalse("円の外(画像の右下)が切り抜かれていません", isRed(img.getRGB(105, 105)));
			assertFalse("円の外(画像の右上)が切り抜かれていません", isRed(img.getRGB(105, 15)));
		}
	}

	private static boolean isRed(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r > 150 && g < 80 && b < 80;
	}
}
