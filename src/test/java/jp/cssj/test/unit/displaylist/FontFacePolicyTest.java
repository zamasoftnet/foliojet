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
 *
 * <p>
 * The families of {@code font-family} are taken in their order, the document's faces of a family before the installed
 * fonts of that family (2026-10-08): every document face used to come before every installed family, so an installed
 * family named first lost to the document's family named after it. Under the cid-identity policy the document's font
 * was written without embedding.
 * </p>
 */
public class FontFacePolicyTest extends TestCase {
	private static List<String> fonts(final String fontPolicy) throws Exception {
		return fonts(fontPolicy, "");
	}

	private static List<String> fonts(final String fontPolicy, final String localFirst) throws Exception {
		return fonts(fontPolicy, localFirst, "'own', sans-serif");
	}

	private static List<String> fonts(final String fontPolicy, final String localFirst, final String families)
			throws Exception {
		final String src = new File("files/unittest/1080-FONT/MinionPro-Regular.otf").getAbsoluteFile().toURI().toString();
		final String html = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
				+ "<style>@font-face { font-family: 'own'; src: " + localFirst + "url('" + src + "') }"
				+ " p { font-family: " + families + " }"
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

	/**
	 * {@code local("")} (google-webfonts-helper writes it first) names no font: it is passed over, not taken as a bad
	 * value that drops the whole {@code src} (2026-10-09, smolcss set in Helvetica; Chrome uses the url()).
	 */
	public void testEmptyLocalIsSkipped() throws Exception {
		for (final String local : new String[] { "local(\"\"), ", "local(''), " }) {
			final List<String> fonts = fonts(null, local);
			assertTrue(local + fonts, fonts.size() == 1 && fonts.get(0).contains("MinionPro"));
		}
	}

	public void testCidKeyedPolicyUsesTheDocumentsFont() throws Exception {
		final List<String> fonts = fonts("cid-keyed");
		assertTrue(fonts.toString(), fonts.size() == 1 && fonts.get(0).contains("MinionPro"));
	}

	public void testCidIdentityPolicyEmbedsTheDocumentsFont() throws Exception {
		final List<String> fonts = fonts("cid-identity");
		assertTrue(fonts.toString(), fonts.size() == 1 && fonts.get(0).contains("MinionPro")
				&& fonts.get(0).endsWith("/embedded"));
	}

	/**
	 * A face the document declares and never uses is not fetched (2026-10-08): its sources are tried only when a font
	 * style names the family, so an unreadable source is reported for a used face only. wordpress-docs imports a CJK
	 * web font in 61 unicode-range files and never uses it; reading them all doubled its time.
	 */
	public void testUnusedFaceIsNotFetched() throws Exception {
		final String html = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
				+ "<style>@font-face { font-family: 'unused'; src: local('No Such Font 20261008'),"
				+ " url('file:///no/such/unused-20261008.woff2') }"
				+ " @font-face { font-family: 'used'; src: local('No Such Font 20261008'),"
				+ " url('file:///no/such/used-20261008.woff2') }"
				+ " p { font-family: 'used', serif }</style></head><body><p>April</p><p>is the cruellest month</p>"
				+ "</body></html>";
		final List<String> messages = new ArrayList<>();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler((code, args, message) -> messages.add(Integer.toHexString(code) + " " + message));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///font-face-unused.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		final List<String> missing = messages.stream().filter(m -> m.startsWith("281e ")).toList();
		assertEquals("読めない書体の警告は使った face の分だけ: " + messages, 1, missing.size());
		assertTrue(missing.get(0), missing.get(0).contains("used-20261008") && !missing.get(0).contains("unused"));
	}

	public void testInstalledFamilyNamedFirstWins() throws Exception {
		final List<String> fonts = fonts(null, "", "Helvetica, 'own'");
		assertEquals(fonts.toString(), 1, fonts.size());
		assertTrue("先に書いた Helvetica が字を持つ: " + fonts, fonts.get(0).contains("Helvetica"));
	}
}
