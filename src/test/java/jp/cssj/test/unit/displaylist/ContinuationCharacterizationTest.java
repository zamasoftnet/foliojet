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
 * 継続(改ページ運搬)の特性テストです(P4: OpenTailShape 縮小の
 * 定量基盤)。チェーン化された破断が Child フレームを通ること、
 * Legacy 経路の発火数が増えないことをカウンタで固定します。
 */
public class ContinuationCharacterizationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testChainedBreakUsesChildFrames() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0460-segment-restyle/float-split-in-chain.html"), "cont-chain");
		assertTrue("チェーン破断が Child フレームを通っていません", ContinuationStats.CHILD_FRAMES.get() > 0);
		// チェーン化できた破断で収集不能 restyle が発火しないこと
		assertEquals("チェーン破断で Legacy 全 restyle が発火", 0, ContinuationStats.UNCHAINED_RESTYLES.get());
	}

	public void testOpenParagraphHandoffGoesThroughSlice() throws Exception {
		ContinuationStats.reset();
		// 段落中央の破断: open 段落の handoff がスライス運搬
		// (M3b Phase 1)を通ることを実測固定
		this.transcode(new File("files/unittest/0460-segment-restyle/mid-paragraph.html"), "cont-open-text");
		assertTrue("open 段落の handoff が観測されていません", ContinuationStats.OPEN_TEXT_HANDOFFS.get() > 0);
	}

	public void testLegacyDepthIsLoadBearing() throws Exception {
		ContinuationStats.reset();
		// OpenTailShape の深さ規約は実働(実測 max=6: moved-open の
		// 入れ子)。Phase 3 の除去は「開きボックス連鎖の値 recipe 化」を
		// 伴う本格工事であることの記録。除去できたらこのテストごと削除
		this.transcode(new File("files/unittest/0460-segment-restyle/mid-paragraph.html"), "p1");
		this.transcode(new File("files/unittest/0460-segment-restyle/float-split-in-chain.html"), "p2");
		this.transcode(new File("files/unittest/0460-segment-restyle/nested-break-in-replay.html"), "p3");
		this.transcode(new File("files/unittest/0460-segment-restyle/moved-blocks.html"), "p4");
		this.transcode(new File("files/unittest/0400-column-count/nest.html"), "p5");
		assertTrue("depth 規約が観測されていません", ContinuationStats.MAX_OPEN_TAIL_DEPTH.get() > 0);
		assertTrue("depth の実測上限が拡大: " + ContinuationStats.MAX_OPEN_TAIL_DEPTH.get(),
				ContinuationStats.MAX_OPEN_TAIL_DEPTH.get() <= 6);
	}

	public void testTableSpanningBreakChainsToo() throws Exception {
		ContinuationStats.reset();
		// 表を跨ぐ改ページも Child フレームで連鎖し、末端だけが
		// OpenTailShape(開きテキスト・moved-open の従来規約)になる。
		// 収集不能の全 restyle(UNCHAINED_RESTYLES)はゼロ — この現状を保存契約
		// として固定する(P4 は OPEN_TAILS の縮小が対象)
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
