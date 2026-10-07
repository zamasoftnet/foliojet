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
 * Verify <b>{@code font-synthesis}</b> (css-fonts-4, 2026-08-20).
 *
 * <p>
 * For fonts without bold or italic faces (Japanese fonts in the default configuration),
 * check PDF content-stream operators to verify that synthetic bold (FILL_STROKE={@code 2 Tr})
 * and synthetic italic ({@code Tm} with shear) apply by default,
 * and {@code font-synthesis: none} suppresses both.
 * </p>
 */
public class FontSynthesisTest extends TestCase {
	/** Presence of synthesis markers in page contents. */
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
				// Synthetic italic in horizontal writing: [1 0 0.25 1 x y] Tm.
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
	 * Verify that translucent (rgba) fills still get synthetic bold
	 * (improved on 2026-08-20; previously, fillAlpha!=1 disabled synthesis entirely,
	 * rendering bold text at normal weight).
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
			// p1: Default (auto) enables synthetic bold and italic (if this premise fails,
			// the test font configuration has acquired real bold/italic faces,
			// so switch the specified font to one with a single weight).
			final Synth p1 = scan(doc.getPage(0));
			assertTrue("疑似ボールドが入っていません(前提: 実太字なし)", p1.strokeBold());
			assertTrue("疑似イタリックが入っていません(前提: 実イタリックなし)", p1.shearItalic());
			// p2: font-synthesis: none suppresses both.
			final Synth p2 = scan(doc.getPage(1));
			assertFalse("font-synthesis:noneでも疑似ボールドが入っています", p2.strokeBold());
			assertFalse("font-synthesis:noneでも疑似イタリックが入っています", p2.shearItalic());
		}
	}

	/** SVG weight 900 gets synthetic bold through both img and background-image paths. */
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
			// If this premise fails, the test configuration's serif has acquired a real
			// weight 900, so switch the specified font to one with a single weight.
			assertTrue("img内のfont-weight:900が太字になっていません", scan(doc.getPage(0)).strokeBold());
			assertTrue("背景SVG内のfont-weight:900が太字になっていません", scan(doc.getPage(1)).strokeBold());
		}
	}
}
