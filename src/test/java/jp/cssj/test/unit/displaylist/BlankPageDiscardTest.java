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
 * <b>何も描かないページは出力しない</b>——ただし作者が求めた白紙は残す
 * ——ことを固定します(2026-07-28新設、css-break-3 §4.4)。
 *
 * <p>
 * {@link TrailingBlankPageTest}が<b>レイアウト側</b>の対策(そもそも
 * 白紙になる切断をしない)を固定するのに対し、こちらは<b>出力側</b>の規則を
 * 固定します: 版が組み上がった時点で紙に何も描かないページは、PDFのページを
 * 作る前に落とす({@code StyleBuilder.drawPage})。
 * </p>
 *
 * <p>
 * <b>境界が4つあり、全部を検査します。</b>
 * </p>
 * <ol>
 * <li><b>末尾の自動白紙は消える</b>——内容が確かに紙をはみ出していて改ページ
 * 自体は正しく要求されるのに、切っても何も動かない形(掃過の seed 18717)。</li>
 * <li><b>文書の途中の自動白紙も消える</b>——白紙は位置によらず存在すべきで
 * ないため(掃過の seed 17726 を画像なしに直したもの)。</li>
 * <li><b>強制改ページの白紙は残る</b>——{@code page-break-after:always}で
 * 末尾に1枚出すのは正当な指定であり、この規則が踏み越えてはならない線。</li>
 * <li><b>落としたページは面(recto/verso)を消費しない</b>——消費すると
 * 以後のページが全部裏返り、両面印刷の面付けが静かに壊れる。
 * {@code @page:left}のマージンボックスを面の目印にして、
 * (4)スタイル選択の面と(4b)左右改ページの面の<b>両方</b>を検査する。</li>
 * </ol>
 *
 * <p>
 * <b>文書はここで組み立てます</b>——外部ファイルにすると相対パスの画像参照で
 * 判定が変わる事故を起こします(docs/LESSONS.md §6.9h)。画像を使わない形に
 * 直してあるのはそのためです。
 * </p>
 *
 * <p>
 * <b>ページ数だけでなくトークンの残存も検査します。</b> 白紙ページは
 * 「内容を捨てる」ことでいくらでも消せるので、それでは退行の検出になりません。
 * </p>
 */
