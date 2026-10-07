package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify that <b>the same content is not drawn twice on the same page</b> (introduced 2026-07-28).
 *
 * <p>
 * Measurements of the remaining "content duplication" category (160 cases) in a 50,000-seed sweep
 * revealed <b>four independent paths</b> to the same outcome: identical text drawn twice on the same
 * sheet. All surface in <b>nested multi-column layout</b>. Column reconstruction
 * ({@code ColumnsContainer.restyle}) <b>rebuilds all columns into one</b>, so a previous fragment
 * normally left untouched on the previous page and its continuation are <b>both resumed
 * within the same reconstruction</b>.
 * </p>
 *
 * <ol>
 * <li><b>Source replay of a split previous fragment</b> ({@code seed 347}):
 * {@code SourceAnchor} <b>remains on the previous fragment</b> after splitting
 * (continuations are recipe-built and never have one). {@code stampRanges} marks the previous
 * fragment as a closed subtree eligible for whole replay; {@code replayFromSource} rebuilds
 * <b>the entire element</b>, duplicating the continuation's resumption.
 * Block this with {@code AbstractBox.markFragmented()} and {@code isSourceReplayable()}.</li>
 *
 * <li><b>Tail replay of split text</b> ({@code seed 118665}):
 * tail replay streamed from {@code breakToken}'s character position <b>to the source end</b>.
 * This is correct for the final fragment of a flow, but column reconstruction also resumes
 * the first column's fragment in the same run, laying out later columns' content too.
 * Tail replay was disabled by default on 2026-07-28 and removed on 2026-10-07.</li>
 *
 * <li><b>Wrong MOVE sentinel</b> ({@code seed 739}):
 * {@code ColumnsContainer.splitPageAxis} <b>delegates cutting to the last column</b> and returns
 * its result unchanged, so the "moved everything" sentinel is <b>the last column</b>, not the
 * multi-column container itself. {@code splitForContinuation} compared only with
 * {@code this.container} (=multi-column container), misreading the last column as a remainder
 * container and laying it out as a continuation <b>while it still remained in the columns</b>.
 * Unify this via {@code AbstractContainerBox.splitMoveSentinel()}.</li>
 *
 * <li><b>Source replay of subtrees containing floats</b> ({@code seed 29708}):
 * aggregation <b>lifts floats to the column container</b>, so they remain in the original column
 * even when the whole subtree moves. Replaying that subtree from source lays them out twice,
 * alongside the lifted copies. Add a {@code containsFloat} gate to {@code stampRanges}
 * ({@code canReplayChildren} already had one).</li>
 * </ol>
 *
 * <p>
 * <b>Build documents here</b>: external files risk changing the verdict through relative image paths
 * (lessons learned §6.9h). Only path 2 requires an image, because an image too large for one column
 * triggers that defect. Use {@code File.toURI()} here to build an <b>absolute URI</b> so the resolved
 * resource stays unchanged if the document directory moves.
 * </p>
 *
 * <p>
 * <b>Check token survival as well as absence of duplication.</b> Dropping both copies also removes
 * duplication, which would fail to detect a regression (content loss is much worse than duplication).
 * </p>
 */
public class NestedMulticolDuplicationTest extends TestCase {
	/** Timeout. Measured execution is under one second per case. */
	private static final long WATCHDOG_MS = 60_000L;

	/** Extract content from display-list text-drawing lines. */
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** Tokens to check (omit unrelated symbols such as list numbers). */
	private static final Pattern TOKEN = Pattern.compile("^T[0-9]+$");

	public NestedMulticolDuplicationTest(String name) {
		super(name);
	}

	/** Path 1: source replay of a split previous fragment (vertical writing, nested columns, {@code <ol>}). */
	private static final String SPLIT_FRAGMENT_REPLAY = """
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
	 * Path 2: tail replay of split text. Replace {@code @IMG@} with {@link #imageURI()}.
	 */
	private static final String TEXT_TAIL_REPLAY = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:5pt}
			body{margin:0;font:normal 12pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<div style="column-count:4">
			<div style="column-count:4">
			<img src="@IMG@" style="display:block;width:248pt" />
			T0 T1 T2 T3 T4
			</div>
			</div>
			<div style="page-break-inside:avoid">
			<ol style="list-style-position:inside">
			<li></li>
			<li></li>
			</ol>
			<span style="font-size:16pt">T9</span>
			<p>T10 T11 T12 T13 </p>
			</div>
			</body></html>
			""";

	/** Path 3: wrong MOVE sentinel (two columns inside three). */
	private static final String MOVE_SENTINEL = """
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

	/** Path 4: source replay of a subtree containing floats. */
	private static final String FLOAT_SUBTREE_REPLAY = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="60pt"?>
			<?jp.cssj.property name="output.page-height" value="60pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<span style="display:inline-block;width:22pt">T2</span>
			<div style="column-count:2">
			<div style="border:1pt solid black">
			<div style="float:left">
			T3
			T4
			</div>
			</div>
			T5
			</div>
			</body></html>
			""";

