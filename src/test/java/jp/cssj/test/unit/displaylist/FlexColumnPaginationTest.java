package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

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
 * <b>column方向flexが救済分割(帯クリップ)でなく行単位に改ページされる</b>
 * ことを固定します(2026-08-18新設)。
 *
 * <p>
 * 従来、column flexは{@code FlexBuilderLifecycle.eligible}の不適格
 * (ページ軸が絶対長でない——{@code min-height:100vh}のapp shell型が代表)で
 * F0の単一列通常フローへ退行しつつ、{@code PageAtomicBox}の原子契約だけは
 * 主張し続けた。結果、内容全体が1個のatomicとなり救済分割へ落ち、
 * <b>行が帯境界で上下にスライス</b>されていた(実コーパス235文書中87文書=
 * 37%に「紙面より大きい箱の平行移動」指摘。bbc-japan等)。
 * </p>
 *
 * <p>
 * 修正は2点: (1) F0退行では原子契約を放棄する
 * ({@code FlexBox.isPageAtomicNow}——{@code GridBox}のtrackLayoutと同じ
 * 設計判断)。中身は通常フローなので、通常ブロックとして行単位に
 * 改ページされる。(2) 適格なcolumn(絶対長主軸)の単一列配置と
 * F4c縮退経路には<b>item1つ=1行のページ軸帳簿を合成</b>し、row方向と
 * 同じ{@code FlexBox.split}(行分割機構)を適用する。
 * </p>
 */
public class FlexColumnPaginationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 打ち切り時間。実測は1件あたり5秒未満。 */
	private static final long WATCHDOG_MS = 120_000L;

	/**
	 * app shell型(min-height:100vh+space-betweenのcolumn flex)が
	 * 行境界で改ページされ、帯スライス(負座標への平行移動描画)が
	 * 起きないこと。
	 */
	public void testAppShellColumnFlexBreaksAtLineBoundaries() throws Exception {
		this.assertCleanPagination("app-shell",
				"min-height:100vh;display:flex;flex-direction:column;justify-content:space-between");
	}

	/** 素のcolumn flex(高さ指定なし)も同じく行境界で改ページされること。 */
	public void testPlainColumnFlexBreaksAtLineBoundaries() throws Exception {
		this.assertCleanPagination("plain", "display:flex;flex-direction:column");
	}

	private void assertCleanPagination(final String name, final String wrapperStyle) throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>@page{margin:10pt} body{font:normal 9pt/1.2 serif;margin:0}</style>
				</head><body>
				""");
		html.append("<div style=\"").append(wrapperStyle).append("\">\n");
		html.append("<header>HEAD</header>\n<main>\n");
		for (int i = 0; i < 120; ++i) {
			html.append("<p>Paragraph ").append(i).append(" text that wraps a bit more here.</p>\n");
		}
		html.append("</main>\n<footer>FOOT</footer>\n</div></body></html>\n");

		final File dir = prepareDir("flex-column-pagination/" + name);
		final int pages = convert(name, dir, html.toString());
		assertTrue(name + ": 最後まで組まれていない(ページ数=" + pages + ")", pages >= 5);
		// 救済分割は2ページ目以降を「全体を負座標へ平行移動+帯クリップ」で
		// 描く(display listでは全項目にartifact印)。行分割なら2ページ目は
		// そのページの内容だけを正座標で持つ
		for (int p = 2; p <= pages; ++p) {
			final File dump = new File(dir, String.format("page-%04d.txt", p));
			final List<String> lines = Files.readAllLines(dump.toPath(), StandardCharsets.UTF_8);
			for (final String line : lines) {
				assertFalse(name + ": p" + p + "が救済分割の帯描画になっている: " + line,
						line.contains("artifact") || line.contains("y=-"));
			}
		}
		// 内容の欠落なし: 最終ページにフッタ
		final File last = new File(dir, String.format("page-%04d.txt", pages));
		assertTrue(name + ": 最終ページにFOOTが無い",
				Files.readString(last.toPath(), StandardCharsets.UTF_8).contains("FOOT"));
	}

	/**
	 * 適格なcolumn(絶対長主軸)+定義済みitem高は、合成された
	 * item1つ=1行の帳簿により{@code FlexBox.split}で分割されること
	 * (紙面200pt・内容180ptにitem 150pt×3=450pt)。境界item(B)は
	 * ラベルが保持側(p1)に残り、残余(空の120pt)が次ページへ持ち越されて
	 * Cが120pt位置から始まる——救済分割(帯クリップの平行移動)は起きない。
	 */
	public void testDefiniteColumnFlexSplitsBetweenItems() throws Exception {
		final String html = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>@page{margin:10pt} body{font:normal 9pt/1.2 serif;margin:0}
				.wrap{height:450pt;display:flex;flex-direction:column}
				.wrap div{height:150pt;flex:none}</style>
				</head><body><div class="wrap">
				<div>ITEM-A</div><div>ITEM-B</div><div>ITEM-C</div>
				</div></body></html>
				""";
		final File dir = prepareDir("flex-column-pagination/definite");
		final int pages = convert("definite", dir, html);
		assertTrue("分割されていない(ページ数=" + pages + ")", pages >= 2);
		final String p1 = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final String p2 = Files.readString(new File(dir, "page-0002.txt").toPath(), StandardCharsets.UTF_8);
		assertTrue("definite: p1にITEM-A/Bが無い", p1.contains("ITEM-A") && p1.contains("ITEM-B"));
		assertFalse("definite: p2が救済分割の帯描画になっている", p2.contains("artifact") || p2.contains("y=-"));
		assertTrue("definite: p2にITEM-Cが無い", p2.contains("ITEM-C"));
	}

	private static File prepareDir(final String name) {
		final File dir = new File("local/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		return dir;
	}

	private static int convert(final String name, final File dir, final String html) throws Exception {
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
			throw new AssertionError(name + ": 変換が失敗", failure[0]);
		}
		final File[] dumps = dir.listFiles((d, f) -> f.startsWith("page-") && f.endsWith(".txt"));
		return dumps == null ? 0 : dumps.length;
	}
}
