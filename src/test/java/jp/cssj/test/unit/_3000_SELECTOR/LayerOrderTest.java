package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Verifies layer order for {@code @layer} (CSS Cascade Layers, added on 2026-07-21).
 * The statement form {@code @layer base, theme;} establishes layer order (base &lt; theme) first.
 * Even when the actual blocks place {@code theme} first and {@code base} later (reverse textual order),
 * precedence must follow the order established by the statement (theme is later and takes precedence).
 * This directly proves that precedence follows the layers' own appearance order (including statements),
 * not just the source order of the blocks.
 */
public class LayerOrderTest extends AbstractTestCase {
	public LayerOrderTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/layer-order.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TEXT_BLOCK) {
			assertEquals("themeレイヤーがbaseレイヤーより優先されるはずです"
					+ "(@layer base, theme;で確定した順序どおり、ブロックのテキスト出現順ではなく)",
					ColorValueUtils.BLUE, ((TextBlockBox) box).getBlockParams().color);
			return true;
		}
		return false;
	}
}
