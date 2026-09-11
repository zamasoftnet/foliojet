package jp.cssj.test.unit._0030_text_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class LastTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0030-text-align/last.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public LastTest(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// 内蔵CIDフォントの幅表を直し、　、。が全角に戻った(2026-09-11)
			assertEquals(71, x, 1);
			// 内蔵CIDフォントの幅表を直し、　、。が全角に戻った(2026-09-11)
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(71, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(71, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// 2026-08-22: justifyの伸長点から禁則境界(atomic)を除外
			// (JLREQ 3.1.11)。「ます|。」に伸長が入らなくなり右へ移動、
			// spanの幅も伸長分を含まない素の値(行末trim済み5pt)に戻った
			// 内蔵CIDフォントの幅表を直し、　、。が全角に戻った(2026-09-11)
			// 行末の。が全角(10pt)なので x = 1+78-10 = 69
			assertEquals(69, x, 1);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// 内蔵CIDフォントの幅表を直し、　、。が全角に戻った(2026-09-11)
			assertEquals(71, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// 内蔵CIDフォントの幅表を直し、　、。が全角に戻った(2026-09-11)
			// 最終行は6字=60pt。(78-60)/2=9 だから x = 1+9+50 = 60
			assertEquals(60, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// 内蔵CIDフォントの幅表を直し、　、。が全角に戻った(2026-09-11)
			// 行末の、は追い込み2ptで行がちょうど埋まる。x = 1+70 = 71
			assertEquals(71, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(61, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
