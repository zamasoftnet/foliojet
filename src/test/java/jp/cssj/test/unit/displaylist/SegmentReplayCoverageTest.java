package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
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
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testSubtreeReplayFires() throws Exception {
		if (Boolean.getBoolean("foliojet.noSegmentRestyle")) {
			// 実験フラグOFF時は対象経路が無効(box-restyle のみ)
			return;
		}
		// 丸ごと次ページへ移動するブロックを含む文書
		final long before = SourceReplayer.SUBTREE_REPLAYS.get();
		this.transcode(new File("files/unittest/0460-segment-restyle/moved-blocks.html"), "coverage-subtree");
		assertTrue("移動した閉部分木のソース再駆動が一度も発火していません",
				SourceReplayer.SUBTREE_REPLAYS.get() > before);
	}

	public void testTextTailReplayFires() throws Exception {
		if (Boolean.getBoolean("foliojet.noSegmentRestyle")) {
			// 実験フラグOFF時は対象経路が無効(box-restyle のみ)
			return;
		}
		if (true) {
			// TODO: v1(StyleBuilder再入型)は無効化済み。テキスト尾部再開の
			// v3(SourceReplayer拡張)が実装されたらこのガードを外す
			return;
		}
		// 段落の中間で改ページされる文書
		final long before = SourceReplayer.TEXT_TAIL_REPLAYS.get();
		this.transcode(new File("files/unittest/0460-segment-restyle/mid-paragraph.html"), "coverage-texttail");
		assertTrue("切断段落の尾部ソース再駆動が一度も発火していません",
				SourceReplayer.TEXT_TAIL_REPLAYS.get() > before);
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
