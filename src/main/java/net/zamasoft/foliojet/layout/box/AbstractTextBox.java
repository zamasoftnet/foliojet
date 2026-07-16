package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.box.content.JustificationState;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Decoration;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.TextShadow;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.foliojet.layout.text.breaking.LineBreakRules;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;
import net.zamasoft.pdfg2d.pdf.font.cid.missing.MissingCIDFontSource;

public abstract class AbstractTextBox extends AbstractBox {
	/**
	 * テキストの部分を25%灰色の枠で囲みます。
	 */
	private static final boolean DEBUG = false;

	/**
	 * テキストボックス内に配置されたインラインです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: AbstractTextBox.java 1633 2023-02-12 03:22:32Z miyabe $
	 */
	public static class Inline {
		public final IInlineBox box;
		public double verticalAlign = 0;

		public Inline(IInlineBox box) {
			this.box = box;
		}

		public String toString() {
			return this.box.toString();
		}
	}

	protected Decoration decoration;

	/**
	 * 内部に含まれるテキストとインラインボックスです。 要素は Text, Control, Inline, IAbsoluteBox のいずれかです。
	 */
	protected List<Object> contents = null;

	protected double ascent = 0;

	protected double descent = 0;

	protected double lineSize;

	public abstract AbstractTextParams getTextParams();

	protected final void setDecoration(final Decoration decoration) {
		final byte flags = (byte) (this.getTextParams().decoration & 7);
		Color underline;
		Color overline;
		Color lineThrough;
		if (decoration == null) {
			if (flags == 0) {
				return;
			}
			underline = overline = lineThrough = null;
		} else {
			underline = decoration.underlineColor;
			overline = decoration.overlineColor;
			lineThrough = decoration.lineThroughColor;
		}
		final Color color = this.getTextParams().color;
		underline = ((flags & AbstractTextParams.DECORATION_UNDERLINE) != 0) ? color : underline;
		overline = ((flags & AbstractTextParams.DECORATION_OVERLINE) != 0) ? color : overline;
		lineThrough = ((flags & AbstractTextParams.DECORATION_LINE_THROUGH) != 0) ? color : lineThrough;
		this.decoration = new Decoration(underline, overline, lineThrough);
	}

	protected final void add(Object content) {
		assert content instanceof Text || content instanceof Control || content instanceof Inline
				|| content instanceof IAbsoluteBox;
		if (this.contents == null) {
			this.contents = new ArrayList<Object>();
		}
		this.contents.add(content);
	}

	/**
	 * 内部の最後のテキストのソース文字終端(オフセット+文字数)を
	 * 返します(M6b v3)。切断で前断片に残った内容の終端=残余の再開
	 * 位置の構造的に正確な導出に使います。
	 *
	 * @return 最後のテキストの文字終端(テキストがなければ -1)
	 */
	public final int lastCharEnd() {
		if (this.contents != null) {
			for (int i = this.contents.size() - 1; i >= 0; --i) {
				final Object content = this.contents.get(i);
				if (content instanceof Text text && text.getCharOffset() >= 0) {
					return text.getCharOffset() + text.getCharCount();
				}
				if (content instanceof AbstractTextBox inline) {
					final int end = inline.lastCharEnd();
					if (end >= 0) {
						return end;
					}
				}
			}
		}
		return -1;
	}

	/**
	 * 内部の最初のテキストのソース文字オフセットを返します(M6b)。
	 * セグメント再駆動の再開位置(BreakToken)の導出に使います。
	 *
	 * @return 最初のテキストの文字オフセット(テキストがなければ -1)
	 */
	public final int firstCharOffset() {
		if (this.contents != null) {
			for (final Object content : this.contents) {
				if (content instanceof Text text) {
					return text.getCharOffset();
				}
				if (content instanceof AbstractTextBox inline) {
					final int offset = inline.firstCharOffset();
					if (offset >= 0) {
						return offset;
					}
				}
			}
		}
		return -1;
	}

	public final double getLineSize() {
		return this.lineSize;
	}

	public final double getPageSize() {
		return this.ascent + this.descent;
	}

	public final double getWidth() {
		if (this.getTextParams().flow.isVertical()) {
			// 縦書き
			return this.getPageSize();
		} else {
			// 横書き
			return this.lineSize;
		}
	}

