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
import net.zamasoft.foliojet.layout.builder.impl.TableBuildStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 表構築の特性テストです(P2-1。§5.2b 表ビルダー統一の保存契約)。
 *
 * <p>
 * golden 一致だけでは検出できない特性 — fixed が OnePass にルーティング
 * されること、そのストリーミングが有界であること、auto が TwoPass で
 * あること、分割・rowspan 切断の経路が実際に通ること — をカウンタで
 * 固定します。P2 の置換はこれらを保存しなければならない。
 * </p>
 */
public class TableBuildCharacterizationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testFixedPagedTableStreamsOnePass() throws Exception {
		final long onePass = TableBuildStats.ONE_PASS_BUILDS.get();
		final long fragments = TableBuildStats.TABLE_FRAGMENTS.get();
		TableBuildStats.ONE_PASS_ROW_HIGH_WATER.set(0);
		this.transcode(new File("files/unittest/0390-writing-mode/fixed-table-pagebreak.html"), "char-fixed");
		assertTrue("fixed 表が OnePass にルーティングされていません",
				TableBuildStats.ONE_PASS_BUILDS.get() > onePass);
		assertTrue("ページ跨ぎで表断片が生成されていません",
				TableBuildStats.TABLE_FRAGMENTS.get() > fragments);
		// fixed のストリーミングは有界: 9行の表で全行保持なら退化。
		// 現行実装の実測値を保存契約として固定する(P2 置換後も維持)
		final long highWater = TableBuildStats.ONE_PASS_ROW_HIGH_WATER.get();
		assertTrue("行保持の high-water が観測されていません", highWater > 0);
		assertTrue("fixed ストリーミングが全体保持に退化しています: high-water=" + highWater, highWater < 9);
	}

	public void testAutoTableUsesTwoPass() throws Exception {
		final long twoPass = TableBuildStats.TWO_PASS_BUILDS.get();
		this.transcode(new File("files/unittest/0070-table-layout/auto-rowspan.html"), "char-auto");
		assertTrue("auto 表が TwoPass にルーティングされていません",
				TableBuildStats.TWO_PASS_BUILDS.get() > twoPass);
	}

	public void testRowspanCutFires() throws Exception {
		final long cuts = TableBuildStats.ROWSPAN_CUTS.get();
		this.transcode(new File("files/unittest/0218-pagebreak-table-span/fixed-rowspan.html"), "char-rowspan");
		assertTrue("rowspan 連結セルの切断経路が通っていません",
				TableBuildStats.ROWSPAN_CUTS.get() > cuts);
	}

	/**
	 * processing.strict-one-pass=trueは、無制限バッファを要する
	 * table-layout:autoのTwoPassTableBuilder使用を避け、警告付きで
	 * OnePassTableBuilder(table-layout:fixed相当)へ近似する
	 * (例外にはしない。未対応セレクタと同じ「警告+縮退」経路。
	 * docs/PLAN.md「2パス制御モード」参照)。
	 */
	public void testStrictOnePassDegradesAutoTableToOnePass() throws Exception {
		final long twoPass = TableBuildStats.TWO_PASS_BUILDS.get();
		final long onePass = TableBuildStats.ONE_PASS_BUILDS.get();
		final long degrades = TableBuildStats.STRICT_ONE_PASS_DEGRADES.get();
		this.transcode(new File("files/unittest/0070-table-layout/auto-rowspan.html"), "char-auto-strict", true);
		assertEquals("strict-one-pass中でもTwoPassTableBuilderが構築されています",
				twoPass, TableBuildStats.TWO_PASS_BUILDS.get());
		assertTrue("auto表がOnePassTableBuilderへ近似されていません",
				TableBuildStats.ONE_PASS_BUILDS.get() > onePass);
		assertTrue("STRICT_ONE_PASS_DEGRADESが計上されていません",
				TableBuildStats.STRICT_ONE_PASS_DEGRADES.get() > degrades);
	}

	/**
	 * processing.strict-one-pass=trueでも、無制限バッファを要しない
	 * (table-layout:fixed)文書は通常どおり処理できる。
	 */
	public void testStrictOnePassAllowsFixedTable() throws Exception {
		this.transcode(new File("files/unittest/0390-writing-mode/fixed-table-pagebreak.html"), "char-fixed-strict",
				true);
	}

	private void transcode(File source, String name) throws Exception {
		this.transcode(source, name, false);
	}

	private void transcode(File source, String name, boolean strictOnePass) throws Exception {
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
				if (strictOnePass) {
					session.property("processing.strict-one-pass", "true");
				}
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
