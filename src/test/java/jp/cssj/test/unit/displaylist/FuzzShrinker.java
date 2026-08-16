package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.test.unit.displaylist.RandomDocumentFuzzTest.Generated;

/**
 * ランダム文書ファザーが見つけた失敗を<b>自動で最小化</b>します(2026-07-28新設)。
 *
 * <h2>なぜ必要か</h2>
 *
 * <p>
 * 掃過は数分で終わるのに、<b>1件の診断に数時間かかる</b>。今季見つかった欠陥は
 * すべて人間かエージェントが手で縮小しており、その過程で<b>偽の最小形を5回</b>
 * 掴んでいる(`copperpdf4/docs/LESSONS.md` §3.15)。縮小は機械にやらせるべき
 * 作業で、かつ<b>機械にやらせるなら述語を正しく書くこと自体が本体</b>である。
 * </p>
 *
 * <h2>実行</h2>
 *
 * <pre>
 * ./gradlew test --rerun --tests "*RandomDocumentFuzzTest*" -Dfoliojet.fuzzShrink=149858 -q
 * ./gradlew test --rerun --tests "*RandomDocumentFuzzTest*" -Dfoliojet.fuzzShrink=149858 \
 *     -Dfoliojet.fuzzShrinkMode=wild
 * </pre>
 *
 * <p>
 * 結果は{@code local/shrink/<mode>-<seed>-min.html}へ書き、標準出力へも出す
 * (gradleは既定でテストの標準出力を隠すので、{@code build/test-results/test/}の
 * XMLか{@code -Dfoliojet.fuzzShrink}指定時の{@code showStandardStreams}で読む)。
 * </p>
 *
 * <h2>述語(ここが危険な部分)</h2>
 *
 * <p>
 * 縮小した文書は<b>もはや生成器が作ったものではない</b>。したがって
 * {@link Generated}の各項——トークン表・並べ替え可能集合・紙面寸法・最大明示
 * サイズ・除外フラグ——は<b>候補文書から計算し直さなければならない</b>。
 * 元のトークン表を引き継ぐと、要素を1個消しただけで不変条件4が
 * 「内容の消失」で発火し、縮小器は<b>自分が作った失敗</b>を大喜びで
 * 保存し続ける。§3.15 の6例目になるところだった。
 * </p>
 *
 * <p>
 * さらに以下を明示的に守る:
 * </p>
 *
 * <ul>
 * <li><b>種別が同じこと</b>を要求する。「まだ落ちる」ではなく
 * {@link RandomDocumentFuzzTest#classify}の文字列が一致すること。そうしないと
 * <b>別の欠陥</b>の最小形が出てくる</li>
 * <li><b>退化解を拒む</b>。トークンが1個も残らない候補は不合格。
 * ページが0枚の候補も(検査側が先に落ちるので)不合格になる</li>
 * <li><b>画像URIには触らない</b>。{@code src}属性は削除も数値縮小も対象外。
 * 相対パスへ書き換わると画像が黙って消えてページ数が変わる(§3.15の5例目)</li>
 * <li><b>オラクルが読む骨格を壊さない</b>。紙面寸法のPI・{@code @page}の
 * マージン・{@code body}の{@code font}が消えた候補は、たとえ落ちても不合格に
 * する——これらが無いと{@code isOversized}/{@code isTinyPage}の意味が
 * 黙って変わる</li>
 * <li><b>予算</b>を持つ。述語の評価回数と実時間に上限を置き、<b>打ち切ったら
 * そう報告する</b>(部分縮小の結果を黙って返さない)</li>
 * </ul>
 *
 * <h2>自己検査</h2>
 *
 * <p>
 * 縮小を始める前に、
 * </p>
 *
 * <ol>
 * <li>構文木の<b>往復</b>(parse→serialize)が元の文字列と1バイトも違わないこと</li>
 * <li>再計算したトークン表・並べ替え可能集合が、<b>生成器が記録した値と一致</b>
 * すること。これが述語の心臓部の直接検算になる</li>
 * <li>元の文書で目的の種別が再現すること、および<b>空文書では再現しないこと</b>
 * (退化側の検算。§3.15 が「片方だけでは足りない」と言っている方)</li>
 * </ol>
 */
final class FuzzShrinker {

	private FuzzShrinker() {
		// ユーティリティ
	}

	/** 述語の評価回数の上限({@code -Dfoliojet.fuzzShrinkEvals})。 */
	private static final int DEFAULT_MAX_EVALS = 5_000;

	/** 実時間の上限({@code -Dfoliojet.fuzzShrinkMillis})。 */
	private static final long DEFAULT_MAX_MS = 20 * 60_000L;

	/** 予算を使い切ったことを縮小ループの外まで伝える。 */
	private static final class BudgetExhausted extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private final String what;

