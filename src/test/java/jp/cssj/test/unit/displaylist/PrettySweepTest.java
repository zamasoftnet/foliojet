package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

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
 * Sweep of the Knuth-Plass line breaking path ({@code text-wrap-style: pretty}) and of slides ({@code output.media_types}
 * with {@code projection}) over the random documents of {@link RandomDocumentFuzzTest} (2026-10-24,
 * TECH-20261024-005: a pretty paragraph on a slide failed with a NullPointerException, and every slide after one
 * that ran past the page went onto that page). The 36M sweep generates neither, so this lays out each document with
 * {@code body} opted into pretty (unless {@code -Dfoliojet.prettyOff}) in print and on slides, and counts by kind:
 * exceptions by type and top frame, words drawn in print but not on slides, and words drawn wholly past the end of
 * a slide along the page's block axis (a slide grows to hold its content). It checks no other layout invariant.
 * With {@code -Dfoliojet.prettyDump=dir} it writes the documents that found something. Without
 * {@code -Dfoliojet.prettySweep} it does nothing.
 *
 * <pre>
 * wsl -e bash dev/tools/wsl/fj-test.sh '*PrettySweepTest' -Dfoliojet.prettySweep=20000 [-Dfoliojet.prettyFrom=N]
 * </pre>
 */
public class PrettySweepTest extends TestCase {
	public PrettySweepTest(final String name) {
		super(name);
	}

