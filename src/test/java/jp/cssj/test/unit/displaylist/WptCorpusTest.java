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
 * Run CSS documents from WPT (web-platform-tests) against <b>invariants 1–3 only</b>.
 *
 * <p>
 * <b>This is opt-in</b> ({@code -Dfoliojet.wptDir=<path>}). Without the option, the test passes
 * without doing anything: the corpus resides outside this repository (by default
 * {@code F:\dev\wpt-css}) and is not intended for CI or regular full test runs.
 * </p>
 *
 * <h2>Why only 1–3</h2>
 *
 * <p>
 * WPT is a corpus that encodes <b>specification conformance</b>, which is not this product's goal
 * (`教訓集` §1.2). Judging by reference images or selector conformance would produce many
 * differences from <b>intentional nonconformance</b>, making it impossible to distinguish
 * what should be fixed from what we decided not to fix (the same trap as Acid2).
 * </p>
 *
 * <p>
 * In contrast, <b>invariants 1–3</b>—no termination by exception, termination, and a bounded page count—
 * must hold <b>regardless of the specification</b>. No input may crash the engine.
 * WPT therefore serves only as a source of previously unknown inputs.
 * </p>
 *
 * <p>
 * Invariants 4/7/8 (loss, reading order, duplication) cannot apply because they <b>require unique
 * tokens</b>.
 * Invariant 5 (blank pages) does not apply until blank pages can be distinguished from failures to
 * resolve external references (there are 241 absolute references such as {@code /fonts/ahem.css}).
 * </p>
 *
 * <h2>Page dimensions</h2>
 *
 * <p>
 * WPT documents specify dimensions such as {@code height:100px} assuming <b>viewport rendering</b>.
 * With the default A4 page, <b>almost all fit on one page</b>, which misses this product's defect
 * area (pagination). {@code -Dfoliojet.wptPageSize=120x120} (pt) makes the page smaller,
 * for the same reason that the generator uses 60x60 pt.
 * </p>
 *
 * <p>
 * <b>Measure the split rate first.</b> This test reports both counts by category and
 * <b>the distribution of page counts</b>. If no splitting occurs, this corpus has no value for this
 * purpose.
 * </p>
 *
 * <pre>
 * ./gradlew test --tests '*WptCorpusTest*' --rerun --no-watch-fs -PtestHeap=4096m \
 *   -Dfoliojet.wptDir=/mnt/f/dev/wpt-css/css -Dfoliojet.wptLimit=50 \
 *   -Dfoliojet.wptPageSize=120x120
 * </pre>
 */
public class WptCorpusTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Timeout. The same value as {@link RandomDocumentFuzzTest}. */
	private static final long WATCHDOG_MS = 30_000L;

	/** Upper bound for invariant 3. The same value as {@link RandomDocumentFuzzTest}. */
	private static final int MAX_PAGES = 300;

	public void testWptInvariants() throws Exception {
		final String dir = System.getProperty("foliojet.wptDir");
		if (dir == null) {
			// Opt-in. The corpus resides outside the repository.
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
				// **Keep all results**. Violations are rare (17/2,409 measured), so
				// there is no reason to stop early; doing so would invite the unsupported
				// assumption that "the rest are probably the same."
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

		// Save the list of documents with violations. To reproduce just the violations without
		// rerunning the entire corpus (6.5 minutes on one core), use a format that can be read with
		// -Dfoliojet.wptOnly=<this file>: one path relative to root per line.
		final File list = new File("local/wpt/violations.txt");
		list.getParentFile().mkdirs();
		Files.write(list.toPath(), violations);
		System.out.println("[wpt] 違反した文書の一覧: " + list.getPath() + " (" + violations.size() + "件)");

		// **Report without failing** by default (as in the sweep).
		// Set -Dfoliojet.wptFailOnViolation=true to make the test fail.
		if (Boolean.getBoolean("foliojet.wptFailOnViolation") && !classCount.isEmpty()) {
			fail("不変条件1〜3の違反が" + classCount.values().stream().mapToInt(AtomicInteger::get).sum() + "件");
		}
	}

	/** Result for one document. A non-null {@code failure} indicates an invariant violation. */
	private record Result(int pages, String failure, String detail) {
	}

	/**
	 * Convert one document and check invariants 1–3.
	 *
	 * <p>
	 * Use <b>the same definitions</b> as {@link RandomDocumentFuzzTest#checkDocument}
	 * (a 30-second watchdog and a 300-page limit). Different values would make it unclear whether
	 * a failure seen in WPT but not in the sweep comes from measurement differences or implementation
	 * differences.
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
		// Match the sweep's stack size so StackOverflow in deeply nested input
		// is not mistaken for an implementation defect.
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
		// Invariant 2: termination
		if (worker.isAlive()) {
			// Actually attempt to stop it, rather than leave it running (as in the sweep: an unstoppable thread
			// retains the heap for one layout, causing a self-amplifying resource jam).
			final DirectSession s = session[0];
			if (s != null) {
				try {
					s.abort(jp.cssj.cti2.CTISession.ABORT_FORCE);
					worker.join(5_000L);
				} catch (final Exception ignore) {
					// Proceed to reporting even if the interrupt request fails.
				}
			}
			if (worker.isAlive()) {
				worker.setPriority(Thread.MIN_PRIORITY);
			}
			return new Result(0, "停止しない(watchdog " + WATCHDOG_MS / 1000 + "秒超過)", null);
		}
		// Invariant 1: no termination by exception
		if (failure[0] != null) {
			return new Result(0, "例外で中断: " + failure[0].getClass().getSimpleName(), summarize(failure[0]));
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		final int count = pages == null ? 0 : pages.length;
		if (count == 0) {
			// **Do not count this as an invariant violation**: WPT contains documents with empty bodies
			// (only a reference skeleton), which cannot be distinguished from correct blank-page
			// suppression (the same reason for excluding invariant 5).
			return new Result(0, null, null);
		}
		// Invariant 3: bounded page count
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
	 * If {@code -Dfoliojet.wptOnly=<path>} is specified, process only that list
	 * (one path relative to root per line). Otherwise, gather all documents with {@link #collect}.
	 *
	 * <p>
	 * Use this to iterate on reproducing and fixing violations: the whole corpus takes 6.5 minutes on
	 * one core, so paying that cost every time to rerun 17 documents is wasteful
	 * (`教訓集`, "反復を高速化せよ").
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
	 * Collect target documents.
	 *
	 * <p>
	 * <b>Exclude documents containing scripts or iframes.</b> The product does not execute scripts,
	 * and iframes load separate documents, so either makes it unclear what was converted.
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
