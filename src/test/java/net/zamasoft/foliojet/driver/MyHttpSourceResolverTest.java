package net.zamasoft.foliojet.driver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;
import net.zamasoft.zstream.resolver.Source;

public class MyHttpSourceResolverTest extends TestCase {

	/**
	 * 2026-07-18: Objects from S3/CloudFront may respond with Content-Encoding: gzip regardless of
	 * the client's Accept-Encoding (i.e., unconditionally; actual example: e-gov.go.jp legislation pages).
	 * HttpClient does not decompress this automatically, so raw compressed bytes reach the parser,
	 * appearing as extensive garbled text. This reproduces that real bug and guards against regression.
	 */
	public void testGzipContentEncodingIsDecompressedEvenWithoutBeingRequested() throws Exception {
		String text = "こんにちは、世界。gzip圧縮された応答のテストです。";
		ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
		try (GZIPOutputStream gzipOut = new GZIPOutputStream(gzipped)) {
			gzipOut.write(text.getBytes(StandardCharsets.UTF_8));
		}
		byte[] gzippedBody = gzipped.toByteArray();

		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/gzip.html", exchange -> {
			try {
				// S3 returns the stored Content-Encoding unchanged, regardless of whether
				// Accept-Encoding was sent. Here, intentionally do not check
				// for Accept-Encoding, and always respond with gzip.
				exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
				exchange.getResponseHeaders().set("Content-Encoding", "gzip");
				exchange.sendResponseHeaders(200, gzippedBody.length);
				exchange.getResponseBody().write(gzippedBody);
			} finally {
				exchange.close();
			}
		});
		server.start();

		MyHttpSourceResolver resolver = new MyHttpSourceResolver();
		Source source = null;
		try {
			URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(),
					"/gzip.html", null, null);
			source = resolver.resolve(uri);
			String actual = new String(source.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			assertEquals("圧縮応答は解凍されてから渡されるべき", text, actual);
			// The compressed byte count (Content-Length) differs from the decompressed length,
			// so report unknown (-1) rather than an incorrect length.
			assertEquals("圧縮応答の長さは不明として報告されるべき", -1, source.getLength());
		} finally {
			if (source != null) {
				resolver.release(source);
			}
			resolver.close();
			server.stop(0);
		}
	}

	/**
	 * 2026-07-18: Without a User-Agent, HttpClient uses its default "Java-http-client/x.x",
	 * preventing content retrieval from sites with bot policies (actual example: Wikipedia;
	 * 403 rejection confirmed in a real-world test).
	 * This regression test checks that the default User-Agent is sent.
	 */
	public void testDefaultUserAgentIsSent() throws Exception {
		java.util.concurrent.atomic.AtomicReference<String> observedUserAgent = new java.util.concurrent.atomic.AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/ua.txt", exchange -> {
			try {
				observedUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
				byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.start();

		MyHttpSourceResolver resolver = new MyHttpSourceResolver();
		Source source = null;
		try {
			URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(),
					"/ua.txt", null, null);
			source = resolver.resolve(uri);
			source.getInputStream().readAllBytes();
			assertNotNull("User-Agent が送られるべき", observedUserAgent.get());
			assertFalse("既定の JDK 表記そのままではなく、識別可能な既定値であるべき",
					observedUserAgent.get().startsWith("Java-http-client"));
		} finally {
			if (source != null) {
				resolver.release(source);
			}
			resolver.close();
			server.stop(0);
		}
	}

	/**
	 * When an administrator explicitly sets User-Agent through input.http-header*.name,
	 * check that it is neither overwritten by the default nor added a second time.
	 */
	public void testExplicitUserAgentOverridesDefault() throws Exception {
		java.util.concurrent.atomic.AtomicReference<String> observedUserAgent = new java.util.concurrent.atomic.AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/ua.txt", exchange -> {
			try {
				observedUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
				byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.start();

		MyHttpSourceResolver resolver = new MyHttpSourceResolver();
		resolver.addHeader("User-Agent", "CustomAgent/1.0");
		Source source = null;
		try {
			URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(),
					"/ua.txt", null, null);
			source = resolver.resolve(uri);
			source.getInputStream().readAllBytes();
			assertEquals("明示設定した User-Agent がそのまま送られるべき", "CustomAgent/1.0", observedUserAgent.get());
		} finally {
			if (source != null) {
				resolver.release(source);
			}
			resolver.close();
			server.stop(0);
		}
	}

	/**
	 * 2026-07-18: java.net.http.HttpClient defaults to Redirect.NEVER
	 * (return 3xx as the response without following it).
	 * Regression test for silent failure to fetch redirecting URLs when redirect following is not explicitly
	 * set.
	 */
	public void testRedirectIsFollowed() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/redirected.html", exchange -> {
			try {
				byte[] body = "redirected-ok".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.createContext("/original.html", exchange -> {
			try {
				exchange.getResponseHeaders().set("Location", "/redirected.html");
				exchange.sendResponseHeaders(302, -1);
			} finally {
				exchange.close();
			}
		});
		server.start();

		MyHttpSourceResolver resolver = new MyHttpSourceResolver();
		Source source = null;
		try {
			URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(),
					"/original.html", null, null);
			source = resolver.resolve(uri);
			String actual = new String(source.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			assertEquals("302 リダイレクト先の内容が返るべき", "redirected-ok", actual);
		} finally {
			if (source != null) {
				resolver.release(source);
			}
			resolver.close();
			server.stop(0);
		}
	}
	public void testResolveStartsRequestAsynchronously() throws Exception {
		CountDownLatch requested = new CountDownLatch(1);
		CountDownLatch releaseResponse = new CountDownLatch(1);
		ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.setExecutor(serverExecutor);
		server.createContext("/asset.txt", exchange -> this.handleAsset(exchange, requested, releaseResponse));
		server.start();

		MyHttpSourceResolver resolver = new MyHttpSourceResolver();
		Source source = null;
		try {
			URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(),
					"/asset.txt", null, null);
			source = resolver.resolve(uri);

			assertTrue("resolve should start the HTTP request", requested.await(2, TimeUnit.SECONDS));
			releaseResponse.countDown();
			assertEquals("ok", new String(source.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
		} finally {
			if (source != null) {
				resolver.release(source);
			}
			resolver.close();
			server.stop(0);
			serverExecutor.shutdownNow();
		}
	}

	/**
	 * 2026-08-10: HTTP response caching across conversions eliminates delays from refetching
	 * @imported web-font CSS and similar resources on every conversion (measured with law3).
	 * Check that fetching the same URI through separate resolvers (= separate conversions)
	 * reaches the server only once.
	 */
	public void testResponseCacheServesRepeatConversionsFromMemory() throws Exception {
		HttpResponseCache.clear();
		java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/font.css", exchange -> {
			try {
				hits.incrementAndGet();
				byte[] body = "@font-face{font-family:x}".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.start();
		try {
			URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(),
					"/font.css", null, null);
			String first = this.fetch(uri, r -> r.setCacheTtl(600));
			String second = this.fetch(uri, r -> r.setCacheTtl(600));
			assertEquals("@font-face{font-family:x}", first);
			assertEquals(first, second);
			assertEquals("2回目はキャッシュから返るべき", 1, hits.get());
		} finally {
			server.stop(0);
			HttpResponseCache.clear();
		}
	}

	/**
	 * Even with gzip delivery (as used by fonts.googleapis.com), cache the decompressed body,
	 * and do not contact the server on the second fetch.
	 */
	public void testResponseCacheStoresDecompressedGzipBody() throws Exception {
		HttpResponseCache.clear();
		String text = "@font-face{font-family:gz}";
		ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
		try (GZIPOutputStream gzipOut = new GZIPOutputStream(gzipped)) {
			gzipOut.write(text.getBytes(StandardCharsets.UTF_8));
		}
		byte[] gzippedBody = gzipped.toByteArray();
		java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/font.css", exchange -> {
			try {
				hits.incrementAndGet();
				exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
				exchange.getResponseHeaders().set("Content-Encoding", "gzip");
				exchange.sendResponseHeaders(200, gzippedBody.length);
				exchange.getResponseBody().write(gzippedBody);
			} finally {
				exchange.close();
			}
		});
		server.start();
		try {
			URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(),
					"/font.css", null, null);
			assertEquals(text, this.fetch(uri, r -> r.setCacheTtl(600)));
			assertEquals(text, this.fetch(uri, r -> r.setCacheTtl(600)));
			assertEquals("2回目はキャッシュから返るべき", 1, hits.get());
		} finally {
			server.stop(0);
			HttpResponseCache.clear();
		}
	}

	/**
	 * Safety conditions: do not cache if there are authentication credentials for that host,
	 * a Cookie, Cache-Control: no-store, or TTL 0. For cookies, the first response's Set-Cookie prevents
	 * storage (response side), and the second request is excluded because it sends Cookie (request side).
	 * Check both decisions in one test.
	 */
	public void testResponseCacheIsBypassedForAuthCookieNoStoreAndZeroTtl() throws Exception {
		java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/plain.txt", exchange -> {
			try {
				hits.incrementAndGet();
				byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.createContext("/cookie.txt", exchange -> {
			try {
				hits.incrementAndGet();
				byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Set-Cookie", "sid=abc");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.createContext("/nostore.txt", exchange -> {
			try {
				hits.incrementAndGet();
				byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Cache-Control", "no-store");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.start();
		try {
			String host = server.getAddress().getHostString();
			int port = server.getAddress().getPort();

			// Credentials matching the destination host exclude the request on the request side.
			HttpResponseCache.clear();
			hits.set(0);
			URI plain = new URI("http", null, host, port, "/plain.txt", null, null);
			this.fetch(plain, r -> {
				r.setCacheTtl(600);
				r.addAuthentication(host, -1, "user", "pass");
			});
			this.fetch(plain, r -> {
				r.setCacheTtl(600);
				r.addAuthentication(host, -1, "user", "pass");
			});
			assertEquals("資格情報があればキャッシュされないべき", 2, hits.get());

			// Responses with Set-Cookie are not stored in the shared cache, and requests sending Cookie
			// are excluded too. However, **within the same conversion**, do not fetch again
			// (2026-08-28. A session-local store avoids fetching the same resource repeatedly;
			// this is not reuse across conversions).
			HttpResponseCache.clear();
			hits.set(0);
			URI cookie = new URI("http", null, host, port, "/cookie.txt", null, null);
			MyHttpSourceResolver resolver = new MyHttpSourceResolver();
			resolver.setCacheTtl(600);
			try {
				this.read(resolver, cookie);
				this.read(resolver, cookie);
				assertEquals("同一変換内では取り直さないべき", 1, hits.get());
			} finally {
				resolver.close();
			}
			// A separate conversion (separate resolver) cannot rely on the shared cache.
			MyHttpSourceResolver another = new MyHttpSourceResolver();
			another.setCacheTtl(600);
			try {
				this.read(another, cookie);
			} finally {
				another.close();
			}
			assertEquals("Cookieが絡む取得は変換をまたいでキャッシュされないべき", 2, hits.get());

			// Do not store no-store responses.
			HttpResponseCache.clear();
			hits.set(0);
			URI nostore = new URI("http", null, host, port, "/nostore.txt", null, null);
			this.fetch(nostore, r -> r.setCacheTtl(600));
			this.fetch(nostore, r -> r.setCacheTtl(600));
			assertEquals("no-store応答はキャッシュされないべき", 2, hits.get());

			// TTL 0 (equivalent to input.http.cache=false) excludes requests from the outset.
			HttpResponseCache.clear();
			hits.set(0);
			this.fetch(plain, r -> r.setCacheTtl(0));
			this.fetch(plain, r -> r.setCacheTtl(0));
			assertEquals("TTL0ならキャッシュされないべき", 2, hits.get());
		} finally {
			server.stop(0);
			HttpResponseCache.clear();
		}
	}

	/**
	 * The response max-age takes precedence if shorter than TTL (max-age=0 expires immediately).
	 */
	public void testResponseCacheHonorsMaxAge() throws Exception {
		HttpResponseCache.clear();
		java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/expire.txt", exchange -> {
			try {
				hits.incrementAndGet();
				byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Cache-Control", "max-age=0");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} finally {
				exchange.close();
			}
		});
		server.start();
		try {
			URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(),
					"/expire.txt", null, null);
			this.fetch(uri, r -> r.setCacheTtl(600));
			this.fetch(uri, r -> r.setCacheTtl(600));
			assertEquals("max-age=0は即時失効すべき", 2, hits.get());
		} finally {
			server.stop(0);
			HttpResponseCache.clear();
		}
	}

	/** Configure a resolver, fetch once, and return the body as a string. */
	private String fetch(URI uri, java.util.function.Consumer<MyHttpSourceResolver> setup) throws Exception {
		MyHttpSourceResolver resolver = new MyHttpSourceResolver();
		setup.accept(resolver);
		try {
			return this.read(resolver, uri);
		} finally {
			resolver.close();
		}
	}

	private String read(MyHttpSourceResolver resolver, URI uri) throws Exception {
		Source source = resolver.resolve(uri);
		try {
			return new String(source.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		} finally {
			resolver.release(source);
		}
	}

	private void handleAsset(HttpExchange exchange, CountDownLatch requested, CountDownLatch releaseResponse)
			throws IOException {
		requested.countDown();
		try {
			assertTrue("test server response was not released", releaseResponse.await(5, TimeUnit.SECONDS));
			byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		} finally {
			exchange.close();
		}
	}
}
