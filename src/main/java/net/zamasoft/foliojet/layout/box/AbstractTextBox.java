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
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;
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

	/**
	 * 直接の子インラインボックスを列挙します(読み取り専用。脚注F4の
	 * call走査用に公開——consult-codex-2026-07-31-footnote-f4.txt)。
	 * 入れ子のインラインへは降りない(呼び出し側が反復DFSで降りる)。
	 *
	 * @param action 各子インラインに適用する処理
	 */
	public final void forEachInlineBox(final java.util.function.Consumer<IInlineBox> action) {
		if (this.contents == null) {
			return;
		}
		for (int i = 0; i < this.contents.size(); ++i) {
			if (this.contents.get(i) instanceof Inline inline) {
				action.accept(inline.box);
			}
		}
	}

	/**
	 * この箱が「後続ブロックへ重ねる外置きマーカー」だけを含むかを返す。
	 * list-itemの先頭子が表である場合、マーカーを表セルへ混入させず、かつ
	 * マーカー専用行で表を1行分送らないための構造判定に使う。
	 */
	public final boolean containsOnlyOverlayOutsideMarker() {
		if (this.contents == null || this.contents.size() != 1) {
			return false;
		}
		return this.contents.get(0) instanceof Inline inline
				&& inline.box instanceof net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox marker
				&& marker.overlaysFollowingBlock();
	}

	protected double ascent = 0;

	protected double descent = 0;

	protected double lineSize;

	public abstract AbstractTextParams getTextParams();

	protected final void setDecoration(final Decoration decoration) {
		final AbstractTextParams params = this.getTextParams();
		final byte flags = (byte) (params.decoration & 7);
		Decoration.Line underline;
		Decoration.Line overline;
		Decoration.Line lineThrough;
		if (decoration == null) {
			if (flags == 0) {
				return;
			}
			underline = overline = lineThrough = null;
		} else {
			underline = decoration.underline;
			overline = decoration.overline;
			lineThrough = decoration.lineThrough;
		}
		// text-decoration-color(2026-08-29): 指定があれば装飾線はその色、
		// 無ければ従来どおり文字色。線種・太さ・下線位置もこの要素(線の
		// 所有者)のparamsから取り、子孫へはそのまま伝播する
		final Color color = params.decorationColor != null ? params.decorationColor : params.color;
		final Decoration.Line own = color == null ? null : Decoration.Line.of(color, params);
		underline = ((flags & AbstractTextParams.DECORATION_UNDERLINE) != 0) ? own : underline;
		overline = ((flags & AbstractTextParams.DECORATION_OVERLINE) != 0) ? own : overline;
		lineThrough = ((flags & AbstractTextParams.DECORATION_LINE_THROUGH) != 0) ? own : lineThrough;
		this.decoration = new Decoration(underline, overline, lineThrough);
	}

	protected final void add(Object content) {
		assert content instanceof Text || content instanceof Control || content instanceof Inline
				|| content instanceof IAbsoluteBox
				|| content instanceof net.zamasoft.foliojet.layout.text.LeaderQuad;
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
				if (content instanceof Inline inline) {
					// ルビ単位は合成箱で、文字は箱の中に整形済みで入って
					// いる(グリフとしては行に現れない)。単位の途中を
					// 再開位置にすると、部分再生がruby開始イベントを
					// 含まない位置から始まって二重供給になるため、
					// 単位全体のソース終端を返す(2026-07-25)
					if (inline.box instanceof net.zamasoft.foliojet.layout.box.impl.RubyUnitBox rubyUnit) {
						final int end = rubyUnit.getSourceEnd();
						if (end >= 0) {
							return end;
						}
						continue;
					}
					if (inline.box instanceof net.zamasoft.foliojet.layout.box.impl.WarichuUnitBox warichuUnit) {
						final int end = warichuUnit.getSourceEnd();
						if (end >= 0) {
							return end;
						}
						continue;
					}
					if (inline.box instanceof AbstractTextBox nested) {
						final int end = nested.lastCharEnd();
						if (end >= 0) {
							return end;
						}
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
				if (content instanceof Inline inline) {
					if (inline.box instanceof net.zamasoft.foliojet.layout.box.impl.RubyUnitBox rubyUnit) {
						final int offset = rubyUnit.getSourceStart();
						if (offset >= 0) {
							return offset;
						}
						continue;
					}
					if (inline.box instanceof net.zamasoft.foliojet.layout.box.impl.WarichuUnitBox warichuUnit) {
						final int offset = warichuUnit.getSourceStart();
						if (offset >= 0) {
							return offset;
						}
						continue;
					}
					if (inline.box instanceof AbstractTextBox nested) {
						final int offset = nested.firstCharOffset();
						if (offset >= 0) {
							return offset;
						}
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

	/** {@code leader()}を追加します(leader() L1、幅は割り付け済み)。 */
	public final void addLeader(final net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
		this.add(leader);
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
	/** JLREQ 3.8.4の追出し優先段階。 */
	protected static final int JUSTIFY_WORD_SPACE = 1;
	protected static final int JUSTIFY_AUTOSPACE = 2;
	protected static final int JUSTIFY_GENERAL = 3;
	protected static final int JUSTIFY_FALLBACK = 4;

	/**
	 * この行／インラインが和文組版を含むかを返す。JLREQの段階的な行長調整は
	 * 和文行にだけ適用し、純欧文のjustifyは従来どおり欧文の分離可能境界へ配分する。
	 */
	protected final boolean containsJapaneseComposition() {
		if (this.contents == null) {
			return false;
		}
		for (final Object content : this.contents) {
			switch (content) {
			case Text text -> {
				final char[] chars = text.getChars();
				for (int i = 0; i < text.getCharCount();) {
					final int cp = Character.codePointAt(chars, i);
					if (isJapaneseCompositionCodePoint(cp)) {
						return true;
					}
					i += Character.charCount(cp);
				}
			}
			case Inline inline -> {
				if (inline.box instanceof AbstractTextBox nested && nested.containsJapaneseComposition()
						|| inline.box instanceof net.zamasoft.foliojet.layout.box.impl.RubyUnitBox
						|| inline.box instanceof net.zamasoft.foliojet.layout.box.impl.WarichuUnitBox) {
					return true;
				}
			}
			case Control ctrl -> {
				if (isJapaneseCompositionCodePoint(ctrl.getControlChar())) {
					return true;
				}
			}
			default -> {
				// 配置物・leaderは文字組版の判定に影響しない。
			}
			}
		}
		return false;
	}

	private static boolean isJapaneseCompositionCodePoint(final int cp) {
		if (net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.of(cp)
				== net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH) {
			return true;
		}
		// CJK約物・縦書き互換形・全角形も和文組版の一部として扱う。
		return cp >= 0x3000 && cp <= 0x303F || cp >= 0xFE10 && cp <= 0xFE1F
				|| cp >= 0xFE30 && cp <= 0xFE4F || cp >= 0xFF01 && cp <= 0xFF60
				|| cp >= 0xFFE0 && cp <= 0xFFE6;
	}

	/** 純欧文・一般文字列における従来のjustify候補数。 */
	protected final int countGeneralJustificationPoints(final JustificationState state) {
		if (this.contents == null) {
			return 0;
		}
		final TextBreakingRules rules = this.getTextParams().lineBreakRules;
		int count = 0;
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> {
				final int glyphCount = text.getGlyphCount();
				final char[] chars = text.getChars();
				final byte[] clusterLengths = text.getClusterLengths();
				int offset = 0;
				for (int j = 0; j < glyphCount; ++j) {
					final int first = Character.codePointAt(chars, offset);
					offset += clusterLengths[j];
					final int last = Character.codePointBefore(chars, offset);
					if (isGeneralJustificationBoundary(state.prevCodePoint, first, rules)) {
						++count;
					}
					state.prevCodePoint = last;
				}
			}
			case Inline inline -> {
				if (inline.box.getType() == BoxType.INLINE) {
					count += ((InlineBox) inline.box).countGeneralJustificationPoints(state);
				}
			}
			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					state.prevCodePoint = ctrl.getControlChar();
				}
			}
			default -> {
				// 配置物・leaderは伸長点を作らない。
			}
			}
		}
		return count;
	}

	/** 純欧文・一般文字列の各justify候補へ同じアキを加える。 */
	protected final void justifyGeneral(final double unitSpacing, final JustificationState state) {
		if (this.contents == null) {
			return;
		}
		final TextBreakingRules rules = this.getTextParams().lineBreakRules;
		for (int i = 0; i < this.contents.size(); ++i) {
			double advance = 0;
			switch (this.contents.get(i)) {
			case Text text -> {
				final int glyphCount = text.getGlyphCount();
				final char[] chars = text.getChars();
				final byte[] clusterLengths = text.getClusterLengths();
				final net.zamasoft.pdfg2d.gc.text.TextImpl textImpl =
						(net.zamasoft.pdfg2d.gc.text.TextImpl) text;
				int offset = 0;
				for (int j = 0; j < glyphCount; ++j) {
					final int first = Character.codePointAt(chars, offset);
					offset += clusterLengths[j];
					final int last = Character.codePointBefore(chars, offset);
					if (isGeneralJustificationBoundary(state.prevCodePoint, first, rules)) {
						textImpl.addXAdvance(j, unitSpacing);
						advance += unitSpacing;
					}
					state.prevCodePoint = last;
				}
			}
			case Inline inline -> {
				if (inline.box.getType() == BoxType.INLINE) {
					final InlineBox inlineBox = (InlineBox) inline.box;
					advance = inlineBox.getLineSize();
					inlineBox.justifyGeneral(unitSpacing, state);
					advance = inlineBox.getLineSize() - advance;
				}
			}
			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					state.prevCodePoint = ctrl.getControlChar();
				}
			}
			default -> {
				// 配置物・leaderは伸長しない。
			}
			}
			if (advance != 0) {
				this.addAdvance(advance);
			}
		}
	}

	private static boolean isGeneralJustificationBoundary(final int previous, final int next,
			final TextBreakingRules rules) {
		return previous >= 0 && previous <= Character.MAX_VALUE && next <= Character.MAX_VALUE
				&& rules.canSeparate((char) previous, (char) next)
				&& !rules.atomic((char) previous, (char) next);
	}

	/**
	 * 指定した追出し段階で利用できる総調整量（pt）を返す。第4段階だけは
	 * 上限でなく、1emの均等配分に対する重みを返す。
	 */
	protected final double justificationCapacity(final int priority, JustificationState state) {
		if (this.contents == null) {
			return 0;
		}
		TextBreakingRules hyph = this.getTextParams().lineBreakRules;
		double capacity = 0;
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
					final int c1 = Character.codePointAt(ch, k);
					k += clens[j];
					final int c2 = Character.codePointBefore(ch, k);
					final double fontSize = text.getFontStyle().getSize();
					capacity += justificationWeight(state, c1, fontSize, hyph, priority);
					state.prevCodePoint = c2;
					state.prevFontSize = fontSize;
					state.wordSpaceAdvance = -1;
				}
			}

			case Inline content -> {
				// インライン
				if (content.box.getType() == BoxType.INLINE) {
					InlineBox inline = (InlineBox) content.box;
					capacity += inline.justificationCapacity(priority, state);
				}
			}

			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					// 幅0のソフトハイフンは語中の伸長点を作らない
					if (ctrl instanceof net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace) {
						state.beforeWordSpaceCodePoint = state.prevCodePoint;
						state.beforeWordSpaceFontSize = state.prevFontSize;
						state.wordSpaceAdvance = ctrl.getAdvance();
					}
					state.prevCodePoint = ctrl.getControlChar();
					state.prevFontSize = this.getTextParams().fontStyle.getSize();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// 位置に影響しない
			}

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader -> {
				// leaderは残余を先に消費するので伸長点を作らない
			}

			default -> throw new IllegalStateException();
			}
		}
		return capacity;
	}

	/** 指定段階の各点へ、段階上限（第4段階は1em）×ratioを加える。 */
	protected final void justify(final int priority, final double ratio, JustificationState state) {
		if (this.contents == null) {
			return;
		}
		TextBreakingRules hyph = this.getTextParams().lineBreakRules;
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
				// 和文詰めT1a: 既存の調整(約物詰め・autospace gap)を保全して
				// 均等割りを上乗せする(addXAdvanceは加算——リセットしない)
				final net.zamasoft.pdfg2d.gc.text.TextImpl textImpl = (net.zamasoft.pdfg2d.gc.text.TextImpl) text;
				int k = 0;
				for (int j = 0; j < glen; ++j) {
					final int c1 = Character.codePointAt(ch, k);
					k += clens[j];
					final int c2 = Character.codePointBefore(ch, k);
					final double fontSize = text.getFontStyle().getSize();
					final double weight = justificationWeight(state, c1, fontSize, hyph, priority);
					if (weight > 0) {
						final double spacing = weight * ratio;
						textImpl.addXAdvance(j, spacing);
						da += spacing;
					}
					state.prevCodePoint = c2;
					state.prevFontSize = fontSize;
					state.wordSpaceAdvance = -1;
				}
			}

			case Inline inline -> {
				// インライン
				if (inline.box.getType() == BoxType.INLINE) {
					InlineBox inlineBox = (InlineBox) inline.box;
					da = inlineBox.getLineSize();
					inlineBox.justify(priority, ratio, state);
					da = inlineBox.getLineSize() - da;
				}
			}

			case Control ctrl -> {
				if (i > 0 && ctrl.getControlChar() != SoftHyphen.CHAR) {
					// 幅0のソフトハイフンは語中の伸長点を作らない
					if (ctrl instanceof net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace) {
						state.beforeWordSpaceCodePoint = state.prevCodePoint;
						state.beforeWordSpaceFontSize = state.prevFontSize;
						state.wordSpaceAdvance = ctrl.getAdvance();
					}
					state.prevCodePoint = ctrl.getControlChar();
					state.prevFontSize = this.getTextParams().fontStyle.getSize();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// 位置に影響しない
			}

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader -> {
				// 割り付け済み——justifyの伸長対象外
			}

			default -> throw new IllegalStateException();
			}
			if (da != 0) {
				this.addAdvance(da);
			}
		}
	}

	/** 1つの境界が指定段階で持つ上限／重み（pt）。 */
	private static double justificationWeight(final JustificationState state, final int next,
			final double nextFontSize, final TextBreakingRules rules, final int priority) {
		final int prev = state.prevCodePoint;
		if (priority == JUSTIFY_WORD_SPACE) {
			if (state.wordSpaceAdvance < 0 || prev != ' '
					|| !isWestern(state.beforeWordSpaceCodePoint) || !isWestern(next)) {
				return 0;
			}
			final double size = Math.min(state.beforeWordSpaceFontSize > 0
					? state.beforeWordSpaceFontSize : nextFontSize, nextFontSize);
			// JLREQ 3.8.4: 欧文語間を通常値から最大二分まで広げる。
			return Math.max(0, size / 2.0 - state.wordSpaceAdvance);
		}
		if (prev < 0 || !net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver
				.allowsJustificationAfter(prev)) {
			return 0;
		}
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind pk =
				net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.of(prev);
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind nk =
				net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.of(next);
		final boolean japaneseLatin = pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
				&& (nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
						|| nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC)
				|| nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
						&& (pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
								|| pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC);
		final boolean westernInterletter = (pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
				|| pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC)
				&& (nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
						|| nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC);
		final boolean bmpPair = prev <= Character.MAX_VALUE && next <= Character.MAX_VALUE;
		final boolean atomic = bmpPair && rules.atomic((char) prev, (char) next);
		if (atomic && !(priority == JUSTIFY_FALLBACK && westernInterletter)) {
			return 0;
		}
		final boolean normal = bmpPair ? rules.canSeparate((char) prev, (char) next)
				: pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
						|| nk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH;
		final double size = japaneseLatin
				&& pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
						? (state.prevFontSize > 0 ? state.prevFontSize : nextFontSize)
						: japaneseLatin ? nextFontSize
								: Math.min(state.prevFontSize > 0 ? state.prevFontSize : nextFontSize,
										nextFontSize);
		return switch (priority) {
		case JUSTIFY_WORD_SPACE -> 0;
		case JUSTIFY_AUTOSPACE -> normal && prev != ' ' && japaneseLatin ? size / 4.0 : 0;
		case JUSTIFY_GENERAL -> normal && prev != ' ' && !japaneseLatin ? size / 4.0 : 0;
		case JUSTIFY_FALLBACK -> normal || westernInterletter ? size : 0;
		default -> throw new IllegalArgumentException("priority=" + priority);
		};
	}

	private static boolean isWestern(final int codePoint) {
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind kind =
				net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.of(codePoint);
		return kind == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
				|| kind == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC;
	}

	public abstract boolean isContextBox();

	public void finishLayoutSelf(IFramedBox containerBox) {
	}

	public void pushFinishLayoutChildren(IFramedBox containerBox,
			final java.util.Deque<FinishLayoutStep> worklist) {
		if (this.contents == null) {
			return;
		}
		if (this.isContextBox()) {
			containerBox = (IFramedBox) this;
		}
		final IFramedBox childContainerBox = containerBox;
		// 元の走査順(先頭から)を保つため、スタックへは逆順(末尾から)でpushする
		for (int i = this.contents.size() - 1; i >= 0; --i) {
			switch (this.contents.get(i)) {
			case IAbsoluteBox absoluteBox ->
				// 絶対配置
				worklist.push(IBox.step(absoluteBox, childContainerBox));

			case Inline inline ->
				// インライン
				worklist.push(IBox.step(inline.box, childContainerBox));

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
			this.blendMode = params.blendMode;
			this.filter = params.filter;
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
			double advance = 0;
			for (int i = this.off; i < this.off + this.len; ++i) {
				if (this.contents.get(i) instanceof Text t) {
					text.append(t.getChars(), 0, t.getCharCount());
					advance += t.getAdvance();
				}
			}
			final String basic = String.format(java.util.Locale.ROOT, "Text[\"%s\" asc=%.2f desc=%.2f]", text,
					this.ascent, this.descent);
			if (!net.zamasoft.foliojet.layout.draw.DisplayListDumper.currentDetailedGeometry()) {
				return basic;
			}
			final boolean vertical = this.params.flow.isVertical();
			return basic + String.format(java.util.Locale.ROOT, " w=%.2f h=%.2f", vertical ? this.ascent + this.descent : advance,
					vertical ? advance : this.ascent + this.descent);
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			// 影
			if (this.params.textShadows != null) {
				for (int i = this.params.textShadows.length - 1; i >= 0; --i) {
					TextShadow shadow = params.textShadows[i];
					try (final var gcState = gc.begin()) {
						gc.setFillPaint(shadow.color);
						if (shadow.blur > 0) {
							this.drawBlurredShadow(gc, shadow, x + shadow.x, y + shadow.y);
						} else {
							final GeneralPath outline = this.textOutline(x + shadow.x, y + shadow.y);
							try (final var artifact = gc.beginArtifactScope()) {
								if (outline != null) {
									gc.fill(outline);
								} else {
									// 字形データが手元に無いフォント。テキストで
									// 描くしかないが、せめて装飾として印を付ける
									this.drawText(gc, x + shadow.x, y + shadow.y);
								}
							}
						}
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

		/**
		 * ぼかし付きの影(2026-08-29)。PDFにぼかしのプリミティブが無いので、
		 * {@code box-shadow}と同じ12段の半透明近似
		 * ({@link net.zamasoft.foliojet.layout.util.BoxDecorationRenderer#BLUR_STEPS})
		 * をテキストで行う: 字形を段ごとに「塗り+外側へ2d幅の縁取り」
		 * (d=段の縁の位置、σ=blur/2)で重ね描きし、各段のアルファは全段が
		 * 重なる中心で指定色のアルファ(0.98で頭打ち)になる{@code 1-(1-α)^(1/N)}。内側へ
		 * 縮めた段は字形を縮められないので塗りだけ(=中心は常に指定の濃さ、
		 * 輪郭のすぐ内側は縁取りと塗りが重なりやや濃い——本体の字形の
		 * 下になる領域なので実用上見えない)。ぼかし0は従来どおり1回描く
		 * (既存出力を変えない)。
		 */
		private void drawBlurredShadow(GC gc, TextShadow shadow, double x, double y) throws GraphicsException {
			final float alpha = shadow.color.getAlpha();
			if (alpha <= 0) {
				return;
			}
			if (gc.supports(GC.Capability.GAUSSIAN_BLUR)) {
				this.drawExactBlurredShadow(gc, shadow, x, y);
				return;
			}
			net.zamasoft.foliojet.layout.util.ApproximationGC.report(gc, "text-shadow", "2822.text-blur-rings");
			final double[] steps = net.zamasoft.foliojet.layout.util.BoxDecorationRenderer.BLUR_STEPS;
			final int n = steps.length;
			// 不透明な影(α=1)では1段あたりのアルファも1になり、外縁まで
			// べた塗りの塊になってしまう。中心の合成アルファを0.98で頭打ちに
			// して、不透明色でも縁が薄れるようにする(1段あたり約0.28)
			final float layerAlpha = (float) (1 - Math.pow(1 - Math.min(alpha, 0.98), 1.0 / n));
			final double sigma = shadow.blur / 2;
			gc.setStrokePaint(shadow.color);
			gc.setFillAlpha(layerAlpha);
			gc.setStrokeAlpha(layerAlpha);
			gc.setLineJoin(GC.LineJoin.ROUND);
			gc.setLineCap(GC.LineCap.ROUND);
			gc.setLinePattern(GC.STROKE_SOLID);
			final GeneralPath outline = this.textOutline(x, y);
			try (final var artifact = gc.beginArtifactScope()) {
				for (int k = 0; k < n; ++k) {
					final double d = steps[k] * sigma;
					if (d > 0) {
						gc.setLineWidth(d * 2);
						gc.setTextMode(GC.TextMode.FILL_STROKE);
					} else {
						gc.setTextMode(GC.TextMode.FILL);
					}
					if (outline != null) {
						// 段ごとに塗り(+縁取り)。テキストで12回描くと
						// 本文がPDFへ12重に入ってしまう(下のtextOutline参照)
						if (d > 0) {
							gc.fillDraw(outline);
						} else {
							gc.fill(outline);
						}
					} else {
						this.drawText(gc, x, y);
					}
				}
			}
		}

		/**
		 * 厳密なぼかし付きの影(2026-08-29)。出力先がガウスぼかしを持つ
		 * (Java2D・SVG)ときは、影の文字を文字の範囲+3σの余白ぶんのグループ
		 * 画像へ描き、{@link GroupEffects}のぼかしを掛けて置く。
		 */
		private void drawExactBlurredShadow(GC gc, TextShadow shadow, double x, double y) throws GraphicsException {
			final double sigma = shadow.blur / 2;
			double advance = 0;
			for (int i = 0; i < this.len; ++i) {
				if (this.contents.get(i + this.off) instanceof Text t) {
					advance += t.getAdvance();
				}
			}
			final double thickness = this.ascent + this.descent;
			final boolean vertical = this.params.flow.isVertical();
			final double minX = x, minY = vertical ? y : y - this.ascent;
			final double w = vertical ? thickness : advance, h = vertical ? advance : thickness;
			// 字形のはみ出し(斜体・アクセント)ぶんも余白に含める
			final double pad = sigma * 3 + thickness * 0.5 + 1;
			final double ox = minX - pad, oy = minY - pad;
			final net.zamasoft.pdfg2d.gc.image.GroupImageGC ggc = gc.createGroupImage(w + pad * 2, h + pad * 2);
			try (final var groupState = ggc.begin()) {
				ggc.transform(AffineTransform.getTranslateInstance(-ox, -oy));
				ggc.setFillPaint(shadow.color);
				this.drawText(ggc, x, y);
			}
			final net.zamasoft.pdfg2d.gc.image.Image image = ggc.finish();
			try (final var gcState = gc.begin()) {
				gc.transform(AffineTransform.getTranslateInstance(ox, oy));
				gc.drawImage(image, new GroupEffects(null, sigma, null, 1));
			}
		}

		/**
		 * この描画単位のテキストを<b>字形の輪郭(パス)</b>として組み立てます
		 * (2026-08-30)。輪郭を取れないフォントが1つでも混ざっていたら
		 * {@code null}を返し、呼び出し側は従来どおりテキストで描きます。
		 *
		 * <p>
		 * <b>影をテキストで描くと、そのままPDFの抽出テキストへ入る。</b>
		 * 鮮明な影なら本文が2回、ぼかし付きの影は12段の重ね描きなので13回、
		 * さらに圏点({@code text-emphasis})が付くと描画単位が1文字ごとに
		 * 割れるため「減減税税と と…」と1文字ずつ交互に出る——縦組みの
		 * 実文書で報告された(2026-08-30)。影は装飾であって本文ではないので、
		 * 字形情報を持たないパスで描き、さらにタグ付きPDFでは
		 * {@code /Artifact}で囲う。
		 *
		 * <p>
		 * 座標の取り方は{@link #drawText}と同じ(縦書きは{@code x+descent}を
		 * 基準に送り、横書きは{@code y+ascent})。輪郭の組み立て自体は
		 * {@code FontUtils.addTextPath}が字送り・カーニング・字間・縦書きの
		 * 回転までまとめて行う。
		 *
		 * <p>
		 * <b>取れない場合</b>: Core-14のType1フォント({@code ShapedFont}を
		 * 実装しない)、画像字形・カラー字形のフォント。いずれも従来の
		 * テキスト描画へ落とす——影が消えるより二重に入る方がまだよい。
		 */
		private GeneralPath textOutline(double x, double y) {
			final GeneralPath path = new GeneralPath();
			final boolean vertical = this.params.flow.isVertical();
			double xx = x, yy = y;
			for (int i = 0; i < this.len; ++i) {
				final Text text = (Text) this.contents.get(i + this.off);
				final Font font = ((FontMetricsImpl) text.getFontMetrics()).getFont();
				if (!(font instanceof ShapedFont shaped)) {
					return null;
				}
				if (vertical) {
					FontUtils.addTextPath(path, shaped, text,
							AffineTransform.getTranslateInstance(x + this.descent, yy));
					yy += text.getAdvance();
				} else {
					FontUtils.addTextPath(path, shaped, text,
							AffineTransform.getTranslateInstance(xx, y + this.ascent));
					xx += text.getAdvance();
				}
			}
			return path.getCurrentPoint() == null ? null : path;
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

	/**
	 * {@code leader()}の反復描画です(leader() L2——
	 * consult-codex-2026-07-31-leader.txt Q3)。shape済みパターン1周期を
	 * 「論理的な行末」を原点とする固定グリッドへ反復描画する(同じ行末
	 * 座標を持つ複数行のドットが縦に揃う)。グリッドへ完全に入るセル
	 * だけを描き、タグ付きPDFではartifact(装飾)として囲む。論理
	 * テキストへは反復文字列を混入させない。
	 */
	protected static class LeaderDrawable extends AbstractDrawable {
		private final net.zamasoft.foliojet.layout.text.LeaderQuad leader;
		private final AbstractTextParams params;
		private final double ascent, descent;

		LeaderDrawable(PageBox pageBox, Shape clip, AffineTransform transform, AbstractTextParams params,
				net.zamasoft.foliojet.layout.text.LeaderQuad leader, double ascent, double descent) {
			super(pageBox, clip, params.opacity, transform);
			this.blendMode = params.blendMode;
			this.filter = params.filter;
			this.leader = leader;
			this.params = params;
			this.ascent = ascent;
			this.descent = descent;
		}

		/** グリッドのセル区間 [kmin, kmax](コピー数はkmax-kmin+1)。 */
		private long[] cellRange() {
			final double p = this.leader.minAdvance;
			final double end = this.leader.advance;
			final double gridOrigin = end + this.leader.endOffset;
			// セルk: [gridOrigin-(k+1)p, gridOrigin-kp)。完全に[0,end]内のみ
			final long kmin = (long) Math.ceil((gridOrigin - end) / p - 0.0001);
			final long kmax = (long) Math.floor((gridOrigin) / p - 1 + 0.0001);
			return new long[] { kmin, kmax };
		}

		public String describe() {
			final StringBuilder pattern = new StringBuilder();
			for (final Text run : this.leader.runs) {
				pattern.append(run.getChars(), 0, run.getCharCount());
			}
			final long[] range = this.cellRange();
			final long copies = Math.max(0, range[1] - range[0] + 1);
			return String.format(java.util.Locale.ROOT, "Leader[\"%s\" advance=%.2f offset=%.2f copies=%d]", pattern,
					this.leader.advance, this.leader.endOffset, copies);
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			final double p = this.leader.minAdvance;
			final double gridOrigin = this.leader.advance + this.leader.endOffset;
			final long[] range = this.cellRange();
			if (range[1] < range[0]) {
				return;
			}
			try (final var artifact = gc.beginArtifactScope(); final var gcState = gc.begin()) {
				if (this.params.color != null) {
					gc.setFillPaint(this.params.color);
				}
				final boolean vertical = this.params.flow.isVertical();
				for (long k = range[0]; k <= range[1]; ++k) {
					double cell = gridOrigin - (k + 1) * p;
					for (final Text run : this.leader.runs) {
						if (vertical) {
							gc.drawText(run, x + this.descent, y + cell);
						} else {
							gc.drawText(run, x + cell, y + this.ascent);
						}
						cell += run.getAdvance();
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
			this.blendMode = params.blendMode;
			this.filter = params.filter;
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

				// 装飾。太さは線ごとの指定(text-decoration-thickness)、無ければ
				// フォントサイズ比の既定(2026-08-29)
				final double fontSize = this.params.fontStyle.getSize();
				final double autoThickness = fontSize * this.params.decorationThickness;
				final net.zamasoft.pdfg2d.gc.font.FontListMetrics flm = this.params.getFontListMetrics();
				if (this.params.flow.isVertical()) {
					// 縦書き進行
					x += this.descent;
					final double lineAxis = this.height;
					final Decoration.Line underline = this.decoration.underline;
					if (underline != null) {
						// 下線。既定は文字の左側、text-underline-position: right なら右側。
						// text-underline-offset は文字から遠ざかる向きへ
						final double t = thicknessOf(underline, autoThickness);
						final boolean right = underline.position() == AbstractTextParams.UNDERLINE_POSITION_RIGHT;
						double lineX = right ? x + flm.getMaxAscent() : x - flm.getMaxDescent();
						if (!Double.isNaN(underline.offset())) {
							lineX += right ? underline.offset() : -underline.offset();
						}
						drawDecorationLine(gc, underline, t, lineX, y, lineX, y + lineAxis, true);
					}
					final Decoration.Line overline = this.decoration.overline;
					if (overline != null) {
						// 上線
						final double lineX = x + flm.getMaxAscent();
						drawDecorationLine(gc, overline, thicknessOf(overline, autoThickness), lineX, y, lineX,
								y + lineAxis, true);
					}
					final Decoration.Line lineThrough = this.decoration.lineThrough;
					if (lineThrough != null) {
						// 打ち消し線
						drawDecorationLine(gc, lineThrough, thicknessOf(lineThrough, autoThickness), x, y, x,
								y + lineAxis, true);
					}
				} else {
					// 横書き進行
					y += this.ascent;
					double lineAxis = this.width;
					final Decoration.Line underline = this.decoration.underline;
					if (underline != null) {
						// 下線
						final double t = thicknessOf(underline, autoThickness);
						final double descent = flm.getMaxDescent();
						double lineY;
						if (underline.position() == AbstractTextParams.UNDERLINE_POSITION_UNDER) {
							// under: ディセントの下端に線の上辺を付け、offsetがあれば
							// その分さらに下げる(css-text-decoration-4 §2.7/§2.8)
							lineY = y + descent + t / 2 + (Double.isNaN(underline.offset()) ? 0 : underline.offset());
						} else if (!Double.isNaN(underline.offset())) {
							// auto位置+offset: ベースラインを零位置として線の上辺をずらす
							lineY = y + underline.offset() + t / 2;
						} else {
							lineY = y + descent;
							// 行の下端から線の太さだけ上がった位置で押さえる
							lineY = Math.min(y + this.descent - t, lineY);
						}
						drawDecorationLine(gc, underline, t, x, lineY, x + lineAxis, lineY, false);
					}
					final Decoration.Line overline = this.decoration.overline;
					if (overline != null) {
						// 上線
						final double t = thicknessOf(overline, autoThickness);
						final double ascent = flm.getMaxAscent();
						double lineY = y - ascent;
						// 行の上端から線の太さだけ下がった位置で押さえる
						lineY = Math.max(y - this.ascent + t, lineY);
						drawDecorationLine(gc, overline, t, x, lineY, x + lineAxis, lineY, false);
					}
					final Decoration.Line lineThrough = this.decoration.lineThrough;
					if (lineThrough != null) {
						// 打ち消し線
						final double xHeight = flm.getMaxXHeight();
						final double lineY = y - xHeight / 2.0;
						drawDecorationLine(gc, lineThrough, thicknessOf(lineThrough, autoThickness), x, lineY,
								x + lineAxis, lineY, false);
					}
				}

			}
		}

		private static double thicknessOf(final Decoration.Line line, final double autoThickness) {
			return line.thickness() > 0 ? line.thickness() : autoThickness;
		}

		/**
		 * 装飾線1本を線種に従って描きます(2026-08-29)。
		 *
		 * <ul>
		 * <li>solid: 従来どおりの1本線</li>
		 * <li>double: 太さと同じ間隔を空けた2本(全体で太さの3倍)</li>
		 * <li>dotted/dashed: GCの線パターン(点=太さ角、破線=太さの3倍)</li>
		 * <li>wavy: 振幅=太さ・周期=太さの4倍の2次曲線の連なり</li>
		 * </ul>
		 * 線パターンや線端はこのDrawableの{@code gc.begin()}ブロック内なので
		 * 後続の描画へ漏れない。
		 */
		private static void drawDecorationLine(final GC gc, final Decoration.Line line, final double t,
				final double x0, final double y0, final double x1, final double y1, final boolean vertical)
				throws GraphicsException {
			gc.setStrokePaint(line.color());
			gc.setLineWidth(t);
			switch (line.style()) {
			case AbstractTextParams.DECORATION_STYLE_DOUBLE: {
				final double dx = vertical ? t : 0, dy = vertical ? 0 : t;
				gc.draw(new Line2D.Double(x0 - dx, y0 - dy, x1 - dx, y1 - dy));
				gc.draw(new Line2D.Double(x0 + dx, y0 + dy, x1 + dx, y1 + dy));
				break;
			}
			case AbstractTextParams.DECORATION_STYLE_DOTTED:
				gc.setLineCap(GC.LineCap.BUTT);
				gc.setLinePattern(new double[] { t, t });
				gc.draw(new Line2D.Double(x0, y0, x1, y1));
				break;
			case AbstractTextParams.DECORATION_STYLE_DASHED:
				gc.setLineCap(GC.LineCap.BUTT);
				gc.setLinePattern(new double[] { t * 3, t * 3 });
				gc.draw(new Line2D.Double(x0, y0, x1, y1));
				break;
			case AbstractTextParams.DECORATION_STYLE_WAVY: {
				// 半周期2t・振幅tの2次曲線(制御点を±2tに置くと頂点が±tになる)。
				// 端数の最後の半周期は長さに比例して振幅を落として端で線上に戻す
				final double length = vertical ? y1 - y0 : x1 - x0;
				final double half = t * 2;
				final java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
				path.moveTo(x0, y0);
				double p = 0;
				int sign = 1;
				while (p < length) {
					final double h = Math.min(half, length - p);
					final double amp = 2 * t * (h / half);
					final double cp = p + h / 2, end = p + h;
					if (vertical) {
						path.quadTo(x0 + sign * amp, y0 + cp, x0, y0 + end);
					} else {
						path.quadTo(x0 + cp, y0 - sign * amp, x0 + end, y0);
					}
					p = end;
					sign = -sign;
				}
				gc.setLineJoin(GC.LineJoin.ROUND);
				gc.draw(path);
				break;
			}
			default:
				gc.draw(new Line2D.Double(x0, y0, x1, y1));
				break;
			}
		}
	}

	private final Drawable createTextSequenceDrawable(PageBox pageBox, Shape clip, AffineTransform transform, int off,
			int len) {
		AbstractTextParams params = this.getTextParams();
		return new TextSequenceDrawable(pageBox, clip, transform, this.contents, off, len, params, this.ascent,
				this.descent);
	}

	public final void pushGetTextSteps(final StringBuilder textBuff, final java.util.Deque<GetTextStep> worklist) {
		if (this.contents == null) {
			return;
		}
		// テキスト抽出は文書順を保つ必要があるため、局所的な追記(Text/
		// Control)も子への委譲(Inline/IAbsoluteBox)も同じ手順列として
		// 組み立て、最後に**逆順**でworklistへpushする(2026-07-20、
		// drawと同じ理由)
		final List<GetTextStep> localSteps = new ArrayList<>();
		for (int i = 0; i < this.contents.size(); ++i) {
			switch (this.contents.get(i)) {
			case Text text -> localSteps.add(w -> textBuff.append(text.getChars(), 0, text.getCharCount()));
			case Inline inline -> localSteps.add(IBox.getTextStep(inline.box, textBuff));
			case IAbsoluteBox absoluteBox -> localSteps.add(IBox.getTextStep(absoluteBox, textBuff));
			case Control control ->
				// 空白
				localSteps.add(w -> textBuff.append(control.getControlChar()));
			case net.zamasoft.foliojet.layout.text.LeaderQuad leader ->
				// 反復ドット列は論理テキストへ混入させない——単一の空白のみ
				localSteps.add(w -> textBuff.append(' '));
			default -> throw new IllegalStateException();
			}
		}
		for (int i = localSteps.size() - 1; i >= 0; --i) {
			worklist.push(localSteps.get(i));
		}
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		if (this.contents == null || this.contents.isEmpty()) {
			return;
		}
		// 局所描画(テキストラン・装飾)と子(インライン・絶対配置)の描画が
		// 同一ループ内で交互に現れるため、両方をこの順番のまま局所リストへ
		// 積み、最後に**逆順**で共有workリストへpushする(2026-07-20、
		// IBox.pushDrawStepsと同じ理由での反復化)。局所描画をここで
		// 即座に実行してしまうと、まだ実行されていない子の描画より先に
		// なってしまい、描画順が崩れる。
		final List<DrawStep> localSteps = new ArrayList<>();
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
					final int foff = off, flen = len;
					final double ftx = tx, fty = ty;
					localSteps.add(w -> drawer.visitDrawable(
							this.createTextSequenceDrawable(pageBox, clip, transform, foff, flen), ftx, fty));
					len = 0;
				}
				if (decoration) {
					// 装飾
					if (this.decoration != null) {
						final double width = xx - dx;
						final double height = yy - dy;
						if ((vertical && height > 0) || (!vertical && width > 0)) {
							final double fdx = dx, fdy = dy;
							localSteps.add(w -> {
								Drawable drawable = new TextDecorationDrawable(pageBox, clip, transform, lineParams,
										this.decoration, this.ascent, this.descent, width, height);
								drawer.visitDrawable(drawable, fdx, fdy);
							});
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
					final double drawX = xx + voffset + inline.verticalAlign, drawY = yy;
					localSteps.add(IBox.drawStep(inlineBox, pageBox, drawer, visitor, clip, transform, contextX,
							contextY, drawX, drawY));
					yy += inlineBox.getHeight();
				} else {
					// 横書き
					final double drawX = xx, drawY = yy - voffset - inline.verticalAlign;
					localSteps.add(IBox.drawStep(inlineBox, pageBox, drawer, visitor, clip, transform, contextX,
							contextY, drawX, drawY));
					xx += inlineBox.getWidth();
				}
			}

			case IAbsoluteBox absoluteBox -> {
				// 絶対配置
				if (lineParams.opacity != 0 && len > 0) {
					final int foff = off, flen = len;
					final double ftx = tx, fty = ty;
					localSteps.add(w -> drawer.visitDrawable(
							this.createTextSequenceDrawable(pageBox, clip, transform, foff, flen), ftx, fty));
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
				localSteps.add(IBox.drawStep(absoluteBox, pageBox, drawer, visitor, clip, transform, contextX,
						contextY, xxx, yyy));
			}

			case Control control -> {
				// 空白
				if (lineParams.opacity != 0 && len > 0) {
					final int foff = off, flen = len;
					final double ftx = tx, fty = ty;
					localSteps.add(w -> drawer.visitDrawable(
							this.createTextSequenceDrawable(pageBox, clip, transform, foff, flen), ftx, fty));
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

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader -> {
				// leader() L2: 反復パターンの描画(グリフ列としては実体化
				// しない——行末原点の固定グリッドで位相を揃える)
				if (lineParams.opacity != 0 && len > 0) {
					final int foff = off, flen = len;
					final double ftx = tx, fty = ty;
					localSteps.add(w -> drawer.visitDrawable(
							this.createTextSequenceDrawable(pageBox, clip, transform, foff, flen), ftx, fty));
					len = 0;
				}
				if (!decoration) {
					dx = xx;
					dy = yy;
					decoration = true;
				}
				if (lineParams.opacity != 0) {
					final double fx = xx, fy = yy;
					localSteps.add(w -> drawer.visitDrawable(new LeaderDrawable(pageBox, clip, transform,
							this.getTextParams(), leader, this.ascent, this.descent), fx, fy));
				}
				if (vertical) {
					yy += leader.getAdvance();
				} else {
					xx += leader.getAdvance();
				}
			}

			default -> throw new IllegalStateException();
			}
		}
		if (lineParams.opacity != 0 && len > 0) {
			final int foff = off, flen = len;
			final double ftx = tx, fty = ty;
			localSteps.add(w -> drawer.visitDrawable(
					this.createTextSequenceDrawable(pageBox, clip, transform, foff, flen), ftx, fty));
			len = 0;
		}
		if (decoration && this.decoration != null) {
			final double width = xx - dx;
			final double height = yy - dy;
			if ((vertical && height > 0) || (!vertical && width > 0)) {
				final double fdx = dx, fdy = dy;
				localSteps.add(w -> {
					Drawable drawable = new TextDecorationDrawable(pageBox, clip, transform, lineParams,
							this.decoration, this.ascent, this.descent, width, height);
					drawer.visitDrawable(drawable, fdx, fdy);
				});
			}
		}
		// 元の実行順を保つため、共有worklistへは逆順でpushする
		for (int i = localSteps.size() - 1; i >= 0; --i) {
			worklist.push(localSteps.get(i));
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

	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			java.util.Deque<TextShapeStep> worklist) {
		if (this.contents == null || this.contents.isEmpty()) {
			return;
		}
		// クリップ用のpathへの追記は描画順に意味がないため、テキストは
		// その場で即座に追記してよい。子(インライン)の輪郭だけを
		// worklistへ積む(2026-07-20、反復化——drawと同じ理由)
		final List<TextShapeStep> localSteps = new ArrayList<>();
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
					final double sx = xx + voffset + inline.verticalAlign, sy = yy;
					localSteps.add(IBox.textShapeStep(inlineBox, pageBox, path, transform, sx, sy));
					yy += inlineBox.getHeight();
				} else {
					// 横書き
					final double sx = xx, sy = yy - voffset - inline.verticalAlign;
					localSteps.add(IBox.textShapeStep(inlineBox, pageBox, path, transform, sx, sy));
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

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader -> {
				// leaderは字形選択に関与しない——幅だけ進める
				if (vertical) {
					yy += leader.getAdvance();
				} else {
					xx += leader.getAdvance();
				}
			}

			default -> throw new IllegalStateException();
			}
		}
		// 元の実行順を保つため、共有worklistへは逆順でpushする
		for (int i = localSteps.size() - 1; i >= 0; --i) {
			worklist.push(localSteps.get(i));
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

			case net.zamasoft.foliojet.layout.text.LeaderQuad leader ->
				// 再駆動でquadを流し直す(幅はdrawLineが割り付け直す)
				gh.control(leader);

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