		BudgetExhausted(final String what) {
			super(what);
			this.what = what;
		}
	}

	// ------------------------------------------------------------------
	// 入口
	// ------------------------------------------------------------------

	static void shrink(final int seed, final boolean strict) throws Exception {
		final String mode = strict ? "strict" : "wild";
		System.out.println("[shrink] seed=" + seed + " mode=" + mode);
		shrink(RandomDocumentFuzzTest.generate(seed, strict), mode + "-" + seed, strict, true);
	}

	/**
	 * <b>ファイルを縮小する入口</b>({@code -Dfoliojet.fuzzShrinkFile=<path>})。
	 *
	 * <p>
	 * 生成器を通さないので自己検査2(生成器の記録との突き合わせ)はできない。
	 * 代わりに再計算した値をそのまま出す。手で書いた再現文書や、
	 * <b>縮小器自身の検算</b>——答えの分かっている文書を水増ししてから
	 * 縮小させ、水増し分だけが消えるかを見る——に使う。
	 * </p>
	 */
	static void shrinkFile(final File file) throws Exception {
		final String html = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		System.out.println("[shrink] file=" + file.getPath());
		final Generated doc = analyze(html);
		if (doc == null) {
			System.out.println("[shrink] 中止: 解析できない(オラクルの骨格が無い)");
			return;
		}
		final String base = file.getName().replaceFirst("\\.html?$", "");
		shrink(doc, base, true, false);
	}

	private static void shrink(final Generated original, final String label, final boolean strict,
			final boolean fromGenerator) throws Exception {
		final long began = System.currentTimeMillis();
		final File dir = new File("local/shrink");
		dir.mkdirs();
		final File work = new File(dir, label + "-work.html");
		final File workDl = new File(dir, "dl-" + label);
		final File min = new File(dir, label + "-min.html");

		// --- 自己検査1: 構文木の往復 ---
		final String rebuilt = rebuild(original.html(), parseBody(original.html()), (Node) null, null);
		if (!rebuilt.equals(original.html())) {
			System.out.println("[shrink] 中止: parse→serialize が元と一致しない。"
					+ "解析器が文書を書き換えているので、この先の縮小結果は信用できない");
			dump("original", original.html());
			dump("rebuilt", rebuilt);
			return;
		}
		System.out.println("[shrink] 自己検査1 OK: 構文木の往復が元と1バイトも違わない");

		// --- 自己検査2: 再計算した述語入力が生成器の記録と一致するか ---
		final Generated recomputed = analyze(original.html());
		if (recomputed == null) {
			System.out.println("[shrink] 中止: 元の文書を解析できない(オラクルの骨格が無い)");
			return;
		}
		if (!fromGenerator) {
			System.out.println("[shrink] 自己検査2 省略(生成器を通していない)。再計算値: tokens="
					+ recomputed.tokens().size() + "個 reorderable=" + recomputed.reorderable().size() + "個 page="
					+ recomputed.pageWidth() + "x" + recomputed.pageHeight() + " maxExplicit="
					+ recomputed.maxExplicitSize() + " oversized=" + recomputed.oversized() + " tiny="
					+ recomputed.tinyPage());
		} else {
			final boolean same = recomputed.tokens().equals(original.tokens())
					&& recomputed.reorderable().equals(original.reorderable())
					&& recomputed.pageWidth() == original.pageWidth() && recomputed.pageHeight() == original.pageHeight()
					&& recomputed.maxExplicitSize() == original.maxExplicitSize()
					&& recomputed.oversized() == original.oversized() && recomputed.tinyPage() == original.tinyPage();
			if (!same) {
				System.out.println("[shrink] 中止: 再計算した述語入力が生成器の記録と食い違う。"
						+ "この食い違いのまま縮小すると、縮小器自身が作った失敗を保存してしまう");
				System.out.println("  生成器: tokens=" + original.tokens() + " reorderable=" + original.reorderable()
						+ " page=" + original.pageWidth() + "x" + original.pageHeight() + " maxExplicit="
						+ original.maxExplicitSize() + " oversized=" + original.oversized() + " tiny="
						+ original.tinyPage());
				System.out.println("  再計算: tokens=" + recomputed.tokens() + " reorderable=" + recomputed.reorderable()
						+ " page=" + recomputed.pageWidth() + "x" + recomputed.pageHeight() + " maxExplicit="
						+ recomputed.maxExplicitSize() + " oversized=" + recomputed.oversized() + " tiny="
						+ recomputed.tinyPage());
				return;
			}
			System.out.println("[shrink] 自己検査2 OK: 再計算したトークン表(" + recomputed.tokens().size() + "個)・"
					+ "並べ替え可能集合(" + recomputed.reorderable().size() + "個)・紙面・除外フラグが生成器の記録と一致");
		}

		// --- 目標の種別を決める ---
		final Probe probe = new Probe(work, workDl, strict);
		final String target = probe.classOf(original.html());
		final int originalPages = probe.lastPages;
		if (target == null) {
			System.out.println("[shrink] このシードは通った(縮小するものがない)");
			return;
		}
		final double originalSeverity = probe.lastSeverity;
		// **深刻度は1ptも落とさない**のが既定(2026-07-28)。
		//
		// 最初は「元の1/4まで許す」にしていたが、seed 149858 でそれが
		// **文書の内容とオラクルの許容量の交換**に使われた: `float`の
		// `width:12pt`を`0pt`まで縮めると、許容量(=最大明示サイズの2倍)が
		// 24pt→0ptになるので、超過22ptを9ptまで落として釣り合わせられる。
		// 得られた最小形は`width:12pt`へ戻すと**通ってしまう**——元の失敗の
		// 縮小形ではなかった。厳しくしても代償は小さい(670→691 bytes)。
		// {@code -Dfoliojet.fuzzShrinkSeverity=0.25}で緩められるが、
		// **緩めたら結果を必ず読むこと**
		final double keep = Double.parseDouble(System.getProperty("foliojet.fuzzShrinkSeverity", "1"));
		final double minSeverity = Math.max(1, originalSeverity * keep);
		System.out.println("[shrink] 目標の種別: " + target + " (元の文書は" + originalPages + "ページ, 深刻度"
				+ originalSeverity + " → 下限" + minSeverity + ")");

		// --- 自己検査3: 退化入力では再現しないこと ---
		final String emptied = emptyBody(original.html());
		final String emptyClass = probe.classOf(emptied);
		if (target.equals(emptyClass) && probe.lastSeverity >= minSeverity) {
			System.out.println("[shrink] 中止: <body>を空にしても同じ種別(" + emptyClass + ")が出る。"
					+ "この述語は退化解で満たせるので縮小に使えない(LESSONS.md §3.15 の4例目)");
			return;
		}
		System.out.println("[shrink] 自己検査3 OK: 空の<body>では目標を満たさない(" + emptyClass + ", 深刻度"
				+ probe.lastSeverity + ", " + probe.lastPages + "ページ)。退化判定でも拒否: "
				+ degenerate(emptied));

		// --- 縮小 ---
		final int maxEvals = Integer.getInteger("foliojet.fuzzShrinkEvals", DEFAULT_MAX_EVALS).intValue();
		final long maxMs = Long.getLong("foliojet.fuzzShrinkMillis", DEFAULT_MAX_MS).longValue();
		final Shrinker s = new Shrinker(probe, target, original.html(), maxEvals, maxMs, originalPages > 0,
				minSeverity);
		String bound = null;
		try {
			s.run();
		} catch (final BudgetExhausted e) {
			bound = e.what;
		}
		final String result = s.current;

		try (Writer w = new OutputStreamWriter(new FileOutputStream(min), StandardCharsets.UTF_8)) {
			w.write(result);
		}

		// --- 最終確認: 出来上がったファイルで、もう一度最初から検査する ---
		final Generated finalDoc = analyze(result);
		final String finalClass = probe.classOf(result);
		final int pages = probe.lastPages;

		System.out.println("[shrink] ---------------- 結果 ----------------");
		System.out.println("[shrink] 元:   " + original.html().length() + " bytes, " + countElements(original.html())
				+ " 要素, " + original.tokens().size() + " トークン");
		System.out.println("[shrink] 最小: " + result.length() + " bytes, " + countElements(result) + " 要素, "
				+ (finalDoc == null ? -1 : finalDoc.tokens().size()) + " トークン, " + pages + " ページ");
		System.out.println("[shrink] 述語の評価回数: " + probe.evals + " (受理 " + s.accepted + " 件)");
		System.out.println("[shrink] 所要: " + ((System.currentTimeMillis() - began) / 1000) + "s");
		System.out.println("[shrink] 最終確認の種別: " + finalClass + (target.equals(finalClass) ? " (一致)" : " (不一致!)"));
		if (probe.lastFailure != null) {
			System.out.println("[shrink] 最終確認の失敗: " + probe.lastFailure);
			System.out.println("[shrink] 最終確認の深刻度: " + probe.lastSeverity + " (元 " + originalSeverity
					+ ", 下限 " + minSeverity + ")");
		}
		if (bound != null) {
			System.out.println("[shrink] **予算を使い切った (" + bound + ")**。"
					+ "以下は不動点ではなく途中結果である。-Dfoliojet.fuzzShrinkEvals / "
					+ "-Dfoliojet.fuzzShrinkMillis を増やして再実行すること");
		} else {
			System.out.println("[shrink] 不動点に到達(どの縮小操作も種別を保てなかった)");
		}
		System.out.println("[shrink] 出力: " + min.getPath());
		System.out.println("[shrink] ---------------- 最小文書 ----------------");
		System.out.println(result);
		System.out.println("[shrink] ------------------------------------------");
	}

	/**
	 * <b>生成器を通さず</b>、ディスク上のHTMLを同じ不変条件にかけます
	 * ({@code -Dfoliojet.fuzzCheckFile=<path>})。縮小結果の再現確認用。
	 *
	 * <p>
	 * 述語の入力({@link Generated})は当然この文書から計算する。縮小器が
	 * 使ったのとまったく同じ経路を、<b>ファイルから読み直して</b>通す。
	 * </p>
	 */
	static void checkFile(final File file) throws Exception {
		final String html = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		final Generated doc = analyze(html);
		System.out.println("[fuzzCheckFile] " + file.getPath() + " (" + html.length() + " bytes)");
		if (doc == null) {
			System.out.println("[fuzzCheckFile]   解析不能(オラクルの骨格が無い)");
			return;
		}
		System.out.println("[fuzzCheckFile]   tokens=" + doc.tokens() + " reorderable=" + doc.reorderable() + " page="
				+ doc.pageWidth() + "x" + doc.pageHeight() + " maxExplicit=" + doc.maxExplicitSize() + " oversized="
				+ doc.oversized() + " tiny=" + doc.tinyPage());
		// 除外の判定に効く軸の入れ替わり回数も出す。2以上で
		// {@code ExcludedByNestedOrthogonalFlow}の対象になるため、
		// **除外が広がりすぎていないか**をこの値で確かめられる(2026-07-30)
		System.out.println("[fuzzCheckFile]   直交フローの軸の入れ替わり="
				+ RandomDocumentFuzzTest.orthogonalAxisChanges(html)
				+ " (2以上なら紙面外配置を除外) 直交フローあり="
				+ RandomDocumentFuzzTest.hasOrthogonalFlow(html));
		final File dir = new File("local/shrink");
		dir.mkdirs();
		final File work = new File(dir, "check-" + file.getName());
		final File workDl = new File(dir, "dl-check");
		final Probe probe = new Probe(work, workDl, true);
		final String cls = probe.classOf(html);
		System.out.println("[fuzzCheckFile]   種別: " + (cls == null ? "(通った)" : cls) + " / " + probe.lastPages
				+ "ページ");
		if (probe.lastFailure != null) {
			System.out.println("[fuzzCheckFile]   " + probe.lastFailure);
			for (Throwable c = probe.lastFailure.getCause(); c != null; c = c.getCause()) {
				System.out.println("[fuzzCheckFile]     caused by " + c);
			}
		}
	}

	private static void dump(final String label, final String s) {
		System.out.println("---- " + label + " (" + s.length() + " bytes) ----");
		System.out.println(s);
	}

	// ------------------------------------------------------------------
	// 述語
	// ------------------------------------------------------------------

	/**
	 * 候補文書を1件変換して<b>失敗の種別</b>を返す(通れば{@code null})。
	 *
	 * <p>
	 * 変換はテストJVMの中で直接行う。候補ごとにgradleを起動するとロック競合と
	 * UP-TO-DATEスキップで<b>述語が嘘をつく</b>(§3.15 の2例目・3例目)。
	 * </p>
	 */
	private static final class Probe {
		private final File work, workDl;

		private final boolean strict;

		int evals;

		int lastPages;

		Probe(final File work, final File workDl, final boolean strict) {
			this.work = work;
			this.workDl = workDl;
			this.strict = strict;
		}

		/** 直近の失敗そのもの(報告用。判定には使わない)。 */
		Throwable lastFailure;

		/** 直近の失敗の<b>深刻度</b>。{@link FuzzShrinker#severity}を参照。 */
		double lastSeverity;

		String classOf(final String html) throws Exception {
			++this.evals;
			final Generated doc = analyze(html);
			if (doc == null) {
				return "(解析不能)";
			}
			try (Writer w = new OutputStreamWriter(new FileOutputStream(this.work), StandardCharsets.UTF_8)) {
				w.write(html);
			}
			try {
				RandomDocumentFuzzTest.checkDocument(doc, this.work, this.workDl, this.strict, "shrink");
				this.lastPages = countPages(this.workDl);
				this.lastFailure = null;
				this.lastSeverity = 0;
				return null;
			} catch (final Throwable t) {
				this.lastPages = countPages(this.workDl);
				this.lastFailure = t;
				this.lastSeverity = severity(t);
				return RandomDocumentFuzzTest.classify(t);
			}
		}

		private static int countPages(final File dir) {
			final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
			return pages == null ? 0 : pages.length;
		}
	}

	/**
	 * 失敗の<b>深刻度</b>。種別が同じでも「どれだけ壊れているか」が桁で違えば
	 * 別の話である。
	 *
	 * <p>
	 * <b>これが無いと縮小器は不変条件の境界へ滑り落ちる</b>(2026-07-28に実際に
	 * 踏んだ)。不変条件6の許容量は{@code 2 × 最大明示サイズ}なので、
	 * 文書から{@code width:}/{@code height:}の指定を消すと<b>許容量が0になり</b>、
	 * 丸め誤差程度のはみ出しでも「同じ種別」を満たす。seed 194970 の1回目の
	 * 縮小結果は<b>0.12ptのはみ出し</b>で、元の失敗とは何の関係もない
	 * 文書だった——これは§3.15 が言う「退化解」の連続量版である。
	 * </p>
	 *
	 * <p>
	 * 紙面外配置では超過量、白紙ページでは最後の白紙のページ番号を使う。
	 * 後者により、内容を全部消して得た1ページだけの白紙を「末尾の余分な
	 * 白紙ページ」と取り違えない。他の種別は{@code 1}を返す。
	 * </p>
	 */
	private static final Pattern OFF_PAGE_DETAIL = Pattern
			.compile("紙面外への配置 (\\d+)pt \\(紙面\\d+x\\d+pt, 最大明示サイズ(\\d+)pt");

	private static final Pattern BLANK_PAGE_DETAIL = Pattern.compile("白紙ページ \\[(\\d+(?:, \\d+)*)\\]");

	static double severity(final Throwable t) {
		for (Throwable c = t; c != null; c = c.getCause()) {
			final String m = c.getMessage();
			if (m == null) {
				continue;
			}
			final Matcher od = OFF_PAGE_DETAIL.matcher(m);
			if (od.find()) {
				// はみ出し量から「作者の指定で説明できる分」を引いた超過
				return Double.parseDouble(od.group(1)) - 2 * Double.parseDouble(od.group(2));
			}
			final Matcher bd = BLANK_PAGE_DETAIL.matcher(m);
			if (bd.find()) {
				int last = 0;
				for (final String page : bd.group(1).split(", ")) {
					last = Math.max(last, Integer.parseInt(page));
				}
				return last;
			}
		}
		return 1;
	}

	// ------------------------------------------------------------------
	// 縮小ループ
	// ------------------------------------------------------------------

	private static final class Shrinker {
		private final Probe probe;

		private final String target;

		private final int maxEvals;

		private final long deadline;

		/** 元の文書がページを出していたなら、候補にも1枚以上を要求する。 */
		private final boolean requirePages;

		/** 保つべき深刻度の下限。境界へ滑り落ちるのを防ぐ。 */
		private final double minSeverity;

		String current;

		int accepted;

		Shrinker(final Probe probe, final String target, final String start, final int maxEvals, final long maxMs,
				final boolean requirePages, final double minSeverity) {
			this.probe = probe;
			this.target = target;
			this.current = start;
			this.maxEvals = maxEvals;
			this.deadline = System.currentTimeMillis() + maxMs;
			this.requirePages = requirePages;
			this.minSeverity = minSeverity;
		}

		/**
		 * 候補を1件試す。<b>同じ種別で落ちたときだけ</b>採用する。
		 *
		 * <p>
		 * 退化解(トークンが残らない・ページが出ない)は、種別が一致しても
		 * <b>採用しない</b>。§3.15 の4例目は「白紙ページがある」という述語を
		 * 「本文を全部消す」で満たした。
		 * </p>
		 */
		private boolean accept(final String candidate) throws Exception {
			if (candidate.equals(this.current) || candidate.isEmpty()) {
				return false;
			}
			if (degenerate(candidate)) {
				return false;
			}
			if (this.probe.evals >= this.maxEvals) {
				throw new BudgetExhausted("述語の評価" + this.maxEvals + "回");
			}
			if (System.currentTimeMillis() > this.deadline) {
				throw new BudgetExhausted("実時間");
			}
			if (!this.target.equals(this.probe.classOf(candidate))) {
				return false;
			}
			if (this.requirePages && this.probe.lastPages < 1) {
				return false;
			}
			if (this.probe.lastSeverity < this.minSeverity) {
				return false; // 種別は同じでも、壊れ方が桁で軽い
			}
			this.current = candidate;
			++this.accepted;
			if (this.accepted % 10 == 0) {
				System.out.println("[shrink]   " + this.accepted + "件受理, " + this.current.length() + " bytes, 評価"
						+ this.probe.evals + "回");
			}
			return true;
		}

		void run() throws Exception {
			for (boolean progress = true; progress;) {
				progress = false;
				while (this.deleteSubtree()) {
					progress = true;
				}
				while (this.unwrap()) {
					progress = true;
				}
				while (this.dropAttribute()) {
					progress = true;
				}
				while (this.dropDeclaration()) {
					progress = true;
				}
				if (this.shrinkNumbers()) {
					progress = true;
				}
				if (!progress) {
					// 1個ずつでは動かなくなってから、**2個同時**を試す。
					// 「AもBも単独では消せないが、両方消すと消せる」は実在する
					// (フロートと、それを`clear`している箱など)。要素数が
					// 減りきってから走らせるので O(n^2) でも数十件で済む
					progress = this.deletePairs();
				}
			}
		}

		/** 1個ずつの削除が止まった後の脱出手段。2つの部分木を同時に消す。 */
		private boolean deletePairs() throws Exception {
			final List<Node> forest = parseBody(this.current);
			final List<Node> elements = nodesOf(forest);
			for (int i = 0; i < elements.size(); ++i) {
				for (int j = i + 1; j < elements.size(); ++j) {
					final Node a = elements.get(i), b = elements.get(j);
					if (contains(a, b) || contains(b, a)) {
						continue; // 入れ子は片方を消せば済む
					}
					if (this.accept(rebuild(this.current, forest, java.util.Set.of(a, b), null))) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * 操作1: 部分木をまるごと消す(効きが最も大きいので最初)。
		 *
		 * <p>
		 * <b>テキストノードも対象に含める。</b> 含めないと、unwrapが作った
		 * 裸のテキスト(親要素を剥がした後に残る{@code T4})を誰も消せず、
		 * 不動点が不必要に大きくなる(2026-07-28に実測: 13要素の不動点に
		 * 7個の裸テキストが残っていた)。
		 * </p>
		 */
		private boolean deleteSubtree() throws Exception {
			final List<Node> forest = parseBody(this.current);
			final List<Node> elements = nodesOf(forest);
			// 大きいものから試す——1回の受理で減る量が最大になる
			elements.sort(Comparator.comparingInt((final Node n) -> serialize(n).length()).reversed());
			for (final Node n : elements) {
				if (this.accept(rebuild(this.current, forest, n, null))) {
					return true;
				}
			}
			return false;
		}

		/** 操作2: 要素を子で置き換える(入れ子を1段減らす)。 */
		private boolean unwrap() throws Exception {
			final List<Node> forest = parseBody(this.current);
			final List<Node> elements = elementsOf(forest);
			for (final Node n : elements) {
				if (n.children.isEmpty()) {
					continue; // 削除と同じ
				}
				if (this.accept(rebuild(this.current, forest, (Node) null, n))) {
					return true;
				}
			}
			return false;
		}

		/** 操作3: 属性を1つ落とす({@code rowspan}/{@code colspan}/{@code style})。 */
		private boolean dropAttribute() throws Exception {
			final List<Node> forest = parseBody(this.current);
			for (final Node n : elementsOf(forest)) {
				for (int a = n.attrs.size() - 1; a >= 0; --a) {
					if (KEEP_ATTRS.contains(n.attrs.get(a).name)) {
						continue; // 画像URIには触らない(§3.15 の5例目)
					}
					final Attr saved = n.attrs.remove(a);
					final String candidate = rebuild(this.current, forest, (Node) null, null);
					if (this.accept(candidate)) {
						return true;
					}
					n.attrs.add(a, saved);
				}
			}
			return false;
		}

		/**
		 * 操作4: CSS宣言を1つ落とす。対象は{@code style}属性と{@code <style>}
		 * ブロックの両方(規則ごと落とす候補も出す)。
		 */
		private boolean dropDeclaration() throws Exception {
			for (final int[] region : declarationRegions(this.current)) {
				final String body = this.current.substring(region[0], region[1]);
				final String[] parts = body.split(";", -1);
				if (parts.length <= 1 && body.isEmpty()) {
					continue;
				}
				for (int i = 0; i < parts.length; ++i) {
					final StringBuilder kept = new StringBuilder();
					for (int j = 0; j < parts.length; ++j) {
						if (j == i) {
							continue;
						}
						if (kept.length() > 0) {
							kept.append(';');
						}
						kept.append(parts[j]);
					}
					final String candidate = this.current.substring(0, region[0]) + kept
							+ this.current.substring(region[1]);
					if (this.accept(candidate)) {
						return true;
					}
				}
			}
			// 規則ごと落とす(`<style>`の1行)
			for (final int[] rule : styleRuleLines(this.current)) {
				final String candidate = this.current.substring(0, rule[0]) + this.current.substring(rule[1]);
				if (this.accept(candidate)) {
					return true;
				}
			}
			return false;
		}

		/**
		 * 操作5: 数値を0(または下限)へ寄せる。<b>1つずつ二分探索</b>する——
		 * 1ずつ減らすと{@code width:250pt}に250回かかる。
		 *
		 * <p>
		 * 数値の位置は受理のたびにずれるが、<b>個数は変わらない</b>(桁を
		 * 書き換えるだけで、正規表現の一致が増減しない)。そこで<b>添字</b>で
		 * 同一性を保ち、毎回取り直す。
		 * </p>
		 */
		private boolean shrinkNumbers() throws Exception {
			boolean any = false;
			for (boolean pass = true; pass;) {
				pass = false;
				final int count = numbers(this.current).size();
				for (int idx = 0; idx < count; ++idx) {
					final List<Num> fresh = numbers(this.current);
					if (fresh.size() != count) {
						break; // 想定外。安全側に倒して打ち切る
					}
					final Num n0 = fresh.get(idx);
					if (n0.value <= n0.floor) {
						continue;
					}
					int lo = n0.floor, hi = n0.value;
					while (lo < hi) {
						final int mid = lo + (hi - lo) / 2;
						final List<Num> now = numbers(this.current);
						if (now.size() != count) {
							break;
						}
						if (this.accept(replace(this.current, now.get(idx), mid))) {
							hi = mid;
							pass = true;
							any = true;
						} else {
							lo = mid + 1;
						}
					}
				}
			}
			return any;
		}
	}

	/** 落としてはいけない属性。{@code src}は画像URIそのもの。 */
	private static final Set<String> KEEP_ATTRS = Set.of("src");

	// ------------------------------------------------------------------
	// 述語の入力を候補文書から計算し直す
	// ------------------------------------------------------------------

	private static final Pattern PAGE_WIDTH_PI = Pattern
			.compile("output\\.page-width\"\\s+value=\"(\\d+)pt\"");

	private static final Pattern PAGE_HEIGHT_PI = Pattern
			.compile("output\\.page-height\"\\s+value=\"(\\d+)pt\"");

	private static final Pattern PAGE_MARGIN_RULE = Pattern.compile("@page\\{margin:(\\d+)pt");

	private static final Pattern BODY_FONT_RULE = Pattern.compile("font:normal (\\d+)pt");

	/** 生成器が埋めるトークン。テキストノードの中だけを見る。 */
	private static final Pattern TOKEN = Pattern.compile("T\\d+");

	/**
	 * 候補文書から{@link Generated}を作り直します。<b>元の値は1つも
	 * 引き継ぎません。</b>
	 *
	 * <p>
	 * オラクルが読む骨格(紙面寸法のPI・{@code @page}のマージン・
	 * {@code body}の{@code font})が欠けていたら{@code null}を返します。
	 * 欠けたまま計算すると{@code isOversized}/{@code isTinyPage}の意味が
	 * 黙って変わり、「除外されるはずの文書が検査され」たり逆になったりする。
	 * </p>
	 */
	static Generated analyze(final String html) {
		final Matcher pw = PAGE_WIDTH_PI.matcher(html), ph = PAGE_HEIGHT_PI.matcher(html);
		if (!pw.find() || !ph.find()) {
			return null;
		}
		if (!PAGE_MARGIN_RULE.matcher(html).find() || !BODY_FONT_RULE.matcher(html).find()) {
			return null;
		}
		final int[] size = { Integer.parseInt(pw.group(1)), Integer.parseInt(ph.group(1)) };
		if (size[0] <= 0 || size[1] <= 0) {
			return null;
		}
		final List<Node> forest;
		try {
			forest = parseBody(html);
		} catch (final RuntimeException broken) {
			return null;
		}
		final List<String> tokens = new ArrayList<>();
		final Set<String> reorderable = new LinkedHashSet<>();
		collectTokens(forest, false, tokens, reorderable);
		double maxExplicit = 0;
		final Matcher em = RandomDocumentFuzzTest.EXPLICIT_SIZE.matcher(html);
		while (em.find()) {
			maxExplicit = Math.max(maxExplicit, Double.parseDouble(em.group(1)));
		}
		return new Generated(html, tokens, reorderable, size[0], size[1], maxExplicit,
				RandomDocumentFuzzTest.isOversized(html, size), RandomDocumentFuzzTest.isTinyPage(html, size));
	}

	/**
	 * トークンと「並べ替えが正当なトークン」を木から拾います。
	 *
	 * <p>
	 * <b>並べ替え可能かは木から計算する</b>——{@code float:}または
	 * {@code position:absolute}の部分木の中にあるか。生成器が記録した集合を
	 * 引き継ぐと、{@code float}の指定を落とす縮小をしたときに集合が実態と
	 * ずれる。
	 * </p>
	 */
	private static void collectTokens(final List<Node> nodes, final boolean inReorderable, final List<String> tokens,
			final Set<String> reorderable) {
		for (final Node n : nodes) {
			if (n.tag == null) {
				final Matcher m = TOKEN.matcher(n.text);
				while (m.find()) {
					tokens.add(m.group());
					if (inReorderable) {
						reorderable.add(m.group());
					}
				}
				continue;
			}
			final String style = n.attr("style");
			final boolean floated = style != null && style.contains("float:") && !style.contains("float:none");
			final boolean here = inReorderable
					|| (style != null && (floated || style.contains("position:absolute")));
			collectTokens(n.children, here, tokens, reorderable);
		}
	}

	/**
	 * <b>退化した候補</b>か。解析できない、またはトークンが1個も残らない文書。
	 * 縮小器は述語を最小コストで満たそうとするので、ここを緩めると必ず
	 * 「本文が空の文書で再現する」という嘘の最小形に落ちる。
	 */
	private static boolean degenerate(final String html) {
		final Generated g = analyze(html);
		return g == null || g.tokens().isEmpty();
	}

	private static int countElements(final String html) {
		return elementsOf(parseBody(html)).size();
	}

	/** 退化側の検算用: {@code <body>}を空にした文書。 */
	private static String emptyBody(final String html) {
		final int[] b = bodyRange(html);
		return html.substring(0, b[0]) + "\n" + html.substring(b[1]);
	}

	// ------------------------------------------------------------------
	// 最小のタグ対応パーサ(HTMLパーサは足さない)
	// ------------------------------------------------------------------

	/**
	 * 生成器が出す文書は<b>整形式</b>なので、タグを数えるだけで足ります。
	 * ここへ本物のHTMLパーサを持ってくると、正規化で文書が変わってしまう
	 * ——縮小器にとってそれは「述語の入力を勝手に書き換える」ことに等しい。
	 */
	static final class Node {
		/** 要素名。テキストノードでは{@code null}。 */
		final String tag;

		final String text;

		final List<Attr> attrs;

		final boolean selfClosing;

		final List<Node> children = new ArrayList<>();

		private Node(final String tag, final String text, final List<Attr> attrs, final boolean selfClosing) {
			this.tag = tag;
			this.text = text;
			this.attrs = attrs;
			this.selfClosing = selfClosing;
		}

		static Node text(final String text) {
			return new Node(null, text, null, false);
		}

		String attr(final String name) {
			for (final Attr a : this.attrs) {
				if (a.name.equals(name)) {
					return a.value;
				}
			}
			return null;
		}
	}

	static final class Attr {
		final String name;

		final String value;

		Attr(final String name, final String value) {
			this.name = name;
			this.value = value;
		}
	}

	private static final Pattern ATTR = Pattern.compile("([\\w:.-]+)=\"([^\"]*)\"");

	private static int[] bodyRange(final String html) {
		final int open = html.indexOf("<body");
		final int start = html.indexOf('>', open) + 1;
		final int end = html.indexOf("</body>");
		if (open < 0 || start <= 0 || end < start) {
			throw new IllegalStateException("<body>が見つからない");
		}
		return new int[] { start, end };
	}

	static List<Node> parseBody(final String html) {
		final int[] r = bodyRange(html);
		return parse(html.substring(r[0], r[1]));
	}

	private static List<Node> parse(final String s) {
		final List<Node> root = new ArrayList<>();
		final Deque<List<Node>> stack = new ArrayDeque<>();
		final Deque<Node> open = new ArrayDeque<>();
		List<Node> cur = root;
		int i = 0;
		while (i < s.length()) {
			final int lt = s.indexOf('<', i);
			if (lt < 0) {
				cur.add(Node.text(s.substring(i)));
				break;
			}
			if (lt > i) {
				cur.add(Node.text(s.substring(i, lt)));
			}
			final int gt = s.indexOf('>', lt);
			if (gt < 0) {
				throw new IllegalStateException("閉じない '<'");
			}
			final String raw = s.substring(lt, gt + 1);
			i = gt + 1;
			if (raw.startsWith("</")) {
				if (open.isEmpty()) {
					throw new IllegalStateException("開いていない終了タグ " + raw);
				}
				final Node n = open.pop();
				if (!raw.equals("</" + n.tag + ">")) {
					throw new IllegalStateException("タグの対応が取れない " + raw + " vs " + n.tag);
				}
				cur = stack.pop();
				continue;
			}
			final boolean selfClosing = raw.endsWith("/>");
			final String inner = raw.substring(1, raw.length() - (selfClosing ? 2 : 1)).trim();
			int sp = 0;
			while (sp < inner.length() && !Character.isWhitespace(inner.charAt(sp))) {
				++sp;
			}
			final String tag = inner.substring(0, sp);
			final List<Attr> attrs = new ArrayList<>();
			final Matcher am = ATTR.matcher(inner.substring(sp));
			while (am.find()) {
				attrs.add(new Attr(am.group(1), am.group(2)));
			}
			final Node n = new Node(tag, null, attrs, selfClosing);
			cur.add(n);
			if (!selfClosing) {
				stack.push(cur);
				open.push(n);
				cur = n.children;
			}
		}
		if (!open.isEmpty()) {
			throw new IllegalStateException("閉じていない要素 " + open.peek().tag);
		}
		return root;
	}

	private static String serialize(final Node n) {
		final StringBuilder sb = new StringBuilder();
		write(sb, n, java.util.Collections.emptySet(), null);
		return sb.toString();
	}

	/** {@code a}の部分木に{@code b}が含まれるか。 */
	private static boolean contains(final Node a, final Node b) {
		if (a == b) {
			return true;
		}
		for (final Node c : a.children) {
			if (contains(c, b)) {
				return true;
			}
		}
		return false;
	}

	private static void write(final StringBuilder sb, final Node n, final Set<Node> omit, final Node unwrap) {
		if (omit.contains(n)) {
			return;
		}
		if (n.tag == null) {
			sb.append(n.text);
			return;
		}
		if (n == unwrap) {
			for (final Node c : n.children) {
				write(sb, c, omit, unwrap);
			}
			return;
		}
		sb.append('<').append(n.tag);
		for (final Attr a : n.attrs) {
			sb.append(' ').append(a.name).append("=\"").append(a.value).append('"');
		}
		if (n.selfClosing) {
			sb.append(" />");
			return;
		}
		sb.append('>');
		for (final Node c : n.children) {
			write(sb, c, omit, unwrap);
		}
		sb.append("</").append(n.tag).append('>');
	}

	private static String rebuild(final String html, final List<Node> forest, final Set<Node> omit,
			final Node unwrap) {
		final int[] r = bodyRange(html);
		final StringBuilder sb = new StringBuilder();
		for (final Node n : forest) {
			write(sb, n, omit, unwrap);
		}
		return html.substring(0, r[0]) + sb + html.substring(r[1]);
	}

	private static String rebuild(final String html, final List<Node> forest, final Node omit, final Node unwrap) {
		return rebuild(html, forest, omit == null ? java.util.Collections.<Node>emptySet() : java.util.Set.of(omit),
				unwrap);
	}

	/**
	 * 削除の候補になるノード: 全要素と、<b>空白だけではない</b>テキスト。
	 *
	 * <p>
	 * 空白だけのテキストを候補に入れると、生成器が入れた改行を1つずつ
	 * 消すのに評価回数を使い果たし(実測 689回→2,638回)、しかも最小形が
	 * <b>1行に潰れて読めなくなる</b>。バイト数もほとんど減らない。
	 * </p>
	 */
	private static List<Node> nodesOf(final List<Node> forest) {
		final List<Node> out = new ArrayList<>();
		collectNodes(forest, out);
		return out;
	}

	private static void collectNodes(final List<Node> nodes, final List<Node> out) {
		for (final Node n : nodes) {
			if (n.tag != null || !n.text.isBlank()) {
				out.add(n);
			}
			collectNodes(n.children, out);
		}
	}

	private static List<Node> elementsOf(final List<Node> forest) {
		final List<Node> out = new ArrayList<>();
		collectElements(forest, out);
		return out;
	}

	private static void collectElements(final List<Node> nodes, final List<Node> out) {
		for (final Node n : nodes) {
			if (n.tag != null) {
				out.add(n);
				collectElements(n.children, out);
			}
		}
	}

	// ------------------------------------------------------------------
	// CSS宣言・数値の位置
	// ------------------------------------------------------------------

	private static final Pattern STYLE_ATTR = Pattern.compile("style=\"([^\"]*)\"");

	private static final Pattern SRC_ATTR = Pattern.compile("src=\"[^\"]*\"");

	/**
	 * 宣言の並び(セミコロン区切り)が入っている範囲。{@code style}属性の値と
	 * {@code <style>}ブロックの{@code { }}の中。
	 */
	private static List<int[]> declarationRegions(final String html) {
		final List<int[]> out = new ArrayList<>();
		final Matcher sm = STYLE_ATTR.matcher(html);
		while (sm.find()) {
			if (!sm.group(1).isEmpty()) {
				out.add(new int[] { sm.start(1), sm.end(1) });
			}
		}
		final int[] block = styleBlockRange(html);
		if (block != null) {
			int i = block[0];
			for (;;) {
				final int open = html.indexOf('{', i);
				if (open < 0 || open >= block[1]) {
					break;
				}
				final int close = html.indexOf('}', open);
				if (close < 0 || close >= block[1]) {
					break;
				}
				if (close > open + 1) {
					out.add(new int[] { open + 1, close });
				}
				i = close + 1;
			}
		}
		return out;
	}

	private static int[] styleBlockRange(final String html) {
		final int open = html.indexOf("<style>");
		if (open < 0) {
			return null;
		}
		final int close = html.indexOf("</style>", open);
		if (close < 0) {
			return null;
		}
		return new int[] { open + "<style>".length(), close };
	}

	/** {@code <style>}の中の「1行=1規則」の範囲(改行を含む)。 */
	private static List<int[]> styleRuleLines(final String html) {
		final List<int[]> out = new ArrayList<>();
		final int[] block = styleBlockRange(html);
		if (block == null) {
			return out;
		}
		int i = block[0];
		while (i < block[1]) {
			int nl = html.indexOf('\n', i);
			if (nl < 0 || nl >= block[1]) {
				nl = block[1] - 1;
			}
			final String line = html.substring(i, nl);
			if (line.trim().endsWith("}")) {
				out.add(new int[] { i, nl + 1 });
			}
			i = nl + 1;
		}
		return out;
	}

	/** 縮小できる数値の1つ。 */
	private static final class Num {
		final int start, end, value, floor;

		Num(final int start, final int end, final int value, final int floor) {
			this.start = start;
			this.end = end;
			this.value = value;
			this.floor = floor;
		}
	}

	private static final Pattern PT_NUMBER = Pattern.compile("(?<![\\d.])(\\d+)pt");

	private static final Pattern SPAN_ATTR = Pattern.compile("(?:rowspan|colspan)=\"(\\d+)\"");

	private static final Pattern COLUMN_COUNT = Pattern.compile("column-count:(\\d+)");

	/**
	 * 縮小候補の数値を集めます。
	 *
	 * <p>
	 * <b>{@code src}属性の中は決して触りません</b>——画像URIには
	 * {@code copper4}のように数字が入っており、書き換えると画像が黙って
	 * 消える(§3.15 の5例目)。
	 * </p>
	 *
	 * <p>
	 * <b>{@code <body>}より前の数値も一切触りません</b>(2026-07-28、
	 * 実際に踏んだ)。紙面寸法・{@code @page}のマージン・基準フォントサイズは
	 * <b>不変条件の物差しそのもの</b>である——不変条件6の許容量は
	 * 「紙面の2倍」と「最大明示サイズの2倍」で決まり、除外判定
	 * ({@code isTinyPage})は「内容領域が基準フォントの8倍あるか」で決まる。
	 * ここを縮めてよいことにすると、縮小器は<b>物差しのほうを縮めて</b>
	 * 述語を満たす: 最初の試走は紙面を120x400ptから<b>8x8pt</b>へ、
	 * フォントを11ptから<b>1pt</b>へ落とし、「1ptの文字が8ptの紙からはみ出す」
	 * という<b>何も言っていない最小形</b>を返した。紙面の設定は縮小対象では
	 * なく<b>固定具</b>である。
	 * </p>
	 */
	private static List<Num> numbers(final String html) {
		final List<int[]> forbidden = new ArrayList<>();
		forbidden.add(new int[] { 0, bodyRange(html)[0] });
		final Matcher src = SRC_ATTR.matcher(html);
		while (src.find()) {
			forbidden.add(new int[] { src.start(), src.end() });
		}
		final List<Num> out = new ArrayList<>();
		add(out, forbidden, html, PT_NUMBER, 0);
		add(out, forbidden, html, SPAN_ATTR, 1);
		add(out, forbidden, html, COLUMN_COUNT, 1);
		out.sort(Comparator.comparingInt(n -> n.start));
		return out;
	}

	private static void add(final List<Num> out, final List<int[]> forbidden, final String html, final Pattern p,
			final int defaultFloor) {
		final Matcher m = p.matcher(html);
		while (m.find()) {
			boolean skip = false;
			for (final int[] f : forbidden) {
				if (m.start(1) >= f[0] && m.start(1) < f[1]) {
					skip = true;
					break;
				}
			}
			if (skip) {
				continue;
			}
			out.add(new Num(m.start(1), m.end(1), Integer.parseInt(m.group(1)), defaultFloor));
		}
	}

	private static String replace(final String html, final Num n, final int value) {
		return html.substring(0, n.start) + value + html.substring(n.end);
	}
}
