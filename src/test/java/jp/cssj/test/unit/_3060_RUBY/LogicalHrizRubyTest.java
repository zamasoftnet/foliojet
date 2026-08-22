package jp.cssj.test.unit._3060_RUBY;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * ルビの幾何(横書き・論理プロパティ)の回帰テストです。
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
public class LogicalHrizRubyTest extends AbstractTestCase {
	public LogicalHrizRubyTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3060-RUBY/logical-hriz-ruby.xhtml");
		CTISessionHelper.transcodeFile(this.session, file, "application/xhtml+xml", null);
	}

	/**
	 * ルビ要素のインラインボックスを検査します。
	 *
	 * <p>
	 * <b>寸法は固定したNotoでの値。</b> 2026-08-03にテスト用フォントを
	 * 公開Notoの自動取得へ切り替えた(それまでは環境にインストールされた
	 * フォントを使っており、機械が変われば基準がずれた)。親文字が漢字の
	 * 箇所は1em丁度のまま(24/36)、<b>ふりがな(かな)が幅を決めている箇所
	 * だけ</b>カーニングのぶんだけ縮む(24→23.892、30→29.682)。
	 * </p>
	 *
	 * @param lineExtent 行方向の寸法 = max(親文字幅, ふりがな幅)
	 */
	private boolean check(IBox box, double x, double y, double expectedX, double expectedY, double lineExtent) {
		if (box.getType() != BoxType.INLINE) {
			return false;
		}
		assertEquals(expectedX, x, 1);
		assertEquals(expectedY, y, 1);
		// 丸め誤差だけ許す(2026-08-03)。値は上の実測どおりで、
		// 29.682 が 29.682000000000002 になるのは積算の丸め
		assertEquals(lineExtent, box.getWidth(), 0.001);
		return true;
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 43.78, 9.71, 24);
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 93.56, 9.71, 36);
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 126, 67.96, 23.892);
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		// x=108→109.25(2026-08-22): justify伸長点の禁則境界除外(JLREQ 3.1.11)
		return this.check(box, x, y, 109.25, 48.54, 29.682);
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 43.78, 87.37, 24);
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 126, 145.62, 23.892);
	}
}
