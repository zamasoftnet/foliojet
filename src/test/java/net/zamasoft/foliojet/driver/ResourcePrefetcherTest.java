package net.zamasoft.foliojet.driver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;
import net.zamasoft.zstream.resolver.Source;

/**
 * Tests for input.prefetch (asynchronous prefetching of external resources, 2026-08-27).
 * Covers the integration of the discovery scanner, read-ahead stream, ACL gate,
 * and session-local store.
 */
public class ResourcePrefetcherTest extends TestCase {

	/** Discovery scanner: pick up only real elements, using the same rules as the engine. */
	public void testScannerFindsExpectedResources() {
		final List<URI> found = new ArrayList<>();
		final MySourceResolver collector = new MySourceResolver() {
			@Override
			public void prefetch(final URI uri) {
				found.add(uri);
			}
		};
		final String html = """
				<!DOCTYPE html>
				<html><head>
				<base href="https://example.com/dir/">
				<link rel="stylesheet" href="style.css?a=1&amp;b=2">
				<link rel="icon" href="favicon.ico">
				<!-- <img src="commented.png"> -->
				<script>var s = '<img src="scripted.png">';</script>
				<style>p { color: red } /* url(instyle.png) は対象外(v1) */</style>
				</head><body>
				<img srcset="small.png 1x, big.png 2x" src="fallback.png">
				<img src="plain.png">
				<img src="data:image/png;base64,xxxx">
				</body></html>
				""";
		final ResourcePrefetcher.Scanner scanner = new ResourcePrefetcher.Scanner(
				URI.create("https://example.com/page.html"), "UTF-8", collector);
		final byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
		// Deliberately feed small chunks to verify that incremental input preserves the state machine.
		for (int i = 0; i < bytes.length; i += 7) {
			scanner.feed(bytes, i, Math.min(7, bytes.length - i));
		}
		final List<String> texts = found.stream().map(URI::toString).toList();
		assertTrue("stylesheetは&amp;復号とbase解決込みで発見すべき: " + texts,
				texts.contains("https://example.com/dir/style.css?a=1&b=2"));
		assertTrue("srcsetは最高解像度候補を選ぶべき", texts.contains("https://example.com/dir/big.png"));
		assertTrue("srcset併記のsrcも先読み対象(別のimgがsrcだけで同じ画像を使い得る)",
				texts.contains("https://example.com/dir/fallback.png"));
		assertTrue("素のimg srcを発見すべき", texts.contains("https://example.com/dir/plain.png"));
		assertFalse("コメント内は対象外", texts.stream().anyMatch(t -> t.contains("commented")));
		assertFalse("script内は対象外", texts.stream().anyMatch(t -> t.contains("scripted")));
		assertFalse("rel=iconは対象外", texts.stream().anyMatch(t -> t.contains("favicon")));
		assertFalse("data:は対象外", texts.stream().anyMatch(t -> t.startsWith("data:")));
	}

	/** Read-ahead stream: deliver all bytes in order, without loss. */
	public void testReadAheadStreamDeliversAllBytes() throws Exception {
		final byte[] data = new byte[5 * 1024 * 1024 + 17];
		new Random(42).nextBytes(data);
		try (final InputStream in = new ResourcePrefetcher.ReadAheadInputStream(new ByteArrayInputStream(data),
				null)) {
			final byte[] out = in.readAllBytes();
			assertEquals(data.length, out.length);
			assertTrue(java.util.Arrays.equals(data, out));
			assertEquals(-1, in.read());
		}
	}

	/** Read-ahead stream: propagate the underlying IOException after the buffered data has been read. */
	public void testReadAheadStreamPropagatesError() throws Exception {
		final byte[] head = "hello".getBytes(StandardCharsets.UTF_8);
		final InputStream failing = new InputStream() {
			private int pos;

			@Override
			public int read() throws IOException {
				if (this.pos < head.length) {
					return head[this.pos++] & 0xFF;
				}
				throw new IOException("boom");
			}
		};
		try (final InputStream in = new ResourcePrefetcher.ReadAheadInputStream(failing, null)) {
			final byte[] buf = new byte[head.length];
			assertEquals(head.length, in.readNBytes(buf, 0, head.length));
			assertTrue(java.util.Arrays.equals(head, buf));
			try {
				in.read();
				fail("下位の例外が失われた");
			} catch (final IOException e) {
				assertEquals("boom", e.getMessage());
			}
		}
	}

