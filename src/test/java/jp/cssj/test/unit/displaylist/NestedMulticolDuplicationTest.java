package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <b>同じ内容を同じページに二度描かない</b>ことを固定します(2026-07-28新設)。
 *
 * <p>
 * 50,000シードの掃過で最後まで残っていた欠陥種別「内容の複製」(160件)の
 * 実測から、<b>4つの独立した経路</b>が同じ結末——同じ文字が同じ紙に二度——に
 * 至ることが分かりました。どれも<b>入れ子の段組</b>で表に出ます。段組の
 * 組み直し({@code ColumnsContainer.restyle})が<b>全ての段を一本に
 * 組み直す</b>ため、普段は「前のページに残って二度と触られない」前断片が、
 * 継続断片と<b>同じ組み直しの中で両方とも再開される</b>からです。
 * </p>
 *
 * <ol>
 * <li><b>切断済み前断片のソース再生</b>({@code seed 347}):
 * {@code SourceAnchor}は切断しても<b>前断片の側に残り続ける</b>
 * (継続断片はレシピ構築なので最初から持たない)。前断片を
 * {@code stampRanges}が「丸ごと再生できる閉部分木」と刻印し、
 * {@code replayFromSource}が<b>要素全体</b>を組み直すため、継続断片の
 * 再開と二重になる。{@code AbstractBox.markFragmented()}と
 * {@code isSourceReplayable()}で塞ぐ。</li>
 *
 * <li><b>切断済みテキストの尾部再生</b>({@code seed 118665}):
 * {@code replayTextFrom}は{@code breakToken}の文字位置から<b>ソースの
 * 末尾まで</b>を流す。断片が流れの最後なら正しいが、段の組み直しでは
 * 先頭の段の断片も同じ走行の中で再開されるので、後続の段の分まで組む。
 * {@code ColumnsContainer.restyle}の間は尾部再生を封じる
 * ({@code FlowContainer.pushTailSeal})。</li>
 *
 * <li><b>MOVE の目印の取り違え</b>({@code seed 739}):
 * {@code ColumnsContainer.splitPageAxis}は切断を<b>最終段へ委譲</b>して
 * 結果をそのまま返すので、「全部移動した」の目印は段組コンテナ自身では
 * なく<b>最終段</b>になる。{@code splitForContinuation}は
 * {@code this.container}(=段組コンテナ)とだけ比べていたため、最終段を
 * 「残余コンテナ」と誤読し、<b>段組の中に残ったまま</b>継続断片としても
 * 組んでいた。{@code AbstractContainerBox.splitMoveSentinel()}で揃える。</li>
 *
 * <li><b>フロートを含む部分木のソース再生</b>({@code seed 29708}):
 * フロートは集約で<b>段のコンテナへ引き上げられる</b>ので、部分木が丸ごと
 * 移動しても元の段に残る。その部分木をソースから再生すると、引き上げ
 * られた側とあわせて二度組まれる。{@code stampRanges}に
 * {@code containsFloat}ゲートを足す
 * ({@code canReplayChildren}/{@code replayTextTail}は最初から持っていた)。</li>
 * </ol>
 *
 * <p>
 * <b>文書はここで組み立てます</b>——外部ファイルにすると相対パスの画像参照で
 * 判定が変わる事故を起こします(docs/LESSONS.md §6.9h)。経路2だけは
 * 「1段に収まらない画像」が欠陥の引き金なので画像を外せませんでした。
 * 参照はここで{@code File.toURI()}から<b>絶対URI</b>を組み立てます——
 * 文書を置くディレクトリが変わっても解決先が動かないようにするためです。
 * </p>
 *
 * <p>
 * <b>複製がないことだけでなくトークンの残存も検査します。</b> 複製は
 * 「両方捨てる」ことでも消せるので、それでは退行の検出になりません
 * (内容の消失は複製よりはるかに悪い)。
 * </p>
 */
