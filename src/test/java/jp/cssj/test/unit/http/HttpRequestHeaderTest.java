package jp.cssj.test.unit.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * HTTP取得で送るリクエストヘッダの契約です(2026-08-02新設)。
 *
 * <p>
 * <b>この層のテストが1つも無かった。</b> そのため
 * 「User-Agentも入出力プロパティで指定したヘッダも一切送られない」
 * という欠陥が、単体テスト1,121件・imageTest 591文書がすべて緑のまま
 * 残っていた(bot対策のあるサイトから何も取得できない状態。実地で
 * Wikipediaが403になって発覚)。原因は差し込まれたリゾルバが
 * http/httpsを横取りして、エンジンのHTTP設定が全部無効になること。
 * </p>
 *
 * <p>
 * ここでは<b>実際に飛んだリクエスト</b>をローカルのHTTPサーバで受けて
 * 検査する。組版結果ではなく取得層の契約なので、レイアウトのテストとは
 * 別に必要である。
 * </p>
 */
public class HttpRequestHeaderTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private HttpServer server;

	private final List<String> userAgents = Collections.synchronizedList(new ArrayList<String>());

	protected void setUp() throws Exception {
		this.userAgents.clear();
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			final String ua = exchange.getRequestHeaders().getFirst("User-Agent");
			this.userAgents.add(ua == null ? "(none)" : ua);
			final byte[] body = "<html><body><p>ok</p></body></html>".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		this.server.start();
	}

	protected void tearDown() throws Exception {
		this.server.stop(0);
	}

	/** 既定のUser-Agentが送られること(JDK既定のままにしない)。 */
	public void testDefaultUserAgentIsSent() throws Exception {
		this.convert(null, null);
		assertFalse("リクエストが届いていない", this.userAgents.isEmpty());
		final String ua = this.userAgents.get(0);
		assertEquals("既定のUser-Agentが送られること", "CopperPDF", ua);
	}

	/** 入出力プロパティで指定したヘッダが実際に送られること。 */
	public void testCustomHeaderIsSent() throws Exception {
		this.convert("User-Agent", "PROBE-UA-123");
		assertFalse("リクエストが届いていない", this.userAgents.isEmpty());
		assertEquals("指定したUser-Agentが送られること", "PROBE-UA-123", this.userAgents.get(0));
	}

	private void convert(final String headerName, final String headerValue) throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				// **実運用と同じ形**: 埋め込み側が独自リゾルバを差し込む。
				// これがhttpを横取りするとエンジンのHTTP設定が無効になる
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				if (headerName != null) {
					session.property("input.http.header.0.name", headerName);
					session.property("input.http.header.0.value", headerValue);
				}
				session.transcode(URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + "/"));
			} finally {
				session.close();
			}
		}
	}
}
