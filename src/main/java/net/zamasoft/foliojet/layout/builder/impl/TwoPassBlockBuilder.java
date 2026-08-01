package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.impl.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Pos;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineBlockQuad;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.TableBuilder;
import net.zamasoft.foliojet.layout.builder.TwoPass;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Element;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

public class TwoPassBlockBuilder implements Builder, LayoutStack, TwoPass {
	private static final boolean DEBUG = false;

	/**
	 * 実測パスで記録し、bind() で再生するイベントです。
	 */
	private sealed interface Recorded {
		/** テキスト要素または制御(インライン境界等)。 */
		record ElementEvent(Element element) implements Recorded {
		}

		/** テキストブロックの終了。 */
		record EndTextBlock() implements Recorded {
			static final EndTextBlock INSTANCE = new EndTextBlock();
		}

		/** フローブロックの開始。 */
		record StartFlow(FlowBlockBox box) implements Recorded {
		}

		/** フローブロックの終了。 */
		record EndFlow(FlowBlockBox box) implements Recorded {
		}

		/** 置換要素。 */
		record ReplacedEvent(IBox box) implements Recorded {
		}

		/** ネストした shrink-to-fit ブロック。 */
		record StfBlock(TwoPassBlockBuilder builder) implements Recorded {
		}

		/** 絶対配置ブロック。 */
		record AbsoluteBlock(TwoPassBlockBuilder builder) implements Recorded {
		}

		/** インラインブロック(ネストした実測ビルダーを内包)。 */
		record InlineBlockEvent(InlineBlockQuad quad, TwoPass measure) implements Recorded {
		}

		/** テーブル(Retained実行計画のみ——Incrementalは録画対象にならない)。 */
		record TableEvent(net.zamasoft.foliojet.layout.builder.RetainedTable builder) implements Recorded {
		}

		/** Grid実行計画(Grid G3d1——TableEventと同型)。 */
		record GridEvent(net.zamasoft.foliojet.layout.builder.RetainedGrid builder) implements Recorded {
		}

		/** Flex実行計画(Flex F1f——GridEventと同型)。 */
		record FlexEvent(net.zamasoft.foliojet.layout.builder.RetainedFlex builder) implements Recorded {
		}
	}

	/**
	 * bind() の再生元となる本文表現です(E-6増分4a、2026-07-24——
	 * {@code docs/consultations/consult-e6b-remaining-increments-codex.md}
	 * §3.2)。録画中は常に {@link LegacyRecords}。表外float/absolute/
	 * inline-blockの適格なビルダーは録画完了(close)時に
	 * {@link #sealBodyForRangeBind()} で {@link SourceRangeBody} へ
	 * 切り替わり、records(TextImplのglyph列・liveボックス)を手放す
	 * (E-6増分4b)。
	 *
	 * <p>
	 * <b>増分4c(range-first capture=録画中からrecordsを作らない)の
	 * 実装可能性調査の結論(2026-07-24、F-4で2026-07-25に再確認)</b>:
	 * fail closedの現行契約下では未成立のため見送り。適格性は録画完了時
	 * にしか確定できず(poison要因——表のOpaque・非固定同方向multicolの
	 * StartFlow・ネストビルダー——は本文streamの途中で初めて到着する)、
	 * seal不適格へ転落したビルダーのfallback bind({@link #bindRecords})は
	 * TextImplのglyph列を要求する。よってglyph列を録画中に落とすと、
	 * 後着のpoisonで内容が復元不能になる(例: float内の途中に表)。
	 * 開始時確定(codex(c)案)はSAX単一パスに先読みがなく不可能、
	 * 投機+live再駆動((b)案)は裁定済み不可、glyph列の別テープ退避は
	 * 「第二のTwoPass glyphテープは作らない」裁定
	 * (2026-07-24-e6-remaining-design-decision.md)に抵触。
	 *
	 * <p>
	 * codex自身の切替条件「対象範囲の全入力variantがrecipe化済み
	 * (Barrierゼロ)」の充足状況(F-4実測、{@code files/unittest}全数
	 * 436文書): ルビ由来のOpaque 160→<b>0</b>(2026-07-25の注釈付き
	 * テキスト化)、絶対配置由来は増分4eで解消済み。しかし残Opaqueは
	 * <b>319件すべてが表</b>(表本体310+その内側の表キャプション9)で、
	 * Barrierゼロには程遠い。しかも表のOpaque化は「浮動/絶対配置の表
	 * だけ」ではなく<b>全ての表</b>である({@code StyleBuilder.boxKind}の
	 * {@code TableBox}分岐のコメント参照)。よって4cの解禁は「表のrecipe記録化」一件に律速される——
	 * ルビ撤去だけでは前提は満たされない。加えて非leaf range bind
	 * (ネストのリース再帰解放)も引き続き必要。
	 * </p>
	 */
	private sealed interface ReplayBody {
		/** 従来のイベント記録(records)による本文です。 */
		final class LegacyRecords implements ReplayBody {
			final List<Recorded> records = new ArrayList<Recorded>();
		}

		/**
		 * LayoutSourceの子イベント範囲 [fromId, toId] による本文です。
		 * bindは{@code SourceReplayer.bindTwoPassRange}(SegmentExecutor
		 * 駆動)で行われ、範囲はseal時に取得した{@code RetentionLease}が
		 * compactから守る。リースはbindのfinallyで解放される(冪等)。
		 */
		record SourceRangeBody(net.zamasoft.foliojet.layout.fragment.LayoutSource source,
				net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator, long fromId, long toId,
				net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease) implements ReplayBody {
		}

		/**
		 * seal済み本文を{@link DeferredBind}へ持ち出した後の状態です
		 * (E-6増分4e)。リースの所有はDeferredBindへ移っており、この
		 * ビルダーへのbind要求は契約違反(このビルダー経由のbindは
		 * 以後起きない——deferred absoluteのbindはDeferredBindが担う)。
		 */
		record Detached() implements ReplayBody {
		}

		/**
		 * 空本文です(DP増分2、2026-07-30——codex相談
		 * consult-codex-2026-07-30-dualpath-endgame.txt 増分1)。子イベント
		 * 範囲が空(空セル{@code <td></td>}・空float等)のビルダーは、旧来
		 * EMPTY_RANGEでseal不適格とされ空のrecords bind(何も再演しない
		 * ループ)へ落ちていた——bindが本文非依存であることは表Pass Bの
		 * 空セル特別扱い({@code hasEmptyRecordedBody})が既に前提として
		 * いた事実で、これを型で表しrecords経路から切り離す。bindはno-op、
		 * リースは不要(範囲を参照しない)。
		 */
		record Empty() implements ReplayBody {
		}

		/**
		 * 親のrange化に吸収された後の状態です(DP増分3、2026-07-30——
		 * codex相談 consult-codex-2026-07-30-dualpath-endgame.txt
		 * NESTED_BUILDER解消)。親の{@code SourceRangeBody}が子の範囲を
		 * 包含し、bindは親の範囲再生(SegmentExecutor)が子の内容ごと
		 * 再構築する——このビルダーへのbind要求は契約違反
		 * ({@link Detached}と同じ扱い)。子が保持していたリースは吸収時に
		 * 解放済み(親リースが先に取得されているためcompact可能水位は
		 * 後退しない)。
		 */
		record Subsumed() implements ReplayBody {
		}
	}

