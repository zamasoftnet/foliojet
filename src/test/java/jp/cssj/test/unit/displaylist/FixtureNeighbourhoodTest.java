package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * The neighbours of the minimized documents (2026-11-03): each document of {@code files/fuzz-repro} with one change, or
 * two, laid out with every sweep invariant.
 *
 * <p>
 * A defect the sweep finds comes back in kin: the columns filled again where no page is known stalled four times in one
 * night in four shapes, and the thickness a table had been given on 10-27 was missing for flex and grid until 11-03. The
 * sweep's coverage counts features of a box and of its parent, while these took three or four boxes nested. A change to
 * a document that already went wrong reaches such kin: another kind of container, one more multicol, another writing
 * mode, a float, or one more host around a box. The first run found a flex in a flex item measured 0 thick next to the
 * fixture of the thickness just fixed.
 * </p>
 *
 * <p>
 * Opt in with {@code -Dfoliojet.neighbourhood=all} or a comma-separated list of fixture names;
 * {@code -Dfoliojet.neighbourhoodThreads} (6); {@code -Dfoliojet.neighbourhoodPairs}: so many documents of two changes
 * per fixture besides those of one (0). Writes {@code build/neighbourhood/report.tsv}, the documents that failed in
 * {@code failed/}, and prints what failed. It reports
 * and does not fail: what it finds is classified like a sweep stop.
 * </p>
 */
public class FixtureNeighbourhoodTest extends TestCase {
	private static final File FIXTURES = new File("files/fuzz-repro");

	private static final File OUT = new File("build/neighbourhood");

	/** The documents that failed and were not excluded, as {@code <fixture>-<n>.html}. */
	private static final File FAILED = new File(OUT, "failed");

	/** Elements whose style is changed; others (table rows, list items, ruby, images) only stay as hosts. */
	private static final Set<String> STYLED = Set.of("div", "p", "span");

	private static final String[] DISPLAYS = { "block", "flow-root", "table", "flex", "flex-column", "grid",
			"inline-block" };

	private static final String[] WRITING_MODES = { "horizontal-tb", "vertical-rl", "vertical-lr" };

	private static final String[][] HOSTS = { //
			{ "cell", "<div style=\"display:table\"><div style=\"display:table-cell\">", "</div></div>" }, //
			{ "float", "<div style=\"float:left\">", "</div>" }, //
			{ "flex-item", "<div style=\"display:flex\">", "</div>" }, //
			{ "grid-item", "<div style=\"display:grid\">", "</div>" }, //
			{ "columns", "<div style=\"column-count:2\">", "</div>" }, //
			{ "horizontal", "<div style=\"writing-mode:horizontal-tb\">", "</div>" }, //
			{ "vertical", "<div style=\"writing-mode:vertical-rl\">", "</div>" } };

	/** A variant that takes longer than this many times its fixture, and at least {@link #SLOW_MIN_MS}, is slow. */
	private static final long SLOW_FACTOR = 20, SLOW_MIN_MS = 5_000;

	/** At most this many elements of a document are changed, in document order. */
	private static final int MAX_ELEMENTS = 40;

	/** A new style for an element, or a host around it, or both. */
	record Edit(Map<String, String> style, String[] host) {
	}

	/** One change: {@code edit} on {@code node}. */
	record Op(FuzzShrinker.Node node, String label, Edit edit) {
	}

	record Variant(String label, String html) {
	}

	public void testNeighbourhood() throws Exception {
		final String spec = System.getProperty("foliojet.neighbourhood");
		if (spec == null || spec.isBlank()) {
			return;
		}
		final int threads = Integer.getInteger("foliojet.neighbourhoodThreads", 6);
		final int pairs = Integer.getInteger("foliojet.neighbourhoodPairs", 0);
		final List<String> names = new ArrayList<>();
		if (spec.equals("all")) {
			final String[] files = FIXTURES.list((d, n) -> n.endsWith(".html"));
			Arrays.sort(files);
			for (final String f : files) {
				names.add(f.substring(0, f.length() - 5));
			}
		} else {
			names.addAll(Arrays.asList(spec.split(",")));
		}
		OUT.mkdirs();
		clear(FAILED);
		final ExecutorService pool = Executors.newFixedThreadPool(threads);
		final List<String[]> rows = new ArrayList<>();
		try {
			for (final String name : names) {
				final String html = Files.readString(new File(FIXTURES, name + ".html").toPath(), StandardCharsets.UTF_8)
						.replace("../unittest/red.png", new File("files/unittest/red.png").toURI().toString());
				if (FuzzShrinker.analyze(html) == null) {
					rows.add(new String[] { name, "-", "読めない(生成器の文書でない)" });
					continue;
				}
				// A WILD document is checked as the sweep checks it, without the strict invariants
				boolean strictMode = true;
				final long t0 = System.nanoTime();
				String base = check(name + "-base", html, true);
				if (!base.equals("ok")) {
					strictMode = false;
					base = check(name + "-base", html, false);
				}
				// A variant this much slower than its fixture counts as a failure: a fix that made the pages go round
				// and round took 18.8s for a document of 3.7s, within the sweep's watchdog of 30s (triage §166)
				final long slow = Math.max(SLOW_MIN_MS, SLOW_FACTOR * (System.nanoTime() - t0) / 1_000_000L);
				if (!base.equals("ok")) {
					// A fixture that fails itself says nothing about its neighbours
					rows.add(new String[] { name, "base", base });
					continue;
				}
				final boolean strict = strictMode;
				final List<Variant> variants = variants(html, pairs, name.hashCode());
				final List<Future<String>> results = new ArrayList<>();
				for (int i = 0; i < variants.size(); ++i) {
					final Variant v = variants.get(i);
					final String label = name + "-" + i;
					results.add(pool.submit(() -> timed(label, v.html(), strict, slow)));
				}
				for (int i = 0; i < variants.size(); ++i) {
					final String result = results.get(i).get();
					rows.add(new String[] { name + (strict ? "" : " (wild)"), variants.get(i).label(), result });
					if (!result.equals("ok") && !result.startsWith("(除外)")) {
						// The failing document itself, to classify it without rebuilding the change (2026-11-05)
						Files.writeString(new File(FAILED, name + "-" + i + ".html").toPath(), variants.get(i).html(),
								StandardCharsets.UTF_8);
					}
				}
			}
		} finally {
			pool.shutdownNow();
		}
		report(rows);
	}

