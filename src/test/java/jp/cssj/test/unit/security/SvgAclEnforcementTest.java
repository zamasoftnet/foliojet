package jp.cssj.test.unit.security;

import junit.framework.TestCase;

/**
 * SVGの中からの取得に<b>ACLが効くか</b>を測ります(2026-09-08新設)。
 *
 * <p>
 * {@link SvgFetchReachabilityTest}で、次の4経路が実際に起動することを
 * 測りました。ここではそれぞれに{@code input.include}が効くかを見ます。
 * </p>
 *
 * <p>
 * <b>この試験は、対策前は落ちるのが正しい。</b>設計(第3版)§8の段階1は
 * 「既知の欠陥に対応する試験が落ち、正例が通る」ことを完了条件にしています。
 * </p>
 *
 * <p>
 * <b>主文書は記録用サーバから配ります。</b>{@code file:}から配ると、
 * SVGの中の{@code http:}取得はBatikの既定判定(取得元のホストが文書と
 * 違えば拒む)に先に止められ、FolioJetのACLが効いているのかBatikの既定が
 * 効いているのか区別できません。実運用のサーバでは主文書も{@code http:}
 * なので、既定判定は素通りします。
 * </p>
 */
public class SvgAclEnforcementTest extends TestCase {

	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private ProbeServer probe;

	@Override
	protected void setUp() throws Exception {
		this.probe = new ProbeServer();
	}

	@Override
	protected void tearDown() throws Exception {
		this.probe.close();
	}

	/**
	 * 主文書だけを許し、それ以外を許さないACLで組みます。
	 *
	 * <p>
	 * ACLは<b>先勝ち</b>なので、末尾にexcludeを足しても先のincludeを
	 * 打ち消せません。ここでは主文書のURIだけをincludeします。
	 * </p>
	 */
	private ConversionProbe.Result renderDocumentOnly(final String html) throws Exception {
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		return new ConversionProbe().include(this.probe.url("/doc.html")).convertUrl(this.probe.url("/doc.html"));
	}

	/** 全部許したときは取れること(正例)。ここが落ちたら遮断のしすぎです。 */
	private ConversionProbe.Result renderAll(final String html) throws Exception {
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		return new ConversionProbe().include("**").convertUrl(this.probe.url("/doc.html"));
	}

	private static String cssImportDocument(final String cssUrl) {
		return "<!DOCTYPE html><html><body><svg width='40' height='40'>" + "<style>@import url(\"" + cssUrl
				+ "\");</style>" + "<rect class='p' x='0' y='0' width='40' height='40'/>" + "</svg></body></html>";
	}

	/**
	 * SVGの中のCSSの{@code @import}にACLが効くこと。
	 *
	 * <p>
	 * <b>効かない見込みです。</b>{@code CSSEngine.parseStyleSheet}は
	 * Batikの{@code checkLoadExternalResource}を呼びますが、その判定は
	 * ホストの比較であって、FolioJetの{@code input.include}ではありません。
	 * 主文書と同じホストなら素通りします。
	 * </p>
	 */
	public void testCssImportObeysAcl() throws Exception {
		this.probe.put("/probe.css", "text/css", ".p { fill: #00ff00; }");
		final ConversionProbe.Result r = this.renderDocumentOnly(cssImportDocument(this.probe.url("/probe.css")));
		System.out.println("[CSS @import / ACL] 到達=" + this.probe.hitPaths() + " 緑=" + r.hasColor("rg", 0, 1, 0));
		assertEquals("許していないCSSが取得された。SVGの中にACLが効いていない: " + this.probe.hitPaths(), 0,
				this.probe.hits("/probe.css"));
		assertFalse("許していないCSSが版面に効いた", r.hasColor("rg", 0, 1, 0));
	}

	/** 許したときは取れること(正例)。 */
	public void testCssImportAllowedWhenIncluded() throws Exception {
		this.probe.put("/probe.css", "text/css", ".p { fill: #00ff00; }");
		final ConversionProbe.Result r = this.renderAll(cssImportDocument(this.probe.url("/probe.css")));
		System.out.println("[CSS @import / 許可] 到達=" + this.probe.hitPaths() + " 緑=" + r.hasColor("rg", 0, 1, 0));
		assertTrue("許したCSSが取得されていない: " + this.probe.hitPaths(), this.probe.hits("/probe.css") >= 1);
		assertTrue("許したCSSが版面に効いていない: " + r.describe(), r.hasColor("rg", 0, 1, 0));
	}

