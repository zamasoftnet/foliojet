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
 * Verify that HTML uses the supplied character encoding even with input.size-limit (2026-10-04,
 * overall review). With a limit, the body took the byte-input path, discarding the supplied encoding
 * and autodetecting it instead.
 */
public class LimitedInputEncodingTest extends TestCase {
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
