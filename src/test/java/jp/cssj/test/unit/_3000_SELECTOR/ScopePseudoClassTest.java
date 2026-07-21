package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code :scope}(2026-07-21新設)。{@code @scope}は未対応のため、
 * 単純化して常に{@code :root}相当(ルート要素にのみマッチ)として扱う
 * (CSS Selectors 4「スタイルシート内でスコープ根が他に指定されなければ
 * ルート要素がデフォルト」に合致)。ルート(html)にだけ適用された
 * {@code color}が子孫まで継承されることを固定する。
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
