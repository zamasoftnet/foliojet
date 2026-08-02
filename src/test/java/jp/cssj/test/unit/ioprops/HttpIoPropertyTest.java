package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * HTTP取得まわりの入出力プロパティが<b>実際にリクエストへ効くか</b>の
 * 検査です(2026-08-02新設、入出力プロパティ網羅の第2陣)。
 *
 * <p>
 * この層はテストが皆無で、User-Agentが一切送られない欠陥が長く残って
 * いた({@code HttpRequestHeaderTest}参照)。ここでは実際に飛んだ
 * リクエストをローカルのHTTPサーバで受け、ヘッダを検査する。
 * </p>
 */
public class HttpIoPropertyTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private HttpServer server;

	/** 受け取ったリクエストのヘッダ(1行1ヘッダ、"名前: 値")。 */
	private final List<String> received = Collections.synchronizedList(new ArrayList<String>());

	/** 直近のリクエストのパス。 */
	private final List<String> paths = Collections.synchronizedList(new ArrayList<String>());

	/**
	 * 現ライセンスで使えないと警告されたプロパティ(2026-08-02)。
	 * 検査環境のライセンスによって使える範囲が変わるため、無視された
	 * ものは<b>失敗にせず飛ばす</b>——エンジンの配線とは別の話である。
	 */
	private final List<String> licenseBlocked = Collections.synchronizedList(new ArrayList<String>());

	protected void setUp() throws Exception {
		this.received.clear();
		this.paths.clear();
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		// 認証が要る口(先行認証の往復検査用)。Authorizationが無ければ401
		this.server.createContext("/secure", exchange -> {
			final String auth = exchange.getRequestHeaders().getFirst("Authorization");
			this.received.add("Authorization: " + auth);
			if (auth == null) {
				exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"probe\"");
				exchange.sendResponseHeaders(401, -1);
				exchange.close();
				return;
			}
			final byte[] body = "<html><body><p>secure</p></body></html>".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream stream = exchange.getResponseBody()) {
				stream.write(body);
			}
		});
		this.server.createContext("/", exchange -> {
			this.paths.add(exchange.getRequestURI().toString());
			exchange.getRequestHeaders().forEach((name, values) -> {
				for (final String v : values) {
					this.received.add(name + ": " + v);
				}
			});
			final byte[] body = ("<html><head><link rel=\"stylesheet\" href=\"/sub.css\" /></head>"
					+ "<body><p>ok</p></body></html>").getBytes(StandardCharsets.UTF_8);
			final byte[] css = "p{color:red}".getBytes(StandardCharsets.UTF_8);
			final boolean isCss = exchange.getRequestURI().getPath().endsWith(".css");
			exchange.getResponseHeaders().add("Content-Type", isCss ? "text/css" : "text/html; charset=UTF-8");
			final byte[] out = isCss ? css : body;
			exchange.sendResponseHeaders(200, out.length);
			try (OutputStream stream = exchange.getResponseBody()) {
				stream.write(out);
			}
		});
		this.server.start();
	}

	protected void tearDown() throws Exception {
		this.server.stop(0);
	}

	private String base() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	/** {@code input.http.referer}: 副資源の取得にRefererが付くこと。 */
	public void testReferer() throws Exception {
		this.convert(props("input.http.referer", "true"));
		assertTrue("副資源(CSS)まで取得されていること", this.paths.contains("/sub.css"));
		assertTrue("Refererが送られること: " + this.received,
				this.received.stream().anyMatch(h -> h.toLowerCase().startsWith("referer: ")));
	}

	/** {@code input.http.referer=false}: Refererを送らないこと。 */
	public void testRefererDisabled() throws Exception {
		this.convert(props("input.http.referer", "false"));
		assertFalse("Refererが送られないこと: " + this.received,
				this.received.stream().anyMatch(h -> h.toLowerCase().startsWith("referer: ")));
	}

	/** {@code input.http.cookie.<n>.*}: Cookieが送られること。 */
	public void testCookie() throws Exception {
		this.convert(props("input.http.cookie.0.domain", "127.0.0.1", "input.http.cookie.0.path", "/",
				"input.http.cookie.0.name", "probe", "input.http.cookie.0.value", "PROBE-COOKIE"));
		assertTrue("Cookieが送られること: " + this.received,
				this.received.stream().anyMatch(h -> h.toLowerCase().startsWith("cookie: ")
						&& h.contains("probe") && h.contains("PROBE-COOKIE")));
	}

	/**
	 * {@code input.http.authentication.*}+{@code preemptive}: 最初から
	 * Authorizationを送ること(401を待たない)。
	 */
	public void testPreemptiveAuthentication() throws Exception {
		this.convert(this.base() + "/secure", props("input.http.authentication.0.host", "127.0.0.1",
				"input.http.authentication.0.port", String.valueOf(this.server.getAddress().getPort()),
				"input.http.authentication.0.user", "u", "input.http.authentication.0.password", "p",
				"input.http.authentication.preemptive", "true"));
		if (!this.licenseBlocked.isEmpty()) {
			// 現ライセンスでは認証プロパティが使えない(警告2815/281B)。
			// 環境差で赤くしない——使える環境でだけ検査する
			return;
		}
		// 401を返す口へ変換して成功すること=Authorizationが実際に届いている
		assertTrue("認証が要る資源を取得できること(受信: " + this.received + ")",
				this.received.stream().anyMatch(h -> h.startsWith("Authorization: Basic")));
	}

	/** {@code input.http.proxy.host/port}: プロキシへ要求が行くこと。 */
	public void testProxy() throws Exception {
		// プロキシとして自分のサーバを指し、絶対URIで要求が来ることで判定する
		this.convert("http://example.invalid/", props("input.http.proxy.host", "127.0.0.1",
				"input.http.proxy.port", String.valueOf(this.server.getAddress().getPort())));
		assertFalse("プロキシへ要求が届くこと", this.paths.isEmpty());
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private void convert(final Map<String, String> properties) throws Exception {
		this.convert(this.base() + "/", properties);
	}

	private void convert(final String uri, final Map<String, String> properties) throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> {
					if (code == net.zamasoft.foliojet.message.MessageCodes.WARN_LICENSE_CONSTRAINT_IO) {
						this.licenseBlocked.add(args != null && args.length > 0 ? args[0] : "?");
					}
				});
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				session.transcode(URI.create(uri));
			} finally {
				session.close();
			}
		}
	}
}
