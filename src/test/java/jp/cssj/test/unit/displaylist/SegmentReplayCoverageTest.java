package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * segment-restyle が実際に発火していることの非空性検証です(M6b)。
 * golden 一致だけでは「常にフォールバックしている」空虚な緑と
 * 区別できないため、発火カウンタで移行経路のカバレッジを固定します。
 * (実際に Phase A が窓の刈り込み順序のバグで一度も発火していなかった
 * 事故の再発防止)
 */
public class SegmentReplayCoverageTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testUnicodeBidiSurvivesParamsReplay() {
		final BlockParams source = new BlockParams();
		source.unicodeBidi = UnicodeBidiValue.ISOLATE_OVERRIDE;
		final BlockParams restored = BlockParamsTemplate.freeze(source).materialize();
		assertEquals(UnicodeBidiValue.ISOLATE_OVERRIDE, restored.unicodeBidi);
	}

	public void testBidiSemanticAliasSurvivesParamsTemplateRoundTrip() {
		final BlockParams source = new BlockParams();
		source.bidiSemanticAlias = true;
		final BlockParams restored = BlockParamsTemplate.freeze(source).materialize();
		assertTrue(restored.bidiSemanticAlias);
	}

	public void testSubtreeReplayFires() throws Exception {
		// 丸ごと次ページへ移動するブロックを含む文書
		final long before = SourceReplayer.SUBTREE_REPLAYS.get();
		final long prefixBefore = SourceReplayer.PREFIX_REPLAYS.get();
		this.transcode(new File("files/unittest/0460-segment-restyle/moved-blocks.html"), "coverage-subtree");
		assertTrue("移動した閉部分木のソース再駆動が一度も発火していません",
				SourceReplayer.SUBTREE_REPLAYS.get() > before);
		assertTrue("吸収済み再生範囲(C1c prefixItems)経由の再駆動が一度も発火していません",
				SourceReplayer.PREFIX_REPLAYS.get() > prefixBefore);
	}

	private void transcode(File source, String name) throws Exception {
		File pdf = new File("local/unittest/display-list/" + name + ".pdf");
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
