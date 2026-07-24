package jp.cssj.test.unit._3060_RUBY;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * ルビの幾何(縦書き)の回帰テストです。
 *
 * <p>
 * 注釈付きテキスト方式(2026-07-25仕様裁定)ではrb/rtは箱にならず、
 * ルビ1単位(親文字+ふりがな)が1つの{@code RubyUnitBox}になる。
 * DOM要素として残るのは{@code ruby}自身だけなので、idは{@code ruby}へ
 * 置き、そのインラインボックス(単位と同じ位置・行方向寸法)を測る。
 * ふりがなは行間へはみ出して描かれるため寸法には算入されない。
 * 単位の内容・寸法そのものは表示リストgolden
 * (3060-RUBY/ruby-annotation.html)が直接固定する。
 * </p>
 */
public class VertRubyTest extends AbstractTestCase {
	public VertRubyTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3060-RUBY/vert-ruby.xhtml");
		CTISessionHelper.transcodeFile(this.session, file, "application/xhtml+xml", null);
	}

	/**
	 * ルビ要素のインラインボックスを検査します。
	 *
	 * @param lineExtent 行方向の寸法 = max(親文字幅, ふりがな幅)
	 */
	private boolean check(IBox box, double x, double y, double expectedX, double expectedY, double lineExtent) {
		if (box.getType() != BoxType.INLINE) {
			return false;
		}
		assertEquals(expectedX, x, 1);
		assertEquals(expectedY, y, 1);
		assertEquals(lineExtent, box.getHeight(), 0);
		return true;
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 178.29, 44.89, 24);
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 178.29, 93.78, 36);
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 120.04, 128, 24);
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 139.46, 107, 30);
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 100.63, 44.89, 24);
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 42.38, 128, 24);
	}
}
