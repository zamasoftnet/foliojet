package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Characterization tests for continuation (carrying content across page breaks)
 * (P4: quantitative basis for reducing OpenTailShape).
 * Counters fix the contracts that chained breaks go through Child frames
 * and Legacy-path activations do not increase.
 */
public class ContinuationCharacterizationTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testChainedBreakUsesChildFrames() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0460-segment-restyle/float-split-in-chain.html"), "cont-chain");
		assertTrue("チェーン破断が Child フレームを通っていません", ContinuationStats.CHILD_FRAMES.get() > 0);
		// Chained breaks must not trigger unchained restyles.
		assertEquals("チェーン破断で Legacy 全 restyle が発火", 0, ContinuationStats.UNCHAINED_RESTYLES.get());
	}

	public void testOpenParagraphHandoffGoesThroughSlice() throws Exception {
		ContinuationStats.reset();
		// Breaks in the middle of a paragraph: measure and fix the contract that open-paragraph handoff
		// uses slice transport (M3b Phase 1).
		this.transcode(new File("files/unittest/0460-segment-restyle/mid-paragraph.html"), "cont-open-text");
		assertTrue("open 段落の handoff が観測されていません", ContinuationStats.OPEN_TEXT_HANDOFFS.get() > 0);
	}

	public void testLegacyDepthIsLoadBearing() throws Exception {
		ContinuationStats.reset();
		// OpenTailShape's depth contract is active (measured max=6: nested moved-open).
		// This records that Phase 3 removal is substantial work requiring open-box chains
		// to become value recipes. Once removed, delete this test too.
		this.transcode(new File("files/unittest/0460-segment-restyle/mid-paragraph.html"), "p1");
		this.transcode(new File("files/unittest/0460-segment-restyle/float-split-in-chain.html"), "p2");
		this.transcode(new File("files/unittest/0460-segment-restyle/nested-break-in-replay.html"), "p3");
		this.transcode(new File("files/unittest/0460-segment-restyle/moved-blocks.html"), "p4");
		this.transcode(new File("files/unittest/0400-column-count/nest.html"), "p5");
		// Removed old MAX_OPEN_TAIL_DEPTH (deprecated counter conflating PAGE/COLUMN).
		// The maximum of the successor PAGE/COLUMN-specific counters has the same meaning as the old counter.
		final long maxOpenTailDepth = Math.max(ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get(),
				ContinuationStats.MAX_COLUMN_OPEN_TAIL_DEPTH.get());
		assertTrue("depth 規約が観測されていません", maxOpenTailDepth > 0);
		assertTrue("depth の実測上限が拡大: " + maxOpenTailDepth, maxOpenTailDepth <= 6);
	}

	public void testTableSpanningBreakChainsToo() throws Exception {
		ContinuationStats.reset();
		// Page breaks through tables also chain via Child frames; only the tail becomes
		// OpenTailShape (the existing contract for open text and moved-open).
		// All unchained restyles (UNCHAINED_RESTYLES) are zero. Fix this current behavior
		// as a preservation contract (P4 targets reducing OPEN_TAILS).
		this.transcode(new File("files/unittest/0218-pagebreak-table-span/fixed-rowspan.html"), "cont-table");
		assertTrue("表跨ぎ破断が Child フレームを通っていません", ContinuationStats.CHILD_FRAMES.get() > 0);
		assertEquals("収集不能の Legacy 全 restyle が発火", 0, ContinuationStats.UNCHAINED_RESTYLES.get());
	}

	private void transcode(File source, String name) throws Exception {
		File pdf = new File("local/unittest/continuation/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
