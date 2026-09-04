package jp.cssj.test.unit._3090_bidi;

import java.io.File;
import java.text.Bidi;
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
import net.zamasoft.pdfg2d.gc.text.pipeline.Itemizer;

/** two-pass/replay の段落方向と論理抽出を固定する。 */
public class TwoPassBidiTest extends AbstractTestCase {
	private final Set<Integer> paragraphPages = new HashSet<>();
	private long paragraphId;

	public TwoPassBidiTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		this.session.property("layout.bidi.paragraph", "true");
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/3090-bidi/two-pass.html"),
				"text/html", null);
	}

	public boolean check_twopass(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertFalse("replay line must retain paragraph bidi metadata", line.getVisualContents().isEmpty());
		if (this.paragraphId == 0) {
			this.paragraphId = line.getBidiParagraphId();
		}
		assertTrue("replay line has no paragraph id", this.paragraphId != 0);
		assertEquals("fragment replay changed the resolved paragraph", this.paragraphId,
				line.getBidiParagraphId());
		final StringBuilder logical = new StringBuilder();
		line.getText(logical);
		assertTrue(logical.length() > 0);
		assertEquals(reorderLine(logical.toString()), visualText(line));
		this.paragraphPages.add(page);
		return true;
	}

	public boolean check_after(final IBox box, final int page, final double x, final double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertTrue("the replay paragraph must cross two pages: " + this.paragraphPages,
				this.paragraphPages.size() >= 2);
		return true;
	}

	private static String visualText(final AbstractLineBox line) {
		final StringBuilder value = new StringBuilder();
		appendVisual(line.getVisualContents(), value);
		return value.toString();
	}

	private static void appendVisual(final java.util.List<Object> contents, final StringBuilder value) {
		for (final Object content : contents) {
			if (content instanceof Text text) {
				value.append(text.getChars(), 0, text.getCharCount());
			} else if (content instanceof Control control) {
				value.append(control.getControlChar());
			} else if (content instanceof AbstractTextBox.Inline inline && inline.box instanceof InlineBox nested) {
				appendVisual(nested.getLogicalContents(), value);
			}
		}
	}

	private static String reorderLine(final String logical) {
		final Bidi bidi = new Bidi(logical, Bidi.DIRECTION_RIGHT_TO_LEFT);
		final byte[] levels = new byte[logical.length()];
		for (int i = 0; i < levels.length; ++i) {
			levels[i] = (byte) bidi.getLevelAt(i);
		}
		final int[] order = Itemizer.reorderVisual(levels);
		final StringBuilder visual = new StringBuilder();
		for (final int index : order) {
			visual.append(logical.charAt(index));
		}
		return visual.toString();
	}

}
