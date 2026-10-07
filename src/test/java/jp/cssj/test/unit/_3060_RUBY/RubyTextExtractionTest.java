package jp.cssj.test.unit._3060_RUBY;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Verifies the contracts for text extraction from ruby units and safe handling of malformed input
 * with blocks inside ruby (annotated-text approach, specification decision on 2026-07-25).
 *
 * <p>
 * A ruby unit ({@code RubyUnitBox}) is a synthetic box without child boxes.
 * Without overriding extraction, recursive extraction from a parent loses all base text
 * (the shared path for link alternative text, string-set content(), bookmark headings,
 * and target-text()).
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

	/** Extraction from the parent returns the ruby base text (not the reading). */
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
	 * A block inside ruby causes no exception. Reaching this point itself proves that the inline stack
	 * is intact; a broken stack would fail conversion with an exception.
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
