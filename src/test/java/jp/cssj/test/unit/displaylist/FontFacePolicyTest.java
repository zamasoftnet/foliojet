package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * A font the document loads with {@code @font-face} is used whatever the font policy (2026-10-08, EPUB brush-up D-15).
 *
 * <p>
 * Under the default policy (core and CID-keyed fonts) the embedded web font was never a candidate, so the text fell
 * back to Helvetica with no word about the {@code @font-face}. The document's own fonts now rank after the ones the policy
 * selects; a family that only the document provides gets its font, embedded.
 * </p>
 */
public class FontFacePolicyTest extends TestCase {
	private static List<String> fonts(final String fontPolicy) throws Exception {
		return fonts(fontPolicy, "");
	}

	private static List<String> fonts(final String fontPolicy, final String localFirst) throws Exception {
		final String src = new File("files/unittest/1080-FONT/MinionPro-Regular.otf").getAbsoluteFile().toURI().toString();
		final String html = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
				+ "<style>@font-face { font-family: 'own'; src: " + localFirst + "url('" + src + "') }"
				+ " p { font-family: 'own', sans-serif }"
				+ "</style></head><body><p>April is the cruellest month</p></body></html>";
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			if (fontPolicy != null) {
				session.property("output.pdf.fonts.policy", fontPolicy);
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///font-face-policy.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		final List<String> names = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final PDResources resources = doc.getPage(0).getResources();
			for (final COSName name : resources.getFontNames()) {
				final PDFont font = resources.getFont(name);
				names.add(font.getName() + (font.isEmbedded() ? "/embedded" : ""));
			}
		}
		return names;
	}

	public void testDefaultPolicyUsesTheDocumentsFont() throws Exception {
		final List<String> fonts = fonts(null);
		assertEquals(fonts.toString(), 1, fonts.size());
		assertTrue(fonts.toString(), fonts.get(0).contains("MinionPro") && fonts.get(0).endsWith("/embedded"));
	}

	/**
	 * {@code local()} naming a font that is not installed falls through to the next source; Java hands back its Dialog
	 * logical font for any unknown name, which would otherwise set the text.
	 */
	public void testMissingLocalFallsThrough() throws Exception {
		final List<String> fonts = fonts(null, "local('No Such Font 20261008'), ");
		assertTrue(fonts.toString(), fonts.size() == 1 && fonts.get(0).contains("MinionPro"));
	}

	public void testCidKeyedPolicyUsesTheDocumentsFont() throws Exception {
		final List<String> fonts = fonts("cid-keyed");
		assertTrue(fonts.toString(), fonts.size() == 1 && fonts.get(0).contains("MinionPro"));
	}
}
