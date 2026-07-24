package jp.cssj.test.unit._3060_RUBY;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * ルビ単位のテキスト抽出と、ルビ内にブロックが現れる異常入力の
 * 安全性を固定します(注釈付きテキスト方式、2026-07-25仕様裁定)。
 *
 * <p>
 * ルビ単位({@code RubyUnitBox})は子ボックスを持たない合成箱なので、
 * 抽出を上書きしないと親からの反復抽出で親文字が丸ごと落ちる
 * (リンクの代替テキスト・string-setのcontent()・ブックマーク見出し・
 * target-text()が共通で使う経路)。
 * </p>
 */
public class RubyTextExtractionTest extends AbstractTestCase {
	public RubyTextExtractionTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3060-RUBY/ruby-text-extraction.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** 親からの抽出でルビの親文字が出る(ふりがなは出ない)。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		final StringBuilder text = new StringBuilder();
		box.getText(text);
		final String s = text.toString();
		assertTrue("ルビの親文字が抽出されていません: " + s, s.contains("漢"));
		assertTrue("ルビの親文字が抽出されていません: " + s, s.contains("字"));
		assertTrue("周囲のテキストが抽出されていません: " + s, s.contains("前"));
		assertTrue("周囲のテキストが抽出されていません: " + s, s.contains("後"));
		assertEquals("ふりがなは本文ではないので抽出しない: " + s, -1, s.indexOf("かん"));
		assertEquals("ふりがなは本文ではないので抽出しない: " + s, -1, s.indexOf("じ"));
		return true;
	}

	/**
	 * ルビの中にブロックが現れても例外にならない(ここに到達している
	 * こと自体が、インラインスタックが壊れていない証拠——壊れると
	 * 変換が例外で落ちる)。
	 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		final StringBuilder text = new StringBuilder();
		box.getText(text);
		final String s = text.toString();
		assertTrue("ルビの手前のテキストが失われています: " + s, s.contains("壊れ"));
		assertTrue("ルビの後のテキストが失われています: " + s, s.contains("末"));
		return true;
	}
}
