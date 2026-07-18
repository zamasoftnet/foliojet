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
