package jp.cssj.test.unit.security;

import junit.framework.TestCase;

/**
 * Measure whether <b>ACLs affect fetching from inside SVG</b> (introduced on 2026-09-08).
 *
 * <p>
 * {@link SvgFetchReachabilityTest} measured that the following four paths actually activate.
 * Here, check whether {@code input.include} affects each one.
 * </p>
 *
 * <p>
 * <b>Before the countermeasure, these tests should fail.</b> Stage 1 of Design (third version) §8
 * defines completion as tests for known defects failing while positive cases pass.
 * </p>
 *
 * <p>
 * <b>Serve the main document from the recording server.</b> Serving it from {@code file:} causes
 * Batik's default check (reject if the source host differs from the document's) to block
 * {@code http:} fetching inside SVG first, making it impossible to distinguish FolioJet ACL
 * enforcement from Batik's default behavior. Production servers also use {@code http:} for
 * the main document, so the default check passes.
 * </p>
 */
public class SvgAclEnforcementTest extends TestCase {

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
	 * Lay out with an ACL that allows only the main document and nothing else.
	 *
	 * <p>
	 * The ACL is <b>first-match-wins</b>, so appending exclude cannot override an earlier include.
	 * Here, include only the main document URI.
	 * </p>
	 */
	private ConversionProbe.Result renderDocumentOnly(final String html) throws Exception {
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		return new ConversionProbe().include(this.probe.url("/doc.html")).convertUrl(this.probe.url("/doc.html"));
	}

	/** Resources can be fetched when everything is allowed (positive case). Failure means excessive blocking. */
	private ConversionProbe.Result renderAll(final String html) throws Exception {
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		return new ConversionProbe().include("**").convertUrl(this.probe.url("/doc.html"));
	}

	private static String cssImportDocument(final String cssUrl) {
		return "<!DOCTYPE html><html><body><svg width='40' height='40'>" + "<style>@import url(\"" + cssUrl
				+ "\");</style>" + "<rect class='p' x='0' y='0' width='40' height='40'/>" + "</svg></body></html>";
	}

	/**
	 * The ACL applies to CSS {@code @import} inside SVG.
	 *
	 * <p>
	 * <b>This is expected not to work.</b> {@code CSSEngine.parseStyleSheet} calls Batik's
	 * {@code checkLoadExternalResource}, but that check compares hosts rather than applying FolioJet's
	 * {@code input.include}. The same host as the main document passes through.
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

	/** Resources can be fetched when allowed (positive case). */
	public void testCssImportAllowedWhenIncluded() throws Exception {
		this.probe.put("/probe.css", "text/css", ".p { fill: #00ff00; }");
		final ConversionProbe.Result r = this.renderAll(cssImportDocument(this.probe.url("/probe.css")));
		System.out.println("[CSS @import / 許可] 到達=" + this.probe.hitPaths() + " 緑=" + r.hasColor("rg", 0, 1, 0));
		assertTrue("許したCSSが取得されていない: " + this.probe.hitPaths(), this.probe.hits("/probe.css") >= 1);
		assertTrue("許したCSSが版面に効いていない: " + r.describe(), r.hasColor("rg", 0, 1, 0));
	}

	/** The ACL applies to SVG color profiles. */
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
	 * The ACL applies to {@code <image>} inside nested SVG documents.
	 *
	 * <p>
	 * Allow the outer SVG but not the image referenced within it.
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
		// Allow only the main document and the outer SVG.
		final ConversionProbe.Result r = new ConversionProbe().include(this.probe.url("/doc.html"))
				.include(this.probe.url("/outer.svg")).convertUrl(this.probe.url("/doc.html"));
		System.out.println("[入れ子SVG / ACL] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertTrue("許した外側のSVGが取得されていない: " + this.probe.hitPaths(), this.probe.hits("/outer.svg") >= 1);
		assertEquals("許していない入れ子の画像が取得された: " + this.probe.hitPaths(), 0, this.probe.hits("/inner.png"));
	}

	/** The ACL applies to external {@code <use>}. */
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

	/** The ACL applies to filter {@code <feImage>}. */
	public void testFeImageObeysAcl() throws Exception {
		this.probe.put("/fe.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
		final ConversionProbe.Result r = this.renderDocumentOnly("<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<filter id='f'><feImage xlink:href='" + this.probe.url("/fe.png") + "'/></filter>"
				+ "<rect x='0' y='0' width='40' height='40' filter='url(#f)'/>" + "</svg></body></html>");
		System.out.println("[feImage / ACL] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertEquals("許していないfeImageが取得された: " + this.probe.hitPaths(), 0, this.probe.hits("/fe.png"));
	}

	/** The ACL applies to {@code <?xml-stylesheet?>} in standalone SVG. */
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

	/** The ACL applies to paint from another host. */
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
	 * The ACL applies to CSS <b>inside</b> a document referenced by external {@code <use>}.
	 *
	 * <p>
	 * {@code BridgeContext.getReferencedNode()} calls {@code createSubBridgeContext()} when the
	 * target belongs to another document (confirmed at position 96 in the bundled jar).
	 * {@code MyBridgeContext} does not override this, so <b>the child should be a plain
	 * {@code BridgeContext}, creating a standard CSS engine</b>. Put {@code @import} in the
	 * referenced document and check whether the ACL applies there.
	 * </p>
	 *
	 * <p>
	 * Allow the referenced document itself but not the CSS it references.
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
	 * The ACL applies to {@code <image>} <b>inside</b> a document referenced by external {@code <use>}.
	 *
	 * <p>
	 * If the child {@code BridgeContext} becomes a plain {@code BridgeContext}, the
	 * {@code MySVGImageElementBridge} installed through {@code registerSVGBridges()} is also lost.
	 * The scope of the fix depends on whether only CSS is affected or the image bridge is also lost.
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
