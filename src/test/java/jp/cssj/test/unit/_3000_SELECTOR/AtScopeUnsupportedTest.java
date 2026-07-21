package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code @scope}(donut scoping at-rule)は未対応(2026-07-21調査、
 * ph-css 8.2.1のjarに関連クラスが一切存在しないと確認済み——
 * {@code docs/CSS-SUPPORT.md}参照)。このテストは「未対応のat-ruleに
 * 遭遇しても例外を投げず、規則全体が安全に無視されること」を固定する
 * ——{@code @scope}ブロック内の宣言(この文書では{@code p{color:red}})が
 * スコープ制約なしに全体へ誤って昇格して適用されてしまう
 * (無視よりも悪い、サイレントな過剰適用)のではないことも合わせて
 * 確認する。
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
