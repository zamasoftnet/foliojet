package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.RetainedTextLimit;

import net.zamasoft.foliojet.layout.box.content.BreakToken;
import net.zamasoft.foliojet.layout.box.content.FloatMeasurement;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.layout.box.params.FloatSide;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;

import net.zamasoft.foliojet.layout.box.params.ClearMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.constraint.AxisSpan;
import net.zamasoft.foliojet.layout.constraint.ExclusionSpace;
import net.zamasoft.foliojet.layout.constraint.FloatExclusion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Insets;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineAbsoluteQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.TableBuilder;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextControl;

public class BlockBuilder implements Builder, LayoutContext {
	private static final Logger LOG = Logger.getLogger(BlockBuilder.class.getName());

	protected LayoutStack layoutStack;

	protected Flow contextFlow;

	/**
	 * 包含ブロックのスタック。
	 */
	protected List<Flow> flowStack = null;

	protected TextBuilder textBuilder = null;

	private RetainedTextLimit.Scope retainedContext;
	private RetainedTextLimit.Scope retainedRoot;
	private java.util.Map<Integer, RetainedTextLimit.Scope> retainedFlows;

	/** 有効時だけ生成する、container 所有の段落イベント queue。 */
	private net.zamasoft.foliojet.layout.text.bidi.BidiParagraphLayout.Session bidiParagraph;

	/**
	 * 上流の {@code GlyphHandler} で現在開いているテキストランです。
	 *
	 * <p>行間の断片化は {@link #textBuilder} を閉じることがありますが、
	 * shaper 側の同じランはその後も glyph を送り続けられます。その場合は
	 * 次の glyph で新しい {@link TextBuilder} とランを遅延再開するため、
	 * ランのフォント状態を builder の寿命とは独立に保持します。</p>
	 */
	private FontStyle openRunFontStyle = null;
	private FontMetrics openRunFontMetrics = null;

