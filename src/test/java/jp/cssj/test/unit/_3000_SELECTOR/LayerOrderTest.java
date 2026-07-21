package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code @layer}(CSS Cascade Layers、2026-07-21新設)のレイヤー順序を
 * 固定する。{@code @layer base, theme;}という文形式でレイヤー順序
 * (base &lt; theme)を先に確定させたうえで、実際のブロックは
 * {@code theme}を先・{@code base}を後(テキスト上の出現順は逆)に
 * 書いても、優先順位は文形式で確定した順序(themeが後=優先)のまま
 * であることを確認する——単なるソース出現順ではなく、レイヤー自体の
 * 出現順(文形式含む)が優先順位を決めることの直接的な証拠。
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
