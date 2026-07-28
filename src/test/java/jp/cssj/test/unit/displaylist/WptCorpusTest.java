package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
 * WPT(web-platform-tests)のCSS文書を<b>不変条件1〜3だけ</b>にかけます。
 *
 * <p>
 * <b>opt-inです</b>({@code -Dfoliojet.wptDir=<path>})。指定が無ければ
 * 何もせず通ります——コーパスはこのリポジトリの外(既定
 * {@code F:\dev\wpt-css})にあり、CIや通常のフルテストで走らせるものでは
 * ないからです。
 * </p>
 *
 * <h2>なぜ1〜3だけなのか</h2>
 *
 * <p>
 * WPTは<b>仕様適合</b>を符号化したコーパスであり、この製品は仕様準拠を
 * 目的としていません(`docs/LESSONS.md` §1.2)。参照画像やセレクタ適合で
 * 判定すると、<b>意図的な非準拠</b>が大量に差分として出てきて、
 * 「直すべきもの」と「直さないと決めたもの」を区別できなくなります
 * (Acid2と同じ罠)。
 * </p>
 *
 * <p>
 * これに対し<b>不変条件1〜3</b>——例外で中断しない・停止する・ページ数が
 * 有界——は<b>仕様と無関係に</b>成り立つべき性質です。どんな入力に対しても
 * 落ちてはいけない。だからWPTを「未知の入力の供給源」としてだけ使います。
 * </p>
 *
 * <p>
 * 不変条件4/7/8(消失・読み順・複製)は<b>一意トークンが前提</b>なので
 * 適用できません。不変条件5(白紙)は、外部参照の解決失敗と区別が
 * つかないうちは適用しません({@code /fonts/ahem.css}のような絶対参照が
 * 241件ある)。
 * </p>
 *
 * <h2>紙面寸法</h2>
 *
 * <p>
 * WPT文書は{@code height:100px}のような寸法を<b>ビューポートで見る</b>
 * 前提で書かれており、既定のA4に流すと<b>ほとんどが1ページに収まって
 * しまう</b>——それではこの製品の欠陥領域(ページ分割)に触れません。
 * {@code -Dfoliojet.wptPageSize=120x120}(pt)で紙面を小さくできます。
 * 生成器が60x60ptを使うのと同じ理由です。
 * </p>
 *
 * <p>
 * <b>まず分割率を測ること。</b> このテストは種別ごとの件数だけでなく
 * <b>ページ数の分布</b>を報告します。分割が起きていなければ、この
 * コーパスは(この用途では)価値がありません。
 * </p>
 *
 * <pre>
 * ./gradlew test --tests '*WptCorpusTest*' --rerun --no-watch-fs -PtestHeap=4096m \
 *   -Dfoliojet.wptDir=/mnt/f/dev/wpt-css/css -Dfoliojet.wptLimit=50 \
 *   -Dfoliojet.wptPageSize=120x120
 * </pre>
 */
