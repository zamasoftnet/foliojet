package jp.cssj.test.unit.security;

import jp.cssj.cti2.TranscoderException;
import junit.framework.TestCase;
import net.zamasoft.foliojet.message.MessageCodes;

/**
 * The proxy decides where a connection goes, so it is checked like a destination.
 *
 * <p>
 * A user without permission to access local resources is kept away from loopback and private addresses by a check
 * of the host in the request URI. With {@code input.http.proxy.host}, which the client sets, every request goes to
 * the proxy instead: a public URI sent to a "proxy" on the server's own network is a request to that inside
 * address, and most HTTP servers answer it with their own content.
 * </p>
 *
 * <p>
 * The recording server stands for the inside service. The requested host is a literal public address, so no name
 * is looked up, and with a proxy set nothing is ever sent to it.
 * </p>
 */
public class ProxyTargetTest extends TestCase {
	private static final String PUBLIC = "http://203.0.113.10";

	private static final String HTML = "<!DOCTYPE html><html><body><img src='" + PUBLIC
			+ "/x.png' width='10' height='10'/></body></html>";

	private static ConversionProbe throughProxy(final ProbeServer proxy, final boolean localAccess) {
		final String base = proxy.base();
		return new ConversionProbe().include("**").localAccessAllowed(localAccess)
				.property("input.http.proxy.host", "127.0.0.1")
				.property("input.http.proxy.port", base.substring(base.lastIndexOf(':') + 1));
	}

	/** A resource of the document: refused and reported as blocked, and no request reaches the inside service. */
	public void testProxyOnTheServersNetworkIsRefusedWithoutLocalAccess() throws Exception {
		try (ProbeServer inside = new ProbeServer()) {
			inside.put("/x.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
			final ConversionProbe.Result r = throughProxy(inside, false).convertHtml(HTML);
			assertEquals("a request went to the proxy: " + inside.hitPaths(), 0, inside.totalHits());
			assertTrue("not reported as blocked: " + r.describe(), r.deniedResource("2814", "203.0.113.10"));
		}
	}

	/** The same conversion for a user who may access local resources goes through the proxy (the path is live). */
	public void testProxyOnTheServersNetworkIsUsedWithLocalAccess() throws Exception {
		try (ProbeServer inside = new ProbeServer()) {
			inside.put("/x.png", "image/png", SvgFetchReachabilityTest.onePixelPng());
			final ConversionProbe.Result r = throughProxy(inside, true).convertHtml(HTML);
			assertTrue("no request went to the proxy: " + r.describe(), inside.hits("/x.png") >= 1);
			assertFalse("reported as blocked: " + r.describe(), r.deniedResource("2814", "203.0.113.10"));
		}
	}

	/** The main document: the conversion ends with the "not permitted" message, as for an inside URL. */
	public void testMainDocumentThroughProxyOnTheServersNetworkIsRefused() throws Exception {
		try (ProbeServer inside = new ProbeServer()) {
			inside.put("/doc.html", "text/html", "<!DOCTYPE html><html><body><p>INSIDE</p></body></html>");
			try {
				throughProxy(inside, false).convertUrl(PUBLIC + "/doc.html");
				fail("the document was fetched through the proxy: " + inside.hitPaths());
			} catch (final TranscoderException e) {
				assertEquals(MessageCodes.ERROR_FORBIDDEN_SERVERSIDE_DOCUMENT, e.getCode());
			}
			assertEquals("a request went to the proxy: " + inside.hitPaths(), 0, inside.totalHits());
		}
	}
}
