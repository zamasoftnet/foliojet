package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code ::selection}は対話的な選択状態を持たないPDF出力エンジンでは
 * 意味を持たない(2026-07-21調査)。既存の疑似要素解析(二重コロン構文は
 * 任意の名前を無条件に受理する、{@code SelectorConverter}参照)により
 * 構文としては既に受理されるが、対応する{@code CSSElement}が一切
 * 合成されないため、このセレクタは常に非マッチのまま残る——構文エラーや
 * クラッシュにならず、単に無視されることを固定する回帰テスト。
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
