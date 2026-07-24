package jp.cssj.test.unit._0415_column_fill;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;

/**
 * バランスプローブ(M6c-4実採用、常時有効)の横書きスモーク+
 * 品質テストです。
 *
 * <p>
 * M6c-3までは「観測のみ・出力不変」を検証していたが、M6c-4でwinnerが
 * 実採用されるため、検証内容を「品質が既存balance({@code HBalanceTest}:
 * height 188±1)と同等以上」へ切り替えた——プローブは指定段数へ収まる
 * <b>最小の実測容量</b>(maxUsed)へスナップするため、段組のページ方向
 * 寸法(=maxUsed)は既存balanceの結果より大きくなってはならない
 * (codex設計§1.8の品質条件「悪化0件」の座標アサート)。あわせて
 * commitが正確に1回発生したこと(カウンタ)を確認する。細部の
 * レイアウトは{@code BalanceProbeGoldenTest}のdisplay list goldenが固定する。
 * </p>
 */
public class BalanceProbeSmokeTest extends AbstractTestCase {
	public BalanceProbeSmokeTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		ContinuationStats.reset();
		File file = new File("files/unittest/0415-column-fill/h-balance.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);

		assertEquals("balanceは1回だけのはずです", 1, ContinuationStats.BALANCE_PROBE_SESSIONS.get());
		assertEquals("この文書はプローブ適格のはずです", 1, ContinuationStats.BALANCE_PROBE_ELIGIBLE.get());
		assertEquals("フォールバックは起きないはずです", 0, ContinuationStats.BALANCE_PROBE_FALLBACKS.get());
		assertEquals("winnerがownerへ正確に1回commitされるはずです", 1, ContinuationStats.BALANCE_PROBE_COMMITS.get());
		final long builds = ContinuationStats.BALANCE_PROBE_BUILDS.get();
		assertTrue("候補が1個以上・上限以下構築されるはずです: " + builds, builds >= 1 && builds <= 20);
	}

	/**
	 * 品質: 実採用された段組のページ方向寸法(=実測maxUsed)は、既存
	 * balance(188±1)より大きくなってはならない。行方向(280)は不変。
	 * 下限は「2段が実際に使われた」ことの健全性検査(全内容約376ptの
	 * 半分を大きく下回ることはない)。
	 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("probe width: " + box.getWidth());
			System.out.println("probe height: " + box.getHeight());
			assertEquals(280, box.getWidth(), 0);
			assertTrue("maxUsedが既存balance(188±1)より悪化してはいけません: " + box.getHeight(), box.getHeight() <= 189);
			assertTrue("2段バランスの下限を下回っています: " + box.getHeight(), box.getHeight() >= 90);
			return true;
		}
		return false;
	}
}
