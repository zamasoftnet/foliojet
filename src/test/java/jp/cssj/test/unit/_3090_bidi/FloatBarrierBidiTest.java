package jp.cssj.test.unit._3090_bidi;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.List;

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

/** flushText で段落を切らず、float barrier を越えて解決する。 */
public class FloatBarrierBidiTest extends AbstractTestCase {
	private boolean sawJoinedLine;
	private Rectangle2D floatBounds;
	private final List<Rectangle2D> textBounds = new ArrayList<>();

	public FloatBarrierBidiTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		this.session.property("layout.bidi.paragraph", "true");
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/3090-bidi/float-barrier.html"),
				"text/html", null);
	}

	public boolean check_barrier(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertFalse(line.getVisualContents().isEmpty());
		final StringBuilder logical = new StringBuilder();
		line.getText(logical);
		assertTrue(logical.length() > 0);
		final String actual = visualText(line);
		assertEquals(reorderLine(logical.toString()), actual);
		this.textBounds.add(new Rectangle2D.Double(x, y - line.getAscent(), line.getLineSize(),
				line.getAscent() + line.getDescent()));
		if (logical.indexOf("ABC") >= 0 && logical.indexOf("DEF") >= 0) {
			this.sawJoinedLine = true;
		}
		return true;
	}

	public boolean check_float(final IBox box, final int page, final double x, final double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		this.floatBounds = new Rectangle2D.Double(x, y, box.getWidth(), box.getHeight());
		return true;
	}

	public boolean check_barrier_after(final IBox box, final int page, final double x, final double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertTrue("flush/barrier split the paragraph before the following text", this.sawJoinedLine);
		assertNotNull("inline-start float was not drawn", this.floatBounds);
		assertTrue("rtl inline-start float must be on the right half: " + this.floatBounds,
				this.floatBounds.getCenterX() > 150);
		boolean compared = false;
		for (final Rectangle2D text : this.textBounds) {
			if (text.getMaxY() <= this.floatBounds.getMinY() || text.getMinY() >= this.floatBounds.getMaxY()) {
				continue;
			}
			compared = true;
			assertTrue("text overlaps the rtl inline-start float: text=" + text + " float=" + this.floatBounds,
					text.getMaxX() <= this.floatBounds.getMinX() + .01);
		}
		assertTrue("no text line shared the float's block-axis range", compared);
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
		final StringBuilder visual = new StringBuilder();
		for (final int index : Itemizer.reorderVisual(levels)) {
			visual.append(logical.charAt(index));
		}
		return visual.toString();
	}
}
