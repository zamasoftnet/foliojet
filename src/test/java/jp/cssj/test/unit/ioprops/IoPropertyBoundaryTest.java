package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.ua.props.BooleanPropManager;
import net.zamasoft.foliojet.ua.props.CodePropManager;
import net.zamasoft.foliojet.ua.props.DoublePropManager;
import net.zamasoft.foliojet.ua.props.IntegerPropManager;
import net.zamasoft.foliojet.ua.props.LongPropManager;
import net.zamasoft.foliojet.ua.props.PropCode;
import net.zamasoft.foliojet.ua.props.PropManager;
import net.zamasoft.foliojet.ua.props.StringPropManager;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Exercise I/O property boundary values comprehensively (2026-10-05, user instruction:
 * "Test I/O property boundary values comprehensively").
 *
 * <p>
 * For every property in {@link UAProps#all()}, set boundary values one at a time by type and lay out
 * a small document: boolean spelling variants; zero, negative, maximum, overflowing, and nonnumeric
 * integer/real values; all valid and invalid choices; and empty, very long, length-valued, or malformed-URI
 * strings. Unacceptable failures are unexpected exceptions (including 4001), nontermination, and broken
 * PDFs.
 * Explainable aborts (such as exceeding limits, with codes other than 4001) count as correct behavior.
 * Results go to {@code build/reports/ioprop-boundary/results.tsv}.
 * </p>
 */
public class IoPropertyBoundaryTest extends TestCase {
	private static final long TIMEOUT_SECONDS = 30;

	private static final String DOC = """
			<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
			<title>boundary</title>
			<style>@page { size: 300pt 300pt; margin: 20pt } h1 { font-size: 14pt } .p2 { break-before: page }</style>
			</head><body>
			<h1 id="top">見出し Heading</h1>
			<p>本文の文字列 text with <a href="#second">a link</a> and <a href="https://example.com/">an external one</a>.</p>
			<table border="1"><tr><td>1</td><td>2</td></tr><tr><td>3</td><td>4</td></tr></table>
			<ul><li>one</li><li>two</li></ul>
			<p><img alt="dot" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" width="20" height="20"/>
			<input type="text" value="field"/></p>
			<h1 class="p2" id="second">二頁目 Second</h1>
			<p>שלום world مرحبا</p>
			</body></html>
			""";

	/** Kinds of unexpected results. */
	private enum Outcome {
		/** Layout completed. */
		DONE,
		/** An explainable abort (a code other than 4001). */
		REFUSED,
		/** An unexpected exception, 4001, nontermination, or broken output. */
		FAILED
	}

	private record Case(String name, String value, Outcome outcome, String detail, int badPropertyWarnings) {
	}

	private static final class CapturingResults implements Results {
		final List<ByteArrayOutputStream> outputs = new ArrayList<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.outputs.add(out);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// Do nothing.
		}
	}

	/** Boundary values by type. Remove duplicates. */
	static List<String> values(final PropManager manager) throws Exception {
		final Set<String> values = new LinkedHashSet<>();
		final String name = manager.getName();
		if (manager instanceof BooleanPropManager) {
			values.addAll(List.of("true", "false", "TRUE", "False", "", "yes", "1", "x"));
		} else if (manager instanceof IntegerPropManager) {
			values.addAll(List.of("0", "1", "-1", "-2", "2", "-2147483648", "2147483647", "2147483648", "99999999", "1.5", "",
					" 3 ", "x"));
		} else if (manager instanceof LongPropManager) {
			values.addAll(List.of("0", "1", "-1", "-2", "9223372036854775807", "-9223372036854775808",
					"9223372036854775808", "", "x"));
		} else if (manager instanceof DoublePropManager) {
			values.addAll(List.of("0", "-0", "1", "-1", "0.0001", "1e308", "1e-308", "NaN", "Infinity", "-Infinity", "",
					"x"));
		} else if (manager instanceof CodePropManager<?> code) {
			final var field = CodePropManager.class.getDeclaredField("type");
			field.setAccessible(true);
			for (final Object e : ((Class<?>) field.get(code)).getEnumConstants()) {
				final String ident = ((PropCode) e).ident();
				values.add(ident);
				values.add(ident.toUpperCase(Locale.ROOT));
			}
			values.addAll(List.of("", "x"));
		} else if (manager instanceof StringPropManager) {
			values.addAll(List.of("", " ", "x", "x".repeat(4096)));
			if (name.matches(".*(width|height|margin|trim|inset|size|spine|viewport|bleed).*")) {
				values.addAll(List.of("0", "0mm", "-10mm", "1mm", "1e9mm", "100%", "10px", "mm", "10 mm 5", "auto",
						"NaNmm"));
			}
			if (name.matches(".*(uri|stylesheet|referer|href|url|include|exclude|lang|host).*")) {
				values.addAll(List.of("::", "file:///nonexistent/x.css", "data:,", "http://", "%zz"));
			}
			if (name.matches(".*(port|limit|time|count|ttl|resolution).*")) {
				values.addAll(List.of("0", "-1", "65536", "2147483648", "1e3"));
			}
			if (name.matches(".*(encoding|charset).*")) {
				values.addAll(List.of("x-unknown-charset", "UTF-16", "ISO-2022-JP"));
			}
		} else {
			values.addAll(List.of("", "x"));
		}
		return List.copyOf(values);
	}

	/** The output format in which this property takes effect. */
	private static String outputType(final String name) {
		if (name.startsWith("output.image.") || name.equals("output.image-pixel-limit")) {
			return "image/png";
		}
		if (name.startsWith("output.paged-svg.")) {
			return "application/vnd.copper.paged-svg";
		}
		return null;
	}

	private static Case run(final ExecutorService executor, final String name, final String value) {
		final List<String> warnings = new ArrayList<>();
		final CapturingResults results = new CapturingResults();
		final DirectSession session;
		try {
			session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		} catch (final Exception e) {
			return new Case(name, value, Outcome.FAILED, "session: " + e, 0);
		}
		final Future<Object> future = executor.submit(() -> {
			try {
				session.setResults(results);
				session.setMessageHandler(new MessageHandler() {
					@Override
					public void message(final short code, final String[] args, final String message) {
						if (code == 0x2804) {
							warnings.add(message);
						}
					}
				});
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				final String type = outputType(name);
				if (type != null && !name.equals("output.type")) {
					session.property("output.type", type);
				}
				session.property(name, value);
				CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(DOC.getBytes(StandardCharsets.UTF_8)),
						URI.create("file:///boundary.xhtml"), "application/xhtml+xml", null);
				return null;
			} finally {
				session.close();
			}
		});
		try {
			future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (final TimeoutException e) {
			try {
				session.abort(CTISession.ABORT_FORCE);
			} catch (final Exception ignored) {
				// The result is the same even if it cannot be stopped.
			}
			future.cancel(true);
			return new Case(name, value, Outcome.FAILED, "timeout " + TIMEOUT_SECONDS + "s", warnings.size());
		} catch (final java.util.concurrent.ExecutionException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof TranscoderException te && te.getCode() != 0x4001) {
				return new Case(name, value, Outcome.REFUSED, Integer.toHexString(te.getCode()) + " " + te.getMessage(),
						warnings.size());
			}
			return new Case(name, value, Outcome.FAILED, describe(cause), warnings.size());
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Case(name, value, Outcome.FAILED, "interrupted", warnings.size());
		}
		// Output check: PDF must be readable by PDFBox.
		final String type = outputType(name);
		// Intermediate passes produce no result (running the final pass afterward is a continued conversion).
		final boolean noOutputExpected = name.equals("processing.middle-pass") && value.equalsIgnoreCase("true");
		if (type == null && !name.equals("output.type") && !noOutputExpected) {
			if (results.outputs.isEmpty()) {
				return new Case(name, value, Outcome.FAILED, "no output", warnings.size());
			}
			for (final ByteArrayOutputStream out : results.outputs) {
				final byte[] bytes = out.toByteArray();
				if (bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P') {
					try (PDDocument doc = Loader.loadPDF(bytes)) {
						if (doc.getNumberOfPages() == 0) {
							return new Case(name, value, Outcome.FAILED, "PDF without pages", warnings.size());
						}
					} catch (final Exception e) {
						return new Case(name, value, Outcome.FAILED, "corrupt PDF: " + e, warnings.size());
					}
				}
			}
		}
		return new Case(name, value, Outcome.DONE, results.outputs.size() + " output(s)", warnings.size());
	}

	private static String describe(final Throwable thrown) {
		final StringBuilder sb = new StringBuilder(String.valueOf(thrown));
		if (thrown instanceof TranscoderException te) {
			sb.insert(0, Integer.toHexString(te.getCode()) + " ");
		}
		Throwable t = thrown;
		Throwable cause = t.getCause();
		while (cause != null && cause != t) {
			sb.append(" <- ").append(cause);
			t = cause;
			cause = cause.getCause();
		}
		final StackTraceElement[] st = t.getStackTrace();
		if (st.length > 0) {
			sb.append(" @ ").append(st[0]);
		}
		return sb.toString().replace('\t', ' ').replace('\n', ' ');
	}

	public void testEveryPropertyAtItsBoundaries() throws Exception {
		final List<Case> cases = new ArrayList<>();
		final ExecutorService executor = Executors.newCachedThreadPool();
		try {
			for (final PropManager manager : UAProps.all()) {
				for (final String value : values(manager)) {
					cases.add(run(executor, manager.getName(), value));
				}
			}
		} finally {
			executor.shutdownNow();
		}
		final File dir = new File("build/reports/ioprop-boundary");
		dir.mkdirs();
		try (PrintWriter out = new PrintWriter(new File(dir, "results.tsv"), StandardCharsets.UTF_8)) {
			out.println("property\tvalue\toutcome\tbad-property-warnings\tdetail");
			for (final Case c : cases) {
				final String value = c.value().length() > 40 ? c.value().substring(0, 20) + "…(" + c.value().length() + ")"
						: c.value();
				out.println(c.name() + "\t" + value + "\t" + c.outcome() + "\t" + c.badPropertyWarnings() + "\t" + c.detail());
			}
		}
		final StringBuilder failures = new StringBuilder();
		for (final Case c : cases) {
			if (c.outcome() == Outcome.FAILED) {
				failures.append('\n').append(c.name()).append('=').append(
						c.value().length() > 40 ? c.value().substring(0, 20) + "…" : c.value()).append(": ").append(c.detail());
			}
		}
		assertTrue(cases.size() + " cases; failures:" + failures, failures.length() == 0);
	}
}
