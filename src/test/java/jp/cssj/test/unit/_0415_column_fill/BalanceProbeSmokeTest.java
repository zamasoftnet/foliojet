package jp.cssj.test.unit._0415_column_fill;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;

/**
 * {@code processing.balance-probe=true}(M6c-3、2026-07-24)のスモーク
 * テストです。
 *
 * <p>
 * プローブはオプトイン時も<b>観測のみ</b>でownerへcommitしないため、
 * 出力は既定(プローブ無効)と同一でなければならない——{@code
 * HBalanceTest}と同じfixture・同じ期待座標をプローブ有効で検証する
 * ことで「変換完走+出力不変」を固定する。あわせてプローブが実際に
 * 起動・適格判定・探索されたこと(カウンタ)を確認する。
 * </p>
 */
public class BalanceProbeSmokeTest extends AbstractTestCase {
	public BalanceProbeSmokeTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		ContinuationStats.reset();
		this.session.property("processing.balance-probe", "true");
		File file = new File("files/unittest/0415-column-fill/h-balance.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);

		// プローブが実際に走ったこと(全テキスト・フロートなしの段組は適格)
		assertEquals("balanceは1回だけのはずです", 1, ContinuationStats.BALANCE_PROBE_SESSIONS.get());
		assertEquals("この文書はプローブ適格のはずです", 1, ContinuationStats.BALANCE_PROBE_ELIGIBLE.get());
		assertEquals("フォールバックは起きないはずです", 0, ContinuationStats.BALANCE_PROBE_FALLBACKS.get());
		final long builds = ContinuationStats.BALANCE_PROBE_BUILDS.get();
		assertTrue("候補が1個以上・上限以下構築されるはずです: " + builds, builds >= 1 && builds <= 20);
	}

	/** {@code HBalanceTest.check_a}と同一の期待値(出力不変の証拠)。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(280, box.getWidth(), 0);
			assertEquals(188, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	/** {@code HBalanceTest.check_b}と同一の期待値(出力不変の証拠)。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(156, x, 1);
			assertEquals(155, y, 1);
			return true;
		}
		return false;
	}
}
