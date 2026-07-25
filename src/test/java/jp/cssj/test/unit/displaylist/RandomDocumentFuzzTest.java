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
	 * <b>既知の未解決</b>: 末尾に空ページが1枚余分に出るシード
	 * (2026-07-25、本テスト自身が発見)。3件とも症状は同じ——幅より広い
	 * 内容を持つフロートがはみ出したあと、全内容が最終ページに収まって
	 * いるのに、さらに空ページが1枚出る。
	 *
	 * <p>
	 * <b>原因は特定済み</b>(2026-07-26): {@code BreakableBuilder.endFlowBlock}
	 * の「ルートボックス内の浮動ボックスを切断」ループが、
	 * {@code pageAxis = getPageLimit() + 1}と高さを人為的に膨らませて
	 * {@code autoBreak()}を呼ぶ。{@code breakFloats}が空でない状態で
	 * ルート直下の最後のブロックが終わると、<b>必ず1ページ余分に作られる</b>。
	 * 通常はその新ページに浮動体の残余が載るので正しいが、
	 * 「浮動体より広い内容が既にはみ出して描き終わっている」場合は
	 * 残余に描くものがなく、空ページだけが残る。
	 * </p>
	 *
	 * <p>
	 * 直すには「保留中の浮動体が実際に描くものを持つか」を分割前に判定する
	 * 必要があり、排除域機構(必須4機能の一つ)の再設計を伴う。
	 * 影響は末尾1ページのみで、内容の消失はない。<b>既知として除外</b>し、
	 * <b>これ以外のシードが落ちたら失敗</b>させる(=新しい退行だけを検出)。
	 * 解消したらこの集合を空にすること。
	 */
	private static final java.util.Set<Integer> KNOWN_TRAILING_BLANK_PAGE = java.util.Set.of(35);

	/**
	 * <b>既知の未解決</b>: 変換が例外で終わるシード(2026-07-26、
	 * 語彙を広げた掃過で発見)。2種類の不変条件違反がある。
	 *
	 * <ul>
	 * <li><b>textBuilderが開いたまま</b>ブロック境界を越える
	 * ({@code BreakableBuilder}の{@code startFlowBlock}/{@code endFlowBlock}/
	 * {@code flush}のassert)。strictで333文書に1件</li>
	 * <li><b>flowStackの深さと継続の深さが食い違う</b>
	 * ({@code RootBuilder.pageBreak}の"break flow failed")。strictで750文書に1件</li>
	 * </ul>
	 *
	 * <p>
	 * <b>本番ではクラッシュしない</b>——assertionはテストでのみ有効。かわりに
	 * 不変条件が破れた状態で処理が続く。出力への影響は未評価。
	 * どちらも改ページ・継続機構の中枢なので、原因を特定してから直す。
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

	/** 失敗メッセージから種別(defect class)を粗く取り出す。 */
	private static String classify(final Throwable t) {
		// 捕捉するのはラッパ(AssertionError)なので、**cause鎖の全メッセージ**を
		// 連結して判定する。t.getMessage()だけを見ると常にラッパの文言に
		// なり、種別が1つに潰れる(2026-07-26に踏んだ)
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
		assertTrue("白紙ページ " + blanks + " (" + html + ")", blanks.isEmpty());
		// 不変条件4: 内容が失われない
		final String all = String.join("", seen);
		final List<String> lost = new ArrayList<>();
		for (final String token : doc.tokens) {
			if (!all.contains(token)) {
				lost.add(token);
			}
		}
		assertTrue("内容が失われた " + lost + " (" + html + ")", lost.isEmpty());
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

	private record Generated(String html, List<String> tokens) {
	}

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
		return new Generated(s.toString(), tokens);
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
