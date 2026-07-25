package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ColumnsFloatTest extends AbstractTestCase {
	public ColumnsFloatTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0400-column-count/columns-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		System.out.println("pageNumber: " + pageNumber);
		assertEquals(214, x, 1);
		assertEquals(28, y, 1);
		assertEquals(1, pageNumber);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		System.out.println("pageNumber: " + pageNumber);
		// 2026-07-25: バランスプローブ撤去により1つ目の段組の高さは
		// ColumnBalancerの一発計算(実測最小容量への収束なし)へ戻り、
		// 2つ目の段組(b)は再び2ページ目の先頭に置かれる。
		assertEquals(214, x, 1);
		assertEquals(28, y, 1);
		assertEquals(2, pageNumber);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		System.out.println("pageNumber: " + pageNumber);
		// 2026-07-25: 同上(bと同じ段組内、2ページ目へ)
		assertEquals(28, x, 1);
		assertEquals(28, y, 1);
		assertEquals(2, pageNumber);
		return true;
	}
}
