package net.zamasoft.foliojet.css.style;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>効かない組み合わせを黙って捨てない</b>ことを固定します(2823、2026-08-29)。
 *
 * <p>
 * 利用者報告(日本自由党川崎)の「書いたのに効かないのが一番時間を溶かす」を
 * 受けた告知。浮動体の{@code display:flex}/{@code display:grid}は
 * 恒久的な部分集合の外で通常ブロックへ落ちるが、以前は何も知らせずに
 * itemが縦に積まれるだけだった。絶対配置のコンテナは 2026-09-02(E-3)に
 * 対応したので、もう知らせない({@code AbsoluteGridTest}が動作を固定する)。
 * </p>
 */
public class IneffectiveCombinationWarningTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
			+ "@page{size:200pt 200pt;margin:10pt}body{margin:0}</style></head><body>";

	/** 絶対配置のflexコンテナは効くようになった(E-3)ので、何も言わない。 */
	public void testAbsoluteFlexIsSilent() throws Exception {
		final List<String[]> messages = convert(HEAD
				+ "<div style=\"position:absolute;left:0;right:0;display:flex;justify-content:space-between\">"
				+ "<span>L</span><span>R</span></div></body></html>");
		assertEquals("an absolutely positioned flex container works since 2026-09-02, so it must not warn: "
				+ describe(messages), 0, select(messages).size());
	}

	/** 浮動体のgridコンテナも同じ。 */
	public void testFloatGridIsReported() throws Exception {
		final List<String[]> messages = convert(HEAD
				+ "<div style=\"float:left;display:grid;grid-template-columns:1fr 1fr\">"
				+ "<span>L</span><span>R</span></div></body></html>");
		final List<String[]> reported = select(messages);
		assertEquals("float grid must be reported once: " + describe(messages), 1, reported.size());
		assertEquals("display: grid", reported.get(0)[1]);
	}

	/** 通常フローのflexは効くので、何も言わない——狼少年にしない。 */
	public void testFlowFlexIsSilent() throws Exception {
		final List<String[]> messages = convert(HEAD
				+ "<div style=\"display:flex;justify-content:space-between\">"
				+ "<span>L</span><span>R</span></div></body></html>");
		assertEquals("a flex container in normal flow works, so it must not warn: " + describe(messages), 0,
				select(messages).size());
	}

	/** 同じ書き方が並んでも1回だけ——警告で埋もれさせない。 */
	public void testReportedOnlyOnce() throws Exception {
		final StringBuilder html = new StringBuilder(HEAD);
		for (int i = 0; i < 5; ++i) {
			html.append("<div style=\"float:left;clear:left;display:flex\"><span>A</span><span>B</span></div>");
		}
		final List<String[]> messages = convert(html.append("</body></html>").toString());
		assertEquals("five identical fallbacks are one message: " + describe(messages), 1, select(messages).size());
	}

	private static List<String[]> select(final List<String[]> messages) {
		final String code = Integer.toString(MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION & 0xFFFF);
		return messages.stream().filter(m -> m[0].equals(code)).toList();
	}

	private static List<String[]> convert(final String html) throws Exception {
		final List<String[]> messages = new ArrayList<>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.setMessageHandler((code, args, mes) -> {
				final String[] m = new String[(args == null ? 0 : args.length) + 1];
				m[0] = Integer.toString(code & 0xFFFF);
				if (args != null) {
					System.arraycopy(args, 0, m, 1, args.length);
				}
				messages.add(m);
			});
			session.property("input.include", "**");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/3080-MODERN-CSS/clip-path-circle.html").toURI(), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return messages;
	}

	private static String describe(final List<String[]> messages) {
		final StringBuilder s = new StringBuilder();
		for (final String[] m : messages) {
			s.append(String.join(" / ", m)).append('\n');
		}
		return s.toString();
	}
}
