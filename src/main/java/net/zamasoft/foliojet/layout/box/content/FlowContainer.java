package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.fragment.FlowCutter;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.Absolutes.Absolute;
import net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.box.content.Floatings.Floating;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Pos;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

public class FlowContainer implements Container {
	/**
	 * 通常のフローのコンテンツです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: FlowContainer.java 1631 2022-05-15 05:43:49Z miyabe $
	 */
	protected static class Flow extends BoxHolder {
		public final IFlowBox box;
		public final double pageAxis;

		public Flow(int serial, IFlowBox box, double pageAxis) {
			super(serial);
			this.box = box;
			this.pageAxis = pageAxis;
		}

		public IBox getBox() {
			return this.box;
		}
	}

	/**
	 * 吸収された閉部分木の再生範囲です(C1c)。ボックスを持たず、
	 * restyle 走行の serial 合流順にソース再駆動を発火させます。
	 */
	private static class Replay extends BoxHolder {
		final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range;

		Replay(net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range) {
			super(range.serial());
			this.range = range;
		}

		public IBox getBox() {
			return null;
		}
	}

	protected AbstractContainerBox box;

	protected int serial = 0;

	/**
	 * 通常のフローのコンテンツ。
	 */
	protected List<Flow> flows = null;

	protected Floatings floatings = null;

	protected Absolutes absolutes = null;

	public FlowContainer() {
		// default
	}

	public final void setBox(AbstractContainerBox box) {
		this.box = box;
	}

	public final void addFlow(IFlowBox box, double pageAxis) {
		assert box != null;
		this.addFlow(++this.serial, box, pageAxis);
	}

	private final void addFlow(int serial, IFlowBox box, double pageAxis) {
		assert box != null;
		Flow flow = new Flow(serial, box, pageAxis);
		if (this.flows == null) {
			this.flows = new ArrayList<Flow>();
		}
		this.flows.add(flow);
		this.adopt(box);
	}

