package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
 * <b>分割されたセルの内容が、継続断片ではなく先頭断片に出る</b>ことを
 * 固定します(2026-07-27新設)。
 *
 * <p>
 * <b>何が起きていたか。</b>{@code TableCellBox.split}はセル内容へ渡す
 * 切断位置を「行の物理分割線 - {@code verticalAlign}」としていました。
 * {@code verticalAlign}は<b>確定セル高と内容高の差</b>から決まるので、
 * セルが{@code rowspan}や背の高い隣接セルのせいで内容よりずっと高いと、
 * <b>整列余白だけで切断線を越えて</b>しまいます。すると先頭断片には
 * 内容が1単位も残らず、<b>前ページには枠だけ・文字は次ページ</b>という
 * 分かれ方をしました。読者には「セルが空の行」に見えます。
 * </p>
 *
 * <p>
 * <b>rowspan固有ではありません。</b> 発見はファジング(不変条件7
 * 「読み順が保たれる」、seed 130ほか)で、観測された12件はすべて
 * {@code rowspan}のセルでしたが、本質は<b>「セル高 &gt; 内容高」かつ
 * 「その行が分割される」</b>ことです。下の1つめの文書は{@code rowspan}を
 * <b>使わずに</b>同じ壊れ方を再現します——隣のセルが背が高いだけで
 * 起こります。2つめが{@code rowspan}版です。
 * </p>
 *
 * <p>
 * <b>修正前の出力</b>(1つめの文書): 1ページ目が{@code P1 B1 B2}
 * (Aのセルは枠だけ)、2ページ目が{@code A B3 B4 B5}。文書順で先の
 * {@code A}が、後の{@code B2}より<b>後のページ</b>に出ていました。
 * </p>
 *
 * <p>
 * <b>文書を外部ファイルにしない</b>のは、相対パスの画像参照で1時間の
 * 誤診断をした前科があるためです(docs/LESSONS.md §6.9h)。ここで組み立てる。
 * </p>
 */
public class SplitCellValignReadingOrderTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public SplitCellValignReadingOrderTest(String name) {
		super(name);
	}

	/** 表示リストの文字。{@code RandomDocumentFuzzTest}と同じ拾い方。 */
	private static final Pattern TEXT_IN_DUMP = Pattern
			.compile("(?:Text|RubyUnit)\\[\"([^\"]*)\"(?: ruby=\"([^\"]*)\")?");

	/** 打ち切り時間。実測は1秒未満。 */
	private static final long WATCHDOG_MS = 60_000L;

	private static final String HEAD = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="60pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:5pt}
			body{margin:0;font:normal 12pt/1.2 serif}
			p,div,td{margin:0;padding:0}
			table{border-collapse:separate;table-layout:fixed}
			td{border:1pt solid black;%s}
			</style></head><body>
			""";

	/**
	 * rowspanなし。1行だけの{@code A}のセルが、7行の隣接セルに引き伸ばされて
	 * 高さ約104ptになる。分割線は行の先頭から約36ptで、既定の
	 * {@code vertical-align}(middle)の整列余白約45ptだけで切断線を越える。
	 * セルの{@code page-break-inside:auto}は、行がページ先頭になくても
	 * 分割されるようにするため(既定のavoidだと行ごと次ページへ送られ、
	 * この欠陥に到達しない)。
	 */
	private static final String HTML_NO_ROWSPAN = String.format(HEAD, "page-break-inside:auto") + """
			<p>P1</p>
			<table><tbody>
			<tr><td>A</td><td>B1<br>B2<br>B3<br>B4<br>B5<br>B6<br>B7</td></tr>
			</tbody></table>
			</body></html>
			""";

	/** 文書順。{@code A}は{@code B1}より先。 */
	private static final String[] ORDER_NO_ROWSPAN = { "P1", "A", "B1", "B2", "B3", "B4", "B5", "B6", "B7" };

	/**
	 * rowspan版。{@code A}は2行にまたがるので高さが内容の何倍にもなる。
	 * 分割は2行目の内部で起きる。
	 */
	private static final String HTML_ROWSPAN = String.format(HEAD, "") + """
			<p>P1</p>
			<table><tbody>
			<tr><td rowspan="2">A</td><td>B1<br>B2</td></tr>
			<tr><td>C1<br>C2<br>C3<br>C4<br>C5</td></tr>
			</tbody></table>
			</body></html>
			""";

	private static final String[] ORDER_ROWSPAN = { "P1", "A", "B1", "B2", "C1", "C2", "C3", "C4", "C5" };

	public void testSplitCellContentStaysInTheFirstFragment() throws Exception {
		check("no-rowspan", HTML_NO_ROWSPAN, ORDER_NO_ROWSPAN);
		check("rowspan", HTML_ROWSPAN, ORDER_ROWSPAN);
	}

	private static void check(final String name, final String html, final String[] orderedTokens) throws Exception {
		final File dir = new File("local/split-cell-valign/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}

		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(() -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "split-cell-valign-" + name);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}

		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		java.util.Arrays.sort(pages);

		// トークンが**最初に現れたページ**。ページ内の描画順は実装の都合
		// (連結セルは跨ぐ行が確定してから描かれる)なので問わない
		final Map<String, Integer> firstPage = new HashMap<String, Integer>();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
			final Matcher m = TEXT_IN_DUMP.matcher(dump);
			while (m.find()) {
				final Integer page = Integer.valueOf(i);
				firstPage.putIfAbsent(m.group(1), page);
				if (m.group(2) != null) {
					firstPage.putIfAbsent(m.group(2), page);
				}
			}
		}

		// 内容が失われていないこと。順序だけ見ると「消えた」退行を通す
		for (final String token : orderedTokens) {
			assertNotNull(name + ": " + token + "が消えた", firstPage.get(token));
		}
		// 読み順: 文書順で先のトークンが、後のトークンより後のページに出ない
		int prev = -1;
		String prevToken = null;
		for (final String token : orderedTokens) {
			final int at = firstPage.get(token).intValue();
			assertTrue(name + ": 読み順が入れ替わった: 文書順では" + prevToken + "→" + token + " だが、" + token + "はページ"
					+ (at + 1) + "、" + prevToken + "はページ" + (prev + 1), at >= prev);
			prev = at;
			prevToken = token;
		}
	}
}
