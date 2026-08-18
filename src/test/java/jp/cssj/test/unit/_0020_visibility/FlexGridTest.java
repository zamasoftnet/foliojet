package jp.cssj.test.unit._0020_visibility;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * <b>visibility:hiddenの中のflex/gridコンテナの中身が描かれない</b>ことを
 * 固定します(2026-08-18新設)。
 *
 * <p>
 * visibilityはopacityへ写像される({@code BoxStyleMapper.setupParams})が、
 * flex/gridの匿名・中立itemの中立化({@code FlexBuilder.itemParams}/
 * {@code GridBuilder.itemParams})がopacityを1fへ戻していたため、hiddenな
 * コンテナの中身だけが描かれていた。実物ではe-Statのドロップダウン
 * メニュー(`ul.stat-gnav-list1{visibility:hidden}`の中の
 * `.stat-gnav-title{display:flex}`)が本文に重なって出た(重なり1,462対)。
 * 修正はitemの中立化でコンテナの実効opacityを引き継ぐこと。
 * </p>
 */
public class FlexGridTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0020-visibility/flex-grid.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FlexGridTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(0f, box.getParams().opacity, 0f);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(0f, box.getParams().opacity, 0f);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		assertEquals(1f, box.getParams().opacity, 0f);
		return true;
	}
}