	public final double getHeight() {
		if (this.getTextParams().flow.isVertical()) {
			// 縦書き
			return this.lineSize;
		} else {
			// 横書き
			return this.getPageSize();
		}
	}

	public double getInnerWidth() {
		return this.getWidth();
	}

	public double getInnerHeight() {
		return this.getHeight();
	}

	public final void addText(Text text) {
		assert text.getGlyphCount() > 0;
		this.add(text);
	}

	public final void addControl(Control control) {
		// System.out.println(control);
		this.add(control);
	}

	/**
	 * インラインを追加します。
	 * 
	 * @param box
	 */
	public final void addInline(IInlineBox box) {
		if (box.getType() == BoxType.INLINE) {
			assert this.getParams().element != box.getParams().element
					: (box.getParams().element + "\n" + this.getParams() + "\n" + box.getParams());
			InlineBox inline = (InlineBox) box;
			inline.setDecoration(this.decoration);
		}
		this.add(new Inline(box));
	}

	public final void addAbsolute(IAbsoluteBox box) {
		this.add(box);
	}

	public final void addAdvance(double advance) {
		this.lineSize += advance;
	}

	/**
	 * 両あわせのために拡大できるポイントをカウントします。
	 * 
	 * @param state TODO
	 * 
	 * @return
	 */
	protected final int countJustificationPoints(JustificationState state) {
		if (this.contents == null) {
			return 0;
		}
		LineBreakRules hyph = this.getTextParams().lineBreakRules;
		int count = 0;
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> {
				// テキスト
				int glen = text.getGlyphCount();
				if (glen <= 0) {
					break;
				}
				char[] ch = text.getChars();
				byte[] clens = text.getClusterLengths();
				int k = 0;
				for (int j = 0; j < glen; ++j) {
					char c1 = ch[k];
					k += clens[j];
					char c2 = ch[k - 1];
					if (state.prevChar != 0 && hyph.canSeparate(state.prevChar, c1)) {
						++count;
					}
					state.prevChar = c2;
				}
			}

			case Inline content -> {
				// インライン
				if (content.box.getType() == BoxType.INLINE) {
					InlineBox inline = (InlineBox) content.box;
					count += inline.countJustificationPoints(state);
				}
			}

			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					// 幅0のソフトハイフンは語中の伸長点を作らない
					state.prevChar = ctrl.getControlChar();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// 位置に影響しない
			}

