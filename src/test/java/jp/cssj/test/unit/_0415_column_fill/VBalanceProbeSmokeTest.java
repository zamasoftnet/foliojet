package jp.cssj.test.unit._0415_column_fill;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;

/**
 * バランスプローブ(M6c-4実採用、常時有効)の縦書きスモーク+
 * 品質テストです。{@code BalanceProbeSmokeTest}(横書き)と対で、
 * codex設計§1.8の品質条件「悪化0件」の座標アサート2件目——縦書きでは
 * ページ方向=幅(既存balance {@code VBalanceTest}: width 188±1)に対して
 * 実測maxUsedが悪化しないことを固定する。
 */
public class VBalanceProbeSmokeTest extends AbstractTestCase {
	public VBalanceProbeSmokeTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		ContinuationStats.reset();
		File file = new File("files/unittest/0415-column-fill/v-balance.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);

		assertEquals("balanceは1回だけのはずです", 1, ContinuationStats.BALANCE_PROBE_SESSIONS.get());
		assertEquals("この文書はプローブ適格のはずです", 1, ContinuationStats.BALANCE_PROBE_ELIGIBLE.get());
		assertEquals("フォールバックは起きないはずです", 0, ContinuationStats.BALANCE_PROBE_FALLBACKS.get());
		assertEquals("winnerがownerへ正確に1回commitされるはずです", 1, ContinuationStats.BALANCE_PROBE_COMMITS.get());
	}

	/**
	 * 品質: 縦書きのページ方向=幅。実採用の幅(実測maxUsed)は既存
	 * balance(188±1)より悪化してはならない。行方向(280)は不変。
	 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("probe width: " + box.getWidth());
			System.out.println("probe height: " + box.getHeight());
			assertEquals(280, box.getHeight(), 0);
			assertTrue("maxUsedが既存balance(188±1)より悪化してはいけません: " + box.getWidth(), box.getWidth() <= 189);
			assertTrue("2段バランスの下限を下回っています: " + box.getWidth(), box.getWidth() >= 90);
			return true;
		}
		return false;
	}
}
