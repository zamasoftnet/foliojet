package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
 * 表のセル連結({@code rowspan}/{@code colspan})に絞ったランダム検査です
 * (2026-07-25新設)。
 *
 * <p>
 * セル連結は<b>グリッドの占有管理・ページ分割・つぶし境界・自動幅・
 * 2つのビルダー(行単位/表全体)・縦書き</b>のすべてと交差します。
 * 2026-07-25に見つかった不具合3件はいずれもこの交点でした——
 * 巨大spanのOOM、rowspanのintオーバーフロー、つぶし境界のnull参照。
 * 交点は人間が固定文書で網羅しきれないため、ここを直接サンプリングします。
 * </p>
 *
 * <h2>検査する不変条件</h2>
 *
 * <ol>
 * <li><b>例外で中断しない・停止する</b></li>
 * <li><b>セルが1つも消えない</b>——各セルに埋めた一意なトークンが、
 * すべて出力に現れる。<b>これがこのテストの主眼</b>。連結の占有管理を
 * 間違えるとセルが上書き・欠落するが、画像比較でも表示リストgoldenでも
 * 「そういうレイアウト」と区別がつかない</li>
 * <li><b>意図しない白紙ページがない</b></li>
 * </ol>
 *
 * <p>
 * <b>{@code table-layout: fixed}では不変条件2を課しません</b>——最初の行が
 * 列数を決め、それを超える列に落ちるセルは仕様どおり描画されないため
 * (CSS 2.1 §17.5.2.1)。この区別を入れる前は5シードが「消失」で落ちたが、
 * いずれも仕様どおりの挙動だった(オラクル側の誤り)。
 *
 * <p>
 * ページを跨ぐ表を意図的に作るため、ページは小さく・行数は多めにします。
 * {@code -Dfoliojet.fuzzSeeds}で件数を増やせます。
 * </p>
 */
