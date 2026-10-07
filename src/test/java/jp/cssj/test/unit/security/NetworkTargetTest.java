package jp.cssj.test.unit.security;

import junit.framework.TestCase;

/**
 * Checks of the actual connection destination (introduced on 2026-09-08).
 *
 * <p>
 * The ACL checks <b>the host of the requested URI</b>. If the actual connection goes elsewhere,
 * the decision and reality differ. This is the same failure pattern as the first version
 * of the countermeasure, so fix it here.
 * </p>
 */
public class NetworkTargetTest extends TestCase {

	/**
	 * Treat unresolvable hosts as internal and reject them.
	 *
	 * <p>
	 * Checking and connecting occur at different times, so <b>do not allow what cannot be verified</b>.
	 * Previously, these were allowed on the assumption that fetching would fail anyway.
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

	/** The same URI is not denied for users allowed local resources (positive case). */
	public void testUnresolvableHostIsNotDeniedWhenLocalAccessAllowed() throws Exception {
		final ConversionProbe.Result r = new ConversionProbe().include("**").localAccessAllowed(true)
				.convertHtml("<!DOCTYPE html><html><body>"
						+ "<img src='http://no-such-host.invalid/x.png' width='10' height='10'/>"
						+ "</body></html>");
		System.out.println("[名前解決できないホスト/許可] " + r.describe());
		assertFalse("ローカル資源を許した利用者にまで拒否が出ている: " + r.describe(),
				r.deniedResource("2814", "no-such-host.invalid"));
	}

	/** Stop redirects to another server if the destination is outside the ACL. */
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

	/** An allowed redirect destination can be fetched (positive case). Detect excessive blocking. */
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
	 * Sessions without an ACL do not attempt HTTP fetching at all.
	 *
	 * <p>
	 * {@code input.include} <b>always takes precedence for http</b>, and the default denies everything
	 * except {@code data:}. Thus, an unset ACL does not mean unrestricted access.
	 * This is why unconditional redirect checks would not break this case, but checks are applied
	 * only when restricted to <b>avoid depending on that behavior</b>.
	 * </p>
	 */
	public void testHttpIsNotFetchedWithoutAcl() throws Exception {
		try (ProbeServer front = new ProbeServer(); ProbeServer inside = new ProbeServer()) {
			inside.put("/inside.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
			front.redirect("/go.png", inside.url("/inside.png"));
			// **Never set include.** The ACL then defaults to denying everything except data:,
			// so applying redirect checks unconditionally would block all redirects.
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