	/** SVGの色プロファイルにACLが効くこと。 */
	public void testColorProfileObeysAcl() throws Exception {
		this.probe.put("/probe.icc", "application/vnd.iccprofile", new byte[] { 0, 0, 0, 0 });
		final ConversionProbe.Result r = this.renderDocumentOnly("<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<defs><color-profile name='probe' xlink:href='" + this.probe.url("/probe.icc") + "'/></defs>"
				+ "<rect x='0' y='0' width='40' height='40' fill='#808080 icc-color(probe, 0.5, 0.5, 0.5)'/>"
				+ "</svg></body></html>");
		System.out.println("[色プロファイル / ACL] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertEquals("許していない色プロファイルが取得された: " + this.probe.hitPaths(), 0, this.probe.hits("/probe.icc"));
	}

	/**
	 * 入れ子のSVG文書の中の{@code <image>}にACLが効くこと。
	 *
	 * <p>
	 * 外側のSVGは許し、その中から参照される画像は許しません。
	 * </p>
	 */
	public void testNestedSvgImageObeysAcl() throws Exception {
		this.probe.put("/inner.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
		this.probe.put("/outer.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'"
						+ " width='40' height='40'>"
						+ "<image x='0' y='0' width='40' height='40' xlink:href='" + this.probe.url("/inner.png")
						+ "'/></svg>");
		final String html = "<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<image x='0' y='0' width='40' height='40' xlink:href='" + this.probe.url("/outer.svg")
				+ "'/></svg></body></html>";
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		// 主文書と外側のSVGだけを許す
		final ConversionProbe.Result r = new ConversionProbe().include(this.probe.url("/doc.html"))
				.include(this.probe.url("/outer.svg")).convertUrl(this.probe.url("/doc.html"));
		System.out.println("[入れ子SVG / ACL] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertTrue("許した外側のSVGが取得されていない: " + this.probe.hitPaths(), this.probe.hits("/outer.svg") >= 1);
		assertEquals("許していない入れ子の画像が取得された: " + this.probe.hitPaths(), 0, this.probe.hits("/inner.png"));
	}

	/** 外部{@code <use>}にACLが効くこと。 */
	public void testExternalUseObeysAcl() throws Exception {
		this.probe.put("/used.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'>"
						+ "<rect id='g' x='0' y='0' width='40' height='40' fill='#00ff00'/></svg>");
		final ConversionProbe.Result r = this.renderDocumentOnly("<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<use xlink:href='" + this.probe.url("/used.svg") + "#g'/></svg></body></html>");
		System.out.println("[外部use / ACL] 到達=" + this.probe.hitPaths() + " 緑=" + r.hasColor("rg", 0, 1, 0));
		assertEquals("許していない外部<use>の参照先が取得された: " + this.probe.hitPaths(), 0, this.probe.hits("/used.svg"));
		assertFalse("許していない図形が版面に出た", r.hasColor("rg", 0, 1, 0));
	}

	/** フィルタの{@code <feImage>}にACLが効くこと。 */
	public void testFeImageObeysAcl() throws Exception {
		this.probe.put("/fe.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
		final ConversionProbe.Result r = this.renderDocumentOnly("<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<filter id='f'><feImage xlink:href='" + this.probe.url("/fe.png") + "'/></filter>"
				+ "<rect x='0' y='0' width='40' height='40' filter='url(#f)'/>" + "</svg></body></html>");
		System.out.println("[feImage / ACL] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertEquals("許していないfeImageが取得された: " + this.probe.hitPaths(), 0, this.probe.hits("/fe.png"));
	}

	/** 単独のSVGの{@code <?xml-stylesheet?>}にACLが効くこと。 */
	public void testXmlStylesheetPiObeysAcl() throws Exception {
		this.probe.put("/pi.css", "text/css", "rect { fill: #00ff00; }");
		this.probe.put("/doc.svg", "image/svg+xml",
				"<?xml version='1.0' encoding='UTF-8'?>"
						+ "<?xml-stylesheet type='text/css' href='" + this.probe.url("/pi.css") + "'?>"
						+ "<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'>"
						+ "<rect x='0' y='0' width='40' height='40'/></svg>");
		final ConversionProbe.Result r = new ConversionProbe().include(this.probe.url("/doc.svg"))
				.convertUrl(this.probe.url("/doc.svg"));
		System.out.println("[xml-stylesheet / ACL] 到達=" + this.probe.hitPaths() + " 緑="
				+ r.hasColor("rg", 0, 1, 0));
		assertEquals("許していないxml-stylesheetが取得された: " + this.probe.hitPaths(), 0,
				this.probe.hits("/pi.css"));
		assertFalse("許していないスタイルシートが版面に効いた", r.hasColor("rg", 0, 1, 0));
	}

	/** 別のホストの塗りにACLが効くこと。 */
	public void testExternalPaintObeysAcl() throws Exception {
		this.probe.put("/paint.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg'>"
						+ "<linearGradient id='p'><stop offset='0' stop-color='#00ff00'/>"
						+ "<stop offset='1' stop-color='#00ff00'/></linearGradient></svg>");
		final ConversionProbe.Result r = this.renderDocumentOnly("<!DOCTYPE html><html><body><svg width='40' height='40'>"
				+ "<rect x='0' y='0' width='40' height='40' fill='url(" + this.probe.url("/paint.svg")
				+ "#p)'/></svg></body></html>");
		System.out.println("[外部の塗り / ACL] 到達=" + this.probe.hitPaths());
		assertEquals("許していない外部の塗りが取得された: " + this.probe.hitPaths(), 0, this.probe.hits("/paint.svg"));
	}

	/**
	 * 外部{@code <use>}で参照した文書の<b>中の</b>CSSにACLが効くこと。
	 *
	 * <p>
	 * {@code BridgeContext.getReferencedNode()}は、参照先が別文書のとき
	 * {@code createSubBridgeContext()}を呼びます(同梱jarの位置96で確認)。
	 * {@code MyBridgeContext}はこれを上書きしていないので、
	 * <b>子は素の{@code BridgeContext}になり、標準のCSSエンジンが作られる</b>
	 * はずです。参照先の文書に{@code @import}を置いて、そこにACLが効くかを見ます。
	 * </p>
	 *
	 * <p>
	 * 参照先の文書自体は許し、その中から参照されるCSSは許しません。
	 * </p>
	 */
	public void testExternalUseInnerCssObeysAcl() throws Exception {
		this.probe.put("/inner.css", "text/css", "#g { fill: #00ff00; }");
		this.probe.put("/used.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'>"
						+ "<style>@import url(\"" + this.probe.url("/inner.css") + "\");</style>"
						+ "<rect id='g' x='0' y='0' width='40' height='40'/></svg>");
		final String html = "<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<use xlink:href='" + this.probe.url("/used.svg") + "#g'/></svg></body></html>";
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		final ConversionProbe.Result r = new ConversionProbe().include(this.probe.url("/doc.html"))
				.include(this.probe.url("/used.svg")).convertUrl(this.probe.url("/doc.html"));
		System.out.println("[外部use内のCSS / ACL] 到達=" + this.probe.hitPaths() + " 緑="
				+ r.hasColor("rg", 0, 1, 0) + " " + r.describe());
		assertTrue("許した参照先が取得されていない: " + this.probe.hitPaths(), this.probe.hits("/used.svg") >= 1);
		assertEquals("参照先の中の許していないCSSが取得された: " + this.probe.hitPaths(), 0,
				this.probe.hits("/inner.css"));
		assertFalse("許していないCSSが版面に効いた", r.hasColor("rg", 0, 1, 0));
	}

	/**
	 * 外部{@code <use>}で参照した文書の<b>中の</b>{@code <image>}にACLが効くこと。
	 *
	 * <p>
	 * 子{@code BridgeContext}が素の{@code BridgeContext}になると、
	 * {@code registerSVGBridges()}で差した{@code MySVGImageElementBridge}も
	 * 失われます。CSSだけの問題なのか、画像ブリッジまで失われるのかで、
	 * 対処の範囲が変わります。
	 * </p>
	 */
	public void testExternalUseInnerImageObeysAcl() throws Exception {
		this.probe.put("/inner.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
		this.probe.put("/used.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'"
						+ " width='40' height='40'><g id='g'>"
						+ "<image x='0' y='0' width='40' height='40' xlink:href='" + this.probe.url("/inner.png")
						+ "'/></g></svg>");
		final String html = "<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<use xlink:href='" + this.probe.url("/used.svg") + "#g'/></svg></body></html>";
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		final ConversionProbe.Result r = new ConversionProbe().include(this.probe.url("/doc.html"))
				.include(this.probe.url("/used.svg")).convertUrl(this.probe.url("/doc.html"));
		System.out.println("[外部use内の画像 / ACL] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertTrue("許した参照先が取得されていない: " + this.probe.hitPaths(), this.probe.hits("/used.svg") >= 1);
		assertEquals("参照先の中の許していない画像が取得された: " + this.probe.hitPaths(), 0,
				this.probe.hits("/inner.png"));
	}
}
