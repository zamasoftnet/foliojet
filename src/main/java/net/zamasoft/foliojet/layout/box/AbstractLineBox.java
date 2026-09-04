package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.content.JustificationState;
import net.zamasoft.foliojet.layout.box.impl.LineBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.LinePos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.SidewaysGeometry;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;

/**
 * 行ボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractLineBox.java 1640 2023-10-04 03:06:26Z miyabe $
 */
public abstract class AbstractLineBox extends AbstractTextBox {
	private static final boolean DEBUG = false;
	private static final java.util.concurrent.atomic.AtomicLong NEXT_LINE_ID =
			new java.util.concurrent.atomic.AtomicLong();

	/**
	 * 行方向アラインメントです。
	 */
	protected double lineAlign = 0;
	/** alignに渡された行内軸の物理寸法。 */
	private double inlineExtent;

	/**
	 * 行末またはブロックの末です。
	 */
	protected boolean last = false;

	/** 段落 UBA が有効なときの描画専用 tree。論理 contents は不変。 */
	private List<Object> visualContents;
	private java.util.Map<Object, net.zamasoft.foliojet.layout.text.bidi.BidiSlice> bidiSlices = java.util.Map.of();
	private boolean paragraphBidiEnabled;
	private byte bidiBaseDirection = AbstractTextParams.DIRECTION_LTR;
	private long bidiParagraphId;
	private net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission logicalLineEmission;
	private String logicalLineVisualText;
	/** TextReplaySlice が段落途中から再開するときの、先行行の論理文脈。 */
	private net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix bidiReplayPrefix =
			net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix.EMPTY;

	public abstract AbstractLineParams getLineParams();

	public BoxType getType() {
		return BoxType.LINE;
	}

	public Pos getPos() {
		return LinePos.POS;
	}

	public boolean isLast() {
		return this.last;
	}

	public final void setParagraphBidi(final boolean enabled, final byte baseDirection) {
		this.paragraphBidiEnabled = enabled;
		this.bidiBaseDirection = baseDirection;
	}

	public final boolean isParagraphBidiEnabled() {
		return this.paragraphBidiEnabled;
	}

	public final void setVisualContents(final List<Object> visualContents,
			final java.util.Map<Object, net.zamasoft.foliojet.layout.text.bidi.BidiSlice> bidiSlices) {
		this.visualContents = java.util.Collections.unmodifiableList(new ArrayList<Object>(visualContents));
		this.bidiSlices = java.util.Collections.unmodifiableMap(new java.util.IdentityHashMap<>(bidiSlices));
		final StringBuilder visual = new StringBuilder();
		appendVisualText(this.visualContents, visual);
		this.logicalLineVisualText = visual.toString();
		this.prepareLogicalLineEmission();
	}

	public final List<Object> getVisualContents() {
		return this.visualContents == null ? java.util.Collections.emptyList() : this.visualContents;
	}

	@Override
	public final net.zamasoft.foliojet.layout.text.bidi.BidiSlice getBidiSlice(final Object visualContent) {
		return this.bidiSlices.get(visualContent);
	}

	@Override
	public final net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission getLogicalLineEmission() {
		return this.logicalLineEmission;
	}

	@Override
	public final String getLogicalLineVisualText() {
		return this.logicalLineVisualText;
	}

	/**
	 * Creates the sidecar before visual inline fragments are built. Atomic inlines
	 * use U+FFFC because alternative text is not uniformly available at this layer.
	 */
	public final net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission prepareLogicalLineEmission() {
		final StringBuilder logical = new StringBuilder();
		appendLogicalText(this.contents, logical);
		final String text = logical.toString();
		if (this.logicalLineEmission == null || !this.logicalLineEmission.logicalText().equals(text)) {
			final long lineId = this.logicalLineEmission == null ? NEXT_LINE_ID.incrementAndGet()
					: this.logicalLineEmission.lineId();
			this.logicalLineEmission = new net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission(lineId, text);
		}
		return this.logicalLineEmission;
	}

