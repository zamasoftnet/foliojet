package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * running-elements R0 のスタイル捕捉点プローブです。
 *
 * <p>
 * 表示結果から内部の Segment を推測して assert するのではなく、ソース上で確実な
 * 「カスケード済みの疑似スタイル」と「要素の counter-increment が疑似内容より先」だけを
 * 生成結果で固定する。捕捉点そのものは stderr に報告する。
 * </p>
 */
public class StyleCapturePointProbeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final Pattern TEXT = Pattern.compile("Text\\[\\\"([^\\\"]*)\\\"");

	private record Conversion(byte[] pdf, String displayList) {
	}

	public void testCascadeAndCounterPrecedeGeneratedBeforeContent() throws Exception {
		final Conversion conversion = convert();
		final String text = displayedText(conversion.displayList());

		System.err.println("[running R0] PDF bytes=" + conversion.pdf().length + ", display text=" + text);
		System.err.println("[running R0] display list:\n" + conversion.displayList());
		System.err.println("[running R0] 捕捉点候補: StyleEventMachine.startStyle(CSSStyle) の"
				+ " Display.get(style) 後 (StyleEventMachine.java:309)〜container 登録前 (:311)。"
				+ "カスケード後で、Segment (:323)、counter/string-set (:343/:345)、生成内容 (:349/:357) より前。"
				+ "部分木を横取りするには characters (:1158) と endStyle (:1491) も同じ捕捉状態で分岐する。");

		assertFalse("低詳細度の div::before 規則が残っています。疑似スタイルのカスケード順序が崩れた可能性: " + text,
				text.contains("WRONG"));
		assertTrue("counter-increment 後の page=5、本文 A、子 B の順で生成されていません: " + text,
				text.contains("5AB"));
	}

	private static Conversion convert() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:200pt 120pt;margin:10pt}html,body{margin:0}"
				+ "div::before{content:'WRONG'}#h{counter-increment:page 4}#h::before{content:counter(page)}"
				+ "</style></head><body><div id='h'>A<span>B</span></div></body></html>";
		final File dumpDir = Files.createTempDirectory("foliojet-running-r0-").toFile();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try (AutoCloseable scope = DisplayListDumper.scopedDir(dumpDir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///running-r0-style-capture.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}

		final File[] pages = dumpDir.listFiles((dir, name) -> name.startsWith("page-") && name.endsWith(".txt"));
		final StringBuilder displayList = new StringBuilder();
		if (pages != null) {
			Arrays.sort(pages, Comparator.comparing(File::getName));
			for (final File page : pages) {
				displayList.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			}
		}
		return new Conversion(out.toByteArray(), displayList.toString());
	}

	private static String displayedText(final String displayList) {
		final StringBuilder text = new StringBuilder();
		final Matcher matcher = TEXT.matcher(displayList);
		while (matcher.find()) {
			text.append(matcher.group(1));
		}
		return text.toString();
	}
}
