package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>14400ptより高いブロックが改ページできる</b>ことを固定します(2026-08-17新設)。
 *
 * <p>
 * {@code max-height}の初期値がUAの{@code getMaxSize()}(=14400pt)だったため、
 * それより高いブロックは{@code AbstractBlockBox}でこの値へ<b>切り詰められて</b>
 * いました。14400ptはPDFの<b>用紙</b>寸法の限界であって、箱の高さの上限では
 * ありません。
 * </p>
 *
 * <h2>機序</h2>
 *
 * <p>
 * 切り詰められた箱は、紙面へ入りきらないまま「入りきらない」と分からなく
 * なります。ページ途中から始まる場合は次ページへ送られ、送った先でも同じ
 * 判定が出るため、<b>内容を1行も消費しないまま改ページだけが繰り返され</b>ます。
 * 前進保証ガード({@code ContinuationStats.guardBreakProgress})が32回目で
 * 自動改ページを放棄し、そこから先の内容は失われます——実測(この文書)で
 * 201ページが34ページになりました。実物では
 * {@code files/realworld/w3c-jlreq}の用語表がこれで壊れ、放棄後の
 * ビルダー状態から{@code NullPointerException}になって<b>変換ごと失敗</b>して
 * いました。
 * </p>
 *
 * <h2>判定について</h2>
 *
 * <p>
 * ページ数だけでなく<b>ガードが発火していないこと</b>も要求します。
 * ライブロックを「32回で見切って先へ進む」機構が働いた時点で内容は
 * 落ちているので、ページ数の判定より機序に近いところで気づけます。
 * </p>
 */
public class TallBlockPaginationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 打ち切り時間。実測は5秒未満。 */
	private static final long WATCHDOG_MS = 120_000L;

	/** 行数。1行90pt+枠なので表全体は18,000pt超——14400ptの上限を確実に越える。 */
	private static final int ROWS = 200;

	public TallBlockPaginationTest(final String name) {
		super(name);
	}

	/**
	 * ページ途中(150pt/紙面180pt)から始まる18,000pt超の表。修正前は
	 * 34ページで内容が尽き、ガードが1回発火していた。
	 */
	public void testTableTallerThanPdfPageLimitPaginates() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 10pt/1.2 serif;margin:0}
				table{border-collapse:collapse}
				td{border:1pt solid black;padding:0}
				</style></head><body>
				<div style="height:150pt">TOP</div>
				<table>
				""");
		for (int i = 0; i < ROWS; ++i) {
			html.append("<tr><td>R").append(i).append("</td><td><div style=\"height:90pt\">C").append(i)
					.append("</div></td></tr>\n");
		}
		html.append("</table>\n<p>TAIL</p>\n</body></html>\n");

		final long alarms = ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get();
		final int pages = convert("tall-block-pagination", html.toString());

		assertEquals("14400ptより高いブロックで前進保証ガードが発火した(内容が失われる)", alarms,
				ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get());
		// 1行90ptで紙面は180pt。行が紙面を跨いで組まれるので、ページ数は
		// 行数と同程度になる。切り詰めが復活すると34ページ程度まで落ちる
		assertTrue("表の内容が最後まで組まれていない(ページ数=" + pages + ")", pages >= ROWS);
	}

	/**
	 * <b>ライブロックしても変換は完走する</b>(2026-08-17)。
	 *
	 * <p>
	 * 紙面の内容高を超える`max-height`を明示すると、初期値を直した後も
	 * ライブロックは起きる(印刷では紙面より大きい`max-height`に意味が
	 * ないので、内容が落ちること自体は文書側の責任と見なす)。
	 * <b>ただし落ちる・止まるのは実装側の責任</b>なので、ガードが発火しても
	 * 例外なく完走することを固定する。
	 * </p>
	 *
	 * <p>
	 * 実物では放棄後の`TextBuilder`が開始のない`INLINE_END`とフォント未設定の
	 * 字を受け取り、`NullPointerException`で変換ごと失敗していた
	 * (w3c-jlreq)。合成文書では同じ落ち方をまだ作れていない——再現手順は
	 * `docs/history/2026-08-17-max-height-clamp-livelock.md`に残してある。
	 * </p>
	 */
	public void testLivelockDegradesWithoutFailing() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 8pt/1.2 serif;margin:0}
				table{border-collapse:collapse;max-height:500pt}
				td,th{border:1pt solid black;padding:0}
				</style></head><body>
				<div style="height:150pt">TOP</div>
				<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody>
				""");
		for (int i = 0; i < ROWS; ++i) {
			html.append("<tr><td>R").append(i)
					.append("</td><td><p lang=\"en\">Definition ").append(i)
					.append(" with an <a href=\"#x\">inline link</a> and <span>a span</span> inside.</p>")
					.append("<div style=\"height:90pt\">C").append(i).append("</div></td></tr>\n");
		}
		html.append("</tbody></table>\n");
		html.append("<section><h2><span>References</span><a href=\"#r\"></a></h2>\n");
		for (int i = 0; i < 20; ++i) {
			html.append("<p>Trailing ").append(i)
					.append(" with <a href=\"#z\">a link</a> and <span>a <b>nested</b> span</span>.</p>\n");
		}
		html.append("</section>\n</body></html>\n");

		final long alarms = ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get();
		// 2026-08-23: ソース再生の再入拒否+表全体MOVE時の再生無効化
		// (SourceReplayer/TableBox)でこのライブロック自体が解消し、
		// ガード発火なしで正しく改ページされるようになった(204ページ・
		// 各行が1回ずつ)。従来はガードの縮退(34ページ・はみ出し配置)を
		// 期待値にしていた。表の後のTrailing段落も同じ修正で復元したため、
		// 下で行と後続内容の双方を固定する
		final int pages = convert("livelock-degrade", html.toString());
		assertTrue("ページが出ていない", pages > 0);
		assertEquals("ライブロックガードが発火した(解消済みのはず)", alarms,
				ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get());
		// 全行が失われず、複製もないこと
		final java.util.Map<String, Integer> count = new java.util.HashMap<>();
		int trailing = 0;
		int references = 0;
		final File dir = new File("local/livelock-degrade");
		for (final File f : dir.listFiles((d, name) -> name.endsWith(".txt"))) {
			final String text = java.nio.file.Files.readString(f.toPath());
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(R\\d+)\"").matcher(text);
			while (m.find()) {
				count.merge(m.group(1), 1, Integer::sum);
			}
			trailing += occurrences(text, "Text[\"Trailing\"");
			references += occurrences(text, "Text[\"References\"");
		}
		for (int i = 0; i < ROWS; ++i) {
			final Integer c = count.get("R" + i);
			assertNotNull("R" + i + " が消失した", c);
			assertEquals("R" + i + " が複製された", 1, c.intValue());
		}
		assertEquals("表の後のTrailing段落が消失または複製された", 20, trailing);
		assertEquals("表の後の見出しが消失または複製された", 1, references);
	}

	private static int occurrences(final String text, final String needle) {
		int count = 0;
		for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
			++count;
		}
		return count;
	}

	/**
	 * 変換して、表示リストのページ数を返します。
	 */
	private static int convert(final String name, final String html) throws Exception {
		final File dir = new File("local/" + name);
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
		final Thread worker = new Thread(null, () -> {
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
		}, name, 64L * 1024 * 1024);
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
		return pages.length;
	}
}