	private static void appendLogicalText(final List<Object> contents, final StringBuilder logical) {
		if (contents == null) {
			return;
		}
		for (final Object content : contents) {
			if (content instanceof net.zamasoft.pdfg2d.gc.text.Text text) {
				logical.append(text.getChars(), 0, text.getCharCount());
			} else if (content instanceof net.zamasoft.pdfg2d.gc.text.layout.control.Control control) {
				logical.append(control.getControlChar());
			} else if (content instanceof Inline inline) {
				if (inline.box instanceof net.zamasoft.foliojet.layout.box.impl.InlineBox box) {
					appendLogicalText(box.getLogicalContents(), logical);
				} else {
					logical.append('\uFFFC');
				}
			} else if (content instanceof net.zamasoft.foliojet.layout.text.LeaderQuad) {
				logical.append('\uFFFC');
			}
		}
	}

	private static void appendVisualText(final List<Object> contents, final StringBuilder visual) {
		if (contents == null) {
			return;
		}
		for (final Object content : contents) {
			if (content instanceof net.zamasoft.pdfg2d.gc.text.Text text) {
				visual.append(text.getChars(), 0, text.getCharCount());
			} else if (content instanceof net.zamasoft.pdfg2d.gc.text.layout.control.Control control) {
				visual.append(control.getControlChar());
			} else if (content instanceof Inline inline
					&& inline.box instanceof net.zamasoft.foliojet.layout.box.impl.InlineBox box) {
				appendVisualText(box.getLogicalContents(), visual);
			}
		}
	}

	public final void setBidiReplayPrefix(
			final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix prefix) {
		this.bidiReplayPrefix = prefix;
	}

	public final void setBidiParagraphId(final long paragraphId) {
		this.bidiParagraphId = paragraphId;
	}

	public final long getBidiParagraphId() {
		return this.bidiParagraphId;
	}

	public final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix getBidiReplayPrefix() {
		return this.bidiReplayPrefix;
	}

	@Override
	protected List<Object> getDrawingContents() {
		return this.visualContents == null ? super.getDrawingContents() : this.visualContents;
	}

	public void addAscentDescent(double ascent, double descent) {
		// アセントディセントの拡大
		if (ascent > this.ascent) {
			this.ascent = ascent;
		}
		if (descent > this.descent) {
			this.descent = descent;
		}
		assert !LayoutUtils.isNone(this.ascent + this.descent);
	}

	/**
	 * 行方向アラインメントを適用します。
	 * 
	 * @param textIndent  インデント
	 * @param offset      浮動ボックス等によるずれ
	 * @param maxLineAxis 最大行幅
	 * @param last        ブロックの末尾または改行された行
	 */
	/**
	 * 行末の詰め/ぶら下げ分です(和文詰めT2/H1——
	 * consult-codex-2026-07-31-text-spacing.txt)。行の配置・均等割りは
	 * この分を除いた実効行幅を基準にし、glyph自体は通常どおり描画される
	 * (ぶら下げ句読点・半角化された行末約物のはみ出しはink扱い)。
	 */
	private double endHangAdvance;

	/**
	 * {@code text-overflow: ellipsis}の省略記号(なければnull。2026-08-29、
	 * TextBuilder.applyTextOverflow)。行の内容とは別に持ち、描画時に
	 * 行末をクリップして追加描画する。
	 */
	private net.zamasoft.pdfg2d.gc.text.Text ellipsis;

	/** 行原点(lineAlign適用前)から測った、内容を描く行方向の長さ。 */
	private double ellipsisClipExtent;

	public void setEllipsis(final net.zamasoft.pdfg2d.gc.text.Text ellipsis, final double clipExtent) {
		this.ellipsis = ellipsis;
		this.ellipsisClipExtent = clipExtent;
	}

	public net.zamasoft.pdfg2d.gc.text.Text getEllipsis() {
		return this.ellipsis;
	}

	/** 行原点から内容の先頭までの行方向のずれ({@link #align}で決まる)。 */
	public double getLineAlign() {
		return this.lineAlign;
	}

	public void setEndHangAdvance(final double endHangAdvance) {
		this.endHangAdvance = endHangAdvance;
	}

	public double getEndHangAdvance() {
		return this.endHangAdvance;
	}

