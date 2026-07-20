package jp.cssj.test.unit._3060_RUBY;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class LogicalVertRubyTest extends AbstractTestCase {
	public LogicalVertRubyTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3060-RUBY/logical-vert-ruby.xhtml");
		CTISessionHelper.transcodeFile(this.session, file, "application/xhtml+xml", null);
	}

	// 2026-07-20: -cssj-direction-mode廃止に伴いフィクスチャからも削除した。
	// bodyのborder(縦書き時、旧回転機構下ではSide.resolve()経由の一部の
	// 参照でLEFT/RIGHT(none)へ誤って回転され、実質無効化されていた)が
	// 常に物理どおり(TOP/BOTTOM、1pt)に効くようになったことで、内容領域が
	// 正しく縮み、期待値がわずかに(x方向+2〜3pt、y方向+約2pt)変化した。

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(190, x, 1);
			assertEquals(45, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(196, x, 1);
			assertEquals(45, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(132, x, 1);
			assertEquals(128, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(137, x, 1);
			assertEquals(128, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(118, x, 1);
			assertEquals(45, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(60, x, 1);
			assertEquals(128, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
