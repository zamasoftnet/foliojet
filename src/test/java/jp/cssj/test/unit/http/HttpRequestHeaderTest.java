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
 * Contract for request headers sent during HTTP fetching (introduced on 2026-08-02).
 *
 * <p>
 * <b>There were no tests at this layer.</b> As a result, the defect that neither User-Agent nor
 * headers specified through I/O properties were sent survived while all 1,121 unit tests and
 * 591 imageTest documents passed. Nothing could be fetched from sites with bot protection;
 * a real Wikipedia request returning 403 exposed it. The injected resolver intercepted
 * http/https, disabling all of the engine's HTTP settings.
 * </p>
 *
 * <p>
 * These tests receive and inspect <b>requests actually sent</b> using a local HTTP server.
 * This is a contract for the fetching layer, not the layout result, so it requires separate tests.
 * </p>
 */
public class HttpRequestHeaderTest extends TestCase {
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

	/** The default User-Agent is sent (do not leave it at the JDK default). */
	public void testDefaultUserAgentIsSent() throws Exception {
		this.convert(null, null);
		assertFalse("リクエストが届いていない", this.userAgents.isEmpty());
		final String ua = this.userAgents.get(0);
		assertEquals("既定のUser-Agentが送られること", "CopperPDF", ua);
	}

	/** Headers specified through I/O properties are actually sent. */
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
				// **Use the same setup as production**: the embedding application injects its own resolver.
				// If it intercepts http, the engine's HTTP settings become ineffective.
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
