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
 * <b>閉じられていないコメントでスタイルシート全体が破棄されない</b>ことを
 * 固定します(2026-08-18、利用者バグ報告)。
 *
 * <p>
 * CSS Syntax Level 3はコメント中のEOFをparse errorとしつつコメントを
 * そこで終えて継続すると定めるが、ph-cssの字句解析器は回復せず
 * {@code CSSReader}がnullを返し、シートに書かれた正常な規則まで
 * すべて捨てられていた(3.2からの回帰)。
 * {@link DeclarationParser#closeUnterminatedComment}が入力終端で
 * コメントを暗黙に閉じる。
 * </p>
 */
public class UnterminatedCommentTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testCloseUnterminatedComment() {
		// 末尾の未閉鎖コメントは暗黙に閉じる
		assertEquals("p{color:red}/* x*/", DeclarationParser.closeUnterminatedComment("p{color:red}/* x"));
		// 閉じているものは変更しない
		assertEquals("p{color:red}/* x */", DeclarationParser.closeUnterminatedComment("p{color:red}/* x */"));
		// 文字列内の/*はコメント開始ではない
		assertEquals("p{content:\"/*\"}", DeclarationParser.closeUnterminatedComment("p{content:\"/*\"}"));
		// 未引用url内の/*はコメント開始ではない(urlトークン内ではコメントは認識されない)
		assertEquals("p{background:url(a/*b)}", DeclarationParser.closeUnterminatedComment("p{background:url(a/*b)}"));
		// 引用付きurl内も同様
		assertEquals("p{background:url(\"a/*b\")}",
				DeclarationParser.closeUnterminatedComment("p{background:url(\"a/*b\")}"));
		// エスケープされた引用符で文字列は終わらない
		assertEquals("p{content:\"a\\\"/*\"}/* c*/",
				DeclarationParser.closeUnterminatedComment("p{content:\"a\\\"/*\"}/* c"));
		// コメント内の/*は入れ子にならない
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
				// 圧縮されていない・画像等のストリームは読み飛ばす
			} finally {
				inflater.end();
			}
		}
		return result;
	}
}
