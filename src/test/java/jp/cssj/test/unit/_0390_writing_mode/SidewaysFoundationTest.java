package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.text.LeaderQuad;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;

/** sideways の used font・bidi・圏点と、通常縦組版の非回帰を固定します。 */
public class SidewaysFoundationTest extends AbstractTestCase {
	public SidewaysFoundationTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		this.session.property("layout.bidi.paragraph", "true");
		CTISessionHelper.transcodeFile(this.session,
				new File("files/unittest/0390-writing-mode/sideways-foundation.html"), "text/html", "UTF-8");
	}

	public boolean check_sw_rl(final IBox box, final int page, final double x, final double y) {
		return checkSideways(box, WritingMode.RL, WritingModeVariant.SIDEWAYS_CW);
	}

	public boolean check_sw_lr(final IBox box, final int page, final double x, final double y) {
		return checkSideways(box, WritingMode.LR, WritingModeVariant.SIDEWAYS_CCW);
	}

	public boolean check_vertical(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertSame(WritingMode.RL, line.getLineParams().flow);
		assertSame(WritingModeVariant.NORMAL, line.getLineParams().writingModeVariant);
		assertTrue(line.getLineParams().isVerticalTypesetting());
		assertTrue("normal vertical-rl must remain outside paragraph bidi",
				line.getVisualContents().isEmpty());
		final List<Text> runs = textRuns(line.getLogicalContents());
		assertFalse(runs.isEmpty());
		for (final Text run : runs) {
			assertSame(FontStyle.Direction.TB, run.getFontStyle().getDirection());
		}
		return true;
	}

	public boolean check_sw_em(final IBox box, final int page, final double x, final double y) {
		return checkEmphasis(box, '\u25CF', '\uFE45');
	}

	public boolean check_vertical_em(final IBox box, final int page, final double x, final double y) {
		return checkEmphasis(box, '\uFE45', '\u25CF');
	}

	private static boolean checkSideways(final IBox box, final WritingMode flow,
			final WritingModeVariant variant) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertSame(flow, line.getLineParams().flow);
		assertSame(variant, line.getLineParams().writingModeVariant);
		assertTrue(line.getLineParams().isHorizontalTypesetting());
		assertFalse("sideways mixed-direction line must use the paragraph-bidi visual tree",
				line.getVisualContents().isEmpty());
		assertTrue(visualText(line.getVisualContents()).contains("\u05D2\u05D1\u05D0"));
		assertNotNull(line.getLogicalLineEmission());
		assertTrue(line.getLogicalLineEmission().logicalText().contains("\u05D0\u05D1\u05D2"));

		final List<Text> runs = textRuns(line.getVisualContents());
		assertFalse(runs.isEmpty());
		boolean ltr = false, rtl = false;
		for (final Text run : runs) {
			final FontStyle.Direction direction = run.getFontStyle().getDirection();
			assertTrue("sideways run must not use a vertical FontStyle", direction != FontStyle.Direction.TB);
			ltr |= direction == FontStyle.Direction.LTR;
			rtl |= direction == FontStyle.Direction.RTL;
			assertSame("sideways used text-orientation", FontStyle.TextOrientation.MIXED,
					run.getFontStyle().getTextOrientation());
		}
		assertTrue("LTR run was not found", ltr);
		assertTrue("RTL run was not found", rtl);
		return true;
	}

	private static boolean checkEmphasis(final IBox box, final char expected, final char unexpected) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		final StringBuilder text = new StringBuilder();
		line.getText(text);
		assertTrue("default emphasis mark was not emitted: " + text, text.indexOf(String.valueOf(expected)) >= 0);
		assertTrue("wrong orientation's emphasis mark was emitted: " + text,
				text.indexOf(String.valueOf(unexpected)) < 0);
		return true;
	}

	private static List<Text> textRuns(final List<Object> contents) {
		final List<Text> runs = new ArrayList<>();
		collectTextRuns(contents, runs);
		return runs;
	}

	private static void collectTextRuns(final List<Object> contents, final List<Text> runs) {
		for (final Object content : contents) {
			if (content instanceof Text run) {
				runs.add(run);
			} else if (content instanceof AbstractTextBox.Inline inline
					&& inline.box instanceof InlineBox nested) {
				collectTextRuns(nested.getLogicalContents(), runs);
			}
		}
	}

	private static String visualText(final List<Object> contents) {
		final StringBuilder text = new StringBuilder();
		for (final Object content : contents) {
			if (content instanceof Text run) {
				text.append(run.getChars(), 0, run.getCharCount());
			} else if (content instanceof Control control) {
				text.append(control.getControlChar());
			} else if (content instanceof AbstractTextBox.Inline inline) {
				if (inline.box instanceof InlineBox nested) {
					text.append(visualText(nested.getLogicalContents()));
				} else {
					text.append('\uFFFC');
				}
			} else if (content instanceof LeaderQuad) {
				text.append('\uFFFC');
			}
		}
		return text.toString();
	}
}
