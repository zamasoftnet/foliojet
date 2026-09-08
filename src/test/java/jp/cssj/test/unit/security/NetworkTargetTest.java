package jp.cssj.test.unit.security;

import junit.framework.TestCase;

/**
 * 接続先そのものの判定(2026-09-08新設)。
 *
 * <p>
 * ACLは<b>要求したURIのホスト</b>を見ます。実際に繋ぐ先がそれと違うと、
 * 判定と実態が食い違います。第1版の対策が失敗したのと同じ型なので、
 * ここで固定します。
 * </p>
 */
public class NetworkTargetTest extends TestCase {

	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/**
	 * 名前を解決できないホストは、内側とみなして拒むこと。
	 *
	 * <p>
	 * 判定の時刻と接続の時刻は違うので、<b>確かめられないものは通さない</b>。
	 * かつては「取得しても失敗するから」と通していました。
	 * </p>
	 */
	public void testUnresolvableHostIsTreatedAsInternal() throws Exception {
		final ConversionProbe.Result r = new ConversionProbe().include("**").localAccessAllowed(false)
				.convertHtml("<!DOCTYPE html><html><body>"
						+ "<img src='http://no-such-host.invalid/x.png' width='10' height='10'/>"
						+ "</body></html>");
		System.out.println("[名前解決できないホスト] " + r.describe());
		assertTrue("解決できないホストが拒まれていない: " + r.describe(),
				r.deniedResource("2814", "no-such-host.invalid"));
	}

	/** ローカル資源を許した利用者では、同じURIでも拒否にならないこと(正例)。 */
	public void testUnresolvableHostIsNotDeniedWhenLocalAccessAllowed() throws Exception {
		final ConversionProbe.Result r = new ConversionProbe().include("**").localAccessAllowed(true)
				.convertHtml("<!DOCTYPE html><html><body>"
						+ "<img src='http://no-such-host.invalid/x.png' width='10' height='10'/>"
						+ "</body></html>");
		System.out.println("[名前解決できないホスト/許可] " + r.describe());
		assertFalse("ローカル資源を許した利用者にまで拒否が出ている: " + r.describe(),
				r.deniedResource("2814", "no-such-host.invalid"));
	}

	/** 転送で別サーバへ連れ出されたとき、ACLの外なら止めること。 */
	public void testRedirectTargetObeysAcl() throws Exception {
		try (ProbeServer front = new ProbeServer(); ProbeServer inside = new ProbeServer()) {
			inside.put("/inside.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
			front.redirect("/go.png", inside.url("/inside.png"));
			final ConversionProbe.Result r = new ConversionProbe().include(front.url("/go.png"))
					.convertHtml("<!DOCTYPE html><html><body>"
							+ "<img src='" + front.url("/go.png") + "' width='10' height='10'/>"
							+ "</body></html>");
			System.out.println("[転送先のACL] 前=" + front.hitPaths() + " 内=" + inside.hitPaths()
					+ " " + r.describe());
			assertTrue("許した入口へ来ていない: " + front.hitPaths(), front.hits("/go.png") >= 1);
			assertEquals("許していない転送先へ届いた: " + inside.hitPaths(), 0, inside.hits("/inside.png"));
		}
	}

	/** 転送先も許していれば取れること(正例)。遮断しすぎの検出。 */
	public void testRedirectFollowedWhenTargetAllowed() throws Exception {
		try (ProbeServer front = new ProbeServer(); ProbeServer inside = new ProbeServer()) {
			inside.put("/inside.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
			front.redirect("/go.png", inside.url("/inside.png"));
			final ConversionProbe.Result r = new ConversionProbe().include(front.url("/go.png"))
					.include(inside.url("/inside.png"))
					.convertHtml("<!DOCTYPE html><html><body>"
							+ "<img src='" + front.url("/go.png") + "' width='10' height='10'/>"
							+ "</body></html>");
			System.out.println("[転送先を許可] 前=" + front.hitPaths() + " 内=" + inside.hitPaths()
					+ " " + r.describe());
			assertTrue("許した転送先へ届いていない: " + inside.hitPaths(), inside.hits("/inside.png") >= 1);
		}
	}

	/**
	 * ACLを設定していないセッションは、そもそもHTTPを取りに行かないこと。
	 *
	 * <p>
	 * {@code input.include}は<b>httpに対しては常に先行</b>し、既定は
	 * {@code data:}以外を拒否します。だから「ACL未設定＝素通り」ではありません。
	 * 転送の判定を無条件に掛けても壊れないのはこのためですが、<b>それに
	 * 依存しない</b>ように判定は制限時だけ掛けています。
	 * </p>
	 */
	public void testHttpIsNotFetchedWithoutAcl() throws Exception {
		try (ProbeServer front = new ProbeServer(); ProbeServer inside = new ProbeServer()) {
			inside.put("/inside.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
			front.redirect("/go.png", inside.url("/inside.png"));
			// **includeを一度も設定しない。**このときACLの既定は「data:以外は拒否」
			// なので、転送の判定を無条件に掛けるとすべての転送が死ぬ
			final ConversionProbe.Result r = new ConversionProbe()
					.convertHtml("<!DOCTYPE html><html><body>"
							+ "<img src='" + front.url("/go.png") + "' width='10' height='10'/>"
							+ "</body></html>");
			System.out.println("[ACL無し] 前=" + front.hitPaths() + " 内=" + inside.hitPaths()
					+ " " + r.describe());
			assertEquals("ACLを設定していないのにHTTPを取りに行った: " + front.hitPaths(), 0, front.totalHits());
			assertEquals("転送先へも届いていないこと: " + inside.hitPaths(), 0, inside.totalHits());
		}
	}
}
