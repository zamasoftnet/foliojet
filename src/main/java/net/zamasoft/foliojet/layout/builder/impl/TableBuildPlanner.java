package net.zamasoft.foliojet.layout.builder.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.AbstractBlockLevelPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * 表の実行計画(Incremental/Retained)を単一の判定点で決定します(C4-B、
 * 2026-07-19)。旧{@code LayoutUtils.needsIntrinsicSizing(TableBox)}の
 * boolean一本化(auto列幅・非FLOW配置・ページ軸寸法指定・行軸auto寸法の
 * 4条件を1つのbooleanへ潰していた)を、理由ごとに追跡できる型へ置き換える。
 * 判定条件そのものは旧実装から変更していない(挙動不変)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class TableBuildPlanner {
	private TableBuildPlanner() {
	}

	/** Pass B 後にだけ判定できる、行送出を従来の assemble へ戻す理由です。 */
	public enum RowEmissionExclusion {
		/** processing.table-row-emission は既定falseのopt-inです。 */
		DISABLED,
		/** MAIN 以外では計測・再配置とページ副作用の順序を変えられません。 */
		NOT_MAIN,
		/** Pass C の行高適用が安定しない表には、固定した h[] を使えません。 */
		PASS_C_INELIGIBLE,
		/** 通常フローの MODE_PAGE_BREAK・breakDepth=-1・非再配置の受理宿主が必要です。 */
		UNSUPPORTED_HOST,
		/** 縦組み・親との軸違いの切断とフレーム会計は第1段では未検証です。 */
		WRITING_MODE,
		/** 複数本文グループは前グループへ戻る avoid とグループ減算順が異なります。 */
		BODY_GROUPS,
		/** rowspan は bind 単位と改頁禁止単位が異なり、移送セルの切断も必要です。 */
		ROWSPAN,
		/** 反復フッタは全断片に終端フレームを予約します。 */
		FOOTER,
		/**
		 * 上部captionは除外を維持します。非ゼロ始点での親の超過判定と局所切断線の
		 * 丸め差により、未完側だけ可視グループKEEP→非先頭の表全体MOVEとなる反例があります。
		 * 原因はcaption固有ではなく、先行内容だけでも起こるため、送出全般で可視範囲を保留します。
		 * 下部だけならcomplete・retained.close後の共通経路で配置し、最後にラッパーを閉じます。
		 */
		CAPTION,
		/** collapse の全行境界配列の断片所有をまだ分離していません。 */
		COLLAPSED_BORDERS,
		/**
		 * ABSOLUTEも含め指定高は完成経路へ戻します(既定のmin=0は除く)。
		 * 配分後のh[]の数値shadowだけでは親の断片寸法まで保証できません。
		 * グループ高と表高を併用した実fixtureで、完成表配置と未完表の寸法更新から
		 * ラッパー終端までの会計が一致せず、祖先枠高・後続本文のD7座標が変わりました。
		 */
		GROUP_PAGE_SIZE,
		/** 行内分割の保持・残余高はセル再配置で変わり、元の h[] では再現できません。 */
		ROW_SPLITTING,
		/**
		 * 直交セルは行のMOVEをグループのKEEPへ変え、ページ先頭でもKEEPを保存します。
		 * ROW_SPLITTING除外だけではこの分岐の同値を保証できません。
		 * 独立shadowと実Root・D7の合格が未確認のためB-3-1でも除外を維持します。
		 */
		ORTHOGONAL_CELL,
		/** 負の終端マージンは、最終追記より前の分割まで取り消すことがあります。 */
		NEGATIVE_END_MARGIN,
		/** 脚注・ページフロート・並列注は bind と改頁を交互にすると台帳登録時点が変わります。 */
		PAGE_SIDE_EFFECTS,
		/** 置換要素・inline-block・表等の割合寸法は、bindで現ページを参照し得ます。 */
		PAGE_DEPENDENT_CELL_CONTENT,
		/** 段組・指定高/min/max の祖先では寸法の復元と再伝播が未検証です。 */
		COMPLEX_ANCESTOR,
		/** 浮動体回避で初回配置が下がると、残り容量だけでは行内分割を除外できません。 */
		FLOATING_HOST,
		/** 完成表の強制分割は列分割処理を通らず、列高の同期だけでは再現できません。 */
		FORCED_BREAK_WITH_COLUMNS,
		/** 空表・列数0は Retained の指定寸法処理が通常の行加算と別経路です。 */
		EMPTY_TABLE
	}

	/**
	 * Pass B 後の形状と実受理宿主から集める材料です。開始時の plan() とは別契約。
	 * hasPageSideEffects はセル内を含む脚注・ページフロート・並列注の有無、
	 * complexAncestor は宿主までの全祖先の段組・指定高/min/max を表します。
	 * maySplitRows は rowspan の有無とは独立に調べる必要があります。
	 * hostSupportsIntake は受理先のモード・分割能力・深さ・再配置/テキスト状態の検査結果です。
	 */
	public record RowEmissionFacts(boolean main, boolean passCEligible, boolean hostSupportsIntake,
			boolean horizontalHost, int bodyGroupCount, int rowCount, int columnCount,
			boolean hasRowspan, boolean hasFooter, boolean hasTopCaption, boolean hasGroupPageSize,
			boolean maySplitRows, boolean hasOrthogonalCell, boolean hasPageSideEffects,
			boolean complexAncestor, boolean hasForcedBreak, boolean hasColumnTree) {
	}

	/**
	 * Pass B 後の送出適格判定。空集合のときだけ送出候補です。
	 * 表フレーム計算後・markIncomplete 前に
	 * 判定し、終端マージンを抑止する前の値を使います。
	 * キャプションは下部だけの形に限定します。上部がある形はCAPTIONで除外し、
	 * Pass B前の配置から親の切断・寸法会計まで完成経路に任せます。
	 */
	public static EnumSet<RowEmissionExclusion> rowEmissionExclusionsAfterPassB(final TableBox table,
			final RowEmissionFacts facts) {
		final EnumSet<RowEmissionExclusion> reasons = EnumSet.noneOf(RowEmissionExclusion.class);
		if (!facts.main()) {
			reasons.add(RowEmissionExclusion.NOT_MAIN);
		}
		if (!facts.passCEligible()) {
			reasons.add(RowEmissionExclusion.PASS_C_INELIGIBLE);
		}
		if (!facts.hostSupportsIntake() || table.getBlockBox().getPos().getType() != PosType.FLOW) {
			reasons.add(RowEmissionExclusion.UNSUPPORTED_HOST);
		}
		if (!facts.horizontalHost() || table.getTableParams().flow.isVertical()) {
			reasons.add(RowEmissionExclusion.WRITING_MODE);
		}
		if (facts.bodyGroupCount() != 1) {
			reasons.add(RowEmissionExclusion.BODY_GROUPS);
		}
		if (facts.hasRowspan()) {
			reasons.add(RowEmissionExclusion.ROWSPAN);
		}
		if (facts.hasFooter()) {
			reasons.add(RowEmissionExclusion.FOOTER);
		}
		if (facts.hasTopCaption()) {
			reasons.add(RowEmissionExclusion.CAPTION);
		}
		if (table.getTableParams().borderCollapse != TableParams.BORDER_SEPARATE) {
			reasons.add(RowEmissionExclusion.COLLAPSED_BORDERS);
		}
		if (facts.hasGroupPageSize()) {
			reasons.add(RowEmissionExclusion.GROUP_PAGE_SIZE);
		}
		if (facts.maySplitRows()) {
			reasons.add(RowEmissionExclusion.ROW_SPLITTING);
		}
		if (facts.hasOrthogonalCell()) {
			reasons.add(RowEmissionExclusion.ORTHOGONAL_CELL);
		}
		if (table.getFrame().margin.bottom < 0) {
			reasons.add(RowEmissionExclusion.NEGATIVE_END_MARGIN);
		}
		if (facts.hasPageSideEffects()) {
			reasons.add(RowEmissionExclusion.PAGE_SIDE_EFFECTS);
		}
		if (facts.complexAncestor()) {
			reasons.add(RowEmissionExclusion.COMPLEX_ANCESTOR);
		}
		if (facts.hasForcedBreak() && facts.hasColumnTree()) {
			reasons.add(RowEmissionExclusion.FORCED_BREAK_WITH_COLUMNS);
		}
		if (facts.rowCount() == 0 || facts.columnCount() == 0) {
			reasons.add(RowEmissionExclusion.EMPTY_TABLE);
		}
		return reasons;
	}

	/** 実際の匿名フローと、まだ解放していない Pass B の計画から材料を集めます。 */
	static EnumSet<RowEmissionExclusion> rowEmissionExclusionsAfterPassB(final TableBox table,
			final BlockBuilder host, final boolean passCEligible, final int bodyGroupCount,
			final boolean footer, final boolean topCaption, final int columnCount, final boolean columns,
			final TableRowGroupBox header, final List<TableRowGroupBox> groups,
			final Map<TableRowGroupBox, ? extends List<TableRowBox>> groupRows,
			final Map<TableRowBox, ? extends List<CellContent>> rowCells) {
		if (host.getPageContext() == null || !UAProps.PROCESSING_TABLE_ROW_EMISSION
				.getBoolean(host.getPageContext().getPageGenerator().getUserAgent())) {
			return EnumSet.of(RowEmissionExclusion.DISABLED);
		}
		final BreakableBuilder intake = host instanceof BreakableBuilder b ? b : null;
		boolean horizontal = true, complex = false, floating = false;
		boolean forced = intake != null && intake.breakAfter != null;
		double ancestorFrame = 0;
		for (Builder ancestor = host; ancestor != null; ancestor = ancestor.getParentBuilder()) {
			if (!(ancestor instanceof BlockBuilder block)) {
				complex = true;
				break;
			}
			floating |= block.hasLineExclusions();
			for (int i = 0; i < block.getFlowCount(); ++i) {
				final var box = block.getFlow(i).box;
				final BlockParams params = box.getBlockParams();
				horizontal &= !params.flow.isVertical();
				if (box.getType() == BoxType.PAGE) continue; // 用紙の指定高は祖先の指定高とは別。
				if (box.getPos() instanceof AbstractBlockLevelPos pos) {
					forced |= forced(pos.pageBreakBefore) || forced(pos.pageBreakAfter);
				}
				complex |= box.getPos().getType() != PosType.FLOW || box.getColumnCount() > 1
						|| params.columns.count != 0 || !LayoutUtils.isNone(params.columns.width)
						|| params.size.getPageType(params.flow) != LengthType.AUTO
						|| params.maxSize.getPageType(params.flow) != LengthType.AUTO
						|| (params.minSize.getPageType(params.flow) != LengthType.AUTO
								&& (params.minSize.getPageType(params.flow) != LengthType.ABSOLUTE
										|| params.minSize.getPageLength(params.flow) != 0))
						|| params.aspectRatio != 0;
				ancestorFrame += Math.max(0, box.getFrame().getFrameTop())
						+ Math.max(0, box.getFrame().getFrameBottom());
			}
		}
		double headerSize = 0;
		if (header != null) {
			for (final TableRowBox row : groupRows.get(header)) headerSize += row.getPageSize();
		}
		// 現頁の容量が次頁でも続くとは限らない。第1段では全頁の容量の床を使う。
		// 枠・反復ヘッダ・祖先の枠を含めて収まる行だけなら、継続先の先頭行も
		// 行内分割・巨大行の rescue に入らない。現在頁の残りは初回先頭行だけに使う。
		final double frame = Math.max(0, table.getFrame().getFrameTop())
				+ Math.max(0, table.getFrame().getFrameBottom());
		final double rowCapacity = BreakableBuilder.MIN_PAGE_LIMIT - ancestorFrame - frame - headerSize;
		final double firstCapacity = intake == null ? 0
				: intake.getPageLimit() - intake.getPageAxis() - frame - headerSize;
		boolean rowspan = false, groupSize = false, orthogonal = false, effects = false, pageDependent = false;
		boolean splitRows = !Double.isFinite(headerSize) || headerSize < 0
				|| !Double.isFinite(rowCapacity) || !Double.isFinite(firstCapacity);
		int rowCount = 0;
		for (final TableRowGroupBox group : groups) {
			final var params = group.getInnerTableParams();
			groupSize |= params.size.getType() != LengthType.AUTO
					|| params.maxSize.getType() != LengthType.AUTO
					|| (params.minSize.getType() != LengthType.AUTO
							&& (params.minSize.getType() != LengthType.ABSOLUTE || params.minSize.getLength() != 0));
			forced |= forced(group.getTableRowGroupPos().pageBreakBefore)
					|| forced(group.getTableRowGroupPos().pageBreakAfter);
			final List<TableRowBox> rows = groupRows.get(group);
			for (int i = 0; i < rows.size(); ++i) {
				final TableRowBox row = rows.get(i);
				if (group != header) {
					++rowCount;
					final double size = row.getPageSize();
					splitRows |= !Double.isFinite(size) || size < 0 || size > rowCapacity
							|| (i == 0 && size > firstCapacity);
					// 行間avoidの後退は、先頭行にも高さ-1ptの切断線を当て得る。
					// ページに収まる行でも、その人工的な線での行内分割は未対応。
					splitRows |= row.getTableRowPos().pageBreakBefore == PageBreakMode.AVOID
							|| row.getTableRowPos().pageBreakAfter == PageBreakMode.AVOID;
				}
				forced |= forced(row.getTableRowPos().pageBreakBefore) || forced(row.getTableRowPos().pageBreakAfter);
				for (final CellContent cell : rowCells.get(row)) {
					rowspan |= cell.rowspan > 1;
					if (cell.isExtended()) continue;
					orthogonal |= cell.getCellBox().getBlockParams().flow.isVertical();
					final var body = cell.sealedBodyOrNull();
					if (body != null && body.handle() != null && !body.handle().hasTextSlice()) {
						final var range = body.handle();
						final var source = range.source();
						if (!pageDependent) {
							// 凍結済みparamsを検査するだけで、box生成・本文bindはしない。
							// %とcalcの割合成分を保守的に除外する(包含セルで解決する幅も含む)。
							// vh/vw等はViewportUnitsで解析時にUA設定から絶対長へ解決済み。
							// その絶対長は再生でも不変なので、現ページへの依存はない。
							try (final var slice = source.capture(range.fromId(), range.toId())) {
								final boolean[] relative = { slice == null };
								if (slice != null) slice.replay(event -> {
									if (event instanceof net.zamasoft.foliojet.layout.fragment.LayoutSource.Replaced replaced) {
										relative[0] |= replaced.recipe().params().hasRelativeSize();
									} else if (event instanceof net.zamasoft.foliojet.layout.fragment.LayoutSource.Start start) {
										relative[0] |= start.recipe().hasPageRelativeSize();
									}
								});
								pageDependent = relative[0];
							}
						}
						// float の索引は FOOTNOTE/PAGE_*/PAGE_NOTE_* と子孫も含む。
						// 通常float・absoluteも、第1段では bind 時点の移動を証明しない。
						effects |= source.containsFloat(range.fromId(), range.toId())
								|| source.containsAbsolute(range.fromId(), range.toId())
								|| source.containsOpaque(range.fromId(), range.toId());
					}
				}
			}
		}
		final EnumSet<RowEmissionExclusion> reasons = rowEmissionExclusionsAfterPassB(table, new RowEmissionFacts(
				ReplayIntent.current() == ReplayIntent.MAIN && host.isMain(), passCEligible,
				intake != null && intake.supportsIncompleteTableIntake(), horizontal, bodyGroupCount,
				rowCount, columnCount, rowspan, footer, topCaption, groupSize, splitRows, orthogonal, effects,
				complex, forced, columns));
		if (floating) reasons.add(RowEmissionExclusion.FLOATING_HOST);
		if (pageDependent) reasons.add(RowEmissionExclusion.PAGE_DEPENDENT_CELL_CONTENT);
		return reasons;
	}

	/**
	 * 未完表の初回受理・追記通知に必要な可視本文の下限です(B-2b-5)。
	 * capacityは本文に使える切断線以上の値を渡します。正の枠・HEADER分を
	 * 差し引かない保守的な容量でも構いません。
	 * 親の加算と局所切断線の減算が0.5pt境界の反対側へ丸まるため、
	 * compareが正になるだけでは足りません。加算・減算の両方で厳密に
	 * THRESHOLDを超えるまで保留します。最終行はこの判定を使わず完成へ進めます。
	 */
	public static boolean hasRowEmissionOverflow(final double visibleBodySize, final double capacity) {
		return visibleBodySize > capacity + LayoutUtils.THRESHOLD
				&& visibleBodySize - capacity > LayoutUtils.THRESHOLD;
	}

	private static boolean forced(final PageBreakMode mode) {
		return mode != PageBreakMode.AUTO && mode != PageBreakMode.AVOID;
	}

	/**
	 * @param builder  表を構築するコンテキストのビルダー
	 * @param tableBox 対象の表ボックス
	 */
	public static TableBuildPlan plan(final Builder builder, final TableBox tableBox) {
		final TableParams params = tableBox.getTableParams();
		final EnumSet<TableRetentionReason> reasons = EnumSet.noneOf(TableRetentionReason.class);
		if (!builder.isMain()) {
			reasons.add(TableRetentionReason.NESTED_LAYOUT);
		}
		if (params.layout == TableParams.LAYOUT_AUTO) {
			reasons.add(TableRetentionReason.AUTO_COLUMNS);
		}
		final boolean isFlow = tableBox.getBlockBox().getPos().getType() == PosType.FLOW;
		if (!isFlow) {
			reasons.add(TableRetentionReason.OUT_OF_FLOW);
		}
		if (params.size.getPageType(params.flow) != LengthType.AUTO) {
			reasons.add(TableRetentionReason.SPECIFIED_PAGE_SIZE);
		}
		if (params.size.getLineType(params.flow) == LengthType.AUTO) {
			reasons.add(TableRetentionReason.AUTO_LINE_SIZE);
		}
		// M6b Phase B5e(2026-07-21): 表自身の書字方向が現在開いているflowと
		// 軸違い(横書き⇄縦書き)の場合はRETAINEDへ回す——Incrementalだと
		// IncrementalTableBuilder.pageBreak()がbreakDepth障壁を迂回し、legacy
		// OpenChain(ContinuationCapability.ORTHOGONAL_FLOW)へ実際に到達する
		// ため(TableRetentionReason.ORTHOGONAL_WRITING_MODEのjavadoc参照)。
		if (builder instanceof BreakableBuilder breakableBuilder
				&& breakableBuilder.getFlowBox().getBlockParams().flow.isVertical() != params.flow.isVertical()) {
			reasons.add(TableRetentionReason.ORTHOGONAL_WRITING_MODE);
		}
		if (reasons.isEmpty()) {
			return new TableBuildPlan(TableBuildPlan.Mode.INCREMENTAL, reasons);
		}
		return new TableBuildPlan(TableBuildPlan.Mode.RETAINED, reasons);
	}
}