	/**
	 * Read-ahead stream: closing it must not close the socket on which the reader thread is waiting (2026-09-28).
	 * Interrupting a virtual thread waiting on a socket read closes the socket. For a CTIP body, that socket is
	 * the client connection, so closing mid-body on cancellation or conversion failure disconnected the client.
	 * Use an underlying stream whose close() does nothing, just like the CTIP body input stream.
	 */
	public void testCloseLeavesTheSocketOpen() throws Exception {
		try (final java.net.ServerSocket server = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				final java.net.Socket client = new java.net.Socket(InetAddress.getLoopbackAddress(),
						server.getLocalPort());
				final java.net.Socket accepted = server.accept()) {
			accepted.setSoTimeout(5000);
			final InputStream body = new java.io.FilterInputStream(accepted.getInputStream()) {
				@Override
				public void close() {
					// As with the CTIP body input stream, do not close the connection.
				}
			};
			client.getOutputStream().write(1);
			client.getOutputStream().flush();
			final InputStream in = new ResourcePrefetcher.ReadAheadInputStream(body, null);
			assertEquals(1, in.read());
			// Close while the reader thread is waiting for the next byte.
			Thread.sleep(200);
			in.close();
			Thread.sleep(200);
			assertFalse("読み手への割り込みでソケットが閉じた", accepted.isClosed());

			// This byte completes the pending read and lets the reader exit. The connection remains usable afterward.
			client.getOutputStream().write(2);
			client.getOutputStream().flush();
			Thread.sleep(200);
			client.getOutputStream().write(3);
			client.getOutputStream().flush();
			assertEquals(3, accepted.getInputStream().read());
		}
	}

