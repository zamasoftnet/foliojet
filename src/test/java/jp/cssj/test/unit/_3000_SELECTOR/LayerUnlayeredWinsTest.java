package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code @layer} (CSS Cascade Layers, added on 2026-07-21). Unlayered normal rules always take
 * precedence over rules in any layer, regardless of specificity or appearance order
 * (the specification treats unlayered rules as an implicit final layer).
 * This test verifies that unlayered {@code p{color:green}} (low specificity, earlier appearance)
 * beats layered {@code p#a{color:red}} (high specificity including an ID, later appearance).
 * A simple fallback using only specificity and appearance order would produce the opposite (red).
 */
public class LayerUnlayeredWinsTest extends AbstractTestCase {
	public LayerUnlayeredWinsTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/layer-unlayered-wins.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TEXT_BLOCK) {
			assertEquals("レイヤーに属さない規則は固有性・出現順に関わらず常に優先されるはずです",
					ColorValueUtils.GREEN, ((TextBlockBox) box).getBlockParams().color);
			return true;
		}
		return false;
	}
}
