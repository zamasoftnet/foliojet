package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Check <b>absolute requirements</b> using randomly generated documents (added 2026-07-25).
 *
 * <p>
 * Previous verification relied on "fixed documents written by humans" (414 unit-corpus documents
 * and 591 visual-corpus documents), making <b>combinations nobody thought of</b> unreachable in principle.
 * This test samples the input space directly. Reviews estimate only "defects discoverable by review",
 * so this measures <b>a different population</b>.
 * </p>
 *
 * <h2>Invariants to check</h2>
 *
 * <ol>
 * <li><b>No exception aborts</b></li>
 * <li><b>Termination</b> (finishes within the watchdog limit)</li>
 * <li><b>Bounded page count</b> (does not explode relative to content volume)</li>
 * <li><b>No content loss</b>: every unique token embedded in the document appears in the output display list.
 * STRICT mode only (see below).</li>
 * <li><b>No unintended blank pages</b>: STRICT mode only.</li>
 * <li><b>No unexplained placement outside the paper</b>: overflow does not exceed twice the largest explicit
 * size in the document. Since CSS {@code overflow} defaults to {@code visible}, we cannot require
 * everything to stay inside the paper. STRICT mode only.</li>
 * <li><b>Reading order is preserved</b>: a token earlier in document order does not appear on a later page
 * than a subsequent token. Painting order <b>within</b> a page is an implementation detail and is not checked.
 * Floats legitimately change reading order, so exclude them. STRICT mode only.</li>
 * </ol>
 *
 * <h2>Two modes</h2>
 *
 * <p>
 * <b>STRICT</b> generates only a subset in which "the author cannot intend blank pages or content loss":
 * no absolute positioning, {@code visibility:hidden}, forced page breaks, or {@code overflow:hidden}.
 * Invariants 4 and 5 can also be checked here.
 * </p>
 *
 * <p>
 * <b>WILD</b> includes those features and checks only invariants 1–3
 * (because intended blank pages and intended overflow cannot be distinguished).
 * </p>
 *
 * <h2>Running</h2>
 *
 * <p>
 * The default is {@value #DEFAULT_SEEDS} seeds per mode for regression checks.
 * For sweeps, increase it, for example with {@code -Dfoliojet.fuzzSeeds=2000}
 * (record the run count, since it directly grounds statistical claims).
 * Save HTML for failed seeds in {@code local/fuzz/} for reproduction.
 * </p>
 */
public class RandomDocumentFuzzTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * Version of the generator's input distribution. Record this and the code identifier in the sweep manifest.
	 * To reproduce v1 seeds exactly, check out the old tag recorded in the manifest.
	 */
	static final int GENERATOR_VERSION = 2;

	/** Version of the opt-in sweep profile that adds heavy local structures without changing v2. */
	static final int EXTREME_PROFILE_VERSION = 1;

	private static boolean extremeProfile() {
		final String value = System.getProperty("foliojet.fuzzExtreme");
		return value != null && !value.equals("0") && !value.equalsIgnoreCase("false");
	}

	/**
	 * Version of the opt-in sweep profile that lays out only documents with active off-paper checks
	 * (2026-10-07, {@code -Dfoliojet.fuzzFit=1}).
	 *
	 * <p>
	 * v2 documents are dense; only about 5% avoided document-level exclusions for off-paper checks
	 * (invariant 6), due to small paper, explicit dimensions larger than the paper, nested orthogonal flows, etc.
	 * fit regenerates v2 documents using an ordered sequence of seeds derived from the same seed,
	 * taking the first document without exclusions ({@link #generateFit}). Neither the generator nor
	 * the check predicates change, so false positives do not increase and reproduction from a seed is deterministic.
	 * </p>
	 */
	static final int FIT_PROFILE_VERSION = 1;

	/** fit attempt count. If about 5% of documents qualify, the chance of finding none in 64 attempts is about 4%. */
	private static final int FIT_TRIES = 64;

	private static boolean fitProfile() {
		final String value = System.getProperty("foliojet.fuzzFit");
		return value != null && !value.equals("0") && !value.equalsIgnoreCase("false");
	}

	private static String generatorProfile() {
		if (fitProfile()) {
			if (extremeProfile()) {
				throw new IllegalStateException("foliojet.fuzzFit と foliojet.fuzzExtreme は同時に使えない");
			}
			return "fit-v" + FIT_PROFILE_VERSION;
		}
		return extremeProfile() ? "extreme-v" + EXTREME_PROFILE_VERSION : "standard";
	}

	private static String generatorLabel() {
		return extremeProfile() || fitProfile() ? GENERATOR_VERSION + "/" + generatorProfile()
				: String.valueOf(GENERATOR_VERSION);
	}

	/** Default seed count (regressions; increase sweeps with -Dfoliojet.fuzzSeeds). */
	private static final int DEFAULT_SEEDS = 60;

	/** Time limit per document. Normally finishes in under one second. */
	private static final long WATCHDOG_MS = Long.getLong("foliojet.fuzzWatchdogMs", 30_000L);

	/** Page-count limit. Clearly excessive relative to generated content volume. */
	private static final int MAX_PAGES = Integer.getInteger("foliojet.fuzzMaxPages", 300);

	/** Absolute extreme limit. Normally element count × 2 takes effect first. */
	private static final int EXTREME_MAX_PAGES = Integer.getInteger("foliojet.fuzzExtremeMaxPages", 4_000);

	private static int pageLimit(final Generated doc) {
		// In documents with an unworkable type area (paper too small or explicit dimensions larger than paper),
		// rescue-splitting oversized boxes can create several pages of slices per element. Seed 5729966 (13 pt on 60×60 pt,
		// 218 elements) produced 8 pages from a grid with three rows of min-width:8em lines alone, and finished in 315 pages
		// **in finite time** (2026-09-18). A fixed limit of 300 labels content volume itself as runaway, so
		// use "element count × 3", as with extreme. Real runaways (seed 2264275 produced 27,820 pages)
		// still exceed it as before.
		if (!doc.html().contains("data-fuzz-profile=\"extreme-v") && !doc.beyondEngineControl()) {
			return MAX_PAGES;
		}
		// Seed 541, with 60×60 pt paper and a 40×40 pt content area, successfully generated 1,242 pages
		// from 1,066 elements. A fixed limit of 300 treats extreme's content volume itself as abnormal.
		// Element count does not express long text volume. Vertical-writing seed 8676 at 13 pt terminated normally
		// with 1,443 pages from 655 elements (2.20 pages/element), so allow up to three times the count.
		// Keep the absolute 4,000-page limit too, rather than allowing unbounded growth.
		final int contentBound = Math.multiplyExact(inspectGeneratedStructure(doc.html()).elements(), 3);
		return Math.max(MAX_PAGES, Math.min(EXTREME_MAX_PAGES, contentBound));
	}

	/** Marker for deliberately stopping conversion at the page-count oracle limit. */
	private static final class PageCountLimitExceeded extends AssertionError {
		private static final long serialVersionUID = 1L;

		PageCountLimitExceeded(final int limit, final Throwable cause) {
			super("ページ数が過大 " + (limit + 1) + "以上");
			initCause(cause);
		}
	}

	/**
	 * Extract "content painted as text" from the display list.
	 *
	 * <p>
	 * <b>Ruby uses a separate notation, {@code RubyUnit["親文字" ruby="ふりがな"]}</b>,
	 * so inspecting only {@code Text[...]} falsely reports "loss"
	 * (2026-07-26, discovered when ruby was added to the generator).
	 * This was <b>an oracle error</b>, not an engine error.
	 * Image {@code alt} is not painted, so the generator embeds no tokens in alt.
	 * </p>
	 */
	private static final Pattern TEXT_IN_DUMP = Pattern
			.compile("(?:Text|RubyUnit)\\[\"([^\"]*)\"(?: ruby=\"([^\"]*)\")?");
	/** Markers generated by the generator's ordered lists (not body-text characters). */
	private static final Pattern ORDERED_LIST_MARKER = Pattern
			.compile("(?:[0-9]+|[ivxlcdm]+)\\.|[〇一二三四五六七八九十百千万]+、");
	/** Fixed text painted by generated form controls, not fuzz tokens. */
	private static final Set<String> GENERATED_CONTROL_TEXT = Set.of("x", "y", "mixed", "甲", "乙", "日本語", "العربية",
			"日本語 العربية");

	/** One text run in the display list and its page number (zero-based). */
	private record ObservedText(String text, int page, boolean artifact) {
	}

	/** Bounding rectangle of a drawable with dimensions in the detailed dump. */
	private static final Pattern DRAWING_GEOMETRY_IN_DUMP = Pattern.compile(
			"x=(-?[\\d.]+) y=(-?[\\d.]+) (?:artifact )?[^\\n]*?w=([\\d.]+) h=([\\d.]+)");

	public RandomDocumentFuzzTest(String name) {
		super(name);
	}

	public void testStrictDocumentsPreserveEverything() throws Exception {
		checkFuzzImage();
		sweep(true);
	}

	public void testWildDocumentsNeverCrashOrHang() throws Exception {
		checkFuzzImage();
		sweep(false);
	}

	/** Structures and size vocabulary added in v2 are actually reachable from a fixed seed range. */
	public void testGeneratorV2VocabularyIsReachable() {
		boolean cellChild = false, flex = false, grid = false, intrinsic = false;
		boolean relativeSize = false, complexWild = false, longRuby = false;
		for (int seed = 0; seed < 512; ++seed) {
			final String strictHtml = generate(seed, true).html();
			final String wildHtml = generate(seed, false).html();
			final String html = strictHtml + wildHtml;
			cellChild |= html.contains("data-fuzz-role=\"cell-child\"");
			flex |= html.contains("display:flex") && html.contains("flex-direction:")
					&& html.contains("flex-wrap:") && html.contains("gap:");
			grid |= html.contains("display:grid") && html.contains("grid-template-columns:");
			intrinsic |= html.contains("width:min-content") && html.contains("width:max-content")
					&& html.contains("width:fit-content(");
			relativeSize |= html.contains("min-width:8em") && html.contains("max-width:90%")
					&& html.contains("width:calc(");
			complexWild |= wildHtml.contains("data-fuzz-role=\"wild-complex\"");
			longRuby |= html.contains("class=\"fuzz-long-ruby\"");
		}
		assertTrue("表セル内の再帰的な子へ到達しない", cellChild);
		assertTrue("複数itemのflexへ到達しない", flex);
		assertTrue("複数itemのgridへ到達しない", grid);
		assertTrue("intrinsic size語彙へ到達しない", intrinsic);
		assertTrue("相対・calc size語彙へ到達しない", relativeSize);
		assertTrue("WILDの複雑な部分木へ到達しない", complexWild);
		assertTrue("長いrubyへ到達しない", longRuby);
		assertEquals("同一seedの生成結果が変動する", generate(12345, true), generate(12345, true));
	}

	/** The dense structures targeted by extreme-v1 occur in every seed and are deterministic. */
	public void testExtremeProfileIsDenseAndDeterministic() {
		for (int seed = 0; seed < 32; ++seed) {
			final Generated strict = generate(seed, true, false, true);
			final String html = strict.html();
			assertTrue("extreme表へ到達しない", html.contains("data-fuzz-role=\"extreme-table\""));
			assertTrue("thead/tfoot/caption/colgroupを生成しない", html.contains("<thead>")
					&& html.contains("<tfoot>") && html.contains("<caption>") && html.contains("<colgroup>"));
			assertTrue("高密度flex/gridへ到達しない", html.contains("data-fuzz-role=\"extreme-layout\"")
					&& html.contains("grid-row:") && html.contains("align-items:")
					&& html.contains("justify-content:"));
			assertTrue("複合リスト項目へ到達しない", html.contains("data-fuzz-role=\"extreme-list\""));
			assertTrue("多言語/bidi/長語へ到達しない", html.contains("data-fuzz-role=\"extreme-text\"")
					&& html.contains("dir=\"rtl\"") && html.contains("fuzzUnbreakable"));
			assertTrue("v2標準より十分に密でない: tokens=" + strict.tokens().size(),
					strict.tokens().size() >= 80);
			assertTrue("extremeのページ上限が内容量に比例しない", pageLimit(strict) > MAX_PAGES);
			assertEquals("extreme同一seedの生成結果が変動する", strict,
					generate(seed, true, false, true));
		}
		final String wild = generate(0, false, false, true).html();
		assertTrue("WILDの複雑な強制改ページ境界へ到達しない",
				wild.contains("data-fuzz-role=\"extreme-wild-break\""));
		final Generated denseVertical = generate(8676, true, false, true);
		assertTrue("長文を持つ縦書きextreme文書の正常停止前にページ上限が切れる: "
				+ pageLimit(denseVertical), pageLimit(denseVertical) >= 1_443);
	}

	/** Do not falsely report content loss when overflow-wrap:anywhere or similar rules split tokens internally. */
	public void testContentOracleRestoresSplitTokensAcrossInterleavedDraws() {
		final Set<String> expected = Set.of("T100", "T423", "T575", "T576", "T657", "T57");
		final List<ObservedText> runs = List.of(new ObservedText("prefix T57", 0, false),
				new ObservedText("T100", 0, false), new ObservedText("5", 0, false),
				new ObservedText("T", 1, false), new ObservedText("1.", 2, false),
				new ObservedText("一、", 2, false), new ObservedText("\u200b", 2, false),
				new ObservedText("42", 2, false), new ObservedText("3", 2, false),
				new ObservedText("T57", 3, false), new ObservedText("6", 3, false),
				new ObservedText("T", 4, false), new ObservedText("x\u200b", 5, false),
				new ObservedText("6", 5, false), new ObservedText("x\u200b", 6, true),
				new ObservedText("5", 6, false), new ObservedText("7", 6, false));
		assertEquals(0, firstObservedTokenPage("T575", runs, expected));
		assertEquals(1, firstObservedTokenPage("T423", runs, expected));
		assertEquals(3, firstObservedTokenPage("T576", runs, expected));
		assertEquals(4, firstObservedTokenPage("T657", runs, expected));
		assertEquals(-1, firstObservedTokenPage("T577", runs, expected));
		assertEquals(0, firstObservedTokenPage("T57", runs, expected));
	}

	/** Do not mistake partial matches or matches across ordinary body text for token presence. */
	public void testContentOracleRejectsPrefixAndTextSkipping() {
		final Set<String> expected = Set.of("T9", "T57", "T91", "T575");
		assertEquals(-1, firstObservedTokenPage("T57",
				List.of(new ObservedText("T575", 0, false)), expected));
		assertEquals(-1, firstObservedTokenPage("T575",
				List.of(new ObservedText("T57", 0, false), new ObservedText("ordinary text", 0, false),
						new ObservedText("5", 0, false)), expected));
		assertEquals(1, firstObservedTokenPage("T91",
				List.of(new ObservedText("T9", 0, false), new ObservedText("1.", 0, false),
						new ObservedText("T91", 1, false)), expected));
		assertEquals(-1, firstObservedTokenPage("T501",
				List.of(new ObservedText("T50", 0, true), new ObservedText("T51", 0, false),
						new ObservedText("1", 1, false)), Set.of("T50", "T51", "T501")));
		assertEquals(2, firstObservedTokenPage("T11",
				List.of(new ObservedText("T1", 1, false), new ObservedText("T2", 1, false),
						new ObservedText("1", 1, false), new ObservedText("T11", 2, false)),
				Set.of("T1", "T2", "T11")));
		assertEquals(1, firstObservedTokenPage("T435",
				List.of(new ObservedText("T43", 0, false), new ObservedText("T89", 0, false),
						new ObservedText("T105", 1, false), new ObservedText("T43", 1, false),
						new ObservedText("5", 1, false)),
				Set.of("T43", "T89", "T105", "T435")));
		assertEquals(-1, firstObservedTokenPage("T575",
				List.of(new ObservedText("T", 0, false), new ObservedText("5", 1, false),
						new ObservedText("7", 1, false), new ObservedText("6", 1, false)), expected));
	}

	/** Interrupted checkpoints retain reproduction seeds as well as classification counts. */
	public void testFuzzManifestCheckpointRetainsSeeds() throws Exception {
		final Path manifest = Files.createTempFile("foliojet-fuzz-manifest-", ".json");
		final String previous = System.getProperty("foliojet.fuzzManifest");
		try {
			System.setProperty("foliojet.fuzzManifest", manifest.toString());
			final var classCount = new java.util.concurrent.ConcurrentHashMap<String,
					java.util.concurrent.atomic.AtomicInteger>();
			final var seedsOf = new java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>>();
			final var defectCount = new java.util.concurrent.ConcurrentHashMap<String,
					java.util.concurrent.atomic.AtomicInteger>();
			final var defectSeeds = new java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>>();
			classCount.put("ページ数過大", new java.util.concurrent.atomic.AtomicInteger(2));
			rememberSeed(seedsOf, "ページ数過大", 123);
			rememberSeed(seedsOf, "ページ数過大", 456);
			defectCount.put("normalized|site=x", new java.util.concurrent.atomic.AtomicInteger(1));
			rememberSeed(defectSeeds, "normalized|site=x", 456);

			writeFuzzManifest(false, 100, 200, 2, 17, false,
					classCount, seedsOf, defectCount, defectSeeds);
			final String json = Files.readString(manifest, StandardCharsets.UTF_8);
			assertTrue(json.contains("\"processed\":17"));
			assertTrue(json.contains("\"completed\":false"));
			assertTrue(json.contains("\"generatorProfile\":\"" + generatorProfile() + "\""));
			assertTrue(json.contains("\"ページ数過大\":[123,456]"));
			assertTrue(json.contains("\"normalized|site=x\":[456]"));
		} finally {
			if (previous == null) {
				System.clearProperty("foliojet.fuzzManifest");
			} else {
				System.setProperty("foliojet.fuzzManifest", previous);
			}
			Files.deleteIfExists(manifest);
		}
	}

	/**
	 * Pin down the first eight seeds with "all drawing outside the paper" in the million-case sweep of 2026-08-14.
	 * Allow only normal successful layout or a mechanically bounded dedicated exclusion.
	 */
	public void testStrictHistoricalAllDrawingOffPageSeeds() throws Exception {
		final int[] seeds = { 36607, 82162, 97953, 132786, 139166, 143513, 157106, 175497 };
		final List<String> unexpected = new ArrayList<>();
		for (final int seed : seeds) {
			try {
				checkOneV1(seed, true);
			} catch (final Throwable t) {
				final String kind = classify(t);
				final String expected = switch (seed) {
				case 36607, 82162 -> "(除外)同軸逆進行フローの組版不能幅";
				case 132786, 143513 -> "(除外)組版できない幅の浮動体";
				default -> null;
				};
				if (!kind.equals(expected)) {
					unexpected.add(seed + "=" + kind + ": " + t);
				}
			}
		}
		assertTrue("過去の全描画紙面外シードが未分類のまま残った: " + unexpected, unexpected.isEmpty());
	}

	/**
	 * Pin down the 12 "all drawing outside the paper" cases remaining in the post-fix million-case sweep of 2026-08-15.
	 * Use narrow dedicated exclusions for three cases made untypesettable by author declarations;
	 * require normal completion for the nine implementation defects.
	 */
	public void testStrictHistoricalRemainingAllDrawingOffPageSeeds() throws Exception {
		final int[] seeds = { 266476, 324423, 372387, 591475, 763450, 778506,
				799286, 835421, 848433, 865035, 911787, 951004 };
		final List<String> unexpected = new ArrayList<>();
		for (final int seed : seeds) {
			try {
				checkOneV1(seed, true);
			} catch (final Throwable t) {
				final String kind = classify(t);
				final String expected = switch (seed) {
				case 372387 -> "(除外)直交フローの組版不能幅";
				case 324423, 865035 -> "(除外)組版できない幅の浮動体";
				default -> null;
				};
				if (!kind.equals(expected)) {
					unexpected.add(seed + "=" + kind + ": " + t);
				}
			}
		}
		assertTrue("残存した全描画紙面外シードが未解決のまま残った: " + unexpected,
				unexpected.isEmpty());
	}

	/**
	 * Even on the same vertical axis, a change in progression direction creates an independent BFC containing inner floats.
	 *
	 * <p>
	 * Minimal case for strict seed 97953. Previously, {@code vertical-lr} directly under {@code vertical-rl}
	 * flowed into the same builder, and float text extended outward from exactly the paper's start edge.
	 * Independently of the million-case sweep, pin down comparison of the full writing-mode value,
	 * rather than just vertical versus horizontal axes.
	 * </p>
	 */
	public void testSameAxisWritingModeChangeContainsFloat() throws Exception {
		final String htmlText = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n"
				+ "<?jp.cssj.property name=\"output.page-width\" value=\"120pt\"?>\n"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n"
				+ "<html><head><style>@page{margin:0}body{margin:0;font:normal 12pt/1.2 serif;"
				+ "writing-mode:vertical-rl}</style></head><body>"
				+ "<div style=\"writing-mode:vertical-lr\"><div style=\"float:left\">T4</div></div>"
				+ "</body></html>";
		final Generated doc = new Generated(htmlText, List.of("T4"), Set.of("T4"), 120, 400, 0, false, false);
		final File root = Files.createTempDirectory("same-axis-writing-mode-float-").toFile();
		final File html = new File(root, "input.html");
		final File outDir = new File(root, "display-list");
		try {
			checkDocument(doc, html, outDir, true, "same-axis-writing-mode-float");
		} finally {
			final File[] outputs = outDir.listFiles();
			if (outputs != null) {
				for (final File output : outputs) {
					assertTrue("一時出力を削除できない: " + output, output.delete());
				}
			}
			assertTrue("一時出力ディレクトリを削除できない", !outDir.exists() || outDir.delete());
			assertTrue("一時HTMLを削除できない", !html.exists() || html.delete());
			assertTrue("一時ディレクトリを削除できない", root.delete());
		}
	}

	/**
	 * Do not finalize an empty line when fragmentation leaves only an inline end without a start.
	 *
	 * <p>
	 * Minimal case for extreme-v1 WILD seed 7593. With a multi-column list containing an empty footnote after a table,
	 * {@code drawLine()} mistook an INLINE_END discarded by recovery for content, called {@code align()}
	 * on an empty line box, and failed conversion.
	 * </p>
	 */
	public void testFragmentRecoveryDoesNotAlignEmptyLine() throws Exception {
		final String htmlText = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="60pt"?>
				<?jp.cssj.property name="output.page-height" value="60pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 6pt/1.2 serif}
				table{border-collapse:collapse}
				td{border:1pt solid black}
				</style></head><body>
				<table>
				<thead><th>T99</th></thead>
				<td><div style="display:grid">T113<div></div></div></td><td></td>
				<tfoot><td>T136</td></tfoot>
				</table>
				<ol><li><div style="column-count:2"><div style="float:footnote"></div></div></li></ol>
				</body></html>
				""";
		final Generated doc = new Generated(htmlText, List.of(), Set.of(), 60, 60, 0, false, true);
		final File root = Files.createTempDirectory("fragment-empty-line-").toFile();
		final File html = new File(root, "input.html");
		final File outDir = new File(root, "display-list");
		try {
			checkDocument(doc, html, outDir, false, "fragment-empty-line");
		} finally {
			final File[] outputs = outDir.listFiles();
			if (outputs != null) {
				for (final File output : outputs) {
					assertTrue("一時出力を削除できない: " + output, output.delete());
				}
			}
			assertTrue("一時出力ディレクトリを削除できない", !outDir.exists() || outDir.delete());
			assertTrue("一時HTMLを削除できない", !html.exists() || html.delete());
			assertTrue("一時ディレクトリを削除できない", root.delete());
		}
	}

	/**
	 * Pin down seed 78906, the only blank-page case in the million-case sweep of 2026-08-14.
	 *
	 * <p>
	 * Until 2026-08-21, this was "(excluded) float with an untypesettable width". Clamping END-side floats
	 * to the line start (preventing floats wider than the band from extending before the line start,
	 * outside the paper; BlockBuilder.tryFloatPlacement) made it **lay out normally**.
	 * Eliminating the exclusion is an improvement from clamping, so pin down the new behavior
	 * (success in both modes).
	 * </p>
	 */
	public void testStrictHistoricalBlankPageSeed() throws Exception {
		checkOneV1(78906, true);
		checkOneV1(78906, false);
	}

	/**
	 * 2026-09-18, the first stop in the restarted sweep (seeds 5,250,000 onward). Slices of boxes that did not fit
	 * in an unworkable type area (13 pt on 60×60 pt) produced 315 pages and hit the fixed limit of 300.
	 * With the limit changed to element count × 3, this becomes the "off-paper placement in a document with
	 * an unworkable type area" exclusion (pinning down finite termination).
	 */
	public void testStrictBrokenLayoutDocumentFinishesWithinContentBound() throws Exception {
		try {
			checkOne(5729966, true);
		} catch (final Throwable t) {
			assertEquals(String.valueOf(t), "(除外)版面が破綻した文書の紙面外配置", classify(t));
		}
	}

	/** Pin down the seven "off-paper placement" seeds from the million-case sweep of 2026-08-14. */
	public void testStrictHistoricalOffPagePlacementSeeds() throws Exception {
		final int[] seeds = { 321621, 473636, 473924, 526411, 651439, 776967, 867178 };
		final List<String> unexpected = new ArrayList<>();
		for (final int seed : seeds) {
			try {
				checkOneV1(seed, true);
			} catch (final Throwable t) {
				final String kind = classify(t);
				final String expected = switch (seed) {
				case 473924 -> "(除外)flex内の段組表によるmin-content幅";
				case 776967 -> "(除外)同軸逆進行フローの組版不能幅";
				default -> null;
				};
				if (!kind.equals(expected)) {
					unexpected.add(seed + "=" + kind + ": " + t);
				}
			}
		}
		assertTrue("過去の紙面外配置シードが未解決のまま残った: " + unexpected, unexpected.isEmpty());
	}

	/**
	 * Verify that an image substituted with {@code -Dfoliojet.fuzzImage} has <b>the same dimensions</b>
	 * as the default image (2026-08-02).
	 *
	 * <p>
	 * This setting relocates an image for faster I/O; it assumes <b>only the location of the same image changes</b>.
	 * Pointing to an image with different dimensions changes the layout and silently changes the document
	 * identified by a seed. That prevents comparison with past sweep results, so fail rather than continue.
	 * </p>
	 */
	private static void checkFuzzImage() throws Exception {
		final String path = System.getProperty("foliojet.fuzzImage");
		if (path == null) {
			return;
		}
		final int[] replaced = pngSize(new File(path));
		final int[] original = pngSize(new File(DEFAULT_FUZZ_IMAGE));
		if (replaced == null || original == null) {
			return;
		}
		if (replaced[0] != original[0] || replaced[1] != original[1]) {
			fail("-Dfoliojet.fuzzImage の画像は既定と同じ寸法でなければならない"
					+ "(シードが指す文書が変わってしまう): " + path + " は "
					+ replaced[0] + "x" + replaced[1] + "、既定の " + DEFAULT_FUZZ_IMAGE + " は "
					+ original[0] + "x" + original[1]);
		}
	}

	/** Read dimensions from PNG IHDR (null if unreadable). */
	private static int[] pngSize(final File file) throws Exception {
		if (!file.isFile()) {
			return null;
		}
		final byte[] head = new byte[24];
		try (java.io.InputStream in = new java.io.FileInputStream(file)) {
			if (in.readNBytes(head, 0, head.length) != head.length) {
				return null;
			}
		}
		// 8-byte signature + 4-byte length + "IHDR" + 4-byte width + 4-byte height.
		if (head[0] != (byte) 0x89 || head[12] != 'I' || head[13] != 'H' || head[14] != 'D'
				|| head[15] != 'R') {
			return null;
		}
		return new int[] { readInt(head, 16), readInt(head, 20) };
	}

	private static int readInt(final byte[] b, final int offset) {
		return ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16) | ((b[offset + 2] & 0xFF) << 8)
				| (b[offset + 3] & 0xFF);
	}

	/**
	 * Starting seed for the sweep ({@code -Dfoliojet.fuzzFrom}).
	 *
	 * <p>
	 * <b>The 30 million documents for the 100-year target cannot run in one batch</b>
	 * (60 million documents ≈ 28 hours). The generator is deterministic, so 30 batches of one million
	 * cover the same document set as a continuous run. Results up to a crash can still be accumulated.
	 * </p>
	 */
	private static int seedFrom() {
		final String v = System.getProperty("foliojet.fuzzFrom");
		return v == null ? 0 : Integer.parseInt(v);
	}

	/** Progress reporting interval. 3,000 lines for 30 million cases; the last reached point survives a crash. */
	private static final int PROGRESS_EVERY = 10_000;

	private static int seedCount() {
		final String v = System.getProperty("foliojet.fuzzSeeds");
		return v == null ? DEFAULT_SEEDS : Integer.parseInt(v);
	}

	/**
	 * <b>Known unresolved</b>: seeds producing one extra trailing empty page.
	 *
	 * <p>
	 * <b>Emptied on 2026-07-26 because these were resolved within the default seed range.</b>
	 * The cause was {@code BreakableBuilder.classifyFloatPlacement} failing to distinguish
	 * "only the box overflows" from "there is something to paint beyond the page".
	 * Adding that distinction ({@code paintsNothingBeyondPage}) reduced occurrences in 6,000 seeds
	 * from <b>88 to 47</b>, and to zero in the default 60 seeds.
	 * </p>
	 *
	 * <p>
	 * <b>The remaining 47 cases have a different mechanism</b> and have not yet been reduced.
	 * They lie outside the default seed range, so regressions stay green; only a sweep
	 * ({@code -Dfoliojet.fuzzReport=1 -Dfoliojet.fuzzSeeds=6000}) reveals them.
	 * **Do not casually add to this set**: doing so declares "we have decided we cannot fix this"
	 * and permanently hides it from default regression checks.
	 * </p>
	 */
	private static final java.util.Set<Integer> KNOWN_TRAILING_BLANK_PAGE = java.util.Set.of();

	/**
	 * <b>Known unresolved</b>: seeds whose conversions end with exceptions
	 * (2026-07-26, found in a sweep with expanded vocabulary). Two types of invariant violation:
	 *
	 * <ul>
	 * <li>Crossing a block boundary <b>with textBuilder still open</b>
	 * ({@code BlockBuilder.requireNoOpenTextBuilder}). One in 400 strict documents.</li>
	 * <li><b>flowStack depth differs from continuation depth</b>
	 * ("break flow failed" in {@code RootBuilder.pageBreak}). One in 1,000 strict documents.</li>
	 * </ul>
	 *
	 * <p>
	 * <b>Both already fail closed</b> (confirmed 2026-07-26). These throw
	 * {@code ContinuationInvariantViolationException}, not assertions, so <b>conversion also fails
	 * in production</b>, rather than silently producing broken output.
	 * Confirmed by unchanged counts in sweeps with {@code -PnoAssertions}.
	 * </p>
	 *
	 * <p>
	 * Before failing closed, content actually disappeared (an entire {@code column-count:3} block
	 * was lost in seed 890). Both involve the core page-break/continuation mechanism,
	 * so identify the cause before fixing them.
	 * </p>
	 */
	private static final java.util.Set<Integer> KNOWN_INVARIANT_VIOLATION = java.util.Set.of();

	/**
	 * Statistical aggregation mode ({@code -Dfoliojet.fuzzReport}). Run all seeds without early termination
	 * and output <b>counts and first seeds per failure type</b>. This supplies estimates for
	 * "how many remain" and "how many runs until the next failure".
	 */
	/**
	 * Count of threads surviving the watchdog. <b>Count them because they cannot be stopped.</b>
	 */
	private static final java.util.concurrent.atomic.AtomicInteger LEAKED_WORKERS =
			new java.util.concurrent.atomic.AtomicInteger();

	/**
	 * Stop the entire sweep above this limit. One leaked thread retains one layout's heap and a 64 MB
	 * stack reservation, so just a few alter measurement conditions.
	 */
	private static final int MAX_LEAKED_WORKERS = 4;

	/**
	 * Scale-proportional leaked-worker limit (2026-07-28).
	 *
	 * <p>
	 * A rate of four per 20,000 will <b>inevitably be hit probabilistically</b> at 30 million.
	 * Do not remove the limit: each leaked thread retains one layout's heap and a 64 MB stack reservation,
	 * so leaving them unchecked causes self-amplification (§10.3).
	 * Keep the stop mechanism; only scale the threshold with volume (one per 100,000 cases).
	 * </p>
	 */
	private static int maxLeakedWorkers() {
		return Math.max(MAX_LEAKED_WORKERS, seedCount() / 100_000);
	}

	private static boolean reportMode() {
		return System.getProperty("foliojet.fuzzReport") != null;
	}

	/**
	 * Shared feature vocabulary for existing document-level and local coverage.
	 * Order also determines bit numbers, so do not reorder existing entries or insert between them.
	 */
	private static final List<String> LOCAL_FEATURES = List.of("display:flex", "display:grid",
			"display:inline-block", "display:list-item", "display:table", "display:none", "position:absolute",
			"position:relative", "float:left", "float:right", "float:footnote", "float:top", "float:bottom",
			"writing-mode:vertical", "overflow:hidden", "<table", "<ul", "<ol", "<ruby", "<img", "<form",
			"<input", "<select", "<textarea", "<button", "page-break-before", "page-break-inside",
			"list-style-type", "clear:", "column-count:");

	private enum LocalScope {
		SAME_BOX("same-box"),
		PARENT_CHILD("parent-child"),
		ANCESTOR_2("ancestor-distance-2"),
		SIBLING("sibling");

		final String label;

		LocalScope(final String label) {
			this.label = label;
		}
	}

	private record CoverageKey(LocalScope scope, long features) {
		int t() {
			return Long.bitCount(this.features);
		}
	}

	private static final class StructureNode {
		final long features;
		final List<StructureNode> children = new ArrayList<>();

		StructureNode(final long features) {
			this.features = features;
		}
	}

	private record GenerationStructure(StructureNode body, int elements) {
		java.util.Set<CoverageKey> coverageKeys() {
			final java.util.Set<CoverageKey> keys = new java.util.HashSet<>();
			collectCoverage(this.body, null, null, keys);
			return keys;
		}

		String topologyHash() {
			final List<String> topology = new ArrayList<>();
			collectTopology(this.body, null, null, topology);
			java.util.Collections.sort(topology);
			final MessageDigest digest = sha256();
			for (final String relation : topology) {
				digest.update(relation.getBytes(StandardCharsets.UTF_8));
				digest.update((byte) '\n');
			}
			return java.util.HexFormat.of().formatHex(digest.digest(), 0, 8);
		}
	}

	private static final ThreadLocal<javax.xml.stream.XMLInputFactory> STRUCTURE_XML =
			ThreadLocal.withInitial(() -> {
				final javax.xml.stream.XMLInputFactory factory =
						javax.xml.stream.XMLInputFactory.newFactory();
				factory.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
				factory.setProperty(javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
						Boolean.FALSE);
				return factory;
			});

	/**
	 * Scan generated HTML as XML events. Use actual parent-child and sibling element relationships,
	 * not regular-expression approximations of the HTML string.
	 */
	private static GenerationStructure inspectGeneratedStructure(final String html) {
		final int firstLf = html.indexOf('\n');
		final String xml = html.startsWith("<!DOCTYPE") && firstLf >= 0
				? html.substring(firstLf + 1) : html;
		final java.util.ArrayDeque<StructureNode> stack = new java.util.ArrayDeque<>();
		StructureNode body = null;
		int elements = 0;
		boolean inBody = false;
		javax.xml.stream.XMLStreamReader reader = null;
		try {
			reader = STRUCTURE_XML.get().createXMLStreamReader(new StringReader(xml));
			while (reader.hasNext()) {
				final int event = reader.next();
				if (event == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
					final String tag = reader.getLocalName().toLowerCase(java.util.Locale.ROOT);
					if ("body".equals(tag)) {
						body = new StructureNode(elementFeatures(tag,
								reader.getAttributeValue(null, "style")));
						stack.push(body);
						inBody = true;
						++elements;
					} else if (inBody) {
						final StructureNode node = new StructureNode(elementFeatures(tag,
								reader.getAttributeValue(null, "style")));
						stack.peek().children.add(node);
						stack.push(node);
						++elements;
					}
				} else if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT && inBody) {
					final String tag = reader.getLocalName().toLowerCase(java.util.Locale.ROOT);
					stack.pop();
					if ("body".equals(tag)) {
						inBody = false;
					}
				}
			}
		} catch (final javax.xml.stream.XMLStreamException e) {
			throw new IllegalStateException("生成HTMLの構造走査に失敗した", e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (final javax.xml.stream.XMLStreamException ignore) {
					// Nothing to release for a StringReader.
				}
			}
		}
		if (body == null) {
			throw new IllegalStateException("生成HTMLにbody要素がない");
		}
		return new GenerationStructure(body, elements);
	}

	private static long elementFeatures(final String tag, final String style) {
		long mask = switch (tag) {
		case "table" -> feature("<table");
		case "ul" -> feature("<ul");
		case "ol" -> feature("<ol");
		case "ruby" -> feature("<ruby");
		case "img" -> feature("<img");
		case "form" -> feature("<form");
		case "input" -> feature("<input");
		case "select" -> feature("<select");
		case "textarea" -> feature("<textarea");
		case "button" -> feature("<button");
		default -> 0;
		};
		if (style == null) {
			return mask;
		}
		for (int start = 0; start < style.length();) {
			int end = style.indexOf(';', start);
			if (end < 0) {
				end = style.length();
			}
			final String declaration = style.substring(start, end).trim();
			final int colon = declaration.indexOf(':');
			if (colon > 0) {
				final String property = declaration.substring(0, colon).trim();
				final String value = declaration.substring(colon + 1).trim();
				mask |= switch (property) {
				case "display" -> optionalFeature("display:" + value);
				case "position" -> optionalFeature("position:" + value);
				case "float" -> optionalFeature("float:" + value);
				case "writing-mode" -> value.startsWith("vertical")
						? feature("writing-mode:vertical") : 0;
				case "overflow" -> "hidden".equals(value) ? feature("overflow:hidden") : 0;
				case "page-break-before" -> feature("page-break-before");
				case "page-break-inside" -> feature("page-break-inside");
				case "list-style-type" -> feature("list-style-type");
				case "clear" -> feature("clear:");
				case "column-count" -> feature("column-count:");
				default -> 0;
				};
			}
			start = end + 1;
		}
		return mask;
	}

	private static long optionalFeature(final String name) {
		final int index = LOCAL_FEATURES.indexOf(name);
		return index < 0 ? 0 : 1L << index;
	}

	private static long feature(final String name) {
		final int index = LOCAL_FEATURES.indexOf(name);
		if (index < 0) {
			throw new IllegalArgumentException("未知の局所被覆機能: " + name);
		}
		return 1L << index;
	}

	private static void collectCoverage(final StructureNode node, final StructureNode parent,
			final StructureNode grandparent, final java.util.Set<CoverageKey> keys) {
		addCoverageCombos(keys, LocalScope.SAME_BOX, node.features);
		if (parent != null) {
			addCoverageCombos(keys, LocalScope.PARENT_CHILD, parent.features | node.features);
		}
		if (grandparent != null) {
			addCoverageCombos(keys, LocalScope.ANCESTOR_2, grandparent.features | node.features);
		}
		for (int i = 0; i < node.children.size(); ++i) {
			for (int j = i + 1; j < node.children.size(); ++j) {
				addCoverageCombos(keys, LocalScope.SIBLING,
						node.children.get(i).features | node.children.get(j).features);
			}
		}
		for (final StructureNode child : node.children) {
			collectCoverage(child, node, parent, keys);
		}
	}

	private static void collectTopology(final StructureNode node, final StructureNode parent,
			final StructureNode grandparent, final List<String> topology) {
		if (node.features != 0) {
			topology.add("B:" + Long.toUnsignedString(node.features, 16));
		}
		if (parent != null && (parent.features | node.features) != 0) {
			topology.add("P:" + Long.toUnsignedString(parent.features, 16) + ">"
					+ Long.toUnsignedString(node.features, 16));
		}
		if (grandparent != null && (grandparent.features | node.features) != 0) {
			topology.add("A2:" + Long.toUnsignedString(grandparent.features, 16) + ">"
					+ Long.toUnsignedString(node.features, 16));
		}
		for (int i = 0; i < node.children.size(); ++i) {
			for (int j = i + 1; j < node.children.size(); ++j) {
				final long a = node.children.get(i).features;
				final long b = node.children.get(j).features;
				if ((a | b) != 0) {
					topology.add("S:" + Long.toUnsignedString(Math.min(a, b), 16) + ","
							+ Long.toUnsignedString(Math.max(a, b), 16));
				}
			}
		}
		for (final StructureNode child : node.children) {
			collectTopology(child, node, parent, topology);
		}
	}

	private static void addCoverageCombos(final java.util.Set<CoverageKey> keys,
			final LocalScope scope, final long presentMask) {
		final int count = Long.bitCount(presentMask);
		final int[] present = new int[count];
		int at = 0;
		for (int bit = 0; bit < LOCAL_FEATURES.size(); ++bit) {
			if ((presentMask & (1L << bit)) != 0) {
				present[at++] = bit;
			}
		}
		for (int t = 2; t <= 5 && t <= count; ++t) {
			final int[] index = new int[t];
			for (int i = 0; i < t; ++i) {
				index[i] = i;
			}
			while (true) {
				long combination = 0;
				for (int i = 0; i < t; ++i) {
					combination |= 1L << present[index[i]];
				}
				keys.add(new CoverageKey(scope, combination));
				int i = t - 1;
				while (i >= 0 && index[i] == count - t + i) {
					--i;
				}
				if (i < 0) {
					break;
				}
				++index[i];
				for (int j = i + 1; j < t; ++j) {
					index[j] = index[j - 1] + 1;
				}
			}
		}
	}

	private static java.util.Set<Long> orProduct(final java.util.Set<Long> left,
			final long... right) {
		final java.util.Set<Long> product = new java.util.HashSet<>();
		for (final long a : left) {
			for (final long b : right) {
				product.add(a | b);
			}
		}
		return product;
	}

	/**
	 * Derive the reachable denominator from current generation-schema choices, not samples.
	 * A mismatch with observation produces a nonzero outsideSchema in the report.
	 */
	private static java.util.Set<CoverageKey> reachableLocalCombos(final boolean strict) {
		java.util.Set<Long> layout = java.util.Set.of(0L);
		layout = orProduct(layout, 0, feature("display:flex"), feature("display:grid"),
				feature("display:inline-block"), feature("display:list-item"), feature("display:table"),
				strict ? 0 : feature("display:none"));
		layout = orProduct(layout, 0, feature("position:relative"),
				strict ? 0 : feature("position:absolute"));
		layout = orProduct(layout, 0, feature("float:left"), feature("float:right"),
				strict ? 0 : feature("float:footnote"),
				strict ? 0 : feature("float:top"),
				strict ? 0 : feature("float:bottom"));
		layout = orProduct(layout, 0, feature("writing-mode:vertical"));
		if (!strict) {
			layout = orProduct(layout, 0, feature("overflow:hidden"));
		}

		final java.util.Set<Long> plain = new java.util.HashSet<>();
		plain.add(0L);
		plain.add(feature("float:left"));
		plain.add(feature("float:right"));
		plain.add(feature("<table"));
		plain.add(feature("column-count:"));
		plain.add(feature("writing-mode:vertical"));
		plain.add(feature("<ul") | feature("list-style-type"));
		plain.add(feature("<ol") | feature("list-style-type"));
		plain.add(feature("clear:"));
		plain.add(feature("page-break-inside"));
		plain.add(feature("<form"));
		if (!strict) {
			plain.add(feature("position:absolute"));
			plain.add(feature("page-break-before") | feature("overflow:hidden"));
		}

		final java.util.Set<Long> roots = new java.util.HashSet<>(plain);
		roots.addAll(layout);
		final java.util.Set<Long> controls = java.util.Set.of(feature("<input"), feature("<select"),
				feature("<textarea"), feature("<button"));
		final java.util.Set<Long> fixedChildren = new java.util.HashSet<>(controls);
		fixedChildren.add(feature("display:inline-block"));
		fixedChildren.add(feature("<ruby"));
		fixedChildren.add(feature("<img"));
		fixedChildren.add(0L);

		final java.util.Set<Long> recursiveParents = java.util.Set.of(0L, feature("float:left"),
				feature("float:right"), feature("column-count:"), feature("writing-mode:vertical"),
				feature("page-break-inside"));
		final java.util.Set<Long> bodyMasks =
				java.util.Set.of(0L, feature("writing-mode:vertical"));

		final java.util.Set<CoverageKey> reachable = new java.util.HashSet<>();
		final java.util.Set<Long> nodeMasks = new java.util.HashSet<>(roots);
		nodeMasks.addAll(fixedChildren);
		for (final long mask : nodeMasks) {
			addCoverageCombos(reachable, LocalScope.SAME_BOX, mask);
		}

		final java.util.Set<Long> ordinaryParents = new java.util.HashSet<>(recursiveParents);
		ordinaryParents.addAll(bodyMasks);
		for (final long parent : ordinaryParents) {
			for (final long child : roots) {
				addCoverageCombos(reachable, LocalScope.PARENT_CHILD, parent | child);
			}
		}
		for (final long wrapper : layout) {
			for (final long child : plain) {
				addCoverageCombos(reachable, LocalScope.PARENT_CHILD, wrapper | child);
			}
		}
		for (final long list : java.util.Set.of(feature("<ul") | feature("list-style-type"),
				feature("<ol") | feature("list-style-type"))) {
			addCoverageCombos(reachable, LocalScope.PARENT_CHILD, list);
		}
		addCoverageCombos(reachable, LocalScope.PARENT_CHILD,
				feature("<form") | feature("<input"));
		addCoverageCombos(reachable, LocalScope.PARENT_CHILD,
				feature("<form") | feature("<select"));
		addCoverageCombos(reachable, LocalScope.PARENT_CHILD,
				feature("<form") | feature("<textarea"));
		addCoverageCombos(reachable, LocalScope.PARENT_CHILD,
				feature("<form") | feature("<button"));

		final java.util.Set<Long> possibleGrandparents = new java.util.HashSet<>(ordinaryParents);
		possibleGrandparents.addAll(layout);
		for (final long grandparent : possibleGrandparents) {
			for (final long descendant : roots) {
				addCoverageCombos(reachable, LocalScope.ANCESTOR_2,
						grandparent | descendant);
			}
		}
		for (final long wrapper : layout) {
			for (final long descendant : fixedChildren) {
				addCoverageCombos(reachable, LocalScope.ANCESTOR_2,
						wrapper | descendant);
			}
		}

		for (final long a : roots) {
			for (final long b : roots) {
				addCoverageCombos(reachable, LocalScope.SIBLING, a | b);
			}
		}
		for (final long a : controls) {
			for (final long b : controls) {
				addCoverageCombos(reachable, LocalScope.SIBLING, a | b);
			}
		}
		return reachable;
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (final java.security.NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	private static final class Distribution {
		private final java.util.concurrent.ConcurrentSkipListMap<Integer,
				java.util.concurrent.atomic.LongAdder> histogram =
						new java.util.concurrent.ConcurrentSkipListMap<>();
		private final java.util.concurrent.atomic.LongAdder count =
				new java.util.concurrent.atomic.LongAdder();
		private final java.util.concurrent.atomic.LongAdder sum =
				new java.util.concurrent.atomic.LongAdder();

		void add(final int value) {
			this.histogram.computeIfAbsent(value,
					x -> new java.util.concurrent.atomic.LongAdder()).increment();
			this.count.increment();
			this.sum.add(value);
		}

		String summary() {
			final long n = this.count.sum();
			if (n == 0) {
				return "n=0";
			}
			return "n=" + n + " min=" + this.histogram.firstKey()
					+ " p50=" + quantile(0.50, n)
					+ " p95=" + quantile(0.95, n)
					+ " p99=" + quantile(0.99, n)
					+ " max=" + this.histogram.lastKey()
					+ " mean=" + String.format(java.util.Locale.ROOT, "%.2f",
							this.sum.sum() / (double) n);
		}

		private int quantile(final double q, final long n) {
			final long target = Math.max(1, (long) Math.ceil(q * n));
			long cumulative = 0;
			for (final var entry : this.histogram.entrySet()) {
				cumulative += entry.getValue().sum();
				if (cumulative >= target) {
					return entry.getKey();
				}
			}
			return this.histogram.lastKey();
		}
	}

	private static final class SweepMeasurements {
		final Distribution elements = new Distribution();
		final Distribution pages = new Distribution();
		final java.util.concurrent.ConcurrentHashMap<CoverageKey,
				java.util.concurrent.atomic.LongAdder> coverage =
						new java.util.concurrent.ConcurrentHashMap<>();

		/**
		 * Count documents with active off-paper checks (invariant 6) (2026-10-07). Exclusion predicates apply per document,
		 * so count from HTML before conversion. Both axes = no exclusion applies; page axis = only the orthogonal-flow
		 * inline-axis exclusion applies (still check page-axis overflow).
		 */
		final java.util.concurrent.atomic.LongAdder offPageDocs = new java.util.concurrent.atomic.LongAdder();
		final java.util.concurrent.atomic.LongAdder offPageLive = new java.util.concurrent.atomic.LongAdder();
		final java.util.concurrent.atomic.LongAdder offPagePageAxisLive = new java.util.concurrent.atomic.LongAdder();

		void recordOffPageLiveness(final Generated doc) {
			this.offPageDocs.increment();
			if (!offPageCheckExcused(doc)) {
				this.offPagePageAxisLive.increment();
				if (!hasOrthogonalFlow(doc.html())) {
					this.offPageLive.increment();
				}
			}
		}

		String offPageSummary() {
			final long n = this.offPageDocs.sum();
			return "両軸 " + this.offPageLive.sum() + "/" + n + " ページ軸 " + this.offPagePageAxisLive.sum() + "/" + n
					+ (n == 0 ? "" : String.format(" (%.1f%%・%.1f%%)", 100.0 * this.offPageLive.sum() / n,
							100.0 * this.offPagePageAxisLive.sum() / n));
		}

		void record(final GenerationStructure structure) {
			this.elements.add(structure.elements());
			for (final CoverageKey key : structure.coverageKeys()) {
				this.coverage.computeIfAbsent(key,
						x -> new java.util.concurrent.atomic.LongAdder()).increment();
			}
		}
	}

	/**
	 * Marker for blank pages in a <b>document containing boxes that cannot fit on the paper</b>
	 * (added 2026-07-26). Treat as an <b>exclusion</b>, not a failure.
	 *
	 * <p>
	 * User decision on 2026-07-26: "Rare cases that cannot occur without deliberate action
	 * may be left to the designer's responsibility." A document placing an indivisible box
	 * larger than the paper has an unworkable layout regardless of what the engine does:
	 * allow overflow or move it to the next page.
	 * </p>
	 *
	 * <p>
	 * <b>Use an exception so aggregation mode can count these cases.</b> Simply skipping checks
	 * hides growth in exclusions. More exclusions mean either "the generator changed" or
	 * "a real regression slipped into exclusions"; neither should go unnoticed.
	 * </p>
	 */
	private static final class ExcludedByOversizedBox extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByOversizedBox(final String message) {
			super(message);
		}
	}

	/**
	 * Marker for <b>an orthogonal flow overflowing the parent's inline axis</b> (added 2026-07-28).
	 * Treat as an <b>exclusion</b>, not a failure.
	 *
	 * <p>
	 * Horizontal writing inside vertical writing (or vice versa) is <b>atomic</b> under the page-break
	 * contract of 2026-07-22 ({@code ContinuationCapability.ORTHOGONAL_FLOW},
	 * {@code supportsPageSplitThrough} is {@code false}). If that box exceeds the paper along
	 * the parent's <b>inline axis</b>, the engine has no remedy: page breaks advance along
	 * the <b>page axis</b>, and a new sheet adds no inline-axis space
	 * (measured: moving it to page 3 still left {@code y=0.00→100.80}, without even a 1 pt change).
	 * </p>
	 *
	 * <p>
	 * <b>The CSS standard attempts to solve this with "automatic multi-column layout"</b>
	 * (css-writing-modes-4 §7.3 auto-multicol: wrap overflowing content into columns along the containing
	 * block's flow direction, avoiding T-shaped documents). However, the specification itself marks this
	 * <b>at-risk</b> (subject to removal during CR) and states that "this requirement automatically creates
	 * a multi-column flow in <b>every block container</b>." Blink <b>abandoned</b> its old slicing behavior
	 * in favor of allowing atomic content to overflow. WPT also has no test requiring
	 * "fragmentation of a long orthogonal flow" (corpus inspected on 2026-07-28).
	 * </p>
	 *
	 * <p>
	 * Thus <b>allowing overflow matches real browsers</b> and is the layout author's responsibility
	 * (user decision on 2026-07-28). This follows ARCHITECTURE.md §5.13,
	 * "Specification (= responsibility of the layout author)".
	 * </p>
	 */
	private static final class ExcludedByOrthogonalLineAxis extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByOrthogonalLineAxis(final String message) {
			super(message);
		}
	}

	/**
	 * Marker for blank pages or off-paper placement from <b>a float or containing block with
	 * an untypesettable width</b> (added 2026-07-29). Treat as an <b>exclusion</b>, not a failure.
	 *
	 * <p>
	 * {@link #hasUntypesettableFloat} checks for a float with an explicit dimension below eight times
	 * the base font size (= about eight characters), a float within an ancestor below that same limit,
	 * or a left/right float wider than its parent/column. If the area is too narrow for layout,
	 * its content must overflow, which under CSS {@code overflow:visible} is <b>correct behavior</b>.
	 * Apply the same criterion as {@code isTinyPage} (paper too small for layout) to an area.
	 * </p>
	 */
	private static final class ExcludedByUntypesettableFloat extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByUntypesettableFloat(final String message) {
			super(message);
		}
	}

	/**
	 * Marker for off-paper placement in a document with <b>three or more nested orthogonal-flow levels</b>
	 * (added 2026-07-30). Treat as an <b>exclusion</b>, not a failure.
	 *
	 * <p>
	 * User decision on 2026-07-30 (seed 5448946). When axes switch twice or more, for example
	 * {@code body} is {@code vertical-rl}, with {@code horizontal-tb} inside and then
	 * {@code vertical-lr} further inside, content extends outside the paper and <b>not a single character
	 * is visible</b>. Measured on 60×60 pt paper:
	 * </p>
	 *
	 * <pre>
	 * T0 x= 60.50   T1 x= 93.66   T3 x=109.90   T4 x=126.14   (output: 1 page)
	 * </pre>
	 *
	 * <p>
	 * <b>This is not a coordinate-transform error.</b> The block-start edge of {@code vertical-rl}
	 * is the paper's <b>right edge</b>. Reversing the block direction to {@code +x} inside it
	 * advances outward from that edge, putting the start at {@code paper width+0.5pt}.
	 * This directly composes the direction reversals; changing paper width changes the offset proportionally
	 * ({@code x=200.5} at 200 pt width).
	 * </p>
	 *
	 * <p>
	 * <b>No correct result can be defined.</b> Available block size for orthogonal flows is ambiguous
	 * even in css-writing-modes-4 §7.3 and differs across real browsers. With three levels,
	 * "where to break pages" is undefined. This occurs once per four million documents,
	 * in a structure absent from real forms or books.
	 * </p>
	 *
	 * <p>
	 * <b>Keep the exclusion narrow.</b> Require {@link #orthogonalAxisChanges} of at least two,
	 * checking only that the axes switch twice. Broadening it to "contains an orthogonal flow"
	 * would miss real off-paper bugs in ordinary vertical-writing documents.
	 * </p>
	 */
	private static final class ExcludedByNestedOrthogonalFlow extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByNestedOrthogonalFlow(final String message) {
			super(message);
		}
	}

	/**
	 * Marker for off-paper placement in a document that <b>reverses page progression in a vertical flow
	 * with an untypesettable width</b> (added 2026-08-15).
	 * Treat as an <b>exclusion</b>, not a failure.
	 *
	 * <p>
	 * Seed 36607 places a 48 pt wide {@code vertical-lr} inside a {@code vertical-rl} parent,
	 * then an 86 pt wide child box and a further 125 pt wide box inside it. The reduced minimal case
	 * places {@code writing-mode:vertical-lr;width:0pt} inside a {@code vertical-rl} parent,
	 * then makes its child {@code vertical-rl} again. The zero-width edge lies at the paper's right edge,
	 * where page progression reverses to {@code +x}, so the child extends off-paper from that edge.
	 * Chrome produces the same placement; this follows from the explicit zero width and
	 * {@code overflow:visible}, not a coordinate-transform defect.
	 * The minimal case for seed 82162 packs a {@code list-item} and a table into a reversed flow
	 * 1 pt wide with 10 pt text.
	 * </p>
	 *
	 * <p>
	 * <b>Limit the exclusion to this shape.</b> {@link #hasUntypesettableOppositeProgression}
	 * is true only when a parent-child pair on the same vertical axis reverses
	 * {@code vertical-rl}/{@code vertical-lr}, and the reversed element's width is less than eight
	 * base-font characters, or an explicitly wider descendant exists.
	 * Do not exclude mere same-axis reversal, typesettable widths, orthogonal flows,
	 * or {@code height:0} on another axis.
	 * </p>
	 */
	private static final class ExcludedByUntypesettableOppositeProgression extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByUntypesettableOppositeProgression(final String message) {
			super(message);
		}
	}

	/** Untypesettable input explicitly giving an axis-changing child a width below the base character advance. */
	private static final class ExcludedByUntypesettableOrthogonalFlow extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByUntypesettableOrthogonalFlow(final String message) {
			super(message);
		}
	}

	/**
	 * Off-paper placement where a multi-column table's min-content width affects a flex item's automatic minimum width.
	 *
	 * <p>
	 * CSS Flexbox §4.5 defines the automatic minimum width of a non-scrollable flex item as content-based minimum.
	 * The minimal case for seed 473924 puts three columns and a table inside one flex item.
	 * Reserving the table's min-content width for each column exceeds the type area.
	 * This follows from author CSS omitting {@code min-width:0}, so use a dedicated exclusion.
	 * Limit the predicate to actual ancestry checked by {@link #hasFlexMulticolTable}.
	 * </p>
	 */
	private static final class ExcludedByFlexMulticolMinContent extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByFlexMulticolMinContent(final String message) {
			super(message);
		}
	}

	/**
	 * Off-paper placement in documents containing <b>content that physically cannot fit</b> in the type area
	 * (added 2026-09-17).
	 *
	 * <p>
	 * After fixing all engine defects in the 36M sweep (seeds 2,000,000–5,249,999), the 31 remaining off-paper cases
	 * reduced to "indivisible ruby longer than the line", "columns too narrow for layout",
	 * "{@code min-width} wider than its container", or "narrow floats". These share the nature of the 2026-07-26
	 * decision (exclude documents placing content that cannot fit; correcting dimensions is the author's
	 * responsibility), but {@link #isOversized} missed them because it checks only {@code width}/{@code height}/font size.
	 * The user decision on 2026-09-17 expanded the detector to other expressions of the same condition.
	 * Use only static lower-bound estimates that follow nesting in {@link #findUnfittableContent}.
	 * </p>
	 *
	 * <p>
	 * <b>Use only for off-paper checks</b> (not blank pages, content loss, reading order, duplication, or conversion failure).
	 * Aggregate each reason as a separate category to notice changes in the exclusion breakdown.
	 * </p>
	 */
	private static final class ExcludedByUnfittableContent extends AssertionError {
		private static final long serialVersionUID = 1L;
		final String reason;

		ExcludedByUnfittableContent(final String message, final String reason) {
			super(message);
			this.reason = reason;
		}
	}

	/** Roughly extract the category (defect class) from a failure message. */
	static String classify(final Throwable t) {
		for (Throwable c = t; c != null; c = c.getCause()) {
			if (c instanceof PageCountLimitExceeded) {
				return "ページ数過大";
			}
		}
		if (t instanceof ExcludedByUntypesettableFloat) {
			return "(除外)組版できない幅の浮動体";
		}
		if (t instanceof ExcludedByOrthogonalLineAxis) {
			return "(除外)直交フローの行軸はみ出し";
		}
		if (t instanceof ExcludedByNestedOrthogonalFlow) {
			return "(除外)直交フロー3段以上の入れ子";
		}
		if (t instanceof ExcludedByUntypesettableOppositeProgression) {
			return "(除外)同軸逆進行フローの組版不能幅";
		}
		if (t instanceof ExcludedByUntypesettableOrthogonalFlow) {
			return "(除外)直交フローの組版不能幅";
		}
		if (t instanceof ExcludedByFlexMulticolMinContent) {
			return "(除外)flex内の段組表によるmin-content幅";
		}
		if (t instanceof ExcludedByUnfittableContent) {
			return "(除外)収まらない内容: " + ((ExcludedByUnfittableContent) t).reason;
		}
		// We catch a wrapper (AssertionError), so concatenate **all messages in the cause chain**
		// for classification. Looking only at t.getMessage() always yields the wrapper message,
		// collapsing all categories into one (encountered on 2026-07-26).
		if (t instanceof ExcludedByOversizedBox) {
			// The exclusion reason is the same (unworkable type area), but retain which invariant
			// triggered it; otherwise, changes in the exclusion breakdown
			// would go unnoticed.
			return String.valueOf(t.getMessage()).startsWith("白紙ページ") ? "(除外)版面が破綻した文書の白紙ページ"
					: "(除外)版面が破綻した文書の紙面外配置";
		}
		final StringBuilder chain = new StringBuilder();
		for (Throwable c = t; c != null; c = c.getCause()) {
			chain.append(String.valueOf(c.getMessage())).append('\0');
		}
		final String m = chain.toString();
		// Classify conversion errors by **message shape**. The original AssertionError stack
		// is lost when wrapping in TranscoderException
		// (not preserved in cause either), so classification by failure location is impossible.
		// Coarse categories underestimate "how many remain" (2026-07-26).
		if (m.contains("auto page break repeated")) {
			// Stopped by the livelock guard. Includes `Unexpected error.`, so without a separate category
			// it gets mixed into textBuilder, falsifying the aggregate (2026-07-27).
			return "進捗のない自動改ページ(ガードが停止)";
		}
		if (m.contains("再生範囲") || m.contains("range is not intact")) {
			return "再生範囲が欠けている";
		}
		if (m.contains("break flow failed")) {
			return "不変条件: flowStack深さ≠継続深さ";
		}
		if (m.contains("text builder still open")) {
			return "不変条件: textBuilderが開いたまま";
		}
		// **Do not collapse `Unexpected error.` here** (2026-07-28).
		// It is a generic TranscoderException wrapper message, attached regardless of cause.
		// Previously, we assumed it meant "textBuilder left open",
		// **reporting completely different defects under that name**. An actual case:
		// the failing assertion was on the `textBuilder != null` side, meaning
		// **it was null, not open**. Since the label did not say that,
		// I (and the fixer) pursued the wrong mechanism.
		// Classify unnamed conversion failures **by their location** (detailKey).
		// The same message at different failure locations represents different defects.
		if (m.contains("白紙ページ")) {
			return "白紙ページ";
		}
		if (m.contains("内容が失われた")) {
			return "内容の消失";
		}
		if (m.contains("紙面外への配置")) {
			return "紙面外への配置";
		}
		if (m.contains("全描画が紙面外")) {
			return "全描画が紙面外";
		}
		if (m.contains("読み順が入れ替わった")) {
			return "読み順の逆転";
		}
		if (m.contains("内容が複製された")) {
			return "内容の複製";
		}
		if (m.contains("watchdogを超えたスレッドが")) {
			return "掃過が過負荷(測定不能)";
		}
		if (m.contains("watchdog超過")) {
			// **Do not assert "nontermination"**. Under an overloaded sweep, even normal documents
			// exceed the limit (demonstrated on 2026-07-27). This does not prove an infinite loop,
			// so make that clear in the category name.
			return "watchdog超過(停止性は未確定)";
		}
		if (m.contains("ページ数が過大")) {
			return "ページ数過大";
		}
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		final String site = detailKey(t);
		return site != null ? site : c.getClass().getSimpleName();
	}

	/**
	 * Classify using the exception's **location** (the topmost foliojet stack frame) too,
	 * so different defects with the same exception type count separately.
	 * Coarse classification underestimates "how many remain".
	 */
	private static String detailKey(final Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		for (final StackTraceElement e : c.getStackTrace()) {
			if (e.getClassName().startsWith("net.zamasoft.foliojet")) {
				final String cls = e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1);
				return c.getClass().getSimpleName() + "@" + cls + "." + e.getMethodName() + ":" + e.getLineNumber();
			}
		}
		return null;
	}

	private static String normalizedDefectKey(final Throwable t, final int seed,
			final boolean strict) {
		Throwable deepest = t;
		while (deepest.getCause() != null) {
			deepest = deepest.getCause();
		}
		String site = "-";
		for (final StackTraceElement frame : deepest.getStackTrace()) {
			if (frame.getClassName().startsWith("net.zamasoft.foliojet")) {
				site = frame.getClassName() + "." + frame.getMethodName()
						+ ":" + frame.getLineNumber();
				break;
			}
		}
		String topology;
		try {
			topology = inspectGeneratedStructure(generate(seed, strict).html()).topologyHash();
		} catch (final Throwable unavailable) {
			topology = "unavailable";
		}
		return classify(t) + "|cause=" + deepest.getClass().getName()
				+ "|site=" + site + "|topology=" + topology;
	}

	private static void rememberSeed(
			final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsOf,
			final String key, final int seed) {
		final java.util.List<Integer> list = seedsOf.computeIfAbsent(key,
				x -> java.util.Collections.synchronizedList(new ArrayList<>()));
		synchronized (list) {
			if (list.size() < 8) {
				list.add(seed);
			}
		}
	}

	/**
	 * Aggregation-mode parallelism ({@code -Dfoliojet.fuzzThreads}). Defaults to "core count - 2".
	 * Sweeping millions of documents takes an impractical time without parallelism
	 * (added on 2026-07-26 when setting the goal of "20 years without errors").
	 *
	 * <p>
	 * Conversions are independent per document, and the engine itself assumes concurrent server conversions
	 * (state is ThreadLocal). Simply run the existing one-document-per-thread structure side by side;
	 * do not change the checks.
	 * </p>
	 */
	private static int sweepThreads() {
		final String v = System.getProperty("foliojet.fuzzThreads");
		if (v != null) {
			return Math.max(1, Integer.parseInt(v));
		}
		return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
	}

	/**
	 * In large sweeps, suppress known per-input WARNING messages on stderr.
	 * The old job reached 20 MB at 80,000 seeds; extreme has about 1,000 elements per document,
	 * so log I/O dominates exploration. Exceptions and oracle violations are always classified separately.
	 */
	private static void configureSweepLogging() {
		configureFuzzLogging(java.util.logging.Level.SEVERE);
	}

	private static void configureFuzzLogging(final java.util.logging.Level level) {
		if (System.getProperty("foliojet.fuzzVerboseLogging") == null) {
			final java.util.logging.Logger packageLogger = java.util.logging.Logger
					.getLogger("net.zamasoft.foliojet");
			packageLogger.setLevel(level);
			final java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
			for (final java.util.logging.Handler handler : root.getHandlers()) {
				handler.setLevel(level);
			}
			// Also silence child loggers that already have their own handlers. Loggers created later inherit
			// packageLogger, and the root handler also discards WARNING messages.
			final java.util.logging.LogManager manager = java.util.logging.LogManager.getLogManager();
			final java.util.Enumeration<String> names = manager.getLoggerNames();
			while (names.hasMoreElements()) {
				final String name = names.nextElement();
				if (!name.startsWith("net.zamasoft.foliojet")) {
					continue;
				}
				final java.util.logging.Logger logger = manager.getLogger(name);
				logger.setLevel(level);
				for (final java.util.logging.Handler handler : logger.getHandlers()) {
					handler.setLevel(level);
				}
			}
		}
	}

	/** Parallel sweep in aggregation mode. Share checks through {@link #checkOne}. */
	private void sweepParallel(final boolean strict, final int seeds) throws Exception {
		configureSweepLogging();
		final int threads = sweepThreads();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> classCount =
				new java.util.concurrent.ConcurrentHashMap<>();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsOf =
				new java.util.concurrent.ConcurrentHashMap<>();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> defectCount =
				new java.util.concurrent.ConcurrentHashMap<>();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> defectSeeds =
				new java.util.concurrent.ConcurrentHashMap<>();
		final SweepMeasurements measurements = new SweepMeasurements();
		final int from = seedFrom();
		System.out.println("[fuzzManifest] {\"generatorVersion\":" + GENERATOR_VERSION
				+ ",\"generatorProfile\":\"" + generatorProfile() + "\",\"mode\":\""
				+ (strict ? "strict" : "wild") + "\",\"from\":" + from + ",\"seeds\":" + seeds + "}");
		final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger(from + 1);
		final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
		final long began = System.currentTimeMillis();
		final java.util.function.BiConsumer<Integer, Throwable> recordFailure = (seed, t) -> {
			final String k = classify(t);
			classCount.computeIfAbsent(k, x -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
			rememberSeed(seedsOf, k, seed);
			final String normalized = normalizedDefectKey(t, seed, strict);
			defectCount.computeIfAbsent(normalized,
					x -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
			rememberSeed(defectSeeds, normalized, seed);
		};
		// Racing lazy initialization of dependency libraries can cause DirectSession initialization
		// to fail only on cold startup. Complete the first case serially
		// before releasing workers (include this seed in the aggregate count too).
		if (seeds > 0) {
			try {
				checkSweepDocument(from, strict, measurements);
			} catch (final Throwable t) {
				recordFailure.accept(from, t);
			}
			done.set(1);
		}
		final Thread[] workers = new Thread[threads];
		for (int w = 0; w < threads; ++w) {
			workers[w] = new Thread(() -> {
				for (;;) {
					final int seed = next.getAndIncrement();
					if (seed >= from + seeds) {
						return;
					}
					try {
						checkSweepDocument(seed, strict, measurements);
					} catch (final Throwable t) {
						recordFailure.accept(seed, t);
					}
					final int n = done.incrementAndGet();
					if (n % PROGRESS_EVERY == 0) {
						System.out.println("[fuzzProgress] " + (strict ? "strict" : "wild") + " " + n + "/" + seeds
								+ " 経過" + ((System.currentTimeMillis() - began) / 1000) + "s "
								+ new java.util.TreeMap<>(classCount));
						try {
							writeFuzzManifest(strict, from, seeds, threads, n, false,
									classCount, seedsOf, defectCount, defectSeeds);
						} catch (final Exception e) {
							System.err.println("[fuzzProgress] manifestのcheckpointを書けない: " + e);
						}
					}
				}
			}, "fuzz-sweep-" + (strict ? "s" : "w") + w);
			workers[w].setDaemon(true);
			workers[w].start();
		}
		for (final Thread t : workers) {
			t.join();
		}
		final long ms = System.currentTimeMillis() - began;
		if (reportMode()) {
			reportFeatureCoverage(strict, seeds, from);
		}
		System.out.println("[fuzzReport] generator=" + generatorLabel() + " mode="
				+ (strict ? "strict" : "wild") + " seeds=" + seeds
				+ (from == 0 ? "" : " from=" + from) + " threads="
				+ threads + " elapsed=" + (ms / 1000) + "s (" + String.format("%.2f", ms / (double) seeds)
				+ " ms/文書)");
		if (classCount.isEmpty()) {
			System.out.println("[fuzzReport]   失敗なし");
		}
		for (final String k : new java.util.TreeSet<>(classCount.keySet())) {
			System.out.println("[fuzzReport]   " + k + " : " + classCount.get(k).get() + "件 seeds=" + seedsOf.get(k));
		}
		for (final String k : new java.util.TreeSet<>(defectCount.keySet())) {
			System.out.println("[fuzzReport]   正規化キー " + k + " : "
					+ defectCount.get(k).get() + "件 seeds=" + defectSeeds.get(k));
		}
		final String mode = strict ? "strict" : "wild";
		System.out.println("[fuzzReport] mode=" + mode + " 生成要素数分布 "
				+ measurements.elements.summary());
		System.out.println("[fuzzReport] mode=" + mode + " 出力ページ数分布 "
				+ measurements.pages.summary());
		System.out.println("[fuzzReport] mode=" + mode + " 紙面外検査の生存 " + measurements.offPageSummary());
		// Count of prevented rescue attempts on open boxes not selected by the plan (2026-10-07, OpenBoxes; JVM-wide total).
		System.out.println("[fuzzReport] mode=" + mode + " 開いた箱の救済の抑止(計画外) "
				+ net.zamasoft.foliojet.layout.fragment.OpenBoxes.UNSELECTED_RESCUES_PREVENTED.get());
		reportLocalCoverage(strict, measurements.coverage);
		final List<Long> defects = new ArrayList<>();
		for (final var count : defectCount.values()) {
			defects.add((long) count.get());
		}
		reportDiscovery(mode, "defects", defects);
		for (final LocalScope scope : LocalScope.values()) {
			for (int t = 2; t <= 5; ++t) {
				final List<Long> occurrences = new ArrayList<>();
				for (final var entry : measurements.coverage.entrySet()) {
					if (entry.getKey().scope() == scope && entry.getKey().t() == t) {
						occurrences.add(entry.getValue().sum());
					}
				}
				reportDiscovery(mode, "coverage/" + scope.label + "/t" + t, occurrences);
			}
		}
		writeFuzzManifest(strict, from, seeds, threads, done.get(), true,
				classCount, seedsOf, defectCount, defectSeeds);
	}

	private void checkSweepDocument(final int seed, final boolean strict,
			final SweepMeasurements measurements) throws Exception {
		final Generated doc = generate(seed, strict);
		measurements.record(inspectGeneratedStructure(doc.html()));
		measurements.recordOffPageLiveness(doc);
		checkOne(seed, strict, doc, measurements.pages::add);
	}

	private static int minimumDocuments(final int t) {
		return switch (t) {
		case 2 -> 100;
		case 3 -> 50;
		case 4 -> 20;
		case 5 -> 5;
		default -> throw new IllegalArgumentException("t=" + t);
		};
	}

	private static void reportLocalCoverage(final boolean strict,
			final java.util.concurrent.ConcurrentHashMap<CoverageKey,
					java.util.concurrent.atomic.LongAdder> counts) {
		final java.util.Set<CoverageKey> reachable = reachableLocalCombos(strict);
		final String mode = strict ? "strict" : "wild";
		for (final LocalScope scope : LocalScope.values()) {
			for (int t = 2; t <= 5; ++t) {
				final int minDocuments = minimumDocuments(t);
				long denominator = 0;
				long observed = 0;
				long qualified = 0;
				for (final CoverageKey key : reachable) {
					if (key.scope() != scope || key.t() != t) {
						continue;
					}
					++denominator;
					final var count = counts.get(key);
					final long documents = count == null ? 0 : count.sum();
					if (documents > 0) {
						++observed;
					}
					if (documents >= minDocuments) {
						++qualified;
					}
				}
				long outsideSchema = 0;
				for (final CoverageKey key : counts.keySet()) {
					if (key.scope() == scope && key.t() == t && !reachable.contains(key)) {
						++outsideSchema;
					}
				}
				final String coverage = denominator == 0 ? "n/a"
						: String.format(java.util.Locale.ROOT, "%.3f%%",
								qualified * 100.0 / denominator);
				System.out.println("[fuzzReport] mode=" + mode + " 局所機能被覆 scope="
						+ scope.label + " t=" + t + " reachable=" + denominator
						+ " observed=" + observed + " qualified=" + qualified
						+ " minDocs=" + minDocuments + " coverage=" + coverage
						+ " outsideSchema=" + outsideSchema);
			}
		}
	}

	private static void reportDiscovery(final String mode, final String label,
			final List<Long> occurrences) {
		long m = 0;
		long f1 = 0;
		long f2 = 0;
		for (final long count : occurrences) {
			m += count;
			if (count == 1) {
				++f1;
			} else if (count == 2) {
				++f2;
			}
		}
		final double p0 = m == 0 ? 0 : f1 / (double) m;
		final double chao1 = occurrences.size()
				+ f1 * (f1 - 1.0) / (2.0 * (f2 + 1.0));
		System.out.println("[fuzzReport] mode=" + mode + " discovery=" + label
				+ " M=" + m + " Sobs=" + occurrences.size()
				+ " f1=" + f1 + " f2=" + f2
				+ " GoodTuring-p0="
				+ String.format(java.util.Locale.ROOT, "%.8g", p0)
				+ " Chao1="
				+ String.format(java.util.Locale.ROOT, "%.6f", chao1));
	}

	private static String buildIdentifier() throws Exception {
		final String supplied = System.getProperty("foliojet.fuzzBuild");
		if (supplied != null && !supplied.isBlank()) {
			return supplied.trim();
		}
		final MessageDigest digest = sha256();
		for (final Class<?> type : List.of(RandomDocumentFuzzTest.class, DirectSession.class)) {
			final String resource = "/" + type.getName().replace('.', '/') + ".class";
			try (java.io.InputStream in = type.getResourceAsStream(resource)) {
				if (in == null) {
					throw new IllegalStateException("build識別用classを読めない: " + resource);
				}
				final byte[] buffer = new byte[8192];
				for (int len; (len = in.read(buffer)) >= 0;) {
					digest.update(buffer, 0, len);
				}
			}
		}
		return "classes-" + java.util.HexFormat.of().formatHex(digest.digest(), 0, 12);
	}

	private static String jsonEscape(final String value) {
		final StringBuilder escaped = new StringBuilder();
		for (int i = 0; i < value.length(); ++i) {
			final char c = value.charAt(i);
			switch (c) {
			case '"' -> escaped.append("\\\"");
			case '\\' -> escaped.append("\\\\");
			case '\b' -> escaped.append("\\b");
			case '\f' -> escaped.append("\\f");
			case '\n' -> escaped.append("\\n");
			case '\r' -> escaped.append("\\r");
			case '\t' -> escaped.append("\\t");
			default -> {
				if (c < 0x20) {
					escaped.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
				} else {
					escaped.append(c);
				}
			}
			}
		}
		return escaped.toString();
	}

	private static synchronized void writeFuzzManifest(final boolean strict, final int from,
			final int seeds, final int threads, final int processed, final boolean completed,
			final java.util.concurrent.ConcurrentHashMap<String,
					java.util.concurrent.atomic.AtomicInteger> classCount,
			final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsOf,
			final java.util.concurrent.ConcurrentHashMap<String,
					java.util.concurrent.atomic.AtomicInteger> defectCount,
			final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> defectSeeds)
			throws Exception {
		final String manifestProperty = System.getProperty("foliojet.fuzzManifest");
		if (manifestProperty == null || manifestProperty.isBlank()) {
			return;
		}
		final String mode = strict ? "strict" : "wild";
		final StringBuilder json = new StringBuilder();
		json.append("{\"from\":").append(from)
				.append(",\"seeds\":").append(seeds)
				.append(",\"processed\":").append(processed)
				.append(",\"completed\":").append(completed)
				.append(",\"generatorVersion\":").append(GENERATOR_VERSION)
				.append(",\"generatorProfile\":\"").append(generatorProfile())
				.append("\",\"mode\":\"").append(mode)
				.append("\",\"build\":\"").append(jsonEscape(buildIdentifier()))
				.append("\",\"threads\":").append(threads)
				.append(",\"updatedAt\":\"").append(Instant.now()).append('"');
		if (completed) {
			json.append(",\"completedAt\":\"").append(Instant.now()).append('"');
		}
		json.append(",\"classificationCounts\":{");
		boolean first = true;
		for (final String key : new java.util.TreeSet<>(classCount.keySet())) {
			if (!first) {
				json.append(',');
			}
			first = false;
			json.append('"').append(jsonEscape(key)).append("\":")
					.append(classCount.get(key).get());
		}
		json.append("},\"classificationSeeds\":");
		appendSeedMap(json, seedsOf);
		json.append(",\"normalizedDefectCounts\":{");
		first = true;
		for (final String key : new java.util.TreeSet<>(defectCount.keySet())) {
			if (!first) {
				json.append(',');
			}
			first = false;
			json.append('"').append(jsonEscape(key)).append("\":")
					.append(defectCount.get(key).get());
		}
		json.append("},\"normalizedDefectSeeds\":");
		appendSeedMap(json, defectSeeds);
		json.append("}\n");

		final Path target = Path.of(manifestProperty).toAbsolutePath();
		Files.createDirectories(target.getParent());
		final Path temporary = target.resolveSibling(target.getFileName()
				+ ".tmp-" + ProcessHandle.current().pid() + "-" + mode);
		Files.writeString(temporary, json, StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
				java.nio.file.StandardOpenOption.WRITE);
		try {
			Files.move(temporary, target,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (final java.nio.file.AtomicMoveNotSupportedException e) {
			Files.move(temporary, target,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		System.out.println("[fuzzReport] manifest=" + target);
	}

	private static void appendSeedMap(final StringBuilder json,
			final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsByKey) {
		json.append('{');
		boolean firstKey = true;
		for (final String key : new java.util.TreeSet<>(seedsByKey.keySet())) {
			if (!firstKey) {
				json.append(',');
			}
			firstKey = false;
			json.append('"').append(jsonEscape(key)).append("\":[");
			final java.util.List<Integer> values = seedsByKey.get(key);
			synchronized (values) {
				for (int i = 0; i < values.size(); ++i) {
					if (i > 0) {
						json.append(',');
					}
					json.append(values.get(i));
				}
			}
			json.append(']');
		}
		json.append('}');
	}

	/**
	 * Measure <b>how many features each document covers</b> (2026-08-02, user feedback).
	 * "N documents, zero failures" describes only single-feature reliability; defects live in
	 * <b>combinations</b>, so use <b>pair coverage</b>: the fraction of all feature pairs
	 * that actually co-occur.
	 *
	 * <p>
	 * Document generation is deterministic, so regenerating and counting separately from a sweep
	 * produces the same set (cheap because there is no conversion). Sample up to the first 2,000 seeds.
	 * </p>
	 */
	private static void reportFeatureCoverage(final boolean strict, final int seeds, final int from) {
		final int sample = Math.min(seeds, 2000);
		final java.util.List<String> features = LOCAL_FEATURES;
		final int n = features.size();
		final java.util.Map<Integer, java.util.Set<Long>> combos = new java.util.HashMap<>();
		long featureTotal = 0;
		for (int i = 0; i < sample; ++i) {
			final String doc = generate(from + i, strict).html();
			final boolean[] has = new boolean[n];
			int count = 0;
			for (int f = 0; f < n; ++f) {
				has[f] = doc.contains(features.get(f));
				if (has[f]) {
					++count;
				}
			}
			featureTotal += count;
			final int[] present = new int[count];
			int at = 0;
			for (int f = 0; f < n; ++f) {
				if (has[f]) {
					present[at++] = f;
				}
			}
			recordCombos(present, count, combos);
		}
		final StringBuilder tways = new StringBuilder();
		for (int t = 2; t <= 5; ++t) {
			final long total = binomial(n, t);
			final java.util.Set<Long> set = combos.get(t);
			final long got = set == null ? 0 : set.size();
			if (tways.length() > 0) {
				tways.append(' ');
			}
			tways.append(t).append("組 ").append(String.format("%.1f", got * 100.0 / total)).append('%');
		}
		System.out.println("[fuzzReport] generator=" + generatorLabel() + " mode="
				+ (strict ? "strict" : "wild") + " 機能被覆: 1文書あたり平均"
				+ String.format("%.1f", featureTotal / (double) sample) + "機能 / " + n + "機能中、"
				+ tways + " (標本" + sample + "文書)");
	}

	/** Number of combinations choosing {@code t} from {@code n}. */
	private static long binomial(final int n, final int t) {
		long v = 1;
		for (int i = 0; i < t; ++i) {
			v = v * (n - i) / (i + 1);
		}
		return v;
	}

	/**
	 * Enumerate and record every t-feature combination (t=2..5) from a document's feature set
	 * (2026-08-02, user feedback: do not stop at pairs). With at most 63 features,
	 * a {@code long} bitmask uniquely represents each combination.
	 */
	private static void recordCombos(final int[] present, final int count,
			final java.util.Map<Integer, java.util.Set<Long>> combos) {
		for (int t = 2; t <= 5; ++t) {
			if (count < t) {
				break;
			}
			final int[] idx = new int[t];
			for (int i = 0; i < t; ++i) {
				idx[i] = i;
			}
			final java.util.Set<Long> set = combos.computeIfAbsent(t, k -> new java.util.HashSet<>());
			while (true) {
				long mask = 0;
				for (int i = 0; i < t; ++i) {
					mask |= 1L << present[idx[i]];
				}
				set.add(mask);
				int i = t - 1;
				while (i >= 0 && idx[i] == count - t + i) {
					--i;
				}
				if (i < 0) {
					break;
				}
				++idx[i];
				for (int j = i + 1; j < t; ++j) {
					idx[j] = idx[j - 1] + 1;
				}
			}
		}
	}

	private void sweep(final boolean strict) throws Exception {
		// **Entry point for automatically minimizing a failed seed** (added 2026-07-28).
		// Sweeps finish in minutes, but diagnosing one case takes hours; let the machine
		// reduce it. See {@link FuzzShrinker} for predicate construction.
		// Careless predicates produce false minimal cases (LESSONS.md §3.15).
		final String shrinkSeed = System.getProperty("foliojet.fuzzShrink");
		if (shrinkSeed != null) {
			// Reproducing the same AssertionError across thousands of candidates must not print
			// DirectSession's SEVERE stack every time. Shrinker classifications and progress go to stdout.
			configureFuzzLogging(java.util.logging.Level.OFF);
			final String mode = System.getProperty("foliojet.fuzzShrinkMode", "strict");
			if ("both".equals(mode) || strict == "strict".equals(mode)) {
				FuzzShrinker.shrink(Integer.parseInt(shrinkSeed.trim()), strict);
			}
			return;
		}
		// **Entry point for reducing a file** (added 2026-07-28). Use for handwritten reproducers or
		// checking the shrinker itself (inflate a document with a known answer, then reduce it).
		final String shrinkFile = System.getProperty("foliojet.fuzzShrinkFile");
		if (shrinkFile != null) {
			if (strict) {
				FuzzShrinker.shrinkFile(new File(shrinkFile));
			}
			return;
		}
		// **Entry point for applying the same checks to arbitrary HTML** (added 2026-07-28). Use it
		// to verify that a reduced document also fails in the same category through
		// the normal path that bypasses the generator.
		final String checkFile = System.getProperty("foliojet.fuzzCheckFile");
		if (checkFile != null) {
			if (strict) {
				FuzzShrinker.checkFile(new File(checkFile));
			}
			return;
		}
		// Entry point for batch rechecking only known classified seeds after a fix. Starting Gradle
		// for each seed is dominated by compilation and JVM startup, so run a comma-separated list
		// to completion in one test JVM and fail collectively only for actual failures.
		final String selected = System.getProperty("foliojet.fuzzOnlySeeds");
		if (selected != null) {
			final boolean v1 = System.getProperty("foliojet.fuzzV1") != null;
			final List<String> failures = new ArrayList<>();
			for (final String part : selected.split(",")) {
				final int seed = Integer.parseInt(part.trim());
				System.out.println("[fuzzOnly] generator=" + (v1 ? "1" : generatorLabel()) + " mode="
						+ (strict ? "strict" : "wild") + " seed=" + seed);
				try {
					if (v1) {
						checkOneV1(seed, strict);
					} else {
						checkOne(seed, strict);
					}
					System.out.println("[fuzzOnly]   通った");
				} catch (final ExcludedByOversizedBox | ExcludedByUntypesettableFloat
						| ExcludedByUnfittableContent excluded) {
					System.out.println("[fuzzOnly]   " + classify(excluded) + " : " + excluded);
				} catch (final Throwable t) {
					System.out.println("[fuzzOnly]   " + classify(t) + " : " + t);
					failures.add("seed " + seed + ": " + t);
				}
			}
			assertTrue(String.join("\n", failures), failures.isEmpty());
			return;
		}
		// **Entry point for running a specific seed only** (added 2026-07-27).
		// Large sweeps reuse and discard artifacts, so discovering later
		// that "content disappeared at seed 27648" previously left no reproducer.
		// The generator is deterministic: specifying the seed always yields the same document.
		final String only = System.getProperty("foliojet.fuzzOnlySeed");
		if (only != null) {
			final int seed = Integer.parseInt(only);
			// -Dfoliojet.fuzzV1=1 reproduces the v1 distribution (for verifying old sweep seeds).
			final boolean v1 = System.getProperty("foliojet.fuzzV1") != null;
			System.out.println("[fuzzOnly] generator=" + (v1 ? "1" : generatorLabel()) + " mode="
					+ (strict ? "strict" : "wild") + " seed=" + seed);
			try {
				if (v1) {
					checkOneV1(seed, strict);
				} else {
					checkOne(seed, strict);
				}
				System.out.println("[fuzzOnly]   通った");
			} catch (final Throwable t) {
				System.out.println("[fuzzOnly]   " + classify(t) + " : " + t);
				// **Always fail** (2026-07-29). Previously, swallowing failures here made this mode
				// always return exit=0 and failures=0,
				// even on failure. This actually caused a false "fixed" conclusion
				// (seed 213026: the sweep kept failing, but checking through this entry point
				// led to reporting it fixed). A display-only checker lies.
				throw new AssertionError("seed " + seed + " (" + (strict ? "strict" : "wild") + ") が失敗した: " + t, t);
			}
			return;
		}
		final int seeds = seedCount();
		final boolean report = reportMode();
		if (report) {
			this.sweepParallel(strict, seeds);
			return;
		}
		final List<String> failures = new ArrayList<>();
		final List<Integer> knownStillFailing = new ArrayList<>();
		final java.util.TreeMap<String, int[]> classCount = new java.util.TreeMap<>();
		final java.util.TreeMap<String, java.util.List<Integer>> seedsOf = new java.util.TreeMap<>();
		for (int seed = seedFrom(), end = seedFrom() + seeds; seed < end; ++seed) {
			final boolean known = !report && strict
					&& (KNOWN_TRAILING_BLANK_PAGE.contains(seed) || KNOWN_INVARIANT_VIOLATION.contains(seed));
			try {
				checkOne(seed, strict);
				if (known) {
					failures.add("seed=" + seed + ": 既知の未解決だったが通った。"
							+ "KNOWN_TRAILING_BLANK_PAGE から外すこと");
				}
			} catch (final ExcludedByOversizedBox excluded) {
				// Exclusion. Count only in aggregation mode (caught by sweepParallel).
				continue;
			} catch (final Throwable t) {
				if (report) {
					final String k = classify(t);
					classCount.computeIfAbsent(k, x -> new int[1])[0]++;
					final var lst = seedsOf.computeIfAbsent(k, x -> new ArrayList<>());
					if (lst.size() < 8) {
						lst.add(seed);
					}
					continue;
				}
				if (known) {
					knownStillFailing.add(seed);
					continue;
				}
				failures.add("seed=" + seed + " (" + (strict ? "strict" : "wild") + "): " + t);
				if (failures.size() >= 5) {
					break; // The first few cases suffice. Running all cases adds no information.
				}
			}
		}
		if (report) {
			System.out.println("[fuzzReport] generator=" + generatorLabel() + " mode="
					+ (strict ? "strict" : "wild") + " seeds=" + seeds);
			if (classCount.isEmpty()) {
				System.out.println("[fuzzReport]   失敗なし");
			}
			for (final var e : classCount.entrySet()) {
				System.out.println("[fuzzReport]   " + e.getKey() + " : " + e.getValue()[0] + "件 seeds="
						+ seedsOf.get(e.getKey()));
			}
			return;
		}
		if (!knownStillFailing.isEmpty()) {
			System.out.println("[fuzz] 既知の未解決(末尾の空ページ): " + knownStillFailing);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private int checkOne(final int seed, final boolean strict) throws Exception {
		return checkOne(seed, strict, generate(seed, strict), null);
	}

	/** Check historical seeds using the v1 input distribution (expected results were recorded for v1 documents). */
	private int checkOneV1(final int seed, final boolean strict) throws Exception {
		return checkOne(seed, strict, generate(seed, strict, true), null);
	}

	private int checkOne(final int seed, final boolean strict, final Generated doc,
			final java.util.function.IntConsumer pageObserver) throws Exception {
		// Long sweeps (millions of documents) cannot retain per-seed artifacts without exhausting disk.
		// Failed seeds always reproduce by rerunning the same seed
		// (the generator is deterministic), so successful-seed artifacts may be discarded.
		// Reuse the output directory instead of creating one per seed (2026-07-26).
		// Aggregation mode reuses per-worker slots regardless of sweep size.
		// The generator is deterministic, so reported seeds can be restored by individual reruns.
		final boolean keep = System.getProperty("foliojet.fuzzOnlySeed") != null || !reportMode();
		final String slot = keep ? String.valueOf(seed) : Thread.currentThread().getName();
		final File html = new File(workDir(), (strict ? "strict" : "wild") + "-" + slot + ".html");
		final File outDir = new File(workDir(), "dl-" + (strict ? "strict" : "wild") + "-" + slot);
		return checkDocument(doc, html, outDir, strict, "fuzz-" + seed, pageObserver);
	}

	/**
	 * Check one <b>already generated document</b> (split from {@code checkOne} on 2026-07-28).
	 *
	 * <p>
	 * Separated for {@link FuzzShrinker} to use as a <b>predicate</b>. The shrinker checks
	 * <b>documents not produced by the generator</b>, so {@link Generated} must be supplied externally.
	 * Rebuilding it from {@code seed} would apply <b>the original document's token table</b>
	 * to the reduced document, mistaking "removed by reduction" for "content loss"
	 * (almost became the sixth example in `LESSONS.md` §3.15).
	 * </p>
	 */
	static int checkDocument(final Generated doc, final File html, final File outDir, final boolean strict,
			final String workerName) throws Exception {
		return checkDocument(doc, html, outDir, strict, workerName, null);
	}

	private static int checkDocument(final Generated doc, final File html, final File outDir,
			final boolean strict, final String workerName,
			final java.util.function.IntConsumer pageObserver) throws Exception {
		final int pageLimit = pageLimit(doc);
		html.getParentFile().mkdirs();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(html), StandardCharsets.UTF_8)) {
			w.write(doc.html);
		}

		outDir.mkdirs();
		final File[] old = outDir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}

		final Throwable[] failure = new Throwable[1];
		final DirectSession[] session = new DirectSession[1];
		final Thread worker = new Thread(() -> {
			try {
				convert(html, outDir, session, pageLimit);
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, workerName);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		// Invariant 2: termination.
		if (worker.isAlive()) {
			// **First, actually try to stop it** (2026-07-27). Cooperative interruption points
			// (`UserAgent.checkAbort`) in the engine allow stopping even partway through a page.
			// Leaving it running retains **one layout's heap and a 64 MB stack reservation**,
			// causing the sweep to stall through self-amplification.
			final DirectSession s = session[0];
			if (s != null) {
				try {
					s.abort(jp.cssj.cti2.CTISession.ABORT_FORCE);
					worker.join(5_000L);
				} catch (final Exception ignore) {
					// Proceed to containment below even if the abort request fails.
				}
			}
		}
		if (worker.isAlive()) {
			// It did not stop after the abort request. Modern Java does not support Thread.stop(),
			// so the only remaining option is to **count and contain**.
			//
			// This self-amplifies (discovered when a 100,000-document sweep stalled for seven hours on 2026-07-27):
			// heap pressure → continuous GC slows everything → more documents
			// exceed the watchdog → more leaks. The measurement depended on scale:
			// **zero at 20,000 seeds, frequent at 50,000 seeds**.
			//
			// Since they cannot be stopped, at least (a) lower their priority so they do not slow the sweep,
			// and (b) stop the entire sweep above a certain count.
			// **Stopping loudly is better than silently slowing down.**
			try {
				worker.setPriority(Thread.MIN_PRIORITY);
			} catch (final RuntimeException ignore) {
				// Continue even if priority cannot be lowered.
			}
			final int leaked = LEAKED_WORKERS.incrementAndGet();
			if (leaked > maxLeakedWorkers()) {
				throw new AssertionError("watchdogを超えたスレッドが" + leaked + "本たまった。"
						+ "掃過が過負荷になっており、以後の測定は信用できない"
						+ "(-Dfoliojet.fuzzThreads を減らすか -PtestHeap を増やすこと)。最後の文書: " + html);
			}
			fail("watchdog超過 " + (WATCHDOG_MS / 1000) + "秒 (" + html + ")");
		}
		// Invariant 1: no exception aborts.
		if (failure[0] != null) {
			throw new AssertionError("変換が例外で終わった (" + html + ")", failure[0]);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("ページが1枚も出ていない (" + html + ")", pages);
		assertTrue("ページが1枚も出ていない (" + html + ")", pages.length > 0);
		// Invariant 3: bounded page count.
		assertTrue("ページ数が過大 " + pages.length + " (上限" + pageLimit + ", " + html + ")",
				pages.length <= pageLimit);
		if (pageObserver != null) {
			pageObserver.accept(pages.length);
		}

		// **WILD stops here** (2026-07-28). Invariants 4–8 are STRICT-only,
		// so WILD uses none of the following read/parse results. Previously it read and parsed all pages
		// before discarding them (the early return was **after** parsing).
		// Measurements differed only within noise, but there is no reason to keep work whose results are discarded.
		if (!strict) {
			return pages.length;
		}

		java.util.Arrays.sort(pages);
		final List<ObservedText> observedText = new ArrayList<>();
		// The **first page on which each token appears** (invariant 7). Within-page painting order
		// is an implementation detail (rowspan cells are painted after their spanned rows are finalized),
		// so order can only be checked at page granularity.
		final java.util.Map<String, Integer> firstPage = new java.util.HashMap<String, Integer>();
		// Per-token "maximum painting count within one page" (invariant 8).
		final java.util.Map<String, int[]> drawn = new java.util.HashMap<String, int[]>();
		final List<Integer> blanks = new ArrayList<>();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = Files.readString(Path.of(pages[i].toURI()), StandardCharsets.UTF_8);
			boolean drew = false;
			for (final String line : dump.split("\n")) {
				final String t = line.trim();
				if (!t.isEmpty() && !t.startsWith("drawer")) {
					drew = true;
				}
			}
			if (!drew) {
				blanks.add(i + 1);
			}
			// Count **within the same page**. When a glyph physically crosses a page boundary,
			// that glyph is painted in parts on the two pages. This is valid, so
			// summing across pages causes false positives (603 in 50,000 documents).
			final java.util.Map<String, int[]> onThisPage = new java.util.HashMap<String, int[]>();
			for (final String raw : dump.split("\n")) {
				final Matcher m = TEXT_IN_DUMP.matcher(raw);
				// **Do not count painting marked artifact** (invariant 8, 2026-07-28).
				// In tagged PDF, artifact means "painting outside the logical structure";
				// the engine **intentionally** duplicates it for rescue splitting and similar operations.
				// Counting it as duplication produced 7,227 false positives in 50,000 documents (14.5%),
				// analogous to the naive "within paper" check's 11.9% in §12.
				final boolean artifact = raw.contains(" artifact ");
				while (m.find()) {
					observedText.add(new ObservedText(m.group(1), i, artifact));
					if (!artifact) {
						countTokens(m.group(1), onThisPage);
					}
					if (m.group(2) != null) {
						// Ruby annotation side.
						observedText.add(new ObservedText(m.group(2), i, artifact));
						if (!artifact) {
							countTokens(m.group(2), onThisPage);
						}
					}
				}
			}
			for (final var e : onThisPage.entrySet()) {
				final int[] max = drawn.computeIfAbsent(e.getKey(), x -> new int[1]);
				max[0] = Math.max(max[0], e.getValue()[0]);
			}
		}

		// Invariant 5: no unintended blank pages.
		//
		// **Exclude documents containing boxes that cannot fit on the paper** (user decision on 2026-07-26).
		// Their layout is unworkable regardless of engine behavior: either allow overflow
		// or move to the next page. Correcting dimensions is the layout author's responsibility.
		// Exclusion does not mean ignoring it; **count it as a separate category**.
		// If growth in exclusions goes unnoticed, real regressions can be missed.
		//
		// **Throw exclusions only after checking for lost content and destinations** (2026-10-07). Previously, throwing here
		// skipped invariants 4 and 9 below (about 1.1% of 250,000 sweep documents). Even with an unworkable type area,
		// content loss is always an engine defect (ARCHITECTURE §5.13), never grounds for exclusion.
		AssertionError deferredExclusion = null;
		if (!blanks.isEmpty()) {
			if (doc.beyondEngineControl()) {
				deferredExclusion = new ExcludedByOversizedBox("白紙ページ " + blanks + " (" + html + ")");
			} else if (hasUntypesettableFloat(doc.html())) {
				deferredExclusion = new ExcludedByUntypesettableFloat(
						"白紙ページ " + blanks + " (" + html + ") [組版できない幅の浮動体]");
			} else {
				fail("白紙ページ " + blanks + " (" + html + ")");
			}
		}
		// Invariant 4: no content loss.
		final List<String> lost = new ArrayList<>();
		final Set<String> expectedTokens = Set.copyOf(doc.tokens);
		for (final String token : doc.tokens) {
			final int page = firstObservedTokenPage(token, observedText, expectedTokens);
			if (page < 0) {
				lost.add(token);
			} else {
				firstPage.put(token, page);
			}
		}
		assertTrue("内容が失われた " + lost + " (" + html + ")", lost.isEmpty());
		// Invariant 9: no loss of PDF destinations (id fragments).
		checkFragments(doc, outDir, html);
		if (deferredExclusion != null) {
			// Blank-page exclusion (above). Skip remaining invariants because an unworkable type area affects them.
			throw deferredExclusion;
		}
		// Invariant 8: no content duplication (report-only for now).
		checkNoDuplication(doc, drawn, html);
		// Invariant 6: no unexplained off-paper placement.
		assertNoUnexplainedOffPage(doc, pages, html);
		// Invariant 10: at least one body-text drawing intersects the actual paper.
		assertSomeDrawingOnPage(doc, pages, html);
		// Invariant 7: reading order is preserved (report-only for now).
		checkReadingOrder(doc, firstPage, html);
		return pages.length;
	}

	/**
	 * <b>Invariant 9: no loss of PDF destinations (id fragments)</b> (added 2026-08-03).
	 *
	 * <p>
	 * The generator assigns {@code id="pN"} to paragraphs ({@code id} does not affect layout:
	 * generated documents use no id selectors, so existing seed results remain unchanged).
	 * Named destinations are output by default with {@code output.pdf.hyperlinks.fragment} on,
	 * so the output PDF should list destinations with those names.
	 * If <b>a path discarding a tentatively laid-out page</b> does not undo destination registration,
	 * destinations for elements completed within that discarded page are lost.
	 * Destinations do not appear in display lists, so all existing detectors miss this
	 * (the gap marked "detector not implemented" in PLAN §3).
	 * </p>
	 *
	 * <p>
	 * Destinations are not drawing commands, so only here do we <b>actually read the output PDF</b>
	 * (PDFBox). Skip the check if unreadable: other checks cover PDF validity,
	 * and failing twice here serves no purpose.
	 * </p>
	 */
	private static void checkFragments(final Generated doc, final File outDir, final File html) throws Exception {
		final java.util.Set<String> expected = new java.util.LinkedHashSet<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("id=\"(p[0-9]+)\"").matcher(doc.html());
		while (m.find()) {
			expected.add(m.group(1));
		}
		if (System.getProperty("foliojet.debug.noFragments") != null) {
			// For checking the detector itself. A destination with this name is never output, so
			// if adding it does not fail, **the detector is ineffective**.
			expected.add("p-not-emitted");
		}
		if (expected.isEmpty()) {
			return;
		}
		final File pdf = new File(outDir, "out.pdf");
		if (System.getProperty("foliojet.debug.fragTrace") != null) {
			System.err.println("[frag] 期待=" + expected.size() + " pdf=" + pdf + " 存在=" + pdf.isFile());
		}
		if (!pdf.isFile() || pdf.length() == 0) {
			return;
		}
		final java.util.Set<String> found = new java.util.LinkedHashSet<>();
		try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
			final org.apache.pdfbox.pdmodel.PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
			if (names == null || names.getDests() == null) {
				// No destinations = all destinations lost.
				fail("PDFの宛先が1つも無い(期待 " + expected.size() + " 件): " + html);
				return;
			}
			collectDestinationNamesRaw(names.getDests(), found);
		} catch (final java.io.IOException e) {
			// Do not check PDF readability here.
			return;
		}
		final java.util.List<String> lost = new ArrayList<>();
		for (final String id : expected) {
			if (!found.contains(id)) {
				lost.add(id);
			}
		}
		assertTrue("PDFの宛先が失われた " + lost + " / 期待" + expected.size() + "件 (" + html + ")", lost.isEmpty());
	}

	/** Recursively collect the destination name tree. */
	private static void collectDestinationNamesRaw(final org.apache.pdfbox.pdmodel.common.PDNameTreeNode<?> node,
			final java.util.Set<String> out) throws java.io.IOException {
		if (node.getNames() != null) {
			out.addAll(node.getNames().keySet());
		}
		if (node.getKids() != null) {
			for (final org.apache.pdfbox.pdmodel.common.PDNameTreeNode<?> kid : node.getKids()) {
				collectDestinationNamesRaw(kid, out);
			}
		}
	}

	/**
	 * Return the page where a token is first painted. Reconstruct tokens not only within a single text run,
	 * but also across multiple runs/pages split by {@code overflow-wrap:anywhere} and similar rules.
	 * Float or marker painting can interleave fragments, so skip only artifacts, ordered-list numeric markers,
	 * and "other complete fuzz tokens". Do not connect across ordinary body text; that hides real loss.
	 */
	private static int firstObservedTokenPage(final String token, final List<ObservedText> runs,
			final Set<String> expectedTokens) {
		// If a complete token exists somewhere, do not fabricate an earlier page by joining another token's
		// short prefix with digits (T1+1 => T11 in seed 5776).
		for (final ObservedText observed : runs) {
			if (!observed.artifact() && containsWholeToken(observed.text(), token)) {
				return observed.page();
			}
		}
		int bestPage = -1;
		int bestSpan = Integer.MAX_VALUE;
		int bestSkipped = Integer.MAX_VALUE;
		for (int i = 0; i < runs.size(); ++i) {
			if (runs.get(i).artifact()) {
				continue;
			}
			final String first = runs.get(i).text();
			for (int split = 1; split < token.length(); ++split) {
				if (!first.endsWith(token.substring(0, split))) {
					continue;
				}
				int matched = split;
				int skipped = 0;
				int lastPage = runs.get(i).page();
				for (int j = i + 1; j < runs.size() && matched < token.length(); ++j) {
					final ObservedText observed = runs.get(j);
					final String next = observed.text();
					// Do not join "T9" and the ordered-list marker "1." and mistake them for T91.
					// Markers are interruptions, not token fragments; always skip them.
					if (ORDERED_LIST_MARKER.matcher(next).matches()) {
						++skipped;
						continue;
					}
					final int take = Math.min(next.length(), token.length() - matched);
					if (take == 0 || !next.regionMatches(0, token, matched, take)) {
						if (observed.artifact() || isIgnorableFormattingText(next) || isGeneratedControlText(next)
								|| containsOnlyExpectedTokens(next, expectedTokens)) {
							++skipped;
							continue;
						}
						break;
					}
					matched += take;
					lastPage = observed.page();
					if (matched == token.length()) {
						final int page = runs.get(i).page();
						final int span = lastPage - page;
						if (span < bestSpan || span == bestSpan && skipped < bestSkipped
								|| span == bestSpan && skipped == bestSkipped && (bestPage < 0 || page < bestPage)) {
							bestPage = page;
							bestSpan = span;
							bestSkipped = skipped;
						}
						break;
					}
					if (take < next.length()) {
						break;
					}
				}
			}
		}
		return bestPage;
	}

	/** Exact-match search that does not count {@code T57} as a substring of {@code T575}. */
	private static boolean containsWholeToken(final String run, final String token) {
		int from = 0;
		while (from <= run.length() - token.length()) {
			final int at = run.indexOf(token, from);
			if (at < 0) {
				return false;
			}
			final int end = at + token.length();
			final boolean startsClean = at == 0 || !Character.isLetterOrDigit(run.charAt(at - 1));
			final boolean endsClean = end == run.length() || !Character.isDigit(run.charAt(end));
			if (startsClean && endsClean) {
				return true;
			}
			from = at + 1;
		}
		return false;
	}

	/** Whether an interleaved run consists only of another complete fuzz token. */
	private static boolean containsOnlyExpectedTokens(final String run, final Set<String> expectedTokens) {
		final Matcher matcher = TOKEN.matcher(run);
		int end = 0;
		boolean found = false;
		while (matcher.find()) {
			if (!run.substring(end, matcher.start()).isBlank() || !expectedTokens.contains(matcher.group())) {
				return false;
			}
			found = true;
			end = matcher.end();
		}
		return found && run.substring(end).isBlank();
	}

	/**
	 * Whether whitespace or Unicode format characters carry no textual content even between token fragments.
	 * Empty form controls may paint a zero-width space (U+200B); inserting one between {@code "T" + "417"}
	 * split across a page boundary is not content loss (extreme strict seed 3797).
	 * Never skip ordinary body-text characters.
	 */
	private static boolean isIgnorableFormattingText(final String run) {
		return run.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)
				|| Character.getType(c) == Character.FORMAT);
	}

	/** Whether this is a generator-specific form display interleaved between token fragments. */
	private static boolean isGeneratedControlText(final String run) {
		final StringBuilder visible = new StringBuilder(run.length());
		run.codePoints().filter(c -> Character.getType(c) != Character.FORMAT).forEach(visible::appendCodePoint);
		return GENERATED_CONTROL_TEXT.contains(visible.toString().strip());
	}

	/**
	 * Count tokens in one display-list text run.
	 *
	 * <p>
	 * Runs such as {@code Text["T0 T1 T2"]} <b>may contain multiple tokens</b>,
	 * so split on whitespace before counting. Never count partial matches:
	 * {@code T1} is a substring of {@code T10} (invariant 4's {@code contains}
	 * has this weakness; do not repeat it here).
	 * </p>
	 */
	private static void countTokens(final String run, final java.util.Map<String, int[]> drawn) {
		for (final String piece : run.split("[ \t]+")) {
			if (TOKEN.matcher(piece).matches()) {
				drawn.computeIfAbsent(piece, x -> new int[1])[0]++;
			}
		}
	}

	/** Shape of tokens embedded by the generator. */
	private static final Pattern TOKEN = Pattern.compile("T[0-9]+");

	/**
	 * <b>Invariant 8 (report-only for now)</b>: no content duplication (added 2026-07-28).
	 *
	 * <p>
	 * <b>The gap to fill.</b> None of the existing invariants catches "the same content is painted twice":
	 * no content is lost (4 passes), no blank pages appear (5 passes), and content is within the paper (6 passes).
	 * In the minimal case for seed 118665, nested multi-column layout replayed the same source range twice,
	 * producing {@code T0, T2, T3, T4, T3, T4} on page 2:
	 * <b>T3/T4 duplicated in separate columns</b>. Invariant 7 (reading order) caught this <b>by chance</b>;
	 * nothing checked duplication itself.
	 * </p>
	 *
	 * <p>
	 * <b>For forms, this is worse than reversal.</b> Output showing an amount row twice
	 * is among the worst kinds of silently corrupted output.
	 * </p>
	 *
	 * <p>
	 * Generator tokens are unique, so <b>correct output paints each token exactly once within a page</b>.
	 * If the generator starts producing repeated table headers ({@code thead}),
	 * exclude their tokens here: that repetition is valid.
	 * </p>
	 *
	 * <p>
	 * <b>Reformulated twice</b> (§6.9j). False positives in 50,000 documents:
	 * </p>
	 *
	 * <ol>
	 * <li>Naive "two or more across all pages" → <b>7,227 cases (14.5%)</b>.
	 * This counted drawing marked {@code artifact}. In tagged PDF, artifact is "drawing outside
	 * the logical structure", <b>intentionally</b> duplicated by the engine for rescue splitting, etc.</li>
	 * <li>Exclude artifacts but count across pages → <b>603 cases (1.2%)</b>. When a glyph <b>physically crosses</b>
	 * a page boundary, the same glyph is painted in parts on adjacent pages. This is valid.</li>
	 * <li>Count <b>within the same page</b> → <b>160 cases (0.32%)</b>. Inspected samples were true positives,
	 * caused by <b>double replay of nested multi-column layout</b>
	 * (minimal case: {@code column-count:2} within {@code column-count:2}).</li>
	 * </ol>
	 */
	private static void checkNoDuplication(final Generated doc, final java.util.Map<String, int[]> drawn,
			final File html) {
		final List<String> dup = new ArrayList<String>();
		for (final String token : doc.tokens()) {
			final int[] n = drawn.get(token);
			if (n != null && n[0] > 1) {
				dup.add(token + "×" + n[0]);
			}
		}
		if (!dup.isEmpty()) {
			throw new AssertionError("内容が複製された " + dup + " (" + html + ")");
		}
	}

	/**
	 * <b>Invariant 7 (report-only for now)</b>: content reading order is preserved (added 2026-07-27).
	 *
	 * <p>
	 * <b>The gap to fill.</b> Invariant 4 checks only that tokens appear <b>somewhere</b>,
	 * so {@code T5} painted before {@code T3} passes. Swapped table rows in a form are critical,
	 * yet no current detector catches this. Just as invariant 6 fills the gap "appears, but in an abnormal
	 * <b>location</b>", this fills "appears, but in an abnormal <b>order</b>".
	 * </p>
	 *
	 * <p>
	 * <b>A naive definition does not work.</b> Later-declared floats rise alongside earlier lines,
	 * and absolute positioning can place content anywhere, so requiring order for all tokens rejects
	 * valid documents wholesale (analogous to the naive "within paper" check's 356 false positives
	 * out of 3000 documents = 11.9% in §12). Have <b>the generator record</b> the distinction
	 * and exclude float/absolute subtrees. Keep multi-column layout, tables, and page breaks,
	 * since they simply flow content in order.
	 * </p>
	 *
	 * <p>
	 * <b>For repeated appearances, check only the first.</b> {@code seen} is a painting-order
	 * {@code LinkedHashSet}, so tokens appearing on multiple pages (e.g., if repeated {@code thead}
	 * is generated in future) are judged on their first appearance.
	 * </p>
	 *
	 * <p>
	 * <b>Measured before pinning down</b> (§6.9j). A report-only sweep of 20,000 documents
	 * yielded <b>12 cases</b>, in the intended range. Five individually inspected cases
	 * <b>all shared one mechanism</b> and were true positives. Other category counts matched
	 * the pre-introduction counts exactly; the generator's random sequence was undisturbed.
	 * </p>
	 *
	 * <p>
	 * <b>The first formulation was discarded</b> (attempt 1). Comparing painting order fired
	 * immediately on seed 0 because {@code rowspan} cells are "painted after the spanned rows are finalized":
	 * <b>a display list records painting order, not reading order</b>.
	 * The current formulation (attempt 2) uses page granularity.
	 * </p>
	 *
	 * <p>
	 * <b>Defect found</b>: split {@code rowspan} cell content appeared in the <b>continuation fragment</b>,
	 * not the head fragment. Seed 130 put the same row on two pages: {@code [ ][ ][T12]} on the preceding page
	 * and {@code [T10][T11][ ]} on the next. Frames appeared on both, but text on only one.
	 * All existing detectors missed this (tokens appear, no blank page, within the paper).
	 * </p>
	 */
	private static void checkReadingOrder(final Generated doc, final java.util.Map<String, Integer> firstPage,
			final File html) {
		// Table cells are parallel flows (2026-08-23): in a split table, each cell's content independently
		// divides into kept/moved portions at the same cut line, so page-order reversals between cells
		// and (in vertical tables) rows are valid. The v1 generator used one token per cell, mostly masking this,
		// but v2's complex cells exposed it frequently (seed 30: the 8 em wide cell's content
		// appeared on the next page, while the small adjacent cell stayed on the preceding page,
		// a correct layout matching Chrome). Therefore:
		// (a) In the overall scan, collapse "pages of tokens belonging to the outermost table" to
		//     the entire table's max page, then check monotonicity (still verify that content after the table
		//     does not move backward to a page in the middle of the table).
		// (b) The token sequence within each cell must be page-monotonic.
		// This relaxation loses cross-cell detection of "rowspan cell content appears in the continuation fragment"
		// (seed 130). If another detector is needed,
		// inspect the presence of cell content in each fragment directly.
		final java.util.Map<String, int[]> tokenTable = tokenTableAndCell(doc.html());
		final java.util.Map<Integer, Integer> tableMaxPage = new java.util.HashMap<>();
		for (final String t : doc.orderedTokens()) {
			final Integer at = firstPage.get(t);
			final int[] tc = tokenTable.get(t);
			if (at == null || tc == null) {
				continue;
			}
			tableMaxPage.merge(tc[0], at, Integer::max);
		}
		int prev = -1;
		String prevToken = null;
		final java.util.Map<Integer, Integer> cellPrev = new java.util.HashMap<>();
		for (final String t : doc.orderedTokens()) {
			final Integer at = firstPage.get(t);
			if (at == null) {
				continue; // Invariant 4 handles loss.
			}
			final int[] tc = tokenTable.get(t);
			final int effective = tc != null ? tableMaxPage.get(tc[0]).intValue() : at.intValue();
			if (effective < prev) {
				throw new AssertionError("読み順が入れ替わった: 文書順では" + prevToken + "→" + t + " だが、" + t
						+ "はページ" + (effective + 1) + "、" + prevToken + "はページ" + (prev + 1) + " (" + html + ")");
			}
			prev = effective;
			prevToken = t;
			if (tc != null) {
				// (b) Within-cell monotonicity.
				final Integer cp = cellPrev.get(tc[1]);
				if (cp != null && at.intValue() < cp.intValue()) {
					throw new AssertionError("読み順が入れ替わった(セル内): " + t + "はページ" + (at.intValue() + 1)
							+ "だが、同セルの先行トークンはページ" + (cp.intValue() + 1) + " (" + html + ")");
				}
				cellPrev.put(tc[1], at);
			}
		}
	}

	/**
	 * Each token's [outermost table id, cell id] (omit tokens outside tables).
	 * Derive structure from a simple HTML parse ({@link FuzzShrinker#parseBody}),
	 * providing the same source of truth for both generator and shrinker output.
	 */
	private static java.util.Map<String, int[]> tokenTableAndCell(final String html) {
		final java.util.Map<String, int[]> result = new java.util.HashMap<>();
		final int[] tableSeq = { 0 };
		final int[] cellSeq = { 0 };
		collectTableTokens(FuzzShrinker.parseBody(html), -1, -1, result, tableSeq, cellSeq);
		return result;
	}

	private static void collectTableTokens(final java.util.List<FuzzShrinker.Node> nodes, final int tableId,
			final int cellId, final java.util.Map<String, int[]> result, final int[] tableSeq, final int[] cellSeq) {
		for (final FuzzShrinker.Node n : nodes) {
			if (n.tag == null) {
				if (tableId >= 0 && n.text != null) {
					final java.util.regex.Matcher m = java.util.regex.Pattern.compile("T\\d+").matcher(n.text);
					// Direct bare text (equivalent to an anonymous item) forms its own branch.
					final int branch = m.find() ? cellSeq[0]++ : cellId;
					m.reset();
					while (m.find()) {
						result.put(m.group(), new int[] { tableId, branch });
					}
				}
				continue;
			}
			int nextTable = tableId;
			int nextCell = cellId;
			final String style = n.attr("style");
			final boolean parallelContainer = "table".equals(n.tag)
					|| (style != null && (style.contains("display:flex") || style.contains("display:grid")));
			if (parallelContainer && tableId < 0) {
				// Use only the outermost parallel container (table/flex/grid) as the unit.
				// (Nested parallelism is contained by the outer container.)
				nextTable = tableSeq[0]++;
			}
			if ("td".equals(n.tag) || "th".equals(n.tag)) {
				// New branch per cell (innermost cell for nesting).
				nextCell = cellSeq[0]++;
			} else if (tableId >= 0 && cellId < 0) {
				// Create a branch for each direct child of a parallel container (flex/grid items; intermediate
				// table elements such as tbody/tr are later overridden by td).
				nextCell = cellSeq[0]++;
			}
			collectTableTokens(n.children, nextTable, nextCell, result, tableSeq, cellSeq);
		}
	}

	/**
	 * <b>Invariant 6</b>: content does not extend beyond the paper by more than author declarations
	 * can explain (added 2026-07-26).
	 *
	 * <p>
	 * <b>Why "within paper" is insufficient.</b> CSS {@code overflow} defaults to {@code visible};
	 * <b>painting overflowing box content outside the paper is correct behavior</b>.
	 * The generator creates pathological documents, such as a 250 pt box on 60×60 pt paper.
	 * Naively requiring "within paper" flagged 356 of 3000 documents (11.9%), most of them valid.
	 * </p>
	 *
	 * <p>
	 * <b>Instead, ask "can the author's specified sizes explain it?"</b> Ignore overflow up to twice
	 * the document's largest explicit size as explainable by the declarations. This criterion left
	 * <b>no horizontal-writing cases, but 19 vertical-writing cases</b> (3000 documents). Details:
	 * </p>
	 *
	 * <p>
	 * <b>[Correction 2026-07-28] Inferring "an implementation defect specific to vertical writing"
	 * from this asymmetry was wrong. It was a false signal from the generator's vocabulary.</b>
	 * As case 2 below shows, the generator specifies <b>only {@code width}</b> on floats.
	 * In vertical writing, {@code width} is the page axis (defect occurs); in horizontal writing,
	 * it is the inline axis (no defect). The asymmetry depended on <b>which axis received explicit dimensions</b>,
	 * not writing direction. In fact, {@code float:right;height:0pt} in horizontal writing
	 * (= explicit page-axis size) reproduces the same defect.
	 * </p>
	 *
	 * <p>
	 * <b>Lesson</b>: when the generator emits only specific axes or attributes, that bias appears
	 * in sweep statistics as <b>a bias seemingly belonging to the implementation</b>.
	 * Before using "occurs in A but not B" as implementation evidence,
	 * verify <b>that the generator treats A and B symmetrically</b>.
	 * </p>
	 *
	 * <p>
	 * Check 4, "no content loss", checks only whether tokens <b>appear</b> in the display list,
	 * so it accepts content painted outside the paper. This fills that gap.
	 * </p>
	 */
	private static void assertNoUnexplainedOffPage(final Generated doc, final File[] pages, final File html)
			throws Exception {
		final double intrinsicControlSize = defaultTextControlWidth(doc.html());
		final double slack = 2 * Math.max(doc.maxExplicitSize(), intrinsicControlSize);
		double worst = 0;
		String worstAt = null;
		boolean worstIsY = false;
		for (final File page : pages) {
			final String dump = Files.readString(Path.of(page.toURI()), StandardCharsets.UTF_8);
			for (final String raw : dump.split("\n")) {
				// **Do not count painting marked artifact** (2026-07-29), analogous to the same exclusion
				// added to invariant 8 for the same reason on 2026-07-28.
				//
				// An indivisible box larger than the paper is represented by **translating the same box**
				// for drawing on each page. For a box spanning three pages,
				// its origin on page 3 is two pages **above** (left in vertical writing).
				// Those coordinates are not "off-paper placement", but **a correct representation
				// of a spanning box**.
				//
				// Measured (seed 422410, 611-byte minimal case): a 121.72 pt table occupies three pages
				// on 60 pt paper, with y=-60 on page 2 and
				// y=-120 on page 3. Both are marked artifact; counting them
				// reports "60 pt outside the paper".
				if (raw.contains(" artifact ")) {
					continue;
				}
				final Matcher m = DRAWING_GEOMETRY_IN_DUMP.matcher(raw);
				while (m.find()) {
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				final double width = Double.parseDouble(m.group(3)), height = Double.parseDouble(m.group(4));
				// Count only overflow by an entire sheet (a 1 pt edge excess can be disregarded;
				// strict checking there fluctuates with border rounding). Measure from the bounding rectangle's
				// nearest edge, not just its origin. Seed 473636's input field
				// starts at x=-70 but is 124 pt wide, overlapping 54 pt of the 60 pt paper.
				final double overX = distanceBeyondWholePage(x, width, doc.pageWidth());
				final double overY = distanceBeyondWholePage(y, height, doc.pageHeight());
				final double over = Math.max(overX, overY);
				if (over > worst) {
					worst = over;
					worstIsY = overY >= overX;
					worstAt = "x=" + x + " y=" + y + " w=" + width + " h=" + height + " "
							+ page.getName();
				}
				}
			}
		}
		if (worst <= slack) {
			return;
		}
		final String detail = "紙面外への配置 " + Math.round(worst) + "pt (紙面" + Math.round(doc.pageWidth()) + "x"
				+ Math.round(doc.pageHeight()) + "pt, 最大指定/UA既定サイズ"
				+ Math.round(Math.max(doc.maxExplicitSize(), intrinsicControlSize)) + "pt, " + worstAt
				+ ") (" + html + ")";
		// Apply the same exclusion criteria as blank pages (2026-07-26). Engine behavior cannot be judged
		// for documents with an unworkable type area.
		if (doc.beyondEngineControl()) {
			throw new ExcludedByOversizedBox(detail);
		}
		// Even if multiple conditions apply, consistently retain the narrower same-axis reverse-progression category.
		if (hasUntypesettableOppositeProgression(doc.html())) {
			throw new ExcludedByUntypesettableOppositeProgression(detail + " [同軸逆進行フローの組版不能幅]");
		}
		if (hasUntypesettableOrthogonalFlow(doc.html())) {
			throw new ExcludedByUntypesettableOrthogonalFlow(detail + " [直交フローの組版不能幅]");
		}
		// Also exclude overflow from floats with untypesettable widths (2026-07-29).
		// See {@link ExcludedByUntypesettableFloat} for the rationale.
		if (hasUntypesettableFloat(doc.html())) {
			throw new ExcludedByUntypesettableFloat(detail + " [組版できない幅の浮動体]");
		}
		if (hasFlexMulticolTable(doc.html())) {
			throw new ExcludedByFlexMulticolMinContent(detail + " [flex内の段組表によるmin-content幅]");
		}
		// Content that physically cannot fit in the type area (user decision on 2026-09-17;
		// see {@link ExcludedByUnfittableContent} for the rationale).
		final String unfittable = findUnfittableContent(doc.html());
		if (unfittable != null) {
			throw new ExcludedByUnfittableContent(detail + " [" + unfittable + "]", unfittable);
		}
		// Also exclude orthogonal flows overflowing the parent's **inline axis** (user decision
		// on 2026-07-28; see {@link ExcludedByOrthogonalLineAxis} for the rationale).
		// **Do not exclude page-axis overflow**: page breaks can fix it,
		// so failure to do so is an engine defect. Losing this distinction
		// would ignore every overflow in documents containing orthogonal flows.
		final boolean overflowInLineAxis = worstIsY != pageAxisIsY(doc.html());
		if (overflowInLineAxis && hasOrthogonalFlow(doc.html())) {
			throw new ExcludedByOrthogonalLineAxis(detail + " [直交フローの行軸]");
		}
		// Also exclude documents with three or more nested orthogonal-flow levels (user decision
		// on 2026-07-30; see {@link ExcludedByNestedOrthogonalFlow} for the rationale).
		// **Do not exclude two levels**: off-paper placement in ordinary horizontal writing within vertical writing
		// is a real defect.
		if (orthogonalAxisChanges(doc.html()) >= 2) {
			throw new ExcludedByNestedOrthogonalFlow(detail + " [直交フロー3段以上]");
		}
		fail(detail);
	}

	/**
	 * Whether document-level predicates exempt a document from off-paper checks (excluding the inline-axis
	 * orthogonal-flow exclusion, which depends on overflow direction).
	 * Same predicate order as {@link #assertNoUnexplainedOffPage} exclusions (2026-10-07, for active-check rate totals).
	 */
	static boolean offPageCheckExcused(final Generated doc) {
		return doc.beyondEngineControl() || hasUntypesettableOppositeProgression(doc.html())
				|| hasUntypesettableOrthogonalFlow(doc.html()) || hasUntypesettableFloat(doc.html())
				|| hasFlexMulticolTable(doc.html()) || findUnfittableContent(doc.html()) != null
				|| orthogonalAxisChanges(doc.html()) >= 2;
	}

	/** Distance by which the entire bounding rectangle lies a further sheet's length beyond the paper. At most zero is allowed. */
	static double distanceBeyondWholePage(final double origin, final double extent, final double pageExtent) {
		return Math.max(-(origin + extent) - pageExtent, origin - 2 * pageExtent);
	}

	private static final Pattern TEXT_CONTROL_TAG = Pattern.compile("<(input|textarea)\\b([^>]*)>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern INPUT_TYPE = Pattern.compile("\\btype\\s*=\\s*[\"']?([a-z]+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern INPUT_SIZE_VALUE = Pattern.compile("\\bsize\\s*=\\s*[\"']?(\\d+)",
			Pattern.CASE_INSENSITIVE);
	/** Test UA CSS form controls use 12 pt. 1 ex = 6 pt; left/right frame total 4 pt (same formula as default 20 ex = 124 pt). */
	private static final double TEXT_CONTROL_EX_PT = 6;
	private static final double TEXT_CONTROL_FRAME_PT = 4;
	/** Actual default single-line input/textarea size: test UA CSS 20 ex plus the frame. */
	private static final double DEFAULT_TEXT_CONTROL_WIDTH_PT = 124;

	/**
	 * UA default width of a single-line input or textarea without a specified width.
	 *
	 * <p>
	 * {@code html-ua.css} sets them to 20ex. With the fixed test font, this is 124 pt including the frame,
	 * directly explaining negative coordinates and subsequent inline positions in seeds 473636/526411/651439.
	 * Do not extend this to checkbox/radio, etc. For inputs with {@code size}, use the actual specified
	 * {@code size}, not the default width; return the largest in the document (2026-09-17).
	 * </p>
	 */
	static double defaultTextControlWidth(final String html) {
		double widest = 0;
		final Matcher tag = TEXT_CONTROL_TAG.matcher(html);
		while (tag.find()) {
			if (tag.group(1).equalsIgnoreCase("textarea")) {
				widest = Math.max(widest, DEFAULT_TEXT_CONTROL_WIDTH_PT);
				continue;
			}
			final String attrs = tag.group(2);
			final Matcher typeMatcher = INPUT_TYPE.matcher(attrs);
			if (typeMatcher.find() && Set.of("button", "submit", "reset", "checkbox", "radio", "image", "hidden")
					.contains(typeMatcher.group(1).toLowerCase(java.util.Locale.ROOT))) {
				continue;
			}
			// A single-line input with size uses size×1 ex (6 pt) + 4 pt frame. Generator v2 assigns size=1–20, so
			// skipping these as before misses an "author-specified size" (2026-09-17, seed 4608881:
			// a size=16=100 pt input does not rotate even in vertical writing, creating a 100 pt thick line on 60 pt wide paper).
			final Matcher size = INPUT_SIZE_VALUE.matcher(attrs);
			widest = Math.max(widest, size.find() ? Integer.parseInt(size.group(1)) * TEXT_CONTROL_EX_PT
					+ TEXT_CONTROL_FRAME_PT : DEFAULT_TEXT_CONTROL_WIDTH_PT);
		}
		return widest;
	}

	/**
	 * <b>Invariant 10</b>: at least one drawing containing a document token intersects the actual paper.
	 *
	 * <p>
	 * Invariant 6 permits small overflow explained by explicit author dimensions. Thus, even if every token
	 * lay just outside the paper, tokens passed the loss check and stayed below the off-paper threshold.
	 * Regardless of magnitude, this check separately catches only cases where <b>all content is invisible</b>.
	 * </p>
	 *
	 * <p>
	 * Do not judge by coordinate points alone. Valid drawing may have an off-paper origin while its glyph
	 * rectangle overlaps the paper. Use rectangle intersection with widths/heights added to the detailed dump
	 * only during sweeps.
	 * </p>
	 */
	static void assertSomeDrawingOnPage(final Generated doc, final File[] pages, final File html)
			throws Exception {
		double nearest = Double.POSITIVE_INFINITY;
		boolean nearestIsY = false;
		String nearestAt = null;
		for (final File page : pages) {
			final String dump = Files.readString(Path.of(page.toURI()), StandardCharsets.UTF_8);
			for (final String raw : dump.split("\n")) {
				final Matcher m = DRAWING_GEOMETRY_IN_DUMP.matcher(raw);
				if (!m.find()) {
					continue;
				}
				final double x = Double.parseDouble(m.group(1));
				final double y = Double.parseDouble(m.group(2));
				final double width = Double.parseDouble(m.group(3) != null ? m.group(3) : m.group(5));
				final double height = Double.parseDouble(m.group(4) != null ? m.group(4) : m.group(6));
				if (rectangleIntersectsPage(x, y, width, height, doc.pageWidth(), doc.pageHeight())) {
					return;
				}
				final double dx = Math.max(Math.max(-x - width, x - doc.pageWidth()), 0);
				final double dy = Math.max(Math.max(-y - height, y - doc.pageHeight()), 0);
				final double distance = Math.hypot(dx, dy);
				if (distance < nearest) {
					nearest = distance;
					nearestIsY = dy >= dx;
					nearestAt = "x=" + x + " y=" + y + " w=" + width + " h=" + height + " "
							+ page.getName();
				}
			}
		}
		final String detail = "全描画が紙面外 (紙面" + Math.round(doc.pageWidth()) + "x"
				+ Math.round(doc.pageHeight()) + "pt, 最短=" + Math.round(nearest) + "pt, " + nearestAt + ") (" + html
				+ ")";
		if (doc.beyondEngineControl()) {
			throw new ExcludedByOversizedBox(detail);
		}
		// Even if multiple conditions apply, consistently retain the narrower same-axis reverse-progression category.
		if (hasUntypesettableOppositeProgression(doc.html())) {
			throw new ExcludedByUntypesettableOppositeProgression(detail + " [同軸逆進行フローの組版不能幅]");
		}
		if (hasUntypesettableOrthogonalFlow(doc.html())) {
			throw new ExcludedByUntypesettableOrthogonalFlow(detail + " [直交フローの組版不能幅]");
		}
		if (hasUntypesettableFloat(doc.html())) {
			throw new ExcludedByUntypesettableFloat(detail + " [組版できない幅の浮動体]");
		}
		if (hasFlexMulticolTable(doc.html())) {
			throw new ExcludedByFlexMulticolMinContent(detail + " [flex内の段組表によるmin-content幅]");
		}
		// Content that physically cannot fit in the type area (user decision on 2026-09-17;
		// see {@link ExcludedByUnfittableContent} for the rationale).
		final String unfittable = findUnfittableContent(doc.html());
		if (unfittable != null) {
			throw new ExcludedByUnfittableContent(detail + " [" + unfittable + "]", unfittable);
		}
		final boolean outsideInLineAxis = nearestIsY != pageAxisIsY(doc.html());
		if (outsideInLineAxis && hasOrthogonalFlow(doc.html())) {
			throw new ExcludedByOrthogonalLineAxis(detail + " [直交フローの行軸]");
		}
		if (orthogonalAxisChanges(doc.html()) >= 2) {
			throw new ExcludedByNestedOrthogonalFlow(detail + " [直交フロー3段以上]");
		}
		fail(detail);
	}

	/** Whether two positive-area rectangles intersect the paper. Mere boundary contact does not count. */
	static boolean rectangleIntersectsPage(final double x, final double y, final double width, final double height,
			final double pageWidth, final double pageHeight) {
		return width > 0 && height > 0 && x < pageWidth && y < pageHeight && x + width > 0 && y + height > 0;
	}

	private static void convert(final File html, final File outDir) throws Exception {
		convert(html, outDir, new DirectSession[1], MAX_PAGES);
	}

	/**
	 * @param sessionOut write the created session here so the watchdog can request an abort.
	 *                   <b>Actually stop it rather than leave it running</b> (2026-07-27):
	 *                   an unstoppable thread retains one layout's heap and a 64 MB stack reservation,
	 *                   causing the sweep to stall through self-amplification
	 */
	private static void convert(final File html, final File outDir, final DirectSession[] sessionOut,
			final int pageLimit)
			throws Exception {
		// Output destinations are per-thread. System properties are shared process-wide,
		// so parallel sweeps would overwrite each other's dump destinations (2026-07-26).
		try (AutoCloseable scope = DisplayListDumper.scopedDir(outDir.getPath());
				AutoCloseable geometry = DisplayListDumper.scopedDetailedGeometry(true)) {
			final File pdf = new File(outDir, "out.pdf");
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				final AtomicBoolean pageLimitExceeded = new AtomicBoolean();
				sessionOut[0] = session;
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					// Printing per-page INFO to stderr during million-document sweeps lets
					// I/O dominate measurement time and log size. Catch only the page limit with a typed marker;
					// print all messages only when needed with -Dfoliojet.fuzzMessages.
					session.setMessageHandler((code, args, mes) -> {
						if (code == net.zamasoft.foliojet.message.MessageCodes.ERROR_OUT_OF_PAGE_LIMIT) {
							pageLimitExceeded.set(true);
						}
						if (System.getProperty("foliojet.fuzzMessages") != null) {
							System.err.println(mes);
						}
					});
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					// Stop conversion itself as soon as it exceeds the oracle's maximum page count.
					// Previously, counting files only after completion let page explosions
					// generate thousands of pages, trigger the watchdog, leak workers, and make the shrinker
					// take 30 seconds per candidate (seed 7662). Stopping at limit+1 pages
					// contains only abnormal inputs earlier without changing invariant 3's meaning.
					session.property("output.page-limit", String.valueOf(pageLimit));
					// Named destinations (id fragments) are output with `output.pdf.hyperlinks.fragment`
					// on by default, so no explicit setting is needed here (confirmed on 2026-08-03;
					// invariant 9 = checkFragments reads them).
					try {
						CTISessionHelper.transcodeFile(session, html, "text/html", null);
					} catch (final jp.cssj.cti2.TranscoderException e) {
						if (pageLimitExceeded.get()) {
							throw new PageCountLimitExceeded(pageLimit, e);
						}
						throw e;
					}
				} finally {
					session.close();
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Generator
	// ------------------------------------------------------------------

	/**
	 * @param pageWidth       paper width (pt)
	 * @param pageHeight      paper height (pt)
	 * @param maxExplicitSize largest specified {@code width}/{@code height} in this document (pt).
	 *                        Used by invariant 6 to decide whether overflow is explainable
	 *                        as a consequence of author-specified sizes
	 */
	record Generated(String html, List<String> tokens, Set<String> reorderable, double pageWidth,
			double pageHeight, double maxExplicitSize, boolean oversized, boolean tinyPage) {

		/** Whether the document has an unworkable type area that prevents judging engine behavior. */
		boolean beyondEngineControl() {
			return this.oversized || this.tinyPage;
		}

		/**
		 * Return tokens whose reading order must be preserved <b>in document order</b>.
		 *
		 * <p>
		 * Exclude float and absolute-positioning subtrees: both <b>legitimately</b> change reading order.
		 * Keep multi-column layout, tables, and page breaks, since they simply flow content in order.
		 * </p>
		 */
		List<String> orderedTokens() {
			final List<String> ordered = new ArrayList<String>();
			for (final String t : this.tokens) {
				if (!this.reorderable.contains(t)) {
					ordered.add(t);
				}
			}
			return ordered;
		}
	}

	/** Explicit sizes emitted by the generator (e.g., {@code width:120pt}). */
	static final Pattern EXPLICIT_SIZE = Pattern.compile("(?:width|height):(\\d+)pt");

	private static final Pattern EXPLICIT_WIDTH = Pattern.compile("width:(\\d+)pt");
	private static final Pattern EXPLICIT_HEIGHT = Pattern.compile("height:(\\d+)pt");
	private static final Pattern FONT_SIZE = Pattern.compile("(?:font-size:|font:normal )(\\d+)pt");
	private static final Pattern PAGE_MARGIN = Pattern.compile("@page\\{margin:(\\d+)pt");
	private static final Pattern PAGE_WIDTH_PROPERTY = Pattern
			.compile("name=\"output\\.page-width\"\\s+value=\"([\\d.]+)pt\"");

	/**
	 * <b>Absolute URI</b> of the image referenced by generated documents (2026-07-27).
	 *
	 * <p>
	 * <b>Never use a relative path.</b> Previously, embedding {@code ../../files/unittest/red.png}
	 * meant the image resolved <b>only when the document was under `local/fuzz/`</b>.
	 * Copying it elsewhere for reduction or reproduction <b>silently removed the image, changing the document</b>.
	 * The failure no longer reproduced, leading to the false conclusion "behavior changes with the path"
	 * (actually encountered on 2026-07-27).
	 * </p>
	 *
	 * <p>
	 * Image presence changes page count (measured: 2 pages ↔ 3 pages), so this is
	 * <b>a prerequisite for reproducibility itself</b>. {@code EnduranceTest} has the same fragility
	 * (hard-coded {@code ../../../}).
	 * </p>
	 */
	/**
	 * Override the image location with {@code -Dfoliojet.fuzzImage} (2026-07-29).
	 *
	 * <p>
	 * The default {@code files/unittest/red.png} is on {@code /mnt/f} (DrvFs).
	 * <b>Opening and decoding it for every document</b> became a major sweep cost
	 * (thread dumps showed most active layout threads in {@code PNGImageReader.readMetadata}).
	 * Copying it to tmpfs and pointing here substantially reduces that cost.
	 * </p>
	 *
	 * <p>
	 * <b>Reproducibility is preserved.</b> Failures are reproduced from <b>seed numbers</b>
	 * ({@code -Dfoliojet.fuzzOnlySeed}), not saved HTML, so a valid image path at generation time suffices.
	 * The reason for absolute URIs remains unchanged: relative paths change the document with its location.
	 * </p>
	 */
	/** Default sweep image (reference for dimension checks when substituting it). */
	private static final String DEFAULT_FUZZ_IMAGE = "files/unittest/red.png";

	private static final String RED_PNG_URI = new File(
			System.getProperty("foliojet.fuzzImage", DEFAULT_FUZZ_IMAGE)).getAbsoluteFile().toURI().toString();

	/**
	 * Sweep working directory ({@code -Dfoliojet.fuzzWorkDir}). Defaults to {@code local/fuzz}.
	 *
	 * <p>
	 * <b>Sweeps are I/O-bound, not CPU-bound</b> (measured on 2026-07-28).
	 * Each document writes HTML, display-list dumps, and a PDF. When default {@code local/}
	 * is on {@code /mnt/f} (WSL DrvFs = Windows filesystem), even 12 threads consume only
	 * 1.2 times elapsed time in worker CPU time; the rest accumulates in I/O wait.
	 * Using tmpfs ({@code /dev/shm}) or WSL ext4 (under {@code /}) makes a major difference.
	 * </p>
	 *
	 * <p>
	 * <b>Failure reproducibility is preserved.</b> The generator is deterministic, so knowing the seed
	 * lets {@code -Dfoliojet.fuzzOnlySeed} regenerate the same document anytime.
	 * Image references already use absolute URIs (see {@link #RED_PNG_URI} below),
	 * so relocating the working directory leaves the document unchanged.
	 * </p>
	 */
	static File workDir() {
		final File dir = new File(System.getProperty("foliojet.fuzzWorkDir", "local/fuzz"));
		dir.mkdirs();
		return dir;
	}

	/** Candidate page dimensions (including extremely small ones). */
	private static final int[][] PAGE_SIZES = { { 200, 200 }, { 300, 150 }, { 120, 400 }, { 595, 842 }, { 60, 60 } };

	private static final String[] WRITING_MODES = { "horizontal-tb", "vertical-rl", "vertical-lr" };

	static Generated generate(final int seed, final boolean strict) {
		return generate(seed, strict, false);
	}

	/**
	 * @param legacyV1 generate using the v1 input distribution (disable all extensions).
	 *                 Fixed-seed regression expectations were recorded for v1 documents,
	 *                 so use this for historical seed checks (2026-08-23)
	 */
	static Generated generate(final int seed, final boolean strict, final boolean legacyV1) {
		if (!legacyV1 && fitProfile()) {
			generatorProfile(); // Reject combining with extreme.
			return generateFit(seed, strict);
		}
		return generate(seed, strict, legacyV1, !legacyV1 && extremeProfile());
	}

	/**
	 * fit-v1: v2 documents whose off-paper checks are not exempted at document level ({@link #FIT_PROFILE_VERSION}).
	 * The first attempt uses the original seed (same document if the standard one already qualifies);
	 * later attempts use derived seeds. If none qualifies, use the last document.
	 */
	static Generated generateFit(final int seed, final boolean strict) {
		Generated doc = null;
		for (int k = 0; k < FIT_TRIES; ++k) {
			doc = generate(fitSeed(seed, k), strict, false, false);
			if (!offPageCheckExcused(doc)) {
				return doc;
			}
		}
		return doc;
	}

	/** Seed for fit attempt k (deterministic; k=0 is the original seed). */
	static int fitSeed(final int seed, final int k) {
		if (k == 0) {
			return seed;
		}
		long z = (seed & 0xFFFF_FFFFL) * 0x9E37_79B9_7F4A_7C15L + k * 0xBF58_476D_1CE4_E5B9L;
		z ^= z >>> 31;
		return (int) (z & 0x7FFF_FFFF);
	}

	/**
	 * @param extreme add constrained dense extreme-v1 scenarios to the v2 body.
	 *                An independent random sequence leaves v2 documents completely unchanged when false.
	 */
	static Generated generate(final int seed, final boolean strict, final boolean legacyV1,
			final boolean extreme) {
		final long randomSeed = seed * 7919L + (strict ? 1 : 2);
		final Random r = new Random(randomSeed);
		// Preserve v1 random-consumption order. Added features consume only this independent sequence.
		// With legacyV1, null means all extension gates stay inactive.
		final Random extensionRandom = legacyV1 ? null
				: new Random(randomSeed ^ 0x6A09E667F3BCC909L
						^ ((long) GENERATOR_VERSION << 32));
		// extreme uses a third sequence, preserving v2 random-consumption order. Mix the profile version into the seed
		// so a future extreme-v2 cannot silently change the meaning of the same seed.
		final Random extremeRandom = extreme
				? new Random(randomSeed ^ 0xBB67AE8584CAA73BL
						^ ((long) EXTREME_PROFILE_VERSION << 40))
				: null;
		final List<String> tokens = new ArrayList<>();
		// Tokens that may legitimately reorder (inside float/absolute-positioning subtrees).
		// Have **the side that knows** record them, rather than inferring them afterward from display lists.
		final Set<String> reorderable = new LinkedHashSet<String>();
		final StringBuilder body = new StringBuilder();
		final int[] counter = { 0 };
		// **Use feature count per document as a metric** (2026-08-02, user feedback).
		// A document with k features covers C(k,t) combinations of t features at once;
		// density therefore matters to degree t. No number of "many small documents"
		// covers triples or quadruples.
		// Pack 3/4 of documents with one-and-a-half to two rounds of every type; leave 1/4 small
		// to keep diagnosis fast when defects occur.
		final boolean dense = r.nextInt(4) != 0;
		if (dense) {
			final int kinds = nodeKinds(strict);
			final int[] order = new int[kinds];
			for (int i = 0; i < kinds; ++i) {
				order[i] = i;
			}
			for (int i = kinds - 1; i > 0; --i) {
				final int j = r.nextInt(i + 1);
				final int t = order[i];
				order[i] = order[j];
				order[j] = t;
			}
			final int nodes = kinds + kinds / 2 + r.nextInt(kinds);
			for (int i = 0; i < nodes; ++i) {
				appendNode(body, r, extensionRandom, 3, strict, tokens, counter, reorderable, false,
						order[i % kinds]);
			}
		} else {
			final int roots = 1 + r.nextInt(4);
			for (int i = 0; i < roots; ++i) {
				appendNode(body, r, extensionRandom, 3, strict, tokens, counter, reorderable, false);
			}
		}
		if (extremeRandom != null) {
			appendExtremeDocument(body, extremeRandom, strict, tokens, counter, reorderable);
		}
		final int[] size = PAGE_SIZES[r.nextInt(PAGE_SIZES.length)];
		final StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		s.append("<?jp.cssj.property name=\"output.page-width\" value=\"").append(size[0]).append("pt\"?>\n");
		s.append("<?jp.cssj.property name=\"output.page-height\" value=\"").append(size[1]).append("pt\"?>\n");
		s.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
		s.append("<title>fuzz v").append(GENERATOR_VERSION);
		if (extreme) {
			s.append("/extreme-v").append(EXTREME_PROFILE_VERSION);
		}
		s.append(' ').append(seed).append("</title>\n")
				.append("<meta name=\"foliojet-fuzz-generator\" content=\"").append(GENERATOR_VERSION)
				.append("\" />\n");
		if (extreme) {
			s.append("<meta name=\"foliojet-fuzz-profile\" content=\"extreme-v")
					.append(EXTREME_PROFILE_VERSION).append("\" />\n");
		}
		s.append("<style>\n");
		s.append("@page{margin:").append(r.nextInt(3) * 5).append("pt}\n");
		s.append("body{margin:0;font:normal ").append(6 + r.nextInt(8)).append("pt/1.2 serif;writing-mode:")
				.append(WRITING_MODES[r.nextInt(WRITING_MODES.length)]).append("}\n");
		s.append("p,div,td{margin:0;padding:0}\n");
		s.append("table{border-collapse:").append(r.nextBoolean() ? "collapse" : "separate")
				.append(";table-layout:").append(r.nextBoolean() ? "auto" : "fixed").append("}\n");
		s.append("td{border:1pt solid black}\n");
		s.append("</style></head><body data-fuzz-generator=\"").append(GENERATOR_VERSION).append('"');
		if (extreme) {
			s.append(" data-fuzz-profile=\"extreme-v").append(EXTREME_PROFILE_VERSION).append('"');
		}
		s.append(">\n");
		s.append(body);
		s.append("\n</body></html>\n");
		final String out = s.toString();
		// Largest explicit size emitted by the generator. Generation sites are scattered, so
		// extracting it from the finished document misses less than passing it through arguments.
		double maxExplicit = 0;
		final Matcher em = EXPLICIT_SIZE.matcher(out);
		while (em.find()) {
			maxExplicit = Math.max(maxExplicit, Double.parseDouble(em.group(1)));
		}
		return new Generated(out, tokens, reorderable, size[0], size[1], maxExplicit, isOversized(out, size),
				isTinyPage(out, size));
	}

	/**
	 * Constrained extreme-v1 scenarios. Simply increasing random depth grows the DOM exponentially,
	 * producing a useless distribution of almost all watchdog/OOM cases. Instead, incorporate past
	 * structural gaps in bounded form into every document. A random sequence independent of the v2 body
	 * preserves seed reproducibility for the standard profile.
	 */
	private static void appendExtremeDocument(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<div data-fuzz-role=\"extreme-document\" style=\"min-width:0;max-width:100%\">\n");
		appendExtremeTable(s, r, strict, tokens, counter, reorderable);
		appendExtremeLayout(s, r, strict, tokens, counter, reorderable);
		appendExtremeList(s, r, strict, tokens, counter, reorderable);
		appendExtremeText(s, r, tokens, counter, reorderable);
		appendExtremeFragmentation(s, r, strict, tokens, counter, reorderable);
		if (!strict) {
			appendExtremeWildBreak(s, r, tokens, counter, reorderable);
		}
		s.append("</div>\n");
	}

	/** Table with thead/tbody/tfoot and multiple complex cells, preserving valid spans. */
	private static void appendExtremeTable(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<table data-fuzz-role=\"extreme-table\" style=\"width:90%;min-width:8em;max-width:100%;")
				.append("break-inside:auto\">\n<caption>")
				.append(token(tokens, counter, reorderable, false)).append("</caption>\n")
				.append("<colgroup><col style=\"width:22%\" /><col span=\"2\" style=\"width:24%\" />")
				.append("<col style=\"width:30%\" /></colgroup>\n<thead><tr>");
		for (int i = 0; i < 4; ++i) {
			s.append("<th>").append(token(tokens, counter, reorderable, false)).append("</th>");
		}
		s.append("</tr></thead>\n<tbody>\n<tr><td rowspan=\"2\">")
				.append(token(tokens, counter, reorderable, false));
		appendRichCellChild(s, r, 3, strict, tokens, counter, reorderable, false);
		s.append("</td><td colspan=\"2\">").append(token(tokens, counter, reorderable, false));
		appendLayoutContainer(s, r, 3, strict, tokens, counter, reorderable, false, r.nextBoolean());
		s.append("</td><td>").append(token(tokens, counter, reorderable, false)).append("</td></tr>\n")
				.append("<tr><td>").append(token(tokens, counter, reorderable, false)).append("</td><td>")
				.append(token(tokens, counter, reorderable, false)).append("</td><td>")
				.append(token(tokens, counter, reorderable, false)).append("</td></tr>\n");
		for (int row = 0; row < 2; ++row) {
			s.append("<tr>");
			for (int col = 0; col < 4; ++col) {
				s.append("<td>").append(token(tokens, counter, reorderable, false));
				if (row == 0 && col == 2) {
					appendRichCellChild(s, r, 2, strict, tokens, counter, reorderable, false);
				}
				s.append("</td>");
			}
			s.append("</tr>\n");
		}
		s.append("</tbody>\n<tfoot><tr><td colspan=\"4\">")
				.append(token(tokens, counter, reorderable, false))
				.append("</td></tr></tfoot>\n</table>\n");
	}

	/** Nest a six-item flex and explicitly placed grid to always exercise width negotiation, wrapping, and order. */
	private static void appendExtremeLayout(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		final boolean reversed = !strict && r.nextBoolean();
		s.append("<div data-fuzz-role=\"extreme-layout\" style=\"display:flex;flex-direction:")
				.append(reversed ? "row-reverse" : "row")
				.append(";flex-wrap:wrap;align-items:stretch;align-content:space-between;")
				.append("justify-content:space-around;gap:calc(1pt + .25em);min-width:0;max-width:100%\">\n");
		for (int item = 0; item < 6; ++item) {
			s.append("<div data-fuzz-role=\"extreme-flex-item\" style=\"order:").append((item * 5) % 7)
					.append(";flex:").append(item % 3).append(' ').append(1 + item % 2)
					.append(" calc(18% + ").append(item).append("pt);min-width:0;align-self:")
					.append(item % 2 == 0 ? "stretch" : "center").append("\">\n")
					.append("<div style=\"display:grid;grid-template-columns:repeat(3,minmax(0,1fr));")
					.append("grid-auto-flow:dense;gap:.25em;align-items:center;justify-content:space-between\">\n");
			for (int grid = 0; grid < 4; ++grid) {
				s.append("<div data-fuzz-role=\"extreme-grid-item\" style=\"grid-column:")
						.append(grid == 0 ? "span 2" : String.valueOf(1 + grid % 3))
						.append(";grid-row:span 1;min-width:0\">\n");
				appendNode(s, r, r, 3, strict, tokens, counter, reorderable, reversed,
						(item + grid) % nodeKinds(strict));
				s.append("</div>\n");
			}
			s.append("</div></div>\n");
		}
		s.append("</div>\n");
	}

	/** Combine ruby, images, forms, and multi-column layout within list items. */
	private static void appendExtremeList(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<ol data-fuzz-role=\"extreme-list\" style=\"list-style-position:outside;break-inside:auto\">\n")
				.append("<li><ruby>").append(token(tokens, counter, reorderable, false)).append("<rt>")
				.append(token(tokens, counter, reorderable, false)).append("</rt></ruby> ");
		for (int i = 0; i < 6; ++i) {
			s.append("<ruby>").append(token(tokens, counter, reorderable, false)).append("<rt>")
					.append(token(tokens, counter, reorderable, false)).append("</rt></ruby> ");
		}
		s.append("</li>\n<li><span>").append(token(tokens, counter, reorderable, false))
				.append("</span><img src=\"").append(RED_PNG_URI)
				.append("\" alt=\"img\" style=\"display:inline;width:3em;height:2em\" /><span>")
				.append(token(tokens, counter, reorderable, false)).append("</span></li>\n")
				.append("<li><form style=\"display:grid;grid-template-columns:1fr 1fr;gap:2pt\">")
				.append("<input type=\"text\" value=\"mixed\" size=\"8\" />")
				.append("<textarea rows=\"2\">日本語 العربية</textarea><select><option>甲</option>")
				.append("<option>乙</option></select><button type=\"button\">")
				.append(token(tokens, counter, reorderable, false)).append("</button></form></li>\n<li>");
		appendPlainNode(s, r, r, 4, strict, tokens, counter, reorderable, false, 4);
		s.append("</li>\n</ol>\n");
	}

	/** Place long words, consecutive spaces, CJK, and RTL/bidi on the same lines as many tracking tokens. */
	private static void appendExtremeText(final StringBuilder s, final Random r,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<div data-fuzz-role=\"extreme-text\" style=\"min-width:0;max-width:100%;")
				.append("overflow-wrap:anywhere;word-break:normal;widows:4;orphans:4\">\n<p id=\"p")
				.append(counter[0]).append("\">");
		for (int i = 0; i < 64; ++i) {
			s.append(token(tokens, counter, reorderable, false)).append(' ');
			s.append(switch (i % 5) {
			case 0 -> "日本語の禁則、句読点。 ";
			case 1 -> "Latin-hyphenation/fragmentation ";
			case 2 -> "<span dir=\"rtl\" style=\"unicode-bidi:isolate\">العربية עברית</span> ";
			case 3 -> "漢字かな交じり文　連続　全角空白 ";
			default -> "emoji&#x1F642;&#x1F469;&#x200D;&#x1F4BB; ";
			});
		}
		s.append("<span class=\"fuzzUnbreakable\">Supercalifragilisticexpialidocious")
				.append("_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz</span></p>\n")
				.append("<p style=\"white-space:pre-wrap\">")
				.append(token(tokens, counter, reorderable, false))
				.append("    spaces\nline-break\t tab ")
				.append(token(tokens, counter, reorderable, false)).append("</p></div>\n");
	}

	/** Build five bounded branches covering column balance, avoid, and deeply nested cut boundaries. */
	private static void appendExtremeFragmentation(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<div data-fuzz-role=\"extreme-fragmentation\" style=\"column-count:3;column-fill:balance;")
				.append("column-gap:calc(1pt + .5em);max-width:100%\">\n");
		final int[] kinds = { 11, 5, 3, 8, 1 };
		for (int i = 0; i < kinds.length; ++i) {
			s.append("<div style=\"page-break-inside:avoid;break-inside:avoid-column;min-height:")
					.append(2 + i).append("em;max-height:none\">\n");
			appendPlainNode(s, r, r, 4, strict, tokens, counter, reorderable, false, kinds[i]);
			s.append("</div>\n");
		}
		s.append("</div>\n");
	}

	/** Only in WILD, add complexity inside forced page breaks, hidden content, and overflow. */
	private static void appendExtremeWildBreak(final StringBuilder s, final Random r,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<div data-fuzz-role=\"extreme-wild-break\" style=\"page-break-before:always;")
				.append("overflow:hidden;visibility:visible;max-height:80vh\">\n");
		for (int i = 0; i < 6; ++i) {
			appendNode(s, r, r, 4, false, tokens, counter, reorderable, false, i % nodeKinds(false));
		}
		s.append("<div style=\"visibility:hidden\">");
		appendLayoutContainer(s, r, 3, false, tokens, counter, reorderable, true, true);
		s.append("</div></div>\n");
	}

	/**
	 * Return whether the document contains <b>boxes that cannot fit in the paper's content area</b>
	 * (added 2026-07-26).
	 *
	 * <p>
	 * <b>Exclude documents placing content that cannot fit from blank-page checks.</b>
	 * Their layout is unworkable regardless of engine behavior (overflow or moving to the next page);
	 * correcting dimensions is the layout author's responsibility: user decision on 2026-07-26.
	 * </p>
	 *
	 * <p>
	 * Judge only from generator-emitted values. Measurements showed <b>28 of 33 documents</b>
	 * producing blank pages met this condition. Of the five remaining, two used 50×50 pt paper
	 * (a 1.7 cm square, smaller than a postage stamp), essentially the same issue.
	 * Pursue the remaining three: <b>realistic dimensions with every element fitting on the paper</b>,
	 * at 190×190 pt, 110×390 pt, and 280×130 pt.
	 * </p>
	 *
	 * <p>
	 * <b>Exclusion does not mean ignoring cases.</b> Aggregation mode also reports exclusion counts:
	 * if growth in exclusions goes unnoticed, real regressions can be missed.
	 * </p>
	 */
	static boolean isOversized(final String html, final int[] pageSize) {
		final Matcher pm = PAGE_MARGIN.matcher(html);
		final double margin = pm.find() ? Double.parseDouble(pm.group(1)) : 0;
		final double contentWidth = pageSize[0] - 2 * margin;
		final double contentHeight = pageSize[1] - 2 * margin;
		if (maxOf(EXPLICIT_WIDTH, html) > contentWidth) {
			return true;
		}
		if (maxOf(EXPLICIT_HEIGHT, html) > contentHeight) {
			return true;
		}
		// Font size determines line height, so compare it against the page-direction dimension.
		return maxOf(FONT_SIZE, html) > contentHeight;
	}

	/**
	 * Return whether the paper is <b>fundamentally too small for layout</b> (added 2026-07-26).
	 *
	 * <p>
	 * The criterion is "some content-area axis is less than {@value #MIN_PAGE_CHARS} times the base font size
	 * (= about {@value #MIN_PAGE_CHARS} characters)." The generator creates 13 pt text on 60×60 pt paper:
	 * <b>four characters per line</b>, where line breaking, floats, and multi-column layout cannot form
	 * a meaningful type area, and no engine behavior yields a correct result. Even the smallest real
	 * printed items (labels/price tags) do not have this ratio.
	 * </p>
	 *
	 * <p>
	 * In practice, only "60×60 pt with font size at least 8 pt" is excluded. Other paper sizes
	 * (120×400, 200×200, 300×150, A4) do not qualify within generated font sizes (6–13 pt).
	 * </p>
	 */
	static boolean isTinyPage(final String html, final int[] pageSize) {
		final Matcher pm = PAGE_MARGIN.matcher(html);
		final double margin = pm.find() ? Double.parseDouble(pm.group(1)) : 0;
		final Matcher fm = FONT_SIZE.matcher(html);
		if (!fm.find()) {
			return false;
		}
		final double font = Double.parseDouble(fm.group(1));
		final double least = font * MIN_PAGE_CHARS;
		return pageSize[0] - 2 * margin < least || pageSize[1] - 2 * margin < least;
	}

	/** Lower bound for "paper suitable for layout", expressed in characters. */
	private static final int MIN_PAGE_CHARS = 8;

	/** Explicit float dimensions emitted by the generator (e.g., {@code float:left;width:64pt}). */
	private static final Pattern FLOAT_EXPLICIT_SIZE = Pattern
			.compile("float:[a-z]+;(?:width|height):(\\d+)pt");

	/**
	 * Return whether the document contains <b>a float with an untypesettable width</b> (added 2026-07-29).
	 *
	 * <p>
	 * Two criteria: an explicit dimension below {@value #MIN_PAGE_CHARS} times the base font size
	 * (= about {@value #MIN_PAGE_CHARS} characters), or a left/right float wider than its explicit
	 * parent/column width. For the same reason that paper too small for layout is excluded,
	 * <b>content must overflow an area too narrow for layout</b>.
	 * </p>
	 *
	 * <p>
	 * Measurements (2026-07-29): all three remaining off-paper sweep cases packed tables or multi-column
	 * layout into floats <b>less than three characters wide</b>:
	 * 596520 at 29 pt / 13 pt (2.2 characters), 1412230 at 10 pt / 11 pt (0.9 characters),
	 * and 1928901 at 15 pt / 10 pt (1.5 characters). CSS {@code overflow} defaults to {@code visible},
	 * so painting this content outside the box is <b>correct behavior</b>;
	 * engine behavior cannot be judged here.
	 * </p>
	 *
	 * <p>
	 * <b>Use only for off-paper checks.</b> Do not apply to content loss, reading order, duplication,
	 * or conversion failure (the same boundary as {@code ARCHITECTURE.md} §5.13,
	 * which allows exclusions only for blank pages and off-paper placement).
	 * </p>
	 */
	static boolean hasUntypesettableFloat(final String html) {
		final Matcher fm = FONT_SIZE.matcher(html);
		if (!fm.find()) {
			return false;
		}
		final double least = Double.parseDouble(fm.group(1)) * MIN_PAGE_CHARS;
		final Matcher m = FLOAT_EXPLICIT_SIZE.matcher(html);
		while (m.find()) {
			if (Double.parseDouble(m.group(1)) < least) {
				return true;
			}
		}
		return hasNarrowFloat(html, least) || hasFloatInsideNarrowContainer(html, least) || hasOverwideFloat(html);
	}

	/**
	 * Whether a left/right float's own explicit width is below the layout lower bound (added 2026-09-17).
	 *
	 * <p>
	 * {@link #FLOAT_EXPLICIT_SIZE} assumes {@code float:…;width:…} are <b>adjacent</b>, but generator v2
	 * inserts {@code writing-mode} between them ({@code float:right;writing-mode:horizontal-tb;width:26pt}).
	 * Declaration order missed the same shape covered by the same decision, so match within one tag's style
	 * (seeds 3767082 and 4709606: lists in 22–26 pt wide right floats; UA default indentation alone goes off-paper).
	 * </p>
	 */
	static boolean hasNarrowFloat(final String html, final double least) {
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				continue;
			}
			final String attrs = String.valueOf(tag.group(3));
			if (!STYLE_FLOAT.matcher(attrs).find()) {
				continue;
			}
			// Last width declaration wins (a final percentage, etc. is unknown: do not exclude). If min-width wins, use that width.
			final double font = least / MIN_PAGE_CHARS;
			final double width = Math.max(lastLength(STYLE_WIDTH_DECLARATION, attrs, font, Double.POSITIVE_INFINITY),
					lastLength(STYLE_MIN_WIDTH_DECLARATION, attrs, font, 0));
			if (width < least) {
				return true;
			}
		}
		return false;
	}

	/** Whether a left/right float actually lies within an ancestor whose explicit width is below the layout lower bound. */
	static boolean hasFloatInsideNarrowContainer(final String html, final double least) {
		final java.util.ArrayDeque<Boolean> narrow = new java.util.ArrayDeque<>();
		narrow.push(Boolean.FALSE);
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (narrow.size() > 1) {
					narrow.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(tag.group(3));
			final boolean insideNarrow = narrow.peek().booleanValue();
			if (insideNarrow && STYLE_FLOAT.matcher(attrs).find()) {
				return true;
			}
			if (attrs.endsWith("/")) {
				continue;
			}
			final Matcher widthMatcher = STYLE_WIDTH.matcher(attrs);
			final boolean thisNarrow = insideNarrow
					|| (widthMatcher.find() && Double.parseDouble(widthMatcher.group(1)) < least);
			narrow.push(Boolean.valueOf(thisNarrow));
		}
		return false;
	}

	private static final Pattern STYLE_FLOAT = Pattern
			.compile("(?:^|[;\\s\"])float\\s*:\\s*(left|right)\\s*(?:;|\"|'|$)");
	private static final Pattern STYLE_COLUMN_COUNT = Pattern.compile("column-count\\s*:\\s*(\\d+)");
	private static final Pattern STYLE_COLUMN_GAP = Pattern.compile("column-gap\\s*:\\s*([\\d.]+)pt");
	private static final Pattern STYLE_FLEX = Pattern
			.compile("(?:^|[;\\s\"])display\\s*:\\s*(?:inline-)?flex\\s*(?:;|\"|'|$)");

	/**
	 * Return whether a left/right float exceeds its explicitly specified containing width.
	 *
	 * <p>
	 * Do not simply compare document-wide maxima and minima. Follow start/end tags with a stack
	 * to avoid associating widths in different branches. For multi-column layout, use the page content width
	 * or explicit parent width to calculate {@code (width - gap*(column count-1))/column count}.
	 * This identifies only strict seeds 132786 (126 pt float in 99 pt) and 143513
	 * (89 pt float in an approximately 25.3 pt column) as untypesettable widths caused by author declarations.
	 * </p>
	 */
	static boolean hasOverwideFloat(final String html) {
		final Matcher pageWidthMatcher = PAGE_WIDTH_PROPERTY.matcher(html);
		if (!pageWidthMatcher.find()) {
			return false;
		}
		final Matcher marginMatcher = PAGE_MARGIN.matcher(html);
		final double margin = marginMatcher.find() ? Double.parseDouble(marginMatcher.group(1)) : 0;
		final double pageContentWidth = Double.parseDouble(pageWidthMatcher.group(1)) - 2 * margin;
		if (!(pageContentWidth > 0)) {
			return false;
		}

		final java.util.ArrayDeque<Double> childWidths = new java.util.ArrayDeque<>();
		final java.util.ArrayDeque<Double> floatLimits = new java.util.ArrayDeque<>();
		childWidths.push(Double.valueOf(pageContentWidth));
		floatLimits.push(Double.valueOf(Double.POSITIVE_INFINITY));
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (childWidths.size() > 1) {
					childWidths.pop();
					floatLimits.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(tag.group(3));
			if (attrs.endsWith("/")) {
				continue;
			}
			final double containingWidth = childWidths.peek().doubleValue();
			final Matcher widthMatcher = STYLE_WIDTH.matcher(attrs);
			final Double explicitWidth = widthMatcher.find() ? Double.valueOf(widthMatcher.group(1)) : null;
			final boolean floating = STYLE_FLOAT.matcher(attrs).find();
			if (explicitWidth != null && explicitWidth.doubleValue() > floatLimits.peek().doubleValue()) {
				// Descendant explicit widths make an auto-width float's shrink-to-fit width exceed its containing width
				// (seed 865035). Widths in other branches are absent from the stack.
				return true;
			}
			if (explicitWidth != null && floating
					&& explicitWidth.doubleValue() > containingWidth) {
				return true;
			}

			double availableForChildren = explicitWidth == null ? containingWidth : explicitWidth.doubleValue();
			final Matcher countMatcher = STYLE_COLUMN_COUNT.matcher(attrs);
			if (countMatcher.find()) {
				final int count = Integer.parseInt(countMatcher.group(1));
				final Matcher gapMatcher = STYLE_COLUMN_GAP.matcher(attrs);
				final double gap = gapMatcher.find() ? Double.parseDouble(gapMatcher.group(1)) : 0;
				if (count > 1) {
					availableForChildren = (availableForChildren - gap * (count - 1)) / count;
				}
			}
			childWidths.push(Double.valueOf(availableForChildren));
			floatLimits.push(Double.valueOf(floating
					? (explicitWidth == null ? containingWidth : explicitWidth.doubleValue())
					: floatLimits.peek().doubleValue()));
		}
		return false;
	}

	/** Whether a table descends from a layout with at least two columns inside a flex ancestor. */
	static boolean hasFlexMulticolTable(final String html) {
		final java.util.ArrayDeque<Boolean> flex = new java.util.ArrayDeque<>();
		final java.util.ArrayDeque<Boolean> multicolInFlex = new java.util.ArrayDeque<>();
		flex.push(Boolean.FALSE);
		multicolInFlex.push(Boolean.FALSE);
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (flex.size() > 1) {
					flex.pop();
					multicolInFlex.pop();
				}
				continue;
			}
			final String name = tag.group(2);
			final String attrs = String.valueOf(tag.group(3));
			final boolean inFlex = flex.peek().booleanValue() || STYLE_FLEX.matcher(attrs).find();
			boolean inMulticol = multicolInFlex.peek().booleanValue();
			final Matcher count = STYLE_COLUMN_COUNT.matcher(attrs);
			if (inFlex && count.find() && Integer.parseInt(count.group(1)) >= 2) {
				inMulticol = true;
			}
			if (inMulticol && name.equalsIgnoreCase("table")) {
				return true;
			}
			if (!attrs.endsWith("/")) {
				flex.push(Boolean.valueOf(inFlex));
				multicolInFlex.push(Boolean.valueOf(inMulticol));
			}
		}
		return false;
	}

	/** {@code writing-mode} declarations (capture both body and style attributes). */
	private static final Pattern WRITING_MODE = Pattern.compile("writing-mode\\s*:\\s*([a-z-]+)");

	/**
	 * Return whether the document contains <b>orthogonal flows</b>
	 * (nested {@code writing-mode} values with different axes) (added 2026-07-28).
	 *
	 * <p>
	 * <b>Compute the predicate from document text alone</b>: humans cannot judge 30 million sweep documents
	 * individually (ARCHITECTURE.md §5.13). The generator writes {@code writing-mode} only in
	 * {@code body} style rules and element {@code style} attributes, so collect all declarations and check
	 * only <b>whether both vertical and horizontal axes appear</b>.
	 * </p>
	 *
	 * <p>
	 * Do not inspect nesting (which is the ancestor): the presence of both axes guarantees an orthogonal
	 * boundary somewhere. Documents containing only opposite directions on the same axis
	 * ({@code vertical-rl} and {@code vertical-lr}) are not orthogonal here:
	 * that is {@code SAME_AXIS_DIRECTION_CHANGE}, and inline-axis length does not change.
	 * </p>
	 */
	static boolean hasOrthogonalFlow(final String html) {
		boolean vertical = false, horizontal = false;
		final Matcher m = WRITING_MODE.matcher(html);
		while (m.find()) {
			if (m.group(1).startsWith("vertical")) {
				vertical = true;
			} else {
				horizontal = true;
			}
		}
		// Without a body declaration, the default (horizontal-tb) applies.
		return vertical && (horizontal || !BODY_WRITING_MODE.matcher(html).find());
	}

	/** Start/end tags and {@code writing-mode} in their {@code style}. */
	private static final Pattern TAG_OR_WM = Pattern
			.compile("</(\\w+)>|<(\\w+)([^>]*)>");
	private static final Pattern STYLE_WRITING_MODE = Pattern
			.compile("writing-mode\\s*:\\s*([a-z-]+)");
	/** Explicit dimensions along the page axis in vertical writing. */
	private static final Pattern STYLE_WIDTH = Pattern
			.compile("(?:^|[;\\s\"])width\\s*:\\s*([\\d.]+)(?:pt)?\\s*(?:;|\"|'|$)");

	/**
	 * Whether an axis-changing child has an explicit width below {@value #MIN_PAGE_CHARS} times
	 * the base character advance, specified via {@code width}.
	 *
	 * <p>Follow tag nesting to avoid associating narrow widths and writing-mode in different branches.
	 * Exclude mere orthogonal flow, same-axis direction changes, {@code height:0},
	 * and auto sizes without specified widths from this predicate.</p>
	 */
	static boolean hasUntypesettableOrthogonalFlow(final String html) {
		final Matcher bm = BODY_WRITING_MODE.matcher(html);
		final String rootMode = bm.find() ? bm.group(1) : "horizontal-tb";
		final Matcher fm = FONT_SIZE.matcher(html);
		if (!fm.find()) {
			return false;
		}
		final double least = Double.parseDouble(fm.group(1)) * MIN_PAGE_CHARS;
		final java.util.ArrayDeque<String> modes = new java.util.ArrayDeque<>();
		modes.push(rootMode);
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (modes.size() > 1) {
					modes.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(tag.group(3));
			if (attrs.endsWith("/")) {
				continue;
			}
			final String parent = modes.peek();
			String mode = parent;
			final Matcher wm = STYLE_WRITING_MODE.matcher(attrs);
			if (wm.find()) {
				mode = wm.group(1);
			}
			final Matcher width = STYLE_WIDTH.matcher(attrs);
			if (mode.startsWith("vertical-") != parent.startsWith("vertical-") && width.find()
					&& Double.parseDouble(width.group(1)) < least) {
				return true;
			}
			modes.push(mode);
		}
		return false;
	}

	/**
	 * Return whether a same-vertical-axis parent-child pair reverses page progression and the reversed element's
	 * explicit width is below {@value #MIN_PAGE_CHARS} base characters, or a descendant's explicit width exceeds it.
	 * Use the same measure as {@link #isTinyPage} and {@link #hasUntypesettableFloat}.
	 *
	 * <p>
	 * Descendant widths are lower bounds (2026-10-07): the last {@code min-width}, and the last {@code width}
	 * if there is no {@code max-width} (pt/em). Previously, comparison used the first {@code width} in pt,
	 * counting boxes narrowed by {@code max-width} too. Ignore widths of non-replaced inline elements
	 * (including tags inline by default) and table parts; flex items use only {@code min-width}.
	 * Do not read em for boxes inheriting changed font sizes (codex soundness counterexamples).
	 * That day, we almost added "exclude when a descendant exceeds a box's explicit width within a region
	 * progressing opposite to the paper", but such a region need not touch the paper edge, and alignment
	 * changes the overflow side (codex's second counterexample). Fit seed 11942560 stopped in that shape
	 * because Copper aligned an inline-block baseline to its first line (CSS 2.1 §10.8.1 and Chrome use the last).
	 * Chrome kept it on the paper: this was not author-caused overflow (triage §22).
	 * </p>
	 *
	 * <p>
	 * Follow generated HTML nesting with a stack, not mere string occurrences. This prevents incorrectly
	 * combining {@code vertical-rl}, {@code vertical-lr}, and {@code width:0} in separate branches
	 * into one exclusion condition.
	 * </p>
	 */
	static boolean hasUntypesettableOppositeProgression(final String html) {
		final Matcher bm = BODY_WRITING_MODE.matcher(html);
		final String rootMode = bm.find() ? bm.group(1) : "horizontal-tb";
		final Matcher fm = FONT_SIZE.matcher(html);
		final double font = fm.find() ? Double.parseDouble(fm.group(1)) : 0;
		final double least = font * MIN_PAGE_CHARS;
		final java.util.ArrayDeque<String> modes = new java.util.ArrayDeque<>();
		final java.util.ArrayDeque<Double> reverseLimits = new java.util.ArrayDeque<>();
		// Direct child of a container where child width declarations do not determine width (flex items grow/shrink)?
		final java.util.ArrayDeque<Boolean> flexParents = new java.util.ArrayDeque<>();
		// Has this element or an ancestor changed font size (so em cannot use the body font size)?
		final java.util.ArrayDeque<Boolean> fontChanged = new java.util.ArrayDeque<>();
		modes.push(rootMode);
		reverseLimits.push(Double.valueOf(Double.POSITIVE_INFINITY));
		flexParents.push(Boolean.FALSE);
		fontChanged.push(Boolean.FALSE);
		final int bodyAt = html.indexOf("<body");
		final Matcher m = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			m.region(bodyAt, html.length());
		}
		while (m.find()) {
			if (m.group(1) != null) {
				if (modes.size() > 1) {
					modes.pop();
					reverseLimits.pop();
					flexParents.pop();
					fontChanged.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(m.group(3));
			if (attrs.endsWith("/")) {
				continue;
			}
			final String parent = modes.peek();
			String mode = parent;
			final Matcher wm = STYLE_WRITING_MODE.matcher(attrs);
			if (wm.find()) {
				mode = wm.group(1);
			}
			final Matcher widthMatcher = STYLE_WIDTH.matcher(attrs);
			final Double width = widthMatcher.find() ? Double.valueOf(widthMatcher.group(1)) : null;
			final double inheritedLimit = reverseLimits.peek().doubleValue();
			// Does the width declaration apply to this box? Non-replaced inline elements (including default-inline tags)
			// and table parts have no width; flex items grow/shrink (only minimum width applies). Ignore em after font-size changes.
			final String name = String.valueOf(m.group(2)).toLowerCase(java.util.Locale.ROOT);
			final boolean unsized = STYLE_DISPLAY_UNSIZED.matcher(attrs).find()
					|| (INLINE_BY_DEFAULT.contains(name) && !STYLE_DISPLAY_SIZED.matcher(attrs).find())
					|| TABLE_PARTS.contains(name);
			final boolean flexItem = flexParents.peek().booleanValue();
			final boolean ownFont = fontChanged.peek().booleanValue() || STYLE_FONT_SIZE_DECLARATION.matcher(attrs).find();
			final double em = ownFont ? Double.NaN : font;
			final double lower = unsized ? 0
					: flexItem ? lastLength(STYLE_MIN_WIDTH_DECLARATION, attrs, em, 0) : widthLowerBound(attrs, em);
			// Compare descendant width lower bounds (previously only the first width in pt was read, counting max-width-narrowed boxes too).
			if (lower > inheritedLimit) {
				return true;
			}
			double reverseLimit = inheritedLimit;
			if (parent.startsWith("vertical-") && mode.startsWith("vertical-") && !parent.equals(mode)
					&& width != null) {
				if (width.doubleValue() < least) {
					return true;
				}
				reverseLimit = Math.min(reverseLimit, width.doubleValue());
			}
			modes.push(mode);
			reverseLimits.push(Double.valueOf(reverseLimit));
			flexParents.push(Boolean.valueOf(STYLE_FLEX.matcher(attrs).find()));
			fontChanged.push(Boolean.valueOf(ownFont));
		}
		return false;
	}

	private static final Pattern STYLE_MAX_WIDTH_DECLARATION = Pattern
			.compile("(?:^|[;\\s\"])max-width\\s*:\\s*([^;\"']*)");
	/** Declarations changing font size, making em lengths unreadable using the body font size. */
	private static final Pattern STYLE_FONT_SIZE_DECLARATION = Pattern.compile("(?:^|[;\\s\"])font(?:-size)?\\s*:");

	/**
	 * Explicit width lower bound (pt): the larger of the last {@code min-width} and, if no {@code max-width}
	 * exists, the last {@code width} (both pt/em). Zero if absent.
	 * The caller checks whether width declarations apply to the box.
	 */
	static double widthLowerBound(final String attrs, final double font) {
		final double min = lastLength(STYLE_MIN_WIDTH_DECLARATION, attrs, font, 0);
		final double width = STYLE_MAX_WIDTH_DECLARATION.matcher(attrs).find() ? 0
				: lastLength(STYLE_WIDTH_DECLARATION, attrs, font, 0);
		// If font is NaN (unknown font size), an em declaration becomes NaN. Use zero as its lower bound.
		return Math.max(Double.isNaN(min) ? 0 : min, Double.isNaN(width) ? 0 : width);
	}

	/** Generator-used tags that default to non-replaced inline (width declarations do not apply). */
	private static final Set<String> INLINE_BY_DEFAULT = Set.of("span", "a", "b", "i", "em", "strong", "small", "big",
			"sub", "sup", "ruby", "rb", "rt", "rp", "label", "code", "q", "abbr", "cite");
	/** Table-part tags (width declarations apply only as minimum widths). */
	private static final Set<String> TABLE_PARTS = Set.of("table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption",
			"col", "colgroup");
	/** display values that make width declarations apply (override a tag's default inline). */
	private static final Pattern STYLE_DISPLAY_SIZED = Pattern.compile(
			"(?:^|[;\\s\"])display\\s*:\\s*(?:block|inline-block|flex|grid|list-item|flow-root)\\s*(?:;|\"|'|$)");

	/**
	 * Follow nesting and return the maximum <b>number of axis switches (vertical/horizontal)</b>.
	 *
	 * <p>
	 * {@link #hasOrthogonalFlow} checks only whether vertical and horizontal coexist,
	 * so it cannot distinguish <b>two levels</b> (ordinary horizontal writing within vertical writing)
	 * from <b>three levels</b> ({@code vertical-rl}→{@code horizontal-tb}→{@code vertical-lr}).
	 * Count depth to limit exclusions to the latter.
	 * </p>
	 *
	 * <p>
	 * Tags without attributes do not change axes; just push them onto the stack.
	 * Self-closing tags ({@code <br/>}) do not create nesting and thus do not affect depth.
	 * Even pushing them retains the same value without increasing switch count,
	 * since they have no {@code writing-mode} in {@code style}.
	 * </p>
	 */
	static int orthogonalAxisChanges(final String html) {
		final Matcher bm = BODY_WRITING_MODE.matcher(html);
		final boolean rootVertical = bm.find() && bm.group(1).startsWith("vertical");
		final java.util.ArrayDeque<Boolean> axis = new java.util.ArrayDeque<>();
		final java.util.ArrayDeque<Integer> changes = new java.util.ArrayDeque<>();
		axis.push(Boolean.valueOf(rootVertical));
		changes.push(Integer.valueOf(0));
		int worst = 0;
		final int bodyAt = html.indexOf("<body");
		final Matcher m = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			m.region(bodyAt, html.length());
		}
		while (m.find()) {
			if (m.group(1) != null) {          // End tag.
				if (axis.size() > 1) {
					axis.pop();
					changes.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(m.group(3));
			if (attrs.endsWith("/")) {         // Self-closing tags do not create nesting.
				continue;
			}
			boolean vertical = axis.peek().booleanValue();
			int n = changes.peek().intValue();
			final Matcher wm = STYLE_WRITING_MODE.matcher(attrs);
			if (wm.find()) {
				final boolean v = wm.group(1).startsWith("vertical");
				if (v != vertical) {
					vertical = v;
					++n;
					worst = Math.max(worst, n);
				}
			}
			axis.push(Boolean.valueOf(vertical));
			changes.push(Integer.valueOf(n));
		}
		return worst;
	}

	private static final Pattern STYLE_WIDTH_DECLARATION = Pattern
			.compile("(?:^|[;\\s\"])width\\s*:\\s*([^;\"']*)");
	private static final Pattern STYLE_HEIGHT_DECLARATION = Pattern
			.compile("(?:^|[;\\s\"])height\\s*:\\s*([^;\"']*)");
	private static final Pattern STYLE_MIN_WIDTH_DECLARATION = Pattern
			.compile("(?:^|[;\\s\"])min-width\\s*:\\s*([^;\"']*)");
	private static final Pattern PT_OR_EM_LENGTH = Pattern.compile("([\\d.]+)(pt|em)");
	private static final Pattern STYLE_BORDER_COLLAPSE = Pattern.compile("border-collapse\\s*:\\s*collapse");
	/** display values where explicit width does not finalize the box (non-replaced inline and table parts). */
	private static final Pattern STYLE_DISPLAY_UNSIZED = Pattern
			.compile("(?:^|[;\\s\"])display\\s*:\\s*(?:inline|table[a-z-]*|inline-table)\\s*(?:;|\"|'|$)");
	private static final Pattern PAGE_HEIGHT_PROPERTY = Pattern
			.compile("name=\"output\\.page-height\"\\s+value=\"([\\d.]+)pt\"");

	/** Reasons from {@link #findUnfittableContent}; become part of aggregation category names. */
	static final String UNFITTABLE_RUBY = "行より長い割れないルビ";
	static final String UNFITTABLE_MIN_WIDTH = "入れ物より広いmin-width";
	static final String UNFITTABLE_COLUMN = "組版できない幅の段";
	static final String UNFITTABLE_FLEX_LINE = "折り返さないflex行の最小主軸サイズ";
	static final String UNFITTABLE_TABLE_COLUMN = "紙の行長を超える表の最小幅";

	/**
	 * Return the reason if content physically cannot fit in the type area ({@code null} otherwise).
	 * Added 2026-09-17. See {@link ExcludedByUnfittableContent} for the history.
	 *
	 * <p>
	 * Follow start/end tags with a stack, carrying <b>upper bounds on available width and height</b>
	 * per element (paper content area → explicit {@code width}/{@code height} → column width).
	 * Narrow separate-border tables by {@code border-spacing} of 1.5 pt × 2, and cells by borders of 1 pt × 2.
	 * Since only upper bounds are tracked, treat percentage and content-dependent widths as "same as parent".
	 * Read width declarations with last-wins precedence; do not narrow for elements whose width does not
	 * finalize the box (non-replaced inline, flex items, tables, cells), to avoid declaring fitting documents
	 * unfit (counterexamples from the 2026-09-17 codex review are pinned down in {@code FuzzOraclePredicateTest}).
	 * This traversal assumes generated HTML (no omitted end tags, no {@code <br>}), not general HTML parsing.
	 * </p>
	 * <ul>
	 * <li>{@link #UNFITTABLE_RUBY}: a lower bound on the base-text width of long ruby ({@code fuzz-long-ruby})
	 * (T 0.6 em, digits 0.5 em, spaces 0.25 em; at most the test font's advances) exceeds the line upper bound
	 * for its writing direction. copper does not split ruby within a line. Initially excluded only when
	 * "the overflow axis matches the ruby inline axis" (20 of 21 ruby cases among 31 measured cases matched),
	 * but in seed 7627539 (2026-09-19), an unfit ruby line just after a multi-page float placed the rescue-split
	 * origin outside the page axis, propagating to another axis. Like other reasons, do not restrict the axis.</li>
	 * <li>{@link #UNFITTABLE_MIN_WIDTH}: {@code min-width} exceeds the available-width upper bound.
	 * Since {@code min-width} wins over {@code max-width}, the box must overflow its container
	 * (same condition as "explicit width exceeds containing width" in {@link #hasOverwideFloat}).</li>
	 * <li>{@link #UNFITTABLE_COLUMN}: column width is below {@value #MIN_PAGE_CHARS} base characters.
	 * Same measure as {@link #isTinyPage} and {@link #hasUntypesettableFloat}
	 * (content must overflow an area too narrow for layout). <b>This is an exclusion-policy threshold,
	 * not proof of physical inability to fit.</b></li>
	 * <li>{@link #UNFITTABLE_FLEX_LINE}: in a non-wrapping row-direction flex, the sum of item main-size
	 * lower bounds exceeds the paper's line length ({@link #flexLineOverflows}). Flex items do not shrink
	 * below their automatic minimum size (min-content); {@code flex-shrink:0} items do not shrink below
	 * {@code flex-basis} (Flexbox §4.5/§9.7). Seed 9321740 (2026-09-28): on vertical-writing paper with
	 * 150 pt line length, a table whose min-content exceeded the line length sat beside
	 * {@code flex:1 0 35%} and {@code flex:2 0 calc(25% + 8pt)} items, pushing ruby to y=307.76.
	 * Chrome produced the same shape (y=300.77); this was not an engine defect.</li>
	 * <li>{@link #UNFITTABLE_TABLE_COLUMN}: for a table directly under body (or inside divs with only frames
	 * and margins), summed column minimum-width lower bounds exceed the paper's line length,
	 * and a text-bearing cell starts in an off-paper column ({@link #tableColumnBeyondPage}).
	 * Seed 10376223 (2026-09-29): a nine-column table (one cell contained a nested table) on 120 pt wide paper
	 * placed T20 at x=269.48. Chrome produced the same shape (x=262.78); this was not an engine defect.</li>
	 * </ul>
	 *
	 * <p>
	 * <b>Limitations</b>: exclusions apply per document, regardless of overflow axis (observed cases propagated
	 * to another axis, such as column overflow pushing out a float). Real defects on other branches/axes
	 * of qualifying documents are hidden. Measurements (20,000 documents from seed 5,250,000 onward) showed
	 * existing predicates already excluded just over 93% of documents on both axes;
	 * this predicate added less than one percentage point.
	 * </p>
	 */
	static String findUnfittableContent(final String html) {
		final Matcher widthProperty = PAGE_WIDTH_PROPERTY.matcher(html);
		final Matcher heightProperty = PAGE_HEIGHT_PROPERTY.matcher(html);
		final Matcher fm = FONT_SIZE.matcher(html);
		if (!widthProperty.find() || !heightProperty.find() || !fm.find()) {
			return null;
		}
		final Matcher pm = PAGE_MARGIN.matcher(html);
		final double margin = pm.find() ? Double.parseDouble(pm.group(1)) : 0;
		final double font = Double.parseDouble(fm.group(1));
		final double least = font * MIN_PAGE_CHARS;
		final Matcher bm = BODY_WRITING_MODE.matcher(html);
		final java.util.ArrayDeque<Boolean> verticals = new java.util.ArrayDeque<>();
		final java.util.ArrayDeque<double[]> extents = new java.util.ArrayDeque<>();
		// Direct child of a container where child width declarations are not available-width upper bounds (flex items grow)?
		final java.util.ArrayDeque<Boolean> flexParents = new java.util.ArrayDeque<>();
		// The generator selects the border model in document style rules (table{border-collapse:…}).
		final boolean collapsedTables = STYLE_BORDER_COLLAPSE.matcher(html).find();
		verticals.push(Boolean.valueOf(bm.find() && bm.group(1).startsWith("vertical")));
		extents.push(new double[] { Double.parseDouble(widthProperty.group(1)) - 2 * margin,
				Double.parseDouble(heightProperty.group(1)) - 2 * margin });
		flexParents.push(Boolean.FALSE);
		// Are all ancestors from body to here divs with only frames and margins ({@link #isPlainWrapper})? The table start
		// cannot precede the content start, and available width cannot exceed the paper's content width.
		final java.util.ArrayDeque<Boolean> plainPaths = new java.util.ArrayDeque<>();
		plainPaths.push(Boolean.TRUE);
		// Direction of a float allowed to wrap a table directly, with only wrapper-div ancestors ({@link #floatWrapperSide}; horizontal only).
		final java.util.ArrayDeque<Character> floatSides = new java.util.ArrayDeque<>();
		floatSides.push(Character.valueOf(NOT_FLOATED));
		final double[] pageExtent = extents.peek();
		boolean minWidth = false, column = false, flexLine = false, tableColumn = false;
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt + 5, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (verticals.size() > 1) {
					verticals.pop();
					extents.pop();
					flexParents.pop();
					plainPaths.pop();
					floatSides.pop();
				}
				continue;
			}
			final String name = tag.group(2);
			final String attrs = String.valueOf(tag.group(3));
			if (attrs.endsWith("/")) {
				continue;
			}
			final boolean flexItem = flexParents.peek().booleanValue();
			final boolean table = name.equalsIgnoreCase("table");
			final boolean cell = name.equalsIgnoreCase("td") || name.equalsIgnoreCase("th");
			boolean vertical = verticals.peek().booleanValue();
			final Matcher wm = STYLE_WRITING_MODE.matcher(attrs);
			if (wm.find()) {
				vertical = wm.group(1).startsWith("vertical");
			}
			double width = extents.peek()[0];
			double height = extents.peek()[1];
			// For separate-border tables, subtract UA default border-spacing (2 px = 1.5 pt) × 2 and cell borders of 1 pt × 2.
			if (collapsedTables) {
				// Collapsed borders (1 pt) lie half inside each cell. No spacing.
				if (cell) {
					width -= 1;
					height -= 1;
				}
			} else if (table || cell) {
				width -= table ? 3 : 2;
				height -= table ? 3 : 2;
			}
			final double minValue = lastLength(STYLE_MIN_WIDTH_DECLARATION, attrs, font, 0);
			if (minValue > width) {
				minWidth = true;
			}
			// Explicit width/height bounds content only for elements whose box is finalized by that dimension.
			// Non-replaced inline elements have no width; flex items grow; tables/cells expand to their content.
			final boolean sized = !flexItem && !table && !cell && !STYLE_DISPLAY_UNSIZED.matcher(attrs).find();
			if (sized) {
				width = lastLength(STYLE_WIDTH_DECLARATION, attrs, font, width);
				height = lastLength(STYLE_HEIGHT_DECLARATION, attrs, font, height);
			}
			width = Math.max(width, minValue);
			final Matcher count = STYLE_COLUMN_COUNT.matcher(attrs);
			if (count.find() && Integer.parseInt(count.group(1)) > 1) {
				final int n = Integer.parseInt(count.group(1));
				final Matcher gapMatcher = STYLE_COLUMN_GAP.matcher(attrs);
				final double gap = gapMatcher.find() ? Double.parseDouble(gapMatcher.group(1)) : 0;
				final double columnExtent = ((vertical ? height : width) - gap * (n - 1)) / n;
				if (vertical) {
					height = columnExtent;
				} else {
					width = columnExtent;
				}
				if (columnExtent < least) {
					column = true;
				}
			}
			// Only flex containers directly under body (where line length is definitively the paper's content width).
			if (!flexLine && verticals.size() == 1 && STYLE_FLEX.matcher(attrs).find() && flexLineOverflows(html,
					tag.end(), attrs, vertical, vertical ? height : width, margin, font, collapsedTables)) {
				flexLine = true;
			}
			// Attribute-free tables directly under body, inside divs with only frames/margins, or directly within floats there
			// (paper edge = content start + paper content width + margin).
			final char floatSide = floatSides.peek().charValue();
			if (!tableColumn && (plainPaths.peek().booleanValue() || floatSide != NOT_FLOATED) && table
					&& attrs.isBlank() && tableColumnBeyondPage(html, tag.end(), pageExtent[vertical ? 1 : 0], margin, font,
							collapsedTables, plainPaths.peek().booleanValue() ? NOT_FLOATED : floatSide)) {
				tableColumn = true;
			}
			if (name.equalsIgnoreCase("ruby") && attrs.contains("fuzz-long-ruby")) {
				final int end = html.indexOf("<rt>", tag.end());
				if (end >= 0) {
					double base = 0;
					for (int i = tag.end(); i < end; ++i) {
						// Base text contains only T+digits and single spaces (appendNode case 8). The fixed test font (Times-Roman)
						// has advances T=0.611 em, digits=0.5 em, spaces=0.25 em; they do not exceed these values.
						// Estimating every character at 0.5 em and spaces at 0.2 em gave 197 pt for 13 words (measured: 232 pt),
						// missing overflow on 200 pt paper (seed 9110300, 2026-09-20).
						final char c = html.charAt(i);
						base += c == ' ' ? 0.25 : c == 'T' ? 0.6 : 0.5;
					}
					if (base * font > (vertical ? height : width)) {
						return UNFITTABLE_RUBY;
					}
				}
			}
			verticals.push(Boolean.valueOf(vertical));
			extents.push(new double[] { width, height });
			flexParents.push(Boolean.valueOf(STYLE_FLEX.matcher(attrs).find()));
			floatSides.push(Character.valueOf(plainPaths.peek().booleanValue() && !vertical
					? floatWrapperSide(name, attrs) : NOT_FLOATED));
			plainPaths.push(Boolean.valueOf(plainPaths.peek().booleanValue() && isPlainWrapper(name, attrs, vertical)));
		}
		return minWidth ? UNFITTABLE_MIN_WIDTH
				: column ? UNFITTABLE_COLUMN
						: flexLine ? UNFITTABLE_FLEX_LINE : tableColumn ? UNFITTABLE_TABLE_COLUMN : null;
	}

	private static final Pattern STYLE_FLEX_DIRECTION = Pattern.compile("flex-direction\\s*:\\s*([a-z-]+)");
	private static final Pattern STYLE_FLEX_WRAP = Pattern.compile("flex-wrap\\s*:\\s*([a-z-]+)");
	private static final Pattern STYLE_GAP_DECLARATION = Pattern.compile("(?:^|[;\\s\"])gap\\s*:\\s*([^;\"']*)");
	/** {@code flex:<grow> <shrink> <basis>} (generator form). */
	private static final Pattern STYLE_FLEX_SHORTHAND = Pattern
			.compile("(?:^|[;\\s\"])flex\\s*:\\s*(\\d+)\\s+(\\d+)\\s+([^;\"']*)");
	private static final Pattern PERCENT_LENGTH = Pattern.compile("([\\d.]+)%");
	private static final Pattern CALC_PERCENT_PLUS_PT = Pattern
			.compile("calc\\(\\s*([\\d.]+)%\\s*\\+\\s*([\\d.]+)pt\\s*\\)");
	/** Declarations that can reduce word width (break/hide text). Do not use table estimates if present in the document. */
	private static final String[] BREAKABLE_TEXT_HINTS = { "display:none", "overflow-wrap:anywhere",
			"overflow-wrap:break-word", "word-break:break", "line-break:anywhere" };

	/** Allowed declarations on a flex container directly under body (generator template); others make paper content width uncertain as line length. */
	private static final Pattern FLEX_CONTAINER_DECLARATION = Pattern.compile(
			"\\s*(?:display\\s*:\\s*flex|position\\s*:\\s*(?:static|relative)|float\\s*:\\s*none"
					+ "|flex-direction\\s*:\\s*[a-z-]+|flex-wrap\\s*:\\s*[a-z-]+|gap\\s*:\\s*[^;]*)\\s*");
	private static final Pattern STYLE_ATTRIBUTE = Pattern.compile("style\\s*=\\s*\"([^\"]*)\"");
	/** Floats that can change inline extent (anything except none). Narrow BFC flex containers. */
	private static final Pattern FLOAT_NOT_NONE = Pattern.compile("float\\s*:\\s*(?!none)[a-z]");
	private static final Pattern OUT_OF_FLOW = Pattern.compile("position\\s*:\\s*(?:absolute|fixed)");
	/** Displacements moving boxes from their painted positions (relative-positioning top/left, etc.). */
	private static final Pattern POSITION_OFFSET = Pattern.compile("(?:^|[;\\s\"])(?:top|left|right|bottom)\\s*:");
	/** Painted text (generator words T+digits). */
	private static final Pattern DRAWN_TOKEN = Pattern.compile("T\\d");

	/**
	 * For a non-wrapping row-direction flex container directly under body ({@code from} immediately after its start tag),
	 * return whether a text-bearing item must be painted off-paper: whether an item containing text starts
	 * where the sum of preceding item main-size lower bounds and gaps exceeds {@code extent + margin}
	 * (content width + margin; beyond this lies outside the paper).
	 *
	 * <p>
	 * <b>Restrict to locations with definite line length.</b> The container must be directly under body,
	 * with only generator-template declarations ({@link #FLEX_CONTAINER_DECLARATION}); the document must have
	 * no floats, {@code display:none}, {@code visibility:hidden}, absolute positioning, or displacements
	 * ({@code top}, etc.). Then the container's line length is exactly content width {@code extent},
	 * and items start at {@code margin}. Do not judge inside tables, beside floats, or inside flex items:
	 * containers can expand/shrink to content, and displacement/absolute positioning can bring text back onto paper.
	 * </p>
	 *
	 * <p>
	 * <b>Use only items whose text cannot extend before the item start as evidence.</b> Only {@code row} direction
	 * qualifies (with {@code row-reverse}, long text in an off-paper item can overflow toward line end,
	 * back toward the paper). The item itself may have only {@link #PLAIN_ITEM_DECLARATION}, and descendants
	 * must have no style/dir (a reversed flex descendant can overflow text toward the start).
	 * Check the start of a text-bearing item, not the sum of boxes, because some documents overflow only
	 * empty space in the final box while text stays on-paper. All are counterexamples from three codex reviews
	 * on 2026-09-28.
	 * </p>
	 *
	 * <p>
	 * The lower bound is {@code c + p×extent}. For {@code flex-shrink:0} items, use {@code flex-basis}
	 * (pt, em, %, {@code calc(%+pt)}; zero for {@code auto}, etc.); for direct attribute-free tables,
	 * use {@link #tableMinContentLowerBound}; between items, use {@code gap}.
	 * If a main-axis maximum ({@code max-width}, etc.) exists, take the smaller value only when it has
	 * the same form as basis (both percentages or both constants); mixed forms use zero.
	 * Adding only lower bounds avoids declaring fitting documents unfit.
	 * </p>
	 */
	static boolean flexLineOverflows(final String html, final int from, final String containerAttrs,
			final boolean vertical, final double extent, final double margin, final double font,
			final boolean collapsedTables) {
		final Matcher direction = STYLE_FLEX_DIRECTION.matcher(containerAttrs);
		final Matcher wrap = STYLE_FLEX_WRAP.matcher(containerAttrs);
		if ((direction.find() && !"row".equals(direction.group(1))) || (wrap.find() && !"nowrap".equals(wrap.group(1)))
				|| html.contains("display:none") || html.contains("visibility:hidden") || FLOAT_NOT_NONE.matcher(html).find()
				|| OUT_OF_FLOW.matcher(html).find() || POSITION_OFFSET.matcher(html).find()) {
			return false;
		}
		final Matcher style = STYLE_ATTRIBUTE.matcher(containerAttrs);
		if (!style.find()) {
			return false;
		}
		for (final String declaration : style.group(1).split(";")) {
			if (!declaration.isBlank() && !FLEX_CONTAINER_DECLARATION.matcher(declaration).matches()) {
				return false;
			}
		}
		// The item's main axis is vertical (max-height) for a vertical-writing container, horizontal (max-width) otherwise.
		final String maxMain = vertical ? "max-height" : "max-width";
		final double gap = lastLength(STYLE_GAP_DECLARATION, containerAttrs, font, 0);
		double c = 0, p = 0;
		int items = 0, depth = 0;
		final Matcher tag = TAG_OR_WM.matcher(html);
		tag.region(from, html.length());
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (depth == 0) {
					break;
				}
				--depth;
				continue;
			}
			final String attrs = String.valueOf(tag.group(3));
			if (attrs.endsWith("/")) {
				continue;
			}
			if (depth == 0) {
				// This item's box starts after preceding items and gaps. If that start lies beyond the paper edge and text exists inside,
				// with no declarations allowing content before the item start (descendant style/dir), the text must be painted off-paper.
				// Judge the painted item's start, not the sum of boxes, to avoid excluding documents where only
				// empty space in the final box overflows.
				if (c + p * extent + items * gap > extent + margin) {
					final String inner = html.substring(tag.end(), elementEnd(html, tag.end()));
					if (isPlainItem(attrs) && !inner.contains("style=") && !inner.contains("dir=")
							&& DRAWN_TOKEN.matcher(inner.replaceAll("<[^>]*>", " ")).find()) {
						return true;
					}
				}
				++items;
				double itemC = 0, itemP = 0;
				final Matcher flex = STYLE_FLEX_SHORTHAND.matcher(attrs);
				if (flex.find() && "0".equals(flex.group(2))) {
					final double[] basis = linearLength(flex.group(3).trim(), font);
					final Matcher max = Pattern.compile("(?:^|[;\\s\"])" + maxMain + "\\s*:\\s*([^;\"']*)").matcher(attrs);
					String maxValue = null;
					while (max.find()) {
						maxValue = max.group(1).trim();
					}
					if (maxValue != null) {
						final double[] m = linearLength(maxValue, font);
						if (basis[1] == 0 && m[1] == 0 && m[0] > 0) {
							basis[0] = Math.min(basis[0], m[0]);
						} else if (basis[0] == 0 && m[0] == 0 && m[1] > 0) {
							basis[1] = Math.min(basis[1], m[1]);
						} else {
							basis[0] = basis[1] = 0;
						}
					}
					itemC = basis[0];
					itemP = basis[1];
				}
				if (tag.group(2).equalsIgnoreCase("table") && attrs.isBlank()) {
					final double table = tableMinContentLowerBound(html, tag.end(), font, collapsedTables);
					// Line length is definitively extent, so choose the larger value evaluated there.
					if (table > itemC + itemP * extent) {
						itemC = table;
						itemP = 0;
					}
				}
				c += itemC;
				p += itemP;
			}
			++depth;
		}
		return false;
	}

	/** Allowed item declarations: no main-axis position changes or text extending before the item start. */
	private static final Pattern PLAIN_ITEM_DECLARATION = Pattern
			.compile("\\s*(?:flex|width|min-width|max-width)\\s*:[^;]*");

	/** Whether item start-tag attributes have no style or only {@link #PLAIN_ITEM_DECLARATION}, and no dir. */
	private static boolean isPlainItem(final String attrs) {
		if (attrs.contains("dir=")) {
			return false;
		}
		final Matcher style = STYLE_ATTRIBUTE.matcher(attrs);
		if (!style.find()) {
			return true;
		}
		for (final String declaration : style.group(1).split(";")) {
			if (!declaration.isBlank() && !PLAIN_ITEM_DECLARATION.matcher(declaration).matches()) {
				return false;
			}
		}
		return true;
	}

	/** From {@code from} just after a start tag, return its matching end-tag position (document end if absent). */
	private static int elementEnd(final String html, final int from) {
		int depth = 0;
		final Matcher tag = TAG_OR_WM.matcher(html);
		tag.region(from, html.length());
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (depth == 0) {
					return tag.start();
				}
				--depth;
			} else if (!String.valueOf(tag.group(3)).endsWith("/")) {
				++depth;
			}
		}
		return html.length();
	}

	/**
	 * Return length as the linear expression {@code {constant pt, fraction}} (pt, em, %, {@code calc(%+pt)}). {0, 0} if unreadable.
	 */
	private static double[] linearLength(final String value, final double font) {
		final Matcher length = PT_OR_EM_LENGTH.matcher(value);
		if (length.matches()) {
			return new double[] { Double.parseDouble(length.group(1)) * ("em".equals(length.group(2)) ? font : 1), 0 };
		}
		final Matcher percent = PERCENT_LENGTH.matcher(value);
		if (percent.matches()) {
			return new double[] { 0, Double.parseDouble(percent.group(1)) / 100 };
		}
		final Matcher calc = CALC_PERCENT_PLUS_PT.matcher(value);
		if (calc.matches()) {
			return new double[] { Double.parseDouble(calc.group(2)), Double.parseDouble(calc.group(1)) / 100 };
		}
		return new double[] { 0, 0 };
	}

	/**
	 * Return a lower bound on a table's min-content line length ({@code from} is just after its start tag).
	 *
	 * <p>
	 * Place cells using HTML table grid placement (skip columns occupied by rowspans from earlier rows).
	 * For each row, sum minimum widths of cells spanning it, plus {@code border-spacing} between cells
	 * and at both ends (UA default 1.5 pt; separate-border tables only). Ignore rows with overlapping cells
	 * (table-model errors). Cell minimum width is the largest lower bound on an unbreakable word's advance
	 * (T+digits, excluding ruby annotations; T 0.6 em, digits 0.5 em), plus {@code td} borders of 1 pt × 2
	 * for separate-border tables. If the cell or its contents have style attributes
	 * (font, writing direction, or word breaking may change), count only borders, not words.
	 * Return zero if the document has declarations that break or hide words ({@link #BREAKABLE_TEXT_HINTS}).
	 * </p>
	 */
	static double tableMinContentLowerBound(final String html, final int from, final double font,
			final boolean collapsedTables) {
		final TableGrid grid = placeTableCells(html, from, font, collapsedTables);
		if (grid == null) {
			return 0;
		}
		double bound = 0;
		for (int r = 0; r < grid.rows(); ++r) {
			if (grid.overlappedRows().get(r)) {
				continue;
			}
			final List<Double> mins = new ArrayList<>();
			for (final PlacedCell cell : grid.cells()) {
				if (cell.row() <= r && r < cell.row() + cell.rowspan()) {
					mins.add(Double.valueOf(cell.min()));
				}
			}
			if (mins.isEmpty()) {
				continue;
			}
			double sum = (mins.size() + 1) * grid.spacing();
			for (final Double w : mins) {
				sum += w.doubleValue();
			}
			bound = Math.max(bound, sum);
		}
		return bound;
	}

	/**
	 * Cell placed in the table grid. {@code min} is the minimum-width lower bound;
	 * {@code attrs}/{@code content} are start-tag attributes/content; {@code from} is the content's document offset.
	 */
	private record PlacedCell(int row, int column, int colspan, int rowspan, double min, String attrs, String content,
			int from) {
	}

	/** Table grid. {@code spacing} is {@code border-spacing} between cells and at both ends. */
	private record TableGrid(List<PlacedCell> cells, int rows, int columns, java.util.BitSet overlappedRows,
			double spacing) {
	}

	/**
	 * Place a table's cells ({@code from} just after its start tag) using HTML table grid placement
	 * (skip columns occupied by rowspans from earlier rows). Treat nested-table cells as outer-cell content.
	 * If declarations break/hide words ({@link #BREAKABLE_TEXT_HINTS}), return {@code null}.
	 * Cell minimum-width lower bounds are described in {@link #tableMinContentLowerBound}.
	 *
	 * <p>
	 * Rowspans end at row-group boundaries (tbody, etc.). Following the second codex review counterexample
	 * on 2026-09-29 (with an empty slot before a short row's end, Copper failed to carry forward a later rowspan,
	 * putting cells in columns different from the grid), we also simulated Copper placement and withheld judgment
	 * on disagreement. Since engine fix c803652d that day ({@code TableSlotTracker} fills empty slots with anonymous
	 * cells), Copper follows the grid, so the simulation was removed on 2026-10-07 (fit seed 11766015:
	 * the simulation wrongly reported disagreement and stopped checking, but Copper and Chrome both followed
	 * the grid, placing nested-table text outside the paper).
	 * </p>
	 */
	private static TableGrid placeTableCells(final String html, final int from, final double font,
			final boolean collapsedTables) {
		for (final String hint : BREAKABLE_TEXT_HINTS) {
			if (html.contains(hint)) {
				return null;
			}
		}
		final boolean bordered = !collapsedTables && html.contains(SOLID_CELL_BORDER);
		final double spacing = collapsedTables || html.contains("border-spacing") ? 0 : 1.5;
		// Row → occupied columns.
		final List<java.util.BitSet> occupied = new ArrayList<>();
		final List<PlacedCell> cells = new ArrayList<>();
		final java.util.BitSet overlapped = new java.util.BitSet();
		// First cell of the row group.
		int groupCell = 0;
		int nested = 0, row = -1, column = 0, columns = 0, cellStart = -1;
		String cellAttrs = "";
		final Matcher tag = TAG_OR_WM.matcher(html);
		tag.region(from, html.length());
		while (tag.find()) {
			final boolean end = tag.group(1) != null;
			final String name = end ? tag.group(1) : tag.group(2);
			if (name.equalsIgnoreCase("table")) {
				if (end) {
					if (nested == 0) {
						break;
					}
					--nested;
				} else {
					++nested;
				}
				continue;
			}
			if (nested > 0) {
				continue;
			}
			// Do not estimate if rows/row groups have attributes (can change font size/writing direction; codex counterexample 6).
			if (!end && (name.equalsIgnoreCase("tr") || name.equalsIgnoreCase("tbody") || name.equalsIgnoreCase("thead")
					|| name.equalsIgnoreCase("tfoot")) && !String.valueOf(tag.group(3)).isBlank()) {
				return null;
			}
			if (end && (name.equalsIgnoreCase("tbody") || name.equalsIgnoreCase("thead")
					|| name.equalsIgnoreCase("tfoot"))) {
				// Do not carry rowspans across row-group boundaries: remove occupied slots beyond the final row and truncate cell rowspans.
				while (occupied.size() > row + 1) {
					occupied.remove(occupied.size() - 1);
				}
				if (overlapped.length() > row + 1) {
					overlapped.clear(row + 1, overlapped.length());
				}
				for (int i = groupCell; i < cells.size(); ++i) {
					final PlacedCell c = cells.get(i);
					if (c.row() + c.rowspan() > row + 1) {
						cells.set(i, new PlacedCell(c.row(), c.column(), c.colspan(), row + 1 - c.row(), c.min(), c.attrs(),
								c.content(), c.from()));
					}
				}
				groupCell = cells.size();
			} else if (!end && name.equalsIgnoreCase("tr")) {
				++row;
				column = 0;
				while (occupied.size() <= row) {
					occupied.add(new java.util.BitSet());
				}
			} else if (row >= 0 && (name.equalsIgnoreCase("td") || name.equalsIgnoreCase("th"))) {
				if (!end) {
					cellStart = tag.end();
					cellAttrs = String.valueOf(tag.group(3));
				} else if (cellStart >= 0) {
					final String content = html.substring(cellStart, tag.start());
					final int colspan = spanOf(cellAttrs, "colspan");
					final int rowspan = spanOf(cellAttrs, "rowspan");
					double min = bordered ? 2 : 0;
					if (!cellAttrs.contains("style") && onlyListStyles(content) && plainCellTags(content)) {
						// Nested-table minimum widths also contribute to the lower bound. A table reports its intrinsic minimum width unshrunk
						// (RetainedTableBuilder minLineSize). Seed 10760020 (2026-09-29).
						min += Math.max(longestWordAdvance(content) * font,
								nestedTablesLowerBound(html, cellStart, tag.start(), font, collapsedTables));
					}
					final java.util.BitSet here = occupied.get(row);
					while (here.get(column)) {
						++column;
					}
					for (int r = row; r < row + rowspan; ++r) {
						while (occupied.size() <= r) {
							occupied.add(new java.util.BitSet());
						}
						final java.util.BitSet slots = occupied.get(r);
						if (slots.get(column, column + colspan).cardinality() > 0) {
							overlapped.set(r);
						}
						slots.set(column, column + colspan);
					}
					cells.add(new PlacedCell(row, column, colspan, rowspan, min, cellAttrs, content, cellStart));
					column += colspan;
					columns = Math.max(columns, column);
					cellStart = -1;
				}
			}
		}
		return new TableGrid(cells, occupied.size(), columns, overlapped, spacing);
	}

	/**
	 * Return the largest minimum-width lower bound of attribute-free tables within cell content
	 * ({@code from}–{@code to}), or zero if none. The caller has verified that the cell and its content
	 * have no styles (containers do not narrow the width).
	 */
	private static double nestedTablesLowerBound(final String html, final int from, final int to, final double font,
			final boolean collapsedTables) {
		double bound = 0;
		for (int at = html.indexOf("<table>", from); at >= 0 && at < to; at = html.indexOf("<table>", at + 7)) {
			// Overlapping-cell tables yield no column lower bound (zero), but cell sums for non-overlapping rows remain valid lower bounds.
			// (2026-10-07, fit seed 11606508: nested-table T22 colspan 3 overlapped T21 rowspan 3's column,
			// dropping the nested contribution from outer column bounds and falsely flagging off-paper text. Chrome used the same position.)
			bound = Math.max(bound, Math.max(columnLowerBoundTotal(placeTableCells(html, at + 7, font, collapsedTables)),
					tableMinContentLowerBound(html, at + 7, font, collapsedTables)));
		}
		return bound;
	}

	private static final Pattern STYLE_ATTRIBUTE_VALUE = Pattern.compile("style=\"([^\"]*)\"");

	/** Tags preserving word-advance estimates (at least upright at body font size). Bold is wider; italic, small, sub, etc. are narrower. */
	private static final Set<String> PLAIN_CELL_TAGS = Set.of("div", "p", "ul", "ol", "li", "table", "thead", "tbody",
			"tfoot", "tr", "td", "th", "b", "strong", "ruby", "rb", "rt", "rp", "br");

	/**
	 * Whether cell content uses only {@link #PLAIN_CELL_TAGS} (2026-10-07, codex soundness counterexample:
	 * {@code <small>}'s UA {@code font-size:0.83em} makes words narrower than estimated).
	 */
	private static boolean plainCellTags(final String content) {
		final Matcher tag = TAG_OR_WM.matcher(content);
		while (tag.find()) {
			final String name = (tag.group(1) != null ? tag.group(1) : tag.group(2)).toLowerCase(java.util.Locale.ROOT);
			if (!PLAIN_CELL_TAGS.contains(name)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether content {@code style} contains only the generator's list {@code list-style-*} declarations (2026-10-07).
	 * Do not estimate cell minimum-width lower bounds when inner declarations can narrow width
	 * (e.g., explicitly sized boxes), but list marker type/position does not narrow it.
	 * Previously, cells containing {@code <ul style="list-style-position:…">}, and cells of nested tables
	 * containing them, were assigned zero.
	 */
	private static boolean onlyListStyles(final String content) {
		final Matcher m = STYLE_ATTRIBUTE_VALUE.matcher(content);
		while (m.find()) {
			for (final String declaration : m.group(1).split(";")) {
				final String d = declaration.trim();
				if (!d.isEmpty() && !d.startsWith("list-style-position:") && !d.startsWith("list-style-type:")) {
					return false;
				}
			}
		}
		return !content.replaceAll("style=\"[^\"]*\"", "").contains("style");
	}

	/**
	 * Per-column width lower bounds (maximum single-column cell minimum width + spacing; zero without such a cell).
	 * With separate borders, half the spacing goes on each side of a cell. In Copper, columns covered only
	 * by colspan cells may become zero, including spacing.
	 */
	private static double[] columnLowerBounds(final TableGrid grid) {
		final double[] column = new double[grid.columns()];
		for (final PlacedCell cell : grid.cells()) {
			if (cell.colspan() == 1) {
				column[cell.column()] = Math.max(column[cell.column()], cell.min() + grid.spacing());
			}
		}
		return column;
	}

	/** Table minimum-width lower bound (half-spacing at both ends + sum of {@link #columnLowerBounds}). Zero for overlapping cells. */
	private static double columnLowerBoundTotal(final TableGrid grid) {
		if (grid == null || !grid.overlappedRows().isEmpty()) {
			return 0;
		}
		double total = grid.spacing();
		for (final double w : columnLowerBounds(grid)) {
			total += w;
		}
		return total;
	}

	/**
	 * In Copper auto table sizing, if the minimum width is within this multiple of available width,
	 * shrink columns proportionally to fit (production {@code AutoColumnWidths.MIN_OVERFLOW_TOLERANCE};
	 * {@code FuzzOraclePredicateTest} verifies equality).
	 */
	static final double TABLE_SHRINK_TOLERANCE = 1.1;

	private static final Pattern STYLE_ONLY_ATTRIBUTE = Pattern.compile("\\s*style\\s*=\\s*\"([^\"]*)\"\\s*");
	/** Frame and margin declarations ({@code margin}, {@code padding}, {@code border}, and their individual sides). */
	private static final Pattern FRAME_DECLARATION = Pattern
			.compile("\\s*(?:margin|padding|border)(?:-[a-z]+)*\\s*:\\s*([^;]*)");

	/**
	 * Whether an ancestor may wrap a table: a div whose only attribute is style, with only frame/margin declarations
	 * (no negative values or {@code calc()}). It only shifts the table start right (down in vertical writing),
	 * never backward; without width declarations, available width cannot exceed its parent's
	 * (seed 10760020, 2026-09-29: table inside a div with {@code margin:7pt;padding:2pt;border:1pt solid black}).
	 */
	static boolean isPlainWrapper(final String name, final String attrs) {
		return isPlainWrapper(name, attrs, false);
	}

	/** Block-axis dimensions ({@code width} in vertical writing, {@code height} in horizontal, and min/max). Do not change line length. */
	private static final Pattern BLOCK_SIZE_DECLARATION_VERTICAL = Pattern
			.compile("\\s*(?:min-|max-)?width\\s*:[^;]*");
	private static final Pattern BLOCK_SIZE_DECLARATION_HORIZONTAL = Pattern
			.compile("\\s*(?:min-|max-)?height\\s*:[^;]*");

	/**
	 * Extend {@link #isPlainWrapper(String, String)} with declarations changing neither line length nor start position
	 * (2026-10-07, fit seed 11898581: table inside a vertical-writing div with
	 * {@code float:none;width:8em;min-width:8em;max-width:90%}).
	 * Allow {@code float:none}, {@code position:static}, and block-axis dimensions in the div's writing direction
	 * ({@code width} properties for vertical writing, {@code height} properties for horizontal writing).
	 */
	static boolean isPlainWrapper(final String name, final String attrs, final boolean vertical) {
		if (!name.equalsIgnoreCase("div")) {
			return false;
		}
		if (attrs.isBlank()) {
			return true;
		}
		final Matcher style = STYLE_ONLY_ATTRIBUTE.matcher(attrs);
		if (!style.matches()) {
			return false;
		}
		for (final String declaration : style.group(1).split(";")) {
			if (declaration.isBlank() || declaration.trim().equals("float:none") || declaration.trim().equals("position:static")
					|| (vertical ? BLOCK_SIZE_DECLARATION_VERTICAL : BLOCK_SIZE_DECLARATION_HORIZONTAL).matcher(declaration)
							.matches()) {
				continue;
			}
			final Matcher frame = FRAME_DECLARATION.matcher(declaration);
			if (!frame.matches() || frame.group(1).contains("-") || frame.group(1).contains("calc")) {
				return false;
			}
		}
		return true;
	}

	/** Allowed attributes on a cell used as evidence (generator template). */
	private static final Pattern PLAIN_CELL_ATTRIBUTES = Pattern
			.compile("(?:\\s+(?:colspan|rowspan)\\s*=\\s*\"\\d+\")*\\s*");

	/**
	 * For an attribute-free table ({@code from} just after its start tag), return whether a text-bearing cell
	 * must be painted off-paper: whether a cell with directly written words starts in a column where
	 * preceding column minimum-width lower bounds plus {@code border-spacing} exceed {@code extent + margin}
	 * (content width + margin; beyond this lies off-paper) (seed 10376223, 2026-09-29).
	 *
	 * <p>
	 * <b>Check per column.</b> {@link #tableMinContentLowerBound} (cell sums per row) bounds table width,
	 * but cannot identify which cells start off-paper. Summing column minimum-width lower bounds from the start
	 * bounds each cell's start. Each column's lower bound is the largest minimum width of a cell occupying
	 * only that column (colspan 1). Even when colspan cells combine columns in a row, another row's cells
	 * determine column widths (in seed 10376223, T25–T27 determine the three columns under T1's colspan 3,
	 * and T4's column starts at 115 pt). With {@code auto} table width, even {@code table-layout:fixed}
	 * uses automatic layout (CSS 2.1 §17.5.2.1), and columns cannot be narrower.
	 * Ignoring colspan cells keeps this a lower bound.
	 * </p>
	 *
	 * <p>
	 * <b>Match Copper's table-width rules</b> (codex review counterexample on 2026-09-29).
	 * Separate borders place half-spacing on each cell side and at each table end, so the column bound is
	 * "cell minimum width + spacing", or zero without a single-column cell.
	 * If the table minimum width is within {@link #TABLE_SHRINK_TOLERANCE} times available width,
	 * Copper shrinks columns proportionally to fit the type area (an intentional difference from Chrome
	 * for print quality). Judge only when the summed lower bounds exceed that tolerance.
	 * </p>
	 *
	 * <p>
	 * <b>Restrict to definite table placement.</b> The table has no attributes (content determines width)
	 * and is directly under body or within divs with only frames/margins ({@link #isPlainWrapper}; seed 10760020).
	 * Its left edge is at or after the content start; columns run from the writing-direction start.
	 * Wrapper divs specify no width, so available width is at most paper content width, which suffices
	 * for the tolerance check. A nested table's minimum-width lower bound contributes to its cell's bound
	 * ({@link #placeTableCells}). As with {@link #flexLineOverflows}, do not judge documents with floats,
	 * {@code display:none}, {@code visibility:hidden}/{@code collapse}, absolute positioning, or displacements.
	 * Evidence cells have only colspan/rowspan attributes, content beginning with a word
	 * (the generator puts words first in cells), and no inner style/dir
	 * (no declarations moving text before the cell start).
	 * </p>
	 *
	 * <p>
	 * Expanded on 2026-10-07 (stops from fit 11,750,000 onward): wrapper divs also allow block-axis dimensions
	 * for their writing direction ({@link #isPlainWrapper(String, String, boolean)}); allow up to one float
	 * directly wrapping the table (overload below); column-boundary lower bounds include colspan cells
	 * ({@link #boundaryLowerBounds}); judge overlapping-cell tables too, and use nested-table cells as evidence
	 * ({@link #cellBeyondFromStart}).
	 * </p>
	 */
	static boolean tableColumnBeyondPage(final String html, final int from, final double extent, final double margin,
			final double font, final boolean collapsedTables) {
		return tableColumnBeyondPage(html, from, extent, margin, font, collapsedTables, NOT_FLOATED);
	}

	/** For {@link #tableColumnBeyondPage}, {@code floatSide} means no float wraps the table. */
	static final char NOT_FLOATED = 0;

	/**
	 * Extend {@link #tableColumnBeyondPage} with the side of a float directly wrapping the table
	 * ({@link #floatWrapperSide}; horizontal writing only) (2026-10-07, fit seed 11866613).
	 * It must be the document's only float. Left floats align to the content start, like un-floated tables.
	 * For a right float wider than the paper, Copper aligns to content start and overflows toward the end,
	 * while Chrome aligns to the end and overflows toward the start, per CSS 2.1 §9.5.1 rule 9.
	 * To hold under either placement, require both start-side evidence (cell-start lower bound measured
	 * from the table start lies beyond the paper edge) and end-side evidence
	 * (cell-end upper bound lies before the paper start when table end is placed at content width).
	 */
	static boolean tableColumnBeyondPage(final String html, final int from, final double extent, final double margin,
			final double font, final boolean collapsedTables, final char floatSide) {
		int floats = 0;
		for (final Matcher f = FLOAT_NOT_NONE.matcher(html); f.find();) {
			++floats;
		}
		if (html.contains("display:none") || html.contains("visibility:hidden") || html.contains("visibility:collapse")
				|| floats > (floatSide == NOT_FLOATED ? 0 : 1) || OUT_OF_FLOW.matcher(html).find()
				|| POSITION_OFFSET.matcher(html).find()) {
			return false;
		}
		// Table-part rules (table/rows/cells) must contain only frame, margin, and border-model declarations. Do not estimate
		// with width (can activate fixed layout), max-width (caps cell minimum width), font size, etc. (codex counterexample).
		if (!plainTableRules(html)) {
			return false;
		}
		final TableGrid grid = placeTableCells(html, from, font, collapsedTables);
		// If table minimum width is within the line-length tolerance, Copper shrinks columns to fit.
		if (grid == null || boundaryLowerBounds(grid)[grid.columns()] <= TABLE_SHRINK_TOLERANCE * extent) {
			return false;
		}
		final boolean bordered = !collapsedTables && html.contains(SOLID_CELL_BORDER);
		final boolean fromStart = cellBeyondFromStart(html, grid, 0, extent + margin, font, collapsedTables, bordered);
		return floatSide == 'R' ? fromStart && cellBeforeFromEnd(grid, extent, -margin) : fromStart;
	}

	/**
	 * Lower bounds on column-boundary positions from table start: boundary j starts column j;
	 * boundary {@code columns} is table end = table width.
	 * In a separate-border table, a cell spanning {@code k} columns from j has the sum of column widths
	 * and intervening spacing, at least its minimum width. Thus boundary {@code j+k} is at least
	 * "cell minimum width + spacing" beyond boundary j. Include colspan cells, not just single-column cells
	 * (2026-10-07, fit seed 11866613: only a colspan 3 cell carried the nested table's minimum width).
	 *
	 * <p>
	 * Include overlapping-cell tables too (colspan overlaps a rowspan from above: a table-model error).
	 * Copper (since c803652d) and Chrome use the same HTML cell-start placement: skip occupied columns,
	 * start there, then advance by colspan. Overlapping cells still have their own widths
	 * (in seed 11866613, T20 is in column 7 in both Copper and Chrome).
	 * </p>
	 */
	private static double[] boundaryLowerBounds(final TableGrid grid) {
		final int columns = grid.columns();
		final double[] s = new double[columns + 1];
		s[0] = grid.spacing();
		for (int j = 0; j <= columns; ++j) {
			if (j > 0) {
				s[j] = Math.max(s[j], s[j - 1]);
			}
			for (final PlacedCell cell : grid.cells()) {
				if (cell.column() == j) {
					final int e = Math.min(columns, j + cell.colspan());
					s[e] = Math.max(s[e], s[j] + cell.min() + grid.spacing());
				}
			}
		}
		return s;
	}

	/** {@link #boundaryLowerBounds} viewed from table end: upper bounds on boundary positions with table end at {@code end}. */
	private static double[] boundaryUpperBoundsFromEnd(final TableGrid grid, final double end) {
		final int columns = grid.columns();
		final double[] u = new double[columns + 1];
		java.util.Arrays.fill(u, Double.POSITIVE_INFINITY);
		u[columns] = end;
		for (int j = columns; j >= 0; --j) {
			if (j < columns) {
				u[j] = Math.min(u[j], u[j + 1]);
			}
			for (final PlacedCell cell : grid.cells()) {
				if (Math.min(columns, cell.column() + cell.colspan()) == j) {
					u[cell.column()] = Math.min(u[cell.column()], u[j] - cell.min() - grid.spacing());
				}
			}
		}
		return u;
	}

	/** Document {@code <style>} rules (selectors and declarations). */
	private static final Pattern STYLE_RULE = Pattern.compile("([^{}]*)\\{([^}]*)\\}");
	/** Selectors matching table parts. */
	private static final Pattern TABLE_PART_SELECTOR = Pattern
			.compile("(?<![\\w-])(?:table|caption|thead|tbody|tfoot|tr|td|th|col|colgroup)(?![\\w-])");
	/** Allowed table-part rule declarations (frames, margins, border model). Only those that widen or preserve width. */
	private static final Pattern TABLE_PART_DECLARATION = Pattern
			.compile("\\s*(?:border(?:-[a-z]+)*|margin(?:-[a-z]+)*|padding(?:-[a-z]+)*|table-layout)\\s*:[^;]*");

	/**
	 * Whether document {@code <style>} rules selecting table parts contain only frame, margin, border-model declarations,
	 * and {@code table-layout}. Disallow {@code table} width: it can activate fixed layout
	 * (columns do not expand with content) (2026-10-07, codex soundness counterexample).
	 */
	static boolean plainTableRules(final String html) {
		final int open = html.indexOf("<style>");
		final int close = html.indexOf("</style>", Math.max(open, 0));
		if (open < 0 || close < 0) {
			return true;
		}
		final Matcher rule = STYLE_RULE.matcher(html.substring(open + 7, close));
		while (rule.find()) {
			if (!TABLE_PART_SELECTOR.matcher(rule.group(1)).find()) {
				continue;
			}
			for (final String declaration : rule.group(2).split(";")) {
				if (!declaration.isBlank() && !TABLE_PART_DECLARATION.matcher(declaration).matches()) {
					return false;
				}
			}
		}
		return true;
	}
	/** Generator cell-border rule. Borders such as {@code none} have zero width; count only solid borders (codex counterexample 5). */
	private static final String SOLID_CELL_BORDER = "td{border:1pt solid";

	/** Evidence cell: only colspan/rowspan attributes, starts with a word, no inner style/dir (text cannot precede cell start). */
	private static boolean evidenceCell(final PlacedCell cell) {
		return PLAIN_CELL_ATTRIBUTES.matcher(cell.attrs()).matches() && DRAWN_TOKEN.matcher(cell.content()).lookingAt()
				&& !cell.content().contains("style=") && !cell.content().contains("dir=");
	}

	/** Generator nested-table shape: word, then div with only {@code data-fuzz-role="cell-child"}, then a direct attribute-free table. */
	private static final Pattern NESTED_TABLE_IN_CELL = Pattern
			.compile("\\s*T\\d+\\s*<div data-fuzz-role=\"cell-child\">\\s*<table>");

	/**
	 * With table start at {@code origin}, whether an evidence cell's start lower bound lies beyond {@code limit}.
	 * For nested-table cells ({@link #NESTED_TABLE_IN_CELL} shape, cell attributes only colspan/rowspan),
	 * use the outer cell start (+ border) as the nested table start and apply the same check
	 * (2026-10-07, fit seed 11766015: all outer cells started on-paper, but nested-table text
	 * inside the final column's cell went off-paper). The outer table cannot shrink (tolerance check),
	 * so cells cannot be narrower than the nested table's minimum width, and the inner table cannot shrink either.
	 */
	private static boolean cellBeyondFromStart(final String html, final TableGrid grid, final double origin,
			final double limit, final double font, final boolean collapsedTables, final boolean bordered) {
		final double[] boundary = boundaryLowerBounds(grid);
		for (final PlacedCell cell : grid.cells()) {
			final double start = origin + boundary[cell.column()];
			if (start > limit && evidenceCell(cell)) {
				return true;
			}
			final Matcher nested = NESTED_TABLE_IN_CELL.matcher(cell.content());
			if (PLAIN_CELL_ATTRIBUTES.matcher(cell.attrs()).matches() && nested.lookingAt()) {
				final TableGrid inner = placeTableCells(html, cell.from() + nested.end(), font, collapsedTables);
				if (inner != null && cellBeyondFromStart(html, inner,
						start + (bordered ? 1 : 0), limit, font, collapsedTables, bordered)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * With table end at {@code origin}, whether an evidence cell's end upper bound lies before {@code limit}.
	 * Cell end is table end minus actual widths and spacing of later columns. Actual column widths exceed
	 * their lower bounds, so subtracting lower bounds yields an upper bound.
	 */
	private static boolean cellBeforeFromEnd(final TableGrid grid, final double origin, final double limit) {
		final double[] boundary = boundaryUpperBoundsFromEnd(grid, origin);
		for (final PlacedCell cell : grid.cells()) {
			final double end = boundary[Math.min(grid.columns(), cell.column() + cell.colspan())] - grid.spacing();
			if (end < limit && evidenceCell(cell)) {
				return true;
			}
		}
		return false;
	}

	/** Float side ({@code float:left|right}). */
	private static final Pattern FLOAT_SIDE_DECLARATION = Pattern.compile("\\s*float\\s*:\\s*(left|right)\\s*");

	/**
	 * Whether a float may directly wrap a table: a div whose only attribute is style,
	 * with only {@code float:left|right} and {@code position:static} declarations (no width/margins).
	 * Return the side as {@code 'L'}/{@code 'R'}, or {@link #NOT_FLOATED} if ineligible.
	 */
	static char floatWrapperSide(final String name, final String attrs) {
		if (!name.equalsIgnoreCase("div")) {
			return NOT_FLOATED;
		}
		final Matcher style = STYLE_ONLY_ATTRIBUTE.matcher(attrs);
		if (!style.matches()) {
			return NOT_FLOATED;
		}
		char side = NOT_FLOATED;
		for (final String declaration : style.group(1).split(";")) {
			if (declaration.isBlank() || declaration.trim().equals("position:static")) {
				continue;
			}
			final Matcher f = FLOAT_SIDE_DECLARATION.matcher(declaration);
			if (!f.matches() || side != NOT_FLOATED) {
				return NOT_FLOATED;
			}
			side = f.group(1).equals("left") ? 'L' : 'R';
		}
		return side;
	}

	private static int spanOf(final String attrs, final String name) {
		final Matcher m = Pattern.compile(name + "\\s*=\\s*\"(\\d+)\"").matcher(attrs);
		return m.find() ? Math.max(1, Integer.parseInt(m.group(1))) : 1;
	}

	/** Largest unbreakable-word advance lower bound (T+digit sequences), in em. Tags break words; omit ruby annotations. */
	private static double longestWordAdvance(final String content) {
		final String text = content.replaceAll("(?s)<rt>.*?</rt>", " ").replaceAll("<[^>]*>", " ");
		double longest = 0, word = 0;
		for (int i = 0; i < text.length(); ++i) {
			final char ch = text.charAt(i);
			if (ch == 'T') {
				word += 0.6;
			} else if (ch >= '0' && ch <= '9') {
				word += 0.5;
			} else {
				word = 0;
			}
			longest = Math.max(longest, word);
		}
		return longest;
	}

	/**
	 * Return the length (pt) of the <b>last</b> declaration in style. CSS is last-wins, and the generator repeats
	 * properties, e.g., {@code width:30pt;width:80%}. If the last value is a percentage, {@code calc()}, or keyword,
	 * it cannot be determined statically: return {@code fallback} (= same as parent), not the old pt value.
	 */
	static double lastLength(final Pattern declaration, final String attrs, final double font, final double fallback) {
		final Matcher m = declaration.matcher(attrs);
		String last = null;
		while (m.find()) {
			last = m.group(1).trim();
		}
		if (last == null) {
			return fallback;
		}
		final Matcher length = PT_OR_EM_LENGTH.matcher(last);
		return length.matches() ? Double.parseDouble(length.group(1)) * ("em".equals(length.group(2)) ? font : 1)
				: fallback;
	}

	/** {@code writing-mode} in the {@code body} rule (determines paper axes). */
	private static final Pattern BODY_WRITING_MODE = Pattern
			.compile("body\\s*\\{[^}]*writing-mode\\s*:\\s*([a-z-]+)");

	/**
	 * Return whether the paper's page axis is vertical (y). If {@code body} uses vertical writing,
	 * the page axis is <b>horizontal (x)</b>, and the inline axis is vertical (y).
	 */
	static boolean pageAxisIsY(final String html) {
		final Matcher m = BODY_WRITING_MODE.matcher(html);
		return !(m.find() && m.group(1).startsWith("vertical"));
	}

	private static double maxOf(final Pattern pattern, final String html) {
		double max = 0;
		final Matcher m = pattern.matcher(html);
		while (m.find()) {
			max = Math.max(max, Double.parseDouble(m.group(1)));
		}
		return max;
	}

	/** Unique token (no whitespace, to prevent splitting at line breaks). */
	private static String token(final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable) {
		final String t = "T" + (counter[0]++);
		tokens.add(t);
		if (inReorderable) {
			reorderable.add(t);
		}
		return t;
	}

	/**
	 * <b>Modifier that independently varies layout-determining properties</b> (2026-08-02).
	 *
	 * <p>
	 * The generator is a collection of "HTML written per pattern", with properties baked into patterns:
	 * absolutely positioned boxes were always plain {@code div}s, floats always {@code div}s, etc.
	 * Thus <b>cross-pattern combinations (position × display × float × writing-mode) were never generated</b>;
	 * even two million documents could not reach a crash with `position:absolute` and `display:flex`
	 * (found on yahoo.co.jp on 2026-08-02). Vary these independently here to create the Cartesian product
	 * without adding patterns.
	 * </p>
	 *
	 * <p>
	 * STRICT (which checks content preservation) does not select values that hide content
	 * (`display:none`, `visibility:hidden`) or move it off-paper.
	 * </p>
	 */
	private static String layoutMods(final Random r, final Random extensionRandom, final boolean strict) {
		// Leave half unmodified (keep generating simple documents too).
		if (r.nextBoolean()) {
			return "";
		}
		final StringBuilder mods = new StringBuilder();
		final String[] displays = strict
				? new String[] { "block", "inline-block", "flex", "grid", "list-item", "table", "inline" }
				: new String[] { "block", "inline-block", "flex", "grid", "list-item", "table", "table-row",
						"table-cell", "inline", "none" };
		if (r.nextBoolean()) {
			mods.append("display:").append(displays[r.nextInt(displays.length)]).append(';');
		}
		if (r.nextBoolean()) {
			// **Do not select values that move content out of paper order in STRICT.** Absolute positioning,
			// page floats, and footnotes legitimately violate STRICT's assumption that content
			// "appears exactly once in document order" (reading-order, duplication, and loss checks),
			// so reserve them for WILD (only crashes, termination, and page count).
			final String[] positions = strict ? new String[] { "static", "relative" }
					: new String[] { "static", "relative", "absolute" };
			final String position = positions[r.nextInt(positions.length)];
			mods.append("position:").append(position).append(';');
			// STRICT covers documents where "the author cannot intend to move content off-paper".
			// Keep relative itself, but top/left can move all content off-paper from the vertical-writing start edge,
			// so generate displacements only in WILD.
			if (!position.equals("static")) {
				final int top = r.nextInt(40) - 10;
				final int left = r.nextInt(40) - 10;
				if (!strict) {
					mods.append("top:").append(top).append("pt;left:").append(left).append("pt;");
				}
			}
		}
		if (r.nextBoolean()) {
			// footnote/top/bottom are page-level floats (added 2026-07-31 and 08-02).
			final String[] floats = strict ? new String[] { "none", "left", "right" }
					: new String[] { "none", "left", "right", "footnote", "top", "bottom", "start", "end" };
			mods.append("float:").append(floats[r.nextInt(floats.length)]).append(';');
		}
		if (r.nextBoolean()) {
			mods.append("writing-mode:").append(WRITING_MODES[r.nextInt(WRITING_MODES.length)]).append(';');
		}
		if (!strict && r.nextBoolean()) {
			mods.append("overflow:hidden;visibility:")
					.append(r.nextBoolean() ? "hidden" : "visible").append(';');
		}
		if (mods.length() == 0) {
			return "";
		}
		// Occasionally specify dimensions; without them, flex/grid paths get sparse coverage.
		if (r.nextBoolean()) {
			mods.append("width:").append(20 + r.nextInt(120)).append("pt;");
		}
		final boolean flex = mods.indexOf("display:flex;") >= 0;
		final boolean grid = mods.indexOf("display:grid;") >= 0;
		if ((flex || grid) && extensionRandom != null) {
			appendLayoutProperties(mods, extensionRandom, grid);
		}
		if (extensionRandom != null && extensionRandom.nextInt(4) == 0) {
			mods.append(sizeMods(extensionRandom));
		}
		return mods.toString();
	}

	/** v2 size vocabulary. An 8 em floor avoids bias toward untypesettably narrow widths. */
	private static String sizeMods(final Random r) {
		return switch (r.nextInt(6)) {
		case 0 -> "width:" + (30 + r.nextInt(51)) + "%;min-width:8em;max-width:90%;";
		case 1 -> "width:8em;min-width:8em;max-width:90%;";
		case 2 -> "width:min-content;min-width:8em;max-width:90%;";
		case 3 -> "width:max-content;min-width:8em;max-width:90%;";
		case 4 -> "width:fit-content(70%);min-width:8em;max-width:90%;";
		default -> "width:calc(35% + 8em);min-width:8em;max-width:90%;";
		};
	}

	/** Flex/grid-specific direction, wrapping, tracks, and gaps. */
	private static void appendLayoutProperties(final StringBuilder mods, final Random r, final boolean grid) {
		if (grid) {
			final String[] tracks = { "repeat(2,minmax(12pt,1fr))", "24pt 1fr",
					"min-content max-content", "fit-content(48pt) minmax(12pt,1fr)" };
			mods.append("grid-template-columns:").append(tracks[r.nextInt(tracks.length)]).append(';');
		} else {
			final String[] directions = { "row", "row-reverse", "column", "column-reverse" };
			final String[] wraps = { "nowrap", "wrap", "wrap-reverse" };
			mods.append("flex-direction:").append(directions[r.nextInt(directions.length)]).append(';')
					.append("flex-wrap:").append(wraps[r.nextInt(wraps.length)]).append(';');
		}
		final String[] gaps = { "0pt", "2pt", ".5em", "calc(1pt + .25em)" };
		mods.append("gap:").append(gaps[r.nextInt(gaps.length)]).append(';');
	}

	/** Number of generatable node types (STRICT excludes types that move content). */
	private static int nodeKinds(final boolean strict) {
		return strict ? 13 : 15;
	}

	private static void appendNode(final StringBuilder s, final Random r, final Random extensionRandom,
			final int depth, final boolean strict, final List<String> tokens, final int[] counter,
			final Set<String> reorderable, final boolean inReorderable) {
		appendNode(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable, -1);
	}

	private static void appendNode(final StringBuilder s, final Random r, final Random extensionRandom,
			final int depth, final boolean strict, final List<String> tokens, final int[] counter,
			final Set<String> reorderable, final boolean inReorderable, final int forcedKind) {
		final String mods = layoutMods(r, extensionRandom, strict);
		if (!mods.isEmpty()) {
			// Apply modifiers as wrappers (create combinations without breaking pattern declarations).
			//
			// **If made a float, exclude its contents from reading-order checks**
			// (2026-08-03). A float that does not fit moves to the next page,
			// while subsequent body text stays on the current page. Document/page order disagreement
			// follows the CSS specification and is not a defect. The dedicated generation path
			// (`<div style="float:left;width:..">`) already created
			// children with {@code inReorderable=true}, but floats introduced
			// through this modifier were missed. Strict seeds
			// 6/9/12/15 actually produced false "reading order reversed" reports
			// (T28 was inside `float:right`).
			final boolean floated = mods.contains("float:") && !mods.contains("float:none");
			final boolean positioned = mods.contains("position:absolute");
			final boolean flex = mods.contains("display:flex;");
			final boolean grid = mods.contains("display:grid;");
			// Reverse flex legitimately changes reading order (row/column-reverse on the main axis,
			// wrap-reverse for cross-axis line order). Mark reorderable like float/absolute.
			final boolean reversedFlex = mods.contains("-reverse");
			final int items = (flex || grid) && extensionRandom != null ? 2 + extensionRandom.nextInt(3) : 1;
			s.append("<div style=\"").append(mods).append("\">\n");
			// Generate the first item using the same r as v1, preserving old-sequence consumption order.
			appendPlainNode(s, r, extensionRandom, depth, strict, tokens, counter, reorderable,
					inReorderable || floated || positioned || reversedFlex, forcedKind);
			for (int i = 1; i < items; ++i) {
				appendLayoutItem(s, extensionRandom, depth, strict, tokens, counter, reorderable,
						inReorderable || floated || positioned || reversedFlex, flex, grid);
			}
			s.append("</div>\n");
			return;
		}
		appendPlainNode(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable,
				forcedKind);
	}

	/** Extra flex/grid items. Use only the extension sequence, producing two to four direct children. */
	private static void appendLayoutItem(final StringBuilder s, final Random r, final int depth,
			final boolean strict, final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable, final boolean flex, final boolean grid) {
		s.append("<div data-fuzz-role=\"layout-item\" style=\"");
		if (flex) {
			final String[] bases = { "auto", "content", "24pt", "35%", "8em", "calc(25% + 8pt)" };
			s.append("flex:").append(r.nextInt(3)).append(' ').append(r.nextInt(3)).append(' ')
					.append(bases[r.nextInt(bases.length)]).append(';');
		} else if (grid && r.nextInt(4) == 0) {
			s.append("grid-column:span 2;");
		}
		if (r.nextBoolean()) {
			s.append(sizeMods(r));
		}
		s.append("\">\n");
		appendNode(s, r, r, depth - 1, strict, tokens, counter, reorderable, inReorderable);
		s.append("</div>\n");
	}

	/** Multi-item flex/grid explicitly generated inside a table cell. */
	private static void appendLayoutContainer(final StringBuilder s, final Random r, final int depth,
			final boolean strict, final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable, final boolean grid) {
		final StringBuilder mods = new StringBuilder(grid ? "display:grid;" : "display:flex;");
		appendLayoutProperties(mods, r, grid);
		if (r.nextBoolean()) {
			mods.append(sizeMods(r));
		}
		final boolean reversedFlex = !grid && mods.indexOf("-reverse") >= 0;
		s.append("<div style=\"").append(mods).append("\">\n");
		final int items = 2 + r.nextInt(3);
		for (int i = 0; i < items; ++i) {
			appendLayoutItem(s, r, depth, strict, tokens, counter, reorderable,
					inReorderable || reversedFlex, !grid, grid);
		}
		s.append("</div>\n");
	}

	private static void appendPlainNode(final StringBuilder s, final Random r, final Random extensionRandom,
			final int depth, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable, final int forcedKind) {
		if (depth <= 0) {
			s.append("<p id=\"p").append(counter[0]).append("\">")
					.append(token(tokens, counter, reorderable, inReorderable)).append("</p>\n");
			return;
		}
		final int kind = forcedKind >= 0 ? forcedKind : r.nextInt(nodeKinds(strict));
		switch (kind) {
		case 0 -> { // Paragraph (multiple tokens).
			s.append("<p id=\"p").append(counter[0]).append("\">");
			final int n = 1 + r.nextInt(6);
			for (int i = 0; i < n; ++i) {
				s.append(token(tokens, counter, reorderable, inReorderable)).append(' ');
			}
			s.append("</p>\n");
		}
		case 1 -> { // Nested block.
			s.append("<div style=\"margin:").append(r.nextInt(8)).append("pt;padding:").append(r.nextInt(6))
					.append("pt;border:").append(r.nextInt(3)).append("pt solid black\">\n");
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 2 -> { // Float.
			s.append("<div style=\"float:").append(r.nextBoolean() ? "left" : "right").append(";width:")
					.append(10 + r.nextInt(120)).append("pt\">\n");
			// Float contents **legitimately** change reading order (rise alongside earlier lines),
			// so exclude them from invariant 7.
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, true);
			s.append("</div>\n");
		}
		case 3 -> { // Table (rowspan/colspan; recursive children in at most one cell).
			final int rows = 1 + r.nextInt(4);
			final int cols = 1 + r.nextInt(4);
			// Limit to one cell per table to prevent exponential DOM growth even with nested tables.
			final int richCell = extensionRandom != null && depth > 1 && extensionRandom.nextInt(3) == 0
					? extensionRandom.nextInt(rows * cols) : -1;
			int cell = 0;
			s.append("<table><tbody>\n");
			for (int y = 0; y < rows; ++y) {
				s.append("<tr>");
				for (int x = 0; x < cols; ++x) {
					s.append("<td");
					if (r.nextInt(4) == 0) {
						s.append(" colspan=\"").append(1 + r.nextInt(3)).append('"');
					}
					if (r.nextInt(4) == 0) {
						s.append(" rowspan=\"").append(1 + r.nextInt(3)).append('"');
					}
					s.append('>').append(token(tokens, counter, reorderable, inReorderable));
					if (cell++ == richCell) {
						appendRichCellChild(s, extensionRandom, depth - 1, strict, tokens, counter, reorderable,
								inReorderable);
					}
					s.append("</td>");
				}
				s.append("</tr>\n");
			}
			s.append("</tbody></table>\n");
		}
		case 4 -> { // Multi-column layout.
			s.append("<div style=\"column-count:").append(2 + r.nextInt(3)).append(";column-gap:")
					.append(r.nextInt(20)).append("pt\">\n");
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 5 -> { // Nested writing directions.
			s.append("<div style=\"writing-mode:").append(WRITING_MODES[r.nextInt(WRITING_MODES.length)])
					.append("\">\n");
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 6 -> { // Inline-block / large font (entry point for rescue splitting).
			s.append("<p><span style=\"display:inline-block;width:").append(10 + r.nextInt(200)).append("pt;height:")
					.append(10 + r.nextInt(200)).append("pt\">").append(token(tokens, counter, reorderable, inReorderable))
					.append("</span></p>\n");
		}
		case 7 -> { // List (markers / list-style).
			final String tag = r.nextBoolean() ? "ul" : "ol";
			s.append('<').append(tag).append(" style=\"list-style-position:")
					.append(r.nextBoolean() ? "inside" : "outside").append(";list-style-type:")
					.append(new String[] { "disc", "decimal", "lower-roman", "cjk-ideographic", "none" }[r.nextInt(5)])
					.append("\">\n");
			final int n = 1 + r.nextInt(4);
			for (int i = 0; i < n; ++i) {
				s.append("<li>").append(token(tokens, counter, reorderable, inReorderable)).append("</li>\n");
			}
			s.append("</").append(tag).append(">\n");
		}
		case 8 -> { // Ruby (short units and long units requiring line breaks).
			s.append("<p>");
			final int n = 1 + r.nextInt(3);
			for (int i = 0; i < n; ++i) {
				s.append("<ruby>").append(token(tokens, counter, reorderable, inReorderable)).append("<rt>")
						.append(token(tokens, counter, reorderable, inReorderable)).append("</rt></ruby>");
			}
			if (extensionRandom != null && extensionRandom.nextInt(3) == 0) {
				s.append("<ruby class=\"fuzz-long-ruby\">");
				final int baseLength = 8 + extensionRandom.nextInt(9);
				for (int i = 0; i < baseLength; ++i) {
					if (i > 0) {
						s.append(' ');
					}
					s.append(token(tokens, counter, reorderable, inReorderable));
				}
				s.append("<rt>");
				final int rubyLength = 8 + extensionRandom.nextInt(9);
				for (int i = 0; i < rubyLength; ++i) {
					if (i > 0) {
						s.append(' ');
					}
					s.append(token(tokens, counter, reorderable, inReorderable));
				}
				s.append("</rt></ruby>");
			}
			s.append("</p>\n");
		}
		case 9 -> { // Replaced element (image; original motivation for rescue splitting).
			// alt is not painted, so do not make it a token (would cause oracle false positives).
			s.append("<p><img src=\"").append(RED_PNG_URI).append("\" alt=\"img\"")
					.append(" style=\"display:").append(r.nextBoolean() ? "block" : "inline")
					.append(";width:").append(10 + r.nextInt(250)).append("pt;height:")
					.append(10 + r.nextInt(250)).append("pt\" /></p>\n");
		}
		case 10 -> { // clear and extreme font sizes (interact with floats).
			s.append("<div style=\"clear:")
					.append(new String[] { "left", "right", "both" }[r.nextInt(3)]).append("\">")
					.append("<span style=\"font-size:").append(6 + r.nextInt(40)).append("pt\">")
					.append(token(tokens, counter, reorderable, inReorderable)).append("</span></div>\n");
		}
		case 11 -> { // avoid hints (exercise page-break decisions).
			s.append("<div style=\"page-break-inside:avoid;margin:").append(r.nextInt(6)).append("pt\">\n");
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 12 -> { // Form controls (value text is not painted, so do not make it a token).
			s.append("<form>");
			final int n = 1 + r.nextInt(3);
			for (int i = 0; i < n; ++i) {
				switch (r.nextInt(6)) {
				case 0 -> s.append("<input type=\"text\" value=\"x\" size=\"")
						.append(1 + r.nextInt(20)).append("\" />");
				case 1 -> s.append("<input type=\"checkbox\" checked=\"checked\" />");
				case 2 -> s.append("<input type=\"radio\" />");
				case 3 -> s.append("<textarea rows=\"").append(1 + r.nextInt(4)).append("\">x</textarea>");
				case 4 -> s.append("<select><option>x</option><option>y</option></select>");
				default -> s.append("<button type=\"button\">")
						.append(token(tokens, counter, reorderable, inReorderable)).append("</button>");
				}
			}
			s.append("</form>\n");
		}
		case 13 -> { // WILD only: absolute positioning.
			s.append("<div style=\"position:absolute;top:").append(r.nextInt(300) - 50).append("pt;left:")
					.append(r.nextInt(300) - 50).append("pt\">").append("X").append("</div>\n");
		}
		default -> { // WILD only: forced page breaks, hidden content, overflow, and complex subtrees.
			if (extensionRandom == null) {
				// v1 compatibility: content is a fixed X.
				s.append("<div style=\"page-break-before:always;visibility:")
						.append(r.nextBoolean() ? "hidden" : "visible").append(";overflow:hidden\">X</div>\n");
			} else {
				s.append("<div data-fuzz-role=\"wild-complex\" style=\"page-break-before:always;visibility:")
						.append(r.nextBoolean() ? "hidden" : "visible").append(";overflow:hidden\">\n");
				final int n = 2 + extensionRandom.nextInt(2);
				for (int i = 0; i < n; ++i) {
					appendNode(s, extensionRandom, extensionRandom, Math.max(1, depth - 1), strict, tokens, counter,
							reorderable, inReorderable);
				}
				s.append("</div>\n");
			}
		}
		}
	}

	/** Generate one of multi-column layout, float, flex/grid, list, image, or nested table inside a table cell. */
	private static void appendRichCellChild(final StringBuilder s, final Random r, final int depth,
			final boolean strict, final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable) {
		s.append("<div data-fuzz-role=\"cell-child\">\n");
		switch (r.nextInt(7)) {
		case 0 -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 4);
		case 1 -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 2);
		case 2 -> appendLayoutContainer(s, r, depth, strict, tokens, counter, reorderable, inReorderable, false);
		case 3 -> appendLayoutContainer(s, r, depth, strict, tokens, counter, reorderable, inReorderable, true);
		case 4 -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 7);
		case 5 -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 9);
		default -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 3);
		}
		s.append("</div>\n");
	}

	private static void appendChildren(final StringBuilder s, final Random r, final Random extensionRandom,
			final int depth, final boolean strict, final List<String> tokens, final int[] counter,
			final Set<String> reorderable, final boolean inReorderable) {
		final int n = 1 + r.nextInt(3);
		for (int i = 0; i < n; ++i) {
			appendNode(s, r, extensionRandom, depth - 1, strict, tokens, counter, reorderable, inReorderable);
		}
	}
}