	public void align(double textIndent, double offset, double maxLineAxis, boolean last) {
		// 行方向アラインメント
		assert this.contents != null && !this.contents.isEmpty();
		// OFF は従来の行単位経路をそのまま保つ。ON は段落終端で
		// 別の visualContents を構成するので、論理 contents に触れない。
		if (!this.paragraphBidiEnabled) {
			this.reorderBidi();
		}
		this.last = last;
		this.inlineExtent = maxLineAxis;
		AbstractLineParams params = this.getLineParams();
		// T2/H1: 実効行幅(行末の詰め/ぶら下げ分を除く)
		double lineWidth = this.lineSize - this.endHangAdvance + textIndent;
		textIndent += offset;
		byte textAlign = last ? params.textAlignLast : params.textAlign;
		// sideways は LTR と同じ論理 offset を作り、描画時の inlineToPhysical で
		// 一度だけ物理化する。通常組版の RTL だけ従来の start/end 交換を残す。
		if (this.paragraphBidiEnabled && this.bidiBaseDirection == AbstractTextParams.DIRECTION_RTL
				&& !TypesettingMode.usesSidewaysInlineAxis(params.flow, params.writingModeVariant)) {
			if (textAlign == AbstractLineParams.TEXT_ALIGN_START) {
				textAlign = AbstractLineParams.TEXT_ALIGN_END;
			} else if (textAlign == AbstractLineParams.TEXT_ALIGN_END) {
				textAlign = AbstractLineParams.TEXT_ALIGN_START;
			}
		}
		switch (textAlign) {
		case AbstractLineParams.TEXT_ALIGN_CENTER:
			// 中央合わせ
			this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
			break;

		case AbstractLineParams.TEXT_ALIGN_END:
			// 行末に合わせる
			this.lineAlign = maxLineAxis - lineWidth + textIndent;
			break;

		case AbstractLineParams.TEXT_ALIGN_JUSTIFY: {
			// 両方合わせ
			double remainderAdvance = maxLineAxis - lineWidth;
			if (remainderAdvance > 0) {
				this.justifyByWritingSystem(remainderAdvance);
			}
			this.lineAlign = textIndent;
		}
			break;

		case AbstractLineParams.TEXT_ALIGN_START:
			// 行頭に合わせる
			this.lineAlign = textIndent;
			break;

		case AbstractLineParams.TEXT_ALIGN_X_JUSTIFY_CENTER:
			// 中央-両合わせ
			double remainderAdvance = maxLineAxis - lineWidth;
			if (remainderAdvance <= 0) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}
			double fontSize = this.getTextParams().fontStyle.getSize();
			if (remainderAdvance <= fontSize) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}

