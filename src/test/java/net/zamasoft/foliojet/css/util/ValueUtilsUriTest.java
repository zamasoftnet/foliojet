package net.zamasoft.foliojet.css.util;

import java.net.URI;
import java.net.URISyntaxException;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.URIValue;

/**
 * background-image等の{@code url()}に、SVGアイコンをdata:で埋め込む際に実物の
 * サイトで頻出する「リテラル空白混じり・非base64」の形が、file://を基底URIと
 * する変換(ローカルHTML変換やインライン&lt;style&gt;)で消えないことの確認
 * (2026-08-06、yahoo.co.jpのヘッダーアイコンで発覚)。
 *
 * <p>
 * {@code net.zamasoft.zstream.resolver.util.URIHelper.resolve()}の不正文字
 * サニタイズはbaseURIがhttp/httpsの時にしか効かない外部ライブラリの挙動——
 * file://基底では{@code new URI(...)}がリテラル空白で{@link URISyntaxException}
 * を投げ、呼び出し元({@code BackgroundImage}等)がそれを握りつぶして
 * background-imageごと消していた。{@link ValueUtils#createURIValue}側で
 * 例外時に最小限の不正文字を%エンコードして再試行するようにした。
 * </p>
 */
public class ValueUtilsUriTest extends TestCase {
	private static final URI FILE_BASE = URI.create("file:///tmp/example.html");
	private static final URI HTTP_BASE = URI.create("https://example.com/style.css");

	/** 実物のyahoo.co.jpのトラベルアイコンと同じ形(属性間はリテラル空白、値はリテラル単引用符)。 */
	private static final String LITERAL_SPACE_SVG_DATA_URI = "data:image/svg+xml;charset=utf-8,%3Csvg width='80' height='80' xmlns='http://www.w3.org/2000/svg'%3E%3Crect fill='%23C73700' width='80' height='80'/%3E%3C/svg%3E";

	public void testFileBaseWithLiteralSpaceDataUriNoLongerThrows() throws URISyntaxException {
		URIValue value = ValueUtils.createURIValue("UTF-8", FILE_BASE, LITERAL_SPACE_SVG_DATA_URI);
		assertNotNull(value);
		URI resolved = value.getURI();
		assertEquals("data", resolved.getScheme());
	}

	public void testFileBaseSanitizedUriDecodesBackToOriginalSvg()
			throws URISyntaxException, java.io.UnsupportedEncodingException {
		// %エンコードは可逆でなければならない。デコードしたら元のSVGテキストに戻ること
		URIValue value = ValueUtils.createURIValue("UTF-8", FILE_BASE, LITERAL_SPACE_SVG_DATA_URI);
		String decoded = java.net.URLDecoder.decode(value.getURI().getSchemeSpecificPart(), "UTF-8");
		assertTrue(decoded.contains("<svg width='80' height='80'"));
		assertTrue(decoded.contains("fill='#C73700'"));
	}

	/** http/https基底では従来通りURIHelper自身のサニタイズで通る(退行確認)。 */
	public void testHttpBaseWithLiteralSpaceDataUriStillWorks() throws URISyntaxException {
		URIValue value = ValueUtils.createURIValue("UTF-8", HTTP_BASE, LITERAL_SPACE_SVG_DATA_URI);
		assertNotNull(value);
		assertEquals("data", value.getURI().getScheme());
	}

	/** 既に正しくbase64/%エンコード済みのURIは今まで通り一発で解決できる(再試行を経由しない)。 */
	public void testAlreadyValidUriUnaffected() throws URISyntaxException {
		String base64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4MCIgaGVpZ2h0PSI4MCIvPg==";
		URIValue value = ValueUtils.createURIValue("UTF-8", FILE_BASE, base64);
		assertNotNull(value);
		assertEquals("data", value.getURI().getScheme());
	}

	/** サニタイズしても直らない(サニタイズが実質no-opな)不正値は、従来通り例外のまま。 */
	public void testStillInvalidAfterSanitizeRethrowsOriginal() {
		try {
			// スキーム名自体が空白を含み、サニタイズしても構造的に直らない
			ValueUtils.createURIValue("UTF-8", FILE_BASE, "ht tp://[invalid");
			fail("URISyntaxExceptionを期待した");
		} catch (URISyntaxException expected) {
			// OK
		}
	}
}
