package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TableCellTest extends AbstractTestCase {
	public TableCellTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0400-column-count/table-cell.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println(box.getWidth());
			System.err.println(box.getHeight());
			// 2026-07-26に更新。旧期待値(94, 152, 50, 43)は
			// **column-gapを段数分だけ重複計上していた**ときの版面である。
			// 段間は段数によらず1回だけ数えるのが正しい
			// (IntrinsicMeasurerの累積乗算を修正)。
			//
			// 新しい配分の裏取り: セルの背景枠は 113.33 と 475.17 で、
			// 合計588.5 ≒ 内容領域590pt(600pt − body枠5pt×2)と整合する。
			// 段幅36.16ptは12ptの和文3字ぶんで、実際に表示リストは
			// 1行3字で折り返している。
			assertEquals(80.4, x, 1);
			assertEquals(210.3, y, 1);
			assertEquals(36.2, box.getWidth(), 1);
			assertEquals(57.6, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println(box.getWidth());
			System.err.println(box.getHeight());
			// 2026-07-26に更新(check_aと同じ理由)
			assertEquals(374.7, x, 1);
			assertEquals(145.3, y, 1);
			assertEquals(217.1, box.getWidth(), 1);
			assertEquals(14.4, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
