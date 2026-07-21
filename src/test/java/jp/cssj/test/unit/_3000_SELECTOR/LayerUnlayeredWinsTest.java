package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code @layer}(CSS Cascade Layers、2026-07-21新設)。レイヤーに属さない
 * 通常規則は、固有性・出現順に関わらず常にどのレイヤーの規則にも
 * 優先する(仕様: レイヤーなし規則は暗黙の最終レイヤーとして扱われる)。
 * このテストでは、レイヤーに属さない{@code p{color:green}}(低固有性、
 * 出現順は先)が、レイヤー内の{@code p#a{color:red}}(ID込みの高固有性、
 * 出現順は後)より優先されることを確認する——固有性・出現順だけの
 * 単純フォールバックだったら逆(red)になるはずの構成。
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
