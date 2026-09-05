package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>{@code font-synthesis}</b>を固定します(css-fonts-4、2026-08-20)。
 *
 * <p>
 * 太字・イタリック体を持たないフォント(既定構成の和文フォント)に対し、
 * 既定では疑似ボールド(FILL_STROKE={@code 2 Tr})と疑似イタリック
 * (シアー入り{@code Tm})が入り、{@code font-synthesis: none}で両方
 * 抑止されることをPDF内容ストリームの演算子で検査する。
 * </p>
 */
public class FontSynthesisTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** ページ内容の疑似化マーカーの有無。 */
	private record Synth(boolean strokeBold, boolean shearItalic) {
	}

	private static Synth scan(final PDPage page) throws Exception {
		boolean strokeBold = false, shearItalic = false;
		final PDFStreamParser parser = new PDFStreamParser(page);
		Object token;
		java.util.ArrayList<Object> operands = new java.util.ArrayList<>();
		while ((token = parser.parseNextToken()) != null) {
			if (!(token instanceof Operator op)) {
				operands.add(token);
				continue;
			}
			if ("Tr".equals(op.getName()) && operands.size() >= 1
					&& operands.get(operands.size() - 1) instanceof COSNumber n && n.intValue() == 2) {
				strokeBold = true;
			} else if ("Tm".equals(op.getName()) && operands.size() >= 6) {
				// 横書きの疑似イタリック: [1 0 0.25 1 x y] Tm
				final Object c = operands.get(operands.size() - 4);
				if (c instanceof COSNumber n && Math.abs(n.floatValue() - 0.25f) < 0.001f) {
					shearItalic = true;
				}
			}
			operands.clear();
		}
		return new Synth(strokeBold, shearItalic);
	}

	/**
	 * 半透明(rgba)の塗りでも疑似ボールドが放棄されないことを固定します
	 * (2026-08-20改善。従来はfillAlpha!=1で疑似化ごと放棄され、太字が
	 * 普通の太さで出ていた)。
	 */
	public void testFakeBoldAppliesToTranslucentFill() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/1080-FONT/font-synthesis-alpha.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final Synth p1 = scan(doc.getPage(0));
			assertTrue("半透明塗りで疑似ボールドが放棄されています", p1.strokeBold());
		}
	}

	public void testSynthesisNoneSuppressesFakeBoldAndItalic() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/1080-FONT/font-synthesis.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			assertEquals(2, doc.getNumberOfPages());
			// p1: 既定(auto)——太字疑似化・斜体疑似化が入る(この前提が
			// 崩れたら、テスト構成のフォントに実太字/実イタリックが入った
			// ということなので、フォント指定を単ウェイトのものへ変える)
			final Synth p1 = scan(doc.getPage(0));
			assertTrue("疑似ボールドが入っていません(前提: 実太字なし)", p1.strokeBold());
			assertTrue("疑似イタリックが入っていません(前提: 実イタリックなし)", p1.shearItalic());
			// p2: font-synthesis: none——両方抑止
			final Synth p2 = scan(doc.getPage(1));
			assertFalse("font-synthesis:noneでも疑似ボールドが入っています", p2.strokeBold());
			assertFalse("font-synthesis:noneでも疑似イタリックが入っています", p2.shearItalic());
		}
	}

	/** SVGの900がimg経路でも背景画像経路でも疑似ボールドになります。 */
	public void testSvgFontWeight900IsBoldForImageAndBackground() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/1080-FONT/svg-font-weight.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			assertEquals(2, doc.getNumberOfPages());
			// この前提が崩れたら、テスト構成のserifに実ウェイト900が
			// 入ったため、単ウェイトのフォント指定へ変える。
			assertTrue("img内のfont-weight:900が太字になっていません", scan(doc.getPage(0)).strokeBold());
			assertTrue("背景SVG内のfont-weight:900が太字になっていません", scan(doc.getPage(1)).strokeBold());
		}
	}
}
