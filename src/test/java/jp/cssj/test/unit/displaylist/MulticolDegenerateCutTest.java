package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;

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
 * <b>内容を1つも取れない切断で、段組がライブロックしない</b>ことを固定します
 * (2026-07-27新設)。
 *
 * <p>
 * <b>何が起きていたか。</b>段組を貫通する改ページ({@code columnSpanning})は
 * 前後の断片で枠を切らないため、継続断片は<b>始端フレームを丸ごと引き継ぎます</b>。
 * 切断線が内始端辺以上({@code pageLimit <= 0})のときにこれを適用すると、
 * 前断片は内容を1つも取らないまま、継続断片が<b>寸分違わぬ幾何</b>で
 * 再構成されます。次のページでも同じ判定が出るため、白紙ページを1枚ずつ
 * 永久に生成し続けました。ページは{@code PDFWriterImpl.pageOutputs}に
 * 保持されるのでヒープは単調増加し、最終的に{@code OutOfMemoryError}です。
 * </p>
 *
 * <p>
 * <b>なぜ既存のガードで止まらなかったか。</b>
 * {@code ContinuationStats.guardBreakProgress}は<b>直前の1回とだけ</b>
 * 比べるので、周期1の反復しか見えません。実測ではこの文書は
 * <b>2つの状態を交互に</b>行き来しており(ingest=12/depth=7と
 * ingest=12/depth=4)、連続カウンタが毎回0へ戻っていました。
 * ファジングで見つかった同一原因の6件のうち5件は周期1でガードに掛かり、
 * <b>最悪の1件だけがすり抜けた</b>のはこのためです。
 * </p>
 *
 * <p>
 * <b>この文書について。</b>60x60ptの紙にmargin 10pt(=内容40x40pt)、
 * その中にmargin 22ptのdiv——枠だけで44ptあり、内容領域を食い尽くします。
 * 1.2KBのファジング文書から縮小したもので、縦書き・画像・
 * インラインブロック・2つ目の段組はいずれも<b>無関係</b>でした。
 * 修正前は9,273ページを出して{@code OutOfMemoryError}、修正後は16ページ・2秒
 * (128MBヒープでも完走)。
 * </p>
 *
 * <p>
 * <b>文書を外部ファイルにしない</b>のは、相対パスの画像参照で1時間の
 * 誤診断をした前科があるためです(docs/LESSONS.md §6.9h)。ここで組み立てる。
 * </p>
 */
public class MulticolDegenerateCutTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public MulticolDegenerateCutTest(String name) {
		super(name);
	}

	/**
	 * 枠(margin 22pt×2=44pt)が内容領域(40pt)を食い尽くす段組。
	 */
	private static final String HTML = """
			<?jp.cssj.property name="output.page-width" value="60pt"?>
			<?jp.cssj.property name="output.page-height" value="60pt"?>
			<html><head><style>
			@page{margin:10pt}
			body{margin:0;font:normal 13pt/1.2 serif}
			p,div{margin:0;padding:0}
			</style></head><body>
			<div style="margin:22pt"><div style="column-count:2"><p>T0</p><p>T1</p></div></div>
			</body></html>
			""";

	/**
	 * ページ数の上限。修正前は9,273ページだったので、16ページの実測に対して
	 * 十分な余裕を取っても<b>3桁の差</b>がある。退行はここで確実に落ちる。
	 */
	private static final int MAX_PAGES = 64;

	/**
	 * 打ち切り時間。退行するとOOMまで数分走る(1GBヒープで3分32秒)ので、
	 * <b>ビルド全体を巻き込まないよう</b>ワーカースレッドで走らせて見張る。
	 * 実測2秒に対する余裕。
	 */
	private static final long WATCHDOG_MS = 60_000L;

	public void testDegenerateColumnCutTerminates() throws Exception {
		final File dir = new File("local/multicol-degenerate");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File html = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(html), StandardCharsets.UTF_8)) {
			w.write(HTML);
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
					CTISessionHelper.transcodeFile(session, html, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "multicol-degenerate");
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse("変換が" + WATCHDOG_MS / 1000 + "秒で終わらない(ライブロックの退行)", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError("変換が例外で終わった", failure[0]);
		}

		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("ページが1枚も出ていない", pages);
		assertTrue("ページが1枚も出ていない", pages.length > 0);
		assertTrue("ページ数が過大 " + pages.length + " (修正前は9273ページでOOM)", pages.length <= MAX_PAGES);

		// 内容が残っていること。ページ数だけ見ると「何も出さない」退行を通す
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			all.append(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		assertTrue("T0が消えた", all.indexOf("T0") >= 0);
		assertTrue("T1が消えた", all.indexOf("T1") >= 0);
	}
}
