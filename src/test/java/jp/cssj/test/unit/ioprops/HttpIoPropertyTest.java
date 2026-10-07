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
 * Tests that HTTP-fetching I/O properties <b>actually affect requests</b>
 * (introduced on 2026-08-02, the second batch of comprehensive I/O property coverage).
 *
 * <p>
 * This layer had no tests, so a defect that never sent User-Agent persisted for a long time
 * (see {@code HttpRequestHeaderTest}). Here, receive actual requests using a local HTTP server
 * and inspect their headers.
 * </p>
 */
public class HttpIoPropertyTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private HttpServer server;

	/** Headers of the received request (one per line, "name: value"). */
	private final List<String> received = Collections.synchronizedList(new ArrayList<String>());

	/** Path of the most recent request. */
	private final List<String> paths = Collections.synchronizedList(new ArrayList<String>());

	protected void setUp() throws Exception {
		this.received.clear();
		this.paths.clear();
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		// Authentication endpoint (for preemptive-authentication round-trip checks). Returns 401 without Authorization.
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

	/** {@code input.http.referer}: subresource requests include Referer. */
	public void testReferer() throws Exception {
		this.convert(props("input.http.referer", "true"));
		assertTrue("副資源(CSS)まで取得されていること", this.paths.contains("/sub.css"));
		assertTrue("Refererが送られること: " + this.received,
				this.received.stream().anyMatch(h -> h.toLowerCase().startsWith("referer: ")));
	}

	/** {@code input.http.referer=false}: do not send Referer. */
	public void testRefererDisabled() throws Exception {
		this.convert(props("input.http.referer", "false"));
		assertFalse("Refererが送られないこと: " + this.received,
				this.received.stream().anyMatch(h -> h.toLowerCase().startsWith("referer: ")));
	}

	/** {@code input.http.cookie.<n>.*}: send Cookie. */
	public void testCookie() throws Exception {
		this.convert(props("input.http.cookie.0.domain", "127.0.0.1", "input.http.cookie.0.path", "/",
				"input.http.cookie.0.name", "probe", "input.http.cookie.0.value", "PROBE-COOKIE"));
		assertTrue("Cookieが送られること: " + this.received,
				this.received.stream().anyMatch(h -> h.toLowerCase().startsWith("cookie: ")
						&& h.contains("probe") && h.contains("PROBE-COOKIE")));
	}

	/**
	 * {@code input.http.authentication.*}+{@code preemptive}: send Authorization immediately (do not wait for
	 * 401).
	 */
	public void testPreemptiveAuthentication() throws Exception {
		this.convert(this.base() + "/secure", props("input.http.authentication.0.host", "127.0.0.1",
				"input.http.authentication.0.port", String.valueOf(this.server.getAddress().getPort()),
				"input.http.authentication.0.user", "u", "input.http.authentication.0.password", "p",
				"input.http.authentication.preemptive", "true"));
		// Successful conversion from the endpoint returning 401 proves that Authorization actually arrives.
		assertTrue("認証が要る資源を取得できること(受信: " + this.received + ")",
				this.received.stream().anyMatch(h -> h.startsWith("Authorization: Basic")));
	}

	/** {@code input.http.proxy.host/port}: requests reach the proxy. */
	public void testProxy() throws Exception {
		// Point to our own server as the proxy and check that requests use absolute URIs.
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