			default -> throw new IllegalStateException();
			}
		}
		return count;
	}

	protected final void justify(double unitSpacing, JustificationState state) {
		if (this.contents == null) {
			return;
		}
		LineBreakRules hyph = this.getTextParams().lineBreakRules;
		for (int i = 0; i < this.contents.size(); ++i) {
			double da = 0;
			switch (this.contents.get(i)) {
			case Text text -> {
				// テキスト
				int glen = text.getGlyphCount();
				if (glen <= 0) {
					break;
				}
				char[] ch = text.getChars();
				byte[] clens = text.getClusterLengths();
				double[] xadvances = text.getXAdvances(true);
				int k = 0;
				for (int j = 0; j < glen; ++j) {
					char c1 = ch[k];
					k += clens[j];
					char c2 = ch[k - 1];
					if (state.prevChar != 0 && hyph.canSeparate(state.prevChar, c1)) {
						xadvances[j] += unitSpacing;
						da += unitSpacing;
					}
					state.prevChar = c2;
				}
			}

			case Inline inline -> {
				// インライン
				if (inline.box.getType() == BoxType.INLINE) {
					InlineBox inlineBox = (InlineBox) inline.box;
					da = inlineBox.getLineSize();
					inlineBox.justify(unitSpacing, state);
					da = inlineBox.getLineSize() - da;
				}
			}

			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					// 幅0のソフトハイフンは語中の伸長点を作らない
					state.prevChar = ctrl.getControlChar();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// 位置に影響しない
			}

			default -> throw new IllegalStateException();
			}
			if (da != 0) {
				this.addAdvance(da);
			}
		}
	}

	public abstract boolean isContextBox();

	public void finishLayout(IFramedBox containerBox) {
		if (this.contents == null) {
			return;
		}
		if (this.isContextBox()) {
			containerBox = (IFramedBox) this;
		}
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case IAbsoluteBox absoluteBox ->
				// 絶対配置
				absoluteBox.finishLayout(containerBox);

			case Inline inline ->
				// インライン
				inline.box.finishLayout(containerBox);

			default -> {
				// テキスト
			}
			}
		}
	}

	protected void verticalAlign(AbstractLineBox lineBox, double baseline) {
		if (this.contents == null) {
			return;
		}
		final AbstractLineParams lineParams = lineBox.getLineParams();
		for (int i = 0; i < this.contents.size(); ++i) {
			if (this.contents.get(i) instanceof Inline inline) {
				// インライン
				final IInlineBox inlineBox = inline.box;
				final InlinePos pos = inlineBox.getInlinePos();
				double ascent;
				double descent;
				switch (inlineBox.getType()) {
				case INLINE: {
					// 普通のインライン
					final InlineBox box = (InlineBox) inlineBox;
					ascent = box.getAscent();
					descent = box.getDescent();
				}
					break;
				case BLOCK: {
					// インラインブロック
					final AbstractContainerBox box = (AbstractContainerBox) inlineBox;
					final BlockParams params = box.getBlockParams();
					if (lineParams.flow.isVertical()) {
						// 縦書き
						if (params.flow.isVertical()) {
							descent = box.getLastDescent();
							if (LayoutUtils.isNone(descent)) {
								descent = inlineBox.getWidth() / 2.0;
							}
						} else {
							// 縦中横
							descent = inlineBox.getWidth() / 2.0;
						}
						ascent = inlineBox.getWidth() - descent;
					} else {
						// 横書き
						if (params.flow.isVertical()) {
							// 横中縦
							descent = 0;
						} else {
							descent = box.getLastDescent();
							if (LayoutUtils.isNone(descent)) {
								descent = 0;
							}
						}
						ascent = inlineBox.getHeight() - descent;
					}
				}
					break;
				case REPLACED: {
					// 画像
					if (lineParams.flow.isVertical()) {
						// 縦書き
						ascent = descent = inlineBox.getWidth() / 2.0;
					} else {
						// 横書き
						descent = 0;
						ascent = inlineBox.getHeight();
					}
				}
					break;
				default:
					throw new IllegalStateException();
				}
				inline.verticalAlign = pos.verticalAlign.getVerticalAlign(this, lineBox, ascent, descent,
						pos.lineHeight, baseline);
				if (inlineBox.getType() == BoxType.INLINE) {
					((InlineBox) inlineBox).verticalAlign(lineBox, baseline + inline.verticalAlign);
				}
			}
		}
	}

	protected static class TextSequenceDrawable extends AbstractDrawable {
		protected final List<Object> contents;
		protected final int off, len;
		protected final AbstractTextParams params;
		protected final double ascent, descent;

		public TextSequenceDrawable(PageBox pageBox, Shape clip, AffineTransform transform, List<Object> contents,
				int off, int len, AbstractTextParams params, double ascent, double descent) {
			super(pageBox, clip, params.opacity, transform);
			this.contents = contents;
			this.off = off;
			this.len = len;
			this.params = params;
			this.ascent = ascent;
			this.descent = descent;
		}

		private void missingFont(Text text) {
			final String c = new String(text.getChars(), 0, text.getCharCount());
			final StringBuilder codes = new StringBuilder();
			for (int j = 0; j < c.length(); ++j) {
				codes.append("[").append(Integer.toHexString(c.charAt(j))).append("]");
			}
			this.pageBox.getUserAgent().message(MessageCodes.WARN_MISSING_FONT, c + codes.toString());
		}

		public String describe() {
			final StringBuilder text = new StringBuilder();
			for (int i = this.off; i < this.off + this.len; ++i) {
				if (this.contents.get(i) instanceof Text t) {
					text.append(t.getChars(), 0, t.getCharCount());
				}
			}
			return String.format(java.util.Locale.ROOT, "Text[\"%s\" asc=%.2f desc=%.2f]", text, this.ascent,
					this.descent);
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			// 影
			if (this.params.textShadows != null) {
				for (int i = this.params.textShadows.length - 1; i >= 0; --i) {
					TextShadow shadow = params.textShadows[i];
					try (final var gcState = gc.begin()) {
						gc.setFillPaint(shadow.color);
						this.drawText(gc, x + shadow.x, y + shadow.y);
					}
				}
			}

			// テキスト本体
			try (final var gcState = gc.begin()) {
				if (this.params.color != null) {
					gc.setFillPaint(this.params.color);
				}
				if (this.params.textStrokeWidth != 0) {
					gc.setLineJoin(GC.LineJoin.ROUND);
					gc.setLinePattern(GC.STROKE_SOLID);
					gc.setLineWidth(this.params.textStrokeWidth);
					gc.setStrokePaint(this.params.textStrokeColor);
					gc.setTextMode(GC.TextMode.FILL_STROKE);
				}
				this.drawText(gc, x, y);
			}
		}

		private void drawText(GC gc, double x, double y) {
			double xx = x, yy = y;
			if (this.params.flow.isVertical()) {
				// 縦書き
				for (int i = 0; i < this.len; ++i) {
					final Text text = (Text) this.contents.get(i + this.off);
					if (text.getFontMetrics().getFontSource() == MissingCIDFontSource.INSTANCES_TB) {
						this.missingFont(text);
					}
					gc.drawText(text, x + this.descent, y);
					y += text.getAdvance();
					if (DEBUG) {
						try (final var gcState = gc.begin()) {
							gc.setStrokePaint(RGBColor.create(63, 63, 63));
							gc.draw(new Rectangle2D.Double(xx, yy, this.ascent + this.descent, y - yy));
						}
					}
				}
			} else {
				// 横書き
				for (int i = 0; i < this.len; ++i) {
					final Text text = (Text) this.contents.get(i + this.off);
					if (text.getFontMetrics().getFontSource() == MissingCIDFontSource.INSTANCES_LTR) {
						this.missingFont(text);
					}
					gc.drawText(text, x, y + this.ascent);
					x += text.getAdvance();
					if (DEBUG) {
						try (final var gcState = gc.begin()) {
							gc.setStrokePaint(RGBColor.create(63, 63, 63));
							gc.draw(new Rectangle2D.Double(xx, y, x - xx, this.ascent + this.descent));
						}
					}
				}
			}
		}
	}

	protected static class TextDecorationDrawable extends AbstractDrawable {
		protected final AbstractTextParams params;
		protected final Decoration decoration;
		protected final double ascent, descent;
		protected final double width, height;

		public TextDecorationDrawable(PageBox pageBox, Shape clip, AffineTransform transform, AbstractTextParams params,
				Decoration decoration, double ascent, double descent, double width, double height) {
			super(pageBox, clip, params.opacity, transform);
			this.params = params;
			this.decoration = decoration;
			this.ascent = ascent;
			this.descent = descent;
			this.width = width;
			this.height = height;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			try (final var gcState = gc.begin()) {

				Color color = this.params.color;
				if (color != null) {
					gc.setStrokePaint(color);
					gc.setFillPaint(color);
				}

				// 装飾
				double fontSize = this.params.fontStyle.getSize();
				double strokeSize = fontSize * this.params.decorationThickness;
				gc.setLineWidth(strokeSize);
				if (this.params.flow.isVertical()) {
					// 縦書き進行
					x += this.descent;
					final double lineAxis = this.height;
					if (this.decoration.underlineColor != null) {
						// 下線
						gc.setStrokePaint(this.decoration.underlineColor);
						double lineX = x - this.params.getFontListMetrics().getMaxDescent();
						Line2D line = new Line2D.Double(lineX, y, lineX, y + lineAxis);
						gc.draw(line);

					}
					if (this.decoration.overlineColor != null) {
						// 上線
						gc.setStrokePaint(this.decoration.overlineColor);
						double lineX = x + this.params.getFontListMetrics().getMaxAscent();
						Line2D line = new Line2D.Double(lineX, y, lineX, y + lineAxis);
						gc.draw(line);
					}
					if (this.decoration.lineThroughColor != null) {
						// 打ち消し線
						gc.setStrokePaint(this.decoration.lineThroughColor);
						Line2D line = new Line2D.Double(x, y, x, y + lineAxis);
						gc.draw(line);
					}
				} else {
					// 横書き進行
					y += this.ascent;
					double lineAxis = this.width;
					if (this.decoration.underlineColor != null) {
						// 下線
						gc.setStrokePaint(this.decoration.underlineColor);
						double descent = this.params.getFontListMetrics().getMaxDescent();
						double lineY = y + descent;
						// 行の下端から線の太さだけ上がった位置で押さえる
						lineY = Math.min(y + this.descent - strokeSize, lineY);
						Line2D line = new Line2D.Double(x, lineY, x + lineAxis, lineY);
						gc.draw(line);
					}
					if (this.decoration.overlineColor != null) {
						// 上線
						gc.setStrokePaint(this.decoration.overlineColor);
						double ascent = this.params.getFontListMetrics().getMaxAscent();
						double lineY = y - ascent;
						// 行の上端から線の太さだけ下がった位置で押さえる
						lineY = Math.max(y - this.ascent + strokeSize, lineY);
						Line2D line = new Line2D.Double(x, lineY, x + lineAxis, lineY);
						gc.draw(line);
					}
					if (this.decoration.lineThroughColor != null) {
						// 打ち消し線
						gc.setStrokePaint(this.decoration.lineThroughColor);
						double xHeight = this.params.getFontListMetrics().getMaxXHeight();
						double lineY = y - xHeight / 2.0;
						Line2D line = new Line2D.Double(x, lineY, x + lineAxis, lineY);
						gc.draw(line);
					}
				}

			}
		}
	}

	private final Drawable createTextSequenceDrawable(PageBox pageBox, Shape clip, AffineTransform transform, int off,
			int len) {
		AbstractTextParams params = this.getTextParams();
		return new TextSequenceDrawable(pageBox, clip, transform, this.contents, off, len, params, this.ascent,
				this.descent);
	}

	public final void getText(final StringBuilder textBuff) {
		if (this.contents == null) {
			return;
		}
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> textBuff.append(text.getChars(), 0, text.getCharCount());
			case Inline inline -> inline.box.getText(textBuff);
			case IAbsoluteBox absoluteBox -> absoluteBox.getText(textBuff);
			case Control control ->
				// 空白
				textBuff.append(control.getControlChar());
			default -> throw new IllegalStateException();
			}
		}
	}

	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.contents == null || this.contents.isEmpty()) {
			return;
		}
		int off = 0;
		int len = 0;
		double xx = x, yy = y;
		double tx = 0, ty = 0;

		boolean decoration = false;
		double dx = 0, dy = 0;
		final AbstractTextParams lineParams = this.getTextParams();
		final boolean vertical = lineParams.flow.isVertical();
		// テキストとインラインの描画
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> {
				// テキスト
				if (len == 0) {
					off = i;
					tx = xx;
					ty = yy;
				}
				if (!decoration) {
					dx = xx;
					dy = yy;
					decoration = true;
				}
				++len;
				if (vertical) {
					// 縦書き
					yy += text.getAdvance();
				} else {
					// 横書き
					xx += text.getAdvance();
				}
			}

			case Inline inline -> {
				// インライン
				if (lineParams.opacity != 0 && len > 0) {
					drawer.visitDrawable(this.createTextSequenceDrawable(pageBox, clip, transform, off, len), tx, ty);
					len = 0;
				}
				if (decoration) {
					// 装飾
					if (this.decoration != null) {
						final double width = xx - dx;
						final double height = yy - dy;
						if ((vertical && height > 0) || (!vertical && width > 0)) {
							Drawable drawable = new TextDecorationDrawable(pageBox, clip, transform, lineParams,
									this.decoration, this.ascent, this.descent, width, height);
							drawer.visitDrawable(drawable, dx, dy);
						}
					}
					decoration = false;
				}
				final IInlineBox inlineBox = inline.box;
				double ascent;
				switch (inlineBox.getType()) {
				case INLINE: {
					// 普通のインライン
					final InlineBox box = (InlineBox) inlineBox;
					ascent = box.getAscent();
				}
					break;
				case BLOCK: {
					// インラインブロック
					double descent;
					AbstractContainerBox box = (AbstractContainerBox) inlineBox;
					BlockParams params = box.getBlockParams();
					if (vertical) {
						// 縦書き
						if (params.flow == WritingMode.RL || params.flow == WritingMode.LR) {
							descent = box.getLastDescent();
							if (LayoutUtils.isNone(descent)) {
								descent = inlineBox.getWidth() / 2.0;
							}
						} else {
							// 縦中横
							descent = inlineBox.getWidth() / 2.0;
						}
						ascent = inlineBox.getWidth() - descent;
					} else {
						// 横書き
						if (params.flow == WritingMode.TB) {
							descent = box.getLastDescent();
							if (LayoutUtils.isNone(descent)) {
								descent = 0;
							}
						} else {
							// 横中縦
							descent = 0;
						}
						ascent = inlineBox.getHeight() - descent;
					}
				}
					break;
				case REPLACED: {
					// 画像
					if (vertical) {
						// 縦書き
						ascent = inlineBox.getWidth() / 2.0;
					} else {
						// 横書き
						ascent = inlineBox.getHeight();
					}
				}
					break;
				default:
					throw new IllegalStateException();
				}

				// ベースラインに合わせる
				// インラインのアセントはベースラインから内変への長さなので
				// 境界とマージンを考慮する
				double voffset = (ascent - this.ascent);
				// System.err.println(ascent + "/" + this.ascent + "/"
				// + inline.verticalAlign);
				if (vertical) {
					// 縦書き(日本)
					voffset += (this.getWidth() - inlineBox.getWidth());
					inlineBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY,
							xx + voffset + inline.verticalAlign, yy);
					yy += inlineBox.getHeight();
				} else {
					// 横書き
					inlineBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx,
							yy - voffset - inline.verticalAlign);
					xx += inlineBox.getWidth();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// 絶対配置
				if (lineParams.opacity != 0 && len > 0) {
					drawer.visitDrawable(this.createTextSequenceDrawable(pageBox, clip, transform, off, len), tx, ty);
					len = 0;
				}
				double xxx, yyy;
				final AbsolutePos pos = absoluteBox.getAbsolutePos();
				if (pos.location.getLeftType() != LengthType.AUTO || pos.location.getRightType() != LengthType.AUTO) {
					xxx = contextX;
				} else {
					xxx = xx;
				}
				if (pos.location.getTopType() != LengthType.AUTO || pos.location.getBottomType() != LengthType.AUTO) {
					yyy = contextY;
				} else {
					yyy = yy;
				}
				absoluteBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xxx, yyy);
			}

			case Control control -> {
				// 空白
				if (lineParams.opacity != 0 && len > 0) {
					drawer.visitDrawable(this.createTextSequenceDrawable(pageBox, clip, transform, off, len), tx, ty);
					len = 0;
				}
				if (!decoration) {
					dx = xx;
					dy = yy;
					decoration = true;
				}
				if (vertical) {
					// 縦書き
					yy += control.getAdvance();
				} else {
					// 横書き
					xx += control.getAdvance();
				}
			}

			default -> throw new IllegalStateException();
			}
		}
		if (lineParams.opacity != 0 && len > 0) {
			drawer.visitDrawable(this.createTextSequenceDrawable(pageBox, clip, transform, off, len), tx, ty);
			len = 0;
		}
		if (decoration && this.decoration != null) {
			final double width = xx - dx;
			final double height = yy - dy;
			if ((vertical && height > 0) || (!vertical && width > 0)) {
				Drawable drawable = new TextDecorationDrawable(pageBox, clip, transform, lineParams, this.decoration,
						this.ascent, this.descent, width, height);
				drawer.visitDrawable(drawable, dx, dy);
			}
		}
	}

	private void missingFontOutline(final PageBox pageBox, final Text text) {
		final String c = new String(text.getChars(), 0, text.getCharCount());
		final StringBuilder codes = new StringBuilder();
		for (int j = 0; j < c.length(); ++j) {
			codes.append("[").append(Integer.toHexString(c.charAt(j))).append("]");
		}
		pageBox.getUserAgent().message(MessageCodes.WARN_MISSING_FONT_OUTLINE, c + codes);
	}

	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y) {
		if (this.contents == null || this.contents.isEmpty()) {
			return;
		}
		double xx = x, yy = y;

		final AbstractTextParams lineParams = this.getTextParams();
		final boolean vertical = lineParams.flow.isVertical();
		// テキストとインラインの描画
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> {
				// テキスト
				Font font = ((FontMetricsImpl) text.getFontMetrics()).getFont();
				if (vertical) {
					// 縦書き
					if (font instanceof ShapedFont) {
						AffineTransform at = AffineTransform.getTranslateInstance(xx + this.descent, yy);
						at.preConcatenate(transform);
						FontUtils.addTextPath(path, (ShapedFont)font, text, at);
					}
					else {
						this.missingFontOutline(pageBox, text);
					}
					yy += text.getAdvance();
				} else {
					// 横書き
					if (font instanceof ShapedFont) {
						AffineTransform at = AffineTransform.getTranslateInstance(xx, yy + this.ascent);
						at.preConcatenate(transform);
						FontUtils.addTextPath(path, (ShapedFont)font, text, at);
					}
					else {
						this.missingFontOutline(pageBox, text);
					}
					xx += text.getAdvance();
				}
			}

			case Inline inline -> {
				// インライン
				final IInlineBox inlineBox = inline.box;
				double ascent;
				switch (inlineBox.getType()) {
				case INLINE: {
					// 普通のインライン
					final InlineBox box = (InlineBox) inlineBox;
					ascent = box.getAscent();
				}
					break;
				case BLOCK: {
					// インラインブロック
					double descent;
					AbstractContainerBox box = (AbstractContainerBox) inlineBox;
					BlockParams params = box.getBlockParams();
					if (vertical) {
						// 縦書き
						if (params.flow == WritingMode.RL || params.flow == WritingMode.LR) {
							descent = box.getLastDescent();
							if (LayoutUtils.isNone(descent)) {
								descent = inlineBox.getWidth() / 2.0;
							}
						} else {
							// 縦中横
							descent = inlineBox.getWidth() / 2.0;
						}
						ascent = inlineBox.getWidth() - descent;
					} else {
						// 横書き
						if (params.flow == WritingMode.TB) {
							descent = box.getLastDescent();
							if (LayoutUtils.isNone(descent)) {
								descent = 0;
							}
						} else {
							// 横中縦
							descent = 0;
						}
						ascent = inlineBox.getHeight() - descent;
					}
				}
					break;
				case REPLACED: {
					// 画像
					if (vertical) {
						// 縦書き
						ascent = inlineBox.getWidth() / 2.0;
					} else {
						// 横書き
						ascent = inlineBox.getHeight();
					}
				}
					break;
				default:
					throw new IllegalStateException();
				}

				// ベースラインに合わせる
				// インラインのアセントはベースラインから内変への長さなので
				// 境界とマージンを考慮する
				double voffset = (ascent - this.ascent);
				// System.err.println(ascent + "/" + this.ascent + "/"
				// + inline.verticalAlign);
				if (vertical) {
					// 縦書き(日本)
					voffset += (this.getWidth() - inlineBox.getWidth());
					inlineBox.textShape(pageBox, path, transform, xx + voffset + inline.verticalAlign, yy);
					yy += inlineBox.getHeight();
				} else {
					// 横書き
					inlineBox.textShape(pageBox, path, transform, xx, yy - voffset - inline.verticalAlign);
					xx += inlineBox.getWidth();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// 絶対配置
				// ignore
			}

			case Control control -> {
				// 空白
				if (vertical) {
					// 縦書き
					yy += control.getAdvance();
				} else {
					// 横書き
					xx += control.getAdvance();
				}
			}

			default -> throw new IllegalStateException();
			}
		}
	}

	public final double getAscent() {
		return this.ascent;
	}

	public final double getDescent() {
		return this.descent;
	}

	public void restyle(final GlyphHandler gh, final boolean widow) {
		if (this.contents == null) {
			return;
		}
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> {
				// テキスト
				assert text.getGlyphCount() > 0;
				text.toGlyphs(gh);
			}

			case Inline content -> {
				// インライン
				switch (content.box.getType()) {
				case INLINE: {
					final InlineBox inlineBox = (InlineBox) content.box;
					inlineBox.restyle(gh, widow && i == 0);
				}
					break;

				case REPLACED: {
					final AbstractReplacedBox replacedBox = (AbstractReplacedBox) content.box;
					final InlineQuad quad = InlineQuad.createReplacedBoxQuad(replacedBox);
					gh.control(quad);
				}
					break;

				case BLOCK: {
					final InlineBlockBox inlineBox = (InlineBlockBox) content.box;
					final InlineQuad quad = InlineQuad.createInlineBlockBoxQuad(inlineBox);
					gh.control(quad);
				}
					break;

				default:
					throw new IllegalStateException();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				final InlineQuad quad = InlineQuad.createInlineAbsoluteBoxQuad(absoluteBox);
				gh.control(quad);
			}

			case Control control -> gh.control(control);

			default -> throw new IllegalStateException();
			}
		}
	}

	public final Object getContent(int ix) {
		return this.contents.get(ix);
	}

	public final int getContentCount() {
		if (this.contents == null) {
			return 0;
		}
		return this.contents.size();
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		if (this.contents != null) {
			for (int i = 0; i < this.contents.size(); ++i) {
				buff.append(this.contents.get(i));
			}
		}
		return buff.toString();
	}
}
