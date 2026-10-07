package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Production-routing regression guard for MULTICOL native worklist descent
 * (legacy recursion removal, increments 1–4, 2026-07-30). Convert each fixture once
 * with production routing and verify:
 *
 * <ol>
 * <li><b>Non-vacuous coverage</b>: documents crossing a MULTICOL boundary have
 * {@code MULTICOL_NATIVE_DESCENTS > 0} (native scope descent actually ran).</li>
 * <li><b>Zero fallbacks</b>: every document has {@code WORKLIST_COMPAT_FALLBACKS == 0}
 * (no escape through compatibility fallback for unknown containers).</li>
 * <li><b>Content preservation</b>: expected inline-document tokens (T2, etc.) are drawn
 * <b>exactly once</b> across all pages, with no loss or duplication
 * (the same kind of check as {@code NestedMulticolDuplicationTest}).</li>
 * </ol>
 *
 * <p>
 * <b>History</b>: at increment 1 (routing unchanged), this proved byte-for-byte display-list
 * equivalence of legacy recursive and forced worklist drivers (foliojet4 67c2414).
 * Increment 2 switched the gate; increment 4 physically removed the old driver and override
 * mechanism, making driver comparison impossible and unnecessary. Redefine this as a production-routing
 * regression guard (running the same implementation twice cannot detect deterministic order regressions;
 * tier1 goldens fix the display lists). codex consultation: design consultation §5.
 * </p>
 */