public class WptCorpusTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 打ち切り時間。{@link RandomDocumentFuzzTest}と同じ値。 */
	private static final long WATCHDOG_MS = 30_000L;

	/** 不変条件3の上限。{@link RandomDocumentFuzzTest}と同じ値。 */
	private static final int MAX_PAGES = 300;

	public void testWptInvariants() throws Exception {
		final String dir = System.getProperty("foliojet.wptDir");
		if (dir == null) {
			// opt-in。コーパスはリポジトリ外にある
			return;
		}
		final File root = new File(dir);
		assertTrue("WPTコーパスが見つからない: " + root.getAbsolutePath(), root.isDirectory());

		final List<File> docs = onlyList(root);
		final int limit = intProperty("foliojet.wptLimit", Integer.MAX_VALUE);
		System.out.println("[wpt] " + root.getAbsolutePath());
		System.out.println("[wpt] 対象 " + docs.size() + "件" + (limit < docs.size() ? "(先頭" + limit + "件を実行)" : ""));

		final String pageSize = System.getProperty("foliojet.wptPageSize");
		System.out.println("[wpt] 紙面 " + (pageSize == null ? "(既定)" : pageSize + "pt"));

		final TreeMap<String, AtomicInteger> classCount = new TreeMap<>();
		final TreeMap<String, List<String>> examples = new TreeMap<>();
		final TreeMap<String, AtomicInteger> pageBuckets = new TreeMap<>();
		final List<String> violations = new ArrayList<>();
		int ran = 0, split = 0;

		final File outDir = new File("local/wpt/dl");
		for (final File doc : docs) {
			if (ran >= limit) {
				break;
			}
			++ran;
			final Result r = check(doc, outDir, pageSize);
			if (r.failure == null) {
				pageBuckets.computeIfAbsent(bucket(r.pages), k -> new AtomicInteger()).incrementAndGet();
				if (r.pages > 1) {
					++split;
				}
			} else {
				classCount.computeIfAbsent(r.failure, k -> new AtomicInteger()).incrementAndGet();
				// **全件を残す**。違反はごく少数(実測17件/2,409)なので
				// 打ち切る理由がなく、打ち切ると「残りは同じだろう」という
				// 根拠のない推測を招く
				examples.computeIfAbsent(r.failure, k -> new ArrayList<>())
						.add(rel(root, doc) + (r.detail == null ? "" : " :: " + r.detail));
				violations.add(rel(root, doc));
			}
		}

		System.out.println("[wpt] 実行 " + ran + "件");
		System.out.println("[wpt] --- ページ数の分布(不変条件を通ったもの) ---");
		for (final java.util.Map.Entry<String, AtomicInteger> e : pageBuckets.entrySet()) {
			System.out.println("[wpt]   " + e.getKey() + " : " + e.getValue().get() + "件");
		}
		final int ok = pageBuckets.values().stream().mapToInt(AtomicInteger::get).sum();
		System.out.println("[wpt]   うち2ページ以上に分割 = " + split + "件 / " + ok + "件"
				+ (ok == 0 ? "" : String.format(Locale.ROOT, " (%.1f%%)", 100.0 * split / ok)));
		System.out.println("[wpt] --- 不変条件の違反 ---");
		if (classCount.isEmpty()) {
			System.out.println("[wpt]   なし");
		} else {
			for (final java.util.Map.Entry<String, AtomicInteger> e : classCount.entrySet()) {
				System.out.println("[wpt]   " + e.getKey() + " : " + e.getValue().get() + "件");
				for (final String x : examples.get(e.getKey())) {
					System.out.println("[wpt]       " + x);
				}
			}
		}

		// 違反した文書の一覧を残す。全件の再走(1コアで6分半)をせずに
		// 違反だけを再現できるよう、-Dfoliojet.wptOnly=<このファイル> で
		// 読み直せる形式(root からの相対パス1行1件)にする
		final File list = new File("local/wpt/violations.txt");
		list.getParentFile().mkdirs();
		Files.write(list.toPath(), violations);
		System.out.println("[wpt] 違反した文書の一覧: " + list.getPath() + " (" + violations.size() + "件)");

		// **報告だけで落とさない**のは既定の運用(掃過と同じ)。
		// -Dfoliojet.wptFailOnViolation=true で赤にできる
		if (Boolean.getBoolean("foliojet.wptFailOnViolation") && !classCount.isEmpty()) {
			fail("不変条件1〜3の違反が" + classCount.values().stream().mapToInt(AtomicInteger::get).sum() + "件");
		}
	}

	/** 1件の判定結果。{@code failure}が非nullなら不変条件違反。 */
	private record Result(int pages, String failure, String detail) {
	}

	/**
	 * 1件を変換して不変条件1〜3にかけます。
	 *
	 * <p>
	 * {@link RandomDocumentFuzzTest#checkDocument}と<b>同じ定義</b>を使います
	 * (watchdog 30秒・上限300ページ)。違う値を使うと「掃過では出ないのに
	 * WPTでは出る」が測定の差なのか実装の差なのか分からなくなります。
	 * </p>
	 */
	private static Result check(final File doc, final File outDir, final String pageSize) {
		outDir.mkdirs();
		final File[] old = outDir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}

		final Throwable[] failure = new Throwable[1];
		final DirectSession[] session = new DirectSession[1];
		// スタックサイズは掃過と揃える(深い入れ子でのStackOverflowを
		// 「実装の欠陥」と誤認しないため)
		final Thread worker = new Thread(null, () -> {
			try {
				convert(doc, outDir, session, pageSize);
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "wpt-" + doc.getName(), 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		try {
			worker.join(WATCHDOG_MS);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Result(0, "割り込まれた", null);
		}
		// 不変条件2: 停止する
		if (worker.isAlive()) {
			// 放置せず実際に止めにいく(掃過と同じ。止められないスレッドは
			// レイアウト1件分のヒープを抱えたまま残り、自己増幅的に詰まる)
			final DirectSession s = session[0];
			if (s != null) {
				try {
					s.abort(jp.cssj.cti2.CTISession.ABORT_FORCE);
					worker.join(5_000L);
				} catch (final Exception ignore) {
					// 中断要求が通らなくても報告へ進む
				}
			}
			if (worker.isAlive()) {
				worker.setPriority(Thread.MIN_PRIORITY);
			}
			return new Result(0, "停止しない(watchdog " + WATCHDOG_MS / 1000 + "秒超過)", null);
		}
		// 不変条件1: 例外で中断しない
		if (failure[0] != null) {
			return new Result(0, "例外で中断: " + failure[0].getClass().getSimpleName(), summarize(failure[0]));
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		final int count = pages == null ? 0 : pages.length;
		if (count == 0) {
			// **これは不変条件違反として数えない**——WPTには本文が空の
			// 文書(参照用の骨組みだけ)があり、白紙抑止が正しく効いた
			// 結果と区別できない(不変条件5を外したのと同じ理由)
			return new Result(0, null, null);
		}
		// 不変条件3: ページ数が有界
		if (count > MAX_PAGES) {
			return new Result(count, "ページ数が過大(>" + MAX_PAGES + ")", count + "ページ");
		}
		return new Result(count, null, null);
	}

	private static void convert(final File html, final File outDir, final DirectSession[] sessionOut,
			final String pageSize) throws Exception {
		try (AutoCloseable scope = DisplayListDumper.scopedDir(outDir.getPath())) {
			final File pdf = new File(outDir, "out.pdf");
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				sessionOut[0] = session;
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					if (pageSize != null) {
						final int x = pageSize.indexOf('x');
						session.property("output.page-width", pageSize.substring(0, x) + "pt");
						session.property("output.page-height", pageSize.substring(x + 1) + "pt");
					}
					CTISessionHelper.transcodeFile(session, html, "text/html", null);
				} finally {
					session.close();
				}
			}
		}
	}

	/**
	 * {@code -Dfoliojet.wptOnly=<path>}が指定されていれば、その一覧
	 * (root からの相対パス1行1件)だけを対象にします。無ければ
	 * {@link #collect}で全件を集めます。
	 *
	 * <p>
	 * 違反の再現・修正の反復に使います——全件は1コアで6分半かかるので、
	 * 17件の再走に毎回それを払うのは無駄です(`docs/LESSONS.md`
	 * 「反復を高速化せよ」)。
	 * </p>
	 */
	private static List<File> onlyList(final File root) throws Exception {
		final String only = System.getProperty("foliojet.wptOnly");
		if (only == null) {
			return collect(root);
		}
		final List<File> out = new ArrayList<>();
		for (final String line : Files.readAllLines(new File(only).toPath())) {
			final String s = line.trim();
			if (!s.isEmpty()) {
				out.add(new File(root, s));
			}
		}
		return out;
	}

	/**
	 * 対象文書を集めます。
	 *
	 * <p>
	 * <b>スクリプトとiframeを含む文書は除外します。</b> スクリプトはこの
	 * 製品が実行せず、iframeは別文書の読み込みなので、どちらも「何を
	 * 変換したか」が曖昧になります。
	 * </p>
	 */
	private static List<File> collect(final File root) throws Exception {
		final List<File> out = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root.toPath())) {
			for (final Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
				final String name = p.getFileName().toString();
				if (!name.endsWith(".html") && !name.endsWith(".xht") && !name.endsWith(".htm")) {
					continue;
				}
				final String s;
				try {
					s = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
				} catch (final Exception broken) {
					continue;
				}
				if (s.contains("<script") || s.contains("<iframe")) {
					continue;
				}
				out.add(p.toFile());
			}
		}
		out.sort(Comparator.comparing(File::getAbsolutePath));
		return out;
	}

	private static String bucket(final int pages) {
		if (pages <= 1) {
			return "1ページ";
		}
		if (pages <= 3) {
			return "2-3ページ";
		}
		if (pages <= 10) {
			return "4-10ページ";
		}
		if (pages <= 50) {
			return "11-50ページ";
		}
		return "51-" + MAX_PAGES + "ページ";
	}

	private static String summarize(final Throwable t) {
		final StringBuilder sb = new StringBuilder();
		for (Throwable c = t; c != null && sb.length() < 300; c = c.getCause()) {
			if (sb.length() > 0) {
				sb.append(" <- ");
			}
			sb.append(c.getClass().getSimpleName());
			if (c.getMessage() != null) {
				sb.append('(').append(c.getMessage().replace('\n', ' ')).append(')');
			}
		}
		return sb.toString();
	}

	private static String rel(final File root, final File f) {
		final String r = root.getAbsolutePath(), a = f.getAbsolutePath();
		return a.startsWith(r) ? a.substring(r.length() + 1) : a;
	}

	private static int intProperty(final String key, final int fallback) {
		final String v = System.getProperty(key);
		return v == null ? fallback : Integer.parseInt(v);
	}
}