public class TableSpanFuzzTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final int DEFAULT_SEEDS = 80;

	private static final long WATCHDOG_MS = 30_000L;

	private static final int MAX_PAGES = 200;

	private static final Pattern TEXT_IN_DUMP = Pattern.compile("Text\\[\"([^\"]*)\"");

	public TableSpanFuzzTest(String name) {
		super(name);
	}

	private static int seedCount() {
		final String v = System.getProperty("foliojet.fuzzSeeds");
		return v == null ? DEFAULT_SEEDS : Integer.parseInt(v);
	}

	public void testSpannedCellsAreNeverLost() throws Exception {
		final int seeds = seedCount();
		final List<String> failures = new ArrayList<>();
		for (int seed = 0; seed < seeds; ++seed) {
			try {
				checkOne(seed);
			} catch (final Throwable t) {
				failures.add("seed=" + seed + ": " + t);
				if (failures.size() >= 5) {
					break;
				}
			}
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void checkOne(final int seed) throws Exception {
		final Generated doc = generate(seed);
		final File html = new File("local/fuzz-span/" + seed + ".html");
		html.getParentFile().mkdirs();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(html), StandardCharsets.UTF_8)) {
			w.write(doc.html);
		}
		final File outDir = new File("local/fuzz-span/dl-" + seed);
		outDir.mkdirs();
		final File[] old = outDir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}

		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(() -> {
			try {
				convert(html, outDir);
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "span-fuzz-" + seed);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse("停止しない (" + html + ")", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError("変換が例外で終わった (" + html + ")", failure[0]);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("ページが出ていない (" + html + ")", pages);
		assertTrue("ページが出ていない (" + html + ")", pages.length > 0);
		assertTrue("ページ数が過大 " + pages.length + " (" + html + ")", pages.length <= MAX_PAGES);
		java.util.Arrays.sort(pages);

		final Set<String> seen = new LinkedHashSet<>();
		final List<Integer> blanks = new ArrayList<>();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = Files.readString(Path.of(pages[i].toURI()), StandardCharsets.UTF_8);
			boolean drew = false;
			for (final String line : dump.split("\n")) {
				final String t = line.trim();
				if (!t.isEmpty() && !t.startsWith("drawer")) {
					drew = true;
				}
			}
			if (!drew) {
				blanks.add(i + 1);
			}
			final Matcher m = TEXT_IN_DUMP.matcher(dump);
			while (m.find()) {
				seen.add(m.group(1));
			}
		}
		assertTrue("白紙ページ " + blanks + " (" + html + ")", blanks.isEmpty());

		if (doc.fixedLayout) {
			// table-layout: fixed は最初の行が列数を決め、それを超える列に
			// 落ちるセルは**仕様どおり描画されない**(CSS 2.1 §17.5.2.1)。
			// したがって消失を欠陥として扱えるのは auto のときだけ
			return;
		}
		final String all = String.join("|", seen);
		final List<String> lost = new ArrayList<>();
		for (final String token : doc.tokens) {
			if (!all.contains(token)) {
				lost.add(token);
			}
		}
		assertTrue("セルが消えた " + lost + " / 全" + doc.tokens.size() + "セル (" + html + ")", lost.isEmpty());
	}

	private static void convert(final File html, final File outDir) throws Exception {
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			try (OutputStream out = new FileOutputStream(new File(outDir, "out.pdf"))) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, html, "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}
	}

	// ------------------------------------------------------------------
	// 生成器
	// ------------------------------------------------------------------

	private record Generated(String html, List<String> tokens, boolean fixedLayout) {
	}

	private static final String[] WRITING_MODES = { "horizontal-tb", "vertical-rl", "vertical-lr" };

	private static Generated generate(final int seed) {
		final Random r = new Random(seed * 104729L + 17);
		final List<String> tokens = new ArrayList<>();
		final int[] counter = { 0 };

		final boolean collapse = r.nextBoolean();
		final boolean fixed = r.nextBoolean();
		final String wm = WRITING_MODES[r.nextInt(WRITING_MODES.length)];
		// ページを跨がせるため小さめ
		final int pw = 120 + r.nextInt(200);
		final int ph = 80 + r.nextInt(160);

		final StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		s.append("<?jp.cssj.property name=\"output.page-width\" value=\"").append(pw).append("pt\"?>\n");
		s.append("<?jp.cssj.property name=\"output.page-height\" value=\"").append(ph).append("pt\"?>\n");
		s.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
		s.append("<title>span fuzz ").append(seed).append("</title>\n<style>\n");
		s.append("@page{margin:0}\n");
		s.append("body{margin:0;font:normal 5pt/1.1 serif;writing-mode:").append(wm).append("}\n");
		s.append("p,div,td,th{margin:0;padding:0}\n");
		s.append("table{border-collapse:").append(collapse ? "collapse" : "separate").append(";table-layout:")
				.append(fixed ? "fixed" : "auto");
		if (fixed || r.nextBoolean()) {
			s.append(";width:").append(60 + r.nextInt(120)).append("pt");
		}
		s.append("}\n");
		s.append("td,th{border:").append(r.nextInt(3)).append("pt solid black}\n");
		s.append("</style></head><body>\n");

		final int cols = 1 + r.nextInt(5);
		final boolean header = r.nextBoolean();
		final boolean footer = r.nextBoolean();
		s.append("<table>\n");
		if (header) {
			s.append("<thead>\n");
			appendRows(s, r, tokens, counter, 1 + r.nextInt(2), cols, "th");
			s.append("</thead>\n");
		}
		if (footer) {
			s.append("<tfoot>\n");
			appendRows(s, r, tokens, counter, 1 + r.nextInt(2), cols, "td");
			s.append("</tfoot>\n");
		}
		// 本体は複数の行グループに分ける(グループ境界とspanの交差を狙う)
		final int groups = 1 + r.nextInt(3);
		for (int g = 0; g < groups; ++g) {
			s.append("<tbody>\n");
			appendRows(s, r, tokens, counter, 2 + r.nextInt(8), cols, "td");
			s.append("</tbody>\n");
		}
		s.append("</table>\n");
		s.append("<p>").append(token(tokens, counter)).append("</p>\n");
		s.append("</body></html>\n");
		return new Generated(s.toString(), tokens, fixed);
	}

	private static void appendRows(final StringBuilder s, final Random r, final List<String> tokens,
			final int[] counter, final int rows, final int cols, final String cellTag) {
		for (int y = 0; y < rows; ++y) {
			s.append("<tr>");
			final int n = 1 + r.nextInt(cols);
			for (int x = 0; x < n; ++x) {
				s.append('<').append(cellTag);
				// span は「収まる値」「はみ出す値」を両方出す
				if (r.nextInt(3) == 0) {
					s.append(" colspan=\"").append(1 + r.nextInt(cols + 2)).append('"');
				}
				if (r.nextInt(3) == 0) {
					s.append(" rowspan=\"").append(1 + r.nextInt(rows + 2)).append('"');
				}
				s.append('>').append(token(tokens, counter)).append("</").append(cellTag).append('>');
			}
			s.append("</tr>\n");
		}
	}

	private static String token(final List<String> tokens, final int[] counter) {
		final String t = "C" + (counter[0]++) + "z";
		tokens.add(t);
		return t;
	}
}
