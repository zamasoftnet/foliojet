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
}