public class NestedMulticolDuplicationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 打ち切り時間。実測は1件あたり1秒未満。 */
	private static final long WATCHDOG_MS = 60_000L;

	/** 表示リストのテキスト描画行から中身を取り出す。 */
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** 検査対象のトークン(箇条書きの番号等、欠陥に無関係な記号は数えない)。 */
	private static final Pattern TOKEN = Pattern.compile("^T[0-9]+$");

	public NestedMulticolDuplicationTest(String name) {
		super(name);
	}

	/** 経路1: 切断済み前断片のソース再生(縦書き・二重の段組・{@code <ol>})。 */
	private static final String SPLIT_FRAGMENT_REPLAY = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 7pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<div style="column-count:2">
			<div style="column-count:2">
			<span style="display:inline-block;width:42pt"></span>
			<ol>
			T24
			<li>T25</li>
			</ol>
			</div>
			</div>
			</body></html>
			""";

	/**
	 * 経路2: 切断済みテキストの尾部再生。{@code @IMG@}は
	 * {@link #imageURI()}で置き換えます。
	 */
	private static final String TEXT_TAIL_REPLAY = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:5pt}
			body{margin:0;font:normal 12pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<div style="column-count:4">
			<div style="column-count:4">
			<img src="@IMG@" style="display:block;width:248pt" />
			T0 T1 T2 T3 T4
			</div>
			</div>
			<div style="page-break-inside:avoid">
			<ol style="list-style-position:inside">
			<li></li>
			<li></li>
			</ol>
			<span style="font-size:16pt">T9</span>
			<p>T10 T11 T12 T13 </p>
			</div>
			</body></html>
			""";

	/** 経路3: MOVE の目印の取り違え(3段の中の2段)。 */
	private static final String MOVE_SENTINEL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="595pt"?>
			<?jp.cssj.property name="output.page-height" value="842pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{font:normal 9pt/1.2 serif}
			</style></head><body>
			<div style="column-count:3">
			T2
			<div style="column-count:2">
			T4
			<p></p>
			T6
			</div>
			</div>
			</body></html>
			""";

	/** 経路4: フロートを含む部分木のソース再生。 */
	private static final String FLOAT_SUBTREE_REPLAY = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="60pt"?>
			<?jp.cssj.property name="output.page-height" value="60pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<span style="display:inline-block;width:22pt">T2</span>
			<div style="column-count:2">
			<div style="border:1pt solid black">
			<div style="float:left">
			T3
			T4
			</div>
			</div>
			T5
			</div>
			</body></html>
			""";

	/**
	 * 経路2が使う画像の絶対URIです。作業ディレクトリはgradleが
	 * {@code rootProject.projectDir}に固定しています。
	 */
	private static String imageURI() {
		final File png = new File("files/unittest/red.png");
		assertTrue("テスト画像が見つからない: " + png.getAbsolutePath(), png.isFile());
		return png.toURI().toString();
	}

	public void testSplitFragmentIsNotReplayedFromSource() throws Exception {
		assertNoDuplication("split-fragment-replay", SPLIT_FRAGMENT_REPLAY, "T24", "T25");
	}

	public void testSplitTextTailIsNotReplayedToSourceEnd() throws Exception {
		assertNoDuplication("text-tail-replay", TEXT_TAIL_REPLAY.replace("@IMG@", imageURI()), "T0", "T1", "T2", "T3",
				"T4", "T9", "T10", "T11", "T12", "T13");
	}

	public void testMovedLastColumnIsNotAlsoAContinuation() throws Exception {
		assertNoDuplication("move-sentinel", MOVE_SENTINEL, "T2", "T4", "T6");
	}

	public void testSubtreeWithFloatIsNotReplayedFromSource() throws Exception {
		assertNoDuplication("float-subtree-replay", FLOAT_SUBTREE_REPLAY, "T2", "T3", "T4", "T5");
	}

	/**
	 * 変換して、(1) 同じトークンが同じページに二度描かれないこと、
	 * (2) 期待するトークンが全部どこかのページに現れること、を検査します。
	 *
	 * @param name     作業ディレクトリ名
	 * @param html     文書
	 * @param expected 文書が持つトークン
	 */
	private static void assertNoDuplication(final String name, final String html, final String... expected)
			throws Exception {
		final File dir = new File("local/nested-multicol-dup/" + name);
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
		}, "nested-multicol-dup-" + name, 64L * 1024 * 1024);
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

		final List<String> duplicated = new ArrayList<>();
		final java.util.Set<String> seen = new java.util.HashSet<>();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
			final Map<String, Integer> counts = new LinkedHashMap<>();
			final Matcher m = TEXT.matcher(dump);
			while (m.find()) {
				for (final String word : m.group(1).trim().split("\\s+")) {
					if (TOKEN.matcher(word).matches()) {
						counts.merge(word, 1, Integer::sum);
					}
				}
			}
			for (final Map.Entry<String, Integer> e : counts.entrySet()) {
				seen.add(e.getKey());
				if (e.getValue() > 1) {
					duplicated.add(e.getKey() + "x" + e.getValue() + "@p" + (i + 1));
				}
			}
		}
		assertTrue(name + ": 内容が複製された " + duplicated + " (全" + pages.length + "ページ)", duplicated.isEmpty());

		// 複製は「両方捨てる」ことでも消せる。それが退行として見えるように
		final List<String> lost = new ArrayList<>();
		for (final String token : expected) {
			if (!seen.contains(token)) {
				lost.add(token);
			}
		}
		assertTrue(name + ": 内容が失われた " + lost, lost.isEmpty());
	}
}