	/**
	 * seal済み本文の持ち出し形です(E-6増分4e、2026-07-24——codex設計
	 * §3.2の増分4e「AbsoluteBlockBoxのTwoPassBlockBuilder保持を
	 * DeferredBind {sizes; range; lease}相当へ置換」)。持ち主は2種:
	 * deferred absolute(position:absolute、ページ末bind——
	 * {@code AbsoluteBlockBox})と、Retained表のseal済みセル
	 * (E-6増分5a——{@code CellContent}。表終端の列幅確定後bind)。
	 *
	 * <p>
	 * ビルダー自体を{@code AbsoluteBlockBox}が保持し続けると、
	 * layoutStack鎖(親ビルダー群)・計測器をページ末のbindまで引き留める。
	 * 適格(seal済み)な場合はこの値オブジェクトだけを箱へ渡し、
	 * ビルダーを手放す。
	 * </p>
	 *
	 * <p>
	 * <b>sizesは模倣計測のスナップショット</b>: 現行の
	 * {@link TwoPassBlockBuilder#intrinsicSizesMeasured()}は絶対配置では
	 * 常に模倣計測({@code IntrinsicMeasurer})へフォールバックする
	 * ({@code MeasuredIntrinsics.of}の絶対配置ゲート——M2c実測の適用は
	 * 寸法変化を伴うため挙動不変制約で見送り)。計測器は録画完了後不変の
	 * ためdetach時のスナップショットはbind時読みと同値。M2cを絶対配置へ
	 * 広げる際は、ここをbind時のMeasuredIntrinsics再計測へ変えること。
	 * </p>
	 *
	 * <p>
	 * <b>リース寿命</b>: seal時に取得したリースの所有はここへ移り、
	 * {@link #bind}のfinallyで解放する(取り残すと以後のcompactが永久に
	 * clampされる)。bindされない破棄経路は構造的に存在しない——
	 * 絶対配置を含む部分木はソース再生で置換されない
	 * ({@code LayoutSource.containsAbsolute}ゲート)ため箱は必ず
	 * box-restyleで運搬され、ページ末の{@code finishLayoutSelf}が
	 * 必ずbindする。セル(E-6増分5a)は{@code CellContent}のjavadoc参照
	 * (表終端の一括bindが全実セルを必ず一度bindする)。この1:1は既存の
	 * 検出器(DisplayListGoldenTestの TWO_PASS_SEALS_ELIGIBLE ==
	 * RANGE_FIRST_BINDS assert+セル専用のCELL_RANGE_SEALS ==
	 * CELL_RANGE_BINDS assert)が監視する。
	 * </p>
	 */
	public static final class DeferredBind {
		private final RootBuilder pageContext;
		private final net.zamasoft.foliojet.layout.fragment.LayoutSource source;
		private final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator;
		private final long fromId, toId;
		private final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease;
		private final IntrinsicSizes sizes;

		private DeferredBind(final RootBuilder pageContext, final ReplayBody.SourceRangeBody range,
				final IntrinsicSizes sizes) {
			this.pageContext = pageContext;
			this.source = range.source();
			this.pageGenerator = range.pageGenerator();
			this.fromId = range.fromId();
			this.toId = range.toId();
			this.lease = range.lease();
			this.sizes = sizes;
		}

		/** 固有寸法(模倣計測のスナップショット——クラスjavadoc参照)。 */
		public IntrinsicSizes sizes() {
			return this.sizes;
		}

		/** bind用のページ文脈({@code new BlockBuilder(pageContext, box)}の第1引数)。 */
		public RootBuilder pageContext() {
			return this.pageContext;
		}

		/**
		 * seal済み範囲を{@code builder}へ再駆動します
		 * ({@link TwoPassBlockBuilder#bind}のSourceRangeBody armと同型。
		 * リースは完了・失敗を問わず解放する)。
		 */
		public void bind(final BlockBuilder builder) {
			try {
				net.zamasoft.foliojet.layout.SourceReplayer.bindTwoPassRange(this.source, this.fromId, this.toId,
						builder, this.pageGenerator);
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassRangeBind();
			} finally {
				this.lease.close();
			}
		}

		/**
		 * 表Pass B(行計測)用にseal済み範囲を{@code builder}へ再駆動します
		 * (E-6増分5b-1、2026-07-24——codex設計§4.4)。{@link #bind}と同じ
		 * SegmentExecutor駆動だが、<b>リースを解放しない</b>(後続の本bindが
		 * 同じ範囲をもう一度captureする——captureはslice自身のリースを都度
		 * 取得・解放する非破壊読み)。統計(TWO_PASS_RANGE_BINDS)も計上しない
		 * (seal:bind 1:1検証を汚さない)。
		 */
		void measureInto(final BlockBuilder builder) {
			// scratch=true: 使い捨て計測。再構築される絶対配置のseal・係留を
			// スキップ(リース孤児化の防止——absolute吸収=codex増分9)
			net.zamasoft.foliojet.layout.SourceReplayer.bindTwoPassRange(this.source, this.fromId, this.toId, builder,
					this.pageGenerator, true);
		}

		/**
		 * このseal済み本文が{@code log}上の[from, to]に包含されるかを
		 * 返します(表吸収=codex増分5、2026-07-30。親range化の検証相が使う
		 * ——副作用なし)。
		 */
		boolean within(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long from, final long to) {
			return this.source == log && this.fromId >= from && this.toId <= to;
		}

		/**
		 * 親のrange化への吸収です(表吸収=codex増分5のコミット相)。
		 * リースを冪等closeし、seal:bind収支のSUBSUMED側(グローバル)を
		 * 計上する(セル固有のCELL_RANGE_SEALS_SUBSUMEDは呼び出し側の
		 * {@code CellContent}が計上する——DeferredBindは絶対配置とセルの
		 * 共用型のため)。呼び出し時点で親のリースは取得済みであること
		 * (compact可能水位の順序契約)。
		 */
		void abandonForParentRange() {
			this.lease.close();
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassSealSubsumed();
		}
	}

	protected final LayoutStack layoutStack;

	/**
	 * 固有寸法の計測器。イベントを記録(records)と同時にこちらへ流し込みます。
	 */
	private final IntrinsicMeasurer measurer = new IntrinsicMeasurer(this);

	private TextImpl text;

	private final List<AbstractContainerBox> flowStack = new ArrayList<AbstractContainerBox>();

	/**
	 * bind() の再生元(E-6増分4a)。録画中は常に LegacyRecords。
	 */
	private ReplayBody body = new ReplayBody.LegacyRecords();

	/**
	 * glyph()イベントの累計(recordsへ保持されるTextImplのglyph総量の概算)。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	private long glyphCount = 0;

	/**
	 * 直近に作られたネスト実測ビルダー。対応する InlineBlockQuad の到着時に
	 * InlineBlockEvent へ内包されます。
	 */
	private TwoPass pendingInlineBlock;

	/**
	 * legacy records bindの由来分類です(DP増分0、2026-07-30)。生成側が
	 * {@link #tagLegacyBindOrigin}で付与し、records bind時の由来別集計に
	 * 使う。既定は表外(DocumentBuilder駆動のfloat/absolute/inline-block)。
	 */
	private net.zamasoft.foliojet.layout.fragment.ContinuationStats.LegacyBindOrigin legacyBindOrigin = net.zamasoft.foliojet.layout.fragment.ContinuationStats.LegacyBindOrigin.TOPLEVEL;

