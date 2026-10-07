package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

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
 * Blank-page characterization test (added on 2026-07-25 as a safety net for the user's explicit
 * absolute requirement: "no unintended blank pages").
 *
 * <p>
 * <b>Distinction</b>: author-intended blank pages (hidden objects, forced page breaks,
 * empty {@code @page}, etc.) are valid. <b>Blank pages caused by the engine are defects</b>.
 * This cannot be distinguished automatically, so <b>documents with blank pages and their counts
 * are fixed as characterization values</b>; any increase or decrease fails the test.
 * An increase suggests extra blank pages from the engine; a decrease suggests lost intentional
 * blank pages. Both require human review.
 * </p>
 *
 * <p>
 * A page is blank if its display list is empty (zero drawing commands).
 * Pages containing only a page background or border count as drawn: an empty page with a
 * CSS background can be considered intentional.
 * </p>
 *
 * <p>
 * Update expected values <b>only after visually inspecting the differences</b>.
 * Casual rebaselining defeats this safety net.
 * </p>
 */
public class BlankPageCharacterizationTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Canonical characterization values (blank-page numbers per document). */
	private static final Path EXPECTED = Path.of("files/unittest/blank-page-characterization.txt");

	/** Characterization value for a document whose conversion ends in an exception. */
	private static final String ERROR = "!conversion-failed";

	/** Characterization value for a document that produces no pages. */
	private static final String NO_PAGES = "!no-pages";

	/**
	 * Shard count (2026-10-05). One class took a minute and a half, becoming the tail of the entire suite.
	 * Documents are now divided by name hash: this class handles shard 0 and
	 * {@code BlankPageCharacterizationShardNTest} handles the others.
	 * Each shard compares only its own document rows in the expected values.
	 */
	static final int SHARDS = 3;

	public BlankPageCharacterizationTest(String name) {
		super(name);
	}

	public void testBlankPagesAreCharacterized() throws Exception {
		checkShard(0);
	}

	private static String keyOf(final Path doc) {
		return Path.of("files/unittest").relativize(doc).toString().replace('\\', '/');
	}

	/**
	 * Checks shard {@code shard} (called by the sharded test classes).
	 * If expected values are absent, shard 0 generates them for all documents.
	 */
	static void checkShard(final int shard) throws Exception {
		final boolean bootstrap = !Files.exists(EXPECTED);
		if (bootstrap && shard != 0) {
			return;
		}
		final List<Path> docs = new ArrayList<>();
		try (var s = Files.walk(Path.of("files/unittest"))) {
			s.filter(p -> {
				final String n = p.getFileName().toString().toLowerCase();
				return n.endsWith(".html") || n.endsWith(".xhtml");
			}).filter(p -> bootstrap || Math.floorMod(keyOf(p).hashCode(), SHARDS) == shard).sorted().forEach(docs::add);
		}

		final TreeMap<String, String> actual = new TreeMap<>();
		for (final Path doc : docs) {
			final String blanks = blankPagesOf(doc);
			if (!blanks.isEmpty()) {
				actual.put(keyOf(doc), blanks);
			}
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("# 白紙ページ(表示リストが空)の特性値。\n");
		sb.append("# 増えた=エンジンが余分な白紙を作った疑い、減った=意図した白紙が消えた疑い。\n");
		sb.append("# " + ERROR + "=変換が例外で終わる文書、" + NO_PAGES + "=1ページも出ない文書。\n");
		sb.append("# 更新は差分を目視確認してから行うこと。\n");
		for (final var e : actual.entrySet()) {
			sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
		}
		final String actualText = sb.toString();

		if (bootstrap) {
			Files.writeString(EXPECTED, actualText, StandardCharsets.UTF_8);
			fail("特性値を生成しました。内容を確認してコミットしてください: " + EXPECTED);
		}
		// Compare only expected-value rows for documents assigned to this shard.
		final TreeMap<String, String> expected = new TreeMap<>();
		for (final String line : Files.readAllLines(EXPECTED, StandardCharsets.UTF_8)) {
			final int tab = line.indexOf('\t');
			if (line.startsWith("#") || tab < 0) {
				continue;
			}
			final String doc = line.substring(0, tab);
			if (Math.floorMod(doc.hashCode(), SHARDS) == shard) {
				expected.put(doc, line.substring(tab + 1));
			}
		}
		if (!expected.equals(actual)) {
			final StringBuilder diff = new StringBuilder();
			final java.util.TreeSet<String> keys = new java.util.TreeSet<>(expected.keySet());
			keys.addAll(actual.keySet());
			for (final String doc : keys) {
				if (!java.util.Objects.equals(expected.get(doc), actual.get(doc))) {
					diff.append(doc).append(": expected=").append(expected.get(doc)).append(" actual=")
							.append(actual.get(doc)).append('\n');
				}
			}
			final Path actualPath = Path.of("local/blank-page-characterization-actual-" + shard + ".txt");
			actualPath.getParent().toFile().mkdirs();
			Files.writeString(actualPath, actualText, StandardCharsets.UTF_8);
			fail("白紙ページの構成が変わりました。差分を目視確認してから期待値(" + EXPECTED + ")の該当行を直してください。\n"
					+ diff + "actual=" + actualPath);
		}
	}

	/**
	 * Converts a document and returns comma-separated page numbers with empty display lists
	 * (an empty string if there are no empty pages).
	 *
	 * <p>
	 * Documents that fail conversion or produce no pages return {@link #ERROR}/{@link #NO_PAGES},
	 * not an empty string, so they <b>appear in the characterization values</b>
	 * (2026-07-25, independent review finding). Previously both returned an empty string,
	 * creating false negatives where broken conversions passed as having zero blank pages,
	 * the exact opposite of this safety net's purpose. Recording them makes both newly failing
	 * and newly passing documents appear as differences.
	 * </p>
	 */
	private static String blankPagesOf(final Path doc) {
		// Files with the same name exist at different levels, so use the full relative path as the directory name
		// (using only the filename causes collisions and reads another document's results).
		final String key = Path.of("files/unittest").relativize(doc).toString().replaceAll("[^A-Za-z0-9._-]", "_");
		final File outDir = new File("local/unittest/blank-page/" + key);
		outDir.mkdirs();
		final File[] old = outDir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			final File pdf = new File("local/unittest/blank-page/out.pdf");
			pdf.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, doc.toFile(), "text/html", null);
				} finally {
					session.close();
				}
			}
		} catch (final Exception | AssertionError e) {
			return ERROR;
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		if (pages == null || pages.length == 0) {
			return NO_PAGES;
		}
		java.util.Arrays.sort(pages);
		final StringBuilder blanks = new StringBuilder();
		for (final File page : pages) {
			final String text;
			try {
				text = Files.readString(page.toPath(), StandardCharsets.UTF_8);
			} catch (final Exception e) {
				continue;
			}
			// A display list containing only "drawer z=..." lines has zero drawing commands.
			boolean hasContent = false;
			for (final String line : text.split("\n")) {
				final String t = line.trim();
				if (t.isEmpty() || t.startsWith("drawer")) {
					continue;
				}
				hasContent = true;
				break;
			}
			if (!hasContent) {
				if (blanks.length() > 0) {
					blanks.append(',');
				}
				blanks.append(page.getName().replaceAll("\\D+", ""));
			}
		}
		return blanks.toString();
	}
}
