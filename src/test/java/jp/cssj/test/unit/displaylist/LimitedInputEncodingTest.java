package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 入力の上限(input.size-limit)があっても、渡された文字コードで HTML を読むことを固定します(2026-10-04、
 * 全体レビュー。上限があると本文はバイトの経路に回り、渡された文字コードを捨てて自動判定していた)。
 */
public class LimitedInputEncodingTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static String dump(final String limit) throws Exception {
		final byte[] body = "<p>café naïve</p>".getBytes(StandardCharsets.ISO_8859_1);
		final Path dir = Files.createTempDirectory("limited-encoding");
		try (AutoCloseable d = DisplayListDumper.scopedDir(dir.toString())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				if (limit != null) {
					session.property("input.size-limit", limit);
				}
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(body), URI.create("file:///a.html"),
						"text/html", "ISO-8859-1");
			} finally {
				session.close();
			}
		}
		return Files.readString(dir.resolve("page-0001.txt"));
	}

	public void testDeclaredEncodingIsUsedWithAndWithoutALimit() throws Exception {
		for (final String limit : new String[] { null, "1000000" }) {
			final String dump = dump(limit);
			assertTrue("limit=" + limit + ": " + dump, dump.contains("café"));
			assertTrue("limit=" + limit + ": " + dump, dump.contains("naïve"));
		}
	}
}
