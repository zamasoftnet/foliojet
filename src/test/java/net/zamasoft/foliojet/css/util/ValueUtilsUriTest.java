package net.zamasoft.foliojet.css.util;

import java.net.URI;
import java.net.URISyntaxException;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.URIValue;

/**
 * Check that SVG icons embedded as data: in {@code url()} (background-image, etc.) in the
 * literal-space, non-base64 form common on real sites do not disappear during conversion with
 * a file:// base URI (local HTML conversion or inline &lt;style&gt;)
 * (2026-08-06, discovered in yahoo.co.jp header icons).
 *
 * <p>
 * The external library {@code net.zamasoft.zstream.resolver.util.URIHelper.resolve()} sanitizes
 * invalid characters only when baseURI is http/https. With a file:// base,
 * {@code new URI(...)} throws {@link URISyntaxException} on literal spaces, which callers
 * ({@code BackgroundImage}, etc.) swallowed, dropping the entire background-image.
 * {@link ValueUtils#createURIValue} now retries on exception after percent-encoding the minimum
 * set of invalid characters.
 * </p>
 */
public class ValueUtilsUriTest extends TestCase {
	private static final URI FILE_BASE = URI.create("file:///tmp/example.html");
	private static final URI HTTP_BASE = URI.create("https://example.com/style.css");

	/**
	 * Same form as the actual yahoo.co.jp travel icon (literal spaces between attributes, literal single quotes
	 * around values).
	 */
	private static final String LITERAL_SPACE_SVG_DATA_URI = "data:image/svg+xml;charset=utf-8,%3Csvg width='80' height='80' xmlns='http://www.w3.org/2000/svg'%3E%3Crect fill='%23C73700' width='80' height='80'/%3E%3C/svg%3E";

	public void testFileBaseWithLiteralSpaceDataUriNoLongerThrows() throws URISyntaxException {
		URIValue value = ValueUtils.createURIValue("UTF-8", FILE_BASE, LITERAL_SPACE_SVG_DATA_URI);
		assertNotNull(value);
		URI resolved = value.getURI();
		assertEquals("data", resolved.getScheme());
	}

	public void testFileBaseSanitizedUriDecodesBackToOriginalSvg()
			throws URISyntaxException, java.io.UnsupportedEncodingException {
		// Percent-encoding must be reversible. Decoding restores the original SVG text.
		URIValue value = ValueUtils.createURIValue("UTF-8", FILE_BASE, LITERAL_SPACE_SVG_DATA_URI);
		String decoded = java.net.URLDecoder.decode(value.getURI().getSchemeSpecificPart(), "UTF-8");
		assertTrue(decoded.contains("<svg width='80' height='80'"));
		assertTrue(decoded.contains("fill='#C73700'"));
	}

	/** With an http/https base, URIHelper's own sanitization still succeeds (regression check). */
	public void testHttpBaseWithLiteralSpaceDataUriStillWorks() throws URISyntaxException {
		URIValue value = ValueUtils.createURIValue("UTF-8", HTTP_BASE, LITERAL_SPACE_SVG_DATA_URI);
		assertNotNull(value);
		assertEquals("data", value.getURI().getScheme());
	}

	/** Already valid base64/percent-encoded URIs still resolve on the first attempt (no retry). */
	public void testAlreadyValidUriUnaffected() throws URISyntaxException {
		String base64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4MCIgaGVpZ2h0PSI4MCIvPg==";
		URIValue value = ValueUtils.createURIValue("UTF-8", FILE_BASE, base64);
		assertNotNull(value);
		assertEquals("data", value.getURI().getScheme());
	}

	/** Invalid values that sanitization cannot fix (sanitization is effectively a no-op) still throw exceptions. */
	public void testStillInvalidAfterSanitizeRethrowsOriginal() {
		try {
			// The scheme name itself contains a space, so sanitization cannot fix its structure.
			ValueUtils.createURIValue("UTF-8", FILE_BASE, "ht tp://[invalid");
			fail("URISyntaxExceptionを期待した");
		} catch (URISyntaxException expected) {
			// OK
		}
	}
}
