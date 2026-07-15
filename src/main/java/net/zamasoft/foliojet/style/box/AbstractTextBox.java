package net.zamasoft.foliojet.style.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.style.box.content.JustificationState;
import net.zamasoft.foliojet.style.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.style.box.impl.InlineBox;
import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.AbsolutePos;
import net.zamasoft.foliojet.style.box.params.AbstractLineParams;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Decoration;
import net.zamasoft.foliojet.style.box.params.InlinePos;
import net.zamasoft.foliojet.style.box.params.Insets;
import net.zamasoft.foliojet.style.box.params.TextShadow;
import net.zamasoft.foliojet.style.builder.InlineQuad;
import net.zamasoft.foliojet.style.draw.AbstractDrawable;
import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.ByteList;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;
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
import net.zamasoft.foliojet.pdfg2d.text.hyphenation.Hyphenation;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
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

	public static final byte TYPE_TEXT = 1;
	public static final byte TYPE_CONTROL = 2;
	public static final byte TYPE_INLINE = 3;
	public static final byte TYPE_ABSOLUTE = 4;

	protected Decoration decoration;

	/**
	 * 内部に含まれるテキストとインラインボックスです。
	 */
	protected List<Object> contents = null;

	/**
	 * コンテンツのタイプのリストです。
	 */
	protected ByteList types = null;

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

	protected final void add(Object content, byte type) {
		assert type != TYPE_TEXT || content instanceof Text;
		assert type != TYPE_CONTROL || content instanceof Control;
		assert type != TYPE_INLINE || content instanceof Inline;
		assert type != TYPE_ABSOLUTE || content instanceof IAbsoluteBox;
		if (this.types == null) {
			this.contents = new ArrayList<Object>();
			this.types = new ByteList();
		}
		this.contents.add(content);
		this.types.add(type);
	}

	public final double getLineSize() {
		return this.lineSize;
	}

	public final double getPageSize() {
		return this.ascent + this.descent;
	}

	public final double getWidth() {
		if (StyleUtils.isVertical(this.getTextParams().flow)) {
			// 縦書き
			return this.getPageSize();
		} else {
			// 横書き
			return this.lineSize;
		}
	}

	public final double getHeight() {
		if (StyleUtils.isVertical(this.getTextParams().flow)) {
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
		this.add(text, TYPE_TEXT);
	}

	public final void addControl(Control control) {
		// System.out.println(control);
		this.add(control, TYPE_CONTROL);
	}

	/**
	 * インラインを追加します。
	 * 
	 * @param box
	 */
	public final void addInline(IInlineBox box) {
		if (box.getType() == IBox.TYPE_INLINE) {
			assert this.getParams().element != box.getParams().element
					: (box.getParams().element + "\n" + this.getParams() + "\n" + box.getParams());
			InlineBox inline = (InlineBox) box;
			inline.setDecoration(this.decoration);
		}
		this.add(new Inline(box), TYPE_INLINE);
	}

	public final void addAbsolute(IAbsoluteBox box) {
		this.add(box, TYPE_ABSOLUTE);
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
		if (this.types == null) {
			return 0;
		}
		Hyphenation hyph = this.getTextParams().hyphenation;
		int count = 0;
		for (int i = 0; i < this.types.size(); ++i) {
			switch (this.types.get(i)) {
			case TYPE_TEXT:
				// テキスト
				Text text = (Text) this.contents.get(i);
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
				break;

			case TYPE_INLINE:
				// インライン
				Inline content = (Inline) this.contents.get(i);
				if (content.box.getType() == IBox.TYPE_INLINE) {
					InlineBox inline = (InlineBox) content.box;
					count += inline.countJustificationPoints(state);
				}
				break;

			case TYPE_CONTROL:
				if (i > 0) {
					Control ctrl = (Control) this.contents.get(i);
					state.prevChar = ctrl.getControlChar();
				}
			case TYPE_ABSOLUTE:
				break;

			default:
				throw new IllegalStateException();
			}
		}
		return count;
	}

	protected final void justify(double unitSpacing, JustificationState state) {
		if (this.types == null) {
			return;
		}
		Hyphenation hyph = this.getTextParams().hyphenation;
		for (int i = 0; i < this.types.size(); ++i) {
			double da = 0;
			switch (this.types.get(i)) {
			case TYPE_TEXT: {
				// テキスト
				Text text = (Text) this.contents.get(i);
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
				break;

			case TYPE_INLINE: {
				// インライン
				Inline inline = (Inline) this.contents.get(i);
				if (inline.box.getType() == IBox.TYPE_INLINE) {
					InlineBox inlineBox = (InlineBox) inline.box;
					da = inlineBox.getLineSize();
					inlineBox.justify(unitSpacing, state);
					da = inlineBox.getLineSize() - da;
				}
			}
				break;

			case TYPE_CONTROL:
				if (i > 0) {
					Control ctrl = (Control) this.contents.get(i);
					state.prevChar = ctrl.getControlChar();
				}
			case TYPE_ABSOLUTE:
				break;

			default:
				throw new IllegalStateException();
			}
			if (da != 0) {
				this.addAdvance(da);
			}
		}
	}

	public abstract boolean isContextBox();

	public void finishLayout(IFramedBox containerBox) {
		if (this.types == null) {
			return;
		}
		if (this.isContextBox()) {
			containerBox = (IFramedBox) this;
		}
		for (int i = 0; i < this.types.size(); ++i) {
			switch (this.types.get(i)) {
			case TYPE_TEXT:
			case TYPE_CONTROL:
				// テキスト
				break;
			case TYPE_ABSOLUTE: {
				// 絶対配置
				final IAbsoluteBox absoluteBox = (IAbsoluteBox) this.contents.get(i);
				absoluteBox.finishLayout(containerBox);
			}
				break;

			case TYPE_INLINE: {
				// インライン
				final Inline inline = (Inline) this.contents.get(i);
				final IInlineBox inlineBox = inline.box;
				inlineBox.finishLayout(containerBox);
			}
				break;

			default:
				throw new IllegalStateException();
			}
		}
	}

	protected void verticalAlign(AbstractLineBox lineBox, double baseline) {
		if (this.types == null) {
			return;
		}
		final AbstractLineParams lineParams = lineBox.getLineParams();
		for (int i = 0; i < this.types.size(); ++i) {
			switch (this.types.get(i)) {
			case TYPE_TEXT:
			case TYPE_CONTROL:
			case TYPE_ABSOLUTE:
				// テキスト
				break;

			case TYPE_INLINE: {
				// インライン
				final Inline inline = (Inline) this.contents.get(i);
				final IInlineBox inlineBox = inline.box;
				final InlinePos pos = inlineBox.getInlinePos();
				double ascent;
				double descent;
				switch (inlineBox.getType()) {
				case IBox.TYPE_INLINE: {
					// 普通のインライン
					final InlineBox box = (InlineBox) inlineBox;
					ascent = box.getAscent();
					descent = box.getDescent();
				}
					break;
				case IBox.TYPE_BLOCK: {
					// インラインブロック
					final AbstractContainerBox box = (AbstractContainerBox) inlineBox;
					final BlockParams params = box.getBlockParams();
					if (StyleUtils.isVertical(lineParams.flow)) {
						// 縦書き
						if (StyleUtils.isVertical(params.flow)) {
							descent = box.getLastDescent();
							if (StyleUtils.isNone(descent)) {
								descent = inlineBox.getWidth() / 2.0;
							}
						} else {
							// 縦中横
							descent = inlineBox.getWidth() / 2.0;
						}
						ascent = inlineBox.getWidth() - descent;
					} else {
						// 横書き
						if (StyleUtils.isVertical(params.flow)) {
							// 横中縦
							descent = 0;
						} else {
							descent = box.getLastDescent();
							if (StyleUtils.isNone(descent)) {
								descent = 0;
							}
						}
						ascent = inlineBox.getHeight() - descent;
					}
				}
					break;
				case IBox.TYPE_REPLACED: {
					// 画像
					if (StyleUtils.isVertical(lineParams.flow)) {
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
				if (inlineBox.getType() == IBox.TYPE_INLINE) {
					((InlineBox) inlineBox).verticalAlign(lineBox, baseline + inline.verticalAlign);
				}
			}
				break;

			default:
				throw new IllegalStateException();
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
			if (StyleUtils.isVertical(this.params.flow)) {
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
				if (StyleUtils.isVertical(this.params.flow)) {
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
		if (this.types != null && !this.types.isEmpty()) {
			for (int i = 0; i < this.types.size(); ++i) {
				switch (this.types.get(i)) {
				case TYPE_TEXT: {
					Text text = (Text) this.contents.get(i);
					textBuff.append(text.getChars(), 0, text.getCharCount());
				}
					break;

				case TYPE_INLINE: {
					Inline inline = (Inline) this.contents.get(i);
					inline.box.getText(textBuff);
				}
					break;

				case TYPE_ABSOLUTE: {
					IAbsoluteBox absoluteBox = (IAbsoluteBox) this.contents.get(i);
					absoluteBox.getText(textBuff);
				}
					break;

				case TYPE_CONTROL: {
					// 空白
					Control control = (Control) this.contents.get(i);
					textBuff.append(control.getControlChar());
				}
					break;

				default:
					throw new IllegalStateException();
				}
			}
		}
	}

	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.types == null || this.types.isEmpty()) {
			return;
		}
		int off = 0;
		int len = 0;
		double xx = x, yy = y;
		double tx = 0, ty = 0;

		boolean decoration = false;
		double dx = 0, dy = 0;
		final AbstractTextParams lineParams = this.getTextParams();
		final boolean vertical = StyleUtils.isVertical(lineParams.flow);
		// テキストとインラインの描画
		for (int i = 0; i < this.types.size(); ++i) {
			switch (this.types.get(i)) {
			case TYPE_TEXT: {
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
				Text text = (Text) this.contents.get(i);
				++len;
				if (vertical) {
					// 縦書き
					yy += text.getAdvance();
				} else {
					// 横書き
					xx += text.getAdvance();
				}
			}
				break;

			case TYPE_INLINE: {
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
				final Inline inline = (Inline) this.contents.get(i);
				final IInlineBox inlineBox = inline.box;
				double ascent;
				switch (inlineBox.getType()) {
				case IBox.TYPE_INLINE: {
					// 普通のインライン
					final InlineBox box = (InlineBox) inlineBox;
					ascent = box.getAscent();
				}
					break;
				case IBox.TYPE_BLOCK: {
					// インラインブロック
					double descent;
					AbstractContainerBox box = (AbstractContainerBox) inlineBox;
					BlockParams params = box.getBlockParams();
					if (vertical) {
						// 縦書き
						if (params.flow == AbstractTextParams.FLOW_RL || params.flow == AbstractTextParams.FLOW_LR) {
							descent = box.getLastDescent();
							if (StyleUtils.isNone(descent)) {
								descent = inlineBox.getWidth() / 2.0;
							}
						} else {
							// 縦中横
							descent = inlineBox.getWidth() / 2.0;
						}
						ascent = inlineBox.getWidth() - descent;
					} else {
						// 横書き
						if (params.flow == AbstractTextParams.FLOW_TB) {
							descent = box.getLastDescent();
							if (StyleUtils.isNone(descent)) {
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
				case IBox.TYPE_REPLACED: {
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
				break;

			case TYPE_ABSOLUTE: {
				// 絶対配置
				if (lineParams.opacity != 0 && len > 0) {
					drawer.visitDrawable(this.createTextSequenceDrawable(pageBox, clip, transform, off, len), tx, ty);
					len = 0;
				}
				double xxx, yyy;
				final IAbsoluteBox absoluteBox = (IAbsoluteBox) this.contents.get(i);
				final AbsolutePos pos = absoluteBox.getAbsolutePos();
				if (pos.location.getLeftType() != Insets.TYPE_AUTO || pos.location.getRightType() != Insets.TYPE_AUTO) {
					xxx = contextX;
				} else {
					xxx = xx;
				}
				if (pos.location.getTopType() != Insets.TYPE_AUTO || pos.location.getBottomType() != Insets.TYPE_AUTO) {
					yyy = contextY;
				} else {
					yyy = yy;
				}
				absoluteBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xxx, yyy);
			}
				break;

			case TYPE_CONTROL: {
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
				Control control = (Control) this.contents.get(i);
				if (vertical) {
					// 縦書き
					yy += control.getAdvance();
				} else {
					// 横書き
					xx += control.getAdvance();
				}
			}
				break;

			default:
				throw new IllegalStateException();
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
		if (this.types == null || this.types.isEmpty()) {
			return;
		}
		double xx = x, yy = y;

		final AbstractTextParams lineParams = this.getTextParams();
		final boolean vertical = StyleUtils.isVertical(lineParams.flow);
		// テキストとインラインの描画
		for (int i = 0; i < this.types.size(); ++i) {
			switch (this.types.get(i)) {
			case TYPE_TEXT: {
				// テキスト
				Text text = (Text) this.contents.get(i);
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
				break;

			case TYPE_INLINE: {
				// インライン
				final Inline inline = (Inline) this.contents.get(i);
				final IInlineBox inlineBox = inline.box;
				double ascent;
				switch (inlineBox.getType()) {
				case IBox.TYPE_INLINE: {
					// 普通のインライン
					final InlineBox box = (InlineBox) inlineBox;
					ascent = box.getAscent();
				}
					break;
				case IBox.TYPE_BLOCK: {
					// インラインブロック
					double descent;
					AbstractContainerBox box = (AbstractContainerBox) inlineBox;
					BlockParams params = box.getBlockParams();
					if (vertical) {
						// 縦書き
						if (params.flow == AbstractTextParams.FLOW_RL || params.flow == AbstractTextParams.FLOW_LR) {
							descent = box.getLastDescent();
							if (StyleUtils.isNone(descent)) {
								descent = inlineBox.getWidth() / 2.0;
							}
						} else {
							// 縦中横
							descent = inlineBox.getWidth() / 2.0;
						}
						ascent = inlineBox.getWidth() - descent;
					} else {
						// 横書き
						if (params.flow == AbstractTextParams.FLOW_TB) {
							descent = box.getLastDescent();
							if (StyleUtils.isNone(descent)) {
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
				case IBox.TYPE_REPLACED: {
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
				break;

			case TYPE_ABSOLUTE: {
				// 絶対配置
				// ignore
			}
				break;

			case TYPE_CONTROL: {
				// 空白
				Control control = (Control) this.contents.get(i);
				if (vertical) {
					// 縦書き
					yy += control.getAdvance();
				} else {
					// 横書き
					xx += control.getAdvance();
				}
			}
				break;

			default:
				throw new IllegalStateException();
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
		if (this.types == null) {
			return;
		}
		for (int i = 0; i < this.types.size(); ++i) {
			// System.err.println(this.contents.get(i));
			switch (this.types.get(i)) {
			case TYPE_TEXT: {
				// テキスト
				final Text text = (Text) this.contents.get(i);
				assert text.getGlyphCount() > 0;
				text.toGlyphs(gh);
			}
				break;

			case TYPE_INLINE: {
				// インライン
				final Inline content = (Inline) this.contents.get(i);
				switch (content.box.getType()) {
				case IBox.TYPE_INLINE: {
					final InlineBox inlineBox = (InlineBox) content.box;
					inlineBox.restyle(gh, widow && i == 0);
				}
					break;

				case IBox.TYPE_REPLACED: {
					final AbstractReplacedBox replacedBox = (AbstractReplacedBox) content.box;
					final InlineQuad quad = InlineQuad.createReplacedBoxQuad(replacedBox);
					gh.control(quad);
				}
					break;

				case IBox.TYPE_BLOCK: {
					final InlineBlockBox inlineBox = (InlineBlockBox) content.box;
					final InlineQuad quad = InlineQuad.createInlineBlockBoxQuad(inlineBox);
					gh.control(quad);
				}
					break;

				default:
					throw new IllegalStateException();
				}
			}
				break;

			case TYPE_ABSOLUTE: {
				final IAbsoluteBox absoluteBox = (IAbsoluteBox) this.contents.get(i);
				final InlineQuad quad = InlineQuad.createInlineAbsoluteBoxQuad(absoluteBox);
				gh.control(quad);
			}
				break;

			case TYPE_CONTROL: {
				final Control control = (Control) this.contents.get(i);
				gh.control(control);
			}
				break;

			default:
				throw new IllegalStateException();
			}
		}
	}

	public final Object getContent(int ix) {
		return this.contents.get(ix);
	}

	public final byte getContentType(int ix) {
		return this.types.get(ix);
	}

	public final int getContentCount() {
		if (this.types == null) {
			return 0;
		}
		return this.types.size();
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
