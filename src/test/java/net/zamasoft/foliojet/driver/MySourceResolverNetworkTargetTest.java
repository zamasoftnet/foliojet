package net.zamasoft.foliojet.driver;

import java.net.URI;

import junit.framework.TestCase;

/**
 * Which destinations a user without permission to access local resources is kept away from, judged on literal
 * addresses (no name is looked up and nothing is connected).
 *
 * <ul>
 * <li>The special-purpose IPv4 ranges that {@link java.net.InetAddress} does not classify are the server's own
 * network: shared address space (carrier-grade NAT, overlay networks, some cloud metadata services), "this
 * network", protocol assignments, benchmarking and the reserved block. They used to count as public.</li>
 * <li>An IPv6 address that stands for an IPv4 host (NAT64, IPv4-compatible) is judged by that host.</li>
 * <li>A redirect in a session that never set an access-control list is only kept away from the server's own
 * network. The empty list used to be asked, and it refuses everything: every redirect of the main document of such
 * a user failed.</li>
 * </ul>
 */
public class MySourceResolverNetworkTargetTest extends TestCase {
	private static boolean local(final String host) {
		return new MySourceResolver().resolvesToLocalNetwork(URI.create("http://" + host + "/"));
	}

	public void testSpecialPurposeIPv4RangesAreTheServersOwnNetwork() {
		for (final String host : new String[] { "0.1.2.3", "100.64.0.1", "100.100.100.200", "100.127.255.254",
				"192.0.0.8", "198.18.0.1", "198.19.255.254", "240.0.0.1", "255.255.255.255" }) {
			assertTrue(host, local(host));
		}
	}

	public void testAddressesNextToThoseRangesStayPublic() {
		for (final String host : new String[] { "1.1.1.1", "100.63.255.255", "100.128.0.1", "192.0.1.1",
				"198.17.255.255", "198.20.0.1", "203.0.113.10", "223.255.255.255" }) {
			assertFalse(host, local(host));
		}
	}

	public void testClassifiedRangesAreStillTheServersOwnNetwork() {
		for (final String host : new String[] { "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1",
				"169.254.169.254", "224.0.0.1", "[::1]", "[fe80::1]", "[fd00::1]" }) {
			assertTrue(host, local(host));
		}
	}

	public void testIPv6FormsOfAnIPv4HostAreJudgedByThatHost() {
		// NAT64 well-known prefix: 10.0.0.1, 127.0.0.1 and 100.100.100.200
		assertTrue(local("[64:ff9b::a00:1]"));
		assertTrue(local("[64:ff9b::7f00:1]"));
		assertTrue(local("[64:ff9b::6464:64c8]"));
		// IPv4-compatible: 10.0.0.1
		assertTrue(local("[::10.0.0.1]"));
		// A public host through NAT64 (203.0.113.10) and a public IPv6 host stay public
		assertFalse(local("[64:ff9b::cb00:710a]"));
		assertFalse(local("[2001:db8::1]"));
	}

	public void testRedirectWithoutAccessControlListIsOnlyKeptFromTheLocalNetwork() {
		final MySourceResolver resolver = new MySourceResolver();
		resolver.setLocalAccessAllowed(false);
		assertTrue(resolver.permitsNetworkTarget(URI.create("https://203.0.113.10/robots.txt")));
		assertFalse(resolver.permitsNetworkTarget(URI.create("http://127.0.0.1/robots.txt")));
		assertFalse(resolver.permitsNetworkTarget(URI.create("http://100.100.100.200/latest/meta-data/")));
		assertFalse(resolver.permitsNetworkTarget(URI.create("file:///etc/passwd")));
	}

	public void testRedirectWithAccessControlListObeysTheList() {
		final MySourceResolver resolver = new MySourceResolver();
		resolver.setLocalAccessAllowed(false);
		resolver.include(URI.create("https://203.0.113.10/pub/**"));
		assertTrue(resolver.permitsNetworkTarget(URI.create("https://203.0.113.10/pub/a.css")));
		assertFalse(resolver.permitsNetworkTarget(URI.create("https://203.0.113.10/robots.txt")));
	}
}