	/** {@link #legacyBindOrigin}を付与します(DP増分0。生成直後に一度だけ)。 */
	public void tagLegacyBindOrigin(
			final net.zamasoft.foliojet.layout.fragment.ContinuationStats.LegacyBindOrigin origin) {
		this.legacyBindOrigin = origin;
	}

	public TwoPassBlockBuilder(LayoutStack layoutStack, AbstractContainerBox containerBox) {
		this.layoutStack = layoutStack;
		this.flowStack.add(containerBox);
		this.measurer.start(containerBox);
		// E-6増分1(2026-07-24): ネスト深さのhigh-water観測(読み取りのみ、
		// 挙動には影響しない)。layoutStack鎖上の連続するTwoPassBlockBuilder
		// 数を数える(表セル経由のネストはRetainedTableBuilderが親のlayoutStack
		// を引き継ぐため、この鎖に自然に現れる)
		int depth = 1;
		for (LayoutStack stack = layoutStack; stack instanceof TwoPassBlockBuilder parent; stack = parent.layoutStack) {
			++depth;
		}
		TableBuildStats.reportTwoPassNestDepth(depth);
	}

	/**
	 * recordsへ追記し、保持量high-waterを報告します(E-6増分1(2026-07-24)、
	 * spill閾値・対象選定の実測基盤。追記+max更新のみで挙動には影響しない)。
	 */
	private void addRecord(final Recorded recorded) {
		if (!(this.body instanceof ReplayBody.LegacyRecords legacy)) {
			// seal(録画完了)後の追記は契約違反(E-6増分4a)
			throw new IllegalStateException("seal済みビルダーへの記録: " + recorded);
		}
		legacy.records.add(recorded);
		TableBuildStats.reportTwoPassRecordRetention(legacy.records.size());
	}

	public AbstractContainerBox getFixedWidthContextBox() {
		AbstractContainerBox box = this.getContextBox();
		if (box.getBlockParams().size.getWidthType() != LengthType.AUTO) {
			return box;
		}
		switch (box.getPos().getType()) {
		case PAGE:
		case INLINE:
		case FLOW:
		case FLOAT:
		case TABLE_CELL:
		case TABLE_CAPTION:
			return this.layoutStack.getFixedWidthFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedWidthContextBox();
		default:
			throw new IllegalStateException();
		}
	}

	public AbstractContainerBox getFixedHeightContextBox() {
		AbstractContainerBox box = this.getContextBox();
		if (box.getBlockParams().size.getHeightType() != LengthType.AUTO) {
			return box;
		}
		switch (box.getPos().getType()) {
		case PAGE:
		case INLINE:
		case FLOW:
		case FLOAT:
		case TABLE_CELL:
		case TABLE_CAPTION:
			return this.layoutStack.getFixedHeightFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedHeightContextBox();
		default:
			throw new IllegalStateException(String.valueOf(box.getPos().getType()));
		}
	}

	public double getFixedWidth() {
		double frameWidth = 0;
		for (int i = this.flowStack.size() - 1; i >= 1; --i) {
			AbstractContainerBox flowBox = (AbstractContainerBox) this.flowStack.get(i);
			frameWidth += flowBox.getFrame().getFrameWidth();
			if (flowBox.getBlockParams().size.getWidthType() != LengthType.AUTO) {
				return flowBox.getWidth() - frameWidth;
			}
		}
		AbstractContainerBox box = this.getFixedWidthContextBox();
		if (box == null) {
			return 0;
		}
		return box.getInnerWidth() - frameWidth;
	}

	public AbstractContainerBox getFixedWidthFlowBox() {
		for (int i = this.flowStack.size() - 1; i >= 1; --i) {
			AbstractContainerBox flowBox = (AbstractContainerBox) this.flowStack.get(i);
			if (flowBox.getBlockParams().size.getWidthType() != LengthType.AUTO) {
				return flowBox;
			}
		}
		return this.getFixedWidthContextBox();
	}

	public double getFixedHeight() {
		double flowHeight = 0;
		for (int i = this.flowStack.size() - 1; i >= 1; --i) {
			AbstractContainerBox flowBox = (AbstractContainerBox) this.flowStack.get(i);
			flowHeight += flowBox.getFrame().getFrameHeight();
			if (flowBox.getBlockParams().size.getHeightType() != LengthType.AUTO) {
				return flowBox.getHeight() - flowHeight;
			}
		}
		AbstractContainerBox box = this.getFixedHeightContextBox();
		if (box == null) {
			return 0;
		}
		return box.getInnerHeight() - flowHeight;
	}

	public AbstractContainerBox getFixedHeightFlowBox() {
		for (int i = this.flowStack.size() - 1; i >= 1; --i) {
			AbstractContainerBox flowBox = (AbstractContainerBox) this.flowStack.get(i);
			if (flowBox.getBlockParams().size.getHeightType() != LengthType.AUTO) {
				return flowBox;
			}
		}
		return this.getFixedHeightContextBox();
	}

	public RootBuilder getPageContext() {
		return this.layoutStack.getPageContext();
	}

	public Builder getParentBuilder() {
		return (Builder) this.layoutStack;
	}

	/**
	 * 固有寸法を実レイアウト計測(M2c)で求め、範囲を特定できない場合は
	 * 旧2パスの模倣計測へフォールバックします。shrinkToFit の全消費者は
	 * getIntrinsicSizes()(模倣のみ)ではなくこちらを使うこと。
	 */
	public IntrinsicSizes intrinsicSizesMeasured() {
		final net.zamasoft.foliojet.layout.builder.impl.RootBuilder root = this.layoutStack == null ? null
				: this.getPageContext();
		if (root != null && root.isSegmentRestyle()) {
			final AbstractContainerBox rootBox = (AbstractContainerBox) this.getRootBox();
			final IntrinsicSizes measured = net.zamasoft.foliojet.layout.sizing.MeasuredIntrinsics.of(
					root.getPageGenerator().getLayoutSource(), rootBox, rootBox.getBlockParams(),
					root.getPageGenerator().getUserAgent());
			if (measured != null) {
				return measured;
			}
		}
		return this.measurer.sizes();
	}

	public IntrinsicSizes getIntrinsicSizes() {
		return this.measurer.sizes();
	}

	public boolean isMain() {
		return false;
	}

	public boolean isTwoPass() {
		return true;
	}

