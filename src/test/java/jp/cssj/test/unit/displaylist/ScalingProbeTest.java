package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import javax.imageio.ImageIO;
import javax.management.NotificationEmitter;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;
import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Measures how time, allocation and live memory grow with the size of each document shape, and how throughput grows
 * with concurrent conversions (2026-10-31, performance audit). Runs only when asked:
 *
 * <ul>
 * <li>{@code -Dfoliojet.scaling=all|shape,shape} converts each shape at its base size and at {@code scalingFactor}
 * (4) times it, and prints the growth exponent. {@code -Dfoliojet.scalingScale=0.5} scales the base sizes,
 * {@code -Dfoliojet.scalingJfr=true} samples the larger run with JFR for every shape (otherwise only for exponents
 * over 1.25), {@code -Dfoliojet.scalingHisto=true} takes a class histogram halfway through the larger run.</li>
 * <li>{@code -Dfoliojet.concurrency=shape} converts that shape on 1, 2, 4, 8, 16 threads and records lock
 * contention at 16.</li>
 * </ul>
 */
public class ScalingProbeTest extends TestCase {
	private static final String TEXT = "吾輩は猫である。名前はまだ無い。どこで生れたかとんと見当がつかぬ。"
			+ "The quick brown fox jumps over the lazy dog, again and again. ";

	private record Shape(String name, int base, IntFunction<String> style, IntFunction<String> body) {
	}

