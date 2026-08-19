package net.zamasoft.foliojet.layout;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableParams;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.TableBuilder;
import net.zamasoft.foliojet.layout.builder.TableBuilderHost;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilderLifecycle;
import net.zamasoft.foliojet.layout.builder.impl.GridBuilder;
import net.zamasoft.foliojet.layout.builder.impl.GridBuilderLifecycle;
import net.zamasoft.foliojet.layout.builder.impl.IncrementalTableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.builder.impl.StyledTextUnitizer;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.util.NumberUtils;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: DocumentBuilder.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class DocumentBuilder implements TableBuilderHost {
	private static final boolean DEBUG = false;

	private static final Logger LOG = Logger.getLogger(DocumentBuilder.class.getName());

	public static final byte PAGE_MODE_CONTINUOUS = 1;
	public static final byte PAGE_MODE_NO_BREAK = 1 << 1;
	
	private final boolean normalizeText;

	protected static class ContainerBuilderEntry {
		public final Builder builder;

		protected StyledTextUnitizer styledTextAtomizer = null;

		public ContainerBuilderEntry(Builder builder) {
			this.builder = builder;
		}

		/**
		 * テキスト出力のためのインタフェースを返します。
		 * 
		 * @return
		 */
		public StyledTextUnitizer getStyledTextUnitizer() {
			if (this.styledTextAtomizer == null) {
				this.styledTextAtomizer = new StyledTextUnitizer(this.builder);
			}
			return this.styledTextAtomizer;
		}
	}

	/**
	 * ページ生成オブジェクト。
	 */
	private final PageGenerator pageGenerator;

	private byte pageMode = 0;

	private final List<INonReplacedBox> boxStack = new ArrayList<INonReplacedBox>();

	private final List<Object> builderStack = new ArrayList<Object>();

	private final List<Object> inlineStack = new ArrayList<Object>();

	private final List<Object> columnSpanStack = new ArrayList<Object>();

	/**
	 * 使い捨て計測(表Pass Bの複製セル計測)駆動か(absolute吸収=codex
	 * 増分9、2026-07-30)。trueのとき、再構築される絶対配置ブロックの
	 * seal・prepareBind・係留をスキップする——scratchでsealすると本物の
	 * リースを取得したままreplicaごと破棄されリース孤児化する。絶対配置は
	 * flow外でセルの計測値に寄与しないためスキップは計測等価
	 * ({@code SourceReplayer.bindTwoPassRange}のscratch引数参照)。
	 */
	private final boolean scratchMeasurement;

	public DocumentBuilder(PageGenerator pageGenerator) {
		this.pageGenerator = pageGenerator;
		this.normalizeText = UAProps.INPUT_NORMALIZE_TEXT.getBoolean(pageGenerator.getUserAgent());
		this.scratchMeasurement = false;
	}

	/**
	 * 改ページ残余のソース再生用に、既存のルートビルダーへ向けた
	 * ドキュメントビルダーを作ります(M6b v3)。ライブの DocumentBuilder
	 * の unitizer・コンテナ状態には一切触れず、新品の状態で記録済み
	 * プロトコルを再駆動するためのものです。
	 */
	public DocumentBuilder(PageGenerator pageGenerator, BlockBuilder existingRoot) {
		this(pageGenerator, existingRoot, false);
	}

	/** {@code scratch}については{@link #scratchMeasurement}参照。 */
	public DocumentBuilder(PageGenerator pageGenerator, BlockBuilder existingRoot, boolean scratch) {
		this.pageGenerator = pageGenerator;
		this.normalizeText = UAProps.INPUT_NORMALIZE_TEXT.getBoolean(pageGenerator.getUserAgent());
		this.scratchMeasurement = scratch;
		this.startContainerBuilder(existingRoot);
		this.startContainer();
	}

	/**
	 * ソース再生を終了し、テキスト文脈を対称に閉じます(M6b v3)。
	 */
	public void finishReplay() {
		this.endContainer();
	}

	/**
	 * 現在のコンテナの配達済みソース文字終端を返します(M6b v3)。
	 * shaper 内の未配達文字はこれ以降にある。
	 */
	public int getDeliveredCharEnd() {
		if (this.builderStack.isEmpty()) {
			return 0;
		}
		return this.containerBuilder().getStyledTextUnitizer().getDeliveredCharEnd();
	}

	/**
	 * ソース再生を、ビルダーのテキストブロックを開いたまま終えます
	 * (M6b v3: 切断段落の尾部再生。続く SAX ストリームが同じ
	 * テキストブロックへ流れ込む — box-restyle と同じ継ぎ目意味論)。
	 */
	public void finishReplayKeepText() {
		this.containerBuilder().getStyledTextUnitizer().flushText();
	}

	public void setPageMode(byte pageMode) {
		this.pageMode = pageMode;
	}

	public byte getPageMode() {
		return this.pageMode;
	}

	private void requirePage() {
		if (!this.builderStack.isEmpty()) {
			return;
		}
		byte mode = (this.pageMode & (PAGE_MODE_CONTINUOUS | PAGE_MODE_NO_BREAK)) != 0 ? BreakableBuilder.MODE_NO_BREAK
				: BreakableBuilder.MODE_PAGE_BREAK;
		BlockBuilder builder = new RootBuilder(this.pageGenerator, mode);
		this.startContainerBuilder(builder);
		this.startContainer();
	}

	private void startContainerBuilder(Builder builder) {
		this.builderStack.add(new ContainerBuilderEntry(builder));
	}

	private ContainerBuilderEntry containerBuilder() {
		int index = this.builderStack.size() - 1;
		Object o = this.builderStack.get(index);
		while (o instanceof TableBuilder || o instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator) {
			// テーブル内でセル外のinline, block, テキスト等をテーブルの前に置くため
			// 一般的なブラウザの動作による
			// GridBuilderのskipは安全網(答申はskip不要としたが、item外へ
			// 漏れた経路がcastで落ちるより宿主へ流す方が頑健。捕捉すべき
			// 内容は3つの入口——startBox/characters/addReplacedBox——で
			// item化するので、ここへ落ちるのは未配線経路のみ)
			--index;
			assert index >= 0 : "builderStack が全て TableBuilder で、周囲のコンテナが見つかりません";
			o = this.builderStack.get(index);
		}
		return (ContainerBuilderEntry) o;
	}

	private ContainerBuilderEntry contextBuilder() {
		for (int i = this.builderStack.size() - 1; i >= 0; --i) {
			Object entry = this.builderStack.get(i);
			if (entry instanceof ContainerBuilderEntry) {
				return (ContainerBuilderEntry) entry;
			}
		}
		throw new ArrayIndexOutOfBoundsException("builderStack に ContainerBuilderEntry がありません: " + this.builderStack);
	}

	/**
	 * ビルダースタックの根(通常はページ文脈の{@code RootBuilder})を
	 * 返します。二段階(two-pass)構築のbind先ビルダーの親として使う。
	 *
	 * <p>
	 * 2026-07-24(M6c-5): 型を{@code RootBuilder}から{@link BlockBuilder}へ
	 * 緩和した。live構築ではスタックの根は常に{@code RootBuilder}のため
	 * 挙動は不変だが、rootlessなソース再生では旧castが
	 * {@code ClassCastException}になり得た。呼び出し側はいずれも
	 * {@code LayoutStack}/{@code getRootBox()}としてしか使わない。
	 * </p>
	 */
	private BlockBuilder pageContextBuilder() {
		return (BlockBuilder) ((ContainerBuilderEntry) this.builderStack.get(0)).builder;
	}

	private ContainerBuilderEntry endContainerBuilder() {
		ContainerBuilderEntry entry = this.containerBuilder();
		if (!entry.builder.isTwoPass()) {
			((BlockBuilder) entry.builder).close();
		}
		// 不変条件: containerBuilder() が探し当てたエントリは builderStack の
		// 末尾でなければならない(末尾に TableBuilder が残ったまま末尾要素を
		// 取り除くと、containerBuilder() が返した entry とは別物を消してしまい、
		// スタックが静かに壊れる — 表キャプション単独再生クラッシュ
		// (2026-07-18)の調査で発見した builderStack 系の脆さの類例)。
		assert this.builderStack.get(this.builderStack.size() - 1) == entry : //
		"containerBuilder() の結果が末尾要素と一致しません: entry=" + entry + ", stack=" + this.builderStack;
		this.builderStack.remove(this.builderStack.size() - 1);
		return entry;
	}

	private TableBuilder tableBuilder() {
		Object top = this.builderStack.get(this.builderStack.size() - 1);
		// 不変条件: このメソッドが呼ばれる時点で、同一の再生/構築セッション内で
		// 対応する TABLE 種別のボックスが先に開始され TableBuilder が積まれて
		// いなければならない。破れていると builderStack の末尾はただの
		// ContainerBuilderEntry のままキャストに失敗する(表キャプションの
		// 単独ソース再生クラッシュ、2026-07-18 で実際に発生・修正済み)。
		// caption recipe化C2(2026-08-01): assert無効の本番でも黙って
		// ClassCastExceptionにせず、通常の実行時例外として型付きで止める
		// (G-1再発防止の本体は範囲適格のcontext-complete検証と
		// SegmentExecutorのkindスタック——これは最終防衛)
		if (!(top instanceof TableBuilder tableBuilder)) {
			throw new IllegalStateException(
					"表構造の外(先行する TABLE 開始イベントなし)で TABLE_CELL/TABLE_ROW/CAPTION 系ボックスを"
							+ "構築しようとしました。単独ソース再生の対象になっていないか確認してください: top=" + top);
		}
		return tableBuilder;
	}

	private TableBuilder endTableBuilder() {
		assert !this.builderStack.isEmpty() && this.builderStack
				.get(this.builderStack.size() - 1) instanceof TableBuilder : //
		"閉じるべき TableBuilder が builderStack の末尾にありません: " + this.builderStack;
		TableBuilder builder = (TableBuilder) this.builderStack.remove(this.builderStack.size() - 1);
		return builder;
	}

	/**
	 * Grid直下(boxStack末尾が当のGridBox)で次の内容を待っている
	 * {@link GridBuilder}を返します(Grid G1b、2026-07-31——
	 * consult-codex-2026-07-31-grid-g1.txt §3)。builderStack末尾が
	 * GridBuilder本体のとき、または末尾が開いているitemのentryで
	 * その直下がGridBuilderのとき(itemの中の入れ子内容は末尾boxが
	 * GridBoxでないため対象外になる)。
	 */
	private net.zamasoft.foliojet.layout.builder.ItemCoordinator coordinatorAwaitingDirectChild() {
		if (this.boxStack.isEmpty() || this.builderStack.isEmpty()) {
			return null;
		}
		final Object tail = this.boxStack.get(this.boxStack.size() - 1);
		final int index = this.builderStack.size() - 1;
		final Object top = this.builderStack.get(index);
		if (top instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator c) {
			return c.getItemHostBox() == tail ? c : null;
		}
		if (top instanceof ContainerBuilderEntry && index > 0
				&& this.builderStack.get(index - 1) instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator c //
				&& c.hasOpenItem() && c.getItemHostBox() == tail) {
			return c;
		}
		return null;
	}

	/** {@code box}の終端で畳むべきcoordinatorを返します。 */
	private net.zamasoft.foliojet.layout.builder.ItemCoordinator coordinatorEndingAt(final IBox box) {
		final int index = this.builderStack.size() - 1;
		final Object top = this.builderStack.get(index);
		if (top instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator c && c.getItemHostBox() == box) {
			return c;
		}
		if (top instanceof ContainerBuilderEntry && index > 0
				&& this.builderStack.get(index - 1) instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator c //
				&& c.hasOpenItem() && c.getItemHostBox() == box) {
			return c;
		}
		return null;
	}

	/** 開いている匿名item(直接テキスト用)を畳みます。element itemは対象外。 */
	private void closeAnonymousItem(final net.zamasoft.foliojet.layout.builder.ItemCoordinator c) {
		if (c.hasOpenItem() && !c.hasOpenElementItem()) {
			this.endContainer();
			this.endContainerBuilder();
			c.itemClosed();
		}
	}

	/** coordinator直下の直接テキスト/インライン用に匿名itemを用意します。 */
	private void requireCoordinatorAnonymousItem() {
		final net.zamasoft.foliojet.layout.builder.ItemCoordinator c = this.coordinatorAwaitingDirectChild();
		if (c != null && !c.hasOpenItem()) {
			this.startContainerBuilder((Builder) c.requireAnonymousItem());
			this.startContainer();
		}
	}

	/**
	 * Grid直下にelement itemを開きます(開いている匿名itemは畳む)。
	 * {@code spec}はauthored childのFlowPosからの明示配置スナップショット
	 * (G4a——consult-codex-2026-07-31-grid-g4.txt Q1)。
	 */
	private GridBuilder startGridElementItem(final net.zamasoft.foliojet.layout.box.params.GridItemSpec spec) {
		return this.startGridElementItem(spec, -1);
	}

	private GridBuilder startGridElementItem(final net.zamasoft.foliojet.layout.box.params.GridItemSpec spec,
			final double minContributionCap) {
		if (this.coordinatorAwaitingDirectChild() instanceof GridBuilder grid) {
			this.closeAnonymousItem(grid);
			this.startContainerBuilder(grid.startElementItem(spec, minContributionCap));
			this.startContainer();
			return grid;
		}
		return null;
	}

	/**
	 * Grid itemの行方向min-content寄与の上限を求めます(2026-08-19、
	 * css-grid §6.6のautomatic minimum size。
	 * {@code GridItemContent.minContributionCap}参照)。
	 * スクロールコンテナは0、行軸のmin寸法が明示宣言(FlexItemSpecの
	 * F1a判定を流用)されABSOLUTEならその値。それ以外は無制限(-1)。
	 * %のminは基準未確定のため数えない(IntrinsicMeasurerと同じ規約)。
	 */
	private static double gridItemMinContributionCap(final IBox box) {
		final net.zamasoft.foliojet.layout.box.params.BlockParams params;
		if (box instanceof net.zamasoft.foliojet.layout.box.AbstractContainerBox c) {
			params = c.getBlockParams();
		} else if (box instanceof TableBox table) {
			params = table.getBlockBox().getBlockParams();
		} else {
			return -1;
		}
		if (params.overflow.clipsPaint()) {
			return 0;
		}
		final net.zamasoft.foliojet.layout.box.params.FlexItemSpec flexSpec = box
				.getPos() instanceof FlowPos flowPos ? flowPos.flexItem : null;
		if (flexSpec == null) {
			return -1;
		}
		final boolean vertical = params.flow.isVertical();
		final boolean minAuto = vertical ? flexSpec.minHeightAuto() : flexSpec.minWidthAuto();
		if (minAuto) {
			return -1;
		}
		final net.zamasoft.foliojet.layout.box.params.Dimension minSize = params.minSize;
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = params.flow;
		if (minSize.getLineType(flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			return Math.max(0, minSize.getLineLength(flow));
		}
		return -1;
	}

	/** boxの明示配置指定を取り出します(FlowPosを持たない配置はauto)。 */
	private static net.zamasoft.foliojet.layout.box.params.GridItemSpec gridItemSpecOf(final IBox box) {
		if (box.getPos() instanceof FlowPos flowPos) {
			return flowPos.gridItem;
		}
		if (box instanceof TableBox table && table.getBlockBox().getPos() instanceof FlowPos flowPos) {
			return flowPos.gridItem;
		}
		return net.zamasoft.foliojet.layout.box.params.GridItemSpec.AUTO;
	}

	/** element itemの一件分を畳みます(one-shot経路と子endBox後の共通処理)。 */
	private void endCoordinatorElementItem(final net.zamasoft.foliojet.layout.builder.ItemCoordinator c) {
		this.endContainer();
		this.endContainerBuilder();
		c.itemClosed();
	}

	/**
	 * {@code box}を元とするtakeover element itemが末尾で開いていれば
	 * そのFlexBuilderを返します(Flex F1d——authored boxのendBox対応付け)。
	 */
	private FlexBuilder flexItemEndingAt(final IBox box) {
		// takeover(authored boxをitem boxへ引き継ぐ)はFlex固有のため
		// coordinator一般化の対象外
		final int index = this.builderStack.size() - 1;
		if (index > 0 && this.builderStack.get(index) instanceof ContainerBuilderEntry
				&& this.builderStack.get(index - 1) instanceof FlexBuilder flex //
				&& flex.isElementItemSource(box)) {
			return flex;
		}
		return null;
	}

	/**
	 * Flex直下に中立wrapperのelement itemを開きます(非plain子・表・置換用)。
	 * {@code authored}(非null)はchildのparamsで、行方向の寸法指定を
	 * wrapperが引き取る({@link FlexBuilder#startNeutralElementItem}参照)。
	 */
	private FlexBuilder startFlexNeutralElementItem(final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec,
			final FlexBuilder.NeutralTransfer authored) {
		if (this.coordinatorAwaitingDirectChild() instanceof FlexBuilder flex) {
			this.closeAnonymousItem(flex);
			this.startContainerBuilder(flex.startNeutralElementItem(spec, authored));
			this.startContainer();
			return flex;
		}
		return null;
	}

	/** boxの伸縮指定を取り出します(FlowPosを持たない配置は既定)。 */
	private static net.zamasoft.foliojet.layout.box.params.FlexItemSpec flexItemSpecOf(final IBox box) {
		if (box.getPos() instanceof FlowPos flowPos) {
			return flowPos.flexItem;
		}
		if (box instanceof TableBox table && table.getBlockBox().getPos() instanceof FlowPos flowPos) {
			return flowPos.flexItem;
		}
		return net.zamasoft.foliojet.layout.box.params.FlexItemSpec.DEFAULT;
	}

	/**
	 * {@link TableBuilderHost}実装(C4-C深化、2026-07-19)。
	 * {@link TableBuilder}実装(現状は{@link IncrementalTableBuilder}のみ)が
	 * 表のセル/カラム/行グループ/行に入る前後で必要なインライン文脈操作を
	 * 呼び出すための公開経路。
	 */
	@Override
	public void closeInlines(Params params) {
		int count = 0;

		for (int i = this.boxStack.size() - 1; i >= 0; --i) {
			final IBox box = (IBox) this.boxStack.get(i);
			if (box.getType() != BoxType.INLINE) {
				break;
			}
			this.endBox();
			this.inlineStack.add(box);
			++count;
		}
		if (count > 0) {
			this.inlineStack.add(NumberUtils.intValue(count));
			this.inlineStack.add(params);
		}
		if (DEBUG) {
			System.err.println(count + ":" + params.element);
		}
	}

	private void restoreInlines(Params params) {
		if (DEBUG) {
			System.err.println("/:" + params.element);
		}
		if (this.inlineStack.isEmpty() || this.inlineStack.get(this.inlineStack.size() - 1) != params) {
			return;
		}
		this.inlineStack.remove(this.inlineStack.size() - 1);
		final Integer count = (Integer) this.inlineStack.remove(this.inlineStack.size() - 1);
		for (int i = 0; i < count.intValue(); ++i) {
			InlineBox box = (InlineBox) this.inlineStack.remove(this.inlineStack.size() - 1);
			box = box.splitLine(false);
			this.startBox(box);
		}
	}

	private void startColumnSpan(FlowPos pos) {
		final Builder builder = this.containerBuilder().builder;
		if (builder.getMulticolumnBox() == null) {
			return;
		}

		// **ここでインラインを開き直さない**(2026-07-28)。
		//
		// かつては各周回の最後に {@code restoreInlines(blockBox.getParams())}
		// を呼んでいたが、これは {@code startBox(blockBox)} の
		// {@code closeInlines(params)} と対になるべき登録を**先取り**する
		// もので、対の相手は本来 {@code endBox(blockBox)} 側の
		// {@code restoreInlines} である。
		//
		// 先取りするとインラインが**開いたまま**次の周回の
		// {@code endContainer()} を跨ぐ。{@code endContainer()}は
		// {@code textParamsStack}の先頭を「コンテナのparams」と決めて外し、
		// さらに{@code textShaper}を捨てる(=その先の
		// {@code InlineParamsStack}も消える)ので、開いていたインラインを
		// 閉じるときに **3つのスタックが同時にずれる**。
		// 症状は{@code InlineParamsStack.current}の`Index -1`
		// (WPT css-multicol/multicol-span-all-children-height-010 等)。
		//
		// 開き直さなければ、登録は {@code endBox(blockBox)} が通常どおり
		// 消費する——ぶち抜きでない場合とまったく同じ経路になる。
		// これに合わせて {@code endColumnSpan} 側の {@code closeInlines}
		// (開き直した分を閉じ直すための対)も外した。
		final List<AbstractBlockBox> flows = new ArrayList<AbstractBlockBox>();
		for (;;) {
			final AbstractBlockBox blockBox = (AbstractBlockBox) builder.getFlowBox();
			flows.add(blockBox);
			if (blockBox.getColumnCount() > 1) {
				final BlockParams colParams = blockBox.getBlockParams();
				final Columns oldColumns = colParams.columns;
				colParams.columns = new Columns(colParams.columns.count, colParams.columns.width, colParams.columns.gap,
						colParams.columns.rule, Columns.FILL_BALANCE);
				this.endContainer();
				builder.endFlowBlock();
				this.startContainer();
				colParams.columns = oldColumns;
				break;
			} else {
				this.endContainer();
				builder.endFlowBlock();
				this.startContainer();
			}
		}
		this.columnSpanStack.add(flows);
		this.columnSpanStack.add(pos);
	}

	private void endColumnSpan(FlowPos pos) {
		if (this.columnSpanStack.isEmpty() || this.columnSpanStack.get(this.columnSpanStack.size() - 1) != pos) {
			return;
		}
		final Builder builder = this.containerBuilder().builder;
		this.columnSpanStack.remove(this.columnSpanStack.size() - 1);
		final List<?> flows = (List<?>) this.columnSpanStack.remove(this.columnSpanStack.size() - 1);
		for (int i = flows.size() - 1; i >= 0; --i) {
			FlowBlockBox flowBox = (FlowBlockBox) flows.get(i);
			// {@code startColumnSpan}が開き直さなくなったので、ここで
			// 閉じ直すものも無い(対で外した。理由は同関数のコメント)
			this.endContainer();
			if (flowBox.getColumnCount() > 1) {
				flowBox = new MulticolumnBlockBox(flowBox.getBlockParams(), flowBox.getFlowPos());
			} else {
				flowBox = new FlowBlockBox(flowBox.getBlockParams(), flowBox.getFlowPos());
			}
			builder.startFlowBlock(flowBox);
			this.startContainer();
		}
	}

	@Override
	public void startContainer() {
		final ContainerBuilderEntry cbe = this.containerBuilder();
		cbe.getStyledTextUnitizer().startContainer();
	}

	@Override
	public void endContainer() {
		final ContainerBuilderEntry cbe = this.containerBuilder();
		cbe.getStyledTextUnitizer().endContainer();
	}

	public void startBox(final INonReplacedBox box) {
		if (DEBUG) {
			System.err.println("startBox: " + box.getParams().element);
		}
		this.requirePage();
		// Grid直下の子はitem(固定トラック幅の合成ボックス)へ包んでから
		// 既存switchへ流す(Grid G1b)。ブロックレベル(FLOW/TABLE)は
		// element item、インラインは匿名itemへ。float/absoluteはG1では
		// item化せず宿主文脈のまま(記録して先送り——CSS的にはfloatは
		// grid itemだが、固定トラックのG1では位置決めが未定義)
		switch (box.getPos().getType()) {
		case FLOW:
		case TABLE:
			this.startGridElementItem(gridItemSpecOf(box), gridItemMinContributionCap(box));
			break;
		case INLINE:
			// 直接インラインの匿名item化はGrid/Flex共通(coordinator一般化)
			this.requireCoordinatorAnonymousItem();
			break;
		default:
			break;
		}
		// Flex直下の子はitem化してから既存switchへ流す(Flex F1d)。
		// plainなブロックはtakeover(authoredのparams/posをFlexItemBoxへ
		// 引き継ぎ、元の外箱は構築しない——答申の最重要プロトタイプ条件)。
		// 非plain(表・入れ子コンテナ・縦書き)は中立wrapper、インラインは
		// 匿名itemへ。float/absoluteはGrid同様に宿主文脈のまま
		if (this.coordinatorAwaitingDirectChild() instanceof FlexBuilder flexHost) {
			switch (box.getPos().getType()) {
			case FLOW:
				if (box.getClass() == FlowBlockBox.class
						&& ((FlowBlockBox) box).getBlockParams().flow == flexHost.getFlexBox()
								.getFlexParams().flow) {
					final FlowBlockBox sourceBox = (FlowBlockBox) box;
					this.closeInlines(sourceBox.getBlockParams());
					this.closeAnonymousItem(flexHost);
					this.endContainer();
					this.startContainerBuilder(flexHost.startElementItem(sourceBox, flexItemSpecOf(box)));
					this.startContainer();
					this.boxStack.add(box);
					return;
				}
				this.startFlexNeutralElementItem(flexItemSpecOf(box),
						box instanceof net.zamasoft.foliojet.layout.box.AbstractContainerBox acb
								? FlexBuilder.NeutralTransfer.of(acb.getBlockParams())
								: null);
				break;
			case TABLE:
				// 表の寸法解決は表側の機構が担うため引き取らない
				this.startFlexNeutralElementItem(flexItemSpecOf(box), null);
				break;
			default:
				break;
			}
		}
		switch (box.getPos().getType()) {
		case TABLE: {
			// テーブル
			final TableBox tableBox = (TableBox) box;
			final TableParams tableParams = tableBox.getTableParams();
			final Builder builder = this.containerBuilder().builder;
			switch (tableBox.getBlockBox().getPos().getType()) {
			case FLOW:
				this.closeInlines(tableParams);
				this.endContainer();
				this.startContainer();
				break;
			}
			// ビルダー選択(fixed/auto)と開始処理は
			// TableBuilderLifecycle(旧TableLayout、C4準備の継ぎ目、2026-07-19。2026-07-21命名訂正)へ委譲。挙動は不変。
			final TableBuilder tableBuilder = net.zamasoft.foliojet.layout.builder.impl.TableBuilderLifecycle.start(builder,
					tableBox);
			this.builderStack.add(tableBuilder);
		}
			break;

		case TABLE_CELL:
		case TABLE_CAPTION: {
			// テーブルセル
			// キャプション
			final TableBuilder tableBuilder = this.tableBuilder();
			tableBuilder.prepareEnterCell(this);
			final AbstractContainerBox containerBox = (AbstractContainerBox) box;
			final Builder newBuilder = tableBuilder.newContext(containerBox);
			this.startContainerBuilder(newBuilder);
			this.startContainer();
		}
			break;

		case TABLE_COLUMN:
		case TABLE_ROW_GROUP:
		case TABLE_ROW: {
			// テーブルカラムグループ
			// テーブルカラム
			// テーブル行グループ
			// テーブル行
			final TableBuilder tableBuilder = this.tableBuilder();
			tableBuilder.prepareEnterTrack(this);
			final AbstractInnerTableBox innerTableBox = (AbstractInnerTableBox) box;
			tableBuilder.startInnerTable(innerTableBox);
			tableBuilder.afterEnterTrack(this);
		}
			break;

		case INLINE: {
			// インライン
			if (box.getType() == BoxType.INLINE) {
				final InlineBox inlineBox = (InlineBox) box;
				this.containerBuilder().getStyledTextUnitizer().startInline(inlineBox);
			} else {
				// インラインブロック
				final InlineBlockBox inlineBlockBox = (InlineBlockBox) box;
				final Builder builder = this.containerBuilder().builder;
				final Builder newBuilder = builder.newBuilder(inlineBlockBox);
				this.startContainerBuilder(newBuilder);
				this.startContainer();
			}
		}
			break;

		case FLOW: {
			// 通常のフローのボックス
			final FlowBlockBox blockBox = (FlowBlockBox) box;
			final BlockParams params = blockBox.getBlockParams();

			// ぶちぬき
			final FlowPos pos = blockBox.getFlowPos();
			// **インラインを閉じるのが先**(2026-07-28)。開いているインラインは
			// ぶち抜き前の文脈で開かれたものなので、その文脈で閉じなければ
			// ならない。{@code startColumnSpan}は段組を抜けるために
			// {@code endFlowBlock}まで戻す——つまり{@code containerBuilder}が
			// 差し替わり、{@code closeInlines}が出す{@code endInline}は
			// **対応する{@code startInline}を見ていない新しい
			// StyledTextUnitizer**へ届く。そのInlineParamsStackは根しか
			// 積んでいないので、popが根を外して
			// {@code InlineParamsStack.current}が空リストを引く
			// (WPTのcolumn-span:all文書10件がここで落ちていた:
			// css-multicol/multicol-span-all-019 等)
			this.closeInlines(params);
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.startColumnSpan(pos);
			}
			this.endContainer();
			final Builder builder = this.containerBuilder().builder;
			if (params.flow.isVertical() == builder.getRootBox().getBlockParams().flow.isVertical()
					&& !blockBox.isFixedMulticolumn()) {
				builder.startFlowBlock(blockBox);
				// Grid本体の開始: 適格なら構築coordinatorを積み、以後の
				// 直接子をitem化する(Grid G1b)。不適格ならBlockBox同然の
				// フォールバック(G0)のまま
				if (blockBox instanceof GridBox gridBox && GridBuilderLifecycle.eligible(gridBox, builder)) {
					this.builderStack.add(GridBuilderLifecycle.start(builder, gridBox));
				}
				// Flex本体の開始(Flex F1d——Gridと同じ形。不適格はF0の
				// 単一列フローのまま)
				if (blockBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox flexBox
						&& FlexBuilderLifecycle.eligible(flexBox, builder)) {
					this.builderStack.add(FlexBuilderLifecycle.start(builder, flexBox));
				}
			} else {
				// ページ進行方向が違う場合
				final Builder newBuilder = builder.newBuilder(blockBox);
				this.startContainerBuilder(newBuilder);
				// F6: 直交フローのFlexコンテナにもcoordinatorを付ける
				// (容れ物はnewBuilder——TwoPassならFlexEventとして録画され、
				// shrinkToFit後のbindでrow/column配置される)
				if (blockBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox orthoFlex
						&& FlexBuilderLifecycle.eligible(orthoFlex, newBuilder)) {
					this.builderStack.add(FlexBuilderLifecycle.start(newBuilder, orthoFlex));
				}
			}
			this.startContainer();
		}
			break;

		case FLOAT:
			// 浮動体
			this.containerBuilder().getStyledTextUnitizer().flushText();
		case ABSOLUTE: {
			// 絶対位置指定
			final AbstractBlockBox stfBox = (AbstractBlockBox) box;
			final Builder builder = this.contextBuilder().builder;
			if (box.getPos().getType() == PosType.ABSOLUTE) {
				final AbsolutePos pos = (AbsolutePos) stfBox.getPos();
				if (pos.autoPosition == AutoPosition.INLINE) {
					this.containerBuilder().getStyledTextUnitizer().flushText();
					this.containerBuilder().getStyledTextUnitizer().requireTextShaper();
				}
			}
			final Builder newBuilder = builder.newBuilder(stfBox);
			this.startContainerBuilder(newBuilder);
			this.startContainer();
		}
			break;
		default:
			throw new IllegalStateException();
		}

		this.boxStack.add(box);
	}

	public void endBox() {
		final IBox box = (IBox) this.boxStack.remove(this.boxStack.size() - 1);
		if (DEBUG) {
			System.err.println("endBox: " + box.getParams().element);
		}
		switch (box.getPos().getType()) {
		case TABLE: {
			// テーブル
			final TableBuilder tableBuilder = this.endTableBuilder();
			final TableBox tableBox = tableBuilder.getTableBox();
			final TableParams tableParams = tableBox.getTableParams();
			switch (tableBox.getBlockBox().getPos().getType()) {
			case FLOW:
				this.closeInlines(tableBox.getBlockBox().getParams());
				this.endContainer();
				break;
			case FLOAT:
				this.containerBuilder().getStyledTextUnitizer().flushText();
				break;
			}
			final Builder builder = this.containerBuilder().builder;
			// 終了処理もTableBuilderLifecycleへ委譲(開始側のルーティング結果と一致させるため、
			// 条件を再計算せずtableBuilder自身に問うのは従来どおり)。挙動は不変。
			net.zamasoft.foliojet.layout.builder.impl.TableBuilderLifecycle.finish(tableBuilder, builder);
			switch (tableBox.getBlockBox().getPos().getType()) {
			case FLOW:
				this.startContainer();
				this.restoreInlines(tableParams);
				break;
			case INLINE:
				this.containerBuilder().getStyledTextUnitizer().addInlineBlock((InlineBlockBox) tableBox.getBlockBox());
				break;
			case ABSOLUTE:
				final AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) tableBox.getBlockBox();
				if (absoluteBox.getAbsolutePos().autoPosition == AutoPosition.INLINE) {
					this.containerBuilder().getStyledTextUnitizer().addInlineAbsolute(absoluteBox);
				}
				break;
			}
		}
			break;
		case TABLE_CELL:
		case TABLE_CAPTION: {
			// テーブルセル
			// キャプション
			this.endContainer();
			final ContainerBuilderEntry entry = this.endContainerBuilder();
			if (box.getPos().getType() == net.zamasoft.foliojet.layout.box.params.PosType.TABLE_CAPTION
					&& entry.builder instanceof TwoPassBlockBuilder sealable) {
				// caption recipe化C3(2026-08-01、consult-codex-2026-08-01-
				// caption-recipe.txt): キャプション本文の録画完了点での
				// range seal(float/inline-blockのclose時sealと同型)。
				// C1のrecipe記録化でendOf(anchor)が引けるようになり、body
				// レンジ[anchor+1, endId-1]は箱自身のStartを含まないため
				// 単独CAPTION再生(G-1)の形にはならない。caption builder
				// 自身はtop/bottomCaptionsに保持され、後で通常どおり
				// bind(anonBuilder)される——そのbindがrange駆動になる
				sealable.sealBodyForRangeBind();
			} else {
				// E-6増分5a(2026-07-24): セルの録画完了点でのrange seal
				// (Retained実装のみ。適格ならCellContentがrecords解放+
				// range+lease保持へ切り替わる)
				this.tableBuilder().sealCellContext(entry.builder);
			}
			assert this.builderStack.size() != 1;
		}
			break;
		case TABLE_COLUMN:
		case TABLE_ROW_GROUP:
		case TABLE_ROW: {
			// テーブル列グループ
			// テーブル列
			// テーブル行グループ
			// テーブル行
			this.tableBuilder().endInnerTable();
		}
			break;

		case INLINE: {
			if (box.getType() == BoxType.INLINE) {
				// インライン
				this.containerBuilder().getStyledTextUnitizer().endInline();
			} else {
				// インラインブロック
				this.endContainer();
				final ContainerBuilderEntry entry = this.endContainerBuilder();
				if (entry.builder instanceof TwoPassBlockBuilder sealable) {
					// E-6増分4a/4b: 録画完了点でのrange seal(適格ならrecords解放)
					sealable.sealBodyForRangeBind();
				}
				final InlineBlockBox inlineBlockBox = (InlineBlockBox) entry.builder.getRootBox();
				final Builder parentBuilder = this.containerBuilder().builder;
				if (!parentBuilder.isTwoPass() && entry.builder.isTwoPass()) {
					// インラインブロックボックスの幅が明示されてなかった場合
					final TwoPassBlockBuilder stfBuilder = (TwoPassBlockBuilder) entry.builder;
					inlineBlockBox.shrinkToFit(parentBuilder, stfBuilder.intrinsicSizesMeasured(), false);
					final BlockBuilder inlineBlockBuilder = new BlockBuilder(this.pageContextBuilder(), inlineBlockBox);
					stfBuilder.bind(inlineBlockBuilder);
					inlineBlockBuilder.close();
				}
				this.containerBuilder().getStyledTextUnitizer().addInlineBlock(inlineBlockBox);
			}
		}
			break;

		case FLOW: {
			// Flexのtakeover element item終端(Flex F1d): authored boxは
			// 構築されていないため、通常のendFlowBlockではなくitemを畳む
			final FlexBuilder flexItemHost = this.flexItemEndingAt(box);
			if (flexItemHost != null) {
				this.endContainer();
				this.endContainerBuilder();
				flexItemHost.itemClosed();
				this.startContainer();
				this.restoreInlines(box.getParams());
				break;
			}
			// coordinator終端(Grid G1b/Flex F1d共通): 匿名itemを畳み、
			// coordinatorを外して配置を確定する。finish()はhostのactive
			// flowがまだ当のコンテナである間——下のendFlowBlockより前——に
			// 呼ぶ(itemの配置と親カーソル同期はそのflowに対して行うため)
			final net.zamasoft.foliojet.layout.builder.ItemCoordinator ending = this.coordinatorEndingAt(box);
			if (ending != null) {
				this.closeAnonymousItem(ending);
				final Object popped = this.builderStack.remove(this.builderStack.size() - 1);
				assert popped == ending : "coordinator終端でbuilderStack末尾が一致しません: " + popped;
				ending.finish();
			}
			// 通常のフロー
			this.endContainer();
			final FlowBlockBox blockBox = (FlowBlockBox) box;
			final Builder builder = this.containerBuilder().builder;
			if (builder.getRootBox() != box) {
				builder.endFlowBlock();
				this.startContainer();
			} else {
				final ContainerBuilderEntry entry = this.endContainerBuilder();
				final Builder parentBuilder = this.containerBuilder().builder;
				if (!parentBuilder.isTwoPass()) {
					if (entry.builder.isTwoPass()) {
						// ビルド
						final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
						blockBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false);
						final BlockBuilder bindBuilder = new BlockBuilder(this.pageContextBuilder(), blockBox);
						contentBuilder.bind(bindBuilder);
						bindBuilder.close();
					}
					parentBuilder.addBound(blockBox);
				} else if (entry.builder.isTwoPass()) {
					// 親も計測中なら、再生イベントの記録だけでなく子のouter
					// contributionを親の固有寸法へ渡す。特に直交フローでは
					// 子ページ軸→親行軸の変換が必要になる。
					((TwoPassBlockBuilder) parentBuilder).fitBlock((TwoPassBlockBuilder) entry.builder);
				}
				this.startContainer();
			}

			final FlowPos pos = blockBox.getFlowPos();
			// ぶち抜き復帰
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.endColumnSpan(pos);
			}
			// **インラインの復元は最後**(2026-07-28)。startBoxで
			// closeInlines→startColumnSpanの順にしたので、その鏡像として
			// endColumnSpan→restoreInlinesの順でなければ入れ子が交差する
			this.restoreInlines(box.getParams());
		}
			break;

		case FLOAT: {
			// 浮動体
			this.endContainer();
			final ContainerBuilderEntry entry = this.endContainerBuilder();
			if (!this.scratchMeasurement && entry.builder instanceof TwoPassBlockBuilder sealable) {
				// E-6増分4a/4b: 録画完了点でのrange seal(適格ならrecords解放)
				sealable.sealBodyForRangeBind();
			}
			final Builder parentBuilder = this.containerBuilder().builder;
			if (box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.PageFloatPos pageFloatPos) {
				// ページフロート(2026-08-02): 脚注と同じ経路で本文から
				// 分離し、ページ台帳へ渡す。台帳が無い文脈(scratch計測・
				// 再生)ではどこにも置かれない=測定等価
				final FloatBlockBox pageFloatBox = (FloatBlockBox) entry.builder.getRootBox();
				if (parentBuilder.isTwoPass() || this.scratchMeasurement) {
					// TwoPass本文では専用recordへ保留し、bind時に一度だけページ台帳へ
					// 渡す。scratchではページ外要素なので計測へ寄与せず破棄する。
					break;
				}
				if (entry.builder.isTwoPass()) {
					final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
					pageFloatBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false);
					final BlockBuilder pageFloatBuilder = new BlockBuilder(this.pageContextBuilder(), pageFloatBox);
					// 浮動体・脚注と同じ理由で、使い捨て計測の最中は消費しない
					contentBuilder.bind(pageFloatBuilder, this.scratchMeasurement);
					pageFloatBuilder.close();
				}
				if (this.pageContextBuilder() instanceof RootBuilder root) {
					root.addPageFloat(pageFloatBox, pageFloatPos.top);
				}
				break;
			}
			if (box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.FootnotePos) {
				// 脚注F2/F3(2026-07-31、consult-codex-2026-07-31-footnote.txt
				// §3): 本文は親のflowへ入れない(呼び出し位置にはF1の
				// ::footnote-callだけが残る)。組み上がった本文ボックスを
				// ページ脚注台帳(RootBuilder)へ渡し、ページ下端領域に
				// 描かれる。scratch計測・再生等でRootBuilderが根に無い
				// 文脈では台帳が無い=どこにも置かれない(本文はflow外
				// なので測定等価。two-passのseal→bindは通常どおり対に
				// なりリースは孤児化しない)
				final FloatBlockBox noteBox = (FloatBlockBox) entry.builder.getRootBox();
				if (parentBuilder.isTwoPass() || this.scratchMeasurement) {
					// PageFloatPosと同じく、親の実レイアウトまで分離配置を保留する。
					break;
				}
				if (entry.builder.isTwoPass()) {
					final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
					noteBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false);
					final BlockBuilder noteBuilder = new BlockBuilder(this.pageContextBuilder(), noteBox);
					// 浮動体と同じ理由で、使い捨て計測の最中は消費しない
					contentBuilder.bind(noteBuilder, this.scratchMeasurement);
					noteBuilder.close();
				}
				if (this.pageContextBuilder() instanceof RootBuilder root) {
					root.addFootnote(noteBox);
				}
				break;
			}
			if (!parentBuilder.isTwoPass()) {
				final BlockBuilder boundBuilder = (BlockBuilder) parentBuilder;
				final FloatBlockBox floatBox = (FloatBlockBox) entry.builder.getRootBox();
				if (entry.builder.isTwoPass()) {
					// ビルド
					final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
					floatBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false);
					final BlockBuilder floatBuilder = new BlockBuilder(this.pageContextBuilder(), floatBox);
					// **使い捨て計測の最中は本文を消費しない**(2026-08-03)。
					// ここで本番のbindをすると使用権が閉じ、あとの本番では
					// 空になる(内容消失。TwoPassBlockBuilder.bindのjavadoc参照)
					contentBuilder.bind(floatBuilder, this.scratchMeasurement);
					floatBuilder.close();
				}
				final FloatPos pos = (FloatPos) box.getPos();
				final boolean pageBreak = (this.pageMode == 0 && ((pos.pageBreakBefore != PageBreakMode.AUTO
						&& pos.pageBreakBefore != PageBreakMode.AVOID)
						|| (pos.pageBreakAfter != PageBreakMode.AUTO
								&& pos.pageBreakAfter != PageBreakMode.AVOID)));
				if (pageBreak) {
					this.closeInlines(box.getParams());
					this.endContainer();
				}
				boundBuilder.addBound(floatBox);
				if (pageBreak) {
					this.startContainer();
					this.restoreInlines(box.getParams());
				}
			} else if (entry.builder.isTwoPass()) {
				// STFコンテキスト内
				TwoPassBlockBuilder stfBuilder = (TwoPassBlockBuilder) parentBuilder;
				TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
				stfBuilder.fitFloating(contentBuilder);
			}
		}
			break;

		case ABSOLUTE: {
			// 絶対位置指定
			this.endContainer();
			ContainerBuilderEntry entry = this.endContainerBuilder();
			if (this.scratchMeasurement) {
				// 使い捨て計測(表Pass B)駆動: seal・prepareBind・係留を
				// スキップし、子builderをreplicaごと破棄する(sealすると
				// 本物のリースを取得したまま破棄されリース孤児化する。
				// 絶対配置はflow外で計測値に寄与しないため計測等価——
				// absolute吸収=codex増分9、2026-07-30)
				break;
			}
			if (entry.builder instanceof TwoPassBlockBuilder sealable) {
				// E-6増分4a/4b: 録画完了点でのrange seal。E-6増分4eの
				// recipe記録化により絶対配置も適格になる(旧NO_RANGE=81の解消)。
				// 適格な本文は下のprepareBindでDeferredBindへ持ち出される
				sealable.sealBodyForRangeBind();
			}
			Builder builder = this.contextBuilder().builder;
			if (!builder.isTwoPass()) {
				BlockBuilder boundBuilder = (BlockBuilder) builder;
				AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) entry.builder.getRootBox();
				if (entry.builder.isTwoPass()) {
					// ビルド
					TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
					if (absoluteBox.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
						// position: fixed; の場合、ここで構築
						IFramedBox containerBox = this.pageContextBuilder().getRootBox();
						absoluteBox.shrinkToFit(containerBox, contentBuilder.intrinsicSizesMeasured());
						BlockBuilder absoluteBuilder = new BlockBuilder(this.pageContextBuilder(), absoluteBox);
						contentBuilder.bind(absoluteBuilder);
						absoluteBuilder.close();
					} else {
						// position: absolute; は後で構築
						absoluteBox.prepareBind(contentBuilder);
					}
				}
				switch (absoluteBox.getAbsolutePos().autoPosition) {
				case AutoPosition.BLOCK:
					boundBuilder.addBound(absoluteBox);
					break;
				case AutoPosition.INLINE:
					this.containerBuilder().getStyledTextUnitizer().addInlineAbsolute(absoluteBox);
					break;
				default:
					throw new IllegalStateException();
				}
			}
		}
			break;

		default:
			throw new IllegalStateException();
		}

		// coordinator直下のelement itemは子box一つで完結する——子のendBox
		// 直後に畳む(Grid G1b/Flex F1d共通。入れ子の子は末尾boxが当の
		// コンテナでないため反応しない。Flexのtakeover itemはendBoxの
		// FLOW分岐で畳み済み)
		final net.zamasoft.foliojet.layout.builder.ItemCoordinator tail = this.coordinatorAwaitingDirectChild();
		if (tail != null && tail.hasOpenElementItem()) {
			this.endCoordinatorElementItem(tail);
		}
	}

	public void addReplacedBox(AbstractReplacedBox replacedBox) {
		this.requirePage();

		// Grid直下の置換要素はitem化する(Grid G1b): ブロックレベルは
		// one-shotのelement item、インラインは匿名itemへ
		net.zamasoft.foliojet.layout.builder.ItemCoordinator oneShot = null;
		switch (replacedBox.getPos().getType()) {
		case FLOW:
			oneShot = this.startGridElementItem(gridItemSpecOf(replacedBox));
			if (oneShot == null) {
				// 行方向の寸法指定はwrapperへ引き取る(FlexBuilder.NeutralTransfer
				// 参照——%幅のsvg等は二パス計測で0になるため、wrapperが引き取ら
				// ないとflex base sizeが0へ潰れる)。寸法解決自体は従来どおり
				// 置換側の機構(calculateReplacedSize)が担う
				oneShot = this.startFlexNeutralElementItem(flexItemSpecOf(replacedBox),
						FlexBuilder.NeutralTransfer.of(replacedBox.getReplacedParams()));
			}
			break;
		case INLINE:
			this.requireCoordinatorAnonymousItem();
			break;
		default:
			break;
		}

		switch (replacedBox.getPos().getType()) {
		case FLOW: {
			// 通常のフロー
			// ぶちぬき
			final FlowPos pos = ((FlowReplacedBox) replacedBox).getFlowPos();
			// インラインを閉じるのが先(理由はstartBoxのFLOWと同じ)。
			// builderの取得は**startColumnSpanの後**——段組を抜けると
			// containerBuilderが差し替わるので、addBound先は新しい方
			this.closeInlines(replacedBox.getParams());
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.startColumnSpan(pos);
			}
			final Builder builder = this.containerBuilder().builder;
			this.endContainer();
			builder.addBound(replacedBox);
			this.startContainer();

			// ぶち抜き復帰
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.endColumnSpan(pos);
			}
			this.restoreInlines(replacedBox.getParams());
		}
			break;

		case FLOAT: {
			// 浮動体
			final Builder context = this.containerBuilder().builder;
			final FloatPos pos = (FloatPos) replacedBox.getPos();
			boolean pageBreak = (this.pageMode == 0 && ((pos.pageBreakBefore != PageBreakMode.AUTO
					&& pos.pageBreakBefore != PageBreakMode.AVOID)
					|| (pos.pageBreakAfter != PageBreakMode.AUTO && pos.pageBreakAfter != PageBreakMode.AVOID)));
			if (pageBreak) {
				this.closeInlines(replacedBox.getParams());
				this.endContainer();
			} else {
				this.containerBuilder().getStyledTextUnitizer().flushText();
			}
			context.addBound(replacedBox);
			if (pageBreak) {
				this.startContainer();
				this.restoreInlines(replacedBox.getParams());
			}
		}
			break;
		case ABSOLUTE: {
			// 絶対位置指定
			final Builder context = this.containerBuilder().builder;
			final IAbsoluteBox absoluteBox = (IAbsoluteBox) replacedBox;
			switch (absoluteBox.getAbsolutePos().autoPosition) {
			case AutoPosition.BLOCK:
				context.addBound(replacedBox);
				break;
			case AutoPosition.INLINE:
				this.containerBuilder().getStyledTextUnitizer().addInlineAbsolute(absoluteBox);
				break;
			default:
				throw new IllegalStateException();
			}
		}
			break;

		case INLINE: {
			// インライン
			this.containerBuilder().getStyledTextUnitizer().addInlineReplaced(replacedBox);
		}
			break;

		default:
			throw new IllegalStateException();
		}

		if (oneShot != null) {
			this.endCoordinatorElementItem(oneShot);
		}
	}

	public void characters(int charOffset, char[] ch, int off, int len, boolean lineFeed) {
		if (this.normalizeText) {
			String s = new String(ch, off, len);
			s = Normalizer.normalize(s, Form.NFC);
			ch = s.toCharArray();
			off = 0;
			len = s.length();
		}
		
		if (DEBUG) {
			System.err.println(charOffset + "/" + new String(ch, off, len));
		}
		this.requirePage();
		// Grid直下の直接テキストは匿名itemへ(Grid G1b)
		this.requireCoordinatorAnonymousItem();
		this.containerBuilder().getStyledTextUnitizer().characters(charOffset, ch, off, len, lineFeed);
	}

	/**
	 * {@code leader()}を現在のインライン文脈へ流します(leader() L1——
	 * consult-codex-2026-07-31-leader.txt)。shape・幅の割り付けは
	 * {@code StyledTextUnitizer.leader}以降が駆動のたびに行う。
	 */
	public void addLeader(final String pattern) {
		this.requirePage();
		this.requireCoordinatorAnonymousItem();
		this.containerBuilder().getStyledTextUnitizer().leader(pattern);
	}

	public void end() {
		this.requirePage();
		this.endContainer();
		this.endContainerBuilder();
		assert this.builderStack.isEmpty();
	}
}
