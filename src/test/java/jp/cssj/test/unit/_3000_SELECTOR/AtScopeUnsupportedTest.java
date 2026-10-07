package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code @scope} (donut scoping at-rule) is unsupported (investigated on 2026-07-21;
 * confirmed that the ph-css 8.2.1 jar contains no related classes; see the support table).
 * This test verifies the contract that an unsupported at-rule throws no exception and the entire rule
 * is safely ignored. It also checks that declarations inside the {@code @scope} block
 * ({@code p{color:red}} in this document) are not mistakenly promoted to apply globally without
 * scope constraints (silent over-application, worse than ignoring the rule).
 */
public class AtScopeUnsupportedTest extends AbstractTestCase {
	public AtScopeUnsupportedTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/at-scope-unsupported.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TEXT_BLOCK) {
			assertEquals("未対応の@scopeブロックは規則全体が無視され、bodyのblackのままのはずです"
					+ "(スコープ制約なしにcolor:redが誤って適用されてはいけません)",
					ColorValueUtils.BLACK, ((TextBlockBox) box).getBlockParams().color);
			return true;
		}
		return false;
	}
}
