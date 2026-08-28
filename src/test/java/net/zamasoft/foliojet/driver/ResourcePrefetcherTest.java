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
 * input.prefetch(外部リソースの非同期先読み、2026-08-27)の検査。
 * 発見スキャナ・読み先行ストリーム・ACLゲート・セッション局所ストアの
 * 合流を対象とする。
 */
public class ResourcePrefetcherTest extends TestCase {

	/** 発見スキャナ: 実要素だけを、エンジンと同じ規則で拾うこと。 */
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
		// 増分供給でも状態機械が保たれることを、わざと小さな塊で確かめる
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

	/** 読み先行ストリーム: バイト列を欠落なく順序どおり届けること。 */
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

	/** 読み先行ストリーム: 下位のIOExceptionは読了後に伝わること。 */
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
	 * ACLゲート: input.includeが許さないURLは、先読みでも外向き要求を
	 * 発生させないこと(先読みが遮断の抜け道にならない)。
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
			// 別ホストだけを許可する——テストサーバーへの要求は全て拒否される
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
	 * 合流: 先読み済みの資源はセッション局所ストアから渡され、同じURIを
	 * 何度resolveしても外向き要求は1回であること(Set-Cookie付き応答でも
	 * 同一変換内では再利用する——Chromeの同一ロード内memory cacheと同じ)。
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
			// 先読みが実際に取り終えてから消費する(発見が消費に先行する
			// 実際の順序)。取得中でないものへは合流しない設計なので、
			// ここで待たないと実要求が自分で取りに行く
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
	 * 同一変換内で同じ資源を何度も外向き取得しないこと(2026-08-28)。
	 *
	 * <p>
	 * 共有キャッシュに載らない応答(Set-Cookieを伴う等)でも、副資源なら
	 * 本文をセッション局所ストアへ控える。実測の発端は、寸法表を再利用した
	 * Paged SVGの2回目の変換が同じ背景SVGを66回取りに行き、5.0秒の変換が
	 * 13.4秒になっていたこと。先読みが順番待ちのまま実要求に降ろされると、
	 * 以降その資源はいつまでも共有されなかった。
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
				// 共有キャッシュには載せられない応答(利用者固有になり得る)
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
			// 先読みは一切挟まず、実要求だけを繰り返す(背景画像が多数の箱から
			// 参照される実際の形)
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
	 * 配信側がレート制限(429)を返したら、そのホストの先読みをやめること
	 * (2026-08-28)。投機で相手を怒らせて本来の取得まで失うのを防ぐ。
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
			// 429を見た後は同じホストへ投機しない
			for (int i = 0; i < 10; i++) {
				resolver.prefetch(URI.create(origin + "/b" + i + ".png"));
			}
			Thread.sleep(500);
			assertEquals("429の後は同じホストへ先読みしない", 1, hits.get());

			// 実要求は従来どおり通る(投機の自粛は実要求を妨げない)
			try {
				final Source source = resolver.resolve(URI.create(origin + "/b0.png"));
				try {
					// resolveは遅延接続。読んで初めてHTTPが飛ぶ
					source.getInputStream().readAllBytes();
				} finally {
					resolver.release(source);
				}
			} catch (final IOException expected) {
				// 429は実要求としては失敗しうる。ここでは要求が飛ぶことが大事
			}
			assertTrue("実要求は投げられるべき", hits.get() >= 2);
		} finally {
			resolver.reset();
			server.stop(0);
		}
	}
}