	public final void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		this.addAbsolute(box, staticX, staticY, false);
	}

	@Override
	public final void addAbsolute(IAbsoluteBox box, double staticX, double staticY, boolean blockStartAnchored) {
		if (this.absolutes == null) {
			this.absolutes = new Absolutes();
		}
		this.absolutes.addAbsolute(box, staticX, staticY, blockStartAnchored);
		this.adopt(box);
	}

	public final void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		this.addFloating(box, lineAxis, pageAxis, false);
	}

	/** 配置時に確定した一回限りの次断片移送を伴ってfloatを保持します。 */
	public final void addFloating(IFloatBox box, double lineAxis, double pageAxis, boolean moveToNext) {
		if (this.floatings == null) {
			this.floatings = new Floatings();
		}
		final Floating floating = new Floating(++this.serial, box, lineAxis, pageAxis, moveToNext);
		this.floatings.addFloating(floating);
		this.adopt(box);
	}

	/**
	 * このコンテナが直接持つページ座標を{@code dy}だけ平行移動します。
	 * 通常フローはserialとボックスを保った新しい要素へ置き換え、浮動体は
	 * serial、行軸位置、{@code moveToNext}を保って作り直します。絶対配置は
	 * ページコンテナの書字方向に従う物理的な静的位置だけを動かします
	 * ({@link Absolutes#shiftPageAxis(double, WritingMode, java.util.Set)}参照)。
	 * いずれもリスト順を変えず、{@code keep}に含まれるボックスは元の要素と
	 * 座標をそのまま残します。
	 *
	 * @param dy   ページ軸方向の移動量
	 * @param keep 移動せず現在位置に留めるボックスの集合
	 */
	public final void shiftPageAxis(final double dy, final java.util.Set<IBox> keep) {
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow flow = this.flows.get(i);
				if (!keep.contains(flow.box)) {
					this.flows.set(i, new Flow(flow.serial, flow.box, flow.pageAxis + dy));
				}
			}
		}
		if (this.floatings != null) {
			this.floatings.shiftPageAxis(dy, keep);
		}
		if (this.absolutes != null) {
			this.absolutes.shiftPageAxis(dy, this.box.getBlockParams().flow, keep);
		}
	}

	/**
	 * このコンテナが直接持つ通常フローのページ軸終端の最大値を返します。
	 * リスト末尾が幾何上も最後とは限らないため、全要素を走査します。
	 *
	 * @param flow このコンテナのページ軸を決める書字方向
	 * @return 通常フローがなければ0、あればその終端の最大値
	 */
	public final double maxNormalFlowPageEnd(final WritingMode flow) {
		double pageEnd = 0;
		if (this.flows != null) {
			for (final Flow child : this.flows) {
				pageEnd = Math.max(pageEnd, child.pageAxis + child.box.getPageExtent(flow));
			}
		}
		return pageEnd;
	}

	/**
	 * このコンテナへ並列注として登録された浮動体のページ軸終端の最大値を
	 * 返します。通常floatやページフロートは対象に含めません。
	 *
	 * @param flow このコンテナのページ軸を決める書字方向
	 * @return 配置済み並列注がなければ0、あればその終端の最大値
	 */
	public final double maxPageMarginNotePageEnd(final WritingMode flow) {
		double pageEnd = 0;
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				if (floating.box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.PageMarginNotePos) {
					final double extent = Math.max(floating.box.getPageExtent(flow),
							floating.box.paintedPageExtent(flow));
					pageEnd = Math.max(pageEnd, floating.pageAxis + extent);
				}
			}
		}
		return pageEnd;
	}

	public boolean hasFlows() {
		return this.flows != null && !this.flows.isEmpty();
	}

	public final int getFlowCount() {
		return this.flows == null ? 0 : this.flows.size();
	}

	@Override
	public final boolean isFirstFlow(final IFlowBox box) {
		return this.flows != null && !this.flows.isEmpty() && this.flows.get(0).box == box;
	}

	/**
	 * 「装飾でない内容があるか」のメモ(2026-08-29)。
	 *
	 * <p>
	 * 改ページの分割は親から子へ1段ずつ降り、各段でこの問いを立てる。
	 * 素朴に部分木を歩くと深さの二乗になり、深さ5000の正当な文書で
	 * 1ページに120秒以上かかってテストハーネスの無進捗watchdogに
	 * 「ハング」と誤認されていた(実測: 深さ1000で18秒、その93%が
	 * この走査)。内容の追加・移動・除去をしたコンテナから
	 * {@link #invalidateNonDecorationContent()}で<b>祖先だけ</b>を
	 * 無効化する(箱は{@code AbstractBox.getContentParent()}で保持先を
	 * 覚えている)ので、ある段の分割が下の段のメモを捨てることはなく、
	 * 1段あたりO(1)になる。保持先を覚えられない箱(AbstractBoxでない
	 * 実装)を受けたときは全体の版を進めて安全側に倒す。
	 * </p>
	 */
	private boolean nonDecorationCached;
	private boolean nonDecorationResult;
	private long nonDecorationVersion;

	/**
	 * 保持先を覚えられない箱の変更に備えた全体の版。
	 *
	 * <p>
	 * プロセス全体で1つなので、変換が並列に走ると複数スレッドが進める。
	 * {@code volatile long}の{@code ++}は読み・足し・書きの3手で、同時に
	 * 進めると片方が失われ、古い版と一致した記憶が生き残る(2026-09-02の
	 * 設計レビューで指摘)。EPUBの項目を並列に組む前提として原子的にする。
	 * </p>
	 */
	private static final java.util.concurrent.atomic.AtomicLong STRUCTURE_VERSION = new java.util.concurrent.atomic.AtomicLong();

	/** このコンテナとその祖先のメモを捨てる。既に捨ててある祖先で止まる。 */
	public final void invalidateNonDecorationContent() {
		FlowContainer c = this;
		while (c != null && c.nonDecorationCached) {
			c.nonDecorationCached = false;
			c = c.box == null ? null : c.box.getContentParent();
		}
	}

	/** 箱をこのコンテナの内容として受け入れ、祖先のメモを捨てる。 */
	private void adopt(final IBox box) {
		if (box instanceof net.zamasoft.foliojet.layout.box.AbstractBox abstractBox) {
			abstractBox.setContentParent(this);
		} else {
			STRUCTURE_VERSION.incrementAndGet();
		}
		this.invalidateNonDecorationContent();
	}

	@Override
	public final boolean hasNonDecorationContent() {
		final long version = STRUCTURE_VERSION.get();
		if (this.nonDecorationCached && this.nonDecorationVersion == version) {
			return this.nonDecorationResult;
		}
		final boolean result = this.computeNonDecorationContent();
		this.nonDecorationVersion = version;
		this.nonDecorationResult = result;
		this.nonDecorationCached = true;
		return result;
	}

	private boolean computeNonDecorationContent() {
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				if (hasNonDecorationContent(this.flows.get(i).box)) {
					return true;
				}
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				if (hasNonDecorationContent(this.floatings.getFloating(i).box)) {
					return true;
				}
			}
		}
		if (this.absolutes != null) {
			for (int i = 0; i < this.absolutes.getCount(); ++i) {
				final IAbsoluteBox box = this.absolutes.getAbsolute(i).box;
				final net.zamasoft.foliojet.css.StructureElement element = box.getParams().element;
				final boolean generatedDecoration = element != null && element.elementKey() < 0
						&& ("before".equals(element.lName()) || "after".equals(element.lName()));
				if (!generatedDecoration && hasNonDecorationContent(box)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public final boolean hasNonDecorationContentExcludingFloatings(
			final java.util.Set<? extends IFloatBox> excluded) {
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				if (hasNonDecorationContent(this.flows.get(i).box)) {
					return true;
				}
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final IFloatBox floating = this.floatings.getFloating(i).box;
				if (!excluded.contains(floating) && hasNonDecorationContent(floating)) {
					return true;
				}
			}
		}
		if (this.absolutes != null) {
			for (int i = 0; i < this.absolutes.getCount(); ++i) {
				final IAbsoluteBox box = this.absolutes.getAbsolute(i).box;
				final net.zamasoft.foliojet.css.StructureElement element = box.getParams().element;
				final boolean generatedDecoration = element != null && element.elementKey() < 0
						&& ("before".equals(element.lName()) || "after".equals(element.lName()));
				if (!generatedDecoration && hasNonDecorationContent(box)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasNonDecorationContent(final IBox box) {
		// 空のTextBlockBoxは、通常の内容消失防止判定では「将来描くかも
		// しれない」ため paintsAnything()==true になる。しかし断片化済みの
		// 前半に行がないこと自体は確定しており、固定高を消費した実内容には
		// 数えない。Yahoo!ニュースのinline wrapperがこの形になる。
		if (box.getType() == BoxType.TEXT_BLOCK) {
			return LayoutUtils.compare(((TextBlockBox) box).getPageSize(), 0) > 0;
		}
		if (box.getType() != BoxType.BLOCK) {
			return box.paintsAnything();
		}
		// ブロック箱のpaintsAnything()は「枠が見える || 中身が描く」で、
		// 中身に装飾でない内容があれば中身は描く。だから
		// 「paintsAnything && (枠 || 中身の内容)」は「枠 || 中身の内容」に
		// 等しく、部分木を二度歩く必要はない(2026-08-29)
		final AbstractContainerBox containerBox = (AbstractContainerBox) box;
		return containerBox.getFrame().isVisible() || containerBox.getContainer().hasNonDecorationContent();
	}

	public final void migrateFlowsFrom(final int fromIndex, final Container dest, final double crossShift) {
		if (this.flows == null || fromIndex >= this.flows.size()) {
			return;
		}
		for (int i = fromIndex; i < this.flows.size(); ++i) {
			final Flow flow = this.flows.get(i);
			dest.addFlow(flow.box, flow.pageAxis - crossShift);
		}
		this.flows = fromIndex <= 0 ? null : new ArrayList<Flow>(this.flows.subList(0, fromIndex));
		this.invalidateNonDecorationContent();
	}

	public boolean hasFloatings() {
		return this.floatings != null && this.floatings.getCount() > 0;
	}

	public double getFirstAscent() {
		final Flow flow = this.getFirstFlow();
		if (flow == null) {
			return LayoutUtils.NONE;
		}

		double ascent;
		switch (flow.box.getType()) {
		case BLOCK: {
			AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
			double firstAscent = containerBox.getFirstAscent();
			if (LayoutUtils.isNone(firstAscent)) {
				return firstAscent;
			}
			ascent = firstAscent;
		}
			break;

		case TEXT_BLOCK: {
			TextBlockBox textBox = (TextBlockBox) flow.box;
			double firstAscent = textBox.getFirstAscent();
			ascent = firstAscent;
		}
			break;

		case RESCUE:
		case REPLACED:
		case TABLE:
			ascent = flow.box.getHeight();
			break;
		default:
			throw new IllegalStateException(String.valueOf(flow.box.getType()));
		}

		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			ascent += this.box.getFrame().getFrameTop();
			break;
		case WritingMode.RL:
			// 縦書き(モンゴル)
			ascent += this.box.getFrame().getFrameLeft();
			break;
		case WritingMode.LR:
			// 縦書き(日本)
			ascent += this.box.getFrame().getFrameRight();
			break;
		default:
			throw new IllegalStateException();
		}
		return ascent;
	}

	public double getLastDescent() {
		final Flow flow = this.getLastFlow();
		if (flow == null) {
			return LayoutUtils.NONE;
		}

		double descent;
		switch (flow.box.getType()) {
		case BLOCK: {
			final AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
			final double lastDescent = containerBox.getLastDescent();
			if (LayoutUtils.isNone(lastDescent)) {
				return lastDescent;
			}
			descent = lastDescent;
		}
			break;

		case TEXT_BLOCK: {
			final TextBlockBox textBox = (TextBlockBox) flow.box;
			double lastDescent = textBox.getLastDescent();
			descent = lastDescent;
		}
			break;

		case RESCUE:
		case REPLACED:
		case TABLE:
			descent = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			descent += this.box.getFrame().getFrameBottom();
			break;
		case WritingMode.RL:
			// 縦書き(日本)
			descent += this.box.getFrame().getFrameLeft();
			break;
		case WritingMode.LR:
			// 縦書き(モンゴル)
			descent += this.box.getFrame().getFrameRight();
			break;
		default:
			throw new IllegalStateException();
		}
		return descent;
	}

	public double getContentSize() {
		final Flow flow = this.getLastFlow();
		if (flow == null) {
			return 0;
		}
		return flow.pageAxis + flow.box.getPageExtent(this.box.getBlockParams().flow);
	}

	@Override
	public double getConsumedPageSizeForFragmentation() {
		final double contentSize = this.getContentSize();
		if (LayoutUtils.compare(contentSize, 0) <= 0 || this.flows == null || this.flows.isEmpty()) {
			return contentSize;
		}
		// 固定高wrapperが入れ子の場合、内側wrapperの内容が丸ごと次頁へ
		// 移っても、外側からは「空の前断片boxの高さ」がcontentSizeに見える。
		// これは実際に消費した空きではない。開始位置0にある、断片化済みで
		// 描画内容を持たない殻だけなら、継続高から差し引かない。
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow flow = this.flows.get(i);
			if (LayoutUtils.compare(flow.pageAxis, 0) > 0
					|| !(flow.box instanceof net.zamasoft.foliojet.layout.box.AbstractBox abstractBox)
					|| !abstractBox.isFragmented()
					|| hasNonDecorationContent(flow.box)) {
				return contentSize;
			}
		}
		return 0;
	}

	@Override
	public double balancePageSizeFloor() {
		if (this.flows == null) {
			return 0;
		}
		// 同軸逆進行(RL⇄LR)の子は改ページ契約でatomic——段境界で内部
		// 切断できないため、その全長より段容量を小さくしてはならない。
		// 従来は容量探索(getCutPointBelow)が逆進行の子の内部境界を
		// 返し、balance()が子より狭いmaxPageAxisを固定→再構築後の
		// contentSize(子の指定幅)が箱幅へ反映されず、親のカーソルも
		// 狭いまま→RL端寄せ配置で内容が紙面外に描かれた(2026-08-22、
		// 掃過seed 1871636/1106107)
		final WritingMode outer = this.box.getBlockParams().flow;
		double floor = 0;
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow f = (Flow) this.flows.get(i);
			if (f.box.getType() != BoxType.BLOCK) {
				continue;
			}
			final WritingMode inner = ((FlowBlockBox) f.box).getBlockParams().flow;
			if (outer.isVertical() && inner.isVertical()
					&& net.zamasoft.foliojet.layout.fragment.PaginationContract.isChainAtomicBoundary(outer,
							inner)) {
				floor = Math.max(floor, f.pageAxis + f.box.getPageExtent(outer));
			}
		}
		return floor;
	}

	public double paintedPageEnd() {
		if (this.absolutes != null) {
			// 絶対配置は静的位置と無関係に描かれうる。読み切れないので
			// 「箱いっぱいに描く」と見なす(安全側)
			return this.box.getInnerPageExtent(this.box.getBlockParams().flow);
		}
		final WritingMode flow = this.box.getBlockParams().flow;
		double end = 0;
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow f = (Flow) this.flows.get(i);
				end = Math.max(end, paintedEndOf(f.pageAxis, f.box, flow));
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				end = Math.max(end, paintedEndOf(floating.pageAxis, floating.box, flow));
			}
		}
		return end;
	}

	public boolean paintsAnything() {
		if (this.absolutes != null) {
			// 絶対配置は静的位置と無関係に描かれうる。読み切れないので
			// 「描く」と見なす(安全側)
			return true;
		}
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				if (((Flow) this.flows.get(i)).box.paintsAnything()) {
					return true;
				}
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				if (this.floatings.getFloating(i).box.paintsAnything()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 何も描かない子({@code paintedPageExtent==0})は<b>位置によらず0</b>を
	 * 寄与します——「ページの奥に置かれた、何も描かない箱」で
	 * {@link #paintedPageEnd()}が0でなくなるのを防ぎます。
	 */
	private static double paintedEndOf(final double pageAxis, final IBox box, final WritingMode flow) {
		final double extent = box.paintedPageExtent(flow);
		return LayoutUtils.compare(extent, 0) <= 0 ? 0 : pageAxis + extent;
	}

	public double getCutPoint(double pageAxis) {
		final WritingMode flow = this.box.getBlockParams().flow;
		if (this.hasFlows()) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow f = (Flow) this.flows.get(i);
				final double bottom = f.pageAxis + f.box.getPageExtent(flow);
				if (LayoutUtils.compare(bottom, pageAxis) >= 0) {
					if (f.box.getType() == BoxType.BLOCK) {
						final FlowBlockBox blockBox = (FlowBlockBox) f.box;
						if (blockBox.getBlockParams().pageBreakInside == PageBreakMode.AVOID) {
							pageAxis = bottom;
							break;
						}
						final AbsoluteRectFrame frame = blockBox.getFrame();
						pageAxis = f.pageAxis
								+ blockBox.getContainer()
										.getCutPoint(pageAxis - f.pageAxis - frame.getFramePageStart(flow))
								+ frame.getFramePageStart(flow) + frame.getFramePageEnd(flow);
					} else if (f.box.getType() == BoxType.TEXT_BLOCK) {
						pageAxis = f.pageAxis + ((TextBlockBox) f.box).getCutPoint(pageAxis - f.pageAxis);
					} else {
						pageAxis = bottom;
					}
					break;
				}
			}
		}
		if (this.hasFloatings()) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				final double bottom = floating.pageAxis + floating.box.getPageExtent(flow);
				if (LayoutUtils.compare(bottom, pageAxis) >= 0) {
					pageAxis = bottom;
					break;
				}
			}
		}
		return pageAxis;
	}

	/**
	 * 浮動体の行方向寸法の最大値を返します(M2c: 使用行寸法の読み取り用)。
	 */
	public double floatingsLineExtent(final WritingMode flow) {
		double max = 0;
		if (this.hasFloatings()) {
			final boolean vertical = flow.isVertical();
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				max = Math.max(max, vertical ? floating.box.getHeight() : floating.box.getWidth());
			}
		}
		return max;
	}

	public double getCutPointBelow(final double pageAxis) {
		final WritingMode flow = this.box.getBlockParams().flow;
		double result = 0;
		if (this.hasFlows()) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow f = (Flow) this.flows.get(i);
				final double bottom = f.pageAxis + f.box.getPageExtent(flow);
				if (LayoutUtils.compare(bottom, pageAxis) <= 0) {
					// 完全に手前に収まるフロー
					result = bottom;
					continue;
				}
				// 提案位置に跨るフロー: 内部の境界を探す
				if (f.box.getType() == BoxType.BLOCK) {
					final FlowBlockBox blockBox = (FlowBlockBox) f.box;
					if (blockBox.getBlockParams().pageBreakInside != PageBreakMode.AVOID) {
						final double frameStart = blockBox.getFrame().getFramePageStart(flow);
						final double inner = blockBox.getContainer()
								.getCutPointBelow(pageAxis - f.pageAxis - frameStart);
						if (LayoutUtils.compare(inner, 0) > 0) {
							result = Math.max(result, f.pageAxis + frameStart + inner);
						}
					}
					// 内部に境界がない場合はブロックの前(直前の result)で切る
				} else if (f.box.getType() == BoxType.TEXT_BLOCK) {
					final double inner = ((TextBlockBox) f.box).getCutPointBelow(pageAxis - f.pageAxis);
					if (LayoutUtils.compare(inner, 0) > 0) {
						result = Math.max(result, f.pageAxis + inner);
					}
				}
				break;
			}
		}
		if (this.hasFloatings()) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				final double top = floating.pageAxis;
				final double bottom = top + floating.box.getPageExtent(flow);
				if (LayoutUtils.compare(top, result) < 0 && LayoutUtils.compare(bottom, result) > 0) {
					// 切断位置に跨る浮動体の前まで引き下げる
					result = top;
				}
			}
		}
		return result;
	}

	public void eachFlowBox(final java.util.function.Consumer<IFlowBox> consumer) {
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				consumer.accept(((Flow) this.flows.get(i)).box);
			}
		}
	}

	@Override
	public void eachFloatingBox(final java.util.function.Consumer<IFloatBox> consumer) {
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				consumer.accept(this.floatings.getFloating(i).box);
			}
		}
	}

	@Override
	public void eachAbsoluteBox(final java.util.function.Consumer<IAbsoluteBox> consumer) {
		if (this.absolutes != null) {
			for (int i = 0; i < this.absolutes.getCount(); ++i) {
				consumer.accept(this.absolutes.getAbsolute(i).box);
			}
		}
	}

	protected Flow getFirstFlow() {
		if (this.flows == null || this.flows.isEmpty()) {
			return null;
		}
		return (Flow) this.flows.get(0);
	}

	protected Flow getLastFlow() {
		if (this.flows == null || this.flows.isEmpty()) {
			return null;
		}
		return (Flow) this.flows.get(this.flows.size() - 1);
	}

	/**
	 * {@code avoidBreakBefore}/{@code avoidBreakAfter}の反復化用ワーク
	 * リストの1フレームです(2026-07-20、ARCHITECTURE.md不変条件6)。
	 * ある{@code FlowContainer}の{@code flows}を末尾または先頭から順に
	 * 見ている状態を表します。{@code awaitingChild}は、現在の
	 * {@code index}の{@link FlowBlockBox}が持つ内部コンテナへ降りるため
	 * 子フレームをpushした直後で、その子フレームの解決(popされて
	 * このフレームへ戻ってきた=trueは見つからなかった)を待っている
	 * ことを表します。
	 */
	private static final class AvoidBreakFrame {
		final List<Flow> flows;
		int index;
		boolean awaitingChild;

		AvoidBreakFrame(List<Flow> flows, int index) {
			this.flows = flows;
			this.index = index;
		}
	}

	public boolean avoidBreakBefore() {
		return this.walkAvoidBreak(false);
	}

	public boolean avoidBreakAfter() {
		return this.walkAvoidBreak(true);
	}

	/**
	 * {@code avoidBreakBefore}/{@code avoidBreakAfter}の実装です。
	 *
	 * <p>
	 * 旧実装は{@code FlowContainer.avoidBreak{Before,After}()}が
	 * {@code flow.box.avoidBreak{Before,After}()}を呼び、それが
	 * {@link FlowBlockBox}であれば自分の内部コンテナ(通常は別の
	 * {@code FlowContainer})へ委譲する、というポリモーフィックな相互
	 * 再帰で、深いネスト文書(改ページを跨ぐ開いた祖先チェーン)で
	 * {@code StackOverflowError}を起こしていた(2026-07-20、
	 * {@code DeepNestingRestyleTest}で確認。restyle系の反復化に着手する
	 * 前に、この別系統の再帰も同じ不変条件6違反として発見された)。
	 * </p>
	 *
	 * <p>
	 * 本メソッドは、{@link FlowBlockBox}への降下だけを明示的
	 * {@link Deque}のワークリストへ置き換える(finishLayout等と同じ
	 * 反復DFSパターン)。{@link IFlowBox}の他の実装
	 * ({@link TableBox}・{@link TextBlockBox}・{@link net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox})
	 * と{@link Container}の他の実装({@link ColumnsContainer})はいずれも
	 * 末端(再帰しない)であることを確認済みのため、それらは直接呼び出す。
	 * </p>
	 */
	private boolean walkAvoidBreak(final boolean after) {
		if (this.flows == null || this.flows.isEmpty()) {
			return false;
		}
		final Deque<AvoidBreakFrame> stack = new ArrayDeque<AvoidBreakFrame>();
		stack.push(new AvoidBreakFrame(this.flows, after ? this.flows.size() - 1 : 0));
		while (!stack.isEmpty()) {
			final AvoidBreakFrame frame = stack.peek();
			if (frame.index < 0 || frame.index >= frame.flows.size()) {
				stack.pop();
				continue;
			}
			final Flow flow = frame.flows.get(frame.index);
			final IFlowBox box = flow.box;
			boolean result;
			if (frame.awaitingChild) {
				// 子コンテナへの降下から戻ってきた。子がtrueを見つけて
				// いれば、その時点で既にreturn trueしているため、ここに
				// 来るのはfalseで確定した場合のみ。
				frame.awaitingChild = false;
				result = false;
			} else if (box instanceof FlowBlockBox) {
				final FlowBlockBox flowBlockBox = (FlowBlockBox) box;
				final PageBreakMode mode = after ? flowBlockBox.getFlowPos().pageBreakAfter
						: flowBlockBox.getFlowPos().pageBreakBefore;
				if (mode == PageBreakMode.AVOID) {
					return true;
				}
				final Container inner = flowBlockBox.getContainer();
				if (inner instanceof FlowContainer) {
					final FlowContainer innerFlowContainer = (FlowContainer) inner;
					if (innerFlowContainer.flows != null && !innerFlowContainer.flows.isEmpty()) {
						// 子コンテナへ降りる。戻ってきたら上のawaitingChild
						// 分岐で続き(高さ判定・次の候補への移動)を処理する。
						frame.awaitingChild = true;
						stack.push(new AvoidBreakFrame(innerFlowContainer.flows,
								after ? innerFlowContainer.flows.size() - 1 : 0));
						continue;
					}
					result = false;
				} else {
					// ColumnsContainer等: 再帰しないことを確認済みの末端
					result = after ? inner.avoidBreakAfter() : inner.avoidBreakBefore();
				}
			} else {
				// TableBox/TextBlockBox/FlowReplacedBox: 再帰しない末端
				result = after ? box.avoidBreakAfter() : box.avoidBreakBefore();
			}
			if (result) {
				return true;
			}
			if (box.getHeight() > 0) {
				frame.index = after ? -1 : frame.flows.size();
			} else {
				frame.index += after ? -1 : 1;
			}
		}
		return false;
	}

	public void pushFinishLayoutChildren(IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		if (this.box.isContextBox()) {
			containerBox = (IFramedBox) this.box;
		}
		final IFramedBox childContainerBox = containerBox;
		// 元の走査順(flows→floatings→absolutes、各々先頭から)を保つため、
		// スタックへは逆順(absolutes→floatings→flows、各々末尾から)でpushする
		if (this.absolutes != null) {
			for (int i = this.absolutes.getCount() - 1; i >= 0; --i) {
				final Absolute c = this.absolutes.getAbsolute(i);
				worklist.push(IBox.step(c.box, childContainerBox));
			}
		}
		if (this.floatings != null) {
			for (int i = this.floatings.getCount() - 1; i >= 0; --i) {
				final Floating c = this.floatings.getFloating(i);
				worklist.push(IBox.step(c.box, childContainerBox));
			}
		}
		if (this.flows != null) {
			for (int i = this.flows.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flows.get(i);
				worklist.push(IBox.step(flow.box, childContainerBox));
			}
		}
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// 論理位置→物理座標の変換は LayoutUtils.drawX/drawY に集約する
		// (2026-07-25、vertical-lr対応。従来はここで RL 専用式を手書きしていた)
		final WritingMode flow = this.box.getBlockParams().flow;
		final double parentPageExtent = this.box.getInnerWidth();
		// 通常のフロー(元の走査順を保つため、スタックへは逆順でpushする)
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			final Flow c = (Flow) this.flows.get(i);
			final boolean rescued = isRescuedFrameOwner(c.box);
			if (!rescued && !(c.box.getType() == BoxType.BLOCK && ((FlowPos) c.box.getPos()).offset == null
					&& !c.box.getParams().isStackingContext())) {
				continue;
			}
			final double cx = LayoutUtils.drawX(flow, x, parentPageExtent, c.pageAxis,
					c.pageAxis + c.box.getWidth(), 0);
			final double cy = LayoutUtils.drawY(flow, y, c.pageAxis, 0);
			if (rescued) {
				// 2026-07-25(救済分割・増分6): ブロックを元にした断片は
				// 枠(背景・ボーダー)もこのフレームパスで描かれる
				((net.zamasoft.foliojet.layout.rescue.VisualRescueBox) c.box).pushSourceFramesSteps(pageBox, drawer,
						clip, transform, cx, cy, worklist);
			} else {
				worklist.push(AbstractContainerBox.framesStep((AbstractBlockBox) c.box, pageBox, drawer, clip,
						transform, cx, cy));
			}
		}
	}

	/**
	 * 救済断片のうち、枠(背景・ボーダー)をフレームパスで描くべきもので
	 * あればtrueを返します(2026-07-25、増分6)。
	 *
	 * <p>
	 * 判定は元ボックスの配置が通常フローの{@code offset == null}
	 * (=相対配置でない)かどうかで、非救済のブロックとまったく同じ条件
	 * です。テキストブロックを元にした断片は{@code FlowPos}を持たない
	 * ためここでfalseになり、置換要素を元にした断片は自分の描画で枠を
	 * 描くため{@code pushSourceFramesSteps}側で何も積みません。
	 * </p>
	 */
	private static boolean isRescuedFrameOwner(final IFlowBox box) {
		return box.getType() == BoxType.RESCUE && box.getPos() instanceof FlowPos flowPos && flowPos.offset == null;
	}

	public final void pushDrawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// 論理位置→物理座標は LayoutUtils.drawX/drawY に集約(2026-07-25)
		final WritingMode flow = this.box.getBlockParams().flow;
		final double parentPageExtent = this.box.getInnerWidth();
		// 通常のフロー(元の走査順を保つため、スタックへは逆順でpushする)
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			final Flow c = (Flow) this.flows.get(i);
			worklist.push(IBox.drawStep(c.box, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(flow, x, parentPageExtent, c.pageAxis, c.pageAxis + c.box.getWidth(), 0),
					LayoutUtils.drawY(flow, y, c.pageAxis, 0)));
		}
	}

	public final void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x,
			double y, Deque<TextShapeStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// 論理位置→物理座標は LayoutUtils.drawX/drawY に集約(2026-07-25)
		final WritingMode flow = this.box.getBlockParams().flow;
		final double parentPageExtent = this.box.getInnerWidth();
		// 通常のフロー(元の走査順を保つため、スタックへは逆順でpushする)
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			final Flow c = (Flow) this.flows.get(i);
			worklist.push(IBox.textShapeStep(c.box, pageBox, path, transform,
					LayoutUtils.drawX(flow, x, parentPageExtent, c.pageAxis, c.pageAxis + c.box.getWidth(), 0),
					LayoutUtils.drawY(flow, y, c.pageAxis, 0)));
		}
	}

	public final void pushDrawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.floatings == null) {
			return;
		}
		this.floatings.pushDraw(this.box, pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				worklist);
	}

	public final void pushDrawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.absolutes == null) {
			return;
		}
		this.absolutes.pushDraw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				this.box.getInnerWidth(), worklist);
	}

	/**
	 * 自動改ページ主ループの1回の切断試行で観測した結果です
	 * (二相分離・増分2、2026-08-01)。従来のIFlowBox sentinel
	 * (null=Keep、元ボックスidentity=Move、その他=Split残余)を型に
	 * 置換した。「Probe(検分)」であって最終配置ではない——Keepは
	 * 牽引(i&lt;lastOrphan)でMoveへ変換されうるし、pushback巻き戻しで
	 * 同じフローが2回検分されうる。Frame(チェーン継続)は即時terminal
	 * のため型に含めない。
	 */
	private sealed interface ProbeOutcome {
		enum MoveReason {
			NORMAL,
			MONOLITHIC_AVOID,
			UNFULFILLABLE_AVOID
		}

		/** ボックス全体をthis側に残す(暫定)。 */
		record Keep() implements ProbeOutcome {
		}

		/** ボックス全体を次断片へ送る。 */
		record Move(MoveReason reason) implements ProbeOutcome {
		}

		/** 切断され、残余を次断片へ送る(変異済み先頭はthis側に残る)。 */
		record Split(IFlowBox remainder) implements ProbeOutcome {
		}

		ProbeOutcome KEEP = new Keep();
		Move MOVE = new Move(MoveReason.NORMAL);
		Move MONOLITHIC_AVOID_MOVE = new Move(MoveReason.MONOLITHIC_AVOID);
		Move UNFULFILLABLE_AVOID_MOVE = new Move(MoveReason.UNFULFILLABLE_AVOID);
	}

	/**
	 * 継続化計画付きのページ方向切断です(C1d-C)。単一実装(旧3引数版
	 * =Plain写像のwrapperは増分5で撤去し、呼び出し側がPlainを直接
	 * 剥がす)。plan が選択したチェーンメンバー(常に末尾フロー)の
	 * 断片は WithFrame の返り値で親へ伝播する。
	 */
	public net.zamasoft.foliojet.layout.fragment.ContainerCut splitPageAxis(double pageLimit, final BreakMode mode,
			final byte flags, final net.zamasoft.foliojet.layout.fragment.BreakPlan plan) {
		final boolean vertical = this.box.getBlockParams().flow.isVertical();
		final double frameStart = this.box.getFrame().getFramePageStart(this.box.getBlockParams().flow);
		final double pageSize = this.box.getPageExtent(this.box.getBlockParams().flow);
		final double pageInnerSize = this.box.getInnerPageExtent(this.box.getBlockParams().flow);

		// System.err.println("ACB A: flags=" + flags + "/" + mode +
		// "/pageLimit=" + pageLimit + "/vertical="+vertical+"/pageInnerSize=" +
		// pageInnerSize
		// + "/flows.size=" + (this.flows == null ? 0 : this.flows.size())
		// + "/" + this.box.getParams().element);
		if (mode instanceof BreakMode.ForceBreakMode) {
			// 強制改ページが指定されている場合
			FlowContainer nextBox;
			ForceBreakMode force = (ForceBreakMode) mode;
			int index;
			net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame chainFrame = null;
			boolean moved = false;
			net.zamasoft.foliojet.layout.fragment.ChainStopReason chainStopReason = null;
			nextBox = new FlowContainer();
			if (this.box != force.box) {
				index = this.flows.size() - 1;
				byte lflags = (byte) 0xFF;
				if (index != 0) {
					lflags ^= IPageBreakableBox.FLAGS_FIRST;
				}
				if (index != this.flows.size() - 1) {
					lflags ^= IPageBreakableBox.FLAGS_LAST;
				}
				Flow flow = (Flow) this.flows.get(index);
				if (plan != null && plan.selects(flow.box)) {
					// C1d-C: チェーンメンバーの継続化。断片はボックスではなく
					// フレームとして返り値で親へ伝播する
					switch (((AbstractBlockBox) flow.box).splitForContinuation(pageLimit - flow.pageAxis, mode,
							(byte) (lflags & flags), plan)) {
					case SplitResult.Frame(
							final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f) ->
						chainFrame = f;
					case SplitResult.Split(final IPageBreakableBox remainder) -> throw new IllegalStateException(
							"チェーンメンバーは Split を返さない");
					case SplitResult.Keep keep -> {
						// 継続化不成立(chainFrame は null のまま)。box 全体を
						// this 側に残す — 720行目の chainFrame==null 分岐が
						// plain(nextBox) へ自然にフォールバックする
						chainStopReason = net.zamasoft.foliojet.layout.fragment.ChainStopReason.KEEP;
					}
					case SplitResult.Move move -> {
						// box全体をnextBox側へ送る。自動改ページ主ループ
						// (942-950行目)と同様、this.flows側からも除去
						// しないと同一boxが前後ページに二重に残ってしまう
						// (除去自体は下のsplitFloatings呼び出しの後——
						// そちらがthis.flowsの元のサイズを前提にしている)
						nextBox.addFlow(flow.serial, flow.box, 0);
						moved = true;
						chainStopReason = net.zamasoft.foliojet.layout.fragment.ChainStopReason.MOVE;
					}
					}
				} else {
					IPageBreakableBox flowBox = (IPageBreakableBox) flow.box;
					final SplitResult forceResult = flowBox.split(pageLimit - flow.pageAxis, mode,
							(byte) (lflags & flags));
					switch (forceResult) {
					case SplitResult.Split(final IPageBreakableBox remainder) -> nextBox.addFlow(flow.serial,
							(IFlowBox) remainder, 0);
					case SplitResult.Frame frame -> throw new IllegalStateException("継続化は plan の選択なしには起きない");
					case SplitResult.Keep keep -> {
						// box 全体を this 側に残す(nextBox には何も加えない)
					}
					case SplitResult.Move move -> {
						// 同上: this.flows側からも除去する(除去はsplitFloatings
						// 呼び出しの後)
						nextBox.addFlow(flow.serial, flow.box, 0);
						moved = true;
					}
					}
				}
			} else {
				index = this.flows == null ? 0 : this.flows.size();
			}
			final FloatAggregate aggregate = this.aggregateFloatings(pageLimit, flags, index);
			if (moved) {
				// aggregateFloatings(pageLimit, flags, index) は呼び出し時点の
				// this.flows.size()==index+1 を前提に0..index-1を走査する
				// ため、除去はその呼び出しの後に行う(先に除去すると
				// FLAGS_LAST判定がずれる)
				this.flows.remove(index);
				this.invalidateNonDecorationContent();
			}
			this.attachAggregate(nextBox, aggregate);
			assert nextBox != null;
			assert nextBox != this;
			if (chainFrame != null) {
				return new net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(nextBox, chainFrame);
			}
			return chainStopReason != null
					? new net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(nextBox,
							chainStopReason)
					: plain(nextBox);
		}

		final double prevPageSize = pageLimit;
		// 主ループ前の判定は FlowCutter に純化されている(M4-A2)
		final FlowCutter.PreDecision pre = FlowCutter.preDecide(pageLimit, pageSize, pageInnerSize, frameStart, flags,
				this.flows != null && !this.flows.isEmpty());
		// **開いたままの末尾フローがあるなら「このページに残す」を選べない**
		// (2026-08-03)。planが選んでいる末尾フローは、まだ組み立て中で
		// 開いているフロー(継続チェーンの一員)である。ここで
		// KeepFloats(=所有者はこのページに残し、溢れた浮動体だけ送る)を
		// 選ぶと、そのフローは次のページに存在しなくなるのに文書としては
		// まだ閉じていない——再開後の流し込みスタックが継続の深さより
		// 浅くなり、ContinuationInvariantViolationException になる。
		//
		// 起きるのは「本文は尽きたが、ページフロートや脚注の都合で改ページが
		// 要る」とき(このとき残り高さは0になる)。実際に
		// files/fuzz-repro/flowstack-depth-pagefloat-footnote.html の2回目の
		// 改ページがこれで、vertical-lr + float:top + float:footnote +
		// float:left の4つが揃ったときだけ再現した。
		final boolean openTailSelected = plan != null && this.flows != null && !this.flows.isEmpty()
				&& plan.selects(((Flow) this.flows.get(this.flows.size() - 1)).box);
		if (openTailSelected && pre instanceof FlowCutter.PreDecision.KeepFloats(final double keepLimit)) {
			pageLimit = keepLimit;
		} else {
			if (!(pre instanceof FlowCutter.PreDecision.Proceed(final double adjustedPageLimit))) {
				return plain(switch (pre) {
				case FlowCutter.PreDecision.CutHead(final double atLimit) -> this.cutHead(atLimit, flags);
				case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this
						.splitFloatingsKeepingOwner(atLimit, flags);
				case FlowCutter.PreDecision.MoveAll moveAll -> this;
				case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this
						.splitFloatingsMovingOwner(atLimit, flags);
				case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
				case FlowCutter.PreDecision.Proceed proceed -> throw new IllegalStateException();
				});
			}
			pageLimit = adjustedPageLimit;
		}

		// 通常のフローで指定位置にさしかかっているボックスを特定
		final BlockParams params = this.box.getBlockParams();
		final double[] flowBottoms = this.computeFlowBottoms(params);
		int lastOrphan = FlowCutter.lastOrphan(flowBottoms, pageLimit);

		// FlowCutter へ渡す純データ(avoid 押し戻し・後段判定用の計測)
		final FlowMeasurements flowMeasurements = this.measureFlows(params);
		final double[] flowPageStarts = flowMeasurements.pageStarts();
		final double[] flowPageExtents = flowMeasurements.pageExtents();
		final boolean[] avoidBefore = flowMeasurements.avoidBefore();
		final boolean[] avoidAfter = flowMeasurements.avoidAfter();
		final double[] flowPageEndFrames = flowMeasurements.pageEndFrames();
		final FloatMeasurements floatMeasurements = this.measureFloats(params);
		final double[] floatPageStarts = floatMeasurements.pageStarts();
		final double[] floatPageExtents = floatMeasurements.pageExtents();
		final boolean[] floatUncut = floatMeasurements.uncut();

		if (lastOrphan == this.flows.size()) {
			// 切断線以下のフローがない場合
			//
			// **開いたままの末尾フローは、動かす内容が無くても継続させる**
			// (2026-08-03)。planが選んでいる末尾フローは「まだ組み立て中で
			// 開いている」フロー(継続チェーンの一員)である。ここで
			// 「前のページに残す」と、そのフローは次のページに存在しなく
			// なるのに、文書としてはまだ閉じていない——再開後の流し込み
			// スタックが継続の深さより浅くなり、
			// ContinuationInvariantViolationException になる。
			//
			// 起きるのは「本文は尽きたが、ページフロートや脚注の都合で
			// 改ページが要る」ときで、実際に
			// files/fuzz-repro/flowstack-depth-pagefloat-footnote.html の
			// 2回目の改ページがこれだった(vertical-lr + float:top +
			// float:footnote + float:left の組み合わせ)。
			if ((flags & IPageBreakableBox.FLAGS_LAST) == 0 && !openTailSelected) {
				if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0 || (flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
					return plain(this.cutTail(prevPageSize, flags));
				}
				final double contentHeight = flowPageStarts[this.flows.size() - 1]
						+ flowPageExtents[this.flows.size() - 1];
				if (LayoutUtils.compare(pageInnerSize, contentHeight) > 0) {
					// 自然の高さより高いボックスは切断
					return plain(this.cutTail(prevPageSize, flags));
				}
				// 前のページに残す
				return plain(this.splitFloatingsKeepingOwner(prevPageSize, flags));
			}
			lastOrphan = this.flows.size() - 1;
		}

		FlowContainer nextBox = null;
		boolean ignoreAvoid = false;
		int relaxInsideIndex = -1;
		double savePageLimit = pageLimit;
		// B5c-2 Step3(自動改ページ主ループ、2026-07-22再挑戦): plan選択
		// 済みチェーンメンバー(常にthis.flowsの末尾)が一度でも検分され
		// たかどうか。チェーンメンバー自身のKeep/Moveの生値はここでは
		// 使わない(pushback巻き戻しで2回検分されうる非純粋呼び出しの
		// ため、switch直後の値は最終配置と食い違いうると判明——詳細は
		// docs/history/2026-07-22-open-chain-b5c2-autoloop-root-cause.md
		// 参照)。かわりに、コンテナ全体の結論が確定する「nextBox+
		// splitFloatings」の最終returnでのみ、その時点で確定している
		// 事実(nextBoxがチェーンメンバー単体だけを含むか)から
		// PlainWithChainStopを付与するか判断する(force-branchと同型の
		// 状況に限定——複数flowが絡む部分継続は単純な二値では表現できない
		// ため、それ以外はplain()のまま、既存のcontainer-identity比較
		// フォールバックに委ねる)。
		boolean sawChainMember = false;
		// 上から下へチェックする
		for (int i = lastOrphan; i < this.flows.size(); ++i) {
			Flow prevFlow = (Flow) this.flows.get(i);
			// フラグ計算は FlowCutter に純化(二相分離・増分1、2026-08-01)
			final FlowCutter.StepFlags step = FlowCutter.stepFlags(pageLimit, prevFlow.pageAxis, i, this.flows.size(),
					((AutoBreakMode) mode).box == this.box, flags);
			final double splitLine = step.splitLine();
			final byte lflags = step.positionMask();
			final byte xflags = step.splitFlags();

			// System.err.println("M: xflags=" + xflags + "/flags=" + flags
			// + "/flows.size=" + this.flows.size() + "/i=" + i
			// + "/this==box=" + (((AutoBreakMode) mode).box == this)
			// + "/this.box=" + this.box.getParams().element
			// + "/prevFlow=" + prevFlow.box.getParams().element);

			final boolean monolithicAvoid;
			final boolean unfulfillableAvoid;
			if (prevFlow.box.getType() == BoxType.BLOCK) {
				final BlockParams childParams = ((AbstractContainerBox) prevFlow.box).getBlockParams();
				monolithicAvoid = childParams.pageBreakInside == PageBreakMode.AVOID;
				unfulfillableAvoid = monolithicAvoid
						&& mode instanceof BreakMode.AutoBreakMode auto && auto.fragmentCapacity > 0
						&& LayoutUtils.compare(prevFlow.box.getPageExtent(this.box.getBlockParams().flow),
								auto.fragmentCapacity) > 0;
			} else {
				monolithicAvoid = false;
				unfulfillableAvoid = false;
			}
			final ProbeOutcome.Move moveOutcome = unfulfillableAvoid ? ProbeOutcome.UNFULFILLABLE_AVOID_MOVE
					: monolithicAvoid ? ProbeOutcome.MONOLITHIC_AVOID_MOVE : ProbeOutcome.MOVE;
			ProbeOutcome outcome;
			switch (prevFlow.box.getType()) {
			case TABLE:
			case TEXT_BLOCK: {
				// 2026-07-25(救済分割・増分6): 巨大な行。答申§1のとおり
				// TextBlockBox.split()を呼ぶ**前に**行の物理下端を検査する。
				// LineCutterはフラグメント先頭で実質1行しかなければ無条件に
				// KEEPを返す(=行分割では前進しない)ため、切断結果からは
				// その非進行を区別できないからである。巨大フォント・背の
				// 高いインラインブロック・インラインテーブル・ルビ単位・
				// インライン置換要素は、すべて「背の高い1行」としてこの
				// 一点で捕捉される——個別の分岐は作らない
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						&& prevFlow.box instanceof net.zamasoft.foliojet.layout.box.impl.TextBlockBox textBlock) {
					final double unbreakableEnd = textBlock.getUnbreakableLinePageEnd();
					if (!LayoutUtils.isNone(unbreakableEnd) && LayoutUtils.compare(splitLine, unbreakableEnd) < 0) {
						final IFlowBox rescued = this.rescueSplit(i, prevFlow, splitLine, prevPageSize);
						if (rescued != null) {
							// rescueSplitは成功時にthis.flows[i]を先頭断片へ
							// 置換済み——残余tailはSplitの残余と同じ扱い
							outcome = new ProbeOutcome.Split(rescued);
							break;
						}
					}
				}
				IPageBreakableBox prevFlowBox = (IPageBreakableBox) prevFlow.box;
				outcome = switch (prevFlowBox.split(splitLine, mode, xflags)) {
				case SplitResult.Keep keep -> ProbeOutcome.KEEP;
				case SplitResult.Move move -> moveOutcome;
				case SplitResult.Split(final IPageBreakableBox remainder) -> new ProbeOutcome.Split(
						(IFlowBox) remainder);
				case SplitResult.Frame frame -> throw new IllegalStateException(
						"チェーン継続は表・テキストでは起きない");
				};
			}
				break;
			case BLOCK:
				BlockParams cParams = ((AbstractContainerBox) prevFlow.box).getBlockParams();
				// **フラグメンテナ丸ごとでも収まらないavoidは履行不能**
				// (2026-08-20、css-break)。送っても結局内部で切ることになり、
				// 送り元のページに大きな空白だけが残る(w3c-jlreqの
				// 二重言語の巨大figure——版面756ptに対し760pt超——で実測)。
				// 同軸ならその場で内部を切り、直交フローのMoveには理由を残す
				// 改ページ禁止でかつページの頭でない場合(§5.11)、または軸が
				// 食い違う場合(PaginationContract.splitsInPageAxis=false、
				// §5.10ルール3)は内部で改ページせずREPLACEDと同じatomic経路へ
				if ((cParams.pageBreakInside != PageBreakMode.AVOID || (xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						|| unfulfillableAvoid)
						&& net.zamasoft.foliojet.layout.fragment.PaginationContract.splitsInPageAxis(vertical,
								(AbstractContainerBox) prevFlow.box)) {
					if (plan != null && plan.selects(prevFlow.box)) {
						// C1d-C: チェーンメンバーの継続化。断片はボックスでは
						// なくフレームとして返り値で親へ伝播する
						// (チェーン子は常に末尾のため後続フローの移送はない)
						if (i != this.flows.size() - 1) {
							throw new IllegalStateException("continuation frame child is not the open-tail flow");
						}
						sawChainMember = true;
						switch (((AbstractBlockBox) prevFlow.box).splitForContinuation(splitLine, mode, xflags,
								plan)) {
						case SplitResult.Keep keep -> outcome = ProbeOutcome.KEEP;
						case SplitResult.Move move -> outcome = moveOutcome;
						case SplitResult.Frame(
								final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f) -> {
							// Existing指定では結果は常にcollectedNext自身
							// (移動があれば台帳が装着される)——旧APIの
							// 返り値(=nextBox)と同じ
							final FlowContainer collectedNext = new FlowContainer();
							this.splitFloatings(new FloatTransferTarget.Existing(collectedNext), prevPageSize, flags);
							return new net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(collectedNext, f);
						}
						case SplitResult.Split(final IPageBreakableBox remainder) -> throw new IllegalStateException(
								"チェーンメンバーは Split を返さない");
						}
						break;
					}
					IPageBreakableBox prevFlowBox = (IPageBreakableBox) prevFlow.box;
					switch (prevFlowBox.split(splitLine, mode, xflags)) {
					case SplitResult.Keep keep -> outcome = ProbeOutcome.KEEP;
					case SplitResult.Move move -> outcome = moveOutcome;
					case SplitResult.Split(final IPageBreakableBox remainder) -> outcome = new ProbeOutcome.Split(
							(IFlowBox) remainder);
					case SplitResult.Frame frame -> throw new IllegalStateException("継続化は plan の選択なしには起きない");
					}
					break;
				}
				if ((xflags & IPageBreakableBox.FLAGS_LAST) != 0) {
					// 末尾の場合、改ページ禁止は必ず送る
					outcome = moveOutcome;
					break;
				}
			case RESCUE:
				// 2026-07-25(救済分割・増分5): 救済断片の続き。断片は
				// 「元ボックスの残余」を表す不可分な箱なので、置換要素と
				// まったく同じ判定でよい(先頭なら再度救済、収まるなら
				// そのまま残す、途中なら丸ごと次フラグメンテナへ)
			case REPLACED: {
				// 置換されたボックス
				double prevFlowPageSize = prevFlow.box.getPageExtent(this.box.getBlockParams().flow);
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						|| LayoutUtils.compare(splitLine, prevFlowPageSize) >= 0) {
					// ページの先頭にある場合、ページ下辺にかかっていない場合は残す
					if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
							&& LayoutUtils.compare(splitLine, prevFlowPageSize) < 0) {
						// 2026-07-25(救済分割・増分4/5): 「フラグメント先頭・
						// 分割不能・なお超過」——現在はここで「はみ出したまま
						// 描画」に落ちる唯一の非進行点(答申§1)
						// 容量の基準は「調整前の切断線」= このコンテナの
						// 始端からフラグメンテナ終端までの距離。コンテナ
						// 自身の内寸(pageInnerSize)は自動高さだと内容に
						// つれて伸びるため基準にならない
						final IFlowBox rescued = this.rescueSplit(i, prevFlow, splitLine, prevPageSize);
						if (rescued != null) {
							outcome = new ProbeOutcome.Split(rescued);
							break;
						}
					}
					outcome = ProbeOutcome.KEEP;
				} else {
					// 次ページに送る
					outcome = moveOutcome;
				}
			}
				break;
			default:
				throw new IllegalStateException(prevFlow.box.toString());
			}

			// System.err.println("ACB H: leave=" + (outcome instanceof ProbeOutcome.Keep)
			// + "/pass=" + (outcome instanceof ProbeOutcome.Move) + "/i=" + i
			// + "/lastOrphan="+lastOrphan+ "/xflags="+xflags+"/" +
			// this.box.getParams().element);
			if (outcome instanceof ProbeOutcome.Keep) {
				// Keepの解決規則はFlowCutterに純化(二相分離・増分3)。
				// TREAT_AS_MOVE=牽引によるMove化はProbeが最終配置でない代表例
				switch (FlowCutter.resolveKeep(i, lastOrphan, xflags)) {
				case KEEP_ALL:
					return plain(null);
				case EXAMINE_NEXT:
					continue;
				case TREAT_AS_MOVE:
					outcome = ProbeOutcome.MOVE;
					break;
				}
			}
			if (outcome instanceof ProbeOutcome.Move move) {
				if (move.reason() == ProbeOutcome.MoveReason.UNFULFILLABLE_AVOID) {
					relaxInsideIndex = Math.max(relaxInsideIndex, i);
				}
				// 分割不可能な場合。解決規則はFlowCutterに純化(二相分離・
				// 増分4)——ここは決定の適用だけを行う
				final FlowCutter.MoveResolution resolution = FlowCutter.resolveMove(lflags, flags, i, lastOrphan,
						ignoreAvoid, relaxInsideIndex, prevPageSize, pageLimit, ((AutoBreakMode) mode).fragmentCapacity,
						flowPageStarts, flowPageExtents, avoidBefore, avoidAfter,
						flowPageEndFrames, floatPageStarts, floatPageExtents, floatUncut);
				switch (resolution) {
				case FlowCutter.MoveResolution.Terminal(final FlowCutter.PreDecision action):
					// **開いたままの末尾フローは前ページに置き去りにできない**
					// (2026-08-21、掃過seed 46342ほか30件)。CutTail/KeepFloats
					// はどちらも「フローはこのページに残し、浮動体だけ次へ送る」
					// 決定である(cutTailは空のnextBoxへ浮動体台帳を移すだけで
					// フローを1つも移さない)。planが選んでいる末尾フローは
					// まだ組み立て中で開いており、次ページに存在しなくなると
					// 再開後の流し込みスタックが継続の深さより浅くなって
					// ContinuationInvariantViolationExceptionになる。
					// 2026-08-03にpreDecide側の同じ穴は塞いだが、主ループの
					// resolveMove→Terminalが残っていた。開いた末尾があるときは
					// ownerごと次ページへ送って継続させる
					if (openTailSelected && (action instanceof FlowCutter.PreDecision.CutTail
							|| action instanceof FlowCutter.PreDecision.KeepFloats)) {
						// コンテナごと次ページへ送る(MoveAll相当)。
						// splitFloatingsMovingOwnerはRemainder時に空コンテナを
						// 返しフローを落とすため使えない(実測)
						return plain(this);
					}
					return plain(switch (action) {
					case FlowCutter.PreDecision.CutHead(final double atLimit) -> this.cutHead(atLimit, flags);
					case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
					case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this
							.splitFloatingsKeepingOwner(atLimit, flags);
					case FlowCutter.PreDecision.MoveAll moveAll -> this;
					default -> throw new IllegalStateException(String.valueOf(action));
					});
				case FlowCutter.MoveResolution.RestartIgnoringAvoid(final int nextIndex):
					// 改ページ禁止を無視して再走(切断線は再開用値へ巻き戻す)
					pageLimit = savePageLimit;
					i = nextIndex - 1; // forの++i前提
					ignoreAvoid = true;
					continue;
				case FlowCutter.MoveResolution.RelaxInside(final int index, final int fallbackIndex): {
					// 境界avoidは保ったまま、末尾のモノリシックなボックスだけを
					// 幾何分割する。target.pageAxisには新しいfragmentainer上で
					// 先行する見出しが消費した量が含まれるため、先頭からの容量
					// ではなく見出し後の残量で切る
					final Flow target = this.flows.get(index);
					final double available = savePageLimit - target.pageAxis;
					final IFlowBox rescued = this.rescueSplit(index, target, available,
							((AutoBreakMode) mode).fragmentCapacity, false, true);
					if (rescued != null) {
						nextBox = this.applyPartition(index, new ProbeOutcome.Split(rescued));
						break;
					}
					// 有用な断片を作れない場合は従来どおり境界avoidを緩和する
					pageLimit = savePageLimit;
					i = fallbackIndex - 1;
					ignoreAvoid = true;
					continue;
				}
				case FlowCutter.MoveResolution.Pushback(final int resumeIndex, final double newPageLimit):
					// ブロック間の改ページ禁止の場合
					i = resumeIndex;
					pageLimit = newPageLimit;
					continue;
				case FlowCutter.MoveResolution.Partition partition:
					nextBox = this.applyPartition(i, outcome);
					break;
				}
			} else {
				nextBox = this.applyPartition(i, outcome);
			}
			break;
		}

		if (nextBox == null) {
			// ブロックを残す(末尾のブロックを残すことはない)。判定は FlowCutter に純化
			assert !((flags & IPageBreakableBox.FLAGS_LAST) != 0 && ((AutoBreakMode) mode).box != this.box);
			final double lastFlowBottom = flowPageStarts[this.flows.size() - 1]
					+ flowPageExtents[this.flows.size() - 1];
			final FlowCutter.PreDecision tailAction = FlowCutter.tailDecide(flags, lastOrphan, pageInnerSize, lastFlowBottom, prevPageSize);
			return plain(switch (tailAction) {
			case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
			case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this.splitFloatingsKeepingOwner(atLimit,
					flags);
			case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this
					.splitFloatingsMovingOwner(atLimit, flags);
			default -> throw new IllegalStateException();
			});
		}

		// nextBoxがチェーンメンバー一つだけを含む(=force-branchと同型の
		// 「識別では判別できない素の全体move」)場合に限り
		// PlainWithChainStopを付与する。複数flowが絡む場合(pushback巻き
		// 戻しで途中の兄弟が実際の切断点になったケース等)は既存の
		// container-identity比較へ安全にフォールバックさせる
		final boolean chainMemberAlone = sawChainMember && nextBox.flows != null && nextBox.flows.size() == 1;
		// Existing指定では結果は常にnextBox自身(移動があれば台帳が装着
		// される)——旧APIの返り値(=nextBox)と同じ
		this.splitFloatings(new FloatTransferTarget.Existing(nextBox), prevPageSize, flags);
		final Container splitResult = nextBox;
		return chainMemberAlone
				? new net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(splitResult,
						net.zamasoft.foliojet.layout.fragment.ChainStopReason.MOVE)
				: plain(splitResult);
	}

	private static net.zamasoft.foliojet.layout.fragment.ContainerCut plain(final Container container) {
		return new net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain(container);
	}

	/**
	 * 主ループの結論を実際のフロー移送として適用する唯一のcommit地点です
	 * (二相分離・増分6、2026-08-01)。
	 *
	 * <p>
	 * Move: 現在のフローを<b>含めて</b>後続を次断片へ送る(B3b-2の
	 * 「Moveなのに元側から除去せず二重描画」の再発をここで構造的に防ぐ)。
	 * Split: 変異済み先頭はthis側に残し、残余+後続を送る。
	 * </p>
	 */
	private FlowContainer applyPartition(final int index, final ProbeOutcome outcome) {
		final FlowContainer nextBox = new FlowContainer();
		final int from;
		if (outcome instanceof ProbeOutcome.Split(final IFlowBox remainder)) {
			nextBox.addFlow(remainder, 0);
			from = index + 1;
		} else {
			assert outcome instanceof ProbeOutcome.Move : outcome;
			nextBox.flows = new ArrayList<Flow>();
			from = index;
		}
		for (int j = from; j < this.flows.size(); ++j) {
			nextBox.flows.add(this.flows.get(j));
			nextBox.adopt(this.flows.get(j).box);
		}
		for (int j = this.flows.size() - 1; j >= from; --j) {
			this.flows.remove(j);
		}
		this.invalidateNonDecorationContent();
		assert this.flows.size() == from : this.flows.size() + "/" + from;
		return nextBox;
	}

	/**
	 * 救済分割(visual rescue split)の、通常フローにおける唯一の差し込み
	 * 地点です(2026-07-25新設。仕様と設計判断の根拠は
	 * {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}の
	 * クラス説明に集約しています)。
	 *
	 * <p>
	 * 呼ばれるのは「フラグメント先頭・分割不能・なお超過」という
	 * <b>非進行点</b>——現在「はみ出したまま描画」に落ちる唯一の地点——
	 * だけです。通常経路(収まる/一度の延期で収まる)は一切通りません。
	 * </p>
	 *
	 * <p>
	 * 断片の運搬は<b>既存の残余運搬機構にそのまま乗ります</b>:
	 * {@code this.flows}の当該要素を先頭断片(head)へ差し替え、残余断片
	 * (tail)を戻り値として返すと、呼び出し側の
	 * {@code SplitResult.Split(remainder)}と同じ経路で次フラグメンテナの
	 * コンテナへ載ります。答申§2の「全断片を先に作らず、各改ページで
	 * head一個とtail一個だけ作る」がそのまま実現されます。
	 * </p>
	 *
	 * @param index     {@code this.flows}での位置(head へ差し替える)
	 * @param prevFlow  非進行点に到達したフロー
	 * @param available このフラグメンテナで使えるページ方向の量
	 * @param capacity  フラグメンテナのページ方向内寸(極小断片の判定用)
	 * @return 次フラグメンテナへ送る残余断片。救済しないなら{@code null}
	 */
	private IFlowBox rescueSplit(final int index, final Flow prevFlow, final double available, final double capacity) {
		return this.rescueSplit(index, prevFlow, available, capacity, true, false);
	}

	/**
	 * 通常のフラグメント先頭判定と、履行不能なavoidだけに許す例外を
	 * 明示して救済分割します。
	 */
	private IFlowBox rescueSplit(final int index, final Flow prevFlow, final double available, final double capacity,
			final boolean atFragmentStart, final boolean relaxUnfulfillableAvoid) {
		final IFlowBox box = prevFlow.box;
		final WritingMode progression = this.box.getBlockParams().flow;
		final IFlowBox source;
		final double sourcePageExtent;
		final double offset;
		if (box instanceof net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox fragment) {
			// 救済済み断片の続き。区間はoffset/sliceExtentだけで表す
			// (断片の断片は作らない)
			source = (IFlowBox) fragment.getSource();
			sourcePageExtent = fragment.getSourcePageExtent();
			offset = fragment.getOffset();
		} else {
			source = box;
			sourcePageExtent = box.getPageExtent(progression);
			offset = 0;
		}
		final net.zamasoft.foliojet.layout.rescue.RescueDecision decision = net.zamasoft.foliojet.layout.rescue.RescueStats
				.record(net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner.planInFragmentainer(
						box.getPos().getType(), atFragmentStart || relaxUnfulfillableAvoid, capacity, available,
						sourcePageExtent, offset));
		if (!(decision instanceof net.zamasoft.foliojet.layout.rescue.RescueDecision.Slice slice)) {
			return null;
		}
		if (!net.zamasoft.foliojet.layout.rescue.RescuePolicy.isEnabled()) {
			// テスト専用の注入点(従来の挙動との比較用)。本番は常に有効
			return null;
		}
		if (!isRescueEnabled(box)) {
			return null;
		}
		if (slice.lastFragment()) {
			// 呼び出し条件(なお超過)からここには来ない。念のため救済しない
			return null;
		}
		final double tailOffset = slice.nextOffset();
		final double tailExtent = sourcePageExtent - tailOffset;
		// 実行時の前進検査(答申§5「offsetの厳密増加を実行時にも検査し、
		// 失敗時はtailを作らない」)。判定器の不変条件と二重になるが、
		// 無限ループの不在は絶対要件なので実行時にも守る
		if (!(tailOffset > offset) || !(tailExtent > 0)) {
			return null;
		}
		final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox head = new net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox(
				source, progression, sourcePageExtent, slice.offset(), slice.sliceExtent());
		final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox tail = new net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox(
				source, progression, sourcePageExtent, tailOffset, tailExtent);
		this.flows.set(index, new Flow(prevFlow.serial, head, prevFlow.pageAxis));
		this.adopt(head);
		net.zamasoft.foliojet.layout.rescue.RescueStats.recordEnabled();
		return tail;
	}

	/**
	 * 救済分割を実際に有効にする範囲です。仕様と設計判断の根拠は
	 * {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}の
	 * クラス説明に集約しています(ここには置きません)。
	 *
	 * <p>
	 * <b>ここはクラス列挙ではありません</b>。この判定に到達する時点で
	 * 「フラグメント先頭・分割不能・なお超過」というエンジン自身の分類は
	 * 済んでいます(答申§4)——
	 * </p>
	 *
	 * <ul>
	 * <li>{@code REPLACED}は元から分割の入口を持たない。</li>
	 * <li>{@code TEXT_BLOCK}は「先頭行が容量を超えている」という、行分割
	 * では前進できない形でだけここへ来る(呼び出し元の事前検査)。</li>
	 * <li>{@code BLOCK}は、書字方向が幹と食い違う等でエンジンが
	 * <b>atomicに分類してREPLACED経路へフォールスルーさせた</b>ものだけが
	 * ここへ来る。通常の(幹と同方向の)ブロックは自分のコンテナで再帰的に
	 * 分割されるため、この地点には到達しない。</li>
	 * <li>{@code RESCUE}は救済済み断片の続き。</li>
	 * </ul>
	 *
	 * <p>
	 * フラグメンテナの種類(ページ・段・表セル)による差はありません。
	 * 段組の中・表セルの中でも、判定({@code prevPageSize}=そのフラグメン
	 * テナ容量)も運搬(残余を戻り値で親へ返す)もまったく同一の経路です。
	 * </p>
	 *
	 * <p>
	 * <b>有効化していないもの</b>: {@code TABLE}(表全体を幾何学的に切る
	 * 経路)。理由は{@code VisualRescuePlanner}のクラス説明§4。
	 * </p>
	 */
	private static boolean isRescueEnabled(final IFlowBox box) {
		return switch (box.getType()) {
		case REPLACED, BLOCK -> box.getPos().getType() == PosType.FLOW;
		case TEXT_BLOCK -> box.getPos().getType() == PosType.TEXT_BLOCK;
		case RESCUE -> true;
		default -> false;
		};
	}

	/**
	 * splitPageAxis の切断判定に渡すフロー計測値(FlowCutter.avoidPushback/tailDecide 用の純データ)。
	 */
	private record FlowMeasurements(double[] pageStarts, double[] pageExtents, boolean[] avoidBefore,
			boolean[] avoidAfter, double[] pageEndFrames) {
	}

	/**
	 * splitPageAxis の切断判定に渡すフロート計測値。フロートが無い場合は全フィールド null(旧コードの契約を維持)。
	 */
	private record FloatMeasurements(double[] pageStarts, double[] pageExtents, boolean[] uncut) {
	}

	/**
	 * 各フローの「内容の下端(ページ軸上の到達位置)」を計算します。
	 * この製品の内部規約(縦書きの pageAxis は常に右→左、LR は描画段で反転)により、
	 * RL/LR は別枝で境界フレームの辺を選びます。
	 */
	private double[] computeFlowBottoms(final BlockParams params) {
		final double[] flowBottoms = new double[this.flows.size()];
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow flow = (Flow) this.flows.get(i);
			double lastBottom = flow.pageAxis;
			if (flow.box.getType() == BoxType.BLOCK) {
				final FlowBlockBox flowBlock = (FlowBlockBox) flow.box;
				switch (params.flow) {
				case WritingMode.TB: {
					// 横書き
					lastBottom += Math.max(flowBlock.getInnerHeight(), flowBlock.getContentSize())
							+ flowBlock.getFrame().getFrameTop();
					break;
				}
				case WritingMode.RL: {
					// 縦書き(日本語)
					lastBottom += Math.max(flowBlock.getInnerWidth(), flowBlock.getContentSize())
							+ flowBlock.getFrame().getFrameRight();
					break;
				}
				case WritingMode.LR: {
					// 縦書き(モンゴル)
					lastBottom += Math.max(flowBlock.getInnerWidth(), flowBlock.getContentSize())
							+ flowBlock.getFrame().getFrameLeft();
					break;
				}
				default:
					throw new IllegalStateException();
				}
			} else {
				lastBottom += flow.box.getPageExtent(params.flow);
			}
			flowBottoms[i] = lastBottom;
		}
		return flowBottoms;
	}

	private FlowMeasurements measureFlows(final BlockParams params) {
		final double[] flowPageStarts = new double[this.flows.size()];
		final double[] flowPageExtents = new double[this.flows.size()];
		final boolean[] avoidBefore = new boolean[this.flows.size()];
		final boolean[] avoidAfter = new boolean[this.flows.size()];
		final double[] flowPageEndFrames = new double[this.flows.size()];
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow flow = (Flow) this.flows.get(i);
			flowPageStarts[i] = flow.pageAxis;
			flowPageExtents[i] = flow.box.getPageExtent(params.flow);
			avoidBefore[i] = flow.box.avoidBreakBefore();
			avoidAfter[i] = flow.box.avoidBreakAfter();
			flowPageEndFrames[i] = flow.box.getType() == BoxType.BLOCK
					? ((AbstractContainerBox) flow.box).getFrame().getFramePageEnd(params.flow)
					: 0;
		}
		return new FlowMeasurements(flowPageStarts, flowPageExtents, avoidBefore, avoidAfter, flowPageEndFrames);
	}

	private FloatMeasurements measureFloats(final BlockParams params) {
		if (this.floatings == null) {
			return new FloatMeasurements(null, null, null);
		}
		final int floatCount = this.floatings.getCount();
		final double[] floatPageStarts = new double[floatCount];
		final double[] floatPageExtents = new double[floatCount];
		final boolean[] floatUncut = new boolean[floatCount];
		for (int k = 0; k < floatCount; ++k) {
			final Floating floating = this.floatings.getFloating(k);
			floatPageStarts[k] = floating.pageAxis;
			floatPageExtents[k] = floating.box.getPageExtent(params.flow);
			// RESCUE(救済断片)は通常の意味では切断不能——行・行グループの
			// ような内部の切断点を持たない。幾何学的な救済切断はこの
			// avoid押し戻しの判断とは別の話なので、置換要素とまったく
			// 同じ扱いにする(2026-07-25、増分7)
			floatUncut[k] = floating.box.getType() == BoxType.REPLACED || floating.box.getType() == BoxType.RESCUE
					|| ((AbstractContainerBox) floating.box).getBlockParams().pageBreakInside == PageBreakMode.AVOID;
		}
		return new FloatMeasurements(floatPageStarts, floatPageExtents, floatUncut);
	}

	/**
	 * 浮動ボックス(直接保持分+子flowの再帰集約)をページ分割し、移動分の
	 * 行き先を型で返します(2026-07-24、P2-4。分岐表の正本:
	 * {@code docs/history/2026-07-24-p2-splitfloatings-branch-table.md}の
	 * 「public 3引数版」の表と1:1対応)。
	 *
	 * <table>
	 * <caption>行き先の写像</caption>
	 * <tr><td>移動なし</td><td>{@link FloatTransferResult#KEEP_OWNER}
	 * (呼び出し側はtargetのコンテナをそのまま使う)</td></tr>
	 * <tr><td>MoveAllかつtarget=MOVE_OWNER</td>
	 * <td>{@link FloatTransferResult#MOVE_OWNER}</td></tr>
	 * <tr><td>MoveAllかつtarget=KEEPかつ非FIRSTかつinnerPageExtent&lt;=0</td>
	 * <td>{@link FloatTransferResult#MOVE_OWNER}(<b>空コンテナ全体を
	 * floatごと移動する特例</b>——台帳はownerに付いたまま)</td></tr>
	 * <tr><td>その他(移動あり)</td><td>{@code Remainder}(targetが
	 * Existingならそのコンテナへ、KEEP/MOVE_OWNERなら新FlowContainerへ
	 * 台帳を装着)</td></tr>
	 * </table>
	 */
	public FloatTransferResult splitFloatings(final FloatTransferTarget target, final double pageLimit,
			final byte flags) {
		this.invalidateNonDecorationContent();
		assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0 || target instanceof FloatTransferTarget.Existing;
		final int flowCount = this.flows == null ? 0 : this.flows.size();
		return switch (this.aggregateFloatings(pageLimit, flags, flowCount)) {
		case FloatAggregate.None none -> FloatTransferResult.KEEP_OWNER;
		case FloatAggregate.OwnerAll ownerAll -> {
			if (target instanceof FloatTransferTarget.MoveOwner) {
				yield FloatTransferResult.MOVE_OWNER;
			}
			if (target instanceof FloatTransferTarget.Keep && (flags & IPageBreakableBox.FLAGS_FIRST) == 0
					&& LayoutUtils.compare(this.box.getInnerPageExtent(this.box.getBlockParams().flow), 0) <= 0) {
				// 空コンテナ全体をfloatごと移動する特例(分岐表)
				yield FloatTransferResult.MOVE_OWNER;
			}
			final Floatings moved = this.floatings;
			this.floatings = null;
			yield remainderWith(target, moved);
		}
		case FloatAggregate.Detached(final Floatings moved) -> remainderWith(target, moved);
		};
	}

	/**
	 * target={@code KEEP}での型付き呼び出しを、切断経路の既存Container契約
	 * (null=移動なし / this=owner丸ごと移動 / 新=残余コンテナ)へ写す補助
	 * です(P2-4)。この契約の消費側({@code ContainerCut.Plain})はP2の
	 * 対象外。
	 */
	private Container splitFloatingsKeepingOwner(final double pageLimit, final byte flags) {
		return switch (this.splitFloatings(FloatTransferTarget.KEEP, pageLimit, flags)) {
		case FloatTransferResult.KeepOwner keepOwner -> null;
		case FloatTransferResult.MoveOwner moveOwner -> this;
		case FloatTransferResult.Remainder(final FlowContainer container) -> container;
		};
	}

	/**
	 * target={@code MOVE_OWNER}(owner自身が丸ごと次フラグメントへ移動する
	 * 文脈)での型付き呼び出しの補助です(P2-4)。移動なしでもownerが移動
	 * するため、KeepOwner/MoveOwnerのどちらもthisになる(旧APIで
	 * {@code nextBox==this}を渡していた契約と同一)。
	 */
	private Container splitFloatingsMovingOwner(final double pageLimit, final byte flags) {
		return switch (this.splitFloatings(FloatTransferTarget.MOVE_OWNER, pageLimit, flags)) {
		case FloatTransferResult.KeepOwner keepOwner -> this;
		case FloatTransferResult.MoveOwner moveOwner -> this;
		case FloatTransferResult.Remainder(final FlowContainer container) -> container;
		};
	}

	private static FloatTransferResult remainderWith(final FloatTransferTarget target, final Floatings moved) {
		final FlowContainer container = target instanceof FloatTransferTarget.Existing(final FlowContainer existing)
				? existing
				: new FlowContainer();
		container.floatings = moved;
		for (int i = 0; i < moved.getCount(); ++i) {
			container.adopt(moved.getFloating(i).box);
		}
		return new FloatTransferResult.Remainder(container);
	}

	public final java.util.Optional<Floatings> detachMovedFloatings(double pageLimit, byte flags) {
		this.invalidateNonDecorationContent();
		final int flowCount = this.flows == null ? 0 : this.flows.size();
		return switch (this.aggregateFloatings(pageLimit, flags, flowCount)) {
		case FloatAggregate.None none -> java.util.Optional.empty();
		case FloatAggregate.OwnerAll ownerAll -> {
			// 自分の台帳をdetachして返す(子flow再帰の内部契約)
			final Floatings moved = this.floatings;
			this.floatings = null;
			yield java.util.Optional.of(moved);
		}
		case FloatAggregate.Detached(final Floatings moved) -> java.util.Optional.of(moved);
		};
	}

	/**
	 * 再帰集約の内部結果です(P2-4。codex設計§2.3の局所状態
	 * {@code NONE/OWNER_ALL/DETACHED}を型で表す。外へは公開しない)。
	 */
	private sealed interface FloatAggregate {
		/** 移動するfloatなし。 */
		record None() implements FloatAggregate {
		}

		/**
		 * ownerの直接保持分が丸ごと移動——台帳はまだownerに付いたまま
		 * (遅延detach。付け替えは呼び出し側が確定する)。
		 */
		record OwnerAll() implements FloatAggregate {
		}

		/**
		 * 移動台帳(ownerからdetach済みの自台帳、直接分割のremainder、
		 * または子から引き取ったFloatings)。
		 */
		record Detached(Floatings floatings) implements FloatAggregate {
		}
	}

	private static final FloatAggregate AGGREGATE_NONE = new FloatAggregate.None();
	private static final FloatAggregate AGGREGATE_OWNER_ALL = new FloatAggregate.OwnerAll();

	/**
	 * 直接保持分と子flow [0..index) の浮動ボックスを分割・集約します
	 * (P2-4で旧private 3引数版のsentinel状態機械を型付きへ置換)。
	 */
	/** 診断用: 保持しているフロー数と直接の浮動体数。 */
	int flowCountForDebug() {
		return (this.flows == null ? 0 : this.flows.size()) * 100
				+ (this.floatings == null ? 0 : this.floatings.getCount());
	}

	private FloatAggregate aggregateFloatings(final double pageLimit, final byte flags, final int index) {
		// 入口final snapshot(addBound事故の教訓——codex設計§2.5)。
		// lflagsのLAST判定は旧実装では「現在の」this.flows.size()を見ていた
		// (ループ上限indexは呼び出し時点のスナップショットという非対称)。
		// このメソッドの実行中this.flowsは変異しない(子再帰は子自身の
		// containerのみを変異させる)ため、入口snapshotと現在値は常に一致
		// し、snapshot化は等価。呼び出し元の変異順序もこの前提を守っている
		// (force-branchの「flow除去はsplitFloatings呼び出しの後」コメント)。
		final int originalFlowCount = this.flows == null ? 0 : this.flows.size();
		assert index <= originalFlowCount;
		FloatAggregate state;
		if (this.floatings != null) {
			// 直接保持分を分割
			state = switch (this.floatings.splitPageAxis(this.box, pageLimit, flags)) {
			case FloatSplitResult.KeepAll keepAll -> AGGREGATE_NONE;
			case FloatSplitResult.MoveAll moveAll -> AGGREGATE_OWNER_ALL;
			case FloatSplitResult.Partition(final Floatings remainder) -> new FloatAggregate.Detached(remainder);
			};
			if (this.floatings.getCount() == 0) {
				// 旧実装からの防御(plan駆動commitのPartitionはsource側を
				// 空にしないため、現行では到達しない)
				this.floatings = null;
			}
		} else {
			state = AGGREGATE_NONE;
		}
		for (int i = 0; i < index; ++i) {
			final Flow flow = (Flow) this.flows.get(i);
			byte lflags = (byte) 0xFF;
			if (i != 0) {
				lflags ^= IPageBreakableBox.FLAGS_FIRST;
			}
			if (i != originalFlowCount - 1) {
				lflags ^= IPageBreakableBox.FLAGS_LAST;
			}
			switch (flow.box.getType()) {
			case RESCUE:
				// 2026-07-25(救済分割・増分6): 救済断片は排除域の台帳を
				// 持たず、子コンテナへも降りない。増分6で自前のコンテナを
				// 持つ元ボックス(書字方向が食い違うブロック・テキスト
				// ブロック)が来るようになったが、<b>何もしないのが正しい</b>。
				//
				// 理由: 救済断片は「元ボックス全体を、消費済み量だけずらして
				// クリップして描く」ものである(答申§2)。元ボックスの中の
				// フロートは、その元ボックスの描画の一部として、各断片の
				// クリップ内に見える範囲だけが描かれる。ここで
				// detachMovedFloatings して次フラグメントの台帳へ移すと、
				// (a) 元ボックスからフロートが失われるため先頭断片で消え、
				// (b) 次フラグメントでは元の幾何を無視した新しい位置へ
				// 置き直される——「レイアウト計算は変えない。同じ箱を
				// ずらしてクリップするだけ」という設計の核を破る。
				//
				// 断片がフラグメント上で占める量(=この断片の排除域の高さ)は
				// sliceExtent であり、それは flow.box.getPageExtent() が
				// 返す値そのものなので、追加の帳簿は要らない。残余(tail)は
				// 次フラグメントで通常どおり配置され、そこで同じ規則が
				// 適用される(答申§5)。
				assert flow.box instanceof net.zamasoft.foliojet.layout.rescue.VisualRescueBox : flow.box;
				break;
			case BLOCK:
				final AbstractContainerBox blockBox = (AbstractContainerBox) flow.box;
				double pageAxis = pageLimit - flow.pageAxis;
				pageAxis -= blockBox.getFrame().getFramePageStart(blockBox.getBlockParams().flow);
				// 子は自分の台帳をdetachして返す(detachMovedFloatingsの再帰)
				final java.util.Optional<Floatings> childDetached = blockBox.getContainer()
						.detachMovedFloatings(pageAxis, (byte) (lflags & flags));
				if (childDetached.isEmpty()) {
					break;
				}
				final Floatings childFloatings = childDetached.get();
				switch (state) {
				case FloatAggregate.None none ->
					// 子のFloatingsオブジェクトをそのまま採用(コンテナごと引き取り)
					state = new FloatAggregate.Detached(childFloatings);
				case FloatAggregate.OwnerAll ownerAll -> {
					// 子float追加時に初めてownerからdetachが確定する
					final Floatings owned = this.floatings;
					this.floatings = null;
					for (int j = 0; j < childFloatings.getCount(); ++j) {
						owned.addFloating(childFloatings.getFloating(j));
					}
					state = new FloatAggregate.Detached(owned);
				}
				case FloatAggregate.Detached(final Floatings moved) -> {
					for (int j = 0; j < childFloatings.getCount(); ++j) {
						moved.addFloating(childFloatings.getFloating(j));
					}
				}
				}
				break;
			}
		}
		assert !(state instanceof FloatAggregate.Detached(final Floatings moved) && moved.getCount() == 0);
		return state;
	}

	private FlowContainer cutHead(double pageLimit, byte flags) {
		if (pageLimit < 0) {
			pageLimit = 0;
		}
		FlowContainer nextBox = new FlowContainer();
		if (this.flows != null) {
			nextBox.flows = this.flows;
			this.flows = null;
			for (int i = 0; i < nextBox.flows.size(); ++i) {
				nextBox.adopt(nextBox.flows.get(i).box);
			}
			this.invalidateNonDecorationContent();
		}
		// flowsは先にnextBoxへ移送済みのため、集約対象は直接保持分のみ
		// (index=0。this.flows==nullなのでLAST判定にも影響しない)
		this.attachAggregate(nextBox, this.aggregateFloatings(pageLimit, flags, 0));
		return nextBox;
	}

	private FlowContainer cutTail(double pageLimit, byte flags) {
		FlowContainer nextBox = new FlowContainer();
		int flowCount = this.flows == null ? 0 : this.flows.size();
		this.attachAggregate(nextBox, this.aggregateFloatings(pageLimit, flags, flowCount));
		return nextBox;
	}

	/**
	 * 集約結果の移動台帳を{@code nextBox}へ装着します(P2-4。旧
	 * 「nextBox.floatings代入+identity比較でthis.floatings=null」の置換)。
	 */
	private void attachAggregate(final FlowContainer nextBox, final FloatAggregate aggregate) {
		switch (aggregate) {
		case FloatAggregate.None none -> {
		}
		case FloatAggregate.OwnerAll ownerAll -> {
			nextBox.floatings = this.floatings;
			this.floatings = null;
		}
		case FloatAggregate.Detached(final Floatings moved) -> nextBox.floatings = moved;
		}
	}

	public final void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// 元の走査順を保つため、スタックへは逆順でpushする
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			// 通常のフロー
			Flow c = (Flow) this.flows.get(i);
			worklist.push(IBox.getTextStep(c.box, textBuff));
		}
	}

	/**
	 * stampRanges 済みの閉部分木をコンテナから吸収します(C1c)。最上位の
	 * 閉じた plain ブロック(restyle 走行で replay-subtree になるもの:
	 * BLOCK・非フロート・書字方向一致)のうち再生範囲が記録されたものを
	 * フローから除去し、serial 付きの再生範囲として返します。resume は
	 * これを {@link #restyle(BlockBuilder, int, boolean, List)} の prefix に
	 * 渡し、serial 順で残アイテムと合流させて再駆動します。
	 * 呼び出しはソースログ水位の計算後であること(吸収されたアイテムは
	 * コンテナを歩く水位計算から見えなくなるため)。
	 */
	public final List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> extractReplayable(
			final java.util.Map<IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges,
			final boolean rootVertical, final int walkDepth) {
		if (this.flows == null || ranges.isEmpty()) {
			return List.of();
		}
		List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix = null;
		// walkDepth >= 1 のとき末尾フローは開いた継続(depth>1 なら moved-open
		// チェーン子、depth==1 なら開きテキスト)であり、たとえソースログ上で
		// 閉じていても(イベント全着)flowStack への再積みが必要なため吸収
		// しない。また末尾を抜くと開き判定(lastFlow)が前のアイテムへ
		// ずれる — C1b までは walk 時の lastFlow 判定が replay より先に
		// 効いて守られていた条件の、記録時への移し替え
		int limit = walkDepth >= 1 ? this.flows.size() - 1 : this.flows.size();
		for (int i = 0; i < limit;) {
			final Flow flow = this.flows.get(i);
			if (flow.box.getType() != BoxType.BLOCK || flow.box.getPos().getType() == PosType.FLOAT
					|| ((AbstractContainerBox) flow.box).getBlockParams().flow.isVertical() != rootVertical) {
				// 表・置換・テキスト・縦横混在は従来経路のまま
				++i;
				continue;
			}
			final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range = ranges.remove(flow.box);
			if (range == null) {
				++i;
				continue;
			}
			if (prefix == null) {
				prefix = new ArrayList<>();
			}
			prefix.add(new net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange(flow.serial, range.fromId(),
					range.toId()));
			this.flows.remove(i);
			this.invalidateNonDecorationContent();
			--limit;
		}
		if (prefix == null) {
			return List.of();
		}
		if (this.flows.isEmpty()) {
			this.flows = null;
		}
		return prefix;
	}

	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes) {
		this.restyle(builder, shape, restyleAbsolutes, List.of());
	}

	/**
	 * 吸収された再生範囲(C1c)を serial 順で合流させながら再開します。
	 *
	 * <p>
	 * 2026-07-30(legacy再帰撤去=増分4a): worklist executorが唯一の
	 * driverになった。従来はここに{@code isWorklistMode()}分岐と旧再帰
	 * driver(forループ+{@code RECURSIVE_DESCENDER})が並存し、
	 * {@code RootBuilder}が継続ごとに適格判定(WorklistTailGate)して
	 * 選んでいた——増分1でMULTICOL native scope降下のバイト等価を証明、
	 * 増分2でgateをMULTICOL許可へ拡張、増分3でrootless COLUMNも接続し、
	 * legacy駆動へ入る入口が消えたため分岐ごと撤去した(codex相談
	 * docs/consultations/consult-codex-2026-07-30-increment4-removal-spec.txt)。
	 * TEXT/BLOCK/TABLE/REPLACEDの意味は{@link #restyleItem}が担い、
	 * OpenChain降下だけが明示スタック(worklist)で駆動される。
	 * </p>
	 */
	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes,
			List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		this.restyleWorklist(builder, shape, restyleAbsolutes, prefix);
	}

	/**
	 * 「この組み直しは尾部ではない」区間の深さです(2026-07-28新設)。
	 *
	 * <p>
	 * 切断されたテキストブロックの尾部再生({@code replayTextFrom})は、
	 * 断片の{@code breakToken}が持つ文字位置から<b>ソースの末尾まで</b>を
	 * 流す。断片が「その流れの最後の断片」であるかぎり正しい——残りは
	 * 全部その断片のものだからである。
	 * </p>
	 *
	 * <p>
	 * ところが{@link ColumnsContainer#restyle}は<b>全ての段を一本に
	 * 組み直す</b>。先頭の段の断片も、最後の段の断片も、同じ組み直しの
	 * 中で再開される。先頭側の断片に「末尾まで」を流させると、後続の段の
	 * 断片が持っている分まで組まれ、<b>同じページに同じ文字が二度描かれる</b>
	 * (実測: local/shrink/strict-118665-min.html。段2が "T2 T3 T4"、
	 * 段3が "T3 T4" を描いていた)。
	 * </p>
	 *
	 * <p>
	 * 断片は流れを分割して持っているので、<b>最後の段以外は自分の分しか
	 * 持っていない</b>。最後の段だけが「末尾まで」を名乗れる——
	 * {@code ColumnsContainer.restyle}が開いた尾({@code shape})を
	 * 最終段にだけ渡すのと同じ理由・同じ境界である。ここが立っている間、
	 * 尾部再生は行わずボックス再生(自分の行だけを再演)へ落とす。
	 * </p>
	 *
	 * <p>
	 * ThreadLocalなのは{@code ContinuationStats}の継続経路スタックと同じ理由(複数変換の
	 * 並行実行)。カウンタなのは入れ子の段組で対称にpush/popするため。
	 * </p>
	 */
	private static final ThreadLocal<int[]> tailSealDepth = ThreadLocal.withInitial(() -> new int[1]);

	/**
	 * {@link #tailSealDepth}を1増やします。必ず{@link #popTailSeal}と
	 * try/finallyで対にすること。
	 */
	public static void pushTailSeal() {
		++tailSealDepth.get()[0];
	}

	/** {@link #tailSealDepth}を1減らします。 */
	public static void popTailSeal() {
		final int[] depth = tailSealDepth.get();
		if (depth[0] <= 0) {
			tailSealDepth.remove();
			throw new IllegalStateException("popTailSeal without a matching push");
		}
		if (--depth[0] == 0) {
			tailSealDepth.remove();
		}
	}

	private static boolean isTailSealed() {
		return tailSealDepth.get()[0] > 0;
	}

	/**
	 * 現スレッドに尾部封印({@link #tailSealDepth})が残っているかを
	 * 返します(2026-07-30、増分1)。正常なら変換完了後は必ずfalse——
	 * trueが残るとそのスレッドの以後の変換で尾部再生が全て封じられる
	 * ため、テストがリーク検査に使う。
	 */
	public static boolean hasOpenTailSeal() {
		return isTailSealed();
	}

	/**
	 * worklist executorのスタック要素です(2026-07-30、legacy再帰撤去=
	 * 増分1で導入)。従来は{@link RestyleFrame}単型だったが、MULTICOL
	 * native降下({@link MulticolRestyleScope})を再帰なしで表すため
	 * 和型へ一般化した。
	 */
	private sealed interface WorklistStep permits RestyleFrame, MulticolRestyleScope {
	}

	/**
	 * worklist executorの1段です(2026-07-22新設、B6a1)。sort済み
	 * {@code items}・次に処理するindex・その段の{@code lastFlow}/
	 * {@code shape}/trace用{@code depth}を保持する可変クラス。
	 */
	private static final class RestyleFrame implements WorklistStep {
		final List<BoxHolder> items;
		final Flow lastFlow;
		final net.zamasoft.foliojet.layout.fragment.OpenShape shape;
		final int depth;
		/**
		 * 処理開始時点のitems数(2026-07-30、増分4a)。旧forループが
		 * ループ前に{@code int size}を固定していた契約の正確な保存——
		 * {@code size}は次itemの終端アンカー判定にも渡るため、都度
		 * {@code items.size()}を読み直すと途中変更に対する意味が変わる。
		 */
		final int size;
		int nextIndex = 0;

		RestyleFrame(List<BoxHolder> items, Flow lastFlow, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
				int depth) {
			this.items = items;
			this.lastFlow = lastFlow;
			this.shape = shape;
			this.depth = depth;
			this.size = items == null ? 0 : items.size();
		}
	}

	/**
	 * {@link ColumnsContainer#restyle}の状態機械を再帰なしで表すscopeです
	 * (2026-07-30、増分1)。{@code ColumnsContainer.beginRestyleScope()}
	 * 済みの旧段snapshotを保持し、executorが段をindex昇順に1つずつ
	 * {@link RestyleFrame}としてpushする——親frameをpauseしたままLIFOで
	 * 積むことで、旧経路(MULTICOL全体を深さ優先で完了してから親の後続
	 * itemへ戻る)と同じ順序を保存する。全段完了で
	 * {@code ColumnsContainer.endRestyleScope()}(尾部封印の解除)。
	 *
	 * <p>
	 * 開いた尾({@code inner})を渡すのは最終段だけ・それ以前は
	 * {@code CLOSED}——{@link ColumnsContainer#restyle}と同じ境界
	 * (最終段以外に渡すと開いたままの他人のボックスの中へ組まれる)。
	 * MULTICOL ownerの{@code endFlowBlock()}は呼ばない({@code inner}は
	 * OpenChain/OpenTextで決してClosedにならないため、legacyの
	 * {@code FlowBlockBox.restyle()}と同じく省く)。
	 * </p>
	 */
	private static final class MulticolRestyleScope implements WorklistStep {
		final List<Container> snapshot;
		final net.zamasoft.foliojet.layout.fragment.OpenShape inner;
		int nextColumn = 0;

		MulticolRestyleScope(List<Container> snapshot, net.zamasoft.foliojet.layout.fragment.OpenShape inner) {
			this.snapshot = snapshot;
			this.inner = inner;
		}
	}

	/**
	 * {@code OpenChain}を明示スタックで駆動するworklist executor本体
	 * です(2026-07-22新設、B6a1——`docs/consultations/consult-b6a1
	 * -explicit-worklist-executor-codex.txt`の設計をそのまま実装。
	 * 2026-07-30の増分4で<b>唯一のdriver</b>となった——旧再帰driverとの
	 * 並存期の経緯は{@link #restyle}のjavadoc参照)。
	 * TEXT/BLOCK/TABLE/REPLACEDの意味は{@link #restyleItem}が担い、
	 * {@code OpenChain}降下は{@link #descendWorklist}が
	 * {@link RestyleFrame}(plain flow)または
	 * {@link MulticolRestyleScope}(段組)としてこの{@code Deque}へ
	 * pushする。
	 *
	 * <p>
	 * <b>2026-07-22の実バグ修正</b>:
	 * {@code containerBox.restyle(builder, inner)}は{@code
	 * AbstractContainerBox.restyle()}ではなく{@code FlowBlockBox
	 * .restyle()}(オーバーライド)へ多態的に解決される——そちらは
	 * {@code builder.startFlowBlock(this)}を呼んだ**後で**
	 * {@code this.container.restyle(...)}へ委譲し、{@code shape}が
	 * {@code Closed}のときだけ{@code builder.endFlowBlock()}を呼ぶ
	 * (`FlowBlockBox.java:628`付近)。最初の実装はこの
	 * {@code startFlowBlock}呼び出しを素通りして{@code containerBox
	 * .getContainer()}のitemsを直接dequeへpushしていたため、
	 * {@code flowStack}が正しい深さまで育たず`ContinuationInvariant
	 * ViolationException(flowStack.size() != continuation.depth())`
	 * を引き起こした(`docs/history/2026-07-22-b6a1-worklist-executor
	 * -bug-found-and-reverted.md`参照)。{@code OpenChain}降下の
	 * {@code inner}は`OpenShape.of()`の構成上常にOpenChainかOpenText
	 * であり決してClosedにならないため、対応する{@code endFlowBlock}
	 * 呼び出しは(legacy再帰と同じく)不要——{@code startFlowBlock}
	 * だけをこのpush分岐へ追加すれば`FlowBlockBox.restyle()`と同じ
	 * 効果になる。
	 * </p>
	 */
	private void restyleWorklist(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes, List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		final Deque<WorklistStep> stack = new ArrayDeque<>();
		this.pushWorklistFrame(stack, builder, shape, restyleAbsolutes, prefix);
		try {
			while (!stack.isEmpty()) {
				final WorklistStep step = stack.peek();
				if (step instanceof MulticolRestyleScope scope) {
					if (scope.nextColumn >= scope.snapshot.size()) {
						// 全段完了。pop→封印解除の順(逆にすると解除が
						// 例外を投げた場合にfinally清算と二重解除になる)
						stack.pop();
						ColumnsContainer.endRestyleScope();
						continue;
					}
					final int c = scope.nextColumn++;
					final FlowContainer column = (FlowContainer) scope.snapshot.get(c);
					// 開いた尾は最終段だけ・それ以前はCLOSED
					// (ColumnsContainer.restyleと同じ境界)
					final net.zamasoft.foliojet.layout.fragment.OpenShape columnShape = c == scope.snapshot.size() - 1
							? scope.inner
							: net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED;
					column.pushWorklistFrame(stack, builder, columnShape, false, List.of());
					continue;
				}
				final RestyleFrame frame = (RestyleFrame) step;
				if (frame.items == null || frame.nextIndex >= frame.size) {
					stack.pop();
					continue;
				}
				final int i = frame.nextIndex++;
				// restyleItem()はthisのインスタンス状態を一切参照しない
				// (items/lastFlow/shape等パラメータのみで完結する)ため、
				// frameがどのFlowContainerに由来するかによらず同じ呼び出しで
				// 正しく動く——呼び出し先はthis固定でよい。
				this.restyleItem(builder, frame.items, i, frame.size, frame.lastFlow, frame.shape, frame.depth,
						stack);
			}
		} finally {
			// 例外時の清算: スタックに残ったMulticolRestyleScopeの
			// 尾部封印を必ず解除する(正常完了時はスタック空でno-op)。
			// 放置するとThreadLocalのtailSealDepthが正のまま残り、
			// 以後この変換の尾部再生が全て封じられる。
			while (!stack.isEmpty()) {
				if (stack.pop() instanceof MulticolRestyleScope) {
					ColumnsContainer.endRestyleScope();
				}
			}
		}
	}

	private void pushWorklistFrame(Deque<WorklistStep> stack, BlockBuilder builder,
			net.zamasoft.foliojet.layout.fragment.OpenShape shape, boolean restyleAbsolutes,
			List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		final CollectedItems collected = this.collectItems(builder, restyleAbsolutes, prefix);
		if (collected.items() != null) {
			Collections.sort(collected.items());
			moveOpenChainTailLast(collected.items(), collected.lastFlow(), shape);
			stack.push(new RestyleFrame(collected.items(), collected.lastFlow(), shape, shape.depth()));
		}
	}

	/**
	 * <b>開いたまま降りるボックスは、必ず最後に処理する</b>(2026-07-27新設)。
	 *
	 * <p>
	 * {@link #collectItems}はfloatとflowを1つのリストへ合流し、呼び出し側が
	 * serial順に並べる。ところが{@code aggregateFloatings}が子から引き取った
	 * floatは<b>子の採番のまま</b>入るため、親子の採番が混ざり、
	 * {@code lastFlow}が末尾に来る保証がない。
	 * </p>
	 *
	 * <p>
	 * {@code lastFlow}が開いた尾のとき{@code FlowBlockBox.restyle}は
	 * <b>{@code endFlowBlock()}を意図的に省く</b>ので、その後ろに残った項目は
	 * <b>開いたままの他人のボックスの中へ</b>組まれる。flowはボックスの同一性で
	 * 再係留されるので影響を受けないが、<b>floatは「そのとき開いているフロー」へ
	 * 位置的に係留される</b>({@code BlockBuilder.commitFloatPlacement})ため、
	 * 順序の誤りだけで別の部分木へ移ってしまう。その部分木が
	 * {@code balance()}のソース再駆動で捨てられると、内容が黙って消える。
	 * </p>
	 *
	 * <p>
	 * <b>{@code OpenText}は対象外。</b>ライブ構築の{@code toAddFloating}と
	 * 同じ保留になるため並べ替えは不要で、実測では並べ替えると
	 * {@code FloatPagebreakTest}・{@code ImageAfterAvoidTest}が落ちる。
	 * </p>
	 *
	 * <p>
	 * 実測(2026-07-27、20万文書の掃過で発見。50,000文書に1件):
	 * 段組の中のフロートの内容が丸ごと消えていた。
	 * </p>
	 */
	private static void moveOpenChainTailLast(final List<BoxHolder> items, final Flow lastFlow,
			final net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		if (lastFlow == null || !(shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.OpenChain)) {
			return;
		}
		// BoxHolder は equals を上書きしないので indexOf は同一性比較
		final int at = items.indexOf(lastFlow);
		if (at < 0 || at == items.size() - 1) {
			return;
		}
		items.remove(at);
		items.add(lastFlow);
	}

	/**
	 * {@link #collectItems}の戻り値です(2026-07-22、B6a1準備)。sort済み
	 * ではない生の合流結果——呼び出し側がsortする。
	 */
	private record CollectedItems(List<BoxHolder> items, Flow lastFlow) {
	}

	/**
	 * floatings・(有効なら)absolutes・flows・prefixを1つの{@code items}
	 * リストへ合流します(2026-07-22、B6a1準備で{@code restyle()}から
	 * 抽出——挙動は一切変えていない純粋な関数抽出)。{@code this
	 * .floatings}/{@code this.absolutes}/{@code this.flows}を消費して
	 * null化する副作用は元のまま維持する。worklist executor
	 * (`restyleWorklist`)が子{@code FlowContainer}へ降りる際も同じ
	 * メソッドを呼ぶことで、合流ロジックの二重実装を避ける。
	 */
	private CollectedItems collectItems(BlockBuilder builder, boolean restyleAbsolutes,
			List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		// フロートは最近接ブロック祖先のコンテナに係留されるため、移動した
		// 部分木の内部フロートは部分木と一緒に動き、ソース再駆動でも二重
		// 生成されない(golden: float-in-moved)。絶対配置ボックスを含む
		// 部分木は stampRanges の containsAbsolute ゲート(E-6増分4e以前は
		// Opaque記録によるcontainsOpaque)が部分木単位で正しくフォールバック
		// させる — 階層単位のゲートは不要
		List<BoxHolder> items = null;
		if (this.floatings != null) {
			Floatings floatings = this.floatings;
			this.floatings = null;
			int size = floatings.getCount();
			if (size > 0) {
				if (items == null) {
					items = new ArrayList<BoxHolder>();
				}
				for (int i = 0; i < size; ++i) {
					items.add(floatings.getFloating(i));
				}
			}
		}

		if (restyleAbsolutes && this.absolutes != null) {
			Absolutes absolutes = this.absolutes;
			this.absolutes = null;
			int size = absolutes.getCount();
			for (int i = 0; i < size; ++i) {
				builder.addBound(absolutes.getAbsolute(i).box);
			}
		}

		Flow lastFlow = null;
		if (this.flows != null) {
			List<Flow> flows = this.flows;
			this.flows = null;
			this.invalidateNonDecorationContent();
			int size = flows.size();
			if (size > 0) {
				if (items == null) {
					items = new ArrayList<BoxHolder>();
				}
				for (int i = 0; i < size; ++i) {
					items.add(flows.get(i));
				}
				lastFlow = (Flow) flows.get(size - 1);
			}
		}

		if (!prefix.isEmpty()) {
			// C1c: 吸収された閉部分木を serial 順の合流に加える
			if (items == null) {
				items = new ArrayList<BoxHolder>();
			}
			for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range : prefix) {
				items.add(new Replay(range));
			}
		}
		return new CollectedItems(items, lastFlow);
	}

	/**
	 * 互換フォールバック({@link #descendWorklist})を警告済みの
	 * box/containerクラス対です(2026-07-30、増分4b。同じ対の大量ログを
	 * 防ぐ——初回だけWARNINGを出す)。
	 */
	private static final java.util.Set<String> WARNED_FALLBACK_PAIRS = java.util.concurrent.ConcurrentHashMap
			.newKeySet();

	/**
	 * {@code OpenChain}の子孫へ1段降ります(2026-07-30、増分4bで
	 * {@code ChainDescender} interface+2実装のlambdaから具体helperへ
	 * 畳み込み——worklist driverが唯一のdriverになり、差し替え点としての
	 * 意味が消えたため)。
	 *
	 * <ul>
	 * <li>plain flow({@link FlowContainer}子): legacy再帰
	 * ({@code FlowBlockBox.restyle()})が暗黙に行うstartFlowBlockを
	 * 明示的に再現し、frameをpushする(2026-07-22の実バグ修正——
	 * 素通りすると{@code flowStack}が育たずinvariant違反)</li>
	 * <li>MULTICOL({@link ColumnsContainer}子): native scope降下
	 * (増分1)。startFlowBlock→beginRestyleScope→scopeをpush。
	 * endFlowBlockはinnerが決してClosedにならないため呼ばない</li>
	 * <li>未知の組み合わせ: <b>互換フォールバック</b>——カウンタ+初回
	 * 警告の上で{@code containerBox.restyle(builder, inner)}の多態的
	 * 意味を維持する。fail closed例外にしないのはクラッシュ排除の絶対
	 * 要件による(「非PLAIN/MULTICOL tailは構造的に不可能」の証明は
	 * 全入口に対しては強くない——codex相談
	 * consult-codex-2026-07-30-increment4-removal-spec.txt §3。
	 * 将来の未知FlowBlockBoxサブタイプはMULTICOLへ分類されるが
	 * containerがColumnsContainerである型保証もない)。再入した
	 * restyle()は無条件worklistなので旧driverは復活しない</li>
	 * </ul>
	 */
	private static void descendWorklist(Deque<WorklistStep> stack, BlockBuilder builder,
			net.zamasoft.foliojet.layout.box.AbstractContainerBox containerBox,
			net.zamasoft.foliojet.layout.fragment.OpenShape inner) {
		final Container childContainer = containerBox.getContainer();
		if (childContainer instanceof FlowContainer childFc && containerBox instanceof FlowBlockBox flowBox) {
			builder.startFlowBlock(flowBox);
			childFc.pushWorklistFrame(stack, builder, inner, false, List.of());
		} else if (childContainer instanceof ColumnsContainer columns && containerBox instanceof FlowBlockBox flowBox) {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordMulticolNativeDescent();
			builder.startFlowBlock(flowBox);
			stack.push(new MulticolRestyleScope(columns.beginRestyleScope(), inner));
		} else {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordWorklistCompatFallback();
			final String pair = containerBox.getClass().getName() + "/"
					+ (childContainer == null ? "null" : childContainer.getClass().getName());
			if (WARNED_FALLBACK_PAIRS.add(pair)) {
				java.util.logging.Logger.getLogger(FlowContainer.class.getName())
						.warning("OpenChain descent fell back to polymorphic restyle for unknown box/container pair "
								+ pair + "; the worklist executor cannot represent this container as a frame "
								+ "(expected FlowContainer or ColumnsContainer under FlowBlockBox)");
			}
			containerBox.restyle(builder, inner);
		}
	}

	/**
	 * sort済み{@code items}の1件を処理する共有dispatchです(2026-07-22、
	 * B6a1準備で{@code restyle()}のforループ本体から抽出——挙動は一切
	 * 変えていない純粋な関数抽出(旧{@code continue}は{@code return}へ
	 * 機械的に置換しただけ)。将来のworklist executor
	 * (`docs/history/2026-07-22-b6a1-trailing-items-measurement.md`
	 * 参照)が、この共有dispatchを複製せずそのまま呼べるようにする
	 * ための下ごしらえ——TEXT/BLOCK/TABLE/REPLACEDの意味を二重実装
	 * しない、というcodex設計相談の要件(却下案「switch全体を新
	 * executor側へコピーする」)に対応する。{@code OpenChain}降下は
	 * {@link #descendWorklist}が明示スタック({@code stack})へ積む
	 * (増分4bでdescender差し替え機構を畳んだ——worklistが唯一のdriver)。
	 */
	private void restyleItem(BlockBuilder builder, List<BoxHolder> items, int i, int size, Flow lastFlow,
			net.zamasoft.foliojet.layout.fragment.OpenShape shape, int depth, Deque<WorklistStep> stack) {
		// 以下2重の{}は抽出前のインデント(if/forの2階層)をそのまま残す
		// ための意図的なもの——大量行の再インデントによる誤りを避けた
		{
			{
				BoxHolder holder = (BoxHolder) items.get(i);
				if (holder instanceof Replay replay) {
					// C1c: 吸収された閉部分木のソース再駆動(再生可否は
					// 破断時に判定済みのため無条件。op は従来と同一)
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "replay-subtree",
							"serial=" + holder.serial);
					builder.getPageContext().replaySubtree(replay.range, builder);
					return;
				}
				switch (holder.getBox().getType()) {
				case TEXT_BLOCK: {
					// テキストブロックボックス
					final TextBlockBox textBlock = (TextBlockBox) holder.getBox();
					final boolean open = lastFlow == holder
							&& shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.OpenText;
					boolean replayed = false;
					// open(live ストリームが続きを流し込む)場合の尾部再生は
					// box-restyle に委ねる。かつての理由「charOffset の±1」は
					// 整形器バグとして根治済み(2026-07-17)だが、解禁実験は
					// 多数の失敗を示した — 残る実質は live shaper の保留
					// バッファと builder テキスト状態の受け渡し(deliveredCharEnd
					// と unitizer 保留の境界)であり、M3b のトークン再開で回収する
					// isTailSealed(2026-07-28): 段の組み直しの最中は、どの
					// 断片も「自分が記録した分しか持っていない」ので、
					// charOffsetからソース末尾までを流す尾部再生をしては
					// いけない({@code FlowContainer.pushTailSeal}参照)
					if (!open && !isTailSealed()
							&& (builder instanceof net.zamasoft.foliojet.layout.builder.impl.RootBuilder
									|| builder instanceof net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder)
							&& builder.getPageContext() != null) {
						final net.zamasoft.foliojet.layout.builder.impl.RootBuilder root = builder.getPageContext();
						// 尾部の終端: 次の item のアンカーがあれば上限として使う。
						// なくても tailBound がログ構造(囲みブロックの EndBlock
						// またはブロック級兄弟の Start)から終端を導出するため、
						// 次兄弟が分割断片(アンカー無効)でも再生できる(2026-07-17)
						long endId = -1;
						if (i + 1 < size) {
							// 次アイテムが吸収済み再生範囲(C1c)なら fromId が
							// そのボックスのアンカーと同値
							final BoxHolder next = (BoxHolder) items.get(i + 1);
							endId = next instanceof Replay replay ? replay.range.fromId()
									: next.getBox().getSourceAnchor();
						}
						// 切断段落の尾部をソース再駆動(M6b v3)
						replayed = root.replayTextFrom(textBlock, endId, open);
					}
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth,
							replayed ? "text-tail" : (open ? "restyle-text-open" : "restyle-text"),
							"serial=" + holder.serial);
					if (!replayed) {
						if (open) {
							// M3b Phase 1: スライス運搬経由(restyle 内部で
							// record→replay)。Phase 2/3 の TextTail 型付き化の実測
							net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordOpenTextHandoff();
						}
						textBlock.restyle(builder);
					}
					// System.err.println("endTextBlock"+depth);
					if (!open && !replayed) {
						// 再駆動時はドライバの finishReplay が既に閉じている
						builder.endTextBlock();
					}
				}
					break;
				case BLOCK: {
					if (holder.getBox().getPos().getType() != PosType.FLOAT) {
						AbstractContainerBox containerBox = (AbstractContainerBox) holder.getBox();
						if (containerBox.getBlockParams().flow.isVertical() != builder.getRootBox().getBlockParams().flow.isVertical()) {
							// 書字方向が違う場合
							builder.addBound(containerBox);
						} else {
							// ブロックボックス
							// 匿名ボックス
							// テーブルキャプション
							if (lastFlow == holder
									&& shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.OpenChain(
											final net.zamasoft.foliojet.layout.fragment.OpenShape inner)) {
								// 開いたままの祖先チェーン
								net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-chain",
										"serial=" + holder.serial);
								net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordChainFiring();
								descendWorklist(stack, builder, containerBox, inner);
							} else if (!((builder instanceof net.zamasoft.foliojet.layout.builder.impl.RootBuilder
									|| builder instanceof net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder)
									&& builder.getPageContext() != null
									&& builder.getPageContext().replayFromSource(containerBox, builder))) {
								// 丸ごと移動した閉じた部分木はソース再駆動される(M6b
								// segment-restyle)。false ならボックス再生でフォールバック。
								// lastFlow && OpenText の末尾も閉じたボックス(次段は Closed)
								net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-box",
										"serial=" + holder.serial);
								containerBox.restyle(builder,
										net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
							} else {
								net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "replay-subtree",
										"serial=" + holder.serial);
							}
						}
					} else {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-float",
								"serial=" + holder.serial);
						((Floating) holder).restyle(builder);
					}
				}
					break;

				case TABLE: {
					// テーブル
					TableBox tableBox = (TableBox) holder.getBox();
					// 表セット T-c(2026-07-30、ユーザー承認によるG-1裁定の更新):
					// 破断時に刻印済み(stampRangesのTABLE根範囲)の表は
					// ソース再駆動で作り直す——BLOCK分岐のreplayFromSourceと
					// 同型のfail closed(範囲なし・範囲欠損・非Root/Column文脈は
					// 従来どおりbox-restyle=addBoundへ)。これがG-1で不在だった
					// 「表recipeの消費者」であり、TABLE_REPLAYSカウンタが
					// 非空振りを検出する
					if ((builder instanceof net.zamasoft.foliojet.layout.builder.impl.RootBuilder
							|| builder instanceof net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder)
							&& builder.getPageContext() != null
							&& builder.getPageContext().replayFromSource(tableBox, builder)) {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "replay-table",
								"serial=" + holder.serial);
					} else {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "bound-table",
								"serial=" + holder.serial);
						builder.addBound(tableBox);
					}
				}
					break;
				case RESCUE: {
					// 2026-07-25(救済分割・増分5): 救済断片の残余。BoxType
					// を偽装せず専用の入口へ明示dispatchする(答申§5——
					// 通常のaddBound()へ流すとParamsのキャストで落ちる)
					if (holder.getBox().getPos().getType() == PosType.FLOAT) {
						// 増分7: 浮動体の残余は通常のfloat配置をやり直す
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-float-rescue",
								"serial=" + holder.serial);
						((Floating) holder).restyle(builder);
						break;
					}
					final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox rescueBox = (net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox) holder
							.getBox();
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "bound-rescue",
							"serial=" + holder.serial);
					builder.addRescueBound(rescueBox);
					break;
				}
				case REPLACED: {
					// 置換されたボックス
					AbstractReplacedBox replacedBox = (AbstractReplacedBox) holder.getBox();
					if (replacedBox.getPos().getType() != PosType.FLOAT) {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "bound-replaced",
								"serial=" + holder.serial);
						builder.addBound(replacedBox);
					} else {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-float-replaced",
								"serial=" + holder.serial);
						((Floating) holder).restyle(builder);
					}
					break;
				}
				default:
					throw new IllegalStateException(holder.getBox().toString());
				}
			}
		}
	}

	public double getMaxWidth() {
		if (this.flows == null) {
			return 0;
		}
		double width = 0;
		for (int i = 0; i < this.flows.size(); ++i) {
			Flow flow = (Flow) this.flows.get(i);
			width = Math.max(width, flow.box.getWidth());
		}
		return width;
	}

	public String toString() {
		return super.toString() + "/flowCount=" + (this.flows == null ? 0 : this.flows.size());
	}
}
