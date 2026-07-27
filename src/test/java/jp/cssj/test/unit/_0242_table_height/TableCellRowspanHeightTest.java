package jp.cssj.test.unit._0242_table_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TableCellRowspanHeightTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0242-table-height/table-cell-rowspan-height.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public TableCellRowspanHeightTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	/**
	 * 2行目のセル(height:200pt・内容1行)の文字。
	 *
	 * <p>
	 * 2026-07-27に4ページ目→3ページ目へ更新した。このセルは
	 * {@code vertical-align}の既定(middle)で内容がセル中央——ちょうど
	 * 3/4ページ境界——に置かれるため、従来は先頭断片に1行も残らず、
	 * <b>3ページ目には枠だけ・文字は4ページ目</b>という分かれ方をしていた
	 * (読み順の逆転と同じ機序。不変条件7で検出)。現在は
	 * {@code TableCellBox.split}が「先頭の不可分単位が前断片に残る範囲」
	 * まで整列余白を詰めるので、文字は枠と同じ3ページ目に出る。
	 * </p>
	 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(3, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(5, pageNumber);
			return true;
		}
		return false;
	}
}