	private static String repeat(final int n, final IntFunction<String> item) {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < n; ++i) s.append(item.apply(i));
		return s.toString();
	}

	private static String nest(final int depth, final String open, final String inner, final String close) {
		return open.repeat(depth) + inner + close.repeat(depth);
	}

	private static final Map<Integer, String> PNG = new HashMap<>();

	/** A distinct 8x8 PNG for each i, as a data URI. */
	private static String png(final int i) {
		return PNG.computeIfAbsent(i, k -> {
			try {
				final BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
				for (int y = 0; y < 8; ++y) for (int x = 0; x < 8; ++x) image.setRGB(x, y, (k * 7919 + x * 31 + y) & 0xFFFFFF);
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(image, "png", out);
				return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
			} catch (final Exception e) {
				throw new IllegalStateException(e);
			}
		});
	}

	private static final List<Shape> SHAPES = List.of(
			new Shape("paras", 2000, n -> "", n -> repeat(n, i -> "<p>" + i + " " + TEXT + TEXT + "</p>")),
			new Shape("longpara", 20000, n -> "", n -> "<p>" + repeat(n, i -> "word" + i + " ") + "</p>"),
			new Shape("tableAuto", 4000, n -> "td{border:0.5pt solid}",
					n -> "<table>" + repeat(n, i -> "<tr><td>" + i + "</td><td>" + TEXT + "</td><td>" + i * 7 + "</td></tr>") + "</table>"),
			new Shape("tableFixed", 4000, n -> "table{table-layout:fixed;width:100%}td{border:0.5pt solid}",
					n -> "<table>" + repeat(n, i -> "<tr><td>" + i + "</td><td>" + TEXT + "</td><td>" + i * 7 + "</td></tr>") + "</table>"),
			new Shape("tableCollapsed", 4000, n -> "table{border-collapse:collapse}td{border:0.5pt solid}",
					n -> "<table>" + repeat(n, i -> "<tr><td>" + i + "</td><td>" + TEXT + "</td><td>" + i * 7 + "</td></tr>") + "</table>"),
			new Shape("tbodyHeight", 2000, n -> "table{table-layout:fixed;width:100%;border-collapse:collapse}tbody{height:1pt}td{border:0.5pt solid}",
					n -> "<table><tbody>" + repeat(n, i -> "<tr><td>" + i + "</td><td>row</td></tr>") + "</tbody></table>"),
			new Shape("inlineStyles", 3000, n -> "",
					n -> "<table>" + repeat(n, i -> "<tr>" + repeat(4, c -> "<td style='color:#333;padding:1pt 2pt;border:0.5pt solid #999;text-align:right;font-size:9pt'>"
							+ i + "</td>") + "</tr>") + "</table>"),
			new Shape("whereRules", 1000, n -> repeat(n, i -> ":where(.c" + i + "){color:#" + String.format("%06x", i * 2654435 & 0xFFFFFF) + "}"),
					n -> repeat(n, i -> "<p class=c" + i + ">" + i + "</p>")),
			new Shape("vars", 3000, n -> ":root{--c:#123456;--m:2pt;--s:10pt;--l:1.4;--b:0.5pt solid #ccc}p{color:var(--c);margin:var(--m) 0;font-size:var(--s);line-height:var(--l);border-bottom:var(--b)}",
					n -> repeat(n, i -> "<p>" + i + " " + TEXT + "</p>")),
			new Shape("hyphens", 2000, n -> ".h{hyphens:auto;text-align:justify}",
					n -> "<div lang=en class=h>" + repeat(n, i -> "<p>" + i + " Internationalization considerations notwithstanding, "
							+ "the extraordinarily comprehensive documentation characterizes incomprehensibilities.</p>") + "</div>"),
			new Shape("tableCols", 60, n -> "td{border:0.5pt solid;font-size:5pt}",
					n -> "<table>" + repeat(30, r -> "<tr>" + repeat(n, c -> "<td>" + r + "." + c + "</td>") + "</tr>") + "</table>"),
			new Shape("bigCell", 1500, n -> "td{border:0.5pt solid}",
					n -> "<table><tr><td>" + repeat(n, i -> "<p>" + i + " " + TEXT + "</p>") + "</td></tr></table>"),
			new Shape("nestedDiv", 400, n -> "div{margin-left:0.01pt}.x div p{color:#333}",
					n -> "<div class=x>" + nest(n, "<div><p>level</p>", "<p>" + TEXT + "</p>", "</div>") + "</div>"),
			new Shape("nestedTable", 6, n -> "td{border:0.5pt solid}",
					n -> nest(n, "<table><tr><td>a</td><td>", TEXT, "</td></tr></table>")),
			new Shape("floats", 1000, n -> ".f{float:left;width:20pt;height:12pt;background:#ccc}",
					n -> repeat(n, i -> "<p><span class=f></span>" + i + " " + TEXT + "</p>")),
			new Shape("footnotes", 800, n -> ".fn{float:footnote}",
					n -> repeat(n, i -> "<p>" + i + " " + TEXT + "<span class=fn>note " + i + "</span></p>")),
			new Shape("pageFloats", 300, n -> ".t{float:top;height:30pt;background:#eee}",
					n -> repeat(n, i -> "<p>" + i + " " + TEXT + TEXT + "</p><div class=t>figure " + i + "</div>")),
			new Shape("pages", 1000, n -> ".b{break-before:page}", n -> repeat(n, i -> "<div class=b>page " + i + "</div>")),
			new Shape("nthChild", 3000,
					n -> "p:nth-child(3n+1){color:red}p:nth-last-child(2n){color:blue}p:last-of-type{font-weight:bold}p:nth-of-type(5n){font-style:italic}",
					n -> "<div>" + repeat(n, i -> "<p>" + i + "</p>") + "</div>"),
			new Shape("generalSibling", 3000, n -> ".a~p{color:red}.a~.z{color:blue}",
					n -> "<div><p class=a>a</p>" + repeat(n, i -> "<p>" + i + "</p>") + "</div>"),
			new Shape("rules", 1000, n -> repeat(n, i -> ".c" + i + "{color:#" + String.format("%06x", i * 2654435 & 0xFFFFFF) + "}"),
					n -> repeat(n, i -> "<p class=c" + i + ">" + i + "</p>")),
			new Shape("spans", 5000, n -> "", n -> "<p>" + repeat(n, i -> "<span>w" + i + " </span>") + "</p>"),
			new Shape("ruby", 1500, n -> "body{writing-mode:vertical-rl}",
					n -> repeat(n / 10, p -> "<p>" + repeat(10, i -> "<ruby>漢字<rt>かんじ</rt></ruby>の" + TEXT.substring(0, 16)) + "</p>")),
			new Shape("vertical", 1500, n -> "body{writing-mode:vertical-rl}", n -> repeat(n, i -> "<p>" + i + " " + TEXT + "</p>")),
			new Shape("multicol", 2000, n -> ".m{column-count:3}", n -> "<div class=m>" + repeat(n, i -> "<p>" + i + " " + TEXT + "</p>") + "</div>"),
			new Shape("flex", 3000, n -> ".f{display:flex;flex-wrap:wrap}.f>div{width:80pt}",
					n -> "<div class=f>" + repeat(n, i -> "<div>item " + i + "</div>") + "</div>"),
			new Shape("grid", 3000, n -> ".g{display:grid;grid-template-columns:repeat(4,1fr)}",
					n -> "<div class=g>" + repeat(n, i -> "<div>item " + i + "</div>") + "</div>"),
			new Shape("targetCounter", 500, n -> "a::after{content:' p.' target-counter(attr(href),page)}.b{break-before:page}",
					n -> repeat(n, i -> "<p><a href=#t" + i + ">ref " + i + "</a></p>") + repeat(n, i -> "<p class=" + (i % 10 == 0 ? "b" : "x")
							+ " id=t" + i + ">target " + i + " " + TEXT + "</p>")),
			new Shape("list", 3000, n -> "", n -> "<ol>" + repeat(n, i -> "<li>" + TEXT + "</li>") + "</ol>"),
			new Shape("absolute", 1000, n -> ".a{position:absolute}",
					n -> repeat(n, i -> "<div class=a style='top:" + i % 700 + "pt;left:" + i % 400 + "pt'>a" + i + "</div>")),
			new Shape("images", 600, n -> "img{width:10pt;height:10pt}", n -> "<p>" + repeat(n, i -> "<img src='" + png(i) + "'> ") + "</p>"),
			new Shape("stringSet", 400, n -> "h2{string-set:x content();break-before:page}@page{@top-center{content:string(x)}}",
					n -> repeat(n, i -> "<h2>Section " + i + "</h2><p>" + TEXT + TEXT + "</p>")),
			new Shape("links", 3000, n -> "", n -> repeat(n / 10, p -> "<p>" + repeat(10, i -> "<a href='https://example.com/" + p + "/" + i + "'>link" + i + "</a> ") + "</p>")));

	private static String html(final Shape shape, final int n) {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>@page{size:A4;margin:15mm}body{margin:0;font:10pt/1.5 serif}"
				+ shape.style().apply(n) + "</style></head><body>" + shape.body().apply(n) + "</body></html>";
	}

	private static final AtomicLong peakAfterGc = new AtomicLong();
	private static boolean listening;

	private static synchronized void listenToGc() {
		if (listening) return;
		listening = true;
		for (final GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			((NotificationEmitter) gc).addNotificationListener((notification, handback) -> {
				if (!notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) return;
				final var info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
				final long used = info.getGcInfo().getMemoryUsageAfterGc().values().stream().mapToLong(MemoryUsage::getUsed).sum();
				peakAfterGc.accumulateAndGet(used, Math::max);
			}, null, null);
		}
	}

	private static long usedAfterFullGc() {
		System.gc();
		return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
	}

	private static long allocated() {
		return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getTotalThreadAllocatedBytes();
	}

	/** One conversion; returns {wall ms, allocated MB, peak live MB over the baseline, retained MB after}. */
	private static double[] convert(final File file, final long histoAtMillis, final StringBuilder histo) throws Exception {
		final long base = usedAfterFullGc();
		peakAfterGc.set(base);
		final long alloc0 = allocated();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		final Thread watchdog = new Thread(() -> {
			try {
				if (histoAtMillis > 0) {
					Thread.sleep(histoAtMillis);
					histo.append(classHistogram());
					Thread.sleep(Math.max(0, 120_000L - histoAtMillis));
				} else {
					Thread.sleep(120_000L);
				}
				session.abort(CTISession.ABORT_FORCE);
			} catch (final Exception e) {
				// Finished first
			}
		}, "scaling-watchdog");
		watchdog.setDaemon(true);
		final long t0 = System.nanoTime();
		watchdog.start();
		String failure = null;
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(OutputStream.nullOutputStream())));
			session.setMessageHandler((code, args, mes) -> {
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, file, "text/html", null);
		} catch (final Exception e) {
			failure = e.toString();
		} finally {
			watchdog.interrupt();
		}
		final double ms = (System.nanoTime() - t0) / 1e6;
		final double allocMb = (allocated() - alloc0) / 1048576.0;
		final double peakMb = (peakAfterGc.get() - base) / 1048576.0;
		session.close();
		final double retainedMb = (usedAfterFullGc() - base) / 1048576.0;
		if (failure != null) System.out.println("[scale]   failed: " + failure);
		return new double[] { ms, allocMb, peakMb, retainedMb };
	}

	private static String classHistogram() {
		try {
			final String all = (String) ManagementFactory.getPlatformMBeanServer().invoke(
					new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
					new Object[] { new String[0] }, new String[] { String[].class.getName() });
			final String[] lines = all.split("\n");
			final StringBuilder s = new StringBuilder();
			for (int i = 0; i < Math.min(lines.length, 22); ++i) s.append("\n[scale]   ").append(lines[i]);
			return s.toString();
		} catch (final Exception e) {
			return "\n[scale]   histogram failed: " + e;
		}
	}

	/** Top frames of the JFR samples: the innermost engine frame and the innermost frame of all. */
	private static String hotFrames(final Path jfr, final String eventName, final boolean weighByDuration) throws Exception {
		final Map<String, Double> ours = new HashMap<>(), leaf = new HashMap<>(), path = new HashMap<>();
		// -Dfoliojet.scalingFrames=net.zamasoft.foliojet.layout: also count the innermost frame under that prefix with
		// its two callers
		final String prefix = System.getProperty("foliojet.scalingFrames");
		double total = 0;
		for (final RecordedEvent e : RecordingFile.readAllEvents(jfr)) {
			if (!e.getEventType().getName().equals(eventName) || e.getStackTrace() == null) continue;
			final double w = weighByDuration ? e.getDuration().toNanos() / 1e6 : 1;
			total += w;
			final List<RecordedFrame> frames = e.getStackTrace().getFrames();
			if (!frames.isEmpty()) leaf.merge(name(frames.get(0)), w, Double::sum);
			for (int i = 0; prefix != null && i < frames.size(); ++i) {
				if (frames.get(i).getMethod().getType().getName().startsWith(prefix)) {
					final StringBuilder p = new StringBuilder(name(frames.get(i)));
					for (int j = i + 1; j < Math.min(frames.size(), i + 3); ++j) p.append(" < ").append(name(frames.get(j)));
					path.merge(p.toString(), w, Double::sum);
					break;
				}
			}
			for (final RecordedFrame f : frames) {
				final String type = f.getMethod().getType().getName();
				if (type.startsWith("net.zamasoft.") || type.startsWith("jp.cssj.")) {
					String key = name(f);
					if (weighByDuration && e.hasField("monitorClass") && e.getClass("monitorClass") != null) {
						key = e.getClass("monitorClass").getName() + " @ " + key;
					}
					ours.merge(key, w, Double::sum);
					break;
				}
			}
		}
		final StringBuilder s = new StringBuilder();
		s.append(String.format("%n[scale]   %s: %.0f %s", eventName, total, weighByDuration ? "ms" : "samples"));
		top(s, "engine", ours, total);
		if (!weighByDuration) top(s, "leaf", leaf, total);
		if (prefix != null) top(s, "path", path, total);
		return s.toString();
	}

	private static String name(final RecordedFrame f) {
		final String type = f.getMethod().getType().getName();
		return type.substring(type.lastIndexOf('.') + 1) + "." + f.getMethod().getName() + ":" + f.getLineNumber();
	}

	private static void top(final StringBuilder s, final String label, final Map<String, Double> counts, final double total) {
		counts.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(8)
				.forEach(e -> s.append(String.format("%n[scale]     %-6s %5.1f%% %s", label, 100 * e.getValue() / total, e.getKey())));	}

	private static File write(final String name, final String html) throws Exception {
		final File f = new File("local/scaling/" + name + ".html");
		f.getParentFile().mkdirs();
		Files.writeString(f.toPath(), html, StandardCharsets.UTF_8);
		return f;
	}

	public void testScaling() throws Exception {
		final String which = System.getProperty("foliojet.scaling");
		if (which == null) return;
		final double scale = Double.parseDouble(System.getProperty("foliojet.scalingScale", "1"));
		final int factor = Integer.getInteger("foliojet.scalingFactor", 4);
		final boolean jfrAll = Boolean.getBoolean("foliojet.scalingJfr"), histo = Boolean.getBoolean("foliojet.scalingHisto");
		listenToGc();
		// Warm up: the first conversions spend most of their time loading classes
		final File warm = write("warm", html(SHAPES.get(0), 300));
		for (int i = 0; i < 3; ++i) convert(warm, 0, null);
		final List<String> rows = new ArrayList<>();
		for (final Shape shape : SHAPES) {
			if (!which.equals("all") && !List.of(which.split(",")).contains(shape.name())) continue;
			final int n = Math.max(1, (int) Math.round(shape.base() * scale)), big = n * factor;
			final File small = write(shape.name() + "-" + n, html(shape, n)), large = write(shape.name() + "-" + big, html(shape, big));
			final double[] a = convert(small, 0, null), a2 = convert(small, 0, null);
			if (a2[0] < a[0]) System.arraycopy(a2, 0, a, 0, 4);
			final Recording rec = new Recording();
			rec.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
			rec.start();
			final StringBuilder h = new StringBuilder();
			final double[] b = convert(large, histo ? (long) (a[0] * factor * 0.5) : 0, h);
			rec.stop();
			final double k = Math.log(b[0] / a[0]) / Math.log(factor), ka = Math.log(b[1] / a[1]) / Math.log(factor);
			final String row = String.format("[scale] %-14s n=%6d/%7d time %8.0f/%9.0f ms k=%.2f  alloc %7.0f/%8.0f MB k=%.2f  peak %6.0f/%7.0f MB  retained %5.1f/%6.1f MB",
					shape.name(), n, big, a[0], b[0], k, a[1], b[1], ka, a[2], b[2], a[3], b[3]);
			System.out.println(row);
			rows.add(row);
			if (jfrAll || k > 1.25 || ka > 1.25) {
				final Path p = Files.createTempFile("scaling", ".jfr");
				rec.dump(p);
				final String hot = hotFrames(p, "jdk.ExecutionSample", false);
				System.out.println(hot.substring(1));
				rows.add(hot.substring(1));
				Files.delete(p);
			}
			rec.close();
			if (h.length() > 0) {
				System.out.println(h.substring(1));
				rows.add(h.substring(1));
			}
			Files.delete(small.toPath());
			Files.delete(large.toPath());
		}
		Files.write(new File("local/scaling/result.txt").toPath(), rows, StandardCharsets.UTF_8);
	}

	public void testConcurrency() throws Exception {
		final String which = System.getProperty("foliojet.concurrency");
		if (which == null) return;
		final Shape shape = SHAPES.stream().filter(s -> s.name().equals(which)).findFirst().orElseThrow();
		final double scale = Double.parseDouble(System.getProperty("foliojet.scalingScale", "1"));
		final File file = write("conc-" + which, html(shape, Math.max(1, (int) Math.round(shape.base() * scale))));
		final int perThread = Integer.getInteger("foliojet.concurrencyDocs", 4);
		final List<String> rows = new ArrayList<>();
		for (int i = 0; i < 3; ++i) convert(file, 0, null);
		double single = 0;
		final var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		for (final int threads : new int[] { 1, 2, 4, 8, 16 }) {
			Recording rec = null;
			if (threads == 16) {
				rec = new Recording();
				rec.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1)).withStackTrace();
				rec.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(5)).withStackTrace();
				rec.start();
			}
			final long cpu0 = os.getProcessCpuTime(), t0 = System.nanoTime();
			final List<Thread> workers = new ArrayList<>();
			for (int t = 0; t < threads; ++t) {
				final Thread w = new Thread(() -> {
					for (int d = 0; d < perThread; ++d) {
						try {
							final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
							session.setResults(new SingleResult(new StreamFragmentedOutput(OutputStream.nullOutputStream())));
							session.setMessageHandler((code, args, mes) -> {
							});
							session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
							session.property("input.include", "**");
							CTISessionHelper.transcodeFile(session, file, "text/html", null);
							session.close();
						} catch (final Exception e) {
							System.out.println("[conc]   failed: " + e);
						}
					}
				}, "conc-" + t);
				workers.add(w);
				w.start();
			}
			for (final Thread w : workers) w.join();
			final double sec = (System.nanoTime() - t0) / 1e9, cpu = (os.getProcessCpuTime() - cpu0) / 1e9;
			final double perSec = threads * perThread / sec;
			if (threads == 1) single = perSec;
			final String row = String.format("[conc] %-10s threads=%2d docs=%3d %6.1f s  %6.2f docs/s  speedup %5.2f  cpu %5.1f cores",
					which, threads, threads * perThread, sec, perSec, perSec / single, cpu / sec);
			System.out.println(row);
			rows.add(row);
			if (rec != null) {
				rec.stop();
				final Path p = Files.createTempFile("conc", ".jfr");
				rec.dump(p);
				for (final String ev : new String[] { "jdk.JavaMonitorEnter", "jdk.ThreadPark" }) {
					final String hot = hotFrames(p, ev, true);
					System.out.println(hot.substring(1));
					rows.add(hot.substring(1));
				}
				Files.delete(p);
				rec.close();
			}
		}
		Files.write(new File("local/scaling/concurrency-" + which + ".txt").toPath(), rows, StandardCharsets.UTF_8);
	}
}