public class BlankPageDiscardTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 打ち切り時間。実測は1件あたり1秒未満。 */
	private static final long WATCHDOG_MS = 60_000L;

	public BlankPageDiscardTest(String name) {
		super(name);
	}

	/**
	 * 境界1: 末尾に自動改ページの白紙ができる形(掃過の seed 18717)。
	 * 修正前は2ページで2枚目が白紙。
	 */
	private static final String AUTO_TRAILING = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="200pt"?>
			<?jp.cssj.property name="output.page-height" value="200pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{margin:0;font:normal 12pt/1.2 serif;writing-mode:horizontal-tb}
			p,div,td{margin:0;padding:0}
			</style></head><body>
			<ul style="list-style-position:outside;list-style-type:decimal">
			<li>T0</li>
			<li>T1</li>
			<li>T2</li>
			<li>T3</li>
			</ul>
			<p>T4 T5 T6 </p>
			<div style="float:right;width:43pt">
			<p><ruby>T7<rt>T8</rt></ruby></p>
			<div style="page-break-inside:avoid;margin:1pt">
			<p>T9 T10 T11 T12 </p>
			</div>
			<div style="column-count:3;column-gap:11pt">
			<div style="clear:both"><span style="font-size:14pt">T13</span></div>
			<p><span style="display:inline-block;width:128pt;height:72pt">T14</span></p>
			<div style="margin:3pt;padding:2pt;border:0pt solid black">
			<p>T15</p>
			<p>T16</p>
			</div>
			</div>
			</div>
			<p><ruby>T17<rt>T18</rt></ruby></p>
			</body></html>
			""";

	/**
	 * 境界2: 文書の途中に自動改ページの白紙ができる形(掃過の seed 17726 の
	 * 画像を同寸法の箱へ置き換えたもの)。修正前は5ページで2枚目が白紙。
	 */
	private static final String AUTO_MID_DOCUMENT = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:5pt}
			body{margin:0;font:normal 9pt/1.2 serif;writing-mode:vertical-lr}
			p,div,td{margin:0;padding:0}
			</style></head><body>
			<div style="page-break-inside:avoid;margin:3pt">
			<div style="float:left;width:81pt">
			<p><ruby>T0<rt>T1</rt></ruby></p>
			</div>
			<div style="float:right;width:69pt">
			<ol style="list-style-position:outside;list-style-type:disc">
			<li>T2</li>
			<li>T3</li>
			<li>T4</li>
			<li>T5</li>
			</ol>
			<p>T6 T7 T8 T9 </p>
			<p><ruby>T10<rt>T11</rt></ruby><ruby>T12<rt>T13</rt></ruby></p>
			</div>
			</div>
			<div style="page-break-inside:avoid;margin:2pt">
			<ul style="list-style-position:outside;list-style-type:decimal">
			<li>T14</li>
			<li>T15</li>
			</ul>
			<p><span style="display:block;width:68pt;height:220pt;border:1pt solid black">T19</span></p>
			</div>
			<div style="clear:right"><span style="font-size:25pt">T16</span></div>
			<ul style="list-style-position:outside;list-style-type:disc">
			<li>T17</li>
			<li>T18</li>
			</ul>
			</body></html>
			""";

	/**
	 * 境界3: 作者が明示的に求めた末尾の白紙。落としてはならない。
	 *
	 * <p>
	 * <b>{@code page-break-after:always}を文書の最後の要素に付けても、この
	 * エンジンは末尾のページを作りません</b>(2026-07-28に実測。修正前後で
	 * 同じ=この規則とは無関係の既存の振る舞い)。末尾に1枚出す指定は
	 * 「空の箱に{@code page-break-before:always}」の形になります——
	 * {@code files/unittest/0120-float/float-break-always.html}が使っている
	 * 形で、特性値({@code files/unittest/blank-page-characterization.txt})に
	 * 白紙ページとして載っているのもこれです。
	 * </p>
	 */
	private static final String FORCED_TRAILING = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="200pt"?>
			<?jp.cssj.property name="output.page-height" value="200pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 12pt/1.2 serif}
			p,div{margin:0;padding:0}
			</style></head><body>
			<p>T0</p>
			<div style="page-break-before:always;width:50pt;height:50pt"> </div>
			</body></html>
			""";

	/**
	 * 境界3b: 縦組みの末尾に、残り幅より大きい空の table box がある形。
	 *
	 * <p>
	 * fuzz seed 5141 の縮小形。修正前は空表が {@code IBox.paintsAnything()}
	 * の安全側既定値を返すため、2ページ目が白紙のまま残った。表に内容・背景・
	 * 罫線がない場合だけ落とし、先行する本文は残す。
	 * </p>
	 */
	private static final String EMPTY_TABLE_TRAILING = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="595pt"?>
			<?jp.cssj.property name="output.page-height" value="842pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{font:normal 11pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<div style="width:502pt">T0</div>
			<div style="display:table;width:74pt"><div></div></div>
			</body></html>
			""";

	/**
	 * 境界4: 境界2と同じ文書に、<b>面の目印</b>だけを足したもの。
	 *
	 * <p>
	 * この文書は縦組みなので右綴じ(1ページ目が verso)です。
	 * {@code @page:left} のマージンボックスは verso のページにだけ "VERSO" を
	 * 刷るので、<b>どのページが verso か</b>が表示リストに直接現れます。
	 * マージンボックスは版面(内容領域)を変えないため、境界2の
	 * レイアウトはそのままです——白紙ページも同じところにできます。
	 * </p>
	 *
	 * <p>
	 * 目印を {@code :left} 側に置くのは、落とされる白紙ページが {@code :right}
	 * だからです。マージンボックスの宣言があるページは「刷るものがある」と
	 * 見なして落とさないので、{@code :right} 側に置くと検査したい discard
	 * 自体が起きなくなります。
	 * </p>
	 */
	private static final String MID_DOCUMENT_WITH_SIDE_MARK = AUTO_MID_DOCUMENT.replace("@page{margin:5pt}",
			"@page{margin:5pt}\n@page:left{@top-center{content:\"VERSO\"}}");

	/**
	 * 境界4b: 境界4の文書の末尾に {@code page-break-before:left}(verso指定)。
	 *
	 * <p>
	 * 面の追跡は<b>2か所</b>にあります。{@code PassContext}の面
	 * ({@code @page:left/:right}のスタイル選択)と、{@code RootBuilder}の
	 * {@code pageSide}(左右改ページの判断)です。境界4は前者を、こちらは
	 * 後者を検査します——落としたページで {@code pageSide} を進めてしまうと、
	 * verso指定の改ページが<b>recto</b>のページへ着きます。
	 * </p>
	 */
	private static final String MID_DOCUMENT_FORCED_VERSO = MID_DOCUMENT_WITH_SIDE_MARK.replace("</body></html>",
			"<div style=\"page-break-before:left\"><p>T20</p></div>\n</body></html>");

	public void testAutomaticTrailingBlankPageIsNotEmitted() throws Exception {
		final Pages pages = convert("auto-trailing", AUTO_TRAILING);
		pages.assertNoBlank();
		pages.assertTokens(19);
		assertEquals("auto-trailing: ページ数", 1, pages.count());
	}

	public void testAutomaticMidDocumentBlankPageIsNotEmitted() throws Exception {
		// 修正前は4ページで2枚目が白紙(実測)
		final Pages pages = convert("auto-mid", AUTO_MID_DOCUMENT);
		pages.assertNoBlank();
		pages.assertTokens(20);
		assertEquals("auto-mid: ページ数", 3, pages.count());
	}

	public void testForcedTrailingBlankPageIsKept() throws Exception {
		final Pages pages = convert("forced-trailing", FORCED_TRAILING);
		pages.assertTokens(1);
		assertEquals("forced-trailing: ページ数", 2, pages.count());
		assertEquals("forced-trailing: 意図した白紙が消えた", List.of(2), pages.blanks());
	}

	public void testTrailingEmptyTableDoesNotCreateBlankPage() throws Exception {
		final Pages pages = convert("empty-table-trailing", EMPTY_TABLE_TRAILING);
		pages.assertNoBlank();
		pages.assertTokens(1);
		assertEquals("empty-table-trailing: ページ数", 1, pages.count());
	}

	/**
	 * 境界4: <b>落としたページは面(recto/verso)を消費しない。</b>
	 *
	 * <p>
	 * 既定は両面印刷({@code output.print-mode=double-side})なので、出力された
	 * ページの面は<b>1枚ずつ交互</b>でなければなりません。落としたページが面を
	 * 消費すると、そこから先が全部裏返り、両面印刷の面付けが<b>静かに</b>
	 * 壊れます——刷り上がるまで誰も気づきません。
	 * </p>
	 *
	 * <p>
	 * 実測(2026-07-28): {@code StyleBuilder.discardPage()}の
	 * {@code setPageSide}の行を外すと、"VERSO" が
	 * <b>1,2ページ目</b>(=verso が2枚続く)に出ます。正しくは<b>1,3ページ目</b>。
	 * </p>
	 */
	public void testDiscardedPageDoesNotConsumeAPageSide() throws Exception {
		final Pages pages = convert("mid-side-mark", MID_DOCUMENT_WITH_SIDE_MARK);
		pages.assertNoBlank();
		pages.assertTokens(20);
		assertEquals("mid-side-mark: ページ数", 3, pages.count());
		final List<Integer> verso = pages.pagesContaining("VERSO");
		assertEquals("mid-side-mark: 出力されたページの面が交互になっていない"
				+ " ——落としたページが面を消費している", List.of(1, 3), verso);
	}

	/**
	 * 境界4b: 落としたページは<b>左右改ページの面の勘定</b>も進めない。
	 *
	 * <p>
	 * 実測(2026-07-28): {@code RootBuilder.pageBreak}の {@code emitted &&} を
	 * 外すと、{@code page-break-before:left} の内容が<b>4ページ目</b>——
	 * "VERSO" の刷られない recto ——へ着きます(全4ページ)。正しくは
	 * 5ページ目(verso)で、4ページ目は丁合合わせの白紙です。
	 * </p>
	 */
	public void testForcedVersoBreakStillLandsOnAVerso() throws Exception {
		final Pages pages = convert("mid-forced-verso", MID_DOCUMENT_FORCED_VERSO);
		pages.assertTokens(21);
		assertEquals("mid-forced-verso: ページ数", 5, pages.count());
		// 4ページ目は丁合合わせの強制白紙なので残る
		assertEquals("mid-forced-verso: 丁合合わせの白紙が消えた", List.of(4), pages.blanks());
		final int landed = pages.pagesContaining("T20").get(0);
		assertTrue("mid-forced-verso: page-break-before:left が recto のページ(" + landed + ")へ着いた"
				+ " ——落としたページが面を消費している", pages.pagesContaining("VERSO").contains(landed));
	}

	/** 1文書分のページ表示リスト。 */
	private record Pages(String name, List<String> dumps) {
		int count() {
			return this.dumps.size();
		}

		/** 表示リストが空(描画命令ゼロ)のページ番号(1起点)。 */
		List<Integer> blanks() {
			final List<Integer> blanks = new ArrayList<>();
			for (int i = 0; i < this.dumps.size(); ++i) {
				boolean drew = false;
				for (final String line : this.dumps.get(i).split("\n")) {
					final String t = line.trim();
					if (!t.isEmpty() && !t.startsWith("drawer")) {
						drew = true;
						break;
					}
				}
				if (!drew) {
					blanks.add(i + 1);
				}
			}
			return blanks;
		}

		void assertNoBlank() {
			final List<Integer> blanks = this.blanks();
			assertTrue(this.name + ": 白紙ページ " + blanks + " (全" + this.count() + "ページ)", blanks.isEmpty());
		}

		/** T0..T(n-1) が全部どこかのページに現れること(内容を捨てて白紙を消す退行の検出)。 */
		void assertTokens(final int tokenCount) {
			final String all = String.join("", this.dumps);
			final List<String> lost = new ArrayList<>();
			for (int i = 0; i < tokenCount; ++i) {
				if (!containsToken(all, "T" + i)) {
					lost.add("T" + i);
				}
			}
			assertTrue(this.name + ": 内容が失われた " + lost, lost.isEmpty());
		}

		/** その文字列を含むページ番号(1起点)を全部返します。 */
		List<Integer> pagesContaining(final String text) {
			final List<Integer> found = new ArrayList<>();
			for (int i = 0; i < this.dumps.size(); ++i) {
				if (this.dumps.get(i).contains(text)) {
					found.add(i + 1);
				}
			}
			return found;
		}

		/** {@code T1} が {@code T19} に一致しないよう、後ろの数字まで見る。 */
		private static boolean containsToken(final String text, final String token) {
			for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + 1)) {
				final int end = at + token.length();
				if (end >= text.length() || !Character.isDigit(text.charAt(end))) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * 文書を変換し、ページごとの表示リストを返します。
	 *
	 * <p>
	 * 変換は<b>別スレッド</b>で走らせて時間で打ち切ります——改ページが進まない
	 * 退行はテストごと固まるため、失敗として見えるようにする必要があります。
	 * </p>
	 */
	private static Pages convert(final String name, final String html) throws Exception {
		final File dir = new File("local/blank-page-discard/" + name);
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
		}, "blank-page-discard-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}

		final File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", files);
		assertTrue(name + ": ページが1枚も出ていない", files.length > 0);
		java.util.Arrays.sort(files);
		final List<String> dumps = new ArrayList<>(files.length);
		for (final File f : files) {
			dumps.add(java.nio.file.Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return new Pages(name, dumps);
	}
}