	private static final Pattern BODY_WRITING_MODE = Pattern.compile("body\\{[^}]*writing-mode:([a-z-]+)");
	private static final Pattern TEXT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) (artifact )?Text\\[\"([^\"]*)\" asc=([\\d.]+) desc=([\\d.]+)\\]( clip=)?");

	/**
	 * One layout: an exception, or the words drawn, the words drawn wholly past a slide's block end, and the number of
	 * words whose origin lies off the page by more than their size.
	 */
	private record Outcome(String crash, Set<String> drawn, List<String> beyond, int offPage) {
	}

	public void testPrettySweep() throws Exception {
		final String count = System.getProperty("foliojet.prettySweep");
		if (count == null || count.isBlank()) {
			return;
		}
		final int n = Integer.parseInt(count.trim());
		final int from = Integer.parseInt(System.getProperty("foliojet.prettyFrom", "1"));
		final boolean pretty = System.getProperty("foliojet.prettyOff") == null;
		final ExecutorService pool = Executors
				.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 2));
		final List<Future<List<String>>> results = new ArrayList<>();
		for (int i = 0; i < n; ++i) {
			final int seed = from + i;
			results.add(pool.submit(() -> check(seed, pretty)));
		}
		final Map<String, List<String>> kinds = new TreeMap<>();
		for (final Future<List<String>> f : results) {
			for (final String r : f.get()) {
				final int tab = r.indexOf('\t');
				kinds.computeIfAbsent(r.substring(tab + 1), k -> new ArrayList<>()).add(r.substring(0, tab));
			}
		}
		pool.shutdown();
		System.out.println("[pretty] documents=" + n + " from=" + from + " pretty=" + pretty + " kinds=" + kinds.size());
		for (final Map.Entry<String, List<String>> e : kinds.entrySet()) {
			final List<String> seeds = e.getValue();
			System.out.println("[pretty] " + seeds.size() + " " + e.getKey() + " seeds="
					+ seeds.subList(0, Math.min(8, seeds.size())));
		}
	}

	/** Lays out one document in print and on slides; returns "seed\tkind" lines. */
	private static List<String> check(final int seed, final boolean pretty) {
		String html = RandomDocumentFuzzTest.generate(seed, true, false, false).html();
		if (pretty) {
			html = html.replaceFirst("body\\{", "body{text-wrap-style:pretty;");
		}
		final List<String> found = new ArrayList<>();
		final Outcome print = layout(seed, html, false);
		final Outcome slides = layout(seed, html, true);
		if (print.crash() != null) {
			found.add(seed + "\tprint " + print.crash());
		}
		if (slides.crash() != null) {
			found.add(seed + "\tslides " + slides.crash());
		}
		if (print.crash() == null && slides.crash() == null) {
			final Set<String> lost = new HashSet<>(print.drawn());
			lost.removeAll(slides.drawn());
			if (!lost.isEmpty()) {
				found.add(seed + "\tslides lost words drawn in print");
			}
			if (!slides.beyond().isEmpty()) {
				// Split by whether print keeps every word on its page: if not, the document overflows anyway
				found.add(seed + "\tslides words past the page end (" + slides.beyond().get(0).replaceAll(" .*", "")
						+ (print.offPage() == 0 ? ", print on paper)" : ", print off paper too)"));
			}
		}
		final String dump = System.getProperty("foliojet.prettyDump");
		if (dump != null && !found.isEmpty()) {
			try {
				Files.writeString(new File(dump, seed + ".html").toPath(), html);
			} catch (final java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
		return found;
	}

	private static Outcome layout(final int seed, final String html, final boolean slides) {
		File dir = null;
		try {
			dir = Files.createTempDirectory("pretty-sweep-").toFile();
			final ByteArrayOutputStream pdf = new ByteArrayOutputStream();
			try (AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath());
					AutoCloseable geometry = DisplayListDumper.scopedDetailedGeometry(true)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
						null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(pdf)));
					session.setMessageHandler(
							CTIMessageHelper.createStreamMessageHandler(new PrintStream(OutputStream.nullOutputStream())));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.property-pi", "true");
					if (slides) {
						session.property("output.media_types", "all projection paged visual bitmap static");
					}
					CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
							URI.create("file:///fuzz/" + seed + ".html"), "text/html", "UTF-8");
				} finally {
					session.close();
				}
			}
			final Matcher wm = BODY_WRITING_MODE.matcher(html);
			final String mode = wm.find() ? wm.group(1) : "horizontal-tb";
			final Set<String> drawn = new HashSet<>();
			final List<String> beyond = new ArrayList<>();
			int offPage = 0;
			try (PDDocument doc = Loader.loadPDF(pdf.toByteArray())) {
				for (int i = 0; i < doc.getNumberOfPages(); ++i) {
					final File page = new File(dir, String.format("page-%04d.txt", i + 1));
					if (!page.exists()) {
						continue;
					}
					final PDRectangle box = doc.getPage(i).getMediaBox();
					final Matcher t = TEXT.matcher(Files.readString(page.toPath()));
					while (t.find()) {
						if (t.group(3) != null) {
							// A copy of a box drawn on more than one page
							continue;
						}
						for (final String word : t.group(4).split("\\s+")) {
							if (!word.isEmpty()) {
								drawn.add(word);
							}
						}
						if (t.group(7) != null) {
							continue;
						}
						final double x = Double.parseDouble(t.group(1)), y = Double.parseDouble(t.group(2));
						final double asc = Double.parseDouble(t.group(5)), desc = Double.parseDouble(t.group(6));
						final double size = asc + desc;
						if (x < -size || x > box.getWidth() + size || y < -size || y > box.getHeight() + size) {
							++offPage;
						}
						if (!slides) {
							continue;
						}
						final boolean past = switch (mode) {
						case "vertical-rl" -> x + desc < -1;
						case "vertical-lr" -> x - asc > box.getWidth() + 1;
						default -> y - asc > box.getHeight() + 1;
						};
						if (past) {
							beyond.add(mode + " p" + (i + 1) + " " + t.group(4));
						}
					}
				}
			}
			return new Outcome(null, drawn, beyond, offPage);
		} catch (final Throwable t) {
			Throwable root = t;
			while (root.getCause() != null && root.getCause() != root) {
				root = root.getCause();
			}
			final StackTraceElement[] st = root.getStackTrace();
			final String top = st.length > 0
					? st[0].getClassName().replaceAll(".*\\.", "") + "." + st[0].getMethodName() + ":" + st[0].getLineNumber()
					: "?";
			return new Outcome(root.getClass().getSimpleName() + " at " + top, Set.of(), List.of(), 0);
		} finally {
			if (dir != null) {
				final File[] files = dir.listFiles();
				if (files != null) {
					for (final File f : files) {
						f.delete();
					}
				}
				dir.delete();
			}
		}
	}
}
