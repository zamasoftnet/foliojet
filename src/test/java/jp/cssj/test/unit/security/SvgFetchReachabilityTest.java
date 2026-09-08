package jp.cssj.test.unit.security;

import junit.framework.TestCase;

/**
 * SVGの中からの取得が<b>そもそも起動するか</b>を測ります(2026-09-08新設)。
 *
 * <p>
 * <b>これは遮断の試験ではありません。</b>対策を測る前に、その経路が実際に
 * 動くことを確かめるための試験です。第1版の対策では、SVGの経路を起動できて
 * いないのに「遮断できた」と報告する誤りを犯しました。取得しない実装と、
 * 取得を拒む実装は、外から見ると同じに見えます。
 * </p>
 *
 * <p>
 * Batik 1.19の全1,337クラスを{@code javap}で走査したところ、
 * {@code ParsedURL.openStream*}を直接呼ぶのは7クラスでした。うち
 * スクリプト2つは無効化済み、{@code SVGImageElementBridge}は
 * {@code MySVGImageElementBridge}で差し替え済みです。残る4つが
 * ここで測る対象です。
 * </p>
 *
 * <ul>
 * <li>{@code css.parser.Parser} — CSSの{@code @import}</li>
 * <li>{@code bridge.FontFace} — ウェブフォント</li>
 * <li>{@code bridge.SVGColorProfileElementBridge} — 色プロファイル</li>
 * <li>{@code anim.dom.SAXSVGDocumentFactory} — 外部SVG文書</li>
 * </ul>
 *
 * <p>
 * なお、Batikの子{@code BridgeContext}は<b>FolioJetでは到達しません</b>。
 * 唐突な対策を入れないよう、{@link #testNestedSvgDocumentIsFetched()}の
 * 説明を見てください。
 * </p>
 *
 * <ul>
 * </ul>
 *
 * <p>
 * ここで到達しない経路は、差し替えではなく<b>到達しないことの証明</b>で
 * 済みます。到達する経路だけを差し替えます。
 * </p>
 */
public class SvgFetchReachabilityTest extends TestCase {

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
	 * ACLで全部許した状態で組み、記録用サーバへ何が来たかを見ます。
	 *
	 * <p>
	 * <b>主文書も記録用サーバから配ります。</b>そうしないとBatikの既定判定
	 * (取得元のホストが文書と違えば拒む)に先に止められ、経路が起動して
	 * いるかどうかを測れません。2026-09-08にこれで一度測り損ねました。
	 * </p>
	 */
	private ConversionProbe.Result render(final String html) throws Exception {
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		return new ConversionProbe().include("**").convertUrl(this.probe.url("/doc.html"));
	}

	/**
	 * SVGの中のCSSの{@code @import}が取得されるか。
	 *
	 * <p>
	 * 取得回数だけでなく、<b>取り込んだCSSの色が版面に出たか</b>も見ます。
	 * 取得しただけで適用されないなら、遮断しても版面は変わらず、
	 * 遮断の効果を測れないからです。
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
	 * SVGの色プロファイルが取得されるか。宣言だけでは取得しないので、
	 * {@code icc-color()}で実際に使います。
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
	 * SVGのウェブフォントが取得されるか。
	 *
	 * <p>
	 * {@code MySVGTextElementBridge.getFontList()}はBatikの実装を丸ごと
	 * 上書きしてFolioJetの{@code FontManager}を直接使うので、
	 * <b>Batikの{@code FontFace}経路は起動しない見込み</b>です。
	 * これを測って確かめます。
	 * </p>
	 */
	public void testWebFontIsFetched() throws Exception {
		this.probe.put("/probe.ttf", "font/ttf", new byte[] { 0, 1, 0, 0 });
		final ConversionProbe.Result r = this.render("<!DOCTYPE html><html><body><svg width='200' height='40'>"
				+ "<style>@font-face { font-family: probefont; src: url(\"" + this.probe.url("/probe.ttf")
				+ "\"); }</style>" + "<text x='0' y='20' font-family='probefont' font-size='16'>probe</text>"
				+ "</svg></body></html>");
		System.out.println("[ウェブフォント] 到達=" + this.probe.hitPaths() + " " + r.describe());
		// **実測(2026-09-08): 到達0、警告も無し。** 仮説どおりBatikの
		// FontFace経路は起動しない。したがってこの経路は差し替えではなく
		// 「到達しないことの証明」で済む。将来この行が落ちたら、
		// Batikのフォント経路が生き返ったということなので、設計を見直すこと
		assertEquals("Batikの FontFace 経路が起動するようになった。設計(§4-3)を見直すこと: " + this.probe.hitPaths(), 0,
				this.probe.hits("/probe.ttf"));
	}

