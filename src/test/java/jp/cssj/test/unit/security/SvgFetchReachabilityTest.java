package jp.cssj.test.unit.security;

import junit.framework.TestCase;

/**
 * Measure whether fetching from inside SVG <b>activates at all</b> (introduced on 2026-09-08).
 *
 * <p>
 * <b>This is not a blocking test.</b> Before measuring a countermeasure, verify that its path
 * actually runs. With the first countermeasure, we reported successful blocking when the SVG path
 * had never activated. From outside, an implementation that does not fetch looks the same as
 * one that denies fetching.
 * </p>
 *
 * <p>
 * Scanning all 1,337 classes in Batik 1.19 with {@code javap} found seven classes that directly
 * call {@code ParsedURL.openStream*}. Two script classes were already disabled, and
 * {@code SVGImageElementBridge} was already replaced by {@code MySVGImageElementBridge}.
 * The remaining four are measured here.
 * </p>
 *
 * <ul>
 * <li>{@code css.parser.Parser} — CSS {@code @import}</li>
 * <li>{@code bridge.FontFace} — web fonts</li>
 * <li>{@code bridge.SVGColorProfileElementBridge} — color profiles</li>
 * <li>{@code anim.dom.SAXSVGDocumentFactory} — external SVG documents</li>
 * </ul>
 *
 * <p>
 * Batik's child {@code BridgeContext} is <b>unreachable in FolioJet</b>.
 * See {@link #testNestedSvgDocumentIsFetched()} before adding an unwarranted countermeasure.
 * </p>
 *
 * <ul>
 * </ul>
 *
 * <p>
 * Paths not reached here need <b>proof of unreachability</b>, not replacement.
 * Replace only reachable paths.
 * </p>
 */
