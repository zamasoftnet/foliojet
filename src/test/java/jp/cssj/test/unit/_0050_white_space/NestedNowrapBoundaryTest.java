package jp.cssj.test.unit._0050_white_space;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * CSS Text: {@code white-space:nowrap} を指定した兄弟インラインの内側だけを
 * 不可分にし、兄弟間は共通祖先の {@code white-space:normal} で折り返す。
 */
public class NestedNowrapBoundaryTest extends AbstractTestCase {
	public NestedNowrapBoundaryTest(String name) {
		super(name);
	}

	private double previousX = Double.NaN;
	private double lockedX = Double.NaN;

	@Override
	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session,
				new File("files/unittest/0050-white-space/nested-nowrap-boundary.html"), "text/html", null);
	}

	private boolean item(IBox box, double x) {
		if (box.getType() != BoxType.INLINE) return false;
		// 行境界には幅0の継続fragmentも現れるため、実内容を持つfragmentだけを測る。
		if (box.getHeight() < .1) return false;
		assertEquals("各nowrap項目は本文6文字と生成ページ番号を含む一単位のまま分断しない",
				70, box.getHeight(), .1);
		if (!Double.isNaN(this.previousX)) {
			assertTrue("兄弟nowrap間では親normalに従って次の行へ送る: previous=" + this.previousX
					+ ", current=" + x, x < this.previousX - 1);
		}
		this.previousX = x;
		return true;
	}

	public boolean check_a(IBox box, int page, double x, double y) { return item(box, x); }
	public boolean check_b(IBox box, int page, double x, double y) { return item(box, x); }
	public boolean check_c(IBox box, int page, double x, double y) { return item(box, x); }
	public boolean check_d(IBox box, int page, double x, double y) { return item(box, x); }

	private boolean lockedItem(IBox box, double x) {
		if (box.getType() != BoxType.INLINE || box.getHeight() < .1) return false;
		assertEquals("外側もnowrapなら兄弟項目間でも折り返さない",
				Double.isNaN(this.lockedX) ? x : this.lockedX, x, .1);
		this.lockedX = x;
		return true;
	}

	public boolean check_e(IBox box, int page, double x, double y) { return lockedItem(box, x); }
	public boolean check_f(IBox box, int page, double x, double y) { return lockedItem(box, x); }
	public boolean check_g(IBox box, int page, double x, double y) { return lockedItem(box, x); }
	public boolean check_h(IBox box, int page, double x, double y) { return lockedItem(box, x); }
}
