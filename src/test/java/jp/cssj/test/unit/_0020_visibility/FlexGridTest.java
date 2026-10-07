package jp.cssj.test.unit._0020_visibility;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Verifies that <b>the contents of flex/grid containers inside visibility:hidden are not drawn</b>
 * (added on 2026-08-18).
 *
 * <p>
 * visibility maps to opacity ({@code BoxStyleMapper.setupParams}), but neutralizing anonymous/neutral
 * flex/grid items ({@code FlexBuilder.itemParams}/{@code GridBuilder.itemParams}) reset opacity to 1f,
 * so only the contents of hidden containers were drawn. In a real document, an e-Stat dropdown menu
 * (`.stat-gnav-title{display:flex}` inside `ul.stat-gnav-list1{visibility:hidden}`) appeared over
 * the body text (1,462 overlapping pairs). The fix makes item neutralization inherit the container's
 * effective opacity.
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