	/**
	 * 入れ子のSVG文書({@code <image xlink:href="…​.svg">})の中の{@code <image>}が
	 * 取得されるか。
	 *
	 * <p>
	 * <b>Batikの子{@code BridgeContext}はFolioJetでは到達しません。</b>
	 * {@code createSubBridgeContext()}の呼び出し元は
	 * {@code SVGImageElementBridge.createSVGImageNode()}だけで、その入口である
	 * {@code createImageGraphicsNode()}を{@code MySVGImageElementBridge}が
	 * {@code super}を呼ばずに完全に上書きしているためです(2026-09-08に
	 * 同梱jarを{@code javap}で走査して確認)。入れ子のSVGは
	 * {@code ua.resolve()}→{@code SVGImageLoader}→新しい
	 * {@code MyBridgeContext}という別の道を通ります。
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
	 * 外部{@code <use>}が{@code MyURIResolver}を通るか。
	 *
	 * <p>
	 * Batikの{@code SVGUseElementBridge}は外部文書から取り込んだ描画部分を
	 * <b>親コンテキストで組みます</b>(子コンテキストは作りません)。参照の
	 * 解決は{@code ctx.createURIResolver()}——つまり{@code MyURIResolver}を
	 * 通るはずです。
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
	 * フィルタの{@code <feImage>}が取得されるか。
	 *
	 * <p>
	 * {@code SVGFeImageElementBridge}は{@code ImageTagRegistry.readURL()}を
	 * 呼び、その先で{@code ParsedURL.openStream()}が走ります。<b>FolioJetは
	 * このブリッジを差し替えていません。</b>2026-09-08に同梱の全17 jar・
	 * 1,964クラスを走査して見つけました(はじめは7 jarしか見ておらず、
	 * この経路を見落としていました)。
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
	 * 単独のSVG文書の{@code <?xml-stylesheet?>}が取得されるか。
	 *
	 * <p>
	 * {@code <style>}の中の{@code @import}とは別の入口です(grok 指摘)。
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
	 * 別のホストの塗り({@code fill="url(…#id)"})が取得されるか。
	 *
	 * <p>
	 * 外部の塗りサーバへの参照です。{@code <use>}とは別の入口(grok 指摘)。
	 * </p>
	 */
	public void testExternalPaintIsFetched() throws Exception {
		// **本当に別のサーバ**にする。同じProbeServerを使うと、
		// 「別ホストでも取れる」の証拠にならない(codex 指摘)
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
		// **取得はする。**したがってACLが効くかどうかは意味を持つ
		assertTrue("別サーバの塗りが取得されていない: " + this.paintProbe.hitPaths(),
				this.paintProbe.hits("/paint.svg") >= 1);
		// **ただし版面には出ない**(2026-09-08実測)。外部の塗りサーバへの
		// 参照はこの形では効いていない。「取得されるのに使われない」ので、
		// 遮断の効果を版面では測れない——取得回数だけで判定するしかない。
		// この非対称は記録しておく(将来効くようになったらこの行が落ちる)
		assertFalse("別サーバの塗りが版面に出るようになった。設計(§4-8)を見直すこと: " + r.describe(),
				r.hasColor("rg", 0, 1, 0));
	}

	/**
	 * grok が挙げた残りの経路をまとめて測ります。
	 *
	 * <p>
	 * いずれも<b>起動しないはず</b>ですが、根拠を持たずに「起動しない」と
	 * 書かないために測ります。ACLは全部許した状態で、記録用サーバへ
	 * 来るかどうかだけを見ます。
	 * </p>
	 *
	 * <ul>
	 * <li>{@code cursor: url(…)} — {@code CursorManager}が
	 *     {@code ImageTagRegistry}を呼ぶ。静的な組版では対話が無いので
	 *     起動しないはず</li>
	 * <li>SVGフォントの{@code <font-face-uri>}</li>
	 * <li>{@code <script xlink:href>} — スクリプトは無効化済み</li>
	 * <li>{@code <feImage xlink:href="…#id">} — {@code #}ありの枝。
	 *     {@code createFilter()}は{@code <use>}を合成する</li>
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
		// **#ありの<feImage>は起動しうる。** 到達したら別途ACLの試験が要る
		System.out.println("  feImage(#あり)の到達回数=" + this.probe.hits("/fe2.svg"));
	}

	/** 1x1の緑のPNGです。 */
	public static byte[] onePixelPng() {
		return java.util.Base64.getDecoder()
				.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
	}
}