	/**
	 * Path 5: <b>unbounded tail replay</b> ({@code seed 186070}, added 2026-07-28).
	 *
	 * <p>
	 * Blocking it only during column reconstruction was insufficient: this document triggered
	 * tail replay in the <b>outer PAGE resumption</b>. A fragment of {@code <p>T17 T18 T19 T20</p>}
	 * streamed from {@code breakToken}'s character position <b>to the source end</b>,
	 * prematurely laying out {@code T18 T19 T20}, which later fragments also lay out.
	 * </p>
	 *
	 * <p>
	 * The design derived the end from the next sibling's {@code SourceAnchor}, but continuations
	 * have no anchor, and the next fragment <b>is not in the same items</b> (it is in another column),
	 * so it is not visible as a sibling. With no available upper bound, disable tail replay
	 * itself by default ({@code RootBuilder.TEXT_TAIL_RESTYLE}).
	 * </p>
	 */
	private static final String UNBOUNDED_TEXT_TAIL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="60pt"?>
			<?jp.cssj.property name="output.page-height" value="60pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 10pt/1.2 serif;writing-mode:vertical-lr}
			p,div{margin:0;padding:0}
			</style></head><body>
			<div style="float:left;width:55pt">
			<div style="clear:left"><span style="font-size:16pt">T2</span></div>
			</div>
			<div style="column-count:3;column-gap:14pt">
			<div style="float:left;width:30pt">
			<div style="column-count:4;column-gap:14pt">
			<p>T15</p>
			<p>T16</p>
			</div>
			</div>
			<p>T17 T18 T19 T20 </p>
			</div>
			</body></html>
			""";

	/**
	 * Absolute URI of the image used by path 2. Gradle fixes the working directory
	 * to {@code rootProject.projectDir}.
	 */
	private static String imageURI() {
		final File png = new File("files/unittest/red.png");
		assertTrue("テスト画像が見つからない: " + png.getAbsolutePath(), png.isFile());
		return png.toURI().toString();
	}

	public void testSplitFragmentIsNotReplayedFromSource() throws Exception {
		assertNoDuplication("split-fragment-replay", SPLIT_FRAGMENT_REPLAY, "T24", "T25");
	}

	public void testSplitTextTailIsNotReplayedToSourceEnd() throws Exception {
		assertNoDuplication("text-tail-replay", TEXT_TAIL_REPLAY.replace("@IMG@", imageURI()), "T0", "T1", "T2", "T3",
				"T4", "T9", "T10", "T11", "T12", "T13");
	}

	public void testMovedLastColumnIsNotAlsoAContinuation() throws Exception {
		assertNoDuplication("move-sentinel", MOVE_SENTINEL, "T2", "T4", "T6");
	}

	public void testSubtreeWithFloatIsNotReplayedFromSource() throws Exception {
		assertNoDuplication("float-subtree-replay", FLOAT_SUBTREE_REPLAY, "T2", "T3", "T4", "T5");
	}

	public void testTextTailDoesNotRunPastItsOwnFragment() throws Exception {
		assertNoDuplication("unbounded-text-tail", UNBOUNDED_TEXT_TAIL, "T2", "T15", "T16", "T17", "T18", "T19", "T20");
	}

	/**
	 * Convert and check (1) no token is drawn twice on the same page,
	 * and (2) every expected token appears on some page.
	 *
	 * @param name     working directory name
	 * @param html     document
	 * @param expected tokens in the document
	 */
	private static void assertNoDuplication(final String name, final String html, final String... expected)
			throws Exception {
		final File dir = new File("local/nested-multicol-dup/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}

		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
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
		}, "nested-multicol-dup-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}

		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		java.util.Arrays.sort(pages);

		final List<String> duplicated = new ArrayList<>();
		final java.util.Set<String> seen = new java.util.HashSet<>();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
			final Map<String, Integer> counts = new LinkedHashMap<>();
			final Matcher m = TEXT.matcher(dump);
			while (m.find()) {
				for (final String word : m.group(1).trim().split("\\s+")) {
					if (TOKEN.matcher(word).matches()) {
						counts.merge(word, 1, Integer::sum);
					}
				}
			}
			for (final Map.Entry<String, Integer> e : counts.entrySet()) {
				seen.add(e.getKey());
				if (e.getValue() > 1) {
					duplicated.add(e.getKey() + "x" + e.getValue() + "@p" + (i + 1));
				}
			}
		}
		assertTrue(name + ": 内容が複製された " + duplicated + " (全" + pages.length + "ページ)", duplicated.isEmpty());

		// Dropping both copies also eliminates duplication. Make that visible as a regression.
		final List<String> lost = new ArrayList<>();
		for (final String token : expected) {
			if (!seen.contains(token)) {
				lost.add(token);
			}
		}
		assertTrue(name + ": 内容が失われた " + lost, lost.isEmpty());
	}
}
