package net.zamasoft.foliojet.css;

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
 * Verify that <b>an unterminated comment does not discard the entire stylesheet</b>
 * (2026-08-18, user bug report).
 *
 * <p>
 * CSS Syntax Level 3 treats EOF inside a comment as a parse error but ends the comment there
 * and continues. The ph-css lexer did not recover: {@code CSSReader} returned null, discarding
 * all valid rules in the sheet too (a regression from 3.2).
 * {@link DeclarationParser#closeUnterminatedComment} implicitly closes the comment at the end of input.
 * </p>
 */
public class UnterminatedCommentTest extends TestCase {
	public void testCloseUnterminatedComment() {
		// Implicitly close an unterminated trailing comment.
		assertEquals("p{color:red}/* x*/", DeclarationParser.closeUnterminatedComment("p{color:red}/* x"));
		// Do not change comments that are already closed.
		assertEquals("p{color:red}/* x */", DeclarationParser.closeUnterminatedComment("p{color:red}/* x */"));
		// /* inside a string does not start a comment.
		assertEquals("p{content:\"/*\"}", DeclarationParser.closeUnterminatedComment("p{content:\"/*\"}"));
		// /* inside an unquoted url does not start a comment (comments are not recognized within a url token).
		assertEquals("p{background:url(a/*b)}", DeclarationParser.closeUnterminatedComment("p{background:url(a/*b)}"));
		// The same holds inside quoted URLs.
		assertEquals("p{background:url(\"a/*b\")}",
				DeclarationParser.closeUnterminatedComment("p{background:url(\"a/*b\")}"));
		// An escaped quotation mark does not end a string.
		assertEquals("p{content:\"a\\\"/*\"}/* c*/",
				DeclarationParser.closeUnterminatedComment("p{content:\"a\\\"/*\"}/* c"));
		// /* inside a comment does not nest.
		assertEquals("/* a /* b */x", DeclarationParser.closeUnterminatedComment("/* a /* b */x"));
	}

	public void testUnterminatedCommentDoesNotDropStyleSheet() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3010-DECLARATION/unterminated-comment.html"), "text/html", null);
		} finally {
			session.close();
		}
		final byte[] pdf = out.toByteArray();
		final String ops = String.join("\n", inflateStreams(pdf));
		assertTrue("未閉鎖コメントより前の規則(赤)が捨てられています:\n" + ops, containsColorOp(ops, "rg", 1, 0, 0));
		assertTrue("インラインの未閉鎖コメント(緑)が回復していません:\n" + ops, containsColorOp(ops, "rg", 0, 1, 0));
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
