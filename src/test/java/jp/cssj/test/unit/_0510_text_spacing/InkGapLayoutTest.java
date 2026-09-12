package jp.cssj.test.unit._0510_text_spacing;

import java.io.File;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.BoxType;

/** 実際のauto表セルとinline-blockの縦方向寸法を検証する。 */
public class InkGapLayoutTest extends AbstractTestCase {
	public InkGapLayoutTest(String name) { super(name); }

	@Override
	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session,
				new File("files/unittest/0510-text-spacing/inkgap-intrinsic.html"), "text/html", null);
	}

	public boolean check_cell(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.TABLE_CELL) return false;
		assertEquals(17.5, ((AbstractContainerBox) box).getInnerHeight(), .01);
		return true;
	}

	public boolean check_inline(IBox box, int pageNumber, double x, double y) {
		if (!(box instanceof AbstractContainerBox container)) return false;
		assertEquals(17.5, container.getInnerHeight(), .01);
		return true;
	}
}
