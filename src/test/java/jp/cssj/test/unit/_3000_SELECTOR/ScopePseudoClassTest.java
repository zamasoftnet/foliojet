package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code :scope} (added on 2026-07-21). Since {@code @scope} is unsupported, this is simplified
 * to always behave like {@code :root} (matching only the root element). This follows CSS Selectors 4:
 * when no other scoping root is specified in a stylesheet, the root element is the default.
 * Verifies that {@code color} applied only to the root (html) is inherited by descendants.
 */
public class ScopePseudoClassTest extends AbstractTestCase {
	public ScopePseudoClassTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/scope.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TEXT_BLOCK) {
			assertEquals(":scopeがルート要素にマッチし、colorが子孫へ継承されているはずです",
					ColorValueUtils.RED, ((TextBlockBox) box).getBlockParams().color);
			return true;
		}
		return false;
	}
}
