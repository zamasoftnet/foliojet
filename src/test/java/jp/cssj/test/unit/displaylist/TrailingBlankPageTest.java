package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * <b>何も描かないページを1枚も作らない</b>ことを固定します(2026-07-27新設)。
 *
 * <p>
 * css-break-3 §4.4「各フラグメンテナは0でない量の内容を取る」。20,000シードの
 * ファジングで残っていた最後の欠陥種別「白紙ページ」(18件)の実測から、
 * <b>複数の独立した経路</b>が同じ結末——描くもののないページ——に至ることが
 * 分かりました。ここでは<b>修正済みの2経路</b>を1件ずつ固定します。
 * どちらも<b>末尾が白紙になる</b>形です。
 * </p>
 *
 * <ol>
 * <li><b>入れ子の浮動体</b>({@code seed 2434}): 浮動体の中身がまた浮動体だと、
 * 旧{@code paintsNothingBeyondPage}は{@code getContentSize()}が入れ子の
 * 浮動体を数えないことを理由に判定を諦め、<b>常に</b>切断を予約していた。
 * 予約された切断は{@code endFlowBlock}の浮動体切断ループで必ず1ページ作る。
 * 18件中12件がこの形。</li>
 * <li><b>段組の段丈が空き容量を超える</b>({@code seed 8986}): 段のバランスが
 * 浮動体の底へ切り上げるため、段組の箱が紙の残りより長くなる。その超過分には
 * 何も描かれないのに、ブロック間自動改ページ(interflow)は<b>箱の幾何</b>
 * だけを見て改ページしていた。18件中1件。</li>
 * </ol>
 *
 * <p>
 * <b>未修正で残っている経路</b>(掃過の {@code seed 15448} と
 * {@code 17726}、20,000件中2件): 内容が確かに紙をはみ出しているのに、
 * はみ出しているのが<b>切れない位置</b>(箱からあふれた中身・
 * {@code page-break-inside:avoid}で丸ごと送られるブロック)なので、
 * 改ページしても継続断片が空になる。<b>「何も描かない継続断片なら捨てる」
 * という対策は本文消失を招きます</b>——空の断片は「取るものがない」のではなく
 * 「中身をこれからソース再生で受け取る器」であり、20,000シードの掃過で
 * 5件の消失(浮動体の中の表が丸ごと)を実測して撤回しました。
 * </p>
 *
 * <p>
 * <b>文書はここで組み立てます</b>——外部ファイルにすると相対パスの画像参照で
 * 判定が変わる事故を起こします(docs/LESSONS.md §6.9h)。画像を使わない
 * シードを選んであるのはそのためです。
 * </p>
 *
 * <p>
 * <b>ページ数だけでなくトークンの残存も検査します。</b> 白紙ページは
 * 「内容を捨てる」ことでいくらでも消せるので、それでは退行の検出になりません。
 * </p>
 */
public class TrailingBlankPageTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 打ち切り時間。実測は1件あたり1秒未満。 */
	private static final long WATCHDOG_MS = 60_000L;

	public TrailingBlankPageTest(String name) {
		super(name);
	}

	/** 経路1: 浮動体の中に浮動体。 */
	private static final String NESTED_FLOAT = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="300pt"?>
			<?jp.cssj.property name="output.page-height" value="150pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 11pt/1.2 serif;writing-mode:vertical-lr}
			p,div{margin:0;padding:0}
			</style></head><body>
			<p><span style="display:inline-block;width:187pt;height:65pt">T0</span></p>
			<div style="writing-mode:vertical-lr">
			<div style="float:left;width:125pt">
			<div style="float:right;width:97pt">
			<p>T1</p>
			</div>
			</div>
			<ul style="list-style-position:inside;list-style-type:disc">
			<li>T2</li>
			<li>T3</li>
			</ul>
			</div>
			<p><ruby>T4<rt>T5</rt></ruby><ruby>T6<rt>T7</rt></ruby></p>
			</body></html>
			""";

	/** 経路2: 段組の段丈が空き容量を超える。 */
	private static final String OVERLONG_COLUMN = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="300pt"?>
			<?jp.cssj.property name="output.page-height" value="150pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{margin:0;font:normal 13pt/1.2 serif;writing-mode:vertical-lr}
			p,div{margin:0;padding:0}
			</style></head><body>
			<p><span style="display:inline-block;width:191pt;height:135pt">T0</span></p>
			<div style="column-count:2;column-gap:5pt">
			<div style="margin:4pt;padding:2pt;border:0pt solid black">
			<div style="page-break-inside:avoid;margin:3pt">
			<p>T1</p>
			<p>T2</p>
			<p>T3</p>
			</div>
			<p><ruby>T4<rt>T5</rt></ruby><ruby>T6<rt>T7</rt></ruby><ruby>T8<rt>T9</rt></ruby></p>
			<div style="float:right;width:109pt">
			<p>T10</p>
			</div>
			</div>
			<div style="clear:both"><span style="font-size:35pt">T11</span></div>
			</div>
			</body></html>
			""";

	public void testNestedFloatMakesNoBlankPage() throws Exception {
		assertNoBlankPage("nested-float", NESTED_FLOAT, 8);
	}

	public void testOverlongColumnMakesNoBlankPage() throws Exception {
		assertNoBlankPage("overlong-column", OVERLONG_COLUMN, 12);
	}

	/**
	 * 変換して、(1) 表示リストが空のページが1枚もないこと、(2) T0..T(n-1) の
	 * トークンが全部どこかのページに現れること、を検査します。
	 *
	 * @param name      作業ディレクトリ名
	 * @param html      文書
	 * @param tokenCount 文書が持つ T トークンの数
	 */
	private static void assertNoBlankPage(final String name, final String html, final int tokenCount)
			throws Exception {
		final File dir = new File("local/trailing-blank/" + name);
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
		}, "trailing-blank-" + name, 64L * 1024 * 1024);
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

		final List<Integer> blanks = new ArrayList<>();
		final StringBuilder all = new StringBuilder();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
			all.append(dump);
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
		}
		assertTrue(name + ": 白紙ページ " + blanks + " (全" + pages.length + "ページ)", blanks.isEmpty());

		// 白紙は「内容を捨てる」ことでも消せる。それが退行として見えるように
		final List<String> lost = new ArrayList<>();
		for (int i = 0; i < tokenCount; ++i) {
			if (all.indexOf("T" + i) < 0) {
				lost.add("T" + i);
			}
		}
		assertTrue(name + ": 内容が失われた " + lost, lost.isEmpty());
	}
}
