package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.LayoutSourceTestHooks;
import net.zamasoft.foliojet.layout.segment.TextSpill;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Output behavior parity test for production text payload spilling
 * (E-6 increment 3b-2, added 2026-07-24).
 *
 * <p>
 * For representative documents covering page-break replay, tail replay, tables, and multi-column layout,
 * use a tiny budget ({@code processing.text-spill-budget=0}: all text spills) and the default budget
 * (no spilling), and verify <b>exact display-list equality</b>.
 * Spilling changes only memory behavior, never output (direct comparison in the same process,
 * like FallbackParityTest; no golden required).
 * Also verify (b) spilling actually fires (counter &gt; 0) and (c) no spill temporary files remain
 * after transcode ends (the formatter's finally also cleans up exception paths;
 * here, pin down reliable close on the success path).
 * </p>
 */
public class TextSpillParityTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * Target documents. Cover replay paths (page breaks, tails, nested breaks from ResumeTraceGoldenTest),
	 * tables, and multi-column layout (balance replay), verifying that decoding and replay of spilled
	 * payloads actually occur with a tiny budget.
	 */
	private static final String[] DOCUMENTS = { //
			"0460-segment-restyle/mid-paragraph.html", //
			"0460-segment-restyle/moved-blocks.html", //
			"0460-segment-restyle/text-tail-avoid.html", //
			"0460-segment-restyle/nested-break-in-replay.html", //
			"0240-table/z-order.html", //
			"0400-column-count/simple.html", //
	};

	public void testTinyBudgetMatchesUnlimited() throws Exception {
		final List<String> failures = new ArrayList<>();
		for (final String doc : DOCUMENTS) {
			this.checkParity(doc, failures);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void checkParity(final String doc, final List<String> failures) throws Exception {
		final String name = doc.replace('/', '_').replace(".html", "");
		final File baselineDir = new File("local/unittest/text-spill-parity/" + name + "-baseline");
		final File tinyDir = new File("local/unittest/text-spill-parity/" + name + "-tiny");

		// Default-budget baseline (no spilling).
		ContinuationStats.reset();
		this.dump(doc, name + "-baseline", baselineDir, null);
		assertEquals(doc + ": 既定予算(8MB)ではspillは起きないはず", 0, ContinuationStats.SPILLED_TEXT_RECORDS.get());

		// Tiny budget (all text spills). Capture spill-store temporary files via the append observation hook
		// and verify that they are deleted after transcode.
		// (Do not scan all of tmpdir, which would conflict with spill files from concurrent tests.)
		ContinuationStats.reset();
		final List<TextSpill> spills = Collections.synchronizedList(new ArrayList<>());
		LayoutSourceTestHooks.setAppendObserver(event -> {
			if (event instanceof LayoutSource.Chars chars
					&& chars.payload() instanceof LayoutSource.TextPayload.Spilled spilled
					&& !spills.contains(spilled.spill())) {
				spills.add(spilled.spill());
			}
		});
		try {
			this.dump(doc, name + "-tiny", tinyDir, "0");
		} finally {
			LayoutSourceTestHooks.setAppendObserver(null);
		}

		// (b) Spilling actually fires.
		assertTrue(doc + ": 極小予算でspillが発火していません", ContinuationStats.SPILLED_TEXT_RECORDS.get() > 0);
		assertTrue(doc + ": spill bytesが計上されていません", ContinuationStats.SPILLED_TEXT_BYTES.get() > 0);
		// With a tiny budget (0), no inline retention occurs.
		assertEquals(doc + ": 予算0でinline保持が発生しています", 0, ContinuationStats.LIVE_TEXT_PAYLOAD_BYTES.get());

		// (c) No spill temporary files remain after transcode (reliable close).
		assertFalse(doc + ": spillストアが観測されていません", spills.isEmpty());
		for (final TextSpill spill : spills) {
			assertTrue(doc + ": transcode後にspill一時ファイルが残っています", spill.tempFilesDeletedForTest());
		}

		// (a)(d) Exact display-list equality (unchanged output behavior, including replay paths).
		final File[] baselinePages = baselineDir.listFiles((d, n) -> n.endsWith(".txt"));
		final File[] tinyPages = tinyDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(doc + ": 表示リストが出力されていません", baselinePages);
		assertTrue(doc + ": 表示リストが出力されていません", baselinePages.length > 0);
		if (tinyPages == null || baselinePages.length != tinyPages.length) {
			failures.add(doc + ": ページ数が一致しません (baseline=" + baselinePages.length + ", tiny="
					+ (tinyPages == null ? 0 : tinyPages.length) + ")");
			return;
		}
		for (final File baseline : baselinePages) {
			final File tiny = new File(tinyDir, baseline.getName());
			final String expected = Files.readString(baseline.toPath(), StandardCharsets.UTF_8);
			final String got = Files.readString(tiny.toPath(), StandardCharsets.UTF_8);
			if (!expected.equals(got)) {
				failures.add(doc + "/" + baseline.getName() + ": 極小予算の出力が既定予算と一致しません (baseline="
						+ baseline + ", tiny=" + tiny + ")");
			}
		}
	}

	private void dump(final String doc, final String name, final File outDir, final String budget) throws Exception {
		deleteChildren(outDir);
		outDir.mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			final File pdf = new File("local/unittest/text-spill-parity/" + name + ".pdf");
			pdf.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					if (budget != null) {
						session.property("processing.text-spill-budget", budget);
					}
					CTISessionHelper.transcodeFile(session, new File("files/unittest/" + doc), "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}
	}

	private static void deleteChildren(final File dir) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			child.delete();
		}
	}
}