	/**
	 * ACL gate: even prefetching must not issue outbound requests for URLs that input.include does not allow
	 * (prefetching must not provide a way around access restrictions).
	 */
	public void testAclDeniedUriIsNeverRequested() throws Exception {
		final AtomicInteger hits = new AtomicInteger();
		final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> {
			hits.incrementAndGet();
			try {
				exchange.sendResponseHeaders(200, 2);
				exchange.getResponseBody().write("ok".getBytes(StandardCharsets.US_ASCII));
			} finally {
				exchange.close();
			}
		});
		server.start();
		final MySourceResolver resolver = new MySourceResolver();
		try {
			final String origin = "http://" + server.getAddress().getHostString() + ":"
					+ server.getAddress().getPort();
			resolver.setup(URI.create(origin + "/doc.html"), Map.of(), (code, args) -> {
			});
			// Allow only a different host: all requests to the test server are denied.
			resolver.include(URI.create("http://allowed.example/**"));
			resolver.prefetch(URI.create(origin + "/secret.png"));
			Thread.sleep(500);
			assertEquals("ACL拒否のURLへ先読み要求が飛んだ", 0, hits.get());
		} finally {
			resolver.reset();
			server.stop(0);
		}
	}

	/**
	 * Integration: serve prefetched resources from the session-local store, issuing only one outbound request
	 * no matter how many times the same URI is resolved (reuse even responses with Set-Cookie within the same
	 * conversion, just like Chrome's memory cache within a single load).
	 */
	public void testPrefetchedBodyIsReusedAcrossResolves() throws Exception {
		final AtomicInteger hits = new AtomicInteger();
		final byte[] body = "image-bytes".getBytes(StandardCharsets.US_ASCII);
		final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/img.png", exchange -> {
			hits.incrementAndGet();
			try {
				exchange.getResponseHeaders().set("Content-Type", "image/png");
				exchange.getResponseHeaders().set("Set-Cookie", "tracking=1");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.start();
		final MySourceResolver resolver = new MySourceResolver();
		try {
			final String origin = "http://" + server.getAddress().getHostString() + ":"
					+ server.getAddress().getPort();
			resolver.setup(URI.create(origin + "/doc.html"), Map.of(), (code, args) -> {
			});
			resolver.include(URI.create("**"));
			final URI img = URI.create(origin + "/img.png");
			resolver.prefetch(img);
			// Consume after prefetching has actually finished (the real ordering,
			// where discovery precedes consumption). By design, requests do not join a fetch that has not started,
			// so without this wait, the actual request fetches the resource itself.
			for (int i = 0; i < 100 && hits.get() == 0; i++) {
				Thread.sleep(20);
			}
			Thread.sleep(100);
			for (int i = 0; i < 3; i++) {
				final Source source = resolver.resolve(img);
				try {
					assertTrue(java.util.Arrays.equals(body, source.getInputStream().readAllBytes()));
				} finally {
					resolver.release(source);
				}
			}
			assertEquals("先読み済み資源への外向き要求は1回であるべき", 1, hits.get());
		} finally {
			resolver.reset();
			server.stop(0);
		}
	}

	/**
	 * Do not fetch the same resource externally more than once within a single conversion (2026-08-28).
	 *
	 * <p>
	 * Even for responses ineligible for the shared cache (such as those with Set-Cookie), keep subresource
	 * bodies in the session-local store. This started with a measurement: a second Paged SVG conversion
	 * reusing the dimension table fetched the same background SVG 66 times, increasing conversion time from
	 * 5.0 seconds to 13.4 seconds. When a queued prefetch fell back to an actual request, that resource
	 * was never shared afterward.
	 * </p>
	 */
	public void testResourceIsFetchedOnlyOncePerTranscode() throws Exception {
		final AtomicInteger hits = new AtomicInteger();
		final byte[] body = "css-bytes".getBytes(StandardCharsets.US_ASCII);
		final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/bg.svg", exchange -> {
			hits.incrementAndGet();
			try {
				exchange.getResponseHeaders().set("Content-Type", "image/svg+xml");
				// A response ineligible for the shared cache (it may be user-specific).
				exchange.getResponseHeaders().set("Set-Cookie", "tracking=1");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.start();
		final MySourceResolver resolver = new MySourceResolver();
		try {
			final String origin = "http://" + server.getAddress().getHostString() + ":"
					+ server.getAddress().getPort();
			resolver.setup(URI.create(origin + "/doc.html"), Map.of(), (code, args) -> {
			});
			resolver.include(URI.create("**"));
			final URI bg = URI.create(origin + "/bg.svg");
			// Repeat actual requests without any prefetching (the real pattern when many boxes
			// reference the same background image).
			for (int i = 0; i < 5; i++) {
				final Source source = resolver.resolve(bg);
				try {
					assertTrue(java.util.Arrays.equals(body, source.getInputStream().readAllBytes()));
				} finally {
					resolver.release(source);
				}
			}
			assertEquals("同じ資源への外向き要求は変換ごとに1回であるべき", 1, hits.get());
		} finally {
			resolver.reset();
			server.stop(0);
		}
	}

	/**
	 * Stop prefetching from a host when it returns a rate limit response (429)
	 * (2026-08-28). Avoid provoking the server with speculation and losing the actual fetch as well.
	 */
	public void testStopsPrefetchingThrottledHost() throws Exception {
		final AtomicInteger hits = new AtomicInteger();
		final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> {
			hits.incrementAndGet();
			try {
				exchange.sendResponseHeaders(429, -1);
			} finally {
				exchange.close();
			}
		});
		server.start();
		final MySourceResolver resolver = new MySourceResolver();
		try {
			final String origin = "http://" + server.getAddress().getHostString() + ":"
					+ server.getAddress().getPort();
			resolver.setup(URI.create(origin + "/doc.html"), Map.of(), (code, args) -> {
			});
			resolver.include(URI.create("**"));
			resolver.prefetch(URI.create(origin + "/a.png"));
			for (int i = 0; i < 40 && hits.get() == 0; i++) {
				Thread.sleep(50);
			}
			assertEquals("最初の1本は投げる", 1, hits.get());
			// Do not speculate on the same host after seeing a 429.
			for (int i = 0; i < 10; i++) {
				resolver.prefetch(URI.create(origin + "/b" + i + ".png"));
			}
			Thread.sleep(500);
			assertEquals("429の後は同じホストへ先読みしない", 1, hits.get());

			// Actual requests still go through as before (suspending speculation does not block actual requests).
			try {
				final Source source = resolver.resolve(URI.create(origin + "/b0.png"));
				try {
					// resolve connects lazily. HTTP is sent only when reading begins.
					source.getInputStream().readAllBytes();
				} finally {
					resolver.release(source);
				}
			} catch (final IOException expected) {
				// A 429 may fail an actual request. What matters here is that the request is sent.
			}
			assertTrue("実要求は投げられるべき", hits.get() >= 2);
		} finally {
			resolver.reset();
			server.stop(0);
		}
	}
}
