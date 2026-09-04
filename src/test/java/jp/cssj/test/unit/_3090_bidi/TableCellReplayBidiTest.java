package jp.cssj.test.unit._3090_bidi;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;

/** root を持たない分割セルの restyle でも段落 bidi flag を保持する。 */
public class TableCellReplayBidiTest extends AbstractTestCase {
	private final Set<Integer> pages = new HashSet<>();

	public TableCellReplayBidiTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		this.session.property("layout.bidi.paragraph", "true");
		this.session.property("processing.pass-count", "1");
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/3090-bidi/table-split.html"),
				"text/html", null);
	}

	public boolean check_table_bidi(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertFalse("split-cell replay lost paragraph bidi", line.getVisualContents().isEmpty());
		// <br> の制御文字(論理末尾)は L1 で段落レベルへ落ち、RTL 行では視覚左端=先頭に来るので除いて比べる
		assertEquals("ABC גבא", visualText(line.getVisualContents()).replace(String.valueOf((char) 10), ""));
		this.pages.add(page);
		return true;
	}

	public boolean check_table_after(final IBox box, final int page, final double x, final double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertTrue("table cell did not split across two pages: " + this.pages, this.pages.size() >= 2);
		return true;
	}

	private static String visualText(final java.util.List<Object> contents) {
		final StringBuilder value = new StringBuilder();
		for (final Object content : contents) {
			if (content instanceof Text text) {
				value.append(text.getChars(), 0, text.getCharCount());
			} else if (content instanceof Control control) {
				value.append(control.getControlChar());
			} else if (content instanceof AbstractTextBox.Inline inline && inline.box instanceof InlineBox nested) {
				value.append(visualText(nested.getLogicalContents()));
			}
		}
		return value.toString();
	}
}
