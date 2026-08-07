package jp.cssj.test.unit._0410_column_width;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteTest extends AbstractTestCase {
	public AbsoluteTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0410-column-width/absolute.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println(box.getWidth());
			System.err.println(box.getHeight());
			// **2026-08-06: widthは73→289.98、heightは92→(実測値)に変更**。
			// 読み込みに失敗した<img>がCSSのwidth/heightを無視して0x0に
			// 縮退していた欠陥を修正(HTMLStyle.applyBrokenImage、
			// AltTextImage新設)。このHTMLのkappa.pngは元々unittestの
			// 別ディレクトリを指しており(壊れた参照、files/unittest直下へ
			// コピーして解消)、修正後は`img{width:50%}`が正しく効いて
			// #a(width未指定、column-count:2の絶対配置ブロック)の
			// shrink-to-fit幅の計算に実寸が反映されるようになった。
			// 目視確認済み(河童の画像が正しく表示され、段組・浮動は
			// 壊れていない)
			assertEquals(7, x, 1);
			assertEquals(7, y, 1);
			assertEquals(289.98, box.getWidth(), 1);
			assertEquals(96.1, box.getHeight(), 1);
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
			assertEquals(15, x, 1);
			assertEquals(150, y, 1);
			assertEquals(229, box.getWidth(), 1);
			assertEquals(34, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
