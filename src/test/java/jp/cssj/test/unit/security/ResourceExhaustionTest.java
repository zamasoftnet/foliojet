package jp.cssj.test.unit.security;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Small documents that used to exhaust the heap or spin, in a JVM that other conversions share (2026-10-26, found
 * by the loop termination audit and reproduced on 19152 with a 2 GB heap). Each must convert quickly and draw its
 * text.
 */
public class ResourceExhaustionTest extends TestCase {
	private static final long WATCHDOG_MS = 30_000L;

	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page { size: 300pt 200pt; margin: 10pt }";

	/** var() references multiplying the tokens at every level: nine levels of ten, 10^9 tokens (1 KB). */
	public void testVarExpansion() throws Exception {
		final StringBuilder css = new StringBuilder(":root{--l0:");
		for (int i = 0; i < 10; ++i) {
			css.append(" 1px");
		}
		css.append(';');
		for (int level = 1; level <= 8; ++level) {
			css.append("--l").append(level).append(':');
			for (int i = 0; i < 10; ++i) {
				css.append(" var(--l").append(level - 1).append(')');
			}
			css.append(';');
		}
		css.append("} p{box-shadow:var(--l8)}");
		assertDrawn(HEAD + css + "</style></head><body><p>T1</p></body></html>");
	}

	/** @counter-style pad with the largest integer (230 bytes). */
	public void testCounterStylePad() throws Exception {
		assertDrawn(HEAD + "@counter-style p{system:cyclic;symbols:\"x\";pad:2147483647 \"0\"} li{list-style-type:p}"
				+ "</style></head><body><ol><li>T1</li></ol></body></html>");
	}

	/** A wavy decoration of a vanishing thickness: billions of steps in the draw phase. */
	public void testThinWavyDecoration() throws Exception {
		assertDrawn(HEAD + "</style></head><body><p style=\"text-decoration: underline wavy;"
				+ " text-decoration-thickness: 0.0000001px\">T1</p></body></html>");
	}

	/** References that expand to nothing multiply the work just the same (codex review). */
	public void testEmptyVarExpansion() throws Exception {
		final StringBuilder css = new StringBuilder(":root{--l0:var(--missing,);");
		for (int level = 1; level <= 9; ++level) {
			css.append("--l").append(level).append(':');
			for (int i = 0; i < 10; ++i) {
				css.append(" var(--l").append(level - 1).append(')');
			}
			css.append(';');
		}
		css.append("} p{margin:var(--l9) 0}");
		assertDrawn(HEAD + css + "</style></head><body><p>T1</p></body></html>");
	}

	/** An unknown env() with a long fallback, expanded through var() (codex review). */
	public void testEnvFallbackExpansion() throws Exception {
		final StringBuilder css = new StringBuilder(":root{--l0:env(unknown,");
		for (int i = 0; i < 1000; ++i) {
			css.append(" 1px");
		}
		css.append(");");
		for (int level = 1; level <= 5; ++level) {
			css.append("--l").append(level).append(':');
			for (int i = 0; i < 10; ++i) {
				css.append(" var(--l").append(level - 1).append(')');
			}
			css.append(';');
		}
		css.append("} p{box-shadow:var(--l5)}");
		assertDrawn(HEAD + css + "</style></head><body><p>T1</p></body></html>");
	}

	/** An unknown env() whose fallback is a function of 100,000 arguments, referenced 4,000 times (codex review). */
	public void testNestedEnvFallback() throws Exception {
		final String css = ":root{--a:env(unknown, foo(" + " 1px".repeat(100_000) + "))} p{box-shadow:"
				+ " var(--a)".repeat(4_000) + "}";
		assertDrawn(HEAD + css + "</style></head><body><p>T1</p></body></html>");
	}

	/** Dotted and dashed decorations of a vanishing thickness (codex review: Java2D's dasher could not get past them). */
	public void testVanishingDashedDecoration() throws Exception {
		assertDrawn(HEAD + "</style></head><body><p style=\"text-decoration: underline dotted;"
				+ " text-decoration-thickness: 1e-20px\">T1</p><p style=\"text-decoration: underline dashed;"
				+ " text-decoration-thickness: 1e-20px\">T2</p></body></html>");
	}

	/** A counter symbol of 100,000 characters, padded and repeated (codex review: the cap counted repetitions only). */
	public void testHugeCounterSymbol() throws Exception {
		final String symbol = "x".repeat(100_000);
		assertDrawn(HEAD + "@counter-style p{system:symbolic;symbols:\"" + symbol + "\";pad:1000 \"" + symbol
				+ "\"} li{list-style-type:p}</style></head><body><ol start=\"900\"><li>T1</li></ol></body></html>");
	}

	/** A wavy underline of an ordinary thickness under a huge letter-spacing (codex review). */
	public void testWavyUnderHugeLetterSpacing() throws Exception {
		assertDrawn(HEAD + "</style></head><body><p style=\"white-space: nowrap; letter-spacing: 100000000px;"
				+ " text-decoration: underline wavy\">ab</p><p>T1</p></body></html>");
	}

	/**
	 * 3,000 nested inline elements, flattened past 512 levels. 20,000 did not finish in 120 seconds before the limit and
	 * still take about 20 seconds: each line carries the open inline elements.
	 */
	public void testDeepInlineNesting() throws Exception {
		assertDrawn(HEAD + "</style></head><body><p>" + "<em>a ".repeat(3_000) + "b" + " b</em>".repeat(3_000)
				+ "</p><p>T1</p></body></html>");
	}

	/** Past 512 levels the elements become siblings, and every text stays exactly once. */
	public void testNestingLimitKeepsContent() throws Exception {
		final StringBuilder body = new StringBuilder();
		for (int i = 1; i <= 600; ++i) {
			body.append("<div>D").append(i).append(' ');
		}
		body.append("</div>".repeat(600)).append("<p>T1</p>");
		final String page = this.convert(HEAD + "@page { size: 300pt 20000pt }</style></head><body>" + body
				+ "</body></html>");
		for (int i = 1; i <= 600; ++i) {
			final String token = "Text[\"D" + i + "\"";
			assertEquals("D" + i, 1, page.split(java.util.regex.Pattern.quote(token), -1).length - 1);
		}
	}

	private void assertDrawn(final String html) throws Exception {
		final String page = this.convert(html);
		assertTrue(this.getName() + ": T1 が無い", page.contains("Text[\"T1\""));
	}

	/** Converts under the watchdog and returns the display list of all pages. */
	private String convert(final String html) throws Exception {
		final File dir = Files.createTempDirectory("resource-exhaustion").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null); ByteArrayOutputStream out = new ByteArrayOutputStream();
					AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				CTISessionHelper.transcodeFile(session, input, "text/html", null);
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "resource-exhaustion-" + this.getName(), 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(this.getName() + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(this.getName() + ": 変換が例外で終わった", failure[0]);
		}
		final StringBuilder pages = new StringBuilder();
		for (int page = 1;; ++page) {
			final File f = new File(dir, String.format("page-%04d.txt", page));
			if (!f.exists()) {
				break;
			}
			pages.append(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages.toString();
	}
}