			final boolean japanese = this.containsJapaneseComposition();
			final double capacity = japanese
					? this.justificationCapacity(JUSTIFY_FALLBACK, new JustificationState())
					: this.countGeneralJustificationPoints(new JustificationState());
			if (capacity <= 0) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}
			this.justifyByWritingSystem(remainderAdvance - fontSize);
			this.lineAlign = textIndent + fontSize / 2.0;
			break;

		default:
			throw new IllegalStateException();
		}

		// ページ方向アラインメント
		super.verticalAlign(this, 0);
	}

	/**
	 * 行の余りを配ります。配り方は{@code text-justify}(2026-09-02):
	 * {@code none}は配らない、{@code inter-word}は語間だけ、
	 * {@code inter-character}は文字間へ(和文行は JLREQ の段階、他は分離可能境界)、
	 * {@code auto}は言語で決める——和文行は JLREQ、韓国語({@code lang=ko})は
	 * 語間だけ(Chrome の実測: 空白だけが伸び、音節の送りは動かない)、
	 * それ以外は従来の分離可能境界。
	 */
	private void justifyByWritingSystem(final double remainder) {
		if (remainder <= 0) {
			return;
		}
		final byte mode = this.getTextParams().textJustify;
		if (mode == AbstractTextParams.TEXT_JUSTIFY_NONE) {
			return;
		}
		if (mode == AbstractTextParams.TEXT_JUSTIFY_INTER_WORD
				|| mode == AbstractTextParams.TEXT_JUSTIFY_AUTO && this.isKorean()) {
			final int spaces = this.countWordSpaceJustificationPoints(new JustificationState());
			if (spaces > 0) {
				this.justifyWordSpaces(remainder / spaces, new JustificationState());
				return;
			}
			if (mode == AbstractTextParams.TEXT_JUSTIFY_INTER_WORD) {
				// 語間が無い行は動かさない(css-text-3 §7.3)
				return;
			}
			// 韓国語の auto で語間が無い行だけ、文字間へ落とす
		}
		if (this.containsJapaneseComposition()) {
			this.justifyByJlreqPriorities(remainder);
			return;
		}
		final int count = this.countGeneralJustificationPoints(new JustificationState());
		if (count > 0) {
			this.justifyGeneral(remainder / count, new JustificationState());
		}
	}

	/** この行の言語が韓国語か({@code lang}が{@code ko})。 */
	private boolean isKorean() {
		final java.util.Locale lang = this.getTextParams().fontStyle == null ? null
				: this.getTextParams().fontStyle.getLang();
		return lang != null && "ko".equals(lang.getLanguage());
	}

	/** JLREQ 3.8.4の4段階で行の余りを配分する。 */
	private void justifyByJlreqPriorities(double remainder) {
		if (remainder <= 0) {
			return;
		}
		for (int priority = JUSTIFY_WORD_SPACE; priority <= JUSTIFY_GENERAL && remainder > 0.0001;
				++priority) {
			final double capacity = this.justificationCapacity(priority, new JustificationState());
			if (capacity <= 0) {
				continue;
			}
			final double used = Math.min(remainder, capacity);
			this.justify(priority, used / capacity, new JustificationState());
			remainder -= used;
		}
		if (remainder > 0.0001) {
			final double weight = this.justificationCapacity(JUSTIFY_FALLBACK, new JustificationState());
			if (weight > 0) {
				this.justify(JUSTIFY_FALLBACK, remainder / weight, new JustificationState());
			}
		}
	}

	public LineBox splitLine(BlockParams params) {
		LineBox newLine = new LineBox(params);
		return newLine;
	}

	/**
	 * この行のトップレベルの内容を Unicode 双方向アルゴリズム(UAX #9)の
	 * 視覚順に並べ替え、右横書き(RTL)ランのグリフを反転します。行のテキストが
	 * すべて左横書き(LTR)なら何もしないため、既存の LTR 文書の出力は変わりません。
	 */
	private void reorderBidi() {
		if (this.contents == null || this.contents.isEmpty()) {
			return;
		}
		// 水平組版(horizontal-tb と sideways-*)のみを対象とする。
		if (this.getLineParams().isVerticalTypesetting()) {
			return;
		}
		final int n = this.contents.size();

		// 行の論理順テキストを構築(非テキストは中立オブジェクト U+FFFC)。
		final StringBuilder logical = new StringBuilder();
		final int[] itemStart = new int[n];
		for (int i = 0; i < n; ++i) {
			itemStart[i] = logical.length();
			if (this.contents.get(i) instanceof net.zamasoft.pdfg2d.gc.text.Text text) {
				logical.append(text.getChars(), 0, text.getCharCount());
			} else {
				logical.append('￼');
			}
		}

		final java.text.Bidi bidi = new java.text.Bidi(logical.toString(), java.text.Bidi.DIRECTION_LEFT_TO_RIGHT);
		if (bidi.isLeftToRight()) {
			// 純 LTR: 並べ替え不要。既存出力を厳密に保持する。
			return;
		}

		final byte[] levels = new byte[n];
		for (int i = 0; i < n; ++i) {
			levels[i] = (byte) bidi.getLevelAt(itemStart[i]);
		}
		final int[] order = net.zamasoft.pdfg2d.gc.text.pipeline.Itemizer.reorderVisual(levels);

		final List<Object> newContents = new ArrayList<Object>(n);
		for (final int idx : order) {
			Object content = this.contents.get(idx);
			// RTL テキストランはグリフを視覚順に反転する。
			if ((levels[idx] & 1) != 0 && content instanceof net.zamasoft.pdfg2d.gc.text.TextImpl ti) {
				content = ti.reverse();
			}
			newContents.add(content);
		}
		this.contents = newContents;
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		if (this.ellipsis != null) {
			// text-overflow: ellipsis(2026-08-29)。内容は行末側を
			// ellipsisClipExtentで切り、省略記号を元のクリップで最後に描く
			// (worklistはLIFOなので先にpushすると子の後で実行される)
			final AbstractLineParams lineParams = this.getLineParams();
			final boolean sideways = lineParams.writingModeVariant != WritingModeVariant.NORMAL;
			final boolean vertical = lineParams.flow.isVertical();
			final boolean bottomToTop = vertical && lineParams.writingModeVariant != WritingModeVariant.NORMAL
					&& TypesettingMode.inlineProgression(lineParams.flow, lineParams.writingModeVariant,
							lineParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
			final double keepStart = vertical
					? LayoutUtils.inlineToPhysical(lineParams, this.inlineExtent, this.lineAlign,
							this.lineAlign + this.ellipsisClipExtent)
					: this.lineAlign;
			final double pw = pageBox.getWidth(), ph = pageBox.getHeight();
			final java.awt.geom.Rectangle2D.Double keep;
			if (sideways) {
				final java.awt.geom.Rectangle2D bounds = SidewaysGeometry.bounds(lineParams.writingModeVariant, x,
						y + (bottomToTop ? keepStart : 0),
						this.ascent, this.descent, this.ellipsisClipExtent);
				keep = bottomToTop
						? new java.awt.geom.Rectangle2D.Double(bounds.getX() - pw, bounds.getY(),
								bounds.getWidth() + pw * 2, bounds.getHeight() + ph)
						: new java.awt.geom.Rectangle2D.Double(bounds.getX() - pw, bounds.getY() - ph,
								bounds.getWidth() + pw * 2, bounds.getHeight() + ph);
			} else {
				keep = bottomToTop
						? new java.awt.geom.Rectangle2D.Double(x - pw, y + keepStart, pw * 3,
								ph + this.ellipsisClipExtent)
						: vertical
						? new java.awt.geom.Rectangle2D.Double(x - pw, y - ph, pw * 3, ph + this.ellipsisClipExtent)
						: new java.awt.geom.Rectangle2D.Double(x - pw, y - ph, pw + this.ellipsisClipExtent, ph * 3);
			}
			final Shape outerClip = clip;
			final double ex = vertical ? x : x + this.ellipsisClipExtent;
			final double ey = bottomToTop
					? y + LayoutUtils.inlineToPhysical(lineParams, this.inlineExtent,
							this.lineAlign + this.ellipsisClipExtent,
							this.lineAlign + this.ellipsisClipExtent + this.ellipsis.getAdvance())
					: vertical ? y + this.ellipsisClipExtent : y;
			final List<Object> run = java.util.Collections.singletonList(this.ellipsis);
			worklist.push(w -> drawer.visitDrawable(new TextSequenceDrawable(pageBox, outerClip, transform, run, 0, 1,
					this.getTextParams(), this.ascent, this.descent), ex, ey));
			if (clip == null) {
				clip = keep;
			} else if (clip instanceof java.awt.geom.Rectangle2D rc) {
				clip = rc.createIntersection(keep);
			} else {
				final java.awt.geom.Area area = new java.awt.geom.Area(clip);
				area.intersect(new java.awt.geom.Area(keep));
				clip = area;
			}
		}
		switch (this.getLineParams().flow) {
		case WritingMode.TB:
			// 横書き
			x += this.lineAlign;
			break;

		case WritingMode.LR:
		case WritingMode.RL:
			// 縦書き
			y += LayoutUtils.inlineToPhysical(this.getLineParams(), this.inlineExtent, this.lineAlign,
					this.lineAlign + this.lineSize);
			break;

		default:
			throw new IllegalStateException();
		}

		visitor.visitBox(transform, this, drawer, x, y);
		if (DEBUG) {
			// super(子)の描画より後に見えるよう、先にpushして最後にpopされるようにする
			final double fx = x, fy = y;
			worklist.push(w -> {
				Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), GrayColor.create(.5f));
				drawer.visitDrawable(drawable, fx, fy);
			});
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			java.util.Deque<TextShapeStep> worklist) {
		switch (this.getLineParams().flow) {
		case WritingMode.TB:
			// 横書き
			x += this.lineAlign;
			break;

		case WritingMode.LR:
		case WritingMode.RL:
			// 縦書き
			y += LayoutUtils.inlineToPhysical(this.getLineParams(), this.inlineExtent, this.lineAlign,
					this.lineAlign + this.lineSize);
			break;

		default:
			throw new IllegalStateException();
		}
		super.pushTextShapeSteps(pageBox, path, transform, x, y, worklist);
	}
}
