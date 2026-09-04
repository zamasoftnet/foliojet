package jp.cssj.test.unit._3090_bidi;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.InlineFragmentView;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.text.LeaderQuad;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;

/** bidi-isolation-design.md Stage 1 受入 1〜7。 */
public class ParagraphBidiTest extends AbstractTestCase {
	private static final String SEMANTIC_TEXT = "אבג-ABC-דהו-DEF-וזח-GHI-טיך-JKL";
	private int fragmentLines, startEdges, endEdges;
	private int relativeLines, relativeFragments;
	private final Map<InlineFragmentView, double[]> relativeDrawPositions = new IdentityHashMap<>();

	public ParagraphBidiTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		this.session.property("layout.bidi.paragraph", "true");
		this.session.property("processing.page-references", "true");
		this.session.property("processing.pass-count", "3");
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/3090-bidi/stage1.html"),
				"text/html", null);
	}

	public boolean check_rtl(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertEquals("ABC \u05D2\u05D1\u05D0", visualText(line.getVisualContents()));
		assertVisualCoordinates(line, "ABC \u05D2\u05D1\u05D0");
		assertEquals("\u05D0\u05D1\u05D2 ABC", logicalText(line));
		assertEquals("\u05D0\u05D1\u05D2 ABC", line.getLogicalLineEmission().logicalText());
		assertTrue("direction:rtl + text-align:start must use the right edge", line.getLineAlign() > 0);
		return true;
	}

	public boolean check_embed(final IBox box, final int page, final double x, final double y) {
		return checkLine(box, "abc DEF \u05D2\u05D1\u05D0 xyz", "abc \u05D0\u05D1\u05D2 DEF xyz");
	}

	public boolean check_override(final IBox box, final int page, final double x, final double y) {
		return checkLine(box, "21-CBA", "ABC-12");
	}

	public boolean check_nested(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertEquals("\u05D5\u05D4\u05D3\u05D2\u05D1\u05D0", visualText(line.getVisualContents()));
		assertVisualCoordinates(line, "\u05D5\u05D4\u05D3\u05D2\u05D1\u05D0");
		assertEquals("\u05D0\u05D1\u05D2\u05D3\u05D4\u05D5", logicalText(line));
		assertTrue("nested inline must be rebuilt as draw-only fragments", containsFragment(line.getVisualContents()));
		return true;
	}

	public boolean check_trailing(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertTrue(visualText(line.getVisualContents()).startsWith("  ABC \u05D2\u05D1\u05D0"));
		assertVisualCoordinates(line, visualText(line.getVisualContents()));
		return true;
	}

	public boolean check_mirror(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		// visual tree の文字列は論理 scalar を保つ。奇数 level では GID だけ鏡像化する。
		final String visual = "»\u05D5\u05D4\u05D3« }ABC{ ]\u05D2\u05D1\u05D0[ )\u05D2\u05D1\u05D0(";
		assertEquals(visual, visualText(line.getVisualContents()));
		assertVisualCoordinates(line, visual);
		assertMirroredPair(line.getVisualContents(), '(', ')');
		assertMirroredPair(line.getVisualContents(), '[', ']');
		assertMirroredPair(line.getVisualContents(), '{', '}');
		assertMirroredPair(line.getVisualContents(), '«', '»');
		assertEquals("(\u05D0\u05D1\u05D2) [\u05D0\u05D1\u05D2] {ABC} «\u05D3\u05D4\u05D5»",
				logicalText(line));
		return true;
	}

	public boolean check_atomic(final IBox box, final int page, final double x, final double y) {
		return checkLine(box, "\u05D3\u05D2 \uFFFC \u05D1\u05D0", "\u05D0\u05D1  \u05D2\u05D3",
				"\u05D0\u05D1 \uFFFC \u05D2\u05D3");
	}

	public boolean check_ruby(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertEquals("前\u05D0\u05D1後", logicalText(line));
		assertEquals("前\uFFFC後", line.getLogicalLineEmission().logicalText());
		assertTrue("ruby unit must remain one outer atomic atom", visualText(line.getVisualContents()).indexOf('\uFFFC') >= 0);
		assertSlices(line, line.getVisualContents());
		return true;
	}

	public boolean check_warichu(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertEquals("前\u05D0\u05D1\u05D2\u05D3後", logicalText(line));
		assertEquals("前\uFFFC後", line.getLogicalLineEmission().logicalText());
		assertTrue("warichu unit must remain one outer atomic atom",
				visualText(line.getVisualContents()).indexOf('\uFFFC') >= 0);
		assertSlices(line, line.getVisualContents());
		return true;
	}

	public boolean check_fragment_edges(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		++this.fragmentLines;
		countEdges(line.getVisualContents());
		return true;
	}

	public boolean check_fragment_edges_after(final IBox box, final int page, final double x, final double y) {
		if (box.getType() != net.zamasoft.foliojet.layout.box.BoxType.BLOCK) {
			return false;
		}
		assertTrue("inline fixture did not wrap", this.fragmentLines >= 2);
		assertEquals("start edge must occur on exactly one visual fragment", 1, this.startEdges);
		assertEquals("end edge must occur on exactly one visual fragment", 1, this.endEdges);
		return true;
	}

	public boolean check_ltr_fast(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertTrue("pure LTR paragraph must keep the logical drawing tree", line.getVisualContents().isEmpty());
		assertEquals("Pure LTR fast path.", logicalText(line));
		assertTrue("pure LTR line has no paragraph id", line.getBidiParagraphId() != 0);
		return true;
	}

	public boolean check_relative(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		++this.relativeLines;
		double cursor = x;
		for (final Object content : line.getVisualContents()) {
			if (content instanceof Text text) {
				cursor += text.getAdvance();
			} else if (content instanceof Control control) {
				cursor += control.getAdvance();
			} else if (content instanceof AbstractTextBox.Inline inline) {
				if (inline.box instanceof InlineFragmentView fragment
						&& fragment.source().getParams().element != null
						&& "relative_span".equals(fragment.source().getParams().element.id())) {
					final double voffset = fragment.getAscent() - line.getAscent();
					this.relativeDrawPositions.put(fragment,
							new double[] { cursor, y - voffset - inline.verticalAlign });
				}
				cursor += inline.box.getLineExtent(line.getLineParams().flow);
			} else if (content instanceof LeaderQuad leader) {
				cursor += leader.getAdvance();
			}
		}
		return true;
	}

	public boolean check_relative_span(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof InlineFragmentView fragment)) {
			return false;
		}
		final double[] raw = this.relativeDrawPositions.remove(fragment);
		assertNotNull("relative fragment was not found in its line visual tree", raw);
		assertEquals("left:-5pt was not applied to the visual fragment", raw[0] - 5, x, .01);
		assertEquals("top:5pt was not applied to the visual fragment", raw[1] + 5, y, .01);
		++this.relativeFragments;
		return true;
	}

	public boolean check_relative_after(final IBox box, final int page, final double x, final double y) {
		if (box.getType() != net.zamasoft.foliojet.layout.box.BoxType.BLOCK) {
			return false;
		}
		assertTrue("positioned inline fixture did not wrap", this.relativeLines >= 2);
		assertTrue("positioned inline did not produce multiple visual fragments", this.relativeFragments >= 2);
		assertTrue("some relative visual fragments were not drawn", this.relativeDrawPositions.isEmpty());
		return true;
	}

	public boolean check_semantic_result(final IBox box, final int page, final double x, final double y) {
		if (box.getType() != net.zamasoft.foliojet.layout.box.BoxType.BLOCK) {
			return false;
		}
		assertEquals("TARGET[" + SEMANTIC_TEXT + "]", logicalText(box));
		return true;
	}

	private static boolean checkLine(final IBox box, final String visual, final String logical) {
		return checkLine(box, visual, logical, logical);
	}

	private static boolean checkLine(final IBox box, final String visual, final String logical,
			final String serialized) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertEquals(visual, visualText(line.getVisualContents()));
		assertVisualCoordinates(line, visual);
		assertEquals(logical, logicalText(line));
		assertEquals(serialized, line.getLogicalLineEmission().logicalText());
		return true;
	}

	private static String logicalText(final IBox box) {
		final StringBuilder text = new StringBuilder();
		box.getText(text);
		return text.toString();
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

	private static boolean containsFragment(final List<Object> contents) {
		for (final Object content : contents) {
			if (content instanceof AbstractTextBox.Inline inline) {
				if (inline.box instanceof InlineFragmentView) {
					return true;
				}
				if (inline.box instanceof InlineBox nested && containsFragment(nested.getLogicalContents())) {
					return true;
				}
			}
		}
		return false;
	}

	private void countEdges(final List<Object> contents) {
		for (final Object content : contents) {
			if (!(content instanceof AbstractTextBox.Inline inline) || !(inline.box instanceof InlineBox nested)) {
				continue;
			}
			if (nested instanceof InlineFragmentView fragment) {
				this.startEdges += fragment.keepsStartEdge() ? 1 : 0;
				this.endEdges += fragment.keepsEndEdge() ? 1 : 0;
				// rtl の要素なので start 辺は右、end 辺は左に出る(中間 fragment は両側とも無い)
				final double left = fragment.getFrame().frame.border.getLeft().width;
				final double right = fragment.getFrame().frame.border.getRight().width;
				assertEquals("rtl fragment: right border iff start edge", fragment.keepsStartEdge(), right > 0);
				assertEquals("rtl fragment: left border iff end edge", fragment.keepsEndEdge(), left > 0);
			}
			this.countEdges(nested.getLogicalContents());
		}
	}

	private static void assertMirroredPair(final List<Object> contents, final char logicalOpen,
			final char logicalClose) {
		final String visual = visualText(contents);
		assertTrue("logical closing character must be left of opening character: " + logicalOpen + logicalClose,
				visual.indexOf(logicalClose) < visual.indexOf(logicalOpen));
		assertMirroredGlyph(contents, logicalClose, logicalOpen);
		assertMirroredGlyph(contents, logicalOpen, logicalClose);
	}

	private static void assertMirroredGlyph(final List<Object> contents, final char logical,
			final char displayed) {
		final Text run = findText(contents, logical);
		assertNotNull("mirrored glyph source was not found: " + logical, run);
		final int expected = run.getFontMetrics().getFontSource().createFont().toGID(displayed, logical,
				run.getFontStyle().getFeatures());
		assertEquals(expected, run.getGlyphIds()[0]);
	}

	private static Text findText(final List<Object> contents, final char logical) {
		for (final Object content : contents) {
			if (content instanceof Text run && run.getCharCount() == 1 && run.getChars()[0] == logical) {
				return run;
			}
			if (content instanceof AbstractTextBox.Inline inline && inline.box instanceof InlineBox nested) {
				final Text run = findText(nested.getLogicalContents(), logical);
				if (run != null) {
					return run;
				}
			}
		}
		return null;
	}

	private record PositionedText(double x, String text) {
	}

	/** 描画時と同じ advance で x を積み、左→右の run 順を受入文字列と照合する。 */
	private static void assertVisualCoordinates(final AbstractLineBox line, final String expected) {
		final List<PositionedText> positioned = new ArrayList<>();
		final double[] cursor = { line.getLineAlign() };
		collectPositions(line.getVisualContents(), cursor, positioned, line.getLineParams().flow);
		assertSlices(line, line.getVisualContents());
		positioned.sort(Comparator.comparingDouble(PositionedText::x));
		final StringBuilder actual = new StringBuilder();
		for (final PositionedText value : positioned) {
			actual.append(value.text());
		}
		assertEquals(expected, actual.toString());
	}

	private static void assertSlices(final AbstractLineBox line, final List<Object> contents) {
		for (final Object content : contents) {
			if (content instanceof AbstractTextBox.Inline inline && inline.box instanceof InlineBox nested) {
				assertSlices(line, nested.getLogicalContents());
			} else {
				assertNotNull("visual leaf has no Folio-side BidiSlice: " + content, line.getBidiSlice(content));
			}
		}
	}

	private static void collectPositions(final List<Object> contents, final double[] cursor,
			final List<PositionedText> positioned, final WritingMode lineFlow) {
		for (final Object content : contents) {
			if (content instanceof Text run) {
				positioned.add(new PositionedText(cursor[0], new String(run.getChars(), 0, run.getCharCount())));
				cursor[0] += run.getAdvance();
			} else if (content instanceof Control control) {
				positioned.add(new PositionedText(cursor[0], String.valueOf(control.getControlChar())));
				cursor[0] += control.getAdvance();
			} else if (content instanceof AbstractTextBox.Inline inline) {
				if (inline.box instanceof InlineBox nested) {
					final double end = cursor[0] + nested.getLineExtent(lineFlow);
					final double[] child = { cursor[0] + nested.getFrame().getFrameLineStart(lineFlow) };
					collectPositions(nested.getLogicalContents(), child, positioned, lineFlow);
					cursor[0] = end;
				} else {
					positioned.add(new PositionedText(cursor[0], "\uFFFC"));
					cursor[0] += inline.box.getLineExtent(lineFlow);
				}
			} else if (content instanceof LeaderQuad leader) {
				positioned.add(new PositionedText(cursor[0], "\uFFFC"));
				cursor[0] += leader.getAdvance();
			}
		}
	}
}
