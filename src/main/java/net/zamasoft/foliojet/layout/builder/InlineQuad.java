package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.value.TextCombineValue;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.RubyUnitBox;
import net.zamasoft.foliojet.layout.box.impl.WarichuUnitBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.text.Quad;

public abstract class InlineQuad extends Quad {
	public static final byte INLINE_START = 1;
	public static final byte INLINE_END = 2;
	public static final byte INLINE_REPLACED = 3;
	public static final byte INLINE_BLOCK = 4;
	public static final byte INLINE_ABSOLUTE = 5;

	/**
	 * Character used for word segmentation of atomic inlines with no text (images/formulas without
	 * alternative text and empty inline blocks) (2026-10-04, TECH-20261003-004 ⑧). Follows CSS Text's rule
	 * to treat atomic inlines as U+FFFC. Previously used {@code BREAK} (an unconditional break opportunity),
	 * which bypassed line-start kinsoku (line-breaking rules) for an immediately following 「、」 or 「。」,
	 * so a line broke immediately after a formula and the next line began with 「、」.
	 */
	static final String OBJECT_REPLACEMENT = "￼";

	public double advance = 0;

	public abstract IBox getBox();

	public abstract byte getType();

	public final double getAdvance() {
		return this.advance;
	}

	public static InlineQuad createInlineBoxStartQuad(InlineBox inline) {
		return new InlineStartQuad(inline);
	}

	public static InlineQuad createInlineBoxEndQuad(InlineBox inline) {
		return new InlineEndQuad(inline);
	}

	public static InlineQuad createReplacedBoxQuad(AbstractReplacedBox replaced) {
		return new InlineReplacedQuad(replaced);
	}

	public static InlineQuad createInlineBlockBoxQuad(InlineBlockBox inlineBlock) {
		return new InlineBlockQuad(inlineBlock);
	}

	public static InlineQuad createInlineAbsoluteBoxQuad(IAbsoluteBox absoluteBox) {
		return new InlineAbsoluteQuad(absoluteBox);
	}

	/**
	 * Start of an inline.
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: InlineQuad.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class InlineStartQuad extends InlineQuad {
		public final InlineBox box;

		InlineStartQuad(InlineBox box) {
			this.box = box;
		}

		public IBox getBox() {
			return this.box;
		}

		public byte getType() {
			return INLINE_START;
		}

		public String getString() {
			return CONTINUE_BEFORE;
		}

		public String toString() {
			return "[INLINE]";
		}
	}

	/**
	 * End of an inline.
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: InlineQuad.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class InlineEndQuad extends InlineQuad {
		public final InlineBox box;

		InlineEndQuad(InlineBox box) {
			this.box = box;
		}

		public IBox getBox() {
			return this.box;
		}

		public byte getType() {
			return INLINE_END;
		}

		public String getString() {
			return CONTINUE_AFTER;
		}

		public String toString() {
			return "[/INLINE]";
		}
	}

	/**
	 * A replaced or block-level inline.
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: InlineQuad.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class InlineReplacedQuad extends InlineQuad {
		public final AbstractReplacedBox box;

		InlineReplacedQuad(AbstractReplacedBox box) {
			this.box = box;
		}

		public IBox getBox() {
			return this.box;
		}

		public byte getType() {
			return INLINE_REPLACED;
		}

		public String getString() {
			String alt = this.box.getReplacedParams().image.getAltString();
			return (alt == null || alt.length() == 0) ? OBJECT_REPLACEMENT : alt;
		}

		public String toString() {
			return "[INLINE-REPLACED]";
		}
	}

	/**
	 * An inline block.
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: InlineQuad.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class InlineBlockQuad extends InlineQuad {
		public final InlineBlockBox box;

		private String text = null;

		InlineBlockQuad(InlineBlockBox box) {
			this.box = box;
		}

		public IBox getBox() {
			return this.box;
		}

		public byte getType() {
			return INLINE_BLOCK;
		}

		/**
		 * The string line breaking sees for this box: U+FFFC for an inline-block, as CSS Text treats an atomic
		 * inline, so it no longer joins the text around it into one word (the line-start and line-end rules of the
		 * text beside it still hold); the text inside for a box that stands for its characters
		 * ({@link #standsForCharacters}).
		 *
		 * <p>
		 * Until 2026-11-10 every inline-block gave its own text, so the first and last characters inside joined the
		 * text around it: three textareas holding "x" read as "xxx" and stayed on one line 376pt long in a box 29pt
		 * wide (sweep seed 82920575, triage §194), and "T1" before an inline-block holding "T2" could not break
		 * after it, where Chrome breaks in both. Images and empty inline-blocks were U+FFFC already.
		 * </p>
		 */
		public String getString() {
			if (this.text == null) {
				if (standsForCharacters(this.box)) {
					StringBuilder textBuff = new StringBuilder();
					this.box.getText(textBuff);
					this.text = textBuff.toString();
				} else {
					this.text = "";
				}
			}
			return this.text.length() == 0 ? OBJECT_REPLACEMENT : this.text;
		}

		/**
		 * Whether an inline-block stands for the characters it holds, so line breaking reads them as text: a ruby or
		 * warichu unit, a list marker, a tate-chu-yoko ({@code text-combine-upright} or {@code -cssj-text-combine}),
		 * and the anonymous unit that carries an emphasis mark over one character (StyleEventMachine; the only
		 * inline-block of an anonymous element, which stays the same singleton when a recipe is frozen and replayed).
		 * Chrome does not break between tate-chu-yoko units or after an emphasised opening bracket either.
		 */
		private static boolean standsForCharacters(final InlineBlockBox box) {
			if (box instanceof RubyUnitBox || box instanceof WarichuUnitBox || box instanceof InsideMarkerBox
					|| box instanceof OutsideMarkerBox) {
				return true;
			}
			final BlockParams params = box.getBlockParams();
			return params.textCombine != TextCombineValue.NONE || params.element == CSSElement.ANON;
		}

		public String toString() {
			return "[INLINE-BLOCK]";
		}
	}

	public static class InlineAbsoluteQuad extends InlineQuad {
		public final IAbsoluteBox box;

		InlineAbsoluteQuad(IAbsoluteBox box) {
			this.box = box;
		}

		public IBox getBox() {
			return this.box;
		}

		public byte getType() {
			return INLINE_ABSOLUTE;
		}

		public String getString() {
			return JOIN;
		}

		public String toString() {
			return "[INLINE-ABSOLUTE]";
		}
	}
}