public class SvgFetchReachabilityTest extends TestCase {

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
	 * Lay out with all access allowed by the ACL and inspect what reaches the recording server.
	 *
	 * <p>
	 * <b>Serve the main document from the recording server too.</b> Otherwise, Batik's default check
	 * (reject if the source host differs from the document's) blocks first, preventing measurement
	 * of whether the path activates. This invalidated one measurement on 2026-09-08.
	 * </p>
	 */
	private ConversionProbe.Result render(final String html) throws Exception {
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		return new ConversionProbe().include("**").convertUrl(this.probe.url("/doc.html"));
	}

	/**
	 * Whether CSS {@code @import} inside SVG is fetched.
	 *
	 * <p>
	 * Check both fetch counts and <b>whether the imported CSS color appears in the type area</b>.
	 * If it is fetched but not applied, blocking does not change the type area, so its effect cannot
	 * be measured there.
	 * </p>
	 */
	public void testCssImportIsFetched() throws Exception {
		this.probe.put("/probe.css", "text/css", ".p { fill: #00ff00; }");
		final ConversionProbe.Result r = this.render("<!DOCTYPE html><html><body><svg width='40' height='40'>"
				+ "<style>@import url(\"" + this.probe.url("/probe.css") + "\");</style>"
				+ "<rect class='p' x='0' y='0' width='40' height='40'/>" + "</svg></body></html>");
		System.out.println("[CSS @import] 到達=" + this.probe.hitPaths() + " 緑=" + r.hasColor("rg", 0, 1, 0) + " "
				+ r.describe());
		assertTrue("CSSの@importが取得されていない。この経路は起動していない: " + this.probe.hitPaths(),
				this.probe.hits("/probe.css") >= 1);
		assertTrue("取得したCSSが版面に効いていない。遮断の効果を測れない: " + r.describe(), r.hasColor("rg", 0, 1, 0));
	}

	/**
	 * Whether an SVG color profile is fetched. A declaration alone does not fetch it,
	 * so actually use it through {@code icc-color()}.
	 */
	public void testColorProfileIsFetched() throws Exception {
		this.probe.put("/probe.icc", "application/vnd.iccprofile", new byte[] { 0, 0, 0, 0 });
		final ConversionProbe.Result r = this.render("<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<defs><color-profile name='probe' xlink:href='" + this.probe.url("/probe.icc") + "'/></defs>"
				+ "<rect x='0' y='0' width='40' height='40' fill='#808080 icc-color(probe, 0.5, 0.5, 0.5)'/>"
				+ "</svg></body></html>");
		System.out.println("[色プロファイル] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertTrue("色プロファイルが取得されていない。この経路は起動していない: " + this.probe.hitPaths(),
				this.probe.hits("/probe.icc") >= 1);
	}

	/**
	 * Whether SVG web fonts are fetched.
	 *
	 * <p>
	 * {@code MySVGTextElementBridge.getFontList()} completely overrides Batik's implementation
	 * and uses FolioJet's {@code FontManager} directly, so <b>Batik's {@code FontFace} path
	 * is expected not to activate</b>. Verify this by measurement.
	 * </p>
	 */
	public void testWebFontIsFetched() throws Exception {
		this.probe.put("/probe.ttf", "font/ttf", new byte[] { 0, 1, 0, 0 });
		final ConversionProbe.Result r = this.render("<!DOCTYPE html><html><body><svg width='200' height='40'>"
				+ "<style>@font-face { font-family: probefont; src: url(\"" + this.probe.url("/probe.ttf")
				+ "\"); }</style>" + "<text x='0' y='20' font-family='probefont' font-size='16'>probe</text>"
				+ "</svg></body></html>");
		System.out.println("[ウェブフォント] 到達=" + this.probe.hitPaths() + " " + r.describe());
		// **Measured (2026-09-08): zero requests, no warnings.** As hypothesized, Batik's
		// FontFace path does not activate. This path therefore needs proof of unreachability,
		// not replacement. If this assertion fails in the future,
		// Batik's font path has become active again; revisit the design.
		assertEquals("Batikの FontFace 経路が起動するようになった。設計(§4-3)を見直すこと: " + this.probe.hitPaths(), 0,
				this.probe.hits("/probe.ttf"));
	}

	/**
	 * Whether {@code <image>} inside a nested SVG document
	 * ({@code <image xlink:href="…​.svg">}) is fetched.
	 *
	 * <p>
	 * <b>Batik's child {@code BridgeContext} is unreachable in FolioJet.</b>
	 * The only caller of {@code createSubBridgeContext()} is
	 * {@code SVGImageElementBridge.createSVGImageNode()}, whose entry point,
	 * {@code createImageGraphicsNode()}, is completely overridden by {@code MySVGImageElementBridge}
	 * without calling {@code super} (confirmed by scanning the bundled jar with {@code javap} on 2026-09-08).
	 * Nested SVG takes a different path:
	 * {@code ua.resolve()}→{@code SVGImageLoader}→a new {@code MyBridgeContext}.
	 * </p>
	 */
	public void testNestedSvgDocumentIsFetched() throws Exception {
		this.probe.put("/inner.png", "image/png", onePixelPng());
		this.probe.put("/outer.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'"
						+ " width='40' height='40'>"
						+ "<image x='0' y='0' width='40' height='40' xlink:href='" + this.probe.url("/inner.png")
						+ "'/></svg>");
		final ConversionProbe.Result r = this.render("<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<image x='0' y='0' width='40' height='40' xlink:href='" + this.probe.url("/outer.svg")
				+ "'/></svg></body></html>");
		System.out.println("[入れ子SVG] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertTrue("外側のSVGが取得されていない: " + this.probe.hitPaths(), this.probe.hits("/outer.svg") >= 1);
		assertTrue("入れ子のSVGの中の<image>が取得されていない。この経路は起動していない: " + this.probe.hitPaths(),
				this.probe.hits("/inner.png") >= 1);
	}

	/**
	 * Whether external {@code <use>} passes through {@code MyURIResolver}.
	 *
	 * <p>
	 * Batik's {@code SVGUseElementBridge} <b>builds drawing imported from external documents
	 * in the parent context</b> (it does not create a child context).
	 * Reference resolution should pass through {@code ctx.createURIResolver()},
	 * i.e., {@code MyURIResolver}.
	 * </p>
	 */
	public void testExternalUseIsFetched() throws Exception {
		this.probe.put("/used.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'>"
						+ "<rect id='g' x='0' y='0' width='40' height='40' fill='#00ff00'/></svg>");
		final ConversionProbe.Result r = this.render("<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<use xlink:href='" + this.probe.url("/used.svg") + "#g'/></svg></body></html>");
		System.out.println("[外部use] 到達=" + this.probe.hitPaths() + " 緑=" + r.hasColor("rg", 0, 1, 0) + " "
				+ r.describe());
		assertTrue("外部<use>の参照先が取得されていない。この経路は起動していない: " + this.probe.hitPaths(),
				this.probe.hits("/used.svg") >= 1);
		assertTrue("取り込んだ図形が版面に出ていない: " + r.describe(), r.hasColor("rg", 0, 1, 0));
	}

	/**
	 * Whether filter {@code <feImage>} is fetched.
	 *
	 * <p>
	 * {@code SVGFeImageElementBridge} calls {@code ImageTagRegistry.readURL()},
	 * which then calls {@code ParsedURL.openStream()}.
	 * <b>FolioJet does not replace this bridge.</b> It was found by scanning all 17 bundled jars
	 * and 1,964 classes on 2026-09-08 (the initial scan covered only seven jars and missed this path).
	 * </p>
	 */
	public void testFeImageIsFetched() throws Exception {
		this.probe.put("/fe.png", "image/png", onePixelPng());
		final ConversionProbe.Result r = this.render("<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<filter id='f'><feImage xlink:href='" + this.probe.url("/fe.png") + "'/></filter>"
				+ "<rect x='0' y='0' width='40' height='40' filter='url(#f)'/>" + "</svg></body></html>");
		System.out.println("[feImage] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertTrue("feImageが取得されていない。この経路は起動していない: " + this.probe.hitPaths(),
				this.probe.hits("/fe.png") >= 1);
	}

	/**
	 * Whether {@code <?xml-stylesheet?>} in a standalone SVG document is fetched.
	 *
	 * <p>
	 * This is a separate entry point from {@code @import} inside {@code <style>} (noted by grok).
	 * </p>
	 */
	public void testXmlStylesheetPiIsFetched() throws Exception {
		this.probe.put("/pi.css", "text/css", "rect { fill: #00ff00; }");
		this.probe.put("/doc.svg", "image/svg+xml",
				"<?xml version='1.0' encoding='UTF-8'?>"
						+ "<?xml-stylesheet type='text/css' href='" + this.probe.url("/pi.css") + "'?>"
						+ "<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'>"
						+ "<rect x='0' y='0' width='40' height='40'/></svg>");
		final ConversionProbe.Result r = new ConversionProbe().include("**")
				.convertUrl(this.probe.url("/doc.svg"));
		System.out.println("[xml-stylesheet] 到達=" + this.probe.hitPaths() + " 緑=" + r.hasColor("rg", 0, 1, 0)
				+ " " + r.describe());
		assertTrue("xml-stylesheetが取得されていない: " + this.probe.hitPaths(), this.probe.hits("/pi.css") >= 1);
	}

	/**
	 * Whether paint from another host ({@code fill="url(…#id)"}) is fetched.
	 *
	 * <p>
	 * This references an external paint server, a separate entry point from {@code <use>} (noted by grok).
	 * </p>
	 */
	public void testExternalPaintIsFetched() throws Exception {
		// Use **a genuinely separate server**. Reusing the same ProbeServer
		// does not prove that a different host can be fetched (noted by codex).
		try (ProbeServer other = new ProbeServer()) {
			this.paintProbe = other;
			this.doTestExternalPaint();
		}
	}

	private ProbeServer paintProbe;

	private void doTestExternalPaint() throws Exception {
		this.paintProbe.put("/paint.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg'>"
						+ "<linearGradient id='p'><stop offset='0' stop-color='#00ff00'/>"
						+ "<stop offset='1' stop-color='#00ff00'/></linearGradient></svg>");
		final ConversionProbe.Result r = this.render("<!DOCTYPE html><html><body><svg width='40' height='40'>"
				+ "<rect x='0' y='0' width='40' height='40' fill='url(" + this.paintProbe.url("/paint.svg")
				+ "#p)'/></svg></body></html>");
		System.out.println("[別サーバの塗り] 主文書側=" + this.probe.hitPaths() + " 別サーバ側="
				+ this.paintProbe.hitPaths() + " 緑=" + r.hasColor("rg", 0, 1, 0) + " " + r.describe());
		// **It is fetched.** Thus, whether the ACL applies is meaningful.
		assertTrue("別サーバの塗りが取得されていない: " + this.paintProbe.hitPaths(),
				this.paintProbe.hits("/paint.svg") >= 1);
		// **However, it does not appear in the type area** (measured on 2026-09-08). The external paint-server
		// reference has no effect in this form. Since it is fetched but unused,
		// blocking cannot be measured in the type area; only fetch counts can determine it.
		// Record this asymmetry (this assertion fails if it starts taking effect in the future).
		assertFalse("別サーバの塗りが版面に出るようになった。設計(§4-8)を見直すこと: " + r.describe(),
				r.hasColor("rg", 0, 1, 0));
	}

	/**
	 * Measure the remaining paths identified by grok together.
	 *
	 * <p>
	 * All are <b>expected not to activate</b>, but measure them rather than asserting unreachability
	 * without evidence. Allow everything in the ACL and check only whether requests reach the recording server.
	 * </p>
	 *
	 * <ul>
	 * <li>{@code cursor: url(…)} — {@code CursorManager} calls {@code ImageTagRegistry}.
	 *     Static layout has no interaction, so this should not activate.</li>
	 * <li>{@code <font-face-uri>} in SVG fonts</li>
	 * <li>{@code <script xlink:href>} — scripts are already disabled</li>
	 * <li>{@code <feImage xlink:href="…#id">} — the branch with {@code #}.
	 *     {@code createFilter()} synthesizes {@code <use>}.</li>
	 * </ul>
	 */
	public void testRemainingPathsAreNotFetched() throws Exception {
		this.probe.put("/cursor.png", "image/png", onePixelPng());
		this.probe.put("/svgfont.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg'><font id='f'/></svg>");
		this.probe.put("/script.js", "text/javascript", "var x = 1;");
		this.probe.put("/fe2.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg'>"
						+ "<rect id='r' x='0' y='0' width='40' height='40' fill='#00ff00'/></svg>");
		final ConversionProbe.Result r = this.render("<!DOCTYPE html><html><body>"
				+ "<svg xmlns:xlink='http://www.w3.org/1999/xlink' width='40' height='40'>"
				+ "<style>@font-face { font-family: sf; src: url(\"" + this.probe.url("/svgfont.svg")
				+ "\") format(\"svg\"); }</style>"
				+ "<script xlink:href='" + this.probe.url("/script.js") + "'/>"
				+ "<filter id='f2'><feImage xlink:href='" + this.probe.url("/fe2.svg") + "#r'/></filter>"
				+ "<rect x='0' y='0' width='40' height='40' filter='url(#f2)'"
				+ " style='cursor: url(" + this.probe.url("/cursor.png") + "), auto'/>"
				+ "</svg></body></html>");
		System.out.println("[残りの経路] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertEquals("cursorのurl()が取得された。設計を見直すこと", 0, this.probe.hits("/cursor.png"));
		assertEquals("SVGフォントのfont-face-uriが取得された。設計を見直すこと", 0, this.probe.hits("/svgfont.svg"));
		assertEquals("<script href>が取得された。スクリプトは無効化されているはず", 0, this.probe.hits("/script.js"));
		// **<feImage> with # can activate.** If reached, it needs a separate ACL test.
		System.out.println("  feImage(#あり)の到達回数=" + this.probe.hits("/fe2.svg"));
	}

	/** A green 1x1 PNG. */
	public static byte[] onePixelPng() {
		return java.util.Base64.getDecoder()
				.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
	}
}
