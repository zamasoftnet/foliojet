package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.css.html.HTMLStyle;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>{@code srcset}/{@code <picture>}の候補選択</b>を固定します
 * (2026-08-20)。
 */
public class SrcsetPictureTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** srcsetは最高解像度候補(印刷向き)。 */
	public void testPickFromSrcset() {
		assertEquals("b.png", HTMLStyle.pickFromSrcset("a.png 1x, b.png 2x"));
		assertEquals("b.png", HTMLStyle.pickFromSrcset("b.png 2x, a.png 1x"));
		assertEquals("wide.png", HTMLStyle.pickFromSrcset("small.png 320w, wide.png 1280w"));
		assertEquals("only.png", HTMLStyle.pickFromSrcset("only.png"));
		assertEquals("a.png", HTMLStyle.pickFromSrcset("a.png"));
		assertNull(HTMLStyle.pickFromSrcset(null));
		assertNull(HTMLStyle.pickFromSrcset(""));
	}

	/** typeフィルタ: 読める形式のみ受け、avif等はスキップ。 */
	public void testSupportedImageType() {
		assertTrue(HTMLStyle.isSupportedImageType(null));
		assertTrue(HTMLStyle.isSupportedImageType("image/webp"));
		assertTrue(HTMLStyle.isSupportedImageType("image/png"));
		assertFalse(HTMLStyle.isSupportedImageType("image/avif"));
		assertFalse(HTMLStyle.isSupportedImageType("image/jxl"));
	}

	/**
	 * {@code <source>}(void要素)が後続内容を飲み込まないことを、
	 * 互換モード(DOCTYPE無し=legacy.xml)で固定します。html4.xml側は
	 * 2026-07-18に是正済みだったが、互換モード側が残っていた
	 * (2026-08-20に是正——srcset/picture対応の検証で実測)。
	 */
	public void testPictureDoesNotSwallowFollowingContent() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/3080-MODERN-CSS/picture-swallow.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		final List<String> texts = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			texts.add(new PDFTextStripper().getText(doc));
		}
		final String all = String.join(" ", texts);
		assertTrue("sourceの後の内容が失われています: " + all, all.contains("AFTERSOURCE"));
		assertTrue("pictureの後の内容が失われています: " + all, all.contains("AFTERPICTURE"));
	}
}
