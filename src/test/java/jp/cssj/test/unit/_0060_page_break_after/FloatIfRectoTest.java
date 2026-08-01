package jp.cssj.test.unit._0060_page_break_after;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 浮動体の{@code page-break-after: if-recto}の回帰テストです
 * (2026-08-01)。
 *
 * <p>
 * 従来、{@code addBound()}のbreak-after switchはIF_VERSO/IF_RECTOの
 * caseを持たず{@code default: throw new IllegalStateException()}に
 * 落ちていた(正規のCSS値でのクラッシュ)。フローブロックの
 * {@code endFlowBlock()}側と同じ裁定(現ページが該当面なら反対面へ
 * 即時改ページ)へ統一した。
 * </p>
 *
 * <p>
 * 文書先頭ページはrecto(奇数ページ)のため、if-rectoの浮動体の後の
 * 内容は2ページ目へ送られる。
 * </p>
 */
public class FloatIfRectoTest extends AbstractTestCase {
	public FloatIfRectoTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0060-page-break-after/float-if-recto.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(2, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}
}
