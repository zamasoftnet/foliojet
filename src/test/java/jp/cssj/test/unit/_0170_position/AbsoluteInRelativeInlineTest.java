package jp.cssj.test.unit._0170_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteInRelativeInlineTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0170-position/absolute-in-relative-inline.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AbsoluteInRelativeInlineTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(30, x, 0);
			// 4.393 は**固定したNoto**での値(2026-08-03にテスト用フォントを
			// 公開Notoの自動取得へ切り替えた)。相対配置のインラインの中の
			// 絶対配置なので、基準の位置が行の基線=フォントの上端量に従う。
			// 旧値5.0は環境にインストールされたフォントでの値
			assertEquals(4.393, y, 0.001);
			assertEquals(38, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(31, x, 0);
			// 4.393 は**固定したNoto**での値(2026-08-03にテスト用フォントを
			// 公開Notoの自動取得へ切り替えた)。相対配置のインラインの中の
			// 絶対配置なので、基準の位置が行の基線=フォントの上端量に従う。
			// 旧値5.0は環境にインストールされたフォントでの値
			assertEquals(4.393, y, 0.001);
			assertEquals(38, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
