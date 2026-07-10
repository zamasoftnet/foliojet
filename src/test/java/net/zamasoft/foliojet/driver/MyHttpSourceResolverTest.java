package net.zamasoft.foliojet.driver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;
import net.zamasoft.zstream.resolver.Source;

public class MyHttpSourceResolverTest extends TestCase {
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
