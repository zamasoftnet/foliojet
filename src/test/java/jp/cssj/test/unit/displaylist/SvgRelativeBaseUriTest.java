package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * When the body URI is <b>relative</b> (sending the body as a file in REST multipart uses the file name
 * directly as the URI), verify that inline SVG {@code url(#…)} references work
 * (2026-10-04, item ④ of TECH-20261003-004).
 */
public class SvgRelativeBaseUriTest extends TestCase {
	private static final String HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<?jp.cssj.property name="output.page-width" value="300pt"?>
			<?jp.cssj.property name="output.page-height" value="200pt"?>
			<style>@page{margin:0} body{margin:0}</style></head><body>
			<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100" viewBox="0 0 200 100">
			<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="5" refY="5" orient="auto">
			<path d="M0,0 L10,5 L0,10 z" fill="red"/></marker>
			<linearGradient id="g"><stop offset="0" stop-color="blue"/><stop offset="1" stop-color="green"/></linearGradient></defs>
			<line x1="10" y1="50" x2="150" y2="50" stroke="black" marker-end="url(#arrow)"/>
			<rect x="160" y="10" width="30" height="30" fill="url(#g)"/>
			</svg>
			</body></html>
			""";

	public void testFragmentReferencesWithRelativeDocumentUri() throws Exception {
		for (final String uri : new String[] { "main.xhtml", "." }) {
			final List<String> problems = new ArrayList<>();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final DirectSession session = (DirectSession) new DirectDriver()
					.getSession(URI.create("copper:direct:"), null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(new MessageHandler() {
					@Override
					public void message(final short code, final String[] args, final String message) {
						// Count warnings (0x2...) and higher severities.
						if ((code & 0xF000) >= 0x2000) {
							problems.add(Integer.toHexString(code) + " " + message);
						}
					}
				});
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
						URI.create(uri), "application/xhtml+xml", null);
			} finally {
				session.close();
			}
			assertTrue("本文の URI が " + uri + " のとき: " + problems, problems.isEmpty());
			// The marker's red triangle is painted (fill color 1 0 0 rg appears).
			final String pdf = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
			assertTrue("本文の URI が " + uri + " のとき PDF ができていない", pdf.startsWith("%PDF"));
		}
	}
}