public class MulticolWorklistScopeTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Timeout. Measured execution is under a few seconds per case. */
	private static final long WATCHDOG_MS = 60_000L;

	/** Extract content from display-list text-drawing lines. */
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** Nested columns (two within three): MOVE_SENTINEL type. The chain crosses a MULTICOL boundary. */
	private static final String NESTED_MULTICOL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="595pt"?>
			<?jp.cssj.property name="output.page-height" value="842pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{font:normal 9pt/1.2 serif}
			</style></head><body>
			<div style="column-count:3">
			T2
			<div style="column-count:2">
			T4
			<p></p>
			T6
			</div>
			</div>
			</body></html>
			""";

	/** Three nested multi-column levels: verify nested MulticolRestyleScope (a scope inside a scope). */
	private static final String TRIPLE_MULTICOL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="595pt"?>
			<?jp.cssj.property name="output.page-height" value="842pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{font:normal 9pt/1.2 serif}
			</style></head><body>
			<div style="column-count:3">
			T2
			<div style="column-count:2">
			T4
			<div style="column-count:2">
			T5
			<p></p>
			T6
			</div>
			</div>
			</div>
			</body></html>
			""";

	/** Vertical writing, two nested multi-column levels, and {@code <ol>}: SPLIT_FRAGMENT_REPLAY type. */
	private static final String VERTICAL_NESTED = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 7pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<div style="column-count:2">
			<div style="column-count:2">
			<span style="display:inline-block;width:42pt"></span>
			<ol>
			T24
			<li>T25</li>
			</ol>
			</div>
			</div>
			</body></html>
			""";

	/**
	 * Minimal case where inner columns break during balancing replay of closed outer columns
	 * (reduced extreme strict seed 4540). The inner COLUMN continuation prunes the open stack
	 * with contextFlow as owner, so an old restyle call frame must not end the same flow twice.
	 */
	private static final String NESTED_BALANCE_COLUMN_BREAK = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="200pt"?>
			<?jp.cssj.property name="output.page-height" value="200pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 7pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			xy T163 xy
			<div style="column-count:3">
			<div style="margin:5pt;padding:5pt">
			<textarea></textarea>
			<div style="margin:7pt;padding:3pt">
			<div style="display:table;float:left;width:83pt"><ol></ol></div>
			<div style="column-count:2">
			<div style="float:left"><table></table></div>
			<div style="display:grid">T630</div>
			</div></div></div></div>
			</body></html>
			""";

	/**
	 * Minimal case preserving later content when COLUMN continuation restacks open flows with different identities.
	 */
	private static final String NESTED_COLUMN_BREAK_TRAILING_CONTENT = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="200pt"?>
			<?jp.cssj.property name="output.page-height" value="200pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 7pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<select></select>
			<p><span style="display:inline-block;width:45pt"></span></p>
			<select><option>x</option><option>y</option></select>
			<div style="display:flex"><div style="column-count:2">
			<div style="column-count:2">
			<p><span style="display:inline-block;width:124pt">T364</span></p>
			</div>
			<div style="writing-mode:vertical-rl">T365</div>
			</div></div>
			</body></html>
			""";

	public void testNestedMulticolNativeDescent() throws Exception {
		assertProductionRouting("nested-multicol", null, NESTED_MULTICOL, true, "T2", "T4", "T6");
	}

	public void testTripleMulticolNativeDescent() throws Exception {
		assertProductionRouting("triple-multicol", null, TRIPLE_MULTICOL, true, "T2", "T4", "T5", "T6");
	}

	public void testVerticalNestedNoFallback() throws Exception {
		assertProductionRouting("vertical-nested", null, VERTICAL_NESTED, false, "T24", "T25");
	}

	public void testNestedBalanceColumnBreakClosesEachFlowOnce() throws Exception {
		assertProductionRouting("nested-balance-column-break", null, NESTED_BALANCE_COLUMN_BREAK, false, "T163",
				"T630");
	}

	public void testNestedColumnBreakPreservesTrailingContent() throws Exception {
		assertProductionRouting("nested-column-break-trailing", null, NESTED_COLUMN_BREAK_TRAILING_CONTENT, false,
				"T364", "T365");
	}

	public void testColumnsFloatNoFallback() throws Exception {
		assertProductionRouting("columns-float", new File("files/unittest/0400-column-count/columns-float.html"), null,
				false);
	}

	public void testPageFirstNoFallback() throws Exception {
		assertProductionRouting("page-first", new File("files/unittest/0400-column-count/page-first.html"), null,
				false);
	}

	/**
	 * Convert one document with production routing and check counters, token preservation, and no leaks.
	 *
	 * @param name                output directory name
	 * @param source              input file (mutually exclusive with {@code html})
	 * @param html                inline document (mutually exclusive with {@code source})
	 * @param expectNativeDescent true if the open chain crosses a MULTICOL boundary
	 *                            and should trigger native descent
	 * @param expectedTokens      tokens that must be drawn exactly once across all pages
	 *                            (inline documents only)
	 */
	private static void assertProductionRouting(final String name, final File source, final String html,
			final boolean expectNativeDescent, final String... expectedTokens) throws Exception {
		final File input;
		if (source != null) {
			input = source;
		} else {
			input = new File("local/unittest/multicol-worklist/" + name + "/input.html");
			input.getParentFile().mkdirs();
			try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
				w.write(html);
			}
		}

		final List<String> dumps = transcodeAndDump(name + "/production", input);
		assertTrue(name + ": ページが出ていません", !dumps.isEmpty());

		assertEquals(name + ": worklist駆動が互換フォールバックへ逃げました", 0,
				ContinuationStats.WORKLIST_COMPAT_FALLBACKS.get());
		if (expectNativeDescent) {
			assertTrue(name + ": native降下が空振り(MULTICOL経路を通っていない)",
					ContinuationStats.MULTICOL_NATIVE_DESCENTS.get() > 0);
		}

		if (expectedTokens.length > 0) {
			final Map<String, Integer> counts = new LinkedHashMap<>();
			for (final String dump : dumps) {
				for (final String line : dump.split("\\n")) {
					// Artifacts from visual rescue splitting draw the same indivisible box translated
					// onto the next page; they are not duplicate logical content. Exclude them
					// from token counts, as the production fuzz oracle does.
					if (line.contains(" artifact ")) {
						continue;
					}
					final Matcher m = TEXT.matcher(line);
					while (m.find()) {
						for (final String word : m.group(1).trim().split("\\s+")) {
							counts.merge(word, 1, Integer::sum);
						}
					}
				}
			}
			for (final String token : expectedTokens) {
				final Integer n = counts.get(token);
				assertNotNull(name + ": トークン" + token + "が消失", n);
				assertEquals(name + ": トークン" + token + "が複製", 1, n.intValue());
			}
		}
	}

	/**
	 * Convert with production routing and return per-page display-list dumps. Reset counters immediately before
	 * conversion.
	 */
	private static List<String> transcodeAndDump(final String name, final File input) throws Exception {
		final File dir = new File("local/unittest/multicol-worklist/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles((d, n) -> n.endsWith(".txt"));
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		ContinuationStats.reset();
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "multicol-worklist-" + name.replace('/', '-'), 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}

		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		java.util.Arrays.sort(pages);
		final List<String> dumps = new ArrayList<>();
		for (final File page : pages) {
			dumps.add(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return dumps;
	}
}
