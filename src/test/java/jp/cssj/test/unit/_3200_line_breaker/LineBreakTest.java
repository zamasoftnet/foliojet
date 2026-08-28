package jp.cssj.test.unit._3200_line_breaker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
 * CSS {@code line-break}(css-text-3 §5.2)の禁則の強さを固定するテスト
 * です(2026-08-29新設)。
 *
 * <p>
 * 幅100pt・10ptの和文(1行10字)で、11字目に対象文字を置く。行頭禁則の
 * 対象なら10字目ごと次行へ送られて1行目は9字、禁則から外れれば1行目は
 * 10字になる。display listの最初の{@code Text[...]}で1行目の字数を読む。
 * </p>
 * <ul>
 * <li>{@code strict}(既定{@code auto}も同じ): 長音・小書き仮名・繰返し記号・
 * 中点は行頭に来ない</li>
 * <li>{@code normal}: 長音・小書き仮名・〜は行頭に来られる。繰返し記号・
 * 中点は来ない</li>
 * <li>{@code loose}: さらに繰返し記号・中点・‐・接尾辞(％)が行頭に、
 * 接頭辞(￥)の直後で分割できる。句読点(、。)は仕様どおりlooseでも
 * 行頭に来ない</li>
 * <li>{@code anywhere}: 欧文単語の途中でも分割する(strictは1行に溢れる)</li>
 * </ul>
 */
public class LineBreakTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** 1行目の字数(1行10字の箱で、11字目の文字が禁則対象なら9、外れれば10)。 */
	private int firstLineLength(final String name, final String lineBreak, final String body) throws Exception {
		final String dump = this.render(name, lineBreak, body);
		final Matcher m = TEXT.matcher(dump);
		assertTrue(name + ": Text[] がありません:\n" + dump, m.find());
		return m.group(1).length();
	}

	private int lineCount(final String name, final String lineBreak, final String body) throws Exception {
		final String dump = this.render(name, lineBreak, body);
		final Matcher m = TEXT.matcher(dump);
		int count = 0;
		while (m.find()) {
			++count;
		}
		return count;
	}

	public void testProlongedSoundMark() throws Exception {
		final String body = "あいうえおかきくけこーさしすせそ";
		assertEquals(9, this.firstLineLength("strict-choon", "strict", body));
		assertEquals(9, this.firstLineLength("auto-choon", "auto", body));
		assertEquals(10, this.firstLineLength("normal-choon", "normal", body));
		assertEquals(10, this.firstLineLength("loose-choon", "loose", body));
	}

	public void testSmallKana() throws Exception {
		final String body = "あいうえおかきくけこっさしすせそ";
		assertEquals(9, this.firstLineLength("strict-small", "strict", body));
		assertEquals(10, this.firstLineLength("normal-small", "normal", body));
	}

	public void testIterationMark() throws Exception {
		final String body = "あいうえおかきくけこ々さしすせそ";
		assertEquals(9, this.firstLineLength("strict-iter", "strict", body));
		assertEquals(9, this.firstLineLength("normal-iter", "normal", body));
		assertEquals(10, this.firstLineLength("loose-iter", "loose", body));
	}

	public void testCenteredPunctuation() throws Exception {
		final String body = "あいうえおかきくけこ・さしすせそ";
		assertEquals(9, this.firstLineLength("strict-nakaguro", "strict", body));
		assertEquals(9, this.firstLineLength("normal-nakaguro", "normal", body));
		assertEquals(10, this.firstLineLength("loose-nakaguro", "loose", body));
	}

	public void testIdeographicCommaStaysForbidden() throws Exception {
		// 句読点はlooseでも行頭禁則(css-text-3の緩和表に無い)
		final String body = "あいうえおかきくけこ、さしすせそ";
		assertEquals(9, this.firstLineLength("strict-touten", "strict", body));
		assertEquals(9, this.firstLineLength("loose-touten", "loose", body));
	}

	public void testPrefixAndSuffix() throws Exception {
		// 接尾辞％: looseだけ行頭に来られる
		final String suffix = "あいうえおかきくけこ％さしすせそ";
		assertEquals(9, this.firstLineLength("normal-suffix", "normal", suffix));
		assertEquals(10, this.firstLineLength("loose-suffix", "loose", suffix));
		// 接頭辞$: 10字目が$なら、strict/normalは$12345が不可分で$ごと次行へ、
		// looseは$の直後で切れる(全角￥はJLREQ規則が元々後続と結んでいない)
		final String prefix = "あいうえおかきくけ$12345";
		assertEquals(9, this.firstLineLength("normal-prefix", "normal", prefix));
		assertEquals(10, this.firstLineLength("loose-prefix", "loose", prefix));
	}

	public void testAnywhere() throws Exception {
		// 欧文1語は分割できず1行に溢れる。anywhereは文字の間で折り返す
		final String body = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
		assertEquals(1, this.lineCount("strict-anywhere", "strict", body));
		assertTrue(this.lineCount("anywhere-anywhere", "anywhere", body) >= 2);
		// 約物の前でも切れる(strictは「け。」を送って9字)
		final String punct = "あいうえおかきくけこ。さしすせそ";
		assertEquals(10, this.firstLineLength("anywhere-punct", "anywhere", punct));
	}

	public void testWordBreakCombination() throws Exception {
		// word-break: break-all と併用してもline-breakの緩和は効く(break-allは
		// CJK同士の禁則を残す。keep-allはCJK同士を分割しないので比較にならない)
		final String body = "あいうえおかきくけこーさしすせそ";
		assertEquals(10, this.firstLineLength("normal-breakall", "normal; word-break: break-all", body));
		assertEquals(9, this.firstLineLength("strict-breakall", "strict; word-break: break-all", body));
	}

	private String render(final String name, final String lineBreak, final String body) throws Exception {
		final File dir = new File("local/unittest/line-break");
		dir.mkdirs();
		final File source = new File(dir, name + ".html");
		Files.writeString(source.toPath(), document(lineBreak, body), StandardCharsets.UTF_8);

		final File outDir = new File(dir, name);
		deleteChildren(outDir);
		outDir.mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			final File pdf = new File(dir, name + ".pdf");
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, source, "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + name, pages);
		assertTrue("表示リストが出力されていません: " + name, pages.length > 0);
		java.util.Arrays.sort(pages);
		final List<String> texts = new ArrayList<>();
		for (final File page : pages) {
			texts.add(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return String.join("\n", texts);
	}

	private static String document(final String lineBreak, final String body) {
		return "<?jp.cssj.property name=\"output.page-width\" value=\"200pt\"?>\n"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n"
				+ "<html lang=\"ja\">\n<head>\n"
				+ "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n"
				+ "<title>line-break</title>\n"
				+ "<style type=\"text/css\">\n"
				+ "@page { margin: 0; }\n"
				+ "body { margin: 0; font-size: 10pt; line-height: 1.2; }\n"
				+ "p { margin: 0; width: 100pt; line-break: " + lineBreak + "; }\n"
				+ "</style>\n</head>\n<body>\n<p>" + body + "</p>\n</body>\n</html>\n";
	}

	private static void deleteChildren(final File dir) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			child.delete();
		}
	}
}