	public AbstractContainerBox getContextBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 1; --i) {
				AbstractContainerBox box = (AbstractContainerBox) this.flowStack.get(i);
				if (box.isContextBox()) {
					return box;
				}
			}
		}
		AbstractContainerBox box = (AbstractContainerBox) this.flowStack.get(0);
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
				final AbstractContainerBox box = (AbstractContainerBox) this.flowStack.get(i);
				if (box.getColumnCount() > 1) {
					return box;
				}
			}
		}
		return null;
	}

	public AbstractContainerBox getRootBox() {
		return (AbstractContainerBox) this.flowStack.get(0);
	}

	public AbstractContainerBox getFlowBox() {
		return (AbstractContainerBox) this.flowStack.get(this.flowStack.size() - 1);
	}

	public void startFlowBlock(final FlowBlockBox flowBox) {
		// 通常のフローのブロックボックス
		AbstractContainerBox containerBox = this.getFlowBox();
		// firstPassLayout は計測状態(浮動体アドバンス)を読まないため、
		// clearFloatAdvance(計測器側)との順序入れ替えは等価。
		flowBox.firstPassLayout(containerBox);
		this.measurer.startFlow(flowBox, containerBox);

		this.flowStack.add(flowBox);
		this.addRecord(new Recorded.StartFlow(flowBox));
	}

	public void endFlowBlock() {
		// 通常のフローのブロックボックス
		AbstractBlockBox flowBox = (AbstractBlockBox) this.flowStack.remove(this.flowStack.size() - 1);
		// 元コードでは記録(records.add)は枠反転処理と textIndent リセットの間にあったが、
		// 計測状態と records は独立のため 計測(末尾リセット含む)→記録 の順でも等価。
		this.measurer.endFlow(flowBox);
		this.addRecord(new Recorded.EndFlow((FlowBlockBox) flowBox));
	}

	public void addBound(IBox box) {
		AbstractReplacedBox replacedBox = (AbstractReplacedBox) box;
		this.measurer.bound(replacedBox);
		this.addRecord(new Recorded.ReplacedEvent(replacedBox));
	}

	public void addTable(net.zamasoft.foliojet.layout.builder.RetainedTable autoTableBuilder) {
		autoTableBuilder.prepareLayout();
		final IntrinsicSizes tableSizes = autoTableBuilder.getIntrinsicSizes();
		this.measurer.table(tableSizes);
		this.addRecord(new Recorded.TableEvent(autoTableBuilder));
		switch (autoTableBuilder.getTableBox().getBlockBox().getPos().getType()) {
		case INLINE:
			this.pendingInlineBlock = autoTableBuilder;
			break;
		}
	}

	public void addGrid(final net.zamasoft.foliojet.layout.builder.RetainedGrid gridBuilder) {
		// Grid G3d1/d2(consult-codex-2026-07-31-grid-g3.txt Q3): TwoPass
		// 宿主では実行計画を録画し、Gridのcontent-box固有寸法を計測器へ
		// 伝える(GridBoxのframeはstartFlowBlock→measurer.startFlowの
		// 通常経路が一度だけ加算する——二重計上防止は答申Q5)。
		// Gridは常にFLOW配置のためpendingInlineBlock相当はない
		this.measurer.grid(gridBuilder.getIntrinsicSizes());
		this.addRecord(new Recorded.GridEvent(gridBuilder));
	}

	public void addFlex(final net.zamasoft.foliojet.layout.builder.RetainedFlex flexBuilder) {
		// Flex F1f(addGridと同型): 実行計画を録画し、Flexのcontent-box
		// 固有寸法を計測器へ伝える(frameは通常経路が一度だけ加算)
		this.measurer.flex(flexBuilder.getIntrinsicSizes());
		this.addRecord(new Recorded.FlexEvent(flexBuilder));
	}

	public Builder newBuilder(final AbstractBlockBox stfBox) {
		// * TODO 絶対幅の場合はBoundContainerContextが使えますが、
		// * 絶対配置の位置調整を構築後に行わないといけないため
		// * そのままにしています。
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this, stfBox);
		builder.tagLegacyBindOrigin(
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.LegacyBindOrigin.NESTED);
		final AbstractContainerBox box = this.getFlowBox();
		stfBox.firstPassLayout(box);
		switch (stfBox.getPos().getType()) {
		case FLOW:
			// 書字方向が違う
		case FLOAT:
			// 浮動体
			this.addRecord(new Recorded.StfBlock(builder));
			break;

		case ABSOLUTE:
			// 絶対配置
			this.addRecord(new Recorded.AbsoluteBlock(builder));
			break;

		case INLINE:
			// インラインブロック
			this.pendingInlineBlock = builder;
			break;

		default:
			throw new IllegalStateException();
		}
		return builder;
	}

	public void fitFloating(TwoPassBlockBuilder childBuilder) {
		this.measurer.fitFloating(childBuilder);
	}

	/**
	 * TwoPass range化(E-6増分4a/4b)の有効スイッチです。増分4bで
	 * default-onへ切替(E-3の教訓: default-off期間を作ると二重経路が
	 * 固定化する。適格判定自体がfail closedのため、怪しい範囲は常に
	 * LegacyRecordsに残る)。{@code foliojet.noTwoPassRangeBind}は退避用の
	 * kill switch。動的に読むのは、parityテストが同一JVM内でon/offを
	 * 切り替えてdisplay list一致を比較するため。
	 */
	private static boolean rangeBindEnabled() {
		return !Boolean.getBoolean("foliojet.noTwoPassRangeBind");
	}

	/**
	 * 録画完了(close)時のrange sealです(E-6増分4a/4b——codex設計§3.2の
	 * 増分4b「close時range seal、records解放」)。表外float/absolute/
	 * inline-blockの録画完了点({@code DocumentBuilder.endBox}の
	 * FLOAT/ABSOLUTE/INLINE(ブロック)ケースが endContainerBuilder 直後に
	 * 呼ぶ)で、本文をLayoutSource範囲参照({@link ReplayBody.SourceRangeBody})
	 * へ切り替え、records(TextImpl glyph列・liveボックス)を手放す。
	 * 計測器({@link IntrinsicMeasurer})はsealの影響を受けない——
	 * shrinkToFitへの固有寸法供給は従来どおり。
	 *
	 * <p>
	 * 適格判定はfail closed(不適格理由は
	 * {@code ContinuationStats.TwoPassSealReject})。冪等で、録画完了後の
	 * 一度だけ効く。
	 * </p>
	 */
	public void sealBodyForRangeBind() {
		if (!(this.body instanceof ReplayBody.LegacyRecords legacy)) {
			return; // 冪等
		}
		if (!rangeBindEnabled() || this.layoutStack == null) {
			return;
		}
		final RootBuilder root = this.getPageContext();
		if (root == null || !root.isSegmentRestyle()) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_SOURCE);
			return;
		}
		final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator = root.getPageGenerator();
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = pageGenerator.getLayoutSource();
		if (log == null) {
			// scratch計測(MeasurePageGenerator)等、ログを持たない文脈
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_SOURCE);
			return;
		}
		final long anchor = this.getRootBox().getSourceAnchor();
		// Opaque記録の種別(表・表キャプション)はendOfが-1になり、ここで
		// 構造的に不適格になる(fail closed)。絶対配置はE-6増分4eの
		// recipe記録化でendOfが引けるようになった(NO_RANGE=81の解消)
		final long endId = anchor < 0 ? -1 : log.endOf(anchor);
		if (endId < 0) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_RANGE);
			return;
		}
		final long fromId = anchor + 1;
		final long toId = endId - 1;
		if (toId < fromId) {
			if (legacy.records.isEmpty()) {
				// DP増分2: 空本文はrecords経路から切り離す(bindは本文
				// 非依存のno-op。リース不要)。ソース範囲が空でもrecordsが
				// 非空でありうるか(生成コンテンツ等)は証明していないため、
				// 「両方空」のときだけEmpty化するfail closed——records非空
				// なら従来どおりEMPTY_RANGE rejectでrecords bindが内容を保つ
				this.body = new ReplayBody.Empty();
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassEmptySeal();
			} else {
				reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.EMPTY_RANGE);
			}
			return;
		}
		if (log.containsOpaque(fromId, toId) || log.captionSealGate(fromId, toId)) {
			// containsCaption(caption recipe化C1): キャプションはOpaque記録
			// からrecipe記録へ移ったが、C2のcontext-complete検証までは
			// 従来と同じ範囲を同じ理由(OPAQUE_RANGE)で弾く——routing不変。
			// 旧コメントの「キャプション付き表はOpaque記録のためここが弾く」
			// はこの分岐が引き継いだ
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.OPAQUE_RANGE);
			return;
		}
		// Gridを含む範囲(旧GRID_RANGE reject=G1d)はG3d3で解禁——G3d1の
		// RetainedGrid/GridEventによりrecords側もGridBuilderの実トラック
		// 配置を通り、範囲再生(DocumentBuilder駆動の新品GridBuilder)と
		// 幾何が一致する(パリティはTwoPassRangeBindParityTestのgrid文書
		// 3件で固定)。GridEventのitem本文は下のcollectAbsorbableNestedが
		// 通常の子として吸収する
		// 表を含む範囲(旧TABLE_RANGE reject)は表吸収(codex増分5、
		// 2026-07-30)で解禁——範囲内の適格表はrecipe再生で再構築でき、
		// 記録済みRetained計画のリース(セルのDeferredBind)は下の
		// collectAbsorbableNested/コミット相が吸収する。非適格表
		// (float/inline/absolute配置・キャプション付き)はOpaque記録の
		// ため上のOPAQUE_RANGEが引き続き弾く
		// 絶対配置(旧ABSOLUTE_RANGE即reject)はowned型付け(absolute吸収=
		// codex増分9、2026-07-30)で解禁——親recordsが排他所有を証明できた
		// absolute Startだけを許可し、1件でもunmatched(外側context・
		// 別実行計画の所有)があれば下のabsoluteStartsExactlyで従来どおり
		// rejectする。TwoPass録画中のabsoluteはcontextがTwoPassのため
		// 係留・prepareBindを一切通らず(DocumentBuilder.endBoxの
		// !isTwoPass()ガード)、吸収しても二重登録にならない
		// DP増分6(2026-07-30): 段組を含む範囲(MULTICOL_RANGE)のrejectは
		// 撤去した。範囲再生側のDocumentBuilderはfixed multicolを
		// ColumnBuilderで・autoをstartFlowBlockで駆動し(liveと同一分岐)、
		// balanceも同じ機構が決定的に再実行する——両駆動のパリティは
		// TwoPassRangeBindParityTestのfloat-STF内/表セル内/absolute内段組
		// 文書で固定する。「未検証」が唯一のreject理由だった
		// (ContinuationStats.TwoPassSealReject旧MULTICOL_RANGEのjavadoc)。
		// DP増分4(2026-07-30): 書字方向混在(MIXED_FLOW_RANGE)のrejectは
		// 撤去した。旧bindRecords自身がStartFlowの軸不一致でサブビルダーを
		// 作っており(下のbindRecords参照)、範囲再生側のDocumentBuilderにも
		// 同型の分岐がある——両駆動のパリティはTwoPassRangeBindParityTestの
		// 縦横混在文書で固定する。balance()等の別用途のmixed-flowゲート
		// (SourceReplayer側)はこの増分の対象外。
		// DP増分3(2026-07-30): ネストしたビルダーは「吸収可能」なら親の
		// range化を妨げない——検証相(副作用なし)で全子孫を列挙し、
		// コミット相で親リース取得後に子リースを閉じる(先に親リースが
		// あるためcompact可能水位は後退しない)。吸収不能(表・絶対配置・
		// 範囲外リース等)は従来どおりNESTED_BUILDERでfail closed。
		final List<TwoPassBlockBuilder> absorbable = new ArrayList<TwoPassBlockBuilder>();
		final List<RetainedTableBuilder> absorbableTables = new ArrayList<RetainedTableBuilder>();
		final java.util.Set<Long> ownedAbsoluteAnchors = new java.util.HashSet<Long>();
		if (!collectAbsorbableNested(legacy.records, log, fromId, toId, absorbable, absorbableTables,
				ownedAbsoluteAnchors,
				java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<TwoPassBlockBuilder, Boolean>()))) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NESTED_BUILDER);
			return;
		}
		if (!log.absoluteStartsExactly(fromId, toId, ownedAbsoluteAnchors)) {
			// absolute吸収(codex増分9): 範囲内のAbsolute Startのうち親records
			// が所有を証明できないものが残る(外側context・別実行計画の所有、
			// またはrecords解放済み孫range内)——fail closed
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.ABSOLUTE_RANGE);
			return;
		}
		// 範囲の完全性(連番で穴なし)の最終検証。probeのリースは即時解放
		try (net.zamasoft.foliojet.layout.fragment.LayoutSource.ReplaySlice probe = log.capture(fromId, toId)) {
			if (probe == null) {
				reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.RANGE_NOT_INTACT);
				return;
			}
		}
		// seal(コミット相): 以後の再生元は範囲参照。recordsはこの差し替えで
		// 手放される。子は親リース取得後に吸収(リース解放+Subsumed化)する
		this.body = new ReplayBody.SourceRangeBody(log, pageGenerator, fromId, toId, log.retainFrom(fromId));
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassSealEligible();
		for (final TwoPassBlockBuilder child : absorbable) {
			child.subsumeIntoParentRange();
		}
		for (final RetainedTableBuilder table : absorbableTables) {
			// 表吸収(codex増分5): seal済みセルのリース解放+計画のabandon。
			// 親の範囲再生がソースから表全体を再構築する
			table.abandonForParentRange();
		}
	}

	/**
	 * 親range化の検証相です(DP増分3)。records内のネストビルダーを
	 * identity重複排除しつつ走査し、全てが「親範囲へ吸収可能」なら
	 * {@code out}へ列挙してtrueを返します(副作用なし)。吸収可能条件:
	 * <ul>
	 * <li>{@code StfBlock}・{@code InlineBlockEvent}(TwoPass実測)の子で、
	 * seal済み({@code SourceRangeBody})なら同じLayoutSource上かつ親範囲に
	 * 包含されるリースを持つ</li>
	 * <li>未seal({@code LegacyRecords})なら孫を再帰的に検証(孫も
	 * 吸収対象として列挙——seal済みの子は自分のseal時に既に孫を吸収済みの
	 * ため再帰不要)</li>
	 * <li>空本文({@code Empty})は無条件で可</li>
	 * </ul>
	 * 表({@code TableEvent}・Retained表実測の{@code InlineBlockEvent})は
	 * セル・キャプションのリース/DeferredBindの再帰解放
	 * ({@code RetainedTableBuilder.abandonForParentRange}相当)が未実装の
	 * ため吸収不可(表recipe裁定後の増分で扱う)。{@code AbsoluteBlock}は
	 * 係留・DeferredBind二重化防止のため吸収不可(通常はABSOLUTE_RANGEの
	 * ログ側ゲートが先に親をrejectする——ここはfail closedの二重防壁)。
	 */
	private static boolean collectAbsorbableNested(final List<Recorded> records,
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId, final long toId,
			final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final java.util.Set<Long> ownedAbsoluteAnchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		for (final Recorded recorded : records) {
			final TwoPassBlockBuilder child;
			if (recorded instanceof Recorded.StfBlock stf) {
				child = stf.builder();
			} else if (recorded instanceof Recorded.InlineBlockEvent inlineBlock) {
				if (inlineBlock.measure() instanceof TwoPassBlockBuilder measured) {
					child = measured;
				} else if (inlineBlock.measure() instanceof RetainedTableBuilder inlineTable) {
					// インラインテーブル(表吸収=codex増分5)。TableEvent側と
					// identity重複するためseenではなくabandonedフラグと
					// outTablesの重複チェックで冪等化する
					if (!collectAbsorbableTable(inlineTable, log, fromId, toId, out, outTables,
							ownedAbsoluteAnchors, seen)) {
						return false;
					}
					continue;
				} else {
					return false;
				}
			} else if (recorded instanceof Recorded.TableEvent tableEvent) {
				// 表吸収(codex増分5、2026-07-30): 記録済みRetained計画の
				// セルリースが全て親範囲に包含されるなら吸収可能。
				// キャプション付き・分割済み等はfail closed
				if (!(tableEvent.builder() instanceof RetainedTableBuilder retained)
						|| !collectAbsorbableTable(retained, log, fromId, toId, out, outTables,
								ownedAbsoluteAnchors, seen)) {
					return false;
				}
				continue;
			} else if (recorded instanceof Recorded.GridEvent gridEvent) {
				// Grid吸収(G3d3): 実行計画のitem本文(合成item——LayoutSource
				// 非記録・リースなし)を通常のネスト子として検証・列挙する。
				// 親の範囲再生がGRIDレシピ(G0c)からGrid全体を再構築する。
				// 計画自体のabandonは不要——recordsごと手放される
				if (!(gridEvent.builder() instanceof GridBuilder gridPlan) || !gridPlan
						.collectAbsorbableItems(log, fromId, toId, out, outTables, ownedAbsoluteAnchors, seen)) {
					return false;
				}
				continue;
			} else if (recorded instanceof Recorded.FlexEvent flexEvent) {
				// Flex吸収(F1f——Grid吸収と同型): itemの本文を通常のネスト子
				// として検証・列挙し、親の範囲再生がFLEXレシピ(F0c)から
				// Flex全体を再構築する
				if (!(flexEvent.builder() instanceof FlexBuilder flexPlan) || !flexPlan
						.collectAbsorbableItems(log, fromId, toId, out, outTables, ownedAbsoluteAnchors, seen)) {
					return false;
				}
				continue;
			} else if (recorded instanceof Recorded.AbsoluteBlock absoluteBlock) {
				// absolute吸収(codex増分9、2026-07-30): 親recordsが排他所有する
				// absoluteだけを吸収可能とする。所有証明=①原箱のanchorが
				// 親範囲内の実Absolute Startであること ②原箱が未係留かつ
				// bind予約(builder/DeferredBind)を持たないこと(TwoPass録画中
				// のabsoluteは構造的に常に未係留——DocumentBuilder.endBoxの
				// !isTwoPass()ガード) ③anchor重複なし。収集したanchor群は
				// seal側のabsoluteStartsExactlyが範囲内全Startと完全一致照合する
				final TwoPassBlockBuilder absChild = absoluteBlock.builder();
				if (!(absChild
						.getRootBox() instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox absBox)) {
					return false;
				}
				final long anchor = absBox.getSourceAnchor();
				if (anchor < fromId || anchor > toId
						|| !(log.get(anchor) instanceof net.zamasoft.foliojet.layout.fragment.LayoutSource.Start start
								&& start.recipe() instanceof net.zamasoft.foliojet.layout.segment.BoxRecipe.Absolute)
						|| !absBox.isUnattachedForParentRange() || !ownedAbsoluteAnchors.add(anchor)) {
					return false;
				}
				child = absChild;
			} else {
				continue;
			}
			if (!collectAbsorbableChild(child, log, fromId, toId, out, outTables, ownedAbsoluteAnchors, seen)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 子ビルダー1個の吸収可否検証です(検証相・副作用なし。DP増分3の
	 * 子検証を表吸収=codex増分5でヘルパへ共通化)。
	 */
	private static boolean collectAbsorbableChild(final TwoPassBlockBuilder child,
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId, final long toId,
			final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final java.util.Set<Long> ownedAbsoluteAnchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		if (!seen.add(child)) {
			return true;
		}
		switch (child.body) {
		case ReplayBody.SourceRangeBody childRange -> {
			if (childRange.source() != log || childRange.fromId() < fromId || childRange.toId() > toId) {
				// 別ソース・親範囲外のリース——構造的には起きないが
				// fail closed(吸収すると孤児リースになる)
				return false;
			}
		}
		case ReplayBody.LegacyRecords childLegacy -> {
			if (!collectAbsorbableNested(childLegacy.records, log, fromId, toId, out, outTables,
					ownedAbsoluteAnchors, seen)) {
				return false;
			}
		}
		case ReplayBody.Empty empty -> {
			// リースなし。無条件で可
		}
		case ReplayBody.Detached detached -> {
			// DeferredBindへ持ち出し済み(絶対配置等)——所有が外にある
			return false;
		}
		case ReplayBody.Subsumed subsumed -> {
			// 二重吸収は契約違反相当——fail closed
			return false;
		}
		}
		out.add(child);
		return true;
	}

	/**
	 * 記録済みRetained表計画1個の吸収可否検証です(表吸収=codex増分5、
	 * 検証相・副作用なし)。TableEventとInlineBlockEvent(インライン
	 * テーブル)は同一計画をidentityで共有しうるため、outTablesの重複を
	 * 冪等スキップする。
	 */
	private static boolean collectAbsorbableTable(final RetainedTableBuilder retained,
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId, final long toId,
			final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final java.util.Set<Long> ownedAbsoluteAnchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		for (int i = 0; i < outTables.size(); ++i) {
			if (outTables.get(i) == retained) {
				return true;
			}
		}
		if (!retained.collectAbsorbableInto(log, fromId, toId, out, outTables, ownedAbsoluteAnchors, seen)) {
			return false;
		}
		outTables.add(retained);
		return true;
	}

	/**
	 * 表の未sealセルビルダー用の自己検証入口です(表吸収=codex増分5。
	 * {@code RetainedTableBuilder.collectAbsorbableInto}が呼ぶ——
	 * {@link #collectAbsorbableChild}と同じ検証をこのビルダー自身へ適用。
	 * セル内の孫表も{@code outTables}へ貫通する)。
	 */
	boolean collectAbsorbableSelf(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId,
			final long toId, final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final java.util.Set<Long> ownedAbsoluteAnchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		return collectAbsorbableChild(this, log, fromId, toId, out, outTables, ownedAbsoluteAnchors, seen);
	}

	/**
	 * 親のrange化に吸収されます(DP増分3のコミット相)。呼び出し時点で
	 * 親のリースは取得済みであること(子リース解放でcompact可能水位が
	 * 後退しないための順序契約)。リースcloseは冪等・非throwing。
	 */
	private void subsumeIntoParentRange() {
		if (this.body instanceof ReplayBody.SourceRangeBody range) {
			range.lease().close();
			// seal適格(TWO_PASS_SEALS_ELIGIBLE)として数えられたがbindは
			// されない——seal:bind 1:1検出の完了条件はSUBSUMEDを加えた形
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassSealSubsumed();
		}
		this.body = new ReplayBody.Subsumed();
	}

	private static void reject(final net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject reason) {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassSealReject(reason);
	}

	/**
	 * seal済み本文を{@link DeferredBind}として持ち出します(E-6増分4e)。
	 * 適格(seal済み={@code SourceRangeBody})な場合のみ値を返し、この
	 * ビルダーは{@code Detached}状態(以後のbindは契約違反)になる。
	 * 不適格({@code LegacyRecords})ならnull——呼び出し側
	 * ({@code AbsoluteBlockBox.prepareBind}・E-6増分5aの
	 * {@code CellContent.sealForRangeBind})はfail closedでビルダー保持を
	 * 継続する。
	 */
	/**
	 * 本文が「records空のLegacyRecords」か(=bindが何も再演しない空本文か)を
	 * 返します(E-6増分5b-2、2026-07-24)。空セル({@code <td></td>}等)は
	 * 子イベントを持たずEMPTY_RANGEでseal不適格になるが、bindは本文非依存
	 * (BlockBuilderのopen/closeのみ)のため、表Pass Bはビルダーに触れずに
	 * 複製セルbox上のclose-onlyで計測できる——この判定はその適格条件
	 * ({@code CellContent.isPassBMeasurable})の部品。
	 */
	boolean hasEmptyRecordedBody() {
		// DP増分2: seal済みの空本文(Empty)も「bindが何も再演しない空本文」
		return this.body instanceof ReplayBody.Empty
				|| this.body instanceof ReplayBody.LegacyRecords legacy && legacy.records.isEmpty();
	}

	public DeferredBind detachDeferredBind() {
		if (!(this.body instanceof ReplayBody.SourceRangeBody range)) {
			return null;
		}
		this.body = new ReplayBody.Detached();
		// sizesスナップショットの同値性はDeferredBindのjavadoc参照
		// (絶対配置のintrinsicSizesMeasured()は常に模倣計測へフォール
		// バックし、計測器は録画完了後不変)
		return new DeferredBind(this.getPageContext(), range, this.measurer.sizes());
	}

	public void bind(BlockBuilder builder) {
		switch (this.body) {
		case ReplayBody.SourceRangeBody range -> {
			// E-6増分4b: seal済み範囲からのSegmentExecutor駆動bind。
			// リースはbindの完了・失敗を問わず解放する(取り残すと以後の
			// compactが永久にclampされる——LayoutSource.ReplaySliceと同じ規約)
			try {
				net.zamasoft.foliojet.layout.SourceReplayer.bindTwoPassRange(range.source(), range.fromId(),
						range.toId(), builder, range.pageGenerator());
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassRangeBind();
			} finally {
				range.lease().close();
			}
		}
		case ReplayBody.LegacyRecords legacy -> {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassLegacyRecordBind(this.legacyBindOrigin);
			this.bindRecords(builder, legacy.records);
		}
		case ReplayBody.Empty empty ->
			// DP増分2: 空本文のbindはno-op(旧来も空recordsのループで
			// 何も再演しなかった——BlockBuilderのopen/closeは呼び出し側)
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassEmptyBind();
		case ReplayBody.Detached detached ->
			// E-6増分4e: DeferredBindへ持ち出し済み。bindはDeferredBindが担う
			throw new IllegalStateException("DeferredBindへ持ち出し済みのビルダーへのbind");
		case ReplayBody.Subsumed subsumed ->
			// DP増分3: 親の範囲再生が内容ごと再構築する。個別bindは契約違反
			throw new IllegalStateException("親のrange化に吸収済みのビルダーへのbind");
		}
	}

	private void bindRecords(BlockBuilder builder, final List<Recorded> records) {
		// 再レイアウト
		if (DEBUG) {
			System.err.println("BIND");
		}
		FilterGlyphHandler textUnitizer = null;

		for (final Recorded recorded : records) {
			switch (recorded) {
			case Recorded.ElementEvent elementEvent: {
				if (textUnitizer == null) {
					textUnitizer = new CSSJTextUnitizer(builder.getFlowBox().getBlockParams());
					textUnitizer.setGlyphHandler(new BuilderGlyphHandler(builder));
				}
				final Element e = elementEvent.element();
				if (DEBUG) {
					System.err.println("ELEMENT " + e);
				}
				if (e instanceof Text) {
					final Text text = ((Text) e);
					assert text.getGlyphCount() > 0;
					text.toGlyphs(textUnitizer);
				} else if (e instanceof TextControl) {
					final TextControl quad = (TextControl) e;
					textUnitizer.control(quad);
				} else {
					throw new IllegalStateException();
				}
			}
				break;

			case Recorded.InlineBlockEvent inlineBlockEvent: {
				if (textUnitizer == null) {
					textUnitizer = new CSSJTextUnitizer(builder.getFlowBox().getBlockParams());
					textUnitizer.setGlyphHandler(new BuilderGlyphHandler(builder));
				}
				// インラインテーブルの実測(RetainedTableBuilder)は TableEvent 側で bind される
				if (inlineBlockEvent.measure() instanceof TwoPassBlockBuilder stfBuilder) {
					final InlineBlockBox inlineBlockBox = inlineBlockEvent.quad().box;
					inlineBlockBox.shrinkToFit(builder, stfBuilder.intrinsicSizesMeasured(), false);
					final BlockBuilder inlineBlockBuilder = new BlockBuilder(this, inlineBlockBox);
					stfBuilder.bind(inlineBlockBuilder);
					inlineBlockBuilder.close();
				}
				textUnitizer.control(inlineBlockEvent.quad());
			}
				break;

			case Recorded.EndTextBlock endTextBlock:
				if (DEBUG) {
					System.err.println("END_TEXT_BLOCK");
				}
				// 内容が空のテキストブロック(例: 空のテーブルセル)では
				// ElementEvent/InlineBlockEventが一度も来ずtextUnitizerが
				// 遅延初期化されないままここに達することがある(2026-07-18、
				// <td></td>のような空セルでNullPointerExceptionが実際に
				// 発生した)。他のcase節と同じくnullガードで対応する。
				if (textUnitizer != null) {
					textUnitizer.close();
					textUnitizer = null;
				}
				builder.endTextBlock();
				break;

			case Recorded.StartFlow startFlow: {
				if (DEBUG) {
					System.err.println("START_FLOW");
				}
				final FlowBlockBox flow = startFlow.box();
				if (flow.getBlockParams().flow.isVertical() == builder.getRootBox().getBlockParams().flow.isVertical()) {
					builder.startFlowBlock(flow);
				} else {
					// 書字方向が違う場合
					builder = (BlockBuilder) builder.newBuilder(flow);
				}
			}
				break;

			case Recorded.EndFlow endFlow:
				if (DEBUG) {
					System.err.println("END_FLOW");
				}
				final FlowBlockBox flow = endFlow.box();
				if (flow == builder.getRootBox()) {
					builder = (BlockBuilder) builder.getParentBuilder();
					builder.addBound(flow);
				} else {
					builder.endFlowBlock();
				}
				break;

			case Recorded.ReplacedEvent replacedEvent: {
				if (DEBUG) {
					System.err.println("REPLACED");
				}
				final IBox replacedBox = replacedEvent.box();
				switch (replacedBox.getPos().getType()) {
				case FLOAT:
				case FLOW:
					if (textUnitizer != null) {
						textUnitizer.flush();
					}
				}
				builder.addBound(replacedBox);
			}
				break;

			case Recorded.StfBlock stfBlock: {
				if (DEBUG) {
					System.err.println("STF");
				}
				if (textUnitizer != null) {
					textUnitizer.flush();
				}
				final TwoPassBlockBuilder stfBuilder = stfBlock.builder();
				final AbstractStaticBlockBox blockBox = (AbstractStaticBlockBox) stfBuilder.getRootBox();
				blockBox.shrinkToFit(builder, stfBuilder.intrinsicSizesMeasured(), false);
				final BlockBuilder boundBuilder = new BlockBuilder(this, blockBox);
				stfBuilder.bind(boundBuilder);
				boundBuilder.close();
				builder.addBound(blockBox);
			}
				break;

			case Recorded.AbsoluteBlock absoluteBlock: {
				if (DEBUG) {
					System.err.println("ABSOLUTE");
				}

				final TwoPassBlockBuilder stfBuilder = absoluteBlock.builder();
				final AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) stfBuilder.getRootBox();
				final AbstractContainerBox containerBox;
				if (absoluteBox.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
					containerBox = builder.getPageContext().getRootBox();
				} else {
					containerBox = builder.getContextBox();
				}
				absoluteBox.shrinkToFit(containerBox, stfBuilder.intrinsicSizesMeasured());
				final BlockBuilder boundBuilder = new BlockBuilder(this, absoluteBox);
				stfBuilder.bind(boundBuilder);
				boundBuilder.close();
				final AbsolutePos pos = absoluteBox.getAbsolutePos();
				switch (pos.autoPosition) {
				case AutoPosition.BLOCK:
					builder.addBound(absoluteBox);
					break;
				case AutoPosition.INLINE:
					final TextControl quad = InlineQuad.createInlineAbsoluteBoxQuad(absoluteBox);
					if (textUnitizer == null) {
						textUnitizer = new CSSJTextUnitizer(builder.getFlowBox().getBlockParams());
						textUnitizer.setGlyphHandler(new BuilderGlyphHandler(builder));
					}
					textUnitizer.control(quad);
					break;
				default:
					throw new IllegalStateException();
				}
			}
				break;

			case Recorded.GridEvent gridEvent: {
				if (DEBUG) {
					System.err.println("GRID");
				}
				// Grid G3d1: 直前のStartFlow(GridBox)でactive flowがGridに
				// なっている。bindがトラック解決→item bind→配置→カーソル
				// 同期まで行い、続くEndFlowが通常どおりGridを畳む
				if (textUnitizer != null) {
					textUnitizer.flush();
				}
				gridEvent.builder().bind(builder);
			}
				break;

			case Recorded.FlexEvent flexEvent: {
				if (DEBUG) {
					System.err.println("FLEX");
				}
				// Flex F1f(GridEventと同型): 直前のStartFlow(FlexBox)で
				// active flowがFlexになっている
				if (textUnitizer != null) {
					textUnitizer.flush();
				}
				flexEvent.builder().bind(builder);
			}
				break;

			case Recorded.TableEvent tableEvent: {
				if (DEBUG) {
					System.err.println("TABLE");
				}
				final net.zamasoft.foliojet.layout.builder.RetainedTable tableBuilder = tableEvent.builder();
				switch (tableBuilder.getTableBox().getBlockBox().getPos().getType()) {
				case FLOAT:
				case FLOW:
					if (textUnitizer != null) {
						textUnitizer.flush();
					}
				}
				tableBuilder.bind(builder);
			}
				break;

			default:
				throw new IllegalStateException();
			}
		}
		if (DEBUG) {
			System.err.println("/FINISH");
		}
	}

	/** 和文詰めA2: text-autospaceのpair追跡(初回glyphで遅延初期化)。 */
	private net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker autospace;

	public void startTextRun(int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.text = new TextImpl(charOffset, fontStyle, fontMetrics);
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		// 和文詰めA2: 境界gapを計測器のmax-contentへ計上する(記録textは
		// 変異させない——records再生はtoGlyphsでxadvanceを運ばず、再構築
		// 時にTextBuilder側trackerが再適用するため。min-content(atomic
		// unit)にも入れない: 和欧文境界は分割機会でgapは分割時に消える)
		if (this.autospace == null) {
			this.autospace = new net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker();
			final net.zamasoft.foliojet.layout.box.params.AbstractTextParams params = //
					(net.zamasoft.foliojet.layout.box.params.AbstractTextParams) this.getRootBox().getParams();
			this.autospace.setFlags(params.textAutospace);
			this.autospace.setTrimOff(params.textSpacingTrimOff);
		}
		final double fontSize = this.text.getFontStyle().getSize();
		final double gap = this.autospace.gapBefore(ch, coff, fontSize);
		// T1a: 同一run内の約物詰め(font層から移管)。記録textは変異させず
		// 計測値だけ旧base挙動どおりtrimを差し引く(min/max両方——trim
		// pairは禁則で不可分。再構築時はTextBuilder側trackerが再適用)
		final double trim = this.autospace.trimBefore(ch, coff, gid, this.text,
				this.text.getFontMetrics(), fontSize);
		// appendGlyph は記録用 TextImpl を構築しつつアドバンスを返すため、
		// 呼び出しは一度だけ行い、結果を計測器へ渡す。
		double advance = this.text.appendGlyph(ch, coff, clen, gid);
		if (gap > 0) {
			this.measurer.autospaceGap(gap);
		}
		this.measurer.glyph(advance - trim);
		this.autospace.glyphAdded(this.text, fontSize, ch, coff, clen, gid);
		// E-6増分1(2026-07-24): glyph保持量の概算観測(加算のみ、挙動不変)
		++this.glyphCount;
	}

	public void endTextRun() {
		this.text.pack();
		this.addRecord(new Recorded.ElementEvent(this.text));
		this.text = null;
		TableBuildStats.reportTwoPassGlyphRetention(this.glyphCount);
	}

	public void control(final TextControl quad) {
		// 和文詰めA2: 制御はpairを断つ(TextBuilder側と同じ規約——幅0の
		// インライン開始/終了だけはpairを維持)
		if (this.autospace != null && !(quad instanceof InlineQuad inlineQuad
				&& (inlineQuad.getType() == InlineQuad.INLINE_START
						|| inlineQuad.getType() == InlineQuad.INLINE_END)
				&& inlineQuad.getAdvance() == 0)) {
			this.autospace.reset();
		}
		final TwoPass inlineBlockMeasure;
		if (quad instanceof InlineBlockQuad inlineBlockQuad && !inlineBlockQuad.box.isPreMeasured()) {
			// ネストした実測ビルダーをイベントに内包する(旧: recordInlineBlocks 側チャネル)
			inlineBlockMeasure = this.pendingInlineBlock;
			assert inlineBlockMeasure != null;
			this.pendingInlineBlock = null;
			this.addRecord(new Recorded.InlineBlockEvent(inlineBlockQuad, inlineBlockMeasure));
		} else {
			inlineBlockMeasure = null;
			this.addRecord(new Recorded.ElementEvent(quad));
		}
		this.measurer.control(quad, inlineBlockMeasure);
	}

	public void flush() {
		this.measurer.flush();
	}

	public void finish() {
		this.flush();
	}

	public void close() {
		this.finish();
	}

	public void endTextBlock() {
		this.addRecord(Recorded.EndTextBlock.INSTANCE);
		this.measurer.endTextBlock();
	}

	public boolean isEmpty() {
		// seal済み(SourceRangeBody)は適格判定が空範囲を除外しているため
		// 常に非空(E-6増分4a)。空本文seal(Empty、DP増分2)は空
		return this.hasEmptyRecordedBody();
	}

	/**
	 * recordsが現に保持しているglyph数の概算です(E-6増分5a、2026-07-24。
	 * Retained表の保持量観測{@code TableBuildStats
	 * .reportRetainedCellGlyphRetention}専用の読み取り——seal済み
	 * (records解放済み)は0)。挙動には影響しない。
	 */
	long retainedGlyphs() {
		return this.body instanceof ReplayBody.LegacyRecords ? this.glyphCount : 0;
	}
}