	/** {@link #check}, and "遅い" when it passes after more than {@code slowMs}. */
	private static String timed(final String label, final String html, final boolean strict, final long slowMs) {
		final long t0 = System.nanoTime();
		final String result = check(label, html, strict);
		final long ms = (System.nanoTime() - t0) / 1_000_000L;
		return result.equals("ok") && ms > slowMs ? "遅い(" + ms / 1000 + "秒、上限 " + slowMs / 1000 + "秒)" : result;
	}

	/** Lays the document out with every sweep invariant: "ok", or the sweep's classification of what failed. */
	private static String check(final String label, final String html, final boolean strict) {
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		if (generated == null) {
			return "読めない";
		}
		final File dir = new File(OUT, "work/" + Thread.currentThread().getName());
		try {
			clear(dir);
			RandomDocumentFuzzTest.checkDocument(generated, new File(dir, "input.html"), new File(dir, "dl"), strict,
					"neighbourhood-" + label);
			return "ok";
		} catch (final Throwable t) {
			return RandomDocumentFuzzTest.classify(t);
		}
	}

	private static void clear(final File dir) throws IOException {
		if (dir.exists()) {
			try (var walk = Files.walk(dir.toPath())) {
				walk.sorted(java.util.Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(File::delete);
			}
		}
		dir.mkdirs();
	}

	/** The documents one change away from {@code html}, and {@code pairs} of those two changes away. */
	static List<Variant> variants(final String html, final int pairs, final long seed) {
		final List<FuzzShrinker.Node> forest = FuzzShrinker.parseBody(html);
		final List<Op> ops = ops(forest);
		final List<Variant> out = new ArrayList<>();
		for (final Op op : ops) {
			out.add(new Variant(op.label(), render(html, forest, Map.of(op.node(), op.edit()))));
		}
		final Random r = new Random(seed);
		for (int k = 0, tries = 0; k < pairs && ops.size() > 1 && tries < pairs * 10; ++tries) {
			final Op a = ops.get(r.nextInt(ops.size())), b = ops.get(r.nextInt(ops.size()));
			final Map<FuzzShrinker.Node, Edit> edits = new LinkedHashMap<>();
			if (a.node() == b.node()) {
				// On one element: one new style and one host, in either order
				if ((a.edit().style() == null) == (b.edit().style() == null)) {
					continue;
				}
				final Edit styled = a.edit().style() != null ? a.edit() : b.edit();
				final Edit hosted = a.edit().style() != null ? b.edit() : a.edit();
				edits.put(a.node(), new Edit(styled.style(), hosted.host()));
			} else {
				edits.put(a.node(), a.edit());
				edits.put(b.node(), b.edit());
			}
			out.add(new Variant(a.label() + " + " + b.label(), render(html, forest, edits)));
			++k;
		}
		return out;
	}

	/** Every single change of the elements of {@code forest}. */
	private static List<Op> ops(final List<FuzzShrinker.Node> forest) {
		final List<FuzzShrinker.Node> elements = new ArrayList<>();
		collect(forest, elements);
		final List<Op> out = new ArrayList<>();
		int changed = 0;
		for (int i = 0; i < elements.size() && changed < MAX_ELEMENTS; ++i) {
			final FuzzShrinker.Node n = elements.get(i);
			if (!STYLED.contains(n.tag)) {
				continue;
			}
			++changed;
			final Map<String, String> style = parseStyle(n.attr("style"));
			final String e = "e" + i + "(" + n.tag + ")";
			for (final String d : DISPLAYS) {
				final Map<String, String> s = new LinkedHashMap<>(style);
				s.remove("flex-direction");
				s.put("display", d.equals("flex-column") ? "flex" : d);
				if (d.equals("flex-column")) {
					s.put("flex-direction", "column");
				}
				if (!s.equals(style)) {
					out.add(new Op(n, e + " display:" + d, new Edit(s, null)));
				}
			}
			final Map<String, String> columns = new LinkedHashMap<>(style);
			if (columns.remove("column-count") == null) {
				columns.put("column-count", "2");
			}
			out.add(new Op(n, e + (style.containsKey("column-count") ? " no columns" : " column-count:2"),
					new Edit(columns, null)));
			for (final String w : WRITING_MODES) {
				if (!w.equals(style.get("writing-mode"))) {
					final Map<String, String> s = new LinkedHashMap<>(style);
					s.put("writing-mode", w);
					out.add(new Op(n, e + " writing-mode:" + w, new Edit(s, null)));
				}
			}
			for (final String f : new String[] { "none", "left", "right" }) {
				if (!f.equals(style.getOrDefault("float", "none"))) {
					final Map<String, String> s = new LinkedHashMap<>(style);
					s.put("float", f);
					out.add(new Op(n, e + " float:" + f, new Edit(s, null)));
				}
			}
			for (final String[] host : HOSTS) {
				out.add(new Op(n, e + " in " + host[0], new Edit(null, host)));
			}
		}
		return out;
	}

	private static void collect(final List<FuzzShrinker.Node> nodes, final List<FuzzShrinker.Node> out) {
		for (final FuzzShrinker.Node n : nodes) {
			if (n.tag != null) {
				out.add(n);
				collect(n.children, out);
			}
		}
	}

	private static Map<String, String> parseStyle(final String style) {
		final Map<String, String> m = new LinkedHashMap<>();
		if (style != null) {
			for (final String d : style.split(";")) {
				final int c = d.indexOf(':');
				if (c > 0) {
					m.put(d.substring(0, c).trim(), d.substring(c + 1).trim());
				}
			}
		}
		return m;
	}

	private static String styleOf(final Map<String, String> m) {
		final StringBuilder sb = new StringBuilder();
		for (final Map.Entry<String, String> e : m.entrySet()) {
			sb.append(e.getKey()).append(':').append(e.getValue()).append(';');
		}
		return sb.toString();
	}

	private static final Pattern BODY_OPEN = Pattern.compile("<body[^>]*>");

	/** The document with the {@code edits} made. */
	private static String render(final String html, final List<FuzzShrinker.Node> forest,
			final Map<FuzzShrinker.Node, Edit> edits) {
		final Matcher m = BODY_OPEN.matcher(html);
		if (!m.find()) {
			throw new IllegalStateException("<body>が見つからない");
		}
		final int end = html.indexOf("</body>");
		final StringBuilder sb = new StringBuilder();
		for (final FuzzShrinker.Node n : forest) {
			write(sb, n, edits);
		}
		return html.substring(0, m.end()) + sb + html.substring(end);
	}

	private static void write(final StringBuilder sb, final FuzzShrinker.Node n, final Map<FuzzShrinker.Node, Edit> edits) {
		if (n.tag == null) {
			sb.append(n.text);
			return;
		}
		final Edit edit = edits.get(n);
		final String style = edit == null || edit.style() == null ? null : styleOf(edit.style());
		if (edit != null && edit.host() != null) {
			sb.append(edit.host()[1]);
		}
		sb.append('<').append(n.tag);
		boolean styled = false;
		for (final FuzzShrinker.Attr a : n.attrs) {
			String value = a.value;
			if (style != null && a.name.equals("style")) {
				value = style;
				styled = true;
			}
			sb.append(' ').append(a.name).append("=\"").append(value).append('"');
		}
		if (style != null && !styled) {
			sb.append(" style=\"").append(style).append('"');
		}
		if (n.selfClosing) {
			sb.append(" />");
		} else {
			sb.append('>');
			for (final FuzzShrinker.Node c : n.children) {
				write(sb, c, edits);
			}
			sb.append("</").append(n.tag).append('>');
		}
		if (edit != null && edit.host() != null) {
			sb.append(edit.host()[2]);
		}
	}

	private static void report(final List<String[]> rows) throws IOException {
		final Map<String, Integer> counts = new TreeMap<>();
		final List<String> failures = new ArrayList<>();
		try (Writer w = Files.newBufferedWriter(new File(OUT, "report.tsv").toPath(), StandardCharsets.UTF_8)) {
			for (final String[] r : rows) {
				w.write(String.join("\t", r) + "\n");
				counts.merge(r[2], 1, Integer::sum);
				if (!r[2].equals("ok") && !r[2].startsWith("(除外)")) {
					failures.add(String.join("  ", r));
				}
			}
		}
		System.out.println("[neighbourhood] " + rows.size() + " documents: " + counts);
		for (int i = 0; i < Math.min(200, failures.size()); ++i) {
			System.out.println("[neighbourhood] " + failures.get(i));
		}
	}
}
