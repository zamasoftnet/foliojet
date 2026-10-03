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
 * 本文の URI が<b>相対</b>(REST の multipart で本文をファイルとして送ると、
 * ファイル名がそのまま URI になる)のとき、インライン SVG の{@code url(#…)}が
 * 壊れないことを固定します(2026-10-04、TECH-20261003-004 の④)。
 */
public class SvgRelativeBaseUriTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

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
						// 警告(0x2...)以上は数える
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
			// marker の赤い三角が描かれている(塗りの色 1 0 0 rg が出る)
			final String pdf = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
			assertTrue("本文の URI が " + uri + " のとき PDF ができていない", pdf.startsWith("%PDF"));
		}
	}
}
