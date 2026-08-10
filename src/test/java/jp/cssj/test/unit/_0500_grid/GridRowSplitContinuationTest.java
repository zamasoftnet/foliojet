package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * grid行分割(G6、2026-08-10)の継続断片の寸法テストです。
 *
 * <p>
 * 3行×2列(各行92pt)のgridが切断線300ptで割れ、3行目の下側56ptが
 * 継続断片として2ページ目へ運ばれる。継続断片はチェーン継続の対象外
 * (PageAtomicBox)のため汎用restyle再構築(itemの縦積み再登録)を通る
 * ——RowSplitContainerのカーソル巻き戻しが無いと、2item×56ptの断片が
 * 112ptへ膨張し後続内容を押し下げる(実測済みの欠陥。巻き戻しを
 * 自己アンカー限定から常時へ広げて根治)。
 * </p>
 */
public class GridRowSplitContinuationTest extends AbstractTestCase {
	public GridRowSplitContinuationTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/row-split-carry.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** 前断片は切断線まで、継続断片は残余ちょうどの高さになる。 */
	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		if (pageNumber == 1) {
			assertEquals("前断片はページ端(y=300)まで", 220.0, box.getHeight(), 0.1);
			return false;
		}
		assertEquals("継続断片が縦積み再構築で膨らんではならない", 2, pageNumber);
		assertEquals("継続断片は3行目の残余ちょうど", 56.0, box.getHeight(), 0.1);
		return true;
	}

	/** 後続要素は継続断片の直後に続く(押し下げられない)。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		assertEquals("AFTERはgrid継続断片と同じ2ページ目", 2, pageNumber);
		return true;
	}
}
