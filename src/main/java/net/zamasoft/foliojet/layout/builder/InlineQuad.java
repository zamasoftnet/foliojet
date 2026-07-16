package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.text.Quad;

public abstract class InlineQuad extends Quad {
	public static final byte INLINE_START = 1;
	public static final byte INLINE_END = 2;
	public static final byte INLINE_REPLACED = 3;
	public static final byte INLINE_BLOCK = 4;
	public static final byte INLINE_ABSOLUTE = 5;

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
	 * インラインの開始です。
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
	 * インラインの終了です。
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
	 * 置換可能/あるいはブロックレベルのインラインです。
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
			return (alt == null || alt.length() == 0) ? BREAK : alt;
		}

		public String toString() {
			return "[INLINE-REPLACED]";
		}
	}

	/**
	 * インラインブロックです。
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

		public String getString() {
			if (this.text == null) {
				StringBuilder textBuff = new StringBuilder();
				this.box.getText(textBuff);
				this.text = textBuff.toString();
			}
			return this.text.length() == 0 ? BREAK : this.text;
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
