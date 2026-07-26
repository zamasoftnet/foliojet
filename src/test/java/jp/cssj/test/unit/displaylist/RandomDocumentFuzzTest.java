package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
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
 * ランダムに生成した文書で<b>絶対要件</b>を検査します(2026-07-25新設)。
 *
 * <p>
 * これまでの検証は「人間が書いた固定の文書」(単体コーパス414文書・視覚
 * コーパス591文書)に依存しており、<b>誰も思いつかなかった組み合わせ</b>は
 * 原理的に踏めませんでした。本テストは入力空間を直接サンプリングします。
 * レビューが推定するのは「レビューで見つかりうる欠陥」だけなので、
 * これは<b>別の母集団</b>を測る手段です
 * (`copperpdf4/docs/REVIEW-STATISTICS.md` の観点一覧を参照)。
 * </p>
 *
 * <h2>検査する不変条件</h2>
 *
 * <ol>
 * <li><b>例外で中断しない</b></li>
 * <li><b>停止する</b>(watchdog内に終わる)</li>
 * <li><b>ページ数が有界</b>(内容量に対して爆発しない)</li>
 * <li><b>内容が失われない</b>——文書に埋めた一意なトークンが、すべて
 * 出力の表示リストに現れる。STRICTモード限定(下記)</li>
 * <li><b>意図しない白紙ページがない</b>——STRICTモード限定</li>
 * <li><b>説明のつかない紙面外への配置がない</b>——はみ出し量が文書中の
 * 最大の明示サイズの2倍を超えない。CSSの{@code overflow}既定は
 * {@code visible}なので「紙面内」は要求できない。STRICTモード限定</li>
 * </ol>
 *
 * <h2>2つのモード</h2>
 *
 * <p>
 * <b>STRICT</b>は「作者が白紙や消失を意図しようがない」部分集合だけを
 * 生成します——絶対配置・{@code visibility:hidden}・強制改ページ・
 * {@code overflow:hidden}を使いません。ここでは不変条件4・5まで検査できます。
 * </p>
 *
 * <p>
 * <b>WILD</b>はそれらも含めて生成し、不変条件1〜3だけを検査します
 * (意図した白紙・意図したはみ出しと区別がつかないため)。
 * </p>
 *
 * <h2>実行</h2>
 *
 * <p>
 * 既定は各モード{@value #DEFAULT_SEEDS}シードの回帰用。掃過するときは
 * {@code -Dfoliojet.fuzzSeeds=2000} のように増やす(件数はそのまま
 * 統計的な主張の根拠になるので、実行数を記録すること)。
 * 失敗したシードは再現用にHTMLを{@code local/fuzz/}へ残します。
 * </p>
 */
public class RandomDocumentFuzzTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 既定のシード数(回帰用。掃過は -Dfoliojet.fuzzSeeds で増やす)。 */
	private static final int DEFAULT_SEEDS = 60;

	/** 1文書あたりの上限時間。通常は1秒未満で終わる。 */
	private static final long WATCHDOG_MS = 30_000L;

	/** ページ数の上限。生成する内容量から見て明らかに過大な値。 */
	private static final int MAX_PAGES = 300;

	/**
	 * 表示リストに現れる「文字として描かれたもの」を拾います。
	 *
	 * <p>
	 * <b>ルビは{@code RubyUnit["親文字" ruby="ふりがな"]}という別表記で出る</b>
	 * ため、{@code Text[...]}だけを見ると「消えた」と誤判定します
	 * (2026-07-26、生成器にルビを足して発覚。エンジンではなく
	 * <b>オラクル側の誤り</b>だった)。画像の{@code alt}は描かれないので、
	 * 生成器はaltへトークンを埋めません。
	 * </p>
	 */
	private static final Pattern TEXT_IN_DUMP = Pattern
			.compile("(?:Text|RubyUnit)\\[\"([^\"]*)\"(?: ruby=\"([^\"]*)\")?");

	public RandomDocumentFuzzTest(String name) {
		super(name);
	}

	public void testStrictDocumentsPreserveEverything() throws Exception {
		sweep(true);
	}

	public void testWildDocumentsNeverCrashOrHang() throws Exception {
		sweep(false);
	}

	private static int seedCount() {
		final String v = System.getProperty("foliojet.fuzzSeeds");
		return v == null ? DEFAULT_SEEDS : Integer.parseInt(v);
	}

	/**
	 * <b>既知の未解決</b>: 末尾に空ページが1枚余分に出るシード。
	 *
	 * <p>
	 * <b>2026-07-26に既定シードの範囲では解消したので空にした。</b>
	 * 原因は{@code BreakableBuilder.classifyFloatPlacement}が
	 * 「箱だけがはみ出しているのか、はみ出した先に描くものがあるのか」を
	 * 区別していなかったこと。区別を入れた
	 * ({@code paintsNothingBeyondPage})結果、6,000シードでの発生が
	 * <b>88件→47件</b>へ減り、既定60シードでは0件になった。
	 * </p>
	 *
	 * <p>
	 * <b>残り47件は別の機序</b>で、まだ縮小できていない。既定シードには
	 * 当たらないので回帰は緑のまま、掃過
	 * ({@code -Dfoliojet.fuzzReport=1 -Dfoliojet.fuzzSeeds=6000})でだけ
	 * 見える。**この集合へ安易に追加しないこと**——追加は「直せないと
	 * 判断した」という意思表示であり、既定の回帰から永久に隠れる。
	 * </p>
	 */
	private static final java.util.Set<Integer> KNOWN_TRAILING_BLANK_PAGE = java.util.Set.of();

	/**
	 * <b>既知の未解決</b>: 変換が例外で終わるシード(2026-07-26、
	 * 語彙を広げた掃過で発見)。2種類の不変条件違反がある。
	 *
	 * <ul>
	 * <li><b>textBuilderが開いたまま</b>ブロック境界を越える
	 * ({@code BlockBuilder.requireNoOpenTextBuilder})。strictで400文書に1件</li>
	 * <li><b>flowStackの深さと継続の深さが食い違う</b>
	 * ({@code RootBuilder.pageBreak}の"break flow failed")。strictで1,000文書に1件</li>
	 * </ul>
	 *
	 * <p>
	 * <b>どちらもfail closed済み</b>(2026-07-26に確認)。assertではなく
	 * {@code ContinuationInvariantViolationException}なので、<b>本番でも
	 * 変換が失敗する</b>——黙って壊れた出力を出すことはない。
	 * {@code -PnoAssertions}で掃過しても件数が変わらないことで確かめた。
	 * </p>
	 *
	 * <p>
	 * fail closed化する前は実際に内容が消えていた(seed 890で
	 * {@code column-count:3}のブロックが丸ごと落ちた)。どちらも改ページ・
	 * 継続機構の中枢なので、原因を特定してから直す。
	 * 再現手順は`copperpdf4/docs/PLAN.md`。
	 * </p>
	 */
	private static final java.util.Set<Integer> KNOWN_INVARIANT_VIOLATION = java.util.Set.of();

	/**
	 * 統計用の集計モード({@code -Dfoliojet.fuzzReport})。早期打ち切りを
	 * せず全シードを走らせ、<b>失敗の種別ごとの件数と初出シード</b>を
	 * 出力する。これが「あと何件残っているか」「次の失敗まで何回か」の
	 * 推定の入力になる(`copperpdf4/docs/REVIEW-STATISTICS.md` §8)。
	 */
	/**
	 * このシード数までは、シードごとに成果物(HTML・表示リスト)を残す。
	 * これを超える掃過では使い回して捨てる——数百万文書を残すと
	 * ディスクが保たないため。生成器は決定的なので、失敗したシードは
	 * 同じシードで再実行すれば再現できる。
	 */
	private static final int KEEP_ARTIFACTS_BELOW = 20_000;

	private static boolean reportMode() {
		return System.getProperty("foliojet.fuzzReport") != null;
	}

	/**
	 * <b>紙面に収まらない箱を含む文書</b>で白紙ページが出た、という印
	 * (2026-07-26新設)。失敗ではなく<b>除外</b>として扱う。
	 *
	 * <p>
	 * 2026-07-26のユーザー裁定: 「意図的にやらないとこうはならないと
	 * 言えるレアケースは、デザイナの責任として取りこぼしてよい」。
	 * 紙面より大きい不可分な箱を置いた文書は、エンジンがどう振る舞っても
	 * ——はみ出させるか、次ページへ送るか——版面が破綻している。
	 * </p>
	 *
	 * <p>
	 * <b>例外にしているのは、集計モードで件数を出すため。</b> 単に検査を
	 * 飛ばすと、除外が増えたことに気づけなくなる。除外の増加は
	 * 「生成器が変わった」か「本当の退行が除外に紛れ込んだ」かの
	 * どちらかであり、どちらも見逃してはならない。
	 * </p>
	 */
	private static final class ExcludedByOversizedBox extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByOversizedBox(final String message) {
			super(message);
		}
	}

	/** 失敗メッセージから種別(defect class)を粗く取り出す。 */
	private static String classify(final Throwable t) {
		// 捕捉するのはラッパ(AssertionError)なので、**cause鎖の全メッセージ**を
		// 連結して判定する。t.getMessage()だけを見ると常にラッパの文言に
		// なり、種別が1つに潰れる(2026-07-26に踏んだ)
		if (t instanceof ExcludedByOversizedBox) {
			// 除外の理由は同じ(版面が破綻した文書)だが、どちらの不変条件が
			// 引っかかったのかは残す——除外の内訳が変わったことに
			// 気づけなくなるため
			return String.valueOf(t.getMessage()).startsWith("白紙ページ") ? "(除外)版面が破綻した文書の白紙ページ"
					: "(除外)版面が破綻した文書の紙面外配置";
		}
		final StringBuilder chain = new StringBuilder();
		for (Throwable c = t; c != null; c = c.getCause()) {
			chain.append(String.valueOf(c.getMessage())).append(' ');
		}
		final String m = chain.toString();
		// 変換エラーは**メッセージの形**で種別を分ける。もとの
		// AssertionErrorのスタックはTranscoderExceptionへ包む段で
		// 失われており(causeにも入らない)、発生箇所では分けられない。
		// 種別を粗くすると「残り何件か」の推定が過小になる(2026-07-26)
		if (m.contains("break flow failed")) {
			return "不変条件: flowStack深さ≠継続深さ";
		}
		if (m.contains("Unexpected error.")) {
			return "不変条件: textBuilderが開いたまま";
		}
		if (m.contains("白紙ページ")) {
			return "白紙ページ";
		}
		if (m.contains("内容が失われた")) {
			return "内容の消失";
		}
		if (m.contains("紙面外への配置")) {
			return "紙面外への配置";
		}
		if (m.contains("停止しない")) {
			return "停止しない";
		}
		if (m.contains("ページ数が過大")) {
			return "ページ数過大";
		}
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		final String site = detailKey(t);
		return site != null ? site : c.getClass().getSimpleName();
	}

	/**
	 * 例外の**発生箇所**(スタックの最上位のfoliojetフレーム)まで見て
	 * 種別を分ける。同じ例外型でも別の欠陥なら別種として数えるため——
	 * ここを粗くすると「残り何件か」の推定が過小になる。
	 */
	private static String detailKey(final Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		for (final StackTraceElement e : c.getStackTrace()) {
			if (e.getClassName().startsWith("net.zamasoft.foliojet")) {
				final String cls = e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1);
				return c.getClass().getSimpleName() + "@" + cls + "." + e.getMethodName() + ":" + e.getLineNumber();
			}
		}
		return null;
	}

	/**
	 * 集計モードの並列度({@code -Dfoliojet.fuzzThreads})。既定は
	 * 「コア数-2」。数百万文書の掃過は並列化しないと現実的な時間に
	 * 収まらない(2026-07-26に「20年間エラーなし」を目標化した際に追加)。
	 *
	 * <p>
	 * 変換は文書ごとに独立で、エンジン自身もサーバ用途で並行変換を
	 * 前提にしている(状態はThreadLocal)。1文書=1スレッドという
	 * 既存の構造をそのまま横に並べるだけで、判定内容は変えない。
	 * </p>
	 */
	private static int sweepThreads() {
		final String v = System.getProperty("foliojet.fuzzThreads");
		if (v != null) {
			return Math.max(1, Integer.parseInt(v));
		}
		return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
	}

	/** 集計モードの並列掃過。判定は{@link #checkOne}で共通。 */
	private void sweepParallel(final boolean strict, final int seeds) throws Exception {
		final int threads = sweepThreads();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> classCount =
				new java.util.concurrent.ConcurrentHashMap<>();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsOf =
				new java.util.concurrent.ConcurrentHashMap<>();
		final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger(0);
		final long began = System.currentTimeMillis();
		final Thread[] workers = new Thread[threads];
		for (int w = 0; w < threads; ++w) {
			workers[w] = new Thread(() -> {
				for (;;) {
					final int seed = next.getAndIncrement();
					if (seed >= seeds) {
						return;
					}
					try {
						checkOne(seed, strict);
					} catch (final Throwable t) {
						final String k = classify(t);
						classCount.computeIfAbsent(k, x -> new java.util.concurrent.atomic.AtomicInteger())
								.incrementAndGet();
						final java.util.List<Integer> lst = seedsOf.computeIfAbsent(k,
								x -> java.util.Collections.synchronizedList(new ArrayList<>()));
						synchronized (lst) {
							if (lst.size() < 8) {
								lst.add(seed);
							}
						}
					}
				}
			}, "fuzz-sweep-" + (strict ? "s" : "w") + w);
			workers[w].setDaemon(true);
			workers[w].start();
		}
		for (final Thread t : workers) {
			t.join();
		}
		final long ms = System.currentTimeMillis() - began;
		System.out.println("[fuzzReport] mode=" + (strict ? "strict" : "wild") + " seeds=" + seeds + " threads="
				+ threads + " elapsed=" + (ms / 1000) + "s (" + String.format("%.2f", ms / (double) seeds)
				+ " ms/文書)");
		if (classCount.isEmpty()) {
			System.out.println("[fuzzReport]   失敗なし");
		}
		for (final String k : new java.util.TreeSet<>(classCount.keySet())) {
			System.out.println("[fuzzReport]   " + k + " : " + classCount.get(k).get() + "件 seeds=" + seedsOf.get(k));
		}
	}

	private void sweep(final boolean strict) throws Exception {
		final int seeds = seedCount();
		final boolean report = reportMode();
		if (report) {
			this.sweepParallel(strict, seeds);
			return;
		}
		final List<String> failures = new ArrayList<>();
		final List<Integer> knownStillFailing = new ArrayList<>();
		final java.util.TreeMap<String, int[]> classCount = new java.util.TreeMap<>();
		final java.util.TreeMap<String, java.util.List<Integer>> seedsOf = new java.util.TreeMap<>();
		for (int seed = 0; seed < seeds; ++seed) {
			final boolean known = !report && strict
					&& (KNOWN_TRAILING_BLANK_PAGE.contains(seed) || KNOWN_INVARIANT_VIOLATION.contains(seed));
			try {
				checkOne(seed, strict);
				if (known) {
					failures.add("seed=" + seed + ": 既知の未解決だったが通った。"
							+ "KNOWN_TRAILING_BLANK_PAGE から外すこと");
				}
			} catch (final ExcludedByOversizedBox excluded) {
				// 除外。集計モードでのみ数える(sweepParallel側で拾う)
				continue;
			} catch (final Throwable t) {
				if (report) {
					final String k = classify(t);
					classCount.computeIfAbsent(k, x -> new int[1])[0]++;
					final var lst = seedsOf.computeIfAbsent(k, x -> new ArrayList<>());
					if (lst.size() < 8) {
						lst.add(seed);
					}
					continue;
				}
				if (known) {
					knownStillFailing.add(seed);
					continue;
				}
				failures.add("seed=" + seed + " (" + (strict ? "strict" : "wild") + "): " + t);
				if (failures.size() >= 5) {
					break; // 最初の数件で十分。全件走らせても情報が増えない
				}
			}
		}
		if (report) {
			System.out.println("[fuzzReport] mode=" + (strict ? "strict" : "wild") + " seeds=" + seeds);
			if (classCount.isEmpty()) {
				System.out.println("[fuzzReport]   失敗なし");
			}
			for (final var e : classCount.entrySet()) {
				System.out.println("[fuzzReport]   " + e.getKey() + " : " + e.getValue()[0] + "件 seeds="
						+ seedsOf.get(e.getKey()));
			}
			return;
		}
		if (!knownStillFailing.isEmpty()) {
			System.out.println("[fuzz] 既知の未解決(末尾の空ページ): " + knownStillFailing);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void checkOne(final int seed, final boolean strict) throws Exception {
		final Generated doc = generate(seed, strict);
		// 長時間掃過(数百万文書)では、シードごとの成果物を残すとディスクが
		// 保たない。失敗したシードは同じシードで再実行すれば必ず再現する
		// (生成器は決定的)ので、成功したシードの成果物は捨ててよい。
		// 保存ディレクトリもシードで分けず使い回す(2026-07-26)
		final boolean keep = !reportMode() || seedCount() <= KEEP_ARTIFACTS_BELOW;
		final String slot = keep ? String.valueOf(seed) : Thread.currentThread().getName();
		final File html = new File("local/fuzz/" + (strict ? "strict" : "wild") + "-" + slot + ".html");
		html.getParentFile().mkdirs();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(html), StandardCharsets.UTF_8)) {
			w.write(doc.html);
		}

		final File outDir = new File("local/fuzz/dl-" + (strict ? "strict" : "wild") + "-" + slot);
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
		}, "fuzz-" + seed);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		// 不変条件2: 停止する
		assertFalse("停止しない (" + html + ")", worker.isAlive());
		// 不変条件1: 例外で中断しない
		if (failure[0] != null) {
			throw new AssertionError("変換が例外で終わった (" + html + ")", failure[0]);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("ページが1枚も出ていない (" + html + ")", pages);
		assertTrue("ページが1枚も出ていない (" + html + ")", pages.length > 0);
		// 不変条件3: ページ数が有界
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
				if (m.group(2) != null) {
					// ルビのふりがな側
					seen.add(m.group(2));
				}
			}
		}

		if (!strict) {
			return; // WILDは1〜3だけ
		}
		// 不変条件5: 意図しない白紙ページがない
		//
		// **紙面に収まらない箱を含む文書は除外する**(2026-07-26のユーザー裁定)。
		// エンジンがどう振る舞っても版面は破綻しており——はみ出させるか、
		// 次ページへ送るか——寸法を直すのは組版を指定した側の責任である。
		// 除外は「見なかったことにする」ではなく**別の種別として数える**:
		// 除外が増えたことに気づけなくなると、本当の退行を見落とす。
		if (!blanks.isEmpty()) {
			if (doc.beyondEngineControl()) {
				throw new ExcludedByOversizedBox("白紙ページ " + blanks + " (" + html + ")");
			}
			fail("白紙ページ " + blanks + " (" + html + ")");
		}
		// 不変条件4: 内容が失われない
		final String all = String.join("", seen);
		final List<String> lost = new ArrayList<>();
		for (final String token : doc.tokens) {
			if (!all.contains(token)) {
				lost.add(token);
			}
		}
		assertTrue("内容が失われた " + lost + " (" + html + ")", lost.isEmpty());
		// 不変条件6: 説明のつかない紙面外への配置がない
		assertNoUnexplainedOffPage(doc, pages, html);
	}

	/**
	 * <b>不変条件6</b>: 内容が、作者の指定では説明できないほど紙面の外へ
	 * 出ていないこと(2026-07-26新設)。
	 *
	 * <p>
	 * <b>なぜ「紙面内」では駄目か。</b>CSSの{@code overflow}の既定値は
	 * {@code visible}で、<b>箱からはみ出した内容を紙の外に描くのは正しい
	 * 挙動</b>である。生成器は60×60ptの紙に250ptの箱を置くような病的な
	 * 文書を作るので、素朴に「紙面内」を要求すると3000文書中356文書
	 * (11.9%)が引っかかり、そのほとんどが正当だった。
	 * </p>
	 *
	 * <p>
	 * <b>そこで「作者が指定した大きさで説明できるか」で切る。</b> はみ出し量が
	 * 文書中の最大の明示サイズの2倍以内なら、指定の帰結として説明できる
	 * ものとして見逃す。この基準で切ると<b>横書きは1件も残らず、縦書きだけ
	 * 19件残った</b>(3000文書)。書字方向でこれほど非対称なら、原因は
	 * 生成器ではなく実装側にある。詳細は
	 * `copperpdf4/docs/REVIEW-STATISTICS.md` §12。
	 * </p>
	 *
	 * <p>
	 * 検査4「内容が失われない」はトークンが表示リストに<b>現れるか</b>しか
	 * 見ないので、紙面外に描かれた内容を合格させる。ここはその穴を埋める。
	 * </p>
	 */
	private static void assertNoUnexplainedOffPage(final Generated doc, final File[] pages, final File html)
			throws Exception {
		final double slack = 2 * doc.maxExplicitSize();
		double worst = 0;
		String worstAt = null;
		for (final File page : pages) {
			final String dump = Files.readString(Path.of(page.toURI()), StandardCharsets.UTF_8);
			final Matcher m = POS_IN_DUMP.matcher(dump);
			while (m.find()) {
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				// 紙面をまるごと1枚分はみ出して初めて数える(端の1ptは論外に
				// してよいが、そこを厳しくすると罫線の丸めで揺れる)
				final double over = Math.max(Math.max(-x - doc.pageWidth(), x - 2 * doc.pageWidth()),
						Math.max(-y - doc.pageHeight(), y - 2 * doc.pageHeight()));
				if (over > worst) {
					worst = over;
					worstAt = "x=" + x + " y=" + y + " " + page.getName();
				}
			}
		}
		if (worst <= slack) {
			return;
		}
		final String detail = "紙面外への配置 " + Math.round(worst) + "pt (紙面" + Math.round(doc.pageWidth()) + "x"
				+ Math.round(doc.pageHeight()) + "pt, 最大明示サイズ" + Math.round(doc.maxExplicitSize()) + "pt, " + worstAt
				+ ") (" + html + ")";
		// 白紙ページと同じ除外基準を適用する(2026-07-26)。版面が破綻して
		// いる文書ではエンジンの振る舞いを問えない
		if (doc.beyondEngineControl()) {
			throw new ExcludedByOversizedBox(detail);
		}
		fail(detail);
	}

	private static void convert(final File html, final File outDir) throws Exception {
		// 出力先はスレッド単位。システムプロパティだとプロセス全体で共有され、
		// 並列掃過でダンプ先が互いに上書きされる(2026-07-26)
		try (AutoCloseable scope = DisplayListDumper.scopedDir(outDir.getPath())) {
			final File pdf = new File(outDir, "out.pdf");
			try (OutputStream out = new FileOutputStream(pdf)) {
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
		}
	}

	// ------------------------------------------------------------------
	// 生成器
	// ------------------------------------------------------------------

	/**
	 * @param pageWidth       紙面の幅(pt)
	 * @param pageHeight      紙面の高さ(pt)
	 * @param maxExplicitSize この文書が指定した{@code width}/{@code height}の
	 *                        最大値(pt)。不変条件6で「作者が指定した大きさの
	 *                        帰結として説明できるはみ出しか」を判定するのに使う
	 */
	private record Generated(String html, List<String> tokens, double pageWidth, double pageHeight,
			double maxExplicitSize, boolean oversized, boolean tinyPage) {

		/** 版面が破綻していて、エンジンの振る舞いを問えない文書か。 */
		boolean beyondEngineControl() {
			return this.oversized || this.tinyPage;
		}
	}

	/** 表示リストの1行から描画位置を拾う。 */
	private static final Pattern POS_IN_DUMP = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+)");

	/** 生成器が出す明示サイズ({@code width:120pt}等)。 */
	private static final Pattern EXPLICIT_SIZE = Pattern.compile("(?:width|height):(\\d+)pt");

	private static final Pattern EXPLICIT_WIDTH = Pattern.compile("width:(\\d+)pt");
	private static final Pattern EXPLICIT_HEIGHT = Pattern.compile("height:(\\d+)pt");
	private static final Pattern FONT_SIZE = Pattern.compile("(?:font-size:|font:normal )(\\d+)pt");
	private static final Pattern PAGE_MARGIN = Pattern.compile("@page\\{margin:(\\d+)pt");

	/** ページ寸法の候補(極端に小さいものを含む)。 */
	private static final int[][] PAGE_SIZES = { { 200, 200 }, { 300, 150 }, { 120, 400 }, { 595, 842 }, { 60, 60 } };

	private static final String[] WRITING_MODES = { "horizontal-tb", "vertical-rl", "vertical-lr" };

	private static Generated generate(final int seed, final boolean strict) {
		final Random r = new Random(seed * 7919L + (strict ? 1 : 2));
		final List<String> tokens = new ArrayList<>();
		final StringBuilder body = new StringBuilder();
		final int[] counter = { 0 };
		final int roots = 1 + r.nextInt(4);
		for (int i = 0; i < roots; ++i) {
			appendNode(body, r, 3, strict, tokens, counter);
		}
		final int[] size = PAGE_SIZES[r.nextInt(PAGE_SIZES.length)];
		final StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		s.append("<?jp.cssj.property name=\"output.page-width\" value=\"").append(size[0]).append("pt\"?>\n");
		s.append("<?jp.cssj.property name=\"output.page-height\" value=\"").append(size[1]).append("pt\"?>\n");
		s.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
		s.append("<title>fuzz ").append(seed).append("</title>\n<style>\n");
		s.append("@page{margin:").append(r.nextInt(3) * 5).append("pt}\n");
		s.append("body{margin:0;font:normal ").append(6 + r.nextInt(8)).append("pt/1.2 serif;writing-mode:")
				.append(WRITING_MODES[r.nextInt(WRITING_MODES.length)]).append("}\n");
		s.append("p,div,td{margin:0;padding:0}\n");
		s.append("table{border-collapse:").append(r.nextBoolean() ? "collapse" : "separate")
				.append(";table-layout:").append(r.nextBoolean() ? "auto" : "fixed").append("}\n");
		s.append("td{border:1pt solid black}\n");
		s.append("</style></head><body>\n");
		s.append(body);
		s.append("\n</body></html>\n");
		final String out = s.toString();
		// 生成器が出した明示サイズの最大値。生成箇所が複数に散っているので、
		// 引数で持ち回るより出来上がった文書から拾うほうが取りこぼさない
		double maxExplicit = 0;
		final Matcher em = EXPLICIT_SIZE.matcher(out);
		while (em.find()) {
			maxExplicit = Math.max(maxExplicit, Double.parseDouble(em.group(1)));
		}
		return new Generated(out, tokens, size[0], size[1], maxExplicit, isOversized(out, size),
				isTinyPage(out, size));
	}

	/**
	 * この文書が<b>紙面の内容領域に収まらない箱</b>を含むかを返します
	 * (2026-07-26新設)。
	 *
	 * <p>
	 * <b>収まらないものを置いた文書は、白紙ページの検査から除外します。</b>
	 * エンジンがどう振る舞っても版面は破綻しており(はみ出させるか、
	 * 次ページへ送るか)、寸法を直すのは組版を指定した側の責任だからです
	 * ——2026-07-26のユーザー裁定。
	 * </p>
	 *
	 * <p>
	 * 判定は生成器が出した値だけで行います。実測では、白紙ページを出す
	 * 33文書のうち<b>28文書</b>がこれに該当しました。残り5件のうち2件は
	 * 50x50pt(1.7cm角=切手より小さい)の紙で、これも実質同じ性質です。
	 * 追うべきは残り3件——190x190pt・110x390pt・280x130pt という
	 * <b>実在する寸法で、かつ全要素が紙面に収まる</b>文書です。
	 * </p>
	 *
	 * <p>
	 * <b>除外は「見なかったことにする」ではありません。</b> 集計モードでは
	 * 除外分も件数として出します——除外が増えたことに気づけなくなると、
	 * 本当の退行を見落とすからです。
	 * </p>
	 */
	private static boolean isOversized(final String html, final int[] pageSize) {
		final Matcher pm = PAGE_MARGIN.matcher(html);
		final double margin = pm.find() ? Double.parseDouble(pm.group(1)) : 0;
		final double contentWidth = pageSize[0] - 2 * margin;
		final double contentHeight = pageSize[1] - 2 * margin;
		if (maxOf(EXPLICIT_WIDTH, html) > contentWidth) {
			return true;
		}
		if (maxOf(EXPLICIT_HEIGHT, html) > contentHeight) {
			return true;
		}
		// フォントサイズは行の高さになるので、ページ方向の寸法と比べる
		return maxOf(FONT_SIZE, html) > contentHeight;
	}

	/**
	 * この文書の紙面が<b>そもそも組版できない大きさ</b>かを返します
	 * (2026-07-26新設)。
	 *
	 * <p>
	 * 基準は「内容領域が基準フォントサイズの{@value #MIN_PAGE_CHARS}倍
	 * (=約{@value #MIN_PAGE_CHARS}文字)に満たない軸がある」。生成器は
	 * 60x60ptの紙に13ptのフォントという文書を作る——<b>1行4文字</b>で、
	 * 行分割も浮動体も段組も意味のある版面にならず、エンジンがどう
	 * 振る舞っても正解がない。実在する最小の印刷物(ラベル・値札)でも
	 * この比率にはならない。
	 * </p>
	 *
	 * <p>
	 * 除外されるのは実質「60x60ptかつフォント8pt以上」だけで、
	 * 他の紙面(120x400・200x200・300x150・A4)は生成器が出す
	 * フォントサイズの範囲(6〜13pt)では該当しない。
	 * </p>
	 */
	private static boolean isTinyPage(final String html, final int[] pageSize) {
		final Matcher pm = PAGE_MARGIN.matcher(html);
		final double margin = pm.find() ? Double.parseDouble(pm.group(1)) : 0;
		final Matcher fm = FONT_SIZE.matcher(html);
		if (!fm.find()) {
			return false;
		}
		final double font = Double.parseDouble(fm.group(1));
		final double least = font * MIN_PAGE_CHARS;
		return pageSize[0] - 2 * margin < least || pageSize[1] - 2 * margin < least;
	}

	/** 「組版できる紙面」の下限を文字数で表したもの。 */
	private static final int MIN_PAGE_CHARS = 8;

	private static double maxOf(final Pattern pattern, final String html) {
		double max = 0;
		final Matcher m = pattern.matcher(html);
		while (m.find()) {
			max = Math.max(max, Double.parseDouble(m.group(1)));
		}
		return max;
	}

	/** 一意なトークン(行分割で割れないよう空白を含めない)。 */
	private static String token(final List<String> tokens, final int[] counter) {
		final String t = "T" + (counter[0]++);
		tokens.add(t);
		return t;
	}

	private static void appendNode(final StringBuilder s, final Random r, final int depth, final boolean strict,
			final List<String> tokens, final int[] counter) {
		if (depth <= 0) {
			s.append("<p>").append(token(tokens, counter)).append("</p>\n");
			return;
		}
		final int kind = r.nextInt(strict ? 12 : 14);
		switch (kind) {
		case 0 -> { // 段落(複数トークン)
			s.append("<p>");
			final int n = 1 + r.nextInt(6);
			for (int i = 0; i < n; ++i) {
				s.append(token(tokens, counter)).append(' ');
			}
			s.append("</p>\n");
		}
		case 1 -> { // 入れ子ブロック
			s.append("<div style=\"margin:").append(r.nextInt(8)).append("pt;padding:").append(r.nextInt(6))
					.append("pt;border:").append(r.nextInt(3)).append("pt solid black\">\n");
			appendChildren(s, r, depth, strict, tokens, counter);
			s.append("</div>\n");
		}
		case 2 -> { // フロート
			s.append("<div style=\"float:").append(r.nextBoolean() ? "left" : "right").append(";width:")
					.append(10 + r.nextInt(120)).append("pt\">\n");
			appendChildren(s, r, depth, strict, tokens, counter);
			s.append("</div>\n");
		}
		case 3 -> { // 表(rowspan/colspanつき)
			final int rows = 1 + r.nextInt(4);
			final int cols = 1 + r.nextInt(4);
			s.append("<table><tbody>\n");
			for (int y = 0; y < rows; ++y) {
				s.append("<tr>");
				for (int x = 0; x < cols; ++x) {
					s.append("<td");
					if (r.nextInt(4) == 0) {
						s.append(" colspan=\"").append(1 + r.nextInt(3)).append('"');
					}
					if (r.nextInt(4) == 0) {
						s.append(" rowspan=\"").append(1 + r.nextInt(3)).append('"');
					}
					s.append('>').append(token(tokens, counter)).append("</td>");
				}
				s.append("</tr>\n");
			}
			s.append("</tbody></table>\n");
		}
		case 4 -> { // 段組
			s.append("<div style=\"column-count:").append(2 + r.nextInt(3)).append(";column-gap:")
					.append(r.nextInt(20)).append("pt\">\n");
			appendChildren(s, r, depth, strict, tokens, counter);
			s.append("</div>\n");
		}
		case 5 -> { // 書字方向の入れ子
			s.append("<div style=\"writing-mode:").append(WRITING_MODES[r.nextInt(WRITING_MODES.length)])
					.append("\">\n");
			appendChildren(s, r, depth, strict, tokens, counter);
			s.append("</div>\n");
		}
		case 6 -> { // インラインブロック・大きいフォント(救済分割の入口)
			s.append("<p><span style=\"display:inline-block;width:").append(10 + r.nextInt(200)).append("pt;height:")
					.append(10 + r.nextInt(200)).append("pt\">").append(token(tokens, counter))
					.append("</span></p>\n");
		}
		case 7 -> { // リスト(マーカー・list-style)
			final String tag = r.nextBoolean() ? "ul" : "ol";
			s.append('<').append(tag).append(" style=\"list-style-position:")
					.append(r.nextBoolean() ? "inside" : "outside").append(";list-style-type:")
					.append(new String[] { "disc", "decimal", "lower-roman", "cjk-ideographic", "none" }[r.nextInt(5)])
					.append("\">\n");
			final int n = 1 + r.nextInt(4);
			for (int i = 0; i < n; ++i) {
				s.append("<li>").append(token(tokens, counter)).append("</li>\n");
			}
			s.append("</").append(tag).append(">\n");
		}
		case 8 -> { // ルビ(2026-07-25に行内の注釈付きテキストへ仕様変更)
			s.append("<p>");
			final int n = 1 + r.nextInt(3);
			for (int i = 0; i < n; ++i) {
				s.append("<ruby>").append(token(tokens, counter)).append("<rt>")
						.append(token(tokens, counter)).append("</rt></ruby>");
			}
			s.append("</p>\n");
		}
		case 9 -> { // 置換要素(画像。救済分割の本来の動機)
			// altは描かれないのでトークンにしない(オラクルが誤検出する)
			s.append("<p><img src=\"../../files/unittest/red.png\" alt=\"img\"")
					.append(" style=\"display:").append(r.nextBoolean() ? "block" : "inline")
					.append(";width:").append(10 + r.nextInt(250)).append("pt;height:")
					.append(10 + r.nextInt(250)).append("pt\" /></p>\n");
		}
		case 10 -> { // clear と極端なフォントサイズ(floatと絡ませる)
			s.append("<div style=\"clear:")
					.append(new String[] { "left", "right", "both" }[r.nextInt(3)]).append("\">")
					.append("<span style=\"font-size:").append(6 + r.nextInt(40)).append("pt\">")
					.append(token(tokens, counter)).append("</span></div>\n");
		}
		case 11 -> { // avoid ヒント(改ページ判定を揺さぶる)
			s.append("<div style=\"page-break-inside:avoid;margin:").append(r.nextInt(6)).append("pt\">\n");
			appendChildren(s, r, depth, strict, tokens, counter);
			s.append("</div>\n");
		}
		case 12 -> { // WILDのみ: 絶対配置
			s.append("<div style=\"position:absolute;top:").append(r.nextInt(300) - 50).append("pt;left:")
					.append(r.nextInt(300) - 50).append("pt\">").append("X").append("</div>\n");
		}
		default -> { // WILDのみ: 強制改ページ・非表示・overflow
			s.append("<div style=\"page-break-before:always;visibility:")
					.append(r.nextBoolean() ? "hidden" : "visible").append(";overflow:hidden\">X</div>\n");
		}
		}
	}

	private static void appendChildren(final StringBuilder s, final Random r, final int depth, final boolean strict,
			final List<String> tokens, final int[] counter) {
		final int n = 1 + r.nextInt(3);
		for (int i = 0; i < n; ++i) {
			appendNode(s, r, depth - 1, strict, tokens, counter);
		}
	}
}
