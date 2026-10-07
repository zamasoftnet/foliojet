package jp.cssj.test.unit.parser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Regression test that author CSS from an HTML document reaches inline SVG (2026-08-07).
 * Convert files/unittest/3050-IMG/svg-author-css.html and check that PDF content streams contain
 * (1) green fill from var(--icon-bg), (2) red stroke from var(--icon-line), and
 * (3) fill:currentColor resolved to the HTML-side color (blue).
 * This is the CSS-in-JS pattern (qiita, etc.) that colors icons using CSS classes and custom properties.
 */
public class SVGAuthorCssTest extends TestCase {
	public void testAuthorCssReachesInlineSVG() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/3050-IMG/svg-author-css.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		final byte[] pdf = out.toByteArray();
		final String ops = String.join("\n", inflateStreams(pdf));
		assertTrue("var(--icon-bg)の緑fillが出ていません:\n" + ops, containsColorOp(ops, "rg", 0, 1, 0));
		assertTrue("var(--icon-line)の赤strokeが出ていません:\n" + ops, containsColorOp(ops, "RG", 1, 0, 0));
		assertTrue("currentColorの青fillが出ていません:\n" + ops, containsColorOp(ops, "rg", 0, 0, 1));
	}

	private static boolean containsColorOp(String ops, String op, double r, double g, double b) {
		final Matcher m = Pattern.compile("([\\d.]+) ([\\d.]+) ([\\d.]+) " + op + "\\b").matcher(ops);
		while (m.find()) {
			if (Math.abs(Double.parseDouble(m.group(1)) - r) < 0.01
					&& Math.abs(Double.parseDouble(m.group(2)) - g) < 0.01
					&& Math.abs(Double.parseDouble(m.group(3)) - b) < 0.01) {
				return true;
			}
		}
		return false;
	}

	private static List<String> inflateStreams(byte[] pdf) throws Exception {
		final List<String> result = new ArrayList<String>();
		final String latin = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
		final Matcher m = Pattern.compile("stream\r?\n(.*?)endstream", Pattern.DOTALL).matcher(latin);
		while (m.find()) {
			final byte[] raw = m.group(1).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
			final Inflater inflater = new Inflater();
			inflater.setInput(raw);
			final ByteArrayOutputStream buff = new ByteArrayOutputStream();
			final byte[] chunk = new byte[8192];
			try {
				while (!inflater.finished()) {
					final int n = inflater.inflate(chunk);
					if (n == 0) {
						break;
					}
					buff.write(chunk, 0, n);
				}
				result.add(buff.toString(java.nio.charset.StandardCharsets.ISO_8859_1));
			} catch (Exception e) {
				// Skip uncompressed streams and streams such as images.
			} finally {
				inflater.end();
			}
		}
		return result;
	}
}
