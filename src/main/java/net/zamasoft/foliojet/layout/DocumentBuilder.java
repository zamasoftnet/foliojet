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

import net.zamasoft.foliojet.layout.box.BoxSubtype;
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
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.RubyBox;
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

		protected StyledTextUnitizer styledTextUnitizer = null;

		public ContainerBuilderEntry(Builder builder) {
			this.builder = builder;
		}

		/**
		 * テキスト出力のためのインタフェースを返します。
		 * 
		 * @return
		 */
		public StyledTextUnitizer getStyledTextUnitizer() {
			if (this.styledTextUnitizer == null) {
				this.styledTextUnitizer = new StyledTextUnitizer(this.builder);
			}
			return this.styledTextUnitizer;
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

	public DocumentBuilder(PageGenerator pageGenerator) {
		this.pageGenerator = pageGenerator;
		this.normalizeText = UAProps.INPUT_NORMALIZE_TEXT.getBoolean(pageGenerator.getUserAgent());
	}

	/**
	 * 改ページ残余のソース再生用に、既存のルートビルダーへ向けた
	 * ドキュメントビルダーを作ります(M6b v3)。ライブの DocumentBuilder
	 * の unitizer・コンテナ状態には一切触れず、新品の状態で記録済み
	 * プロトコルを再駆動するためのものです。
	 */
	public DocumentBuilder(PageGenerator pageGenerator, BlockBuilder existingRoot) {
		this(pageGenerator);
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
		while (o instanceof TableBuilder) {
			// テーブル内でセル外のinline, block, テキスト等をテーブルの前に置くため
			// 一般的なブラウザの動作による
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
	 * 挙動は不変だが、rootlessなソース再生(バランスプローブの候補構築
	 * ——根は{@code BalanceProbeBuilder})では、段組内floatやインライン
	 * ブロックのtwo-pass bindがここを通るため、旧castは
	 * {@code ClassCastException}になっていた(段組内float解禁で実際に
	 * 踏む経路になった)。呼び出し側はいずれも{@code LayoutStack}/
	 * {@code getRootBox()}としてしか使わない。
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
		assert top instanceof TableBuilder : //
		"表構造の外(先行する TABLE 開始イベントなし)で TABLE_CELL/TABLE_ROW 系ボックスを"
				+ "構築しようとしました。単独ソース再生の対象になっていないか確認してください: top=" + top;
		return (TableBuilder) top;
	}

	private TableBuilder endTableBuilder() {
		assert !this.builderStack.isEmpty() && this.builderStack
				.get(this.builderStack.size() - 1) instanceof TableBuilder : //
		"閉じるべき TableBuilder が builderStack の末尾にありません: " + this.builderStack;
		TableBuilder builder = (TableBuilder) this.builderStack.remove(this.builderStack.size() - 1);
		return builder;
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
				this.restoreInlines(blockBox.getParams());
				colParams.columns = oldColumns;
				break;
			} else {
				this.endContainer();
				builder.endFlowBlock();
				this.startContainer();
				this.restoreInlines(blockBox.getParams());
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
			this.closeInlines(flowBox.getBlockParams());
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

			if (!this.boxStack.isEmpty()) {
				final IBox parentBox = (IBox) this.boxStack.get(this.boxStack.size() - 1);
				if (parentBox.getSubtype() == BoxSubtype.RUBY && blockBox.getSubtype() == BoxSubtype.RUBY_BODY
						&& this.containerBuilder().builder.isTwoPass()) {
					TwoPassBlockBuilder builder = (TwoPassBlockBuilder) this.containerBuilder().builder;
					if (!builder.isEmpty()) {
						RubyBox rubyBox = (RubyBox) parentBox;
						this.endBox();
						rubyBox = new RubyBox(rubyBox.getBlockParams(), rubyBox.getInlinePos());
						this.startBox(rubyBox);
					}
				}
			}

			// ぶちぬき
			final FlowPos pos = blockBox.getFlowPos();
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.startColumnSpan(pos);
			}

			this.closeInlines(params);
			this.endContainer();
			final Builder builder = this.containerBuilder().builder;
			if (params.flow.isVertical() == builder.getRootBox().getBlockParams().flow.isVertical()
					&& !blockBox.isFixedMulticolumn()) {
				builder.startFlowBlock(blockBox);
			} else {
				// ページ進行方向が違う場合
				final Builder newBuilder = builder.newBuilder(blockBox);
				this.startContainerBuilder(newBuilder);
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
			this.endContainerBuilder();
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
			// 通常のフロー
			this.endContainer();
			final FlowBlockBox blockBox = (FlowBlockBox) box;
			final Builder builder = this.containerBuilder().builder;
			if (builder.getRootBox() != box) {
				builder.endFlowBlock();
				this.startContainer();
				this.restoreInlines(box.getParams());
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
				}
				this.startContainer();
				this.restoreInlines(box.getParams());
			}

			final FlowPos pos = blockBox.getFlowPos();
			// ぶち抜き復帰
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.endColumnSpan(pos);
			}
		}
			break;

		case FLOAT: {
			// 浮動体
			this.endContainer();
			final ContainerBuilderEntry entry = this.endContainerBuilder();
			if (entry.builder instanceof TwoPassBlockBuilder sealable) {
				// E-6増分4a/4b: 録画完了点でのrange seal(適格ならrecords解放)
				sealable.sealBodyForRangeBind();
			}
			final Builder parentBuilder = this.containerBuilder().builder;
			if (!parentBuilder.isTwoPass()) {
				final BlockBuilder boundBuilder = (BlockBuilder) parentBuilder;
				final FloatBlockBox floatBox = (FloatBlockBox) entry.builder.getRootBox();
				if (entry.builder.isTwoPass()) {
					// ビルド
					final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
					floatBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false);
					final BlockBuilder floatBuilder = new BlockBuilder(this.pageContextBuilder(), floatBox);
					contentBuilder.bind(floatBuilder);
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
	}

	public void addReplacedBox(AbstractReplacedBox replacedBox) {
		this.requirePage();

		switch (replacedBox.getPos().getType()) {
		case FLOW: {
			// 通常のフロー
			// ぶちぬき
			final FlowPos pos = ((FlowReplacedBox) replacedBox).getFlowPos();
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.startColumnSpan(pos);
			}

			final Builder builder = this.containerBuilder().builder;
			this.closeInlines(replacedBox.getParams());
			this.endContainer();
			builder.addBound(replacedBox);
			this.startContainer();
			this.restoreInlines(replacedBox.getParams());

			// ぶち抜き復帰
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.endColumnSpan(pos);
			}
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
		this.containerBuilder().getStyledTextUnitizer().characters(charOffset, ch, off, len, lineFeed);
	}

	public void end() {
		this.requirePage();
		this.endContainer();
		this.endContainerBuilder();
		assert this.builderStack.isEmpty();
	}
}
