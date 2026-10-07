package jp.cssj.test.unit.security;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

/**
 * A <b>recording</b> HTTP server for resource-access-control tests.
 *
 * <p>
 * Successful layout or no request reaching the server <b>cannot distinguish blocking from simply
 * ignoring the resource</b>. In the first version of the countermeasure, we actually reported
 * successful blocking when the SVG path had never activated. Every check in this suite therefore
 * records the following three points.
 * </p>
 *
 * <ol>
 * <li><b>Path reachability</b> — what was requested from this recording server</li>
 * <li><b>Source</b> — whether pushed input, a custom resolver, HTTP, or cache supplied the body</li>
 * <li><b>Result</b> — whether its distinctive content was actually used or the intended denial
 * occurred</li>
 * </ol>
 *
 * <p>
 * For the third point, every response body contains <b>distinctive, identifiable content</b>.
 * Judge whether fetched contents appeared in the type area, not merely whether fetching succeeded.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/zamasoftnet/foliojet">Design (third version)</a>
 */
public class ProbeServer implements AutoCloseable {

	/** Paths of received requests in arrival order. */
	private final List<String> hits = Collections.synchronizedList(new ArrayList<String>());

	private final Map<String, byte[]> bodies = new HashMap<String, byte[]>();

	private final Map<String, String> types = new HashMap<String, String>();

	/** Redirect destinations by path. If a value exists, issue a 302 to it. */
	private final Map<String, String> redirects = new HashMap<String, String>();

	private final HttpServer server;

	public ProbeServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			final String path = exchange.getRequestURI().getPath();
			this.hits.add(path);
			final String to = this.redirects.get(path);
			if (to != null) {
				exchange.getResponseHeaders().add("Location", to);
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
				return;
			}
			final byte[] body = this.bodies.get(path);
			if (body == null) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			final String type = this.types.get(path);
			if (type != null) {
				exchange.getResponseHeaders().add("Content-Type", type);
			}
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		this.server.start();
	}

	public ProbeServer put(final String path, final String type, final byte[] body) {
		this.bodies.put(path, body);
		this.types.put(path, type);
		return this;
	}

	public ProbeServer put(final String path, final String type, final String body) {
		return this.put(path, type, body.getBytes(StandardCharsets.UTF_8));
	}

	public ProbeServer redirect(final String path, final String to) {
		this.redirects.put(path, to);
		return this;
	}

	/** This server's {@code http://127.0.0.1:port}. */
	public String base() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	public String url(final String path) {
		return this.base() + path;
	}

	/** Number of requests to this path. <b>0 is evidence that no fetch occurred.</b> */
	public int hits(final String path) {
		int n = 0;
		for (final String hit : this.hits) {
			if (hit.equals(path)) {
				n++;
			}
		}
		return n;
	}

	/** Total number of requests. */
	public int totalHits() {
		return this.hits.size();
	}

	/** Paths in arrival order. Used to explain failures. */
	public List<String> hitPaths() {
		return new ArrayList<String>(this.hits);
	}

	public void reset() {
		this.hits.clear();
	}

	@Override
	public void close() {
		this.server.stop(0);
	}
}
