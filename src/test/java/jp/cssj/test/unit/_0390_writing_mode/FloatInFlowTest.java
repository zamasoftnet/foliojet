package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatInFlowTest extends AbstractTestCase {
	public FloatInFlowTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/float-in-flow.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(175, x, 1);
			assertEquals(6, y, 0);
			assertEquals(90, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			// 直交ブロックのfit-content限度はページ内容域(2026-08-10)——
			// 以下の旧値は一様に16.35pt(物理ページと内容域の差)だけ
			// マージンへ食い込んでいた
			assertEquals(-6, x, 1);
			assertEquals(225, y, 1);
			assertEquals(271.65, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(0, x, 1);
			assertEquals(6, y, 0);
			assertEquals(271.65, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(10, x, 1);
			assertEquals(16, y, 0);
			assertEquals(50, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(10, x, 1);
			assertEquals(46, y, 1);
			assertEquals(251.65, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(10, x, 1);
			assertEquals(86, y, 1);
			assertEquals(30, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
