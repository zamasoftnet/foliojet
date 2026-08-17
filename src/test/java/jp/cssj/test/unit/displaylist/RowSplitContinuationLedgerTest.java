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
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>1つのitemが複数ページぶんあるgrid/flexが最後まで改ページできる</b>ことを
 * 固定します(2026-08-17新設)。
 *
 * <p>
 * {@code GridBox.split}/{@code FlexBox.split}の境界行強制分割
 * (crosses+anySplit)は、継続断片の行帳簿の行高を<b>分割直後のremainderの
 * 実測</b>で書いていた。remainderはその時点で未レイアウト(アンカー復元前)で
 * {@code getPageExtent}がほぼ0を返すため、gridでは次のsplitの境界探索
 * ({@code Row.start}直接比較)が「全行が切断線の手前に収まる」と誤読して
 * <b>空の継続断片</b>を返し、残余が頭断片に積み残って紙外へ描かれた。
 * </p>
 *
 * <p>
 * 実物: eLifeの論文(`files/realworld/elife-art`)は本文全体を
 * {@code display:grid}のラッパーで包んでおり、95ページぶんが3ページ目に
 * 積み上がっていた(文字の重なり4,081,661対・紙外51,339pt)。実コーパス
 * 235文書のうち同型の壊れ方が6文書(pandoc-doc・qiita-article・godoc-pkg・
 * elife-art・mathjax-docs・rtd-theme)。
 * </p>
 *
 * <p>
 * 修正は「継続行の高さは<b>元の行の高さ−このページで消費した量</b>を
 * 下回らせない」という幾何学的下限(GridBox/FlexBox両方)。flexは境界探索が
 * 累積和なので実害までは確認されていないが、同じ帳簿誤りがあるため同じ
 * 下限で守る。
 * </p>
 */
public class RowSplitContinuationLedgerTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 打ち切り時間。実測は1件あたり5秒未満。 */
	private static final long WATCHDOG_MS = 120_000L;

	/** 段落数。紙面(内容180pt)の50ページぶん超。 */
	private static final int PARAGRAPHS = 300;

	public RowSplitContinuationLedgerTest(final String name) {
		super(name);
	}

	/** eLifeと同型: 12列grid、小さいnavと巨大itemの2行。修正前は3ページで内容が尽きた。 */
	public void testGridWithMultiPageItemPaginatesToTheEnd() throws Exception {
		final int pages = convert("grid-multipage-item",
				".wrap{display:grid;grid-template-columns:repeat(12,1fr);grid-column-gap:8px}\n"
						+ ".nav{grid-column:1/13}.main{grid-column:2/12}");
		assertTrue("gridの巨大itemが最後まで組まれていない(ページ数=" + pages + ")", pages >= 40);
	}

	/** flexの鏡像(column方向)。帳簿誤りは同じだが境界探索が累積和のため実害は未確認——防御の固定。 */
	public void testFlexColumnWithMultiPageItemPaginatesToTheEnd() throws Exception {
		final int pages = convert("flex-multipage-item",
				".wrap{display:flex;flex-direction:column}\n.nav{}.main{}");
		assertTrue("flexの巨大itemが最後まで組まれていない(ページ数=" + pages + ")", pages >= 40);
	}

	private static int convert(final String name, final String wrapCss) throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 9pt/1.2 serif;margin:0}
				""").append(wrapCss).append("""
				</style></head><body>
				<div class="wrap">
				<div class="nav">NAV</div>
				<div class="main">
				""");
		for (int i = 0; i < PARAGRAPHS; ++i) {
			html.append("<div><p>Paragraph ").append(i)
					.append(" with some text that wraps a little bit more.</p></div>\n");
		}
		html.append("</div>\n</div>\n<p>AFTER</p>\n</body></html>\n");

		final File dir = new File("local/row-split-ledger/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html.toString());
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
