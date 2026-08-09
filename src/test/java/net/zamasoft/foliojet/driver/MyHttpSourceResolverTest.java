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
	 * 2026-07-18: S3/CloudFront 由来のオブジェクトは、クライアントの
	 * Accept-Encoding に関わらず(=無条件に)Content-Encoding: gzip で
	 * 応答することがある(実例: e-gov.go.jp の法令ページ)。HttpClient は
	 * これを自動解凍しないため、素の圧縮バイト列がそのままパーサへ渡り、
	 * 大量の文字化けとして観測される実バグの再現・回帰テスト。
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
				// S3 は Accept-Encoding 送信の有無に関わらず、保存済みの
				// Content-Encoding をそのまま返す。ここでは意図的に
				// Accept-Encoding の有無を検査せず、常に gzip で応答する
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
			// 圧縮後バイト数(Content-Length)は解凍後の長さと一致しないため、
			// 誤った長さを伝えるより不明(-1)であるべき
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
	 * 2026-07-18: User-Agent を送らないと HttpClient 既定の
	 * "Java-http-client/x.x" が使われ、bot policy を敷くサイト(実例:
	 * Wikipedia、実地テストで403拒否を確認)からコンテンツを取得できない
	 * 回帰テスト。既定の User-Agent が送られることを確認する。
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
	 * 管理者が input.http-header*.name 経由で User-Agent を明示設定した
	 * 場合は、既定値で上書き・重複追加しないことを確認する。
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
	 * 2026-07-18: java.net.http.HttpClient は既定で Redirect.NEVER
	 * (3xx を追従せずそのまま応答として返す)。追従設定を明示していないと
	 * リダイレクトするURLの取得が静かに壊れる回帰テスト。
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
	 * 2026-08-10: 変換をまたぐHTTP応答キャッシュ。@importされたウェブ
	 * フォントCSS等を毎変換取り直す遅延(law3で実測)の解消。別々の
	 * リゾルバ(=別々の変換)から同じURIを取得しても、サーバーへの
	 * 到達は1回であることを確認する。
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
	 * gzip配信(fonts.googleapis.comの実態)でも、解凍済みの本文が
	 * キャッシュされ2回目はサーバーへ行かないことを確認する。
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
	 * 安全条件: 認証情報(当該ホストの資格情報)・Cookie・
	 * Cache-Control: no-store・TTL0のいずれかがあればキャッシュしない。
	 * Cookieは初回応答のSet-Cookieが保存を止め(応答側)、2回目の要求は
	 * Cookieを送るため対象外(要求側)——両側の判定を1本で検査する。
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

			// 資格情報が当該ホストに一致する場合は要求側で対象外
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

			// Set-Cookie付き応答は保存されず、Cookieを送る要求は対象外。
			// 同一リゾルバ(CookieManager共有)で2回取得する
			HttpResponseCache.clear();
			hits.set(0);
			URI cookie = new URI("http", null, host, port, "/cookie.txt", null, null);
			MyHttpSourceResolver resolver = new MyHttpSourceResolver();
			resolver.setCacheTtl(600);
			try {
				this.read(resolver, cookie);
				this.read(resolver, cookie);
			} finally {
				resolver.close();
			}
			assertEquals("Cookieが絡む取得はキャッシュされないべき", 2, hits.get());

			// no-store応答は保存されない
			HttpResponseCache.clear();
			hits.set(0);
			URI nostore = new URI("http", null, host, port, "/nostore.txt", null, null);
			this.fetch(nostore, r -> r.setCacheTtl(600));
			this.fetch(nostore, r -> r.setCacheTtl(600));
			assertEquals("no-store応答はキャッシュされないべき", 2, hits.get());

			// TTL0(input.http.cache=false相当)は最初から対象外
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
	 * 応答のmax-ageはTTLより短ければ優先される(max-age=0は即時失効)。
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

	/** リゾルバを設定して1回取得し、本文を文字列で返します。 */
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
