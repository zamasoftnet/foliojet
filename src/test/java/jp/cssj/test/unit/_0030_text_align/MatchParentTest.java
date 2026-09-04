package jp.cssj.test.unit._0030_text_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.text.TextAlign;
import net.zamasoft.foliojet.css.impl.property.text.TextAlignLast;
import net.zamasoft.foliojet.css.util.TextValueUtils;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

public class MatchParentTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		this.checkComputedValues();
		File file = new File("files/unittest/0030-text-align/match-parent.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public MatchParentTest(String name) {
		super(name);
	}

	private static CSSStyle style(CSSStyle parent) {
		return CSSStyle.getCSSStyle(null, parent, CSSElement.ANON);
	}

	private void checkComputedValues() {
		assertSame(TextAlignValue.MATCH_PARENT_VALUE, TextValueUtils.toTextAlign("match-parent"));
		assertEquals("match-parent", TextAlignValue.MATCH_PARENT_VALUE.toString());

		CSSStyle parent = style(null);
		parent.set(Direction.INFO, DirectionValue.RTL_VALUE);
		parent.set(TextAlign.INFO, TextAlignValue.START_VALUE);
		CSSStyle child = style(parent);
		child.set(Direction.INFO, DirectionValue.LTR_VALUE);
		child.set(TextAlign.INFO, TextAlignValue.MATCH_PARENT_VALUE);
		assertSame(TextAlignValue.RIGHT_VALUE, child.get(TextAlign.INFO));

		parent = style(null);
		parent.set(Direction.INFO, DirectionValue.LTR_VALUE);
		parent.set(TextAlign.INFO, TextAlignValue.END_VALUE);
		child = style(parent);
		child.set(TextAlign.INFO, TextAlignValue.MATCH_PARENT_VALUE);
		assertSame(TextAlignValue.RIGHT_VALUE, child.get(TextAlign.INFO));

		parent = style(null);
		parent.set(TextAlign.INFO, TextAlignValue.CENTER_VALUE);
		child = style(parent);
		child.set(TextAlign.INFO, TextAlignValue.MATCH_PARENT_VALUE);
		assertSame(TextAlignValue.CENTER_VALUE, child.get(TextAlign.INFO));

		CSSStyle root = style(null);
		root.set(TextAlign.INFO, TextAlignValue.MATCH_PARENT_VALUE);
		assertSame(TextAlignValue.START_VALUE, root.get(TextAlign.INFO));

		parent = style(null);
		parent.set(TextAlignLast.INFO, TextAlignValue.RIGHT_VALUE);
		child = style(parent);
		child.set(TextAlignLast.INFO, TextAlignValue.MATCH_PARENT_VALUE);
		assertSame(TextAlignValue.RIGHT_VALUE, child.get(TextAlignLast.INFO));

		parent = style(null);
		child = style(parent);
		child.set(TextAlignLast.INFO, TextAlignValue.MATCH_PARENT_VALUE);
		assertSame(KeywordValue.AUTO, child.get(TextAlignLast.INFO));

		root = style(null);
		root.set(TextAlignLast.INFO, TextAlignValue.MATCH_PARENT_VALUE);
		assertSame(TextAlignValue.START_VALUE, root.get(TextAlignLast.INFO));

		try {
			TextValueUtils.toTextAlignParam(TextAlignValue.MATCH_PARENT_VALUE, child);
			fail("計算値にmatch-parentを残してはいけません");
		} catch (IllegalStateException e) {
			// 期待どおり
		}
	}

	private boolean checkRight(IBox box, double x) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(String.valueOf(box.getParams().element), 100, x + box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	public boolean check_root_start(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(0, x, 0.01);
			return true;
		}
		return false;
	}

	public boolean check_rtl_start(IBox box, int pageNumber, double x, double y) {
		return this.checkRight(box, x);
	}

	public boolean check_ltr_end(IBox box, int pageNumber, double x, double y) {
		return this.checkRight(box, x);
	}

	public boolean check_center(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(50, x + box.getWidth() / 2, 0.01);
			return true;
		}
		return false;
	}

	public boolean check_align_last(IBox box, int pageNumber, double x, double y) {
		return this.checkRight(box, x);
	}

	public boolean check_table_cell(IBox box, int pageNumber, double x, double y) {
		return this.checkRight(box, x);
	}
}
