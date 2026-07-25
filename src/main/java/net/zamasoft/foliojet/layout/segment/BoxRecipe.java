package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

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
 * </p>
 *
 * <p>
 * E-6増分3b-4(2026-07-24): {@link #freeze}を追加し、記録時
 * ({@code StyleBuilder.startBox})にlive params/posから凍結する——
 * {@code StyleBuilder.boxKind}が非nullを返す全14 kind(E-6増分4eで
 * {@link Absolute}追加)をカバーする
 * <b>総関数</b>(旧{@code LayoutSourceEventConverter.convertStart}の
 * 変換時freezeの移設。{@code ReplacedRecipe.freeze}と違い失敗変種は
 * ない)。テンプレートを持たない種別はそもそも{@code boxKind}がnullを
 * 返し{@code LayoutSource.Opaque}として記録される。
 * </p>
 *
 * <p>
 * 子Segment参照はここに含めない——recipeは「箱の生成方法」、子範囲は
 * {@link ContainerNode}が別途持つ「構造」(A3a方針を継続)。
 * </p>
 */
public sealed interface BoxRecipe {
	BoxKind kind();

	/**
	 * 凍結済みparamsの書字方向を返します(E-6増分3b-4——
	 * {@code LayoutSource.containsMixedFlow}が凍結済みStartから読む)。
	 * {@code InnerTableParams}系({@code AbstractTextParams}を継承せず
	 * flowを持たない)は{@code null}——旧liveログの
	 * {@code params instanceof AbstractTextParams}判定と同値。
	 */
	WritingMode flowOrNull();

	/**
	 * kindとlive params/posから対応するvariantを凍結します(E-6増分3b-4、
	 * 記録時freeze)。castの成立は{@code StyleBuilder.boxKind}のkind判定が
	 * 保証する(例: {@link BoxKind#TABLE}は{@code box.getPos() instanceof
	 * FlowPos}の場合のみ記録される)。
	 */
	static BoxRecipe freeze(final LayoutSource.BoxKind kind, final Params params, final Pos pos) {
		return switch (kind) {
		case FLOW -> new Flow(BlockParamsTemplate.freeze((BlockParams) params), FlowPosTemplate.freeze((FlowPos) pos));
		// MulticolumnBlockBoxはFlowBlockBoxを継承するためBoxKind.FLOWと
		// 同じBlockParams/FlowPosを使う(既存コード確認済み)
		case MULTICOL -> new Multicol(BlockParamsTemplate.freeze((BlockParams) params),
				FlowPosTemplate.freeze((FlowPos) pos));
		case INLINE -> new Inline(InlineParamsTemplate.freeze((InlineParams) params),
				InlinePosTemplate.freeze((InlinePos) pos));
		// OutsideMarkerBoxはBlockParams/InlinePosを使う(既存コード確認済み)
		case MARKER -> new Marker(BlockParamsTemplate.freeze((BlockParams) params),
				InlinePosTemplate.freeze((InlinePos) pos));
		// FloatBlockBoxはBlockParams/FloatPosを使う(既存コード確認済み)
		case FLOAT_BLOCK -> new FloatBlock(BlockParamsTemplate.freeze((BlockParams) params),
				FloatPosTemplate.freeze((FloatPos) pos));
		// InlineBlockBoxはBlockParams/InlinePosを使う(既存コード確認済み)
		case INLINE_BLOCK -> new InlineBlock(BlockParamsTemplate.freeze((BlockParams) params),
				InlinePosTemplate.freeze((InlinePos) pos));
		// InsideMarkerBoxはBlockParams/InlinePosを使う(既存コード確認済み)
		case INSIDE_MARKER -> new InsideMarker(BlockParamsTemplate.freeze((BlockParams) params),
				InlinePosTemplate.freeze((InlinePos) pos));
		// TableBoxはTableParams+「内側blockBoxのFlowPos」を使う。
		// F-4(2026-07-25)の実測どおり外側TableBox.getPos()は常にTablePos
		// (FlowPosではない)なので、記録側(StyleBuilder.startBox)は
		// TableBox.getBlockBox().getPos()を渡す契約。BoxKind.TABLEは
		// 内側blockBoxがFlowPosのときだけ記録されるためcastは成立する
		case TABLE -> new Table(TableParamsTemplate.freeze((TableParams) params),
				FlowPosTemplate.freeze((FlowPos) pos));
		// TableRowGroupBoxはInnerTableParams/TableRowGroupPosを使う(既存コード確認済み)
		case TABLE_ROW_GROUP -> new TableRowGroup(InnerTableParamsTemplate.freeze((InnerTableParams) params),
				TableRowGroupPosTemplate.freeze((TableRowGroupPos) pos));
		// TableRowBoxはInnerTableParams/TableRowPosを使う(既存コード確認済み)
		case TABLE_ROW -> new TableRow(InnerTableParamsTemplate.freeze((InnerTableParams) params),
				TableRowPosTemplate.freeze((TableRowPos) pos));
		// TableCellBoxはBlockParams/TableCellPosを使う(既存コード確認済み)
		case TABLE_CELL -> new TableCell(BlockParamsTemplate.freeze((BlockParams) params),
				TableCellPosTemplate.freeze((TableCellPos) pos));
		// TableColumnGroupBoxはInnerTableParams/TableColumnPosを使う(既存コード確認済み)
		case TABLE_COLUMN_GROUP -> new TableColumnGroup(InnerTableParamsTemplate.freeze((InnerTableParams) params),
				TableColumnPosTemplate.freeze((TableColumnPos) pos));
		// TableColumnBoxはTableColumnGroupBoxと同じInnerTableParams/
		// TableColumnPosを使う(既存コード確認済み)
		case TABLE_COLUMN -> new TableColumn(InnerTableParamsTemplate.freeze((InnerTableParams) params),
				TableColumnPosTemplate.freeze((TableColumnPos) pos));
		// AbsoluteBlockBoxはBlockParams/AbsolutePosを使う(E-6増分4e。
		// AbsolutePosTemplateはReplacedRecipe.Absoluteで実績あり)
		case ABSOLUTE -> new Absolute(BlockParamsTemplate.freeze((BlockParams) params),
				AbsolutePosTemplate.freeze((AbsolutePos) pos));
		};
	}

	record Flow(BlockParamsTemplate params, FlowPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.FLOW;
		}

		public WritingMode flowOrNull() {
			return this.params.flow();
		}
	}

	record Inline(InlineParamsTemplate params, InlinePosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.INLINE;
		}

		public WritingMode flowOrNull() {
			return this.params.flow();
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

		public WritingMode flowOrNull() {
			return this.params.flow();
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

		public WritingMode flowOrNull() {
			return this.params.flow();
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

		public WritingMode flowOrNull() {
			return this.params.flow();
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

		public WritingMode flowOrNull() {
			return this.params.flow();
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

		public WritingMode flowOrNull() {
			return this.params.flow();
		}
	}

	/**
	 * 表({@code TableBox})——{@code TableParams}と<b>内側blockBoxの</b>
	 * {@code FlowPos}を使う(外側{@code TableBox.getPos()}は常に
	 * {@code TablePos}で配置種別を持たない。{@code StyleBuilder.startBox}
	 * が内側posを渡す契約)。
	 *
	 * <p>
	 * <b>既定では生成されない(G-1、2026-07-25)</b>: 記録は
	 * {@code foliojet.tableRecipe}実験フラグ配下で、既定は全ての表が
	 * {@code Opaque}({@code StyleBuilder.boxKind}のTableBox分岐参照)。
	 * </p>
	 */
	record Table(TableParamsTemplate params, FlowPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.TABLE;
		}

		public WritingMode flowOrNull() {
			return this.params.flow();
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

		public WritingMode flowOrNull() {
			return null;
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

		public WritingMode flowOrNull() {
			return null;
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

		public WritingMode flowOrNull() {
			return this.params.flow();
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

		public WritingMode flowOrNull() {
			return null;
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

		public WritingMode flowOrNull() {
			return null;
		}
	}

	/**
	 * 絶対配置ブロック({@code AbsoluteBlockBox})——{@code BlockParams}/
	 * {@code AbsolutePos}を使う(E-6増分4e、2026-07-24)。記録の主目的は
	 * 絶対配置ビルダー自身の本文range seal({@code endOf}が引けるように
	 * なること——旧Opaque記録では{@code TwoPassSealReject.NO_RANGE})。
	 * 絶対配置を<b>含む</b>範囲の再生は{@code LayoutSource
	 * .containsAbsolute}ゲートが従来どおりフォールバックさせる
	 * (係留・deferred bindの二重化防止——同メソッドjavadoc参照)。
	 */
	record Absolute(BlockParamsTemplate params, AbsolutePosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.ABSOLUTE;
		}

		public WritingMode flowOrNull() {
			return this.params.flow();
		}
	}
}
