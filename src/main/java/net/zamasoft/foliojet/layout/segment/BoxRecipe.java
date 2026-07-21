package net.zamasoft.foliojet.layout.segment;

/**
 * ボックスの生成方法を表すrecipeです(2026-07-22新設、M6d-A3a策定・
 * A3c向けに拡張)。
 *
 * <p>
 * A3aでは{@link BoxKind}だけを持つ骨格だったが、A3b(`Params`/`Pos`の
 * freeze/materialize)実装後、実際にfrozenな内容を運べる形へ拡張する
 * 必要が生じた——{@code BoxKind}ごとに必要なテンプレートの組が異なる
 * (例: {@link BoxKind#FLOW}は{@link BlockParamsTemplate}+
 * {@link FlowPosTemplate}、{@link BoxKind#INLINE}は
 * {@link InlineParamsTemplate}+{@link InlinePosTemplate})ため、
 * 単一recordではなくsealed interfaceのvariantとして表現する。
 * まだテンプレートを持たない{@code BoxKind}は、そもそも
 * {@code BoxRecipe}を構築せず{@link SegmentEvent.Barrier}
 * ({@link BarrierReason#NOT_YET_SUPPORTED})で表す(A3c)。
 * </p>
 *
 * <p>
 * 子Segment参照はここに含めない——recipeは「箱の生成方法」、子範囲は
 * {@link ContainerNode}が別途持つ「構造」(A3a方針を継続)。
 * </p>
 */
public sealed interface BoxRecipe {
	BoxKind kind();

	record Flow(BlockParamsTemplate params, FlowPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.FLOW;
		}
	}

	record Inline(InlineParamsTemplate params, InlinePosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.INLINE;
		}
	}

	/**
	 * 段組ブロック({@code MulticolumnBlockBox})——{@code FlowBlockBox}を
	 * 継承するため{@link BoxKind#FLOW}と同じ{@code BlockParams}/
	 * {@code FlowPos}を使う(既存コード確認済み)。
	 */
	record Multicol(BlockParamsTemplate params, FlowPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.MULTICOL;
		}
	}

	/**
	 * 外置きリストマーカー({@code OutsideMarkerBox})——
	 * {@code BlockParams}/{@code InlinePos}を使う(既存コード確認済み)。
	 */
	record Marker(BlockParamsTemplate params, InlinePosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.MARKER;
		}
	}

	/**
	 * 浮動ブロック({@code FloatBlockBox})——{@code BlockParams}/
	 * {@code FloatPos}を使う(既存コード確認済み)。
	 */
	record FloatBlock(BlockParamsTemplate params, FloatPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.FLOAT_BLOCK;
		}
	}

	/**
	 * インラインブロック({@code InlineBlockBox})——{@code BlockParams}/
	 * {@code InlinePos}を使う(既存コード確認済み)。
	 */
	record InlineBlock(BlockParamsTemplate params, InlinePosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.INLINE_BLOCK;
		}
	}

	/**
	 * 内部マーカー({@code InsideMarkerBox})——{@code BlockParams}/
	 * {@code InlinePos}を使う(既存コード確認済み)。
	 */
	record InsideMarker(BlockParamsTemplate params, InlinePosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.INSIDE_MARKER;
		}
	}

	/**
	 * 表({@code TableBox})——{@code TableParams}/{@code FlowPos}を使う。
	 * ただし{@code box.getPos() instanceof FlowPos}の場合のみ記録可能
	 * (絶対配置・浮動の表は{@code StyleBuilder}が当面{@code Opaque}に
	 * する、既存コード確認済み)。
	 */
	record Table(TableParamsTemplate params, FlowPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.TABLE;
		}
	}

	/**
	 * 表の行グループ({@code TableRowGroupBox})——{@code InnerTableParams}/
	 * {@code TableRowGroupPos}を使う(既存コード確認済み)。
	 */
	record TableRowGroup(InnerTableParamsTemplate params, TableRowGroupPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.TABLE_ROW_GROUP;
		}
	}

	/**
	 * 表の行({@code TableRowBox})——{@code InnerTableParams}/
	 * {@code TableRowPos}を使う(既存コード確認済み)。
	 */
	record TableRow(InnerTableParamsTemplate params, TableRowPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.TABLE_ROW;
		}
	}

	/**
	 * 表のセル({@code TableCellBox})——既存{@link BoxKind#FLOW}等と
	 * 同じ{@code BlockParams}を再利用し、{@code TableCellPos}を使う
	 * (既存コード確認済み)。
	 */
	record TableCell(BlockParamsTemplate params, TableCellPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.TABLE_CELL;
		}
	}

	/**
	 * 表のカラムグループ({@code TableColumnGroupBox})——
	 * {@code InnerTableParams}/{@code TableColumnPos}を使う
	 * (既存コード確認済み)。
	 */
	record TableColumnGroup(InnerTableParamsTemplate params, TableColumnPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.TABLE_COLUMN_GROUP;
		}
	}

	/**
	 * 表のカラム({@code TableColumnBox})——{@code TableColumnGroup}と
	 * 同じ{@code InnerTableParams}/{@code TableColumnPos}を使う
	 * (既存コード確認済み)。
	 */
	record TableColumn(InnerTableParamsTemplate params, TableColumnPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.TABLE_COLUMN;
		}
	}
}