	/**
	 * ブロック境界で<b>テキストビルダーが開いたままでない</b>ことを検査します
	 * (2026-07-26、assertから fail-closed へ昇格)。
	 *
	 * <p>
	 * この不変条件が破れると、<b>本番では黙って内容が落ちる</b>ことを実測で
	 * 確認した——ランダム生成のstrict seed 890は、assertionを切ると変換に
	 * 成功したまま段落3つ({@code column-count:3}のブロック丸ごと)を出力から
	 * 失う。assertionが有効なら同じ文書は明示的に失敗する。
	 * </p>
	 *
	 * <p>
	 * 証券レポート等では「出力されない」より「間違ったものが出力される」方が
	 * 桁違いに危険なので、<b>テスト・本番を問わず例外にする</b>。
	 * 検査自体はnull比較で費用ゼロ。{@code RootBuilder.pageBreak}の
	 * 深さ検査が2026-07-21に同じ理由で昇格済みで、これはその第2弾。
	 * </p>
	 *
	 * @param context 例外メッセージに載せる文脈(要素など)
	 */
	protected final void requireNoOpenTextBuilder(final Object context) {
		if (this.textBuilder != null) {
			throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
					"text builder still open at a block boundary: " + context);
		}
	}

	/**
	 * Knuth-Plass行分割({@code text-wrap-style: pretty})の蓄積
	 * セッションです(2026-07-23、M3c増分3)。オプトインが有効で段落が
	 * 適格な場合のみ{@link #requireTextBlock()}で開始され、記録中は
	 * テキストイベントを{@link #textBuilder}へ配達せず蓄積する。既定
	 * (legacy)では常にnullで挙動不変。
	 */
	TotalFitSession textSession = null;

	/**
	 * 次のテキストブロックの継続状態です。
	 */
	protected BreakToken breakToken = BreakToken.NONE;

	protected double poLastMargin = 0, neLastMargin = 0;

	/**
	 * 配置中の通常のフローのボックスの、コンテキストボックスに対する位置です。
	 */
	protected double lineAxis = 0, pageAxis = 0;

	/**
	 * 次の行またはフローの開始で追加する浮動ボックスです。
	 */
	protected List<IFloatBox> toAddFloatings = null;

	/**
	 * 追加済みの浮動ボックスです。
	 */
	protected List<Floating> floatings = null;
	/** overflow:hiddenまたはwriting-mode変更で独立したfloat台帳。 */
	private List<List<Floating>> noOverflowFloatings = null;
	/** {@link #noOverflowFloatings}と同じ順で、その台帳を所有するflow box。 */
	private List<AbstractContainerBox> independentFloatScopeOwners = null;

	/**
	 * {@link #floatings}台帳の世代カウンタです(2026-07-24、E-5——codex
	 * アーキレビュー指摘「照会ごとのO(N) snapshot再構築」の解消)。台帳を
	 * 変更する全ての口が{@link #noteFloatingsChanged()}を呼んで
	 * インクリメントする:
	 * <ul>
	 * <li>{@link #addFloating(LayoutContext.Floating)}——要素追加</li>
	 * <li>{@link #addFloating(IFloatBox)}——{@code FLOAT_COMP}安定ソート
	 * (並び順は{@code FloatExclusion.order}とスナップショット内容に
	 * 影響する)</li>
	 * <li>{@link #endFlowBlock()}——overflow:hiddenスコープpop時の
	 * {@code removeAll}</li>
	 * <li>{@code BreakableBuilder.resetFragmentCursor()}——フラグメント
	 * 境界での台帳リセット({@code floatings = null})</li>
	 * </ul>
	 * 要素{@link LayoutContext.Floating}は全フィールドfinalの不変値で、
	 * スナップショットが参照する{@code box.getFloatPos().floating}も
	 * 台帳追加後に書き換わることはない({@code StyleBuilder}・
	 * {@code FloatPosTemplate}ともレイアウト開始前の構築時のみ書く)ため、
	 * リスト自体の追加・削除・並び替えだけを世代に数えれば足りる。
	 */
	private int floatingsGeneration = 0;

	/** {@link #snapshotExclusions()}のキャッシュ(不変値なので共有可)。 */
	private ExclusionSpace cachedExclusions = null;

	/** {@link #cachedExclusions}を構築した時点の世代。 */
	private int cachedExclusionsGeneration = -1;

	/**
	 * {@link #floatings}台帳の変更を記録します(E-5、世代キャッシュの
	 * 無効化)。変更操作を増やす場合は必ずこれを呼ぶこと——呼び漏らしは
	 * staleなスナップショット=実挙動バグになる。迷ったら安全側
	 * (余分なインクリメントは再構築が増えるだけで正しさは保たれる)。
	 */
	final void noteFloatingsChanged() {
		++this.floatingsGeneration;
		this.cachedExclusions = null;
	}

	/**
	 * 通常の浮動体台帳と、各独立BFCが共有する浮動体台帳をページ軸方向へ
	 * 平行移動します。同じ{@link Floating}は複数の台帳に同一identityで
	 * 現れるため、identityによるold→new対応を一度だけ作り、すべての
	 * 参照を同じ新インスタンスへ置き換えます。これにより台帳間のaliasを
	 * 保ったまま、排除域スナップショットのキャッシュも無効化します。
	 *
	 * @param dy ページ軸方向の移動量
	 */
	public final void shiftFloatLedgers(final double dy) {
		final java.util.IdentityHashMap<Floating, Floating> shifted = new java.util.IdentityHashMap<>();
		if (this.floatings != null) {
			for (final Floating floating : this.floatings) {
				shifted.put(floating, floating.shiftedPageAxis(dy));
			}
		}
		if (this.noOverflowFloatings != null) {
			for (final List<Floating> ledger : this.noOverflowFloatings) {
				for (final Floating floating : ledger) {
					shifted.computeIfAbsent(floating, key -> key.shiftedPageAxis(dy));
				}
			}
		}
		if (this.floatings != null) {
			this.replaceShiftedFloatings(this.floatings, shifted);
		}
		if (this.noOverflowFloatings != null) {
			for (final List<Floating> ledger : this.noOverflowFloatings) {
				this.replaceShiftedFloatings(ledger, shifted);
			}
		}
		this.noteFloatingsChanged();
	}

	/**
	 * 現在有効な通常float台帳と、開いている独立BFCの台帳が持つ
	 * ページ軸終端の最大値を返します。同じ要素が両方へ現れても最大値だけを
	 * 求めるため、identityの重複は結果に影響しません。
	 *
	 * @return 配置済みfloatがなければ0、あればそのページ軸終端の最大値
	 */
	protected final double maxActiveFloatingPageEnd() {
		double pageEnd = 0;
		if (this.floatings != null) {
			for (final Floating floating : this.floatings) {
				pageEnd = Math.max(pageEnd, floating.pageEnd);
			}
		}
		if (this.noOverflowFloatings != null) {
			for (final List<Floating> ledger : this.noOverflowFloatings) {
				for (final Floating floating : ledger) {
					pageEnd = Math.max(pageEnd, floating.pageEnd);
				}
			}
		}
		return pageEnd;
	}

	private void replaceShiftedFloatings(final List<Floating> ledger,
			final java.util.IdentityHashMap<Floating, Floating> shifted) {
		for (int i = 0; i < ledger.size(); ++i) {
			final Floating replacement = shifted.get(ledger.get(i));
			if (replacement != null) {
				ledger.set(i, replacement);
			}
		}
	}

	/**
	 * 開いている通常フローのページ軸位置と現在のカーソルを同量だけ
	 * 平行移動します。各フローのボックス、行軸位置、枠量と
	 * {@code line-clamp}状態は保ちます。{@link #contextFlow}はflowStackの
	 * 外にあるページ自身の{@code (0, 0)}基準なので移動しません。
	 *
	 * @param dy ページ軸方向の移動量
	 */
	public final void shiftFlowStack(final double dy) {
		if (this.flowStack != null) {
			for (int i = 0; i < this.flowStack.size(); ++i) {
				this.flowStack.set(i, this.flowStack.get(i).shiftedPageAxis(dy));
			}
		}
		this.pageAxis += dy;
	}

	/**
	 * 浮動ボックスをページ方向の底辺がページ開始位置にあるものから順に整列します。
	 */
	private static Comparator<Object> FLOAT_COMP = new Comparator<Object>() {
		public int compare(Object o1, Object o2) {
			double a, b;
			if (o1 instanceof LayoutContext.Floating) {
				LayoutContext.Floating c1 = (LayoutContext.Floating) o1;
				a = c1.pageEnd;
			} else {
				Double c1 = (Double) o1;
				a = c1.doubleValue();
			}
			if (o2 instanceof LayoutContext.Floating) {
				LayoutContext.Floating c2 = (LayoutContext.Floating) o2;
				b = c2.pageEnd;
			} else {
				Double c2 = (Double) o2;
				b = c2.doubleValue();
			}
			return (a > b) ? 1 : ((a == b) ? 0 : -1);
		}
	};

	public BlockBuilder(LayoutStack layoutStack, AbstractContainerBox contextBox) {
		this.layoutStack = layoutStack;
		if (contextBox != null) {
			this.contextFlow = new Flow(contextBox, 0, 0);
			if (!(this instanceof ColumnBuilder) && retainsFlowContent(contextBox)) {
				this.retainedRoot = this.enterRetained(contextBox);
			}
		}
	}

	private static boolean retainsFlowContent(final AbstractContainerBox box) {
		return box instanceof net.zamasoft.foliojet.layout.box.impl.GridBox
				|| box instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox
				|| (box.getColumnCount() > 1 && box.getBlockParams().columns.fill == Columns.FILL_BALANCE);
	}

	private RetainedTextLimit.Scope enterRetained(final AbstractContainerBox box) {
		final RetainedTextLimit limit = RetainedTextLimit.get(this);
		return limit == null ? null : limit.enter(RetainedTextLimit.elementName(box.getParams(), "block"));
	}

	private void beginRetainedContext(final AbstractContainerBox box) {
		this.retainedContext = this.retainedRoot == null ? this.enterRetained(box) : this.retainedRoot;
		this.retainedRoot = null;
	}

	/** 固定幅の独立ビルダーは、親への配置後に呼び側のfinallyで閉じます。 */
	public void finishRetainedContext() {
		if (this.retainedContext != null) {
			this.retainedContext.close();
			this.retainedContext = null;
		}
	}

	public Builder getParentBuilder() {
		return (Builder) this.layoutStack;
	}

	public AbstractContainerBox getFixedWidthContextBox() {
		AbstractContainerBox box = this.getRootBox();
		if (box.getBlockParams().size.getWidthType() != LengthType.AUTO) {
			if (!box.getBlockParams().size.getWidthType().needsReference() || !box.getType().isTableInternal()) {
				return box;
			}
		}
		if (this.layoutStack == null) {
			return null;
		}
		switch (box.getPos().getType()) {
		case FLOW:
		case FLOAT:
		case INLINE:
		case TABLE_CELL:
			return this.layoutStack.getFixedWidthFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedWidthContextBox();
		default:
			throw new IllegalStateException();
		}
	}

	public AbstractContainerBox getFixedHeightContextBox() {
		AbstractContainerBox box = this.getRootBox();
		if (box.getBlockParams().size.getHeightType() != LengthType.AUTO) {
			if (!box.getBlockParams().size.getHeightType().needsReference() || !box.getType().isTableInternal()) {
				return box;
			}
		}
		if (this.layoutStack == null) {
			return null;
		}
		switch (box.getPos().getType()) {
		case FLOW:
		case FLOAT:
		case INLINE:
		case TABLE_CELL:
			return this.layoutStack.getFixedHeightFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedHeightContextBox();
		default:
			throw new IllegalStateException(String.valueOf(box.getClass()));
		}
	}

	public double getFixedWidth() {
		double frameWidth = 0;
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				BlockParams params = flow.box.getBlockParams();
				frameWidth += flow.box.getFrame().getFrameWidth();
				if (!params.flow.isVertical()) {
					// 横書き
					return flow.box.getWidth() - frameWidth;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// 幅が指定されている
					return flow.box.getWidth() - frameWidth;
				}
			}
		}
		AbstractContainerBox box = this.getFixedWidthContextBox();
		return box == null ? 0 : box.getWidth() - frameWidth;
	}

	public AbstractContainerBox getFixedWidthFlowBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				BlockParams params = flow.box.getBlockParams();
				if (!params.flow.isVertical()) {
					// 横書き
					return flow.box;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// 幅が指定されている
					return flow.box;
				}
			}
		}
		return this.getFixedWidthContextBox();
	}

	public AbstractContainerBox getFixedHeightFlowBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				final BlockParams params = flow.box.getBlockParams();
				if (params.flow.isVertical()) {
					// 縦書き
					return flow.box;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// 幅が指定されている
					return flow.box;
				}
			}
		}
		return this.getFixedHeightContextBox();
	}

	public double getFixedHeight() {
		double frameHeight = 0;
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				BlockParams params = flow.box.getBlockParams();
				frameHeight += flow.box.getFrame().getFrameHeight();
				if (params.flow.isVertical()) {
					// 縦書き
					return flow.box.getHeight() - frameHeight;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// 幅が指定されている
					return flow.box.getHeight() - frameHeight;
				}
			}
		}
		AbstractContainerBox box = this.getFixedHeightContextBox();
		return box == null ? 0 : box.getInnerHeight() - frameHeight;
	}

	/**
	 * ページ文脈(根のbuilder)です。<b>持たないことがある</b>——表セルの
	 * 再レイアウト用builder({@code TableRowBox}が
	 * {@code new BlockBuilder(null, ...)}で作る)は版面に紐づかないため。
	 * 呼び出し側は既にnullを想定しており({@code optimizedTextEnabled}の
	 * 「ページ文脈を持たない再レイアウト用ビルダー」分岐等)、ここだけが
	 * null安全でなかった(2026-08-02、掃過のNPEで発覚)。
	 */
	public RootBuilder getPageContext() {
		return this.layoutStack == null ? null : this.layoutStack.getPageContext();
	}

	final boolean paragraphBidiEnabled() {
		return this.getFlowBox().getBlockParams().paragraphBidi;
	}

	final void noteBidiLine(final net.zamasoft.foliojet.layout.box.impl.TextBlockBox block,
			final net.zamasoft.foliojet.layout.box.AbstractLineBox line) {
		if (!this.paragraphBidiEnabled()) {
			return;
		}
		if (this.bidiParagraph == null) {
			this.bidiParagraph = new net.zamasoft.foliojet.layout.text.bidi.BidiParagraphLayout.Session();
		}
		this.bidiParagraph.line(block, line);
	}

	final void seedBidiReplayPrefix(
			final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix prefix) {
		if (!this.paragraphBidiEnabled() || prefix.isEmpty()) {
			return;
		}
		if (this.bidiParagraph == null) {
			this.bidiParagraph = new net.zamasoft.foliojet.layout.text.bidi.BidiParagraphLayout.Session();
		}
		this.bidiParagraph.replayPrefix(prefix);
	}

	/** float/absolute/bound などの外側の順序境界を段落 queue へ残す。 */
	public final void noteBidiBarrier(final Object payload) {
		if (!this.paragraphBidiEnabled()) {
			return;
		}
		if (this.bidiParagraph == null) {
			this.bidiParagraph = new net.zamasoft.foliojet.layout.text.bidi.BidiParagraphLayout.Session();
		}
		this.bidiParagraph.barrier(payload);
	}

	final void resolveBidiParagraph(final BlockParams params) {
		if (this.bidiParagraph == null) {
			return;
		}
		this.bidiParagraph.resolve(params);
		this.bidiParagraph = null;
	}

	/** 改ページは段落終端にせず、確定ページの描画用 tree だけ先行生成する。 */
	final void previewBidiParagraph(final BlockParams params) {
		if (this.bidiParagraph != null) {
			this.bidiParagraph.preview(params);
		}
	}

	/**
	 * <b>協調的な中断点</b>(2026-07-27新設)。長く走るループの先頭で
	 * 呼びます。
	 *
	 * <p>
	 * <b>ページの境目だけでは足りない。</b>変換を外から止める手段は
	 * {@code abort()}しかないが、それは旗を立てるだけで、エンジンが旗を
	 * 読む場所がなければ何も起きない。従来は読む場所が
	 * {@code UserAgent.nextPage()}=ページの境目だけだったので、
	 * <b>1ページの処理が終わらない文書は永久に止められなかった</b>。
	 * </p>
	 *
	 * <p>
	 * 見るのは{@code ABORT_FORCE}だけ。{@code ABORT_NORMAL}は
	 * 「次のページの区切りで綺麗に止める」意味なので、ページの途中では
	 * 反応してはいけない。
	 * </p>
	 */
	protected final void checkAbort() {
		// ページ文脈を持たないbuilder(表セルの再レイアウト用)では
		// 中断の旗を読む相手が居ない——外から止める必要があるループは
		// 版面側で回るため、ここは何もしないでよい(2026-08-02、掃過のNPE)
		final RootBuilder root = this.getPageContext();
		if (root == null) {
			return;
		}
		root.getPageGenerator().getUserAgent().checkAbort(jp.cssj.cti2.CTISession.ABORT_FORCE);
	}

	private int getFloatingCount() {
		return this.floatings == null ? 0 : this.floatings.size();
	}

	private Floating getFloating(int index) {
		return (Floating) this.floatings.get(index);
	}

	public void setPageAxis(double pageAxis) {
		this.pageAxis = pageAxis;
	}

	public double getPageAxis() {
		return this.pageAxis;
	}

	public boolean isTwoPass() {
		return false;
	}

	public boolean isMain() {
		return false;
	}

	/**
	 * 現在のフローか上位にある最初のpositionが指定されているボックスを返す。
	 */
	public AbstractContainerBox getContextBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				AbstractContainerBox box = flow.box;
				if (box.isContextBox()) {
					return flow.box;
				}
			}
		}
		AbstractContainerBox box = this.contextFlow.box;
		if (this.layoutStack == null) {
			return box;
		}
		if (!box.isContextBox()) {
			return this.layoutStack.getContextBox();
		}
		return box;
	}

	public AbstractContainerBox getMulticolumnBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				if (flow.box.getColumnCount() > 1) {
					return flow.box;
				}
			}
		}
		return null;
	}

	/**
	 * 現在のフローからrootまでの間の最初のpositionが指定されているボックスを返す。
	 * 
	 * @return
	 */
	Flow getSubContextFlow() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				AbstractContainerBox box = flow.box;
				if (box.isContextBox()) {
					return flow;
				}
			}
		}
		return this.contextFlow;
	}

	public AbstractContainerBox getRootBox() {
		return this.contextFlow.box;
	}

	public AbstractContainerBox getFlowBox() {
		return this.getFlow().box;
	}

	public Flow getFlow() {
		if (this.flowStack == null || this.flowStack.isEmpty()) {
			return this.contextFlow;
		}
		return (Flow) this.flowStack.get(this.flowStack.size() - 1);
	}

	/**
	 * contextFlowより内側に、閉じるべきopen flowが残っているかを返します。
	 *
	 * <p>
	 * 閉部分木のrestyle中にも、その内容が段からあふれると入れ子の
	 * COLUMN継続が発生します。ownerが{@link #contextFlow}の場合、継続は
	 * {@code pruneFlowStackTo(contextFlow)}はrestyle呼出し元が積んだフローを
	 * 消費し、COLUMN継続が同じ構造深度を別の箱identityで積み直すことが
	 * あります。閉じたrestyleの各Java呼出しフレームは、その新しい最上位を
	 * 1段ずつ閉じる必要があります。一方、継続が内側を全て消費して何も
	 * 積み直さなかった場合だけ、余った古い呼出しフレームは閉じる対象を
	 * 持ちません(2026-08-25、extreme strict seed 4540)。
	 * </p>
	 */
	public final boolean hasOpenFlow() {
		return this.flowStack != null && !this.flowStack.isEmpty();
	}

	public int getFlowCount() {
		return this.flowStack == null ? 1 : this.flowStack.size() + 1;
	}

	public Flow getFlow(int index) {
		if (index == 0) {
			return this.contextFlow;
		}
		return (Flow) this.flowStack.get(index - 1);
	}

	public void startFlowBlock(final FlowBlockBox flowBox) {
		this.startFlowBlock(flowBox, 0, 0);
	}

	/**
	 * 行方向の内側への差し込みを与えて通常フローのブロックを開きます。
	 *
	 * <p>
	 * 表のキャプション専用の入口です。CSS 2.1 §17.4のとおり、表要素の
	 * {@code margin}は表そのものではなく<b>ラッパー箱</b>に付き、キャプションの
	 * 包含ブロックはラッパーの内容箱——つまり<b>表のborder box</b>です。
	 * copper4はラッパーの内容幅を表のmargin boxにしているので、そのまま並べると
	 * キャプションが表のマージンぶんだけ外側へはみ出す(2026-08-30、
	 * Wikipediaのサムネイルの説明文が図の左へずれる欠陥)。ここで差し込んで
	 * 表のborder boxに合わせる。
	 * </p>
	 *
	 * @param insetStart 行方向先頭側の差し込み幅
	 * @param insetEnd   行方向末尾側の差し込み幅
	 */
	public void startFlowBlock(final FlowBlockBox flowBox, final double insetStart, final double insetEnd) {
		this.requireNoOpenTextBuilder("(no context)");
		AbstractContainerBox containerBox = this.getFlowBox();
		final BlockParams cParams = containerBox.getBlockParams();
		double xmargin = 0;
		double lineSize = containerBox.getLineSize();
		if (flowBox.getColumnCount() > 1
				|| flowBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox
				|| flowBox instanceof net.zamasoft.foliojet.layout.box.impl.GridBox) {
			// マルチカラムの場合浮動ボックスを避ける(排除域は
			// ExclusionSpace queryへ一本化済み——2026-07-23、P0完了)。
			// flex/gridコンテナも独立整形文脈のためfloatと重ならない
			// (CSS 2.1 §9.5——border boxがfloatのmargin boxを避ける。
			// 2026-08-27、asahi.comフッターのfloatラベルへ隣のflexリストが
			// 重なった実バグ)。帯はコンテナ開始時点の排除域で確定する
			final ExclusionSpace snapshot = this.snapshotExclusions();
			final AxisSpan band = snapshot.narrowLineBandForMulticol(this.pageAxis,
					new AxisSpan(this.lineAxis, this.lineAxis + lineSize));
			xmargin = band.start() - this.lineAxis;
			lineSize = band.extent();
		}
		if (insetStart != 0 || insetEnd != 0) {
			final double inset = insetStart + insetEnd;
			if (inset < lineSize) {
				xmargin += insetStart;
				lineSize -= inset;
			}
		}
		flowBox.calculateSize(this, xmargin, lineSize);
		final FlowPos pos = flowBox.getFlowPos();

		if (establishesIndependentFloatScope(flowBox, cParams)) {
			// overflow:hiddenとwriting-mode変更はいずれも独立BFCを作り、
			// 内側のfloatを親の排除域へ漏らさない。
			if (this.noOverflowFloatings == null) {
				this.noOverflowFloatings = new ArrayList<List<Floating>>();
				this.independentFloatScopeOwners = new ArrayList<AbstractContainerBox>();
			}
			this.noOverflowFloatings.add(new ArrayList<Floating>());
			this.independentFloatScopeOwners.add(flowBox);
		}

		final AbsoluteRectFrame frame = flowBox.getFrame();

		if (pos.clear != ClearMode.NONE && this.getFloatingCount() > 0) {
			// clearが指定されている場合
			final double marginStart;
			if (cParams.flow.isVertical()) {
				marginStart = frame.margin.right;
			} else {
				marginStart = frame.margin.top;
			}
			final double pageStart = this.pageAxis - marginStart;
			final ExclusionSpace snapshot = this.snapshotExclusions();
			final FloatExclusion found = snapshot.findClearBoundary(pageStart, marginStart, pos.clear);
			if (found != null) {
				// 浮動ボックスの下につける
				this.poLastMargin = this.neLastMargin = 0;
				this.pageAxis = found.pageSpan().end() - marginStart;
			}
		}

		// 開始位置マージンのつぶし
		// SPEC CSS 2.1 8.3.1
		// 正のマージンでは大きいほうが採用される
		// 正と負のマージンでは両方が足される
		// 負と負のマージンでは絶対値が大きいほうが採用される
		LayoutContext.Flow parentFlow = this.getFlow(this.getFlowCount() - 1);
		double marginStart, frameStart, frameHead;
		boolean bordered;
		if (cParams.flow.isVertical()) {
			// 縦書き
			marginStart = frame.margin.right;
			frameHead = frame.getFrameTop();
			frameStart = frame.getFrameRight();
			bordered = frame.padding.right > 0 || !frame.frame.border.getRight().isNull();
		} else {
			// 横書きのフロー
			marginStart = frame.margin.top;
			frameHead = frame.getFrameLeft();
			frameStart = frame.getFrameTop();
			bordered = frame.padding.top > 0 || !frame.frame.border.getTop().isNull();
		}
		if (marginStart >= 0) {
			if (marginStart > this.poLastMargin) {
				this.pageAxis -= this.poLastMargin;
				this.poLastMargin = marginStart;
			} else {
				this.pageAxis -= marginStart;
			}
		} else {
			if (marginStart < this.neLastMargin) {
				this.pageAxis -= this.neLastMargin;
				this.neLastMargin = marginStart;
			} else {
				this.pageAxis -= marginStart;
			}
		}
		if (bordered) {
			this.poLastMargin = this.neLastMargin = 0;
		}

		this.lineAxis += frameHead;
		parentFlow.box.addFlow(flowBox, this.pageAxis - parentFlow.pageAxis);
		this.pageAxis += frameStart;

		if (this.flowStack == null) {
			this.flowStack = new ArrayList<Flow>();
		}
		final Flow flow = new Flow(flowBox, this.lineAxis, this.pageAxis, frameHead);
		this.flowStack.add(flow);
		if (retainsFlowContent(flowBox)) {
			if (this.retainedFlows == null) this.retainedFlows = new java.util.HashMap<>();
			if (!this.retainedFlows.containsKey(this.flowStack.size())) {
				this.retainedFlows.put(this.flowStack.size(), this.enterRetained(flowBox));
			}
		}
		this.breakToken = BreakToken.NONE;
	}

			/**
	 * 現在の{@link #floatings}を{@link ExclusionSpace}へ変換した
	 * スナップショットを返します(2026-07-23新設、P0 Step4以降は
	 * 実レイアウトに使用。{@code TextBuilder}もこれを共用する)。
	 * {@code this.floatings}は既に{@code FLOAT_COMP}(pageEnd昇順、
	 * 同値は追加順)でソート済みのため、並び順をそのまま
	 * {@link ExclusionSpace#copyOfSorted}へ渡すO(N)一括構築で足りる
	 * (2026-07-23、codexレビュー指摘のO(N²)解消)。
	 *
	 * <p>
	 * 台帳が前回構築時から変わっていなければ({@link #floatingsGeneration}
	 * 世代一致)、再構築せずキャッシュ済みの不変スナップショットを返す
	 * (2026-07-24、E-5——照会ごとのO(N)再構築の解消)。
	 * </p>
	 */
	ExclusionSpace snapshotExclusions() {
		final int count = this.getFloatingCount();
		if (count == 0) {
			return ExclusionSpace.EMPTY;
		}
		if (this.cachedExclusions != null && this.cachedExclusionsGeneration == this.floatingsGeneration) {
			return this.cachedExclusions;
		}
		final List<FloatExclusion> exclusions = new ArrayList<>(count);
		// shape-outside(2026-08-29)の解決に要る文脈。書字方向は根ボックス、
		// shape-marginの%基準は包含ブロックの行方向幅。台帳が変わらない限り
		// キャッシュに乗るので、行ごとに形状を作り直すことはない
		final BlockParams rootParams = this.getRootBox().getBlockParams();
		final WritingMode progression = rootParams.flow;
		final double containingLineSize = this.getFlowBox().getLineSize();
		for (int i = 0; i < count; ++i) {
			final LayoutContext.Floating floating = this.getFloating(i);
			final FloatPos floatingPos = floating.box.getFloatPos();
			final net.zamasoft.foliojet.layout.constraint.ExclusionShape shape = floatingPos.shapeOutside == null
					? null
					: FloatShapeResolver.resolve(floating.box, floating.lineStart, floating.pageStart, rootParams,
							containingLineSize);
			exclusions.add(new FloatExclusion(i, floatingPos.floating,
					new AxisSpan(floating.pageStart, floating.pageEnd),
					new AxisSpan(floating.lineStart, floating.lineEnd), shape));
		}
		final ExclusionSpace snapshot = ExclusionSpace.copyOfSorted(exclusions);
		this.cachedExclusions = snapshot;
		this.cachedExclusionsGeneration = this.floatingsGeneration;
		return snapshot;
	}

	/**
	 * 行組み用に通常フロートとページフロートを別々に走査し、共通の
	 * 利用可能帯へ合成します。
	 *
	 * <p>
	 * bottomページフロートは現在位置より未来から始まるため、通常floatの
	 * 「最初の未来開始で打ち切る」スナップショットへ混ぜない。通常集合は
	 * 従来の{@link ExclusionSpace#scanLineBand}、ページ集合だけは全件走査し、
	 * lineStartの最大、lineEndとmaxPageSizeの最小を採る。clear、BFC回避、
	 * 通常float配置は従来どおり{@link #snapshotExclusions()}だけを見る。
	 * </p>
	 */
	final ExclusionSpace.LineScan scanLineBandForLineLayout(final double pageStart, final double lineHeight,
			final double lineStart0, final double lineEnd0) {
		final ExclusionSpace ordinarySpace = this.snapshotExclusions();
		final ExclusionSpace.LineScan ordinary = ordinarySpace.scanLineBand(pageStart, lineHeight, lineStart0,
				lineEnd0);
		final ExclusionSpace pageSpace = this.pageFloatExclusionsForLineLayout();
		if (pageSpace.isEmpty()) {
			// 通常floatだけの文書は結果オブジェクトも含め従来経路をそのまま返す。
			return ordinary;
		}
		final ExclusionSpace.LineScan page = pageSpace.scanLineBandFully(pageStart, lineHeight, lineStart0, lineEnd0);
		final int startOrder = LayoutUtils.compare(page.lineStart(), ordinary.lineStart());
		final int endOrder = LayoutUtils.compare(page.lineEnd(), ordinary.lineEnd());
		final boolean maxPageSizeSet = ordinary.maxPageSizeSet() || page.maxPageSizeSet();
		final double maxPageSize;
		if (!ordinary.maxPageSizeSet()) {
			maxPageSize = page.maxPageSize();
		} else if (!page.maxPageSizeSet()) {
			maxPageSize = ordinary.maxPageSize();
		} else {
			maxPageSize = Math.min(ordinary.maxPageSize(), page.maxPageSize());
		}
		return new ExclusionSpace.LineScan(
				startOrder > 0 ? page.startExclusion()
						: startOrder < 0 ? ordinary.startExclusion()
								: laterEnding(ordinary.startExclusion(), page.startExclusion()),
				endOrder < 0 ? page.endExclusion()
						: endOrder > 0 ? ordinary.endExclusion()
								: laterEnding(ordinary.endExclusion(), page.endExclusion()),
				Math.max(ordinary.lineStart(), page.lineStart()), Math.min(ordinary.lineEnd(), page.lineEnd()),
				maxPageSizeSet, maxPageSize);
	}

	/** 同じ行境界を作る排除域のうち、境界が最後まで残る方を返す。 */
	private static FloatExclusion laterEnding(final FloatExclusion a, final FloatExclusion b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		final int endOrder = Double.compare(a.pageSpan().end(), b.pageSpan().end());
		if (endOrder != 0) {
			return endOrder > 0 ? a : b;
		}
		return a.order() >= b.order() ? a : b;
	}

	final boolean hasLineExclusions() {
		return !this.snapshotExclusions().isEmpty() || !this.pageFloatExclusionsForLineLayout().isEmpty();
	}

	/** Root座標の行組みだけが別走査するページフロート排除域。 */
	protected ExclusionSpace pageFloatExclusionsForLineLayout() {
		return ExclusionSpace.EMPTY;
	}

	protected final RetainedTextLimit.Scope takeRetainedFlow() {
		return this.retainedFlows == null ? null
				: this.retainedFlows.remove(this.flowStack == null ? 0 : this.flowStack.size());
	}

	public void endFlowBlock() {
		final var retained = this.takeRetainedFlow();
		try (retained) {
			this.endFlowBlockContent();
		}
	}

	private void endFlowBlockContent() {
		this.requireNoOpenTextBuilder("(no context)");
		final Flow flow = (Flow) this.flowStack.remove(this.flowStack.size() - 1);
		final FlowBlockBox flowBox = (FlowBlockBox) flow.box;
		final BlockParams params = flowBox.getBlockParams();
		final Flow parentFlow = this.getFlow();
		final BlockParams parentParams = parentFlow.box.getBlockParams();

		if (flowBox.getColumnCount() > 1 && params.columns.fill == Columns.FILL_BALANCE) {
			// カラムのバランス
			this.pageAxis = flow.pageAxis;
			flowBox.balance(this);
		}
		if (establishesIndependentFloatScope(flowBox, parentParams)) {
			// 独立BFC内のfloatを親の排除域から外す。
			assert this.independentFloatScopeOwners.get(this.independentFloatScopeOwners.size() - 1) == flowBox;
			this.independentFloatScopeOwners.remove(this.independentFloatScopeOwners.size() - 1);
			final List<Floating> floatings = this.noOverflowFloatings.remove(this.noOverflowFloatings.size() - 1);
			// CSS 2.1 §10.6.7: auto高さのBFCは、そのBFCに属するfloatの
			// margin box下端を含む。通常フローのカーソルだけで高さを決めると、
			// 改ページ後の短い本文(55.2pt)を高さにして、先頭へ移った画像float
			// (150pt)をoverflow:hiddenで切ってしまう(Yahoo!ニュース実例)。
			// 配置台帳のpageEndはこのbuilderと同じページ軸座標なので、scopeを
			// 外す前にauto箱のカーソルと箱寸法をfloat下端まで伸ばす。
			if (!flowBox.isSpecifiedPageSize()) {
				for (int i = 0; i < floatings.size(); ++i) {
					this.pageAxis = Math.max(this.pageAxis, floatings.get(i).pageEnd);
				}
				flowBox.setPageAxis(this.pageAxis - flow.pageAxis);
			}
			if (this.floatings != null) {
				this.floatings.removeAll(floatings);
				this.noteFloatingsChanged();
			}
		}

		final AbsoluteRectFrame frame = flowBox.getFrame();
		final double marginEnd, frameEnd;
		boolean bordered;
		if (parentParams.flow.isVertical()) {
			// 縦書き
			marginEnd = frame.margin.left;
			bordered = frame.padding.left > 0 || !frame.frame.border.getLeft().isNull()
					|| params.overflow != OverflowMode.VISIBLE || flowBox.getColumnCount() > 1;
			double width = flowBox.getInnerWidth();
			if (flowBox.getContentSize() != width || bordered) {
				this.pageAxis = flow.pageAxis + width;
				if (params.size.getWidthType() == LengthType.ABSOLUTE && width > 0) {
					bordered = true;
				}
			}
			frameEnd = frame.getFrameLeft();
		} else {
			// 横書き
			marginEnd = frame.margin.bottom;
			bordered = frame.padding.bottom > 0 || !frame.frame.border.getBottom().isNull()
					|| params.overflow != OverflowMode.VISIBLE || flowBox.getColumnCount() > 1;
			double height = flowBox.getInnerHeight();
			if (flowBox.getContentSize() != height || bordered) {
				this.pageAxis = flow.pageAxis + height;
				if (params.size.getHeightType() == LengthType.ABSOLUTE && height > 0) {
					bordered = true;
				}
			}
			frameEnd = frame.getFrameBottom();
		}
		if (bordered) {
			if (marginEnd >= 0) {
				this.poLastMargin = marginEnd;
				this.neLastMargin = 0;
			} else {
				this.poLastMargin = 0;
				this.neLastMargin = marginEnd;
			}
		} else {
			if (marginEnd >= 0) {
				if (marginEnd > this.poLastMargin) {
					this.pageAxis -= this.poLastMargin;
					this.poLastMargin = marginEnd;
				} else {
					this.pageAxis -= marginEnd;
				}
			} else {
				if (marginEnd < this.neLastMargin) {
					this.pageAxis -= this.neLastMargin;
					this.neLastMargin = marginEnd;
				} else {
					this.pageAxis -= marginEnd;
				}
			}
		}
		this.pageAxis += frameEnd;

		parentFlow.box.setPageAxis(this.pageAxis - parentFlow.pageAxis);
		// **積んだ量をそのまま戻す。** frame から取り直してはいけない——
		// margin:auto はフローの内側で解決されるので、積んだ 0 に対して
		// 106.75 を引く、といった食い違いが起きる(Flow.frameHead の説明)
		this.lineAxis -= flow.frameHead;
	}

	/**
	 * 通常フローのボックスのauto margin・表整列(align)を物理マージンへ
	 * 解決します(addBoundから抽出した純計算、2026-07-30。演算順は
	 * 旧実装のまま。{@code amargin}へ書き込む)。
	 *
	 * <p>
	 * 注意: 旧コード同様、渡された{@code lineSize}からframeSizeを引いた
	 * 値を分配式に使う(呼び出し側の{@code lineSize}は変更されない——
	 * 旧実装でもこの計算より後に{@code lineSize}を読む箇所はない)。
	 * </p>
	 */
	private static void resolveAutoMargins(final boolean vertical, final AbsoluteRectFrame frame, final Insets margin,
			final AbsoluteInsets amargin, final double cLineSize, double lineSize, final double xMarginStart,
			final double xMarginEnd, final Align align) {
		double frameSize, marginStart, marginEnd;
		if (vertical) {
			frameSize = frame.getFrameHeight();
			marginStart = margin.getTopType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.top;
			marginEnd = margin.getBottomType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.bottom;
		} else {
			frameSize = frame.getFrameWidth();
			marginStart = margin.getLeftType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.left;
			marginEnd = margin.getRightType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.right;
		}
		lineSize -= frameSize;
		// **包含ブロックより広い箱では auto マージンを0にする**(2026-08-03)。
		//
		// CSS 2.1 §10.3.3: 幅が指定されていて合計が包含ブロックを超える場合、
		// {@code direction: ltr} では{@code margin-right}の指定が無視される
		// ——つまり<b>箱は始端に揃い、終端側へ溢れる</b>。従来は余りを機械的に
		// 2で割っていたため、余りが負のときに<b>負の始端マージン</b>ができ、
		// 内容の左半分が紙の外へ出て切れていた。
		//
		// 固定幅の版面を{@code margin: 0 auto}で中央寄せする作りは実地で
		// 極めて多い(総務省統計局のページを取り込んだ第3波で発覚、PLAN §3)。
		// 紙幅より広い版面はどのみち溢れるが、<b>始端側を守れば読める</b>。
		final double autoRemainder = cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
		if (autoRemainder < 0 && (LayoutUtils.isNone(marginStart) || LayoutUtils.isNone(marginEnd))) {
			marginStart = LayoutUtils.isNone(marginStart) ? 0 : marginStart;
			marginEnd = LayoutUtils.isNone(marginEnd) ? 0 : marginEnd;
		} else if (LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
			// 左右のマージンを同じにする
			marginStart = marginEnd = autoRemainder / 2.0;
		} else if (LayoutUtils.isNone(marginStart)) {
			// 左が不確定
			marginStart = autoRemainder;
		} else if (LayoutUtils.isNone(marginEnd)) {
			// 右が不確定
			marginEnd = autoRemainder;
		} else {
			// 制限しすぎ
			switch (align) {
			case Align.START:
				// 左寄せ
				marginEnd = 0;
				break;
			case Align.END:
				// 右寄せ
				marginStart += cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
				break;
			case Align.CENTER:
				// 中央
				double remainder = cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
				remainder /= 2.0;
				marginStart += remainder;
				marginEnd += remainder;
				break;
			default:
				throw new IllegalStateException();
			}
		}
		if (vertical) {
			amargin.top = marginStart + xMarginStart;
			amargin.bottom = marginEnd + xMarginEnd;
		} else {
			amargin.left = marginStart + xMarginStart;
			amargin.right = marginEnd + xMarginEnd;
		}
	}

	/** shaper/分綴待ちの文字。静的位置を読むときだけ計量用に配達する。 */
	java.util.function.Consumer<net.zamasoft.pdfg2d.gc.text.GlyphHandler> pendingText = measurement -> { };

	public void addBound(IBox box) {
		// M3c: float・絶対配置はTextBuilderの実状態(lineAxis/pageAxis)を
		// 読むため、K-P蓄積中なら先にlegacyへ確定させる
		if (this.textSession != null) {
			this.textSession.abortToLegacy();
		}
		this.noteBidiBarrier(box);
		switch (box.getPos().getType()) {
		case FLOW:
		case TABLE: {
			// 通常のフロー
			this.requireNoOpenTextBuilder("(no context)");
			IFlowBox flowBox = (IFlowBox) box;

			Flow flow = this.getFlow();
			BlockParams params = flow.box.getBlockParams();
			boolean vertical = params.flow.isVertical();
			AbsoluteInsets amargin;
			AbsoluteRectFrame frame;
			ClearMode clear;
			Align align;
			switch (box.getType()) {
			case REPLACED: {
				AbstractReplacedBox replacedBox = (AbstractReplacedBox) flowBox;
				LayoutUtils.calculateReplacedSize(this, replacedBox);
				frame = replacedBox.getFrame();
				FlowPos pos = (FlowPos) flowBox.getPos();
				clear = pos.clear;
				align = pos.align;
			}
				break;
			case BLOCK: {
				AbstractBlockBox blockBox = (AbstractBlockBox) flowBox;
				frame = blockBox.getFrame();
				FlowPos pos = (FlowPos) flowBox.getPos();
				clear = pos.clear;
				// 表整列の解決結果は箱ローカル(共有 pos は record 後不変)
				align = blockBox instanceof FlowBlockBox fb ? fb.getResolvedAlign() : pos.align;
			}
				break;
			case TABLE: {
				TableBox tableBox = (TableBox) flowBox;
				frame = tableBox.getFrame();
				clear = ClearMode.NONE;
				align = null;
			}
				break;
			default:
				throw new IllegalStateException();
			}
			Insets margin = frame.frame.margin;
			amargin = frame.margin;
			double lineSize = box.getLineExtent(params.flow);
			final double cLineSize = flow.box.getLineSize();
			final double lineStop = this.lineAxis + cLineSize;
			double xMarginStart = 0, lineEnd = lineStop, xMarginEnd = 0;
			if (this.getFloatingCount() > 0) {
				// clearのチェックと置換ボックスやテーブルが浮動ボックスと重ならない処理
				// *** CLEAR_NONEもチェックしていることに注意 ***
				final double pageStart;
				final double marginAdjust;
				if (vertical) {
					marginAdjust = amargin.right;
				} else {
					marginAdjust = amargin.top;
				}
				pageStart = this.pageAxis - marginAdjust;
				final ExclusionSpace snapshot = this.snapshotExclusions();
				final ExclusionSpace.BoundAvoidance found = snapshot.findBoundAvoidance(pageStart, lineSize, lineStop,
						marginAdjust, clear);
				xMarginStart = found.xMarginStart();
				lineEnd = found.lineEnd();
				if (found.clearingExclusion() != null) {
					this.poLastMargin = this.neLastMargin = 0;
					this.pageAxis = found.clearPageEnd();
				}
			}
			xMarginEnd = lineStop - lineEnd;

			//
			// ■ 通常のフローのマージンの計算(純計算は resolveAutoMargins へ — 2026-07-30)
			// flex itemのauto marginはFlexBuilderが解決済みのため再解決しない
			// (FlowBlockBox.coordinatorOwnsAutoMarginsの説明を参照)
			//
			if (align != null
					&& !(flowBox instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox fb
							&& fb.coordinatorOwnsAutoMargins())) {
				resolveAutoMargins(vertical, frame, margin, amargin, cLineSize, lineSize, xMarginStart, xMarginEnd,
						align);
			}
			if (amargin.top >= 0) {
				if (amargin.top > this.poLastMargin) {
					this.pageAxis -= this.poLastMargin;
					this.poLastMargin = amargin.top;
				} else {
					this.pageAxis -= amargin.top;
				}
			} else {
				if (amargin.top < this.neLastMargin) {
					this.pageAxis -= this.neLastMargin;
					this.neLastMargin = amargin.top;
				} else {
					this.pageAxis -= amargin.top;
				}
			}
			if (flowBox instanceof TableBox tableBox && tableBox.isIncomplete()) {
				this.poLastMargin = this.neLastMargin = 0;
			} else if (vertical) {
				this.poLastMargin = this.neLastMargin = amargin.left;
			} else {
				this.poLastMargin = this.neLastMargin = amargin.bottom;
			}
			flow.box.addFlow(flowBox, this.pageAxis - flow.pageAxis);

			if (flowBox instanceof TableBox tableBox && tableBox.isIncomplete()) {
				// getFrame() は終端を保留した有効フレーム。通常経路の演算順は維持する。
				this.pageAxis += tableBox.getInnerPageExtent(params.flow) + frame.getFramePageExtent(params.flow);
			} else {
				this.pageAxis += flowBox.getPageExtent(params.flow);
			}
			flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
		}
			break;

		case FLOAT: {
			if (box.getType() == BoxType.REPLACED) {
				AbstractReplacedBox replacedBox = (AbstractReplacedBox) box;
				LayoutUtils.calculateReplacedSize(this, replacedBox);
			}

			// 浮動体
			final IFloatBox floatBox = (IFloatBox) box;
			if (System.getProperty("foliojet.debug.floatTrace") != null) {
				final StringBuilder where = new StringBuilder();
				final StackTraceElement[] st = new Throwable().getStackTrace();
				for (int k = 1; k < Math.min(st.length, 7); ++k) {
					where.append(' ').append(st[k].getMethodName()).append(':').append(st[k].getLineNumber());
				}
				System.err.println("[float] 受理 side=" + floatBox.getFloatPos().floating + " box="
						+ System.identityHashCode(floatBox) + " 経路" + where);
			}
			if (this.textBuilder != null && this.textBuilder.getLineAxis() > 0) {
				// 行の途中に現れたフロート。行末側で現在行の残り幅に
				// 収まるなら現在行の上端へ置き、行をその場で狭める
				// (CSS 2.1 §9.5、ブラウザと同じ。kabutan 2026-08-08)。
				// 収まらないとき・行頭側・clear付きは従来どおり行末まで
				// 先送りして次の帯へ置く
				if (!this.tryFloatOnCurrentLine(floatBox)) {
					this.toAddFloating(floatBox);
				}
			} else {
				this.addFloating(floatBox);
			}
		}
			break;

		case ABSOLUTE: {
			// 絶対位置
			final IAbsoluteBox absoluteBox = (IAbsoluteBox) box;
			final AbsolutePos pos = absoluteBox.getAbsolutePos();
			final AbstractContainerBox contextBox;
			{
				// 通常の絶対配置
				// 固定配置
				final Flow flow = this.getFlow();
				contextBox = flow.box;
				double staticX = this.lineAxis - flow.lineAxis;
				double staticY = this.pageAxis - flow.pageAxis;
				if (pos.usesStaticPageAxis(flow.box.getBlockParams().flow)) {
					assert pos.autoPosition == AutoPosition.BLOCK : box.getParams();
					if (this.textBuilder != null) {
						staticY += this.textBuilder.getVirtualClosedPageAxis(this.pendingText);
					} else {
						staticY += new TextBuilder(this, this.breakToken).getVirtualClosedPageAxis(this.pendingText);
					}
				}
				if (box.getType() == BoxType.REPLACED) {
					// 縦組みRLの静的位置を物理化するには、箱のページ方向寸法が
					// 必要なのでabsolute台帳へ渡す前に確定する。
					((AbstractReplacedBox) box).calculateFrame(contextBox.getLineSize());
				}
				contextBox.addAbsolute(absoluteBox, staticX, staticY);
			}
		}
			break;

		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * 救済分割(visual rescue split)の残余断片をフローへ載せます
	 * (2026-07-25新設、増分5。
	 * {@code docs/consultations/consult-rescue-split-codex.md} §5)。
	 *
	 * <p>
	 * 通常の{@link #addBound(net.zamasoft.foliojet.layout.box.IBox)}へ
	 * BoxTypeを偽装して流すことは<b>しません</b>。断片は
	 * {@code ReplacedParams}も{@code AbsoluteRectFrame}も持たない短命な
	 * 描画デコレータであり、あちらの経路は必ずキャストで落ちるためです。
	 * </p>
	 *
	 * <p>
	 * 断片はレイアウト済みの元ボックスを幾何学的に切ったものなので、
	 * ここでやることは「現在のページ方向カーソルへ、断片の占有量ぶん
	 * だけ載せる」だけです。マージンの再計算・整列の再計算・排除域の
	 * 回避は<b>一切行いません</b>:
	 * </p>
	 *
	 * <ul>
	 * <li>行方向の位置は元ボックス自身が自分のマージンから決めるため、
	 * 先頭断片と必ず一致する。</li>
	 * <li>上マージンは元ボックスの幾何の一部として先頭断片の中にあり、
	 * 続きの断片には存在しない(切断面には装飾を付けない)。したがって
	 * ここでマージンを積むと二重になる。</li>
	 * <li>下マージンも同じく最終断片の幾何の中にある。断片の直後の
	 * 折りたたみは、その内側の下マージンを基準に再開する。</li>
	 * </ul>
	 *
	 * @param box 残余断片
	 */
	public void addRescueBound(final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox box) {
		if (this.textSession != null) {
			this.textSession.abortToLegacy();
		}
		this.requireNoOpenTextBuilder("(no context)");
		final Flow flow = this.getFlow();
		final BlockParams params = flow.box.getBlockParams();
		// 断片の前でマージンを折りたたまない(切断面には装飾がない)
		this.poLastMargin = this.neLastMargin = 0;
		flow.box.addFlow(box, this.pageAxis - flow.pageAxis);
		this.pageAxis += box.getPageExtent(params.flow);
		flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
		// 断片の後は、元ボックスの下マージン(最終断片の幾何に含まれる)を
		// 基準に折りたたみを再開する
		final double bottomMargin = box.isLastFragment() ? box.sourceCollapsibleEndMargin(params.flow) : 0;
		this.poLastMargin = this.neLastMargin = bottomMargin;
	}

	/**
	 * 左浮動体の位置を設定します。
	 *
	 * @param box
	 */
	protected void addStartFloat(IFloatBox box) {
		// 1.浮動ボックスの基準となる左右の辺は包含ボックスからはみ出さない
		// 2.浮動ボックスの次に浮動ボックスがある場合、後の浮動ボックスは前の浮動ボックスの横に並ぶか下につくかのどちらかである
		// 3.左右の浮動ボックスが重なることはない
		// 4.浮動ボックスの上辺は包含ボックスからはみ出さない
		// 5.浮動ボックスの上辺は以前に現れたブロックボックスの上辺より上にはならない
		// 6.浮動ボックスの上辺は以前に現れたボックスを包含する行ボックスの上辺より上にはならない
		// 7.浮動ボックスは最も端にある場合を除き、包含ボックスの左右の辺をはみ出してはならない
		// 8.浮動ボックスは第一になるべく高く、第二になるべく端に位置しなければならない
		this.commitFloatPlacement(this.tryFloatPlacement(box, this.snapshotExclusions(), FloatSide.START));
	}

	/**
	 * 右浮動体の位置を設定します。
	 *
	 * @param box
	 */
	protected void addEndFloat(IFloatBox box) {
		this.commitFloatPlacement(this.tryFloatPlacement(box, this.snapshotExclusions(), FloatSide.END));
	}

	/**
	 * 新規floatの配置先を副作用なしで探索し、配置計画を返します
	 * (2026-07-23、排除域P1増分3——addStartFloat/addEndFloatが重複して
	 * 持っていた探索ループの一本化。挙動不変)。入力はすべて実測物理値
	 * ({@code exclusions}スナップショットと現在カーソル)で、builderの
	 * 状態は一切変更しない——計画を捨てるだけで試行のrollbackになる。
	 *
	 * <p>
	 * 「浮動体はこれより上にはならない」制約(最後に配置されたfloatの
	 * 上端)は{@code exclusions}の末尾要素から取る(旧コードの
	 * {@code floatings}末尾と同じ値——スナップショットは並びを保存する)。
	 * </p>
	 */
	final FloatPlacementDelta tryFloatPlacement(final IFloatBox box, final ExclusionSpace exclusions,
			final FloatSide side) {
		final WritingMode progression = this.getRootBox().getBlockParams().flow;
		final double lineWidth = box.getLineExtent(progression);
		final double pageWidth = box.getPageExtent(progression);
		double pageStart = this.pageAxis;
		if (this.textBuilder != null) {
			pageStart += this.textBuilder.getActualPageAxis();
		}
		final double lineStart0 = this.lineAxis;
		final double lineEnd0 = this.lineAxis + this.getFlowBox().getLineSize();
		double lineStart = lineStart0;
		double lineEnd = lineEnd0;
		if (!exclusions.isEmpty()) {
			final List<FloatExclusion> ascending = exclusions.ascendingByPageEnd();
			pageStart = Math.max(pageStart, ascending.get(ascending.size() - 1).pageSpan().start());

			final FloatPos pos = box.getFloatPos();
			for (;;) {
				final ExclusionSpace.FloatPlacementScan found = exclusions.scanFloatPlacementBand(pageStart,
						lineStart0, lineEnd0, pos.clear);
				pageStart = found.pageStart();
				lineStart = found.lineStart();
				lineEnd = found.lineEnd();
				final double width = lineEnd - lineStart;
				if (LayoutUtils.compare(width, lineWidth) >= 0) {
					// 幅に余裕がある
					break;
				}
				// 余裕がない場合は１つ下りて再探索
				if (found.startExclusion() == null && found.endExclusion() == null) {
					break;
				}
				if (found.endExclusion() == null) {
					pageStart = found.startExclusion().pageSpan().end();
				} else if (found.startExclusion() == null) {
					pageStart = found.endExclusion().pageSpan().end();
				} else {
					final double startPageEnd = found.startExclusion().pageSpan().end();
					final double endPageEnd = found.endExclusion().pageSpan().end();
					if (startPageEnd > endPageEnd) {
						pageStart = endPageEnd;
					} else {
						pageStart = startPageEnd;
					}
				}
			}
		}
		// END側は行末揃え。ただし**行頭より前(=紙面の外側)へは出さない**
		// (2026-08-21、掃過seed 615921)。CSS 2.2 §9.5.1はSTART側フロートに
		// 「包含ブロックの行頭端より前に出ない」を課しており、帯より広い
		// END側フロートも同じ下限で止める。画面のブラウザは行頭側へ
		// はみ出させる(スクロールで読める)が、紙の外に置かれた内容には
		// 続きがない——columnInflatedクランプ(AbstractStaticBlockBox)と
		// 同じ印刷優先の判断。発動するのは帯幅より広いフロートだけ
		// 直交縦書きフロートのはみ出し描画の紙側寄せ(2026-08-22、掃過seed
		// 1353935): 横書き包含ブロック中の縦書きフロートは、内側のページ軸
		// (=外側の線軸)方向へoverflow:visibleの描画が箱幅を超えて伸びる。
		// RLなら物理左へ、LRなら物理右へ——紙の外に落ちる側の超過分だけ
		// フロートを内側へ寄せる(END側クランプと同じ印刷優先の判断)
		final double paintedOverflow;
		final WritingMode innerFlow;
		if (progression == WritingMode.TB && box instanceof AbstractContainerBox innerBox
				&& innerBox.getBlockParams().flow.isVertical()) {
			innerFlow = innerBox.getBlockParams().flow;
			paintedOverflow = Math.max(0,
					box.paintedPageExtent(innerFlow) - box.getPageExtent(innerFlow));
		} else {
			innerFlow = progression;
			paintedOverflow = 0;
		}
		final double lineOffset;
		if (side == FloatSide.START) {
			if (innerFlow == WritingMode.RL && paintedOverflow > 0) {
				lineOffset = Math.min(lineStart + paintedOverflow,
						Math.max(lineStart, lineEnd - lineWidth));
			} else {
				lineOffset = lineStart;
			}
		} else if (innerFlow == WritingMode.LR && paintedOverflow > 0) {
			lineOffset = Math.max(lineStart, lineEnd - lineWidth - paintedOverflow);
		} else {
			lineOffset = Math.max(lineStart, lineEnd - lineWidth);
		}
		final FloatCommitKind kind = this.classifyFloatPlacement(box, pageStart);
		return new FloatPlacementDelta(box, side, new AxisSpan(lineOffset, lineOffset + lineWidth),
				new AxisSpan(pageStart, pageStart + pageWidth), kind);
	}

	/**
	 * 配置計画をレイアウト状態へ反映します(2026-07-23、排除域P1増分3。
	 * 副作用の発生順は旧コードと同一: breakFloats記録→コンテナへの追加
	 * (serial更新)→排除域台帳(+hidden台帳、FLOAT_COMP安定ソート)→
	 * 親ボックスのpage extent拡張)。
	 */
	final void commitFloatPlacement(final FloatPlacementDelta delta) {
		if (System.getProperty("foliojet.debug.breakTrace") != null) {
			System.err.println("[float-commit] kind=" + delta.kind() + " el=" + delta.box().getParams().element
					+ " pageSpan=" + delta.pageSpan().start() + ".." + delta.pageSpan().end() + " limit="
					+ (this instanceof BreakableBuilder bb ? bb.getPageLimit() : Double.NaN));
		}
		if (delta.kind() != FloatCommitKind.PLACED) {
			this.recordBreakFloat(delta.side());
		}
		// 配置
		final double lineOffset = delta.lineSpan().start();
		final double pageStart = delta.pageSpan().start();
		final Flow flow = this.getFlow();
		final boolean forceMoveFromBottomBand = delta.kind() == FloatCommitKind.MOVE_TO_NEXT
				&& this instanceof RootBuilder root && root.hasTwoDimensionalBottomFloatLimit();
		if (forceMoveFromBottomBand
				&& flow.box.getContainer() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer container) {
			container.addFloating(delta.box(), lineOffset - flow.lineAxis, pageStart - flow.pageAxis, true);
		} else {
			flow.box.addFloating(delta.box(), lineOffset - flow.lineAxis, pageStart - flow.pageAxis);
		}
		if (delta.kind() == FloatCommitKind.MOVE_BY_CLEAR) {
			// clear先送りは現行のroot-only extent規則を保存する(通常の
			// extendParentsと同じではない——ネスト中は親extentを更新しない。
			// codex設計: この非対称をP1で黙って正規化しない)。
			if (this.flowStack == null || this.flowStack.isEmpty()) {
				this.getRootBox().setPageAxis(delta.pageSpan().end());
			}
			return;
		}
		if (delta.kind() != FloatCommitKind.MOVE_TO_NEXT) {
			final WritingMode progression = this.getRootBox().getBlockParams().flow;
			this.addFloating(new LayoutContext.Floating(delta.box(), lineOffset, pageStart, progression));
		}
		// 2026-09-04: 配置を確定するこの一か所からだけatomic floorを通知する。
		if ((delta.kind() == FloatCommitKind.PLACED || delta.kind() == FloatCommitKind.SPLIT_AT_BREAK)
				&& this instanceof RootBuilder root) {
			final WritingMode ownerFlow = flow.box.getBlockParams().flow;
			final boolean first = root.isFloatAtFragmentStart(pageStart);
			final BoxType boxType = delta.box().getType();
			final net.zamasoft.foliojet.layout.box.params.PageBreakMode pageBreakInside = boxType == BoxType.BLOCK
					? ((AbstractContainerBox) delta.box()).getBlockParams().pageBreakInside
					: null;
			if (FloatMeasurement.isUnsplittable(boxType,
					FloatMeasurement.sameWritingAxis(ownerFlow, delta.box()), pageBreakInside, first)) {
				root.reportAtomicFloatPlacement(delta.box(), ownerFlow, pageStart);
			}
		}
		// 上位ボックスの幅の拡張
		this.extendParents(pageStart, delta.pageSpan().extent());
	}

	private void extendParents(final double pageStart, final double pageWidth) {
		// 浮動ボックスによる上位ボックスの幅の拡張
		Flow contextFlow = this.getSubContextFlow();
		double pageAxis = pageStart + pageWidth;
		int i;
		if (this.flowStack != null) {
			i = this.flowStack.size() - 1;
			for (; i >= 0; --i) {
				contextFlow = (Flow) this.flowStack.get(i);
				final BlockParams params = contextFlow.box.getBlockParams();
				if (this.independentFloatScopeOwners != null
						&& this.independentFloatScopeOwners.contains(contextFlow.box)) {
					contextFlow.box.setPageAxis(pageAxis - contextFlow.pageAxis);
					// overflow:hiddenの指定寸法、またはwriting-mode変更BFCが
					// 確定した内側extentだけを上位へ伝える。
					pageAxis = contextFlow.box.getInnerPageExtent(params.flow) + contextFlow.pageAxis;
				}
			}
		} else {
			i = -1;
		}
		if (i == -1) {
			AbstractContainerBox rootBox = this.getRootBox();
			rootBox.setPageAxis(pageAxis);
		}
	}

	/**
	 * 浮動体の追加を予約します。
	 * 
	 * @param box
	 */
	/**
	 * 行の途中に現れた行末側フロートの、現在行への同一行配置の試みです
	 * (2026-08-08)。CSS 2.1 §9.5の「行の途中のフロートは、収まるなら
	 * 現在の行ボックスの上端に置き、行ボックスを狭める」のうち、既配置の
	 * 内容を動かさずに済む行末側だけを実装する(行頭側は既配置内容の
	 * 再配置が必要になるため、従来どおり行末で先送りする)。
	 *
	 * @return 配置したら true。false なら呼び出し側が従来の先送りへ回す
	 */
	private boolean tryFloatOnCurrentLine(final IFloatBox box) {
		final FloatPos pos = box.getFloatPos();
		if (pos.floating != FloatSide.END) {
			return false;
		}
		// clearは排除帯の走査(scanFloatPlacementBand)がそのまま解決する。
		// clearが現在行の上端より下を要求する場合はpageStart検査で
		// 先送りに落ちる(021-RIGHT_clearのclear:left、Chrome実測と一致)
		final WritingMode progression = this.getRootBox().getBlockParams().flow;
		final double lineWidth = box.getLineExtent(progression);
		final double pageWidth = box.getPageExtent(progression);
		// 現在行の上端(開いている行の高さは含めない)
		final double lineTop = this.pageAxis + this.textBuilder.getPageAxis();
		final double lineStart0 = this.lineAxis;
		final double lineEnd0 = this.lineAxis + this.getFlowBox().getLineSize();
		double lineEnd = lineEnd0;
		final ExclusionSpace exclusions = this.snapshotExclusions();
		if (!exclusions.isEmpty()) {
			// 「以前のフロートの上端より上に置かない」制約を現在行の
			// 上端が満たさないなら、同一行配置はできない
			final List<FloatExclusion> ascending = exclusions.ascendingByPageEnd();
			if (LayoutUtils.compare(ascending.get(ascending.size() - 1).pageSpan().start(), lineTop) > 0) {
				return false;
			}
			final ExclusionSpace.FloatPlacementScan found = exclusions.scanFloatPlacementBand(lineTop, lineStart0,
					lineEnd0, pos.clear);
			if (LayoutUtils.compare(found.pageStart(), lineTop) != 0) {
				return false;
			}
			lineEnd = found.lineEnd();
		}
		final double lineOffset = lineEnd - lineWidth;
		// 2026-09-04: MOVE_TO_NEXTの行を狭めて跡地を残さないよう、先に分類する。
		final FloatCommitKind kind = this.classifyFloatPlacement(box, lineTop);
		if (kind == FloatCommitKind.PLACED || kind == FloatCommitKind.SPLIT_AT_BREAK) {
			// 実際に現断片へ残す場合だけ、現在行の既存内容
			// (textIndent+確定・未確定アドバンス)の手前へ狭める。
			if (!this.textBuilder.narrowCurrentLine(lineOffset)) {
				return false;
			}
		}
		this.commitFloatPlacement(new FloatPlacementDelta(box, FloatSide.END,
				new AxisSpan(lineOffset, lineOffset + lineWidth), new AxisSpan(lineTop, lineTop + pageWidth),
				kind));
		if (this.floatings != null) {
			// 台帳の底辺昇順を回復する(addFloating(IFloatBox)と同じ。
			// 現在行の上端は既存フロートの底辺より上のことがある)
			Collections.sort(this.floatings, FLOAT_COMP);
		}
		return true;
	}

	private void toAddFloating(IFloatBox box) {
		if (this.toAddFloatings == null) {
			this.toAddFloatings = new ArrayList<IFloatBox>();
		}
		this.toAddFloatings.add(box);
		if (this.textBuilder == null) {
			this.checkFloatings();
		}
	}

	/**
	 * 予約された浮動体が存在すれば追加します。
	 */
	void checkFloatings() {
		if (this.toAddFloatings == null || this.toAddFloatings.isEmpty()) {
			return;
		}
		for (Iterator<IFloatBox> i = this.toAddFloatings.iterator(); i.hasNext();) {
			IFloatBox box = (IFloatBox) i.next();
			i.remove();
			this.addFloating(box);
		}
	}

	private void addFloating(IFloatBox box) {
		if (System.getProperty("foliojet.debug.floatTrace") != null) {
			System.err.println("[float] 配置 side=" + box.getFloatPos().floating + " box="
					+ System.identityHashCode(box) + " builder=" + System.identityHashCode(this));
		}
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("Add float: " + box.getParams().element + "/" + this.getFlow().box.getParams().element);
		}
		FloatPos pos = box.getFloatPos();
		switch (pos.floating) {
		case FloatSide.START:
			this.addStartFloat(box);
			break;
		case FloatSide.END:
			this.addEndFloat(box);
			break;
		default:
			throw new IllegalStateException();
		}
		if (this.floatings != null) {
			// 底辺を下から順に整列
			// このソートは安定ソートである必要があります
			Collections.sort(this.floatings, FLOAT_COMP);
			this.noteFloatingsChanged();
		}
	}

	private void addFloating(LayoutContext.Floating floating) {
		if (this.floatings == null) {
			this.floatings = new ArrayList<Floating>();
		}
		this.floatings.add(floating);
		this.noteFloatingsChanged();
		if (this.noOverflowFloatings != null && !this.noOverflowFloatings.isEmpty()) {
			List<Floating> floatings = (List<Floating>) this.noOverflowFloatings
					.get(this.noOverflowFloatings.size() - 1);
			floatings.add(floating);
		}
	}

	/**
	 * fragment境界(改ページ・改段)通過後に、overflow:hiddenのfloat台帳
	 * ({@link #noOverflowFloatings})を現在のflowStack上のactive hidden
	 * flowから再構築します(2026-07-23、排除域P1増分1)。
	 *
	 * <p>
	 * 従来は{@code resetFragmentCursor()}が{@code floatings}だけをnullに
	 * 戻しこの台帳には触れなかったため、PAGE改ページ(flowStack.clear()+
	 * resume再駆動でhidden flowが台帳を再pushする経路)では旧断片の
	 * スコープエントリがpopされないまま残留し続けた——レイアウト結果には
	 * 影響しない(popは常に末尾=新エントリ、removeAllは空振り)が、
	 * ページ数×hidden深さ×float数で旧Floating/IFloatBox参照が文書終了
	 * までGCできなかった(codexレビュー指摘)。
	 * </p>
	 *
	 * <p>
	 * PAGE({@code flowStack.clear()}直後に呼ぶ)では結果はnull——再開される
	 * hidden flowが{@code startFlowBlock()}で新しい台帳を積む。COLUMN
	 * ({@code pruneFlowStackTo()}+{@code resetFragmentCursor()}直後に呼ぶ)
	 * では、保持されたhidden flowの数だけ空の台帳を積み直す——それらの
	 * flowは再度{@code startFlowBlock()}されないため、積み直さないと
	 * 終了時のpopが破綻する。どちらも{@code floatings}自体はリセット済み
	 * (assertで検査)のため、旧floatを引き継ぐ必要はなく空台帳で十分
	 * (owner付けは不要)。
	 * </p>
	 */
	protected final void rebuildNoOverflowFloatingScopes() {
		assert this.floatings == null;
		this.noOverflowFloatings = null;
		this.independentFloatScopeOwners = null;
		if (this.flowStack == null) {
			return;
		}
		BlockParams parentParams = this.contextFlow.box.getBlockParams();
		for (int i = 0; i < this.flowStack.size(); ++i) {
			final Flow flow = (Flow) this.flowStack.get(i);
			final FlowBlockBox flowBox = (FlowBlockBox) flow.box;
			if (establishesIndependentFloatScope(flowBox, parentParams)) {
				if (this.noOverflowFloatings == null) {
					this.noOverflowFloatings = new ArrayList<List<Floating>>();
					this.independentFloatScopeOwners = new ArrayList<AbstractContainerBox>();
				}
				this.noOverflowFloatings.add(new ArrayList<Floating>());
				this.independentFloatScopeOwners.add(flowBox);
			}
			parentParams = flowBox.getBlockParams();
		}
	}

	/** CSS Writing Modes 3 §3.2とoverflowによる独立BFCのfloat境界。 */
	private static boolean establishesIndependentFloatScope(final FlowBlockBox flowBox,
			final BlockParams parentParams) {
		final BlockParams params = flowBox.getBlockParams();
		// display:flow-rootは独立BFC(2026-08-29)。overflow:hiddenと同じ扱い
		return params.overflow == OverflowMode.HIDDEN || params.flowRoot
				|| (params.flow.isVertical() == parentParams.flow.isVertical()
						&& params.flow != parentParams.flow);
	}

	public void addTable(final net.zamasoft.foliojet.layout.builder.RetainedTable tableBuilder) {
		tableBuilder.prepareLayout();
		tableBuilder.bind(this);
	}

	public void addGrid(final net.zamasoft.foliojet.layout.builder.RetainedGrid gridBuilder) {
		// Grid G3d1: 通常フローでは即時bind(トラック解決→item bind→配置)
		gridBuilder.bind(this);
	}

	public void addFlex(final net.zamasoft.foliojet.layout.builder.RetainedFlex flexBuilder) {
		// Flex F1f: 通常フローでは即時bind(§9.7解決→item bind→row配置)
		flexBuilder.bind(this);
	}

	public Builder newBuilder(AbstractBlockBox blockBox) {
		final Builder builder;
		AbstractContainerBox containerBox;
		switch (blockBox.getPos().getType()) {
		case FLOW:
		case TABLE_CAPTION:
			if (blockBox.isFixedMulticolumn()) {
				final FlowBlockBox flowBox = (FlowBlockBox) blockBox;
				containerBox = this.getFlowBox();
				flowBox.calculateSize(this, 0, containerBox.getLineSize());
				final BlockBuilder columns = new ColumnBuilder(this, blockBox);
				if (retainsFlowContent(blockBox)) columns.beginRetainedContext(blockBox);
				return columns;
			}
			// フロー（ページ進行方向が違う場合）
		case FLOAT:
		case INLINE: {
			// 浮動体
			// インライン配置
			final AbstractStaticBlockBox staticBlockBox = (AbstractStaticBlockBox) blockBox;
			containerBox = this.getFlowBox();
			if (!LayoutUtils.needsIntrinsicSizing(containerBox, blockBox)) {
				// 固定幅
				staticBlockBox.shrinkToFit(this, IntrinsicSizes.ZERO, false);
				if (blockBox.isFixedMulticolumn()) {
					// ページ方向が固定されたマルチカラム
					builder = new ColumnBuilder(this, blockBox);
				} else {
					builder = new BlockBuilder(this, blockBox);
				}
			} else {
				// STF
				blockBox.firstPassLayout(containerBox);
				builder = new TwoPassBlockBuilder(this, blockBox);
			}
		}
			break;

		case ABSOLUTE: {
			final AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) blockBox;
			if (absoluteBox.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
				// 固定配置
				containerBox = this.getPageContext().getRootBox();
			} else {
				// 絶対配置
				containerBox = this.getContextBox();
			}
			if (!LayoutUtils.needsIntrinsicSizing(containerBox, blockBox)) {
				// 固定幅
				absoluteBox.shrinkToFit(containerBox, IntrinsicSizes.ZERO);

				// 高さは最後に確定するので、マルチカラムで高さが明示された場合でもリフローする
				builder = new BlockBuilder(this, blockBox);
			} else {
				// STF
				absoluteBox.firstPassLayout(containerBox);
				builder = new TwoPassBlockBuilder(this, blockBox);
			}
		}
			break;

		default:
			throw new IllegalStateException();
		}
		if (builder instanceof BlockBuilder retained) {
			retained.beginRetainedContext(blockBox);
		}
		return builder;
	}

	public void finish() {
		try (var retained = this.retainedRoot) {
			this.finishContent();
		} finally {
			this.retainedRoot = null;
		}
	}

	private void finishContent() {
		assert this.flowStack == null || this.flowStack.isEmpty();
		this.requireNoOpenTextBuilder("(no context)");

		final AbstractContainerBox flowBox = (AbstractContainerBox) this.contextFlow.box;
		final BlockParams params = flowBox.getBlockParams();
		if (flowBox.getColumnCount() > 1 && params.columns.fill == Columns.FILL_BALANCE) {
			// カラムのバランス
			this.pageAxis = this.contextFlow.pageAxis;
			flowBox.balance(this);
		}
	}

	public void close() {
		this.finish();
	}

	public void setBreakToken(BreakToken breakToken) {
		this.breakToken = this.breakToken.combine(breakToken);
	}

	protected void requireTextBlock() {
		// 新規テキストブロック
		// System.err.println("requireTextBlock");
		this.requireNoOpenTextBuilder("(no context)");
		// textSessionは再生中の改ページ処理が新しいTextBuilderを作る間も
		// 保持される(配達境界のclampのため)——記録中でないことだけを検査
		assert this.textSession == null || !this.textSession.recording();
		final BreakToken breakToken = this.breakToken;
		this.textBuilder = new TextBuilder(this, breakToken);
		this.breakToken = BreakToken.MID_FLOW;

		final Flow flow = this.getFlow();
		double localPageAxis = this.pageAxis - flow.pageAxis;
		flow.box.addFlow(this.textBuilder.textBlockBox, localPageAxis);

		// M3c: オプトイン時(text-wrap-style: pretty)のみ、適格な段落の
		// K-P蓄積セッションを開始する
		if (this.textSession == null && this.optimizedTextEnabled()) {
			this.textSession = TotalFitSession.tryBegin(this, this.textBuilder, breakToken);
		}
	}

	/**
	 * 「物理的にTextBuilderへ届いたソース文字の配達境界」を返します
	 * (M3c)。K-Pセッションが蓄積・再生中の場合、未配達イベントの先頭
	 * ソース位置でclampする(切断段落の尾部再生とセッションの残イベント
	 * 配達が二重供給にならないため)。セッションがなければそのまま返す。
	 */
	final int clampDeliveredCharEnd(final int deliveredCharEnd) {
		final TotalFitSession session = this.textSession;
		if (session == null) {
			return deliveredCharEnd;
		}
		return session.clampDeliveredCharEnd(deliveredCharEnd);
	}

	/**
	 * Knuth-Plass行分割の蓄積セッションを開始しうる文脈かを返します
	 * (M3c)。オプトインの可否そのものは段落の算出値
	 * ({@code text-wrap-style: pretty})で決まり、{@link TotalFitSession#tryBegin}
	 * が判定する(2026-07-25、独自プロパティ{@code text.line-breaker}から
	 * CSSへ一本化)。ここで見るのは文脈側の条件だけ——破断残余の再構築
	 * (restyle)中は、切断段落の尾部再生等の再開機構と混線しないよう
	 * 保守的に無効とする。
	 */
	private boolean optimizedTextEnabled() {
		final RootBuilder root;
		if (this instanceof RootBuilder r) {
			root = r;
		} else if (this.layoutStack != null) {
			root = this.getPageContext();
		} else {
			// ページ文脈を持たない再レイアウト用ビルダー
			return false;
		}
		if (root == null || root.isRestyling()) {
			return false;
		}
		if (this instanceof BreakableBuilder breakable && breakable.isRestyling()) {
			return false;
		}
		return true;
	}

	/**
	 * 開いているテキストランのフォントを返します(無ければ{@code null})。
	 * 途中で作り直された{@link TextBuilder}がフォントを引き継げなかった
	 * ときの復元元です(2026-08-17。{@link #glyph}が遅延生成でこれを使うのと
	 * 同じ値)。
	 */
	FontStyle getOpenRunFontStyle() {
		return this.openRunFontStyle;
	}

	/** @see #getOpenRunFontStyle() */
	FontMetrics getOpenRunFontMetrics() {
		return this.openRunFontMetrics;
	}

	public void startTextRun(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
		this.openRunFontStyle = fontStyle;
		this.openRunFontMetrics = fontMetrics;
		if (this.textBuilder == null) {
			// System.err.println("begin1");
			this.requireTextBlock();
		}
		if (this.textSession != null && this.textSession.recordRun(fontStyle, fontMetrics)) {
			return;
		}
		this.textBuilder.startTextRun(fontStyle, fontMetrics);
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		// System.err.println("glyph: "+new String(ch, coff, clen));
		if (this.textSession != null && this.textSession.recordGlyph(charOffset, ch, coff, clen, gid)) {
			return;
		}
		if (this.textBuilder == null) {
			// flush()中の行間断片化がTextBuilderだけを閉じ、shaperの
			// テキストランは継続している場合がある。次のglyphが実際に
			// 到着した時点でだけ継続ブロックを作る（末尾で空の
			// TextBuilderを合成しない）。
			if (this.openRunFontStyle == null || this.openRunFontMetrics == null) {
				throw new IllegalStateException("glyph outside a text run");
			}
			this.requireTextBlock();
			this.textBuilder.startTextRun(this.openRunFontStyle, this.openRunFontMetrics);
		}
		this.textBuilder.glyph(charOffset, ch, coff, clen, gid);
	}

	public void endTextRun() {
		try {
			if (this.textSession != null && this.textSession.recordRunEnd()) {
				return;
			}
			// ラン末尾の直前で断片化し、その後にglyphが無い場合は
			// TextBuilderを作り直す必要がない。
			if (this.textBuilder != null) {
				this.textBuilder.endTextRun();
			}
		} finally {
			this.openRunFontStyle = null;
			this.openRunFontMetrics = null;
		}
	}

	public void control(final TextControl quad) {
		if (quad instanceof InlineQuad) {
			// インラインボックス
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				// インライン開始
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				inlineStartQuad.box.fixLineAxis(this.getFlowBox());
			}
				break;

			case InlineQuad.INLINE_END:
			case InlineQuad.INLINE_BLOCK:
				break;

			case InlineQuad.INLINE_ABSOLUTE:
				final InlineAbsoluteQuad inlineAbsoluteQuad = (InlineAbsoluteQuad) inlineQuad;
				if (inlineAbsoluteQuad.box.getType() == BoxType.REPLACED) {
					LayoutUtils.calculateReplacedSize(this, (AbstractReplacedBox) inlineAbsoluteQuad.box);
				}
				break;

			case InlineQuad.INLINE_REPLACED: {
				// 置換されたインライン
				final InlineReplacedQuad inlineReplacedQuad = (InlineReplacedQuad) inlineQuad;
				LayoutUtils.calculateReplacedSize(this, inlineReplacedQuad.box);
			}
				break;

			default:
				throw new IllegalStateException();
			}
		}
		if (this.textBuilder == null) {
			// System.err.println("begin2");
			this.requireTextBlock();
		}
		if (this.textSession != null && this.textSession.recordControl(quad)) {
			return;
		}
		// System.err.println(this+"/"+quad);
		this.textBuilder.control(quad);
	}

	public void flush() {
		if (this.textSession != null && this.textSession.recordFlush()) {
			return;
		}
		// テキストブロックが空(textBuilderが生成されていない)場合の
		// flushは何もしない。endTextBlock()と同じnullガード(809行目付近の
		// コメント参照)——E-6増分5aのセルrange bindで実際に発生する:
		// セル内容がsoft hyphen(U+00AD)のみのとき、StyledTextUnitizerは
		// textShaperを作るがWordHyphenatorがMarkerを黙って落とす
		// (hyphens:manualでfontMetrics未設定)ため、ビルダーへはglyphも
		// controlも届かないままshaperのclose連鎖がflush()だけを呼ぶ
		// (2026-07-24、040-8BITS_ASCII.htmlのNullPointerException)。
		// live経路のセルはTwoPassBlockBuilder(textBuilder非依存)で
		// 記録されるため、この空flushはbind時のrange再生でのみ到達する。
		if (this.textBuilder == null) {
			return;
		}
		while (this.textBuilder.flush())
			;
	}

	public void endTextBlock() {
		this.endTextBlock(false);
	}

	/**
	 * テキストブロックを閉じます。
	 *
	 * @param fragmentBreak 本文の終端ではなく、断片の容量超過によって
	 *                      テキストが後続断片へ継続する場合は {@code true}
	 */
	protected final void endTextBlock(final boolean fragmentBreak) {
		// テキストブロックの終了。内容が空(control()が一度も呼ばれず
		// requireTextBlock()でtextBuilderが生成されない)場合はnullのまま
		// ここに達することがある(2026-07-18、空のテーブルセルで
		// NullPointerExceptionが実際に発生した)。このクラスの他の箇所
		// (809行目付近等)と同じくnullガードで対応する
		if (this.textBuilder != null) {
			if (this.textSession != null) {
				// M3c: 蓄積分のbreakpoint選択と再生(不適格ならlegacyと
				// 同一のverbatim再生)。再生中の再入では何もしない
				this.textSession.finishSession();
			}
			this.textBuilder.finish(fragmentBreak);
			this.pageAxis += this.textBuilder.getFlowPageAdvance();
			this.textBuilder = null;
		}
		final Flow flow = this.getFlow();
		flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
		// System.err.println("endTextBlock");
	}

	/**
	 * 新規floatの配置確定の種別を、副作用なしで分類します(2026-07-23、
	 * 排除域P1増分2——従来の{@code transferFloatToNextPage}を純分類と
	 * {@link #recordBreakFloat}へ分解)。基底実装は常に
	 * {@link FloatCommitKind#PLACED}(改ページ文脈を持たないbuilderは
	 * floatを先送りしない)。
	 */
	FloatCommitKind classifyFloatPlacement(IFloatBox box, double pageStart) {
		return FloatCommitKind.PLACED;
	}

	/**
	 * フラグメント境界と交差したfloatの記録hookです(2026-07-23、
	 * 排除域P1増分2)。基底実装は何もしない。{@code BreakableBuilder}が
	 * {@code breakFloats}への追加として実装する。
	 */
	void recordBreakFloat(FloatSide side) {
	}
}
