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
 * <b>Automatically minimize</b> failures found by the random document fuzzer (introduced 2026-07-28).
 *
 * <h2>Why this is needed</h2>
 *
 * <p>
 * A sweep finishes in minutes, but <b>diagnosing one case takes hours</b>. Every defect found this season
 * was reduced manually by a human or agent, producing <b>five false minimal cases</b> along the way.
 * Reduction belongs to machines, and <b>writing the predicate correctly is the core task
 * when a machine performs it</b>.
 * </p>
 *
 * <h2>Execution</h2>
 *
 * <pre>
 * ./gradlew test --rerun --tests "*RandomDocumentFuzzTest*" -Dfoliojet.fuzzShrink=149858 -q
 * ./gradlew test --rerun --tests "*RandomDocumentFuzzTest*" -Dfoliojet.fuzzShrink=149858 \
 *     -Dfoliojet.fuzzShrinkMode=wild
 * </pre>
 *
 * <p>
 * Write results to {@code local/shrink/<mode>-<seed>-min.html} and stdout
 * (gradle hides test stdout by default; read XML in {@code build/test-results/test/},
 * or use {@code showStandardStreams} when {@code -Dfoliojet.fuzzShrink} is specified).
 * </p>
 *
 * <h2>Predicate (the dangerous part)</h2>
 *
 * <p>
 * A reduced document <b>is no longer generator output</b>. Every field of {@link Generated},
 * including the token table, reorderable set, paper dimensions, maximum explicit size,
 * and exclusion flags, <b>must be recomputed from the candidate document</b>.
 * Reusing the original token table makes invariant 4 report content loss after deleting just
 * one element, and the shrinker happily keeps preserving <b>a failure it created itself</b>.
 * This nearly became the sixth example in §3.15.
 * </p>
 *
 * <p>
 * Also explicitly enforce the following:
 * </p>
 *
 * <ul>
 * <li>Require <b>the same category</b>: the {@link RandomDocumentFuzzTest#classify} string must match,
 * not merely "still fails". Otherwise the result may minimize <b>a different defect</b>.</li>
 * <li><b>Reject degenerate solutions.</b> Candidates with no remaining tokens fail.
 * Zero-page candidates also fail (the checker fails first).</li>
 * <li><b>Do not touch image URIs.</b> Exclude {@code src} attributes from deletion and numeric reduction.
 * Rewriting them as relative paths silently removes images and changes page counts (§3.15, example 5).</li>
 * <li><b>Preserve the skeleton the oracle reads.</b> Reject candidates missing paper-size PIs,
 * {@code @page} margins, or {@code body}'s {@code font}, even if they fail;
 * without these, the meaning of {@code isOversized}/{@code isTinyPage} silently changes.</li>
 * <li>Have a <b>budget</b>. Bound predicate evaluations and wall time, and <b>report truncation</b>
 * instead of silently returning a partial reduction.</li>
 * </ul>
 *
 * <h2>Self-checks</h2>
 *
 * <p>
 * Before shrinking, verify:
 * </p>
 *
 * <ol>
 * <li>The syntax-tree <b>round trip</b> (parse→serialize) matches the original string byte for byte.</li>
 * <li>The recomputed token table and reorderable set <b>match the generator's recorded values</b>.
 * This directly cross-checks the predicate's core.</li>
 * <li>The original document reproduces the target category, and <b>an empty document does not</b>
 * (cross-check the degenerate side; §3.15 says checking only one side is insufficient).</li>
 * </ol>
 */
final class FuzzShrinker {

	private FuzzShrinker() {
		// Utility class.
	}

	/** Maximum predicate evaluations ({@code -Dfoliojet.fuzzShrinkEvals}). */
	private static final int DEFAULT_MAX_EVALS = 5_000;

	/** Wall-time limit ({@code -Dfoliojet.fuzzShrinkMillis}). */
	private static final long DEFAULT_MAX_MS = 20 * 60_000L;

	/** Propagate budget exhaustion out of the shrinking loop. */
	private static final class BudgetExhausted extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private final String what;

		BudgetExhausted(final String what) {
			super(what);
			this.what = what;
		}
	}

	// ------------------------------------------------------------------
	// Entry points.
	// ------------------------------------------------------------------

	static void shrink(final int seed, final boolean strict) throws Exception {
		final String mode = strict ? "strict" : "wild";
		System.out.println("[shrink] seed=" + seed + " mode=" + mode);
		shrink(RandomDocumentFuzzTest.generate(seed, strict), mode + "-" + seed, strict, true);
	}

	/**
	 * <b>Entry point for shrinking a file</b> ({@code -Dfoliojet.fuzzShrinkFile=<path>}).
	 *
	 * <p>
	 * Without the generator, self-check 2 (comparison with generator records) is unavailable.
	 * Print the recomputed values instead. Use for handwritten reproducers or
	 * <b>cross-checking the shrinker itself</b>: inflate a document with a known answer,
	 * shrink it, and check that only the added material disappears.
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
		// An extreme document can generate thousands of pages per candidate. Repeated output
		// to the working tree (DrvFs) makes Windows-side monitoring dominate the cost,
		// so provide a way to use tmpfs, as the sweep does.
		final File dir = new File(System.getProperty("foliojet.fuzzShrinkDir", "local/shrink"));
		dir.mkdirs();
		final File work = new File(dir, label + "-work.html");
		final File workDl = new File(dir, "dl-" + label);
		final File min = new File(dir, label + "-min.html");

		// --- Self-check 1: syntax-tree round trip. ---
		final String rebuilt = rebuild(original.html(), parseBody(original.html()), (Node) null, null);
		if (!rebuilt.equals(original.html())) {
			System.out.println("[shrink] 中止: parse→serialize が元と一致しない。"
					+ "解析器が文書を書き換えているので、この先の縮小結果は信用できない");
			dump("original", original.html());
			dump("rebuilt", rebuilt);
			return;
		}
		System.out.println("[shrink] 自己検査1 OK: 構文木の往復が元と1バイトも違わない");

		// --- Self-check 2: recomputed predicate inputs match generator records. ---
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

		// --- Determine the target category. ---
		final Probe probe = new Probe(work, workDl, strict);
		final String target = probe.classOf(original.html());
		final int originalPages = probe.lastPages;
		if (target == null) {
			System.out.println("[shrink] このシードは通った(縮小するものがない)");
			return;
		}
		final double originalSeverity = probe.lastSeverity;
		// By default, **do not reduce severity even by 1 pt** (2026-07-28).
		//
		// Initially, severity could drop to one quarter of the original, but seed 149858 used this
		// to **trade document content for the oracle's allowance**: reducing a float's
		// `width:12pt` to `0pt` reduces the allowance (=twice the largest explicit size)
		// from 24 pt to 0 pt, allowing excess overflow to drop from 22 pt to 9 pt to compensate.
		// Restoring `width:12pt` made the resulting minimum **pass**; it was not a reduction
		// of the original failure. The stricter rule costs little (670→691 bytes).
		// Relax with {@code -Dfoliojet.fuzzShrinkSeverity=0.25},
		// but **always inspect the result if you relax it**.
		final double keep = Double.parseDouble(System.getProperty("foliojet.fuzzShrinkSeverity", "1"));
		final double minSeverity = Math.max(1, originalSeverity * keep);
		System.out.println("[shrink] 目標の種別: " + target + " (元の文書は" + originalPages + "ページ, 深刻度"
				+ originalSeverity + " → 下限" + minSeverity + ")");

		// --- Self-check 3: degenerate input does not reproduce. ---
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

		// --- Shrink. ---
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

		// --- Final verification: check the resulting file again from scratch. ---
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
	 * Check HTML on disk against the same invariants <b>without the generator</b>
	 * ({@code -Dfoliojet.fuzzCheckFile=<path>}). Used to verify that reduced results reproduce.
	 *
	 * <p>
	 * Naturally, compute predicate inputs ({@link Generated}) from this document.
	 * <b>Reread the file</b> and follow exactly the same path as the shrinker.
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
		// Also report the axis-switch count used by exclusions. Two or more triggers
		// {@code ExcludedByNestedOrthogonalFlow}, so this value helps check
		// **whether exclusions are too broad** (2026-07-30).
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
	// Predicate.
	// ------------------------------------------------------------------

	/**
	 * Convert one candidate document and return the <b>failure category</b> ({@code null} on success).
	 *
	 * <p>
	 * Convert directly in the test JVM. Launching gradle per candidate causes lock contention
	 * and UP-TO-DATE skips that <b>make the predicate lie</b> (§3.15, examples 2 and 3).
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

		/** The most recent failure itself (for reporting, not decisions). */
		Throwable lastFailure;

		/** The most recent failure's <b>severity</b>. See {@link FuzzShrinker#severity}. */
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
				final String kind = RandomDocumentFuzzTest.classify(t);
				// Stack overflow locations (top stack frame) vary between runs, so use one category regardless of location
				// (2026-10-07, seed 12453214; comparing locations made every candidate a different category, preventing shrinking).
				return kind != null && kind.startsWith("StackOverflowError@") ? "StackOverflowError" : kind;
			}
		}

		private static int countPages(final File dir) {
			final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
			return pages == null ? 0 : pages.length;
		}
	}

	/**
	 * Failure <b>severity</b>. Even within one category, damage differing by orders of magnitude
	 * is a different matter.
	 *
	 * <p>
	 * <b>Without this, the shrinker slides down to the invariant boundary</b> (actually encountered
	 * on 2026-07-28). Invariant 6 allows {@code 2 × maximum explicit size}, so removing
	 * {@code width:}/{@code height:} declarations <b>reduces the allowance to zero</b>,
	 * letting rounding-error-level overflow satisfy the same category. The first reduction of
	 * seed 194970 had <b>0.12 pt overflow</b> and no relation to the original failure.
	 * This is the continuous-valued form of the degenerate solutions described in §3.15.
	 * </p>
	 *
	 * <p>
	 * Use excess overflow for off-paper placement and the last blank page's number for blank pages.
	 * The latter avoids mistaking a single blank page obtained by removing all content for an
	 * extra trailing blank page. Return {@code 1} for other categories.
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
				// Overflow in excess of the portion explained by author-specified sizes.
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
	// Shrinking loop.
	// ------------------------------------------------------------------

	private static final class Shrinker {
		private final Probe probe;

		private final String target;

		private final int maxEvals;

		private final long deadline;

		/** If the original document produced pages, require at least one from candidates too. */
		private final boolean requirePages;

		/** Minimum severity to preserve. Prevent sliding down to the boundary. */
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
		 * Try one candidate. Accept it <b>only if it fails in the same category</b>.
		 *
		 * <p>
		 * <b>Reject</b> degenerate solutions (no remaining tokens or no output pages) even
		 * when the category matches. Example 4 in §3.15 satisfied "there is a blank page"
		 * by deleting all body text.
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
				return false; // Same category, but orders of magnitude less severe.
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
					// After single deletions stop working, try **two at once**.
					// Cases exist where neither A nor B can be removed alone, but both can be removed together
					// (a float and the box that `clear`s it, for example). Run this only after minimizing
					// the element count, so even O(n^2) needs only dozens of evaluations.
					progress = this.deletePairs();
				}
			}
		}

		/** Escape when single deletions stop working: delete two subtrees together. */
		private boolean deletePairs() throws Exception {
			final List<Node> forest = parseBody(this.current);
			final List<Node> elements = nodesOf(forest);
			for (int i = 0; i < elements.size(); ++i) {
				for (int j = i + 1; j < elements.size(); ++j) {
					final Node a = elements.get(i), b = elements.get(j);
					if (contains(a, b) || contains(b, a)) {
						continue; // For nested nodes, deleting one suffices.
					}
					if (this.accept(rebuild(this.current, forest, java.util.Set.of(a, b), null))) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Operation 1: delete entire subtrees (first because it has the greatest effect).
		 *
		 * <p>
		 * <b>Include text nodes.</b> Otherwise nothing can remove bare text created by unwrap
		 * ({@code T4} left after stripping its parent), leaving an unnecessarily large fixed point
		 * (measured on 2026-07-28: seven bare text nodes remained in a 13-element fixed point).
		 * </p>
		 */
		private boolean deleteSubtree() throws Exception {
			final List<Node> forest = parseBody(this.current);
			final List<Node> elements = nodesOf(forest);
			// Try largest first to maximize the reduction per accepted candidate.
			elements.sort(Comparator.comparingInt((final Node n) -> serialize(n).length()).reversed());
			for (final Node n : elements) {
				if (this.accept(rebuild(this.current, forest, n, null))) {
					return true;
				}
			}
			return false;
		}

		/** Operation 2: replace an element with its children (remove one nesting level). */
		private boolean unwrap() throws Exception {
			final List<Node> forest = parseBody(this.current);
			final List<Node> elements = elementsOf(forest);
			for (final Node n : elements) {
				if (n.children.isEmpty()) {
					continue; // Same as deletion.
				}
				if (this.accept(rebuild(this.current, forest, (Node) null, n))) {
					return true;
				}
			}
			return false;
		}

		/** Operation 3: drop one attribute ({@code rowspan}/{@code colspan}/{@code style}). */
		private boolean dropAttribute() throws Exception {
			final List<Node> forest = parseBody(this.current);
			for (final Node n : elementsOf(forest)) {
				for (int a = n.attrs.size() - 1; a >= 0; --a) {
					if (KEEP_ATTRS.contains(n.attrs.get(a).name)) {
						continue; // Do not touch image URIs (§3.15, example 5).
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
		 * Operation 4: drop one CSS declaration, from either {@code style} attributes or
		 * {@code <style>} blocks (also propose deleting entire rules).
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
			// Drop the entire rule (one line in `<style>`).
			for (final int[] rule : styleRuleLines(this.current)) {
				final String candidate = this.current.substring(0, rule[0]) + this.current.substring(rule[1]);
				if (this.accept(candidate)) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Operation 5: move numbers toward zero (or the lower bound). <b>Binary-search one at a time</b>;
		 * decrementing by one would take 250 evaluations for {@code width:250pt}.
		 *
		 * <p>
		 * Number positions shift on every acceptance, but <b>the count stays the same</b>
		 * (only digits change, so regex matches are neither added nor removed).
		 * Preserve identity by <b>index</b> and locate them again each time.
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
						break; // Unexpected. Stop on the safe side.
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

	/** Attributes that must not be dropped. {@code src} is the image URI itself. */
	private static final Set<String> KEEP_ATTRS = Set.of("src");

	// ------------------------------------------------------------------
	// Recompute predicate inputs from the candidate document.
	// ------------------------------------------------------------------

	private static final Pattern PAGE_WIDTH_PI = Pattern
			.compile("output\\.page-width\"\\s+value=\"(\\d+)pt\"");

	private static final Pattern PAGE_HEIGHT_PI = Pattern
			.compile("output\\.page-height\"\\s+value=\"(\\d+)pt\"");

	private static final Pattern PAGE_MARGIN_RULE = Pattern.compile("@page\\{margin:(\\d+)pt");

	private static final Pattern BODY_FONT_RULE = Pattern.compile("font:normal (\\d+)pt");

	/** Tokens inserted by the generator. Inspect only text nodes. */
	private static final Pattern TOKEN = Pattern.compile("T\\d+");

	/**
	 * Rebuild {@link Generated} from the candidate document. <b>Reuse none of the original values.</b>
	 *
	 * <p>
	 * Return {@code null} if the skeleton read by the oracle (paper-size PIs, {@code @page}
	 * margins, or {@code body}'s {@code font}) is missing. Computing without it silently
	 * changes the meaning of {@code isOversized}/{@code isTinyPage}, checking documents
	 * that should be excluded or vice versa.
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
	 * Collect tokens and tokens that may legitimately reorder from the tree.
	 *
	 * <p>
	 * <b>Compute reorderability from the tree</b>: whether a token lies in a subtree with
	 * {@code float:} or {@code position:absolute}. Reusing the generator's recorded set
	 * makes it disagree with reality when a reduction drops the {@code float} declaration.
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
			// Reverse flex (row/column-reverse, wrap-reverse) also legitimately changes reading order.
			// Match the generator's recording rules (v2) (2026-08-23).
			final boolean here = inReorderable
					|| (style != null && (floated || style.contains("position:absolute")
							|| style.contains("-reverse")));
			collectTokens(n.children, here, tokens, reorderable);
		}
	}

	/**
	 * Is this a <b>degenerate candidate</b>: unparseable or with no remaining tokens?
	 * The shrinker seeks the cheapest way to satisfy the predicate; relaxing this inevitably
	 * yields a false minimum claiming reproduction with an empty body.
	 */
	private static boolean degenerate(final String html) {
		final Generated g = analyze(html);
		if (g == null || g.tokens().isEmpty()) {
			return true;
		}
		// Reject candidates that break the table content model (2026-08-23). Stripping td/tr
		// leaves bare text directly under the table; the HTML parser's foster parenting
		// moves it **before** the table, invalidating the oracle's assumption that the
		// generator's token order (source order) is the expected reading order. This falsely
		// reports legitimate layout as reversed reading order (seen while shrinking v2 seed 30;
		// the generator itself never produces this structure).
		return breaksTableContentModel(html);
	}

	/** Does this structure trigger foster parenting, such as bare text directly under {@code <table>}? */
	private static boolean breaksTableContentModel(final String html) {
		return breaksTableContentModel(parseBody(html));
	}

	private static boolean breaksTableContentModel(final List<Node> nodes) {
		for (final Node n : nodes) {
			if (n.tag == null) {
				continue;
			}
			final String tag = n.tag;
			if ("table".equals(tag) || "thead".equals(tag) || "tbody".equals(tag) || "tfoot".equals(tag)
					|| "tr".equals(tag)) {
				for (final Node child : n.children) {
					if (child.tag == null) {
						if (child.text != null && !child.text.isBlank()) {
							return true;
						}
					}
				}
			}
			if (breaksTableContentModel(n.children)) {
				return true;
			}
		}
		return false;
	}

	private static int countElements(final String html) {
		return elementsOf(parseBody(html)).size();
	}

	/** For cross-checking the degenerate side: a document with an empty {@code <body>}. */
	private static String emptyBody(final String html) {
		final int[] b = bodyRange(html);
		return html.substring(0, b[0]) + "\n" + html.substring(b[1]);
	}

	// ------------------------------------------------------------------
	// Minimal tag-matching parser (do not add an HTML parser).
	// ------------------------------------------------------------------

	/**
	 * Generator output is <b>well formed</b>, so counting tags suffices.
	 * A real HTML parser would normalize and change the document,
	 * equivalent to rewriting the shrinker's predicate input without permission.
	 */
	static final class Node {
		/** Element name. {@code null} for text nodes. */
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

	/** Does {@code a}'s subtree contain {@code b}? */
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
	 * Deletion candidates: all elements and text that is <b>not whitespace-only</b>.
	 *
	 * <p>
	 * Including whitespace-only text wastes evaluations deleting generator-inserted newlines
	 * one by one (measured: 689→2,638 evaluations), while <b>collapsing the minimum into one
	 * unreadable line</b>. It barely reduces byte count either.
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
	// CSS declaration and number positions.
	// ------------------------------------------------------------------

	private static final Pattern STYLE_ATTR = Pattern.compile("style=\"([^\"]*)\"");

	private static final Pattern SRC_ATTR = Pattern.compile("src=\"[^\"]*\"");

	/**
	 * A range containing semicolon-separated declarations: {@code style} attribute values
	 * and contents of {@code { }} in {@code <style>} blocks.
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

	/** A one-line, one-rule range inside {@code <style>} (including the newline). */
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

	/** One reducible number. */
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
	 * Collect numbers eligible for reduction.
	 *
	 * <p>
	 * <b>Never touch {@code src} attributes</b>: image URIs contain numbers, as in
	 * {@code copper4}, and changing them silently removes images (§3.15, example 5).
	 * </p>
	 *
	 * <p>
	 * <b>Never touch numbers before {@code <body>} either</b> (actually encountered on 2026-07-28).
	 * Paper dimensions, {@code @page} margins, and the reference font size are <b>the invariant's
	 * measuring stick itself</b>. Invariant 6's allowance uses twice the paper size and twice the
	 * maximum explicit size; exclusion ({@code isTinyPage}) depends on whether the content area
	 * is eight times the reference font size. Allowing these to shrink lets the shrinker
	 * <b>shorten the measuring stick</b> to satisfy the predicate. The first trial reduced
	 * the paper from 120x400 pt to <b>8x8 pt</b> and the font from 11 pt to <b>1 pt</b>,
	 * returning a <b>meaningless minimum</b>: 1 pt characters overflow 8 pt paper.
	 * Paper settings are <b>fixtures</b>, not reduction targets.
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
