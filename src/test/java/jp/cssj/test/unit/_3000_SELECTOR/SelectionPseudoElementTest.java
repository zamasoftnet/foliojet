package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code ::selection} has no meaning in a PDF output engine without interactive selection state
 * (investigated on 2026-07-21). Existing pseudo-element parsing already accepts its syntax
 * (the double-colon syntax accepts any name unconditionally; see {@code SelectorConverter}),
 * but no corresponding {@code CSSElement} is ever synthesized, so the selector never matches.
 * Regression test ensuring it is simply ignored, without a syntax error or crash.
 */
public class SelectionPseudoElementTest extends AbstractTestCase {
	public SelectionPseudoElementTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/selection.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TEXT_BLOCK) {
			assertEquals("::selection/p::selectionはどちらも非マッチのままのはずです(色はbodyのblackのまま)",
					ColorValueUtils.BLACK, ((TextBlockBox) box).getBlockParams().color);
			return true;
		}
		return false;
	}
}
