package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.fragment.LineCutter;
import net.zamasoft.foliojet.layout.fragment.SplitResult;

import net.zamasoft.foliojet.layout.box.content.BreakToken;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import java.util.Deque;

import net.zamasoft.foliojet.css.impl.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBox;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TextBlockPos;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BuilderGlyphHandler;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;

/**
 * テキストだけを含むことができるボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TextBlockBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class TextBlockBox extends AbstractBox implements IPageBreakableBox, IFlowBox {
	/**
	 * ボックスの外辺を薄紫色の枠で囲みます。
	 */
	private static final boolean DEBUG = false;

	/**
	 * 配置された行です。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: TextBlockBox.java 1631 2022-05-15 05:43:49Z miyabe $
	 */
	protected static class Line {
		public final AbstractLineBox box;
		public final double pageAxis;

		public Line(AbstractLineBox line, double pageAxis) {
			this.box = line;
			this.pageAxis = pageAxis;
		}

		public double getPageEnd() {
			return this.pageAxis + this.box.getAscent() + this.box.getDescent();
		}

		public String toString() {
			return this.box.toString();
		}
	}

	protected final BlockParams params;

	/**
	 * テキストブロックに含まれる行のリスト。
	 */
	protected final List<Line> lines = new ArrayList<Line>();

	protected double lineSize = 0;

	/**
	 * このテキストブロックの継続状態です。
	 */
	protected final BreakToken breakToken;

	public TextBlockBox(final BlockParams params, final BreakToken breakToken) {
		this.params = params;
		this.breakToken = breakToken;
	}

	public final BoxType getType() {
		return BoxType.TEXT_BLOCK;
	}

	/**
	 * このテキストブロックの継続トークンを返します(M6b)。
	 */
	public final BreakToken getBreakToken() {
		return this.breakToken;
	}

	public final Params getParams() {
		return this.params;
	}

	public final BlockParams getBlockParams() {
		return this.params;
	}

	public final Pos getPos() {
		return TextBlockPos.POS;
	}

	public final double getFirstAscent() {
		double ascent = 0;
		if (this.lines != null && !this.lines.isEmpty()) {
			Line line = (Line) this.lines.get(0);
			ascent += line.box.getAscent();
		}
		return ascent;
	}

	public final double getLastDescent() {
		double descent = 0;
		if (this.lines != null && !this.lines.isEmpty()) {
			final Line line = (Line) this.lines.get(this.lines.size() - 1);
			descent += line.box.getDescent();
		}
		return descent;
	}

	public final double getLineSize() {
		return this.lineSize;
	}

	public final double getPageSize() {
		// 切断残余の運搬体や、回復時に開始のないINLINE_ENDだけを
		// 捨てたブロックは、行をまだ／もう持たない。幾何寸法は0とし、
		// 描画有無の保守判定はpaintedPageExtent()のPAINTS_UNKNOWNへ任せる。
		if (this.lines.isEmpty()) {
			return 0;
		}
		Line line = (Line) this.lines.get(this.lines.size() - 1);
		return line.getPageEnd();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>行が1本もないテキストブロックは「測れない」</b>——切断された段落の
	 * 尾部断片は、まだソースから再生されていない中身を待っている状態であり、
	 * 「何も描かない」と断じてはいけない(断じると、白紙ページの抑止判定が
	 * その断片ごと捨ててよいと誤り、<b>内容が消える</b>)。
	 * {@link net.zamasoft.foliojet.layout.util.LayoutUtils#PAINTS_UNKNOWN}を
	 * 返して、判定を常に安全側(=描くものがある)へ倒す。幾何寸法を返す
	 * {@link #getPageSize()}は空なら0だが、描画有無だけはそれと分けて扱う。
	 * </p>
	 */
	@Override
	public double paintedPageExtent(final net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
		if (this.lines.isEmpty()) {
			return LayoutUtils.PAINTS_UNKNOWN;
		}
		return this.getPageExtent(flow);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * テキストは<b>行の中にしか描かれません</b>。行が占めるページ方向の高さが
	 * 0なら、字面も下線も置く場所がない=何も描きません
	 * ({@link #getPageSize()}は最後の行の終端、つまり全行の高さの合計)。
	 * </p>
	 *
	 * <p>
	 * <b>行が1本もない場合はここも「描く」と答えます</b>——
	 * {@link #paintedPageExtent}が
	 * {@link LayoutUtils#PAINTS_UNKNOWN}を返すためです。切断された段落の
	 * 尾部断片は「中身をこれからソース再生で受け取る器」であり、空だからと
	 * 捨てると<b>内容が消えます</b>(そちらのjavadoc参照)。
	 * </p>
	 */
	@Override
	public boolean paintsAnything() {
		return LayoutUtils.compare(this.paintedPageExtent(this.params.flow), 0) > 0;
	}

	public final double getWidth() {
		if (this.params.flow.isVertical()) {
			// 縦書き
			return this.getPageSize();
		} else {
			// 横書き
			return this.lineSize;
		}
	}

	public final double getHeight() {
		if (this.params.flow.isVertical()) {
			// 縦書き
			return this.lineSize;
		} else {
			// 横書き
			return this.getPageSize();
		}
	}

	public final double getInnerWidth() {
		return this.getWidth();
	}

	public final double getInnerHeight() {
		return this.getHeight();
	}

	/**
	 * 行ボックスを列挙します(読み取り専用。脚注F4のcall走査用に公開)。
	 *
	 * @param action 各行に適用する処理
	 */
	public final void forEachLine(final java.util.function.Consumer<net.zamasoft.foliojet.layout.box.AbstractLineBox> action) {
		for (int i = 0; i < this.lines.size(); ++i) {
			action.accept(this.lines.get(i).box);
		}
	}

	public final void addLine(AbstractLineBox lineBox, double pageAxis) {
		// 行が増えると「内容がある」の答えが変わりうる(FlowContainerのメモ)
		if (this.getContentParent() != null) {
			this.getContentParent().invalidateNonDecorationContent();
		}
		assert !LayoutUtils.isNone(pageAxis);
		this.lines.add(new Line(lineBox, pageAxis));
		// この拡張はIE互換モードでなければ、あまり意味はない
		// (T2/H1: 行末の詰め/ぶら下げ分は論理幅から除く=effective基準)
		this.lineSize = Math.max(lineBox.getLineSize() - lineBox.getEndHangAdvance(), this.lineSize);
	}

	/**
	 * 表の直前へ独立して出力した外置きマーカー専用ブロックか。
	 */
	public final boolean overlaysFollowingBlock() {
		return this.lines.size() == 1 && this.lines.get(0).box.containsOnlyOverlayOutsideMarker();
	}

	public final void finishLayoutSelf(IFramedBox containerBox) {
	}

	public final void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		// 元の走査順(先頭行から)を保つため、スタックへは逆順(末尾行から)でpushする
		for (int i = this.lines.size() - 1; i >= 0; --i) {
			Line line = (Line) this.lines.get(i);
			worklist.push(IBox.step(line.box, containerBox));
		}
	}

	public final void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		// 元の走査順(先頭行から)を保つため、スタックへは逆順(末尾行から)でpushする
		for (int i = this.lines.size() - 1; i >= 0; --i) {
			Line line = (Line) this.lines.get(i);
			worklist.push(IBox.getTextStep(line.box, textBuff));
		}
	}

	/**
	 * 行境界では一切前進できない(=行分割の切断点が存在しない)場合に、
	 * その唯一の行の物理下端を返します。前進できるなら
	 * {@link LayoutUtils#NONE}を返します(2026-07-25新設、救済分割・増分6。
	 * {@code docs/consultations/consult-rescue-split-codex.md} §1)。
	 *
	 * <p>
	 * 「巨大な行」の救済分割は、{@link #split(double, byte)}を<b>呼ぶ前に</b>
	 * この値を検査して判定します。{@link LineCutter}はフラグメント先頭
	 * ({@code FLAGS_FIRST})で実質1行しかなければ<b>無条件に</b>
	 * {@code KEEP}を返す——つまり容量を超えていてもはみ出したまま
	 * 描かれる——ので、切断結果からはその非進行を区別できないためです。
	 * 巨大フォント・背の高いインラインブロック・インラインテーブル・
	 * ルビ単位・インライン置換要素は、すべて「背の高い1行」として
	 * この一点に集約されます(個別の分岐は作りません)。
	 * </p>
	 *
	 * <p>
	 * <b>複数行あるときは救済しません</b>。行分割が実際に前進する
	 * (先頭行を残して残りを次フラグメントへ送る)ため非進行点ではなく、
	 * そこで段落全体を幾何学的に切ると「全ページに全行の帯が並ぶ」という
	 * 明確な劣化になるからです({@code files/unittest/2010-LIMIT/line.html}
	 * で実測)。先頭行だけが極端に高い段落のはみ出しは、従来どおり
	 * 残ります。
	 * </p>
	 *
	 * @return 前進できないときの唯一の行の下端。前進できるなら{@code NONE}
	 */
	public final double getUnbreakableLinePageEnd() {
		if (this.lines.isEmpty()) {
			return LayoutUtils.NONE;
		}
		final double[] lineStarts = new double[this.lines.size()];
		final double[] lineEnds = new double[this.lines.size()];
		this.measureLines(lineStarts, lineEnds);
		if (!LineCutter.singleEffectiveLine(lineStarts, lineEnds)) {
			return LayoutUtils.NONE;
		}
		return lineEnds[0];
	}

	/** 各行の上辺・底辺(このボックスの上端からの距離)を採取します。 */
	private void measureLines(final double[] lineStarts, final double[] lineEnds) {
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			lineStarts[i] = line.pageAxis;
			lineEnds[i] = line.getPageEnd();
		}
	}

	public final double getCutPoint(double pageAxis) {
		if (this.lines.isEmpty()) {
			return pageAxis;
		}
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			final double bottom = line.pageAxis + line.box.getPageExtent(this.getBlockParams().flow);
			if (LayoutUtils.compare(bottom, pageAxis) >= 0) {
				pageAxis = bottom;
				break;
			}
		}

		return pageAxis;
	}

	/**
	 * 提案位置の直前の行境界を返します(M5-B)。getCutPoint の切り上げに
	 * 対する切り下げで、提案位置より前に行境界がなければ 0 を返します。
	 *
	 * @param pageAxis 提案位置
	 * @return 直前の行境界(なければ 0)
	 */
	public final double getCutPointBelow(final double pageAxis) {
		double result = 0;
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			final double bottom = line.pageAxis + line.box.getPageExtent(this.getBlockParams().flow);
			if (LayoutUtils.compare(bottom, pageAxis) > 0) {
				break;
			}
			result = bottom;
		}
		return result;
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);
		visitor.visitBox(transform, this, drawer, x, y);

		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(1, .5f, 1));
			drawer.visitDrawable(drawable, x, y);
		}
		// 元の走査順(先頭行から)を保つため、スタックへは逆順(末尾行から)でpushする
		for (int i = this.lines.size() - 1; i >= 0; --i) {
			Line line = (Line) this.lines.get(i);
			AbstractLineBox lineBox = line.box;
			// 描画(論理→物理変換は LayoutUtils.drawX/drawY に集約)
			worklist.push(IBox.drawStep(lineBox, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(this.params.flow, x, this.getPageSize(), line.pageAxis, line.getPageEnd(), 0),
					LayoutUtils.drawY(this.params.flow, y, line.pageAxis, 0)));
		}
	}

	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			Deque<TextShapeStep> worklist) {
		// 元の走査順(先頭行から)を保つため、スタックへは逆順(末尾行から)でpushする
		for (int i = this.lines.size() - 1; i >= 0; --i) {
			Line line = (Line) this.lines.get(i);
			AbstractLineBox lineBox = line.box;
			worklist.push(IBox.textShapeStep(lineBox, pageBox, path, transform,
					LayoutUtils.drawX(this.params.flow, x, this.getPageSize(), line.pageAxis, line.getPageEnd(), 0),
					LayoutUtils.drawY(this.params.flow, y, line.pageAxis, 0)));
		}
	}

	/**
	 * 行境界でページ方向に切断します(柱2cの型付きプロトコル)。
	 * 切断判定は {@link LineCutter} が行い、Split の場合このボックスは
	 * 前ページ分の行のみを保持するよう変異します。
	 *
	 * @param pageLimit ボックスの外辺から切断線までの距離
	 * @param flags     IPageBreakableBox.FLAGS_* のビット和
	 * @return 切断結果
	 */
	public final SplitResult split(final double pageLimit, final byte flags) {
		assert (!this.lines.isEmpty());
		// FLAGS_LASTは実際の要素に対するもので、仮想的なテキストブロックには適用しない

		final double pageSize = this.getPageExtent(this.params.flow);
		final double[] lineStarts = new double[this.lines.size()];
		final double[] lineEnds = new double[this.lines.size()];
		this.measureLines(lineStarts, lineEnds);
		final LineCutter.Decision decision = LineCutter.decide(pageLimit, pageSize, this.params.lineHeight,
				this.params.orphans, this.params.widows, (flags & IPageBreakableBox.FLAGS_FIRST) != 0, lineStarts,
				lineEnds);
		switch (decision) {
		case LineCutter.Decision.Keep keep:
			return SplitResult.KEEP;
		case LineCutter.Decision.Move move:
			return SplitResult.MOVE;
		case LineCutter.Decision.CutAfter(final int lastLine): {
			// 切断行以降(widows)を次ページのフラグメントに移す
			final int firstWidow = lastLine + 1;
			final double top = ((Line) this.lines.get(firstWidow)).pageAxis;
			// 再開位置 = 前断片(切断行まで)の末尾文字終端(M6b v3)。
			// 残余先頭の firstCharOffset は行分割時の Text 分割の丸めで
			// ずれることがあるため、残った側の終端から導出する
			final int resumeOffset = ((Line) this.lines.get(lastLine)).box.lastCharEnd();
			final BreakToken token = ((Line) this.lines.get(lastLine)).box.isLast()
					? new BreakToken.MidFlow(resumeOffset)
					: new BreakToken.MidLine(resumeOffset);
			final TextBlockBox nextTextBlock = new TextBlockBox(this.params, token);
			for (int i = firstWidow; i < this.lines.size(); ++i) {
				final Line line = (Line) this.lines.get(i);
				nextTextBlock.addLine(line.box, line.pageAxis - top);
			}
			while (this.lines.size() > firstWidow) {
				this.lines.remove(this.lines.size() - 1);
			}
			assert !this.lines.isEmpty();
			assert !nextTextBlock.lines.isEmpty();
			// M3b Phase 2/3a: handoff 内容は破断時点で確定する — 残余の
			// 正規化イベント列をここで捕捉し、行は捨てる。運搬体は
			// slice+breakToken だけを運ぶ(切断は live、運搬は不変、
			// 再開で再構築 — grok 裁定 docs/consult-p3-resplit-grok.txt。
			// resume 前に残余の行・寸法を読む経路はない)
			nextTextBlock.slice = nextTextBlock.recordSlice();
			nextTextBlock.lines.clear();
			return new SplitResult.Split(nextTextBlock);
		}
		}
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		assert !(mode instanceof BreakMode.ForceBreakMode);
		return this.split(pageLimit, flags);
	}

	public final int getLineCount() {
		return this.lines.size();
	}

	/**
	 * 破断時に捕捉した残余の正規化イベント列です(M3b Phase 2)。
	 * 分割断片(運搬体)のみ非 null。
	 */
	private net.zamasoft.foliojet.layout.fragment.TextReplaySlice slice;

	public final void restyle(final BlockBuilder builder) {
		// sliceを持たない空ブロックは、開始のないINLINE_ENDだけを
		// 回復的に捨てて確定したもの。再生すべきソースも行もない。
		// 切断残余は行が空でもsliceを持つため、ここでは吸収されない。
		if (this.slice == null && this.lines.isEmpty()) {
			return;
		}
		assert this.slice != null || !this.lines.isEmpty();
		builder.setBreakToken(this.breakToken);
		// M3b Phase 1/2: 運搬体はスライス。分割断片は破断時に捕捉済み、
		// それ以外(全 restyle 経路)はここで捕捉する。捕捉→再生は
		// 構成的に同一の呼び出し列なので挙動不変
		final net.zamasoft.foliojet.layout.fragment.TextReplaySlice slice = this.slice != null ? this.slice
				: this.recordSlice();
		slice.replay(new BuilderGlyphHandler(builder));
	}

	/**
	 * 残余行の正規化イベント列を捕捉します(M3b Phase 1 / C3)。
	 * WordHyphenator 相当(unitizer)の出口で捕捉した、restyle が
	 * BuilderGlyphHandler へ配達するのと同一の列。
	 */
	private net.zamasoft.foliojet.layout.fragment.TextReplaySlice recordSlice() {
		return net.zamasoft.foliojet.layout.fragment.TextReplaySlice.record(gh -> {
			final FilterGlyphHandler textUnitizer = new CSSJTextUnitizer(this.params);
			textUnitizer.setGlyphHandler(gh);
			for (int i = 0; i < this.lines.size(); ++i) {
				final Line line = (Line) this.lines.get(i);
				line.box.restyle(textUnitizer, i == 0);
			}
			textUnitizer.close();
		});
	}

	public final boolean avoidBreakAfter() {
		return false;
	}

	public final boolean avoidBreakBefore() {
		return false;
	}

	public String toString() {
		return super.toString() + "/lineCount=" + this.lines.size();
	}
}
