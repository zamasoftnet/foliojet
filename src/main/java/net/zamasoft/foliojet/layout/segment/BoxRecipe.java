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
 * A recipe describing how to create a box
 * (introduced 2026-07-22, defined in M6d-A3a and extended for A3c).
 *
 * <p>
 * A3a was a skeleton containing only {@link BoxKind} , but implementing A3b
 * (`Params`/`Pos` freeze/materialize) required an extension to carry actual frozen contents.
 * Each {@code BoxKind} needs a different set of templates:
 * for example, {@link BoxKind#FLOW} uses {@link BlockParamsTemplate} + {@link FlowPosTemplate} ,
 * while {@link BoxKind#INLINE} uses {@link InlineParamsTemplate} + {@link InlinePosTemplate} .
 * Therefore represented as sealed-interface variants rather than a single record.
 * </p>
 *
 * <p>
 * E-6 increment 3b-4 (2026-07-24): Added {@link #freeze} to freeze live params/pos at recording time
 * ({@code StyleBuilder.startBox}).
 * A <b>total function</b> covering all 13 kinds for which {@code StyleBuilder.boxKind} returns non-null
 * ({@link Absolute} added in E-6 increment 4e).
 * Moved the former conversion-time freezing from {@code LayoutSourceEventConverter.convertStart} ;
 * unlike {@code ReplacedRecipe.freeze} , it has no failure variant.
 * Kinds without templates already return null from {@code boxKind}
 * and are recorded as {@code LayoutSource.Opaque} .
 * </p>
 *
 * <p>
 * Contains no child Segment references: a recipe describes box creation, not structure
 * (continuing the A3a policy).
 * </p>
 */
public sealed interface BoxRecipe {
	BoxKind kind();

	/**
	 * A form that searches up to a fixed page axis to resolve percentage sizes even if the containing block's
	 * height is auto. Excludes ordinary Flow height/min-height, which refer to the immediate parent.
	 * Conservatively returns true even when actual ancestors can resolve them; does not create or bind boxes.
	 */
	default boolean hasPageRelativeSize() {
		final BlockParamsFields fields = switch (this) {
		case InlineBlock box -> box.params().fields();
		case Marker box -> box.params().fields();
		case InsideMarker box -> box.params().fields();
		case FloatBlock box -> box.params().fields();
		case Absolute box -> box.params().fields();
		case Table box -> box.params().common();
		case PlacedTable box -> box.params().common();
		default -> null;
		};
		if (fields == null) return false;
		// For orthogonal children, physical height becomes the line axis. Check both axes and conservatively
		// include percentage widths resolvable by the containing cell (the same policy as the Replaced check).
		return hasRelativeSize(fields.size()) || hasRelativeSize(fields.minSize()) || hasRelativeSize(fields.maxSize())
				|| (this instanceof PlacedTable box && box.placement().hasPageRelativeSize());
	}

	private static boolean hasRelativeSize(final net.zamasoft.foliojet.layout.box.params.Dimension size) {
		return size.getWidthType().needsReference() || size.getHeightType().needsReference();
	}

	/**
	 * Inline/float/absolute tables in the main log and repeated content.
	 * The kind remains TABLE with only one Start/End pair.
	 * Like TABLE freezing, freezes positioning from the inner blockBox's pos
	 * and shares TableParams between inner and outer boxes on reconstruction.
	 * The table builder handles placement of the host.
	 */
	record PlacedTable(TableParamsTemplate params, BoxRecipe placement) implements BoxRecipe {
		public PlacedTable {
			if (!(placement instanceof InlineBlock || placement instanceof FloatBlock || placement instanceof Absolute)) {
				throw new IllegalArgumentException("table placement: " + placement.kind());
			}
		}

		public BoxKind kind() { return BoxKind.TABLE; }
		public WritingMode flowOrNull() { return this.placement.flowOrNull(); }
	}

	/**
	 * Returns the frozen params' writing direction (E-6 increment 3b-4;
	 * {@code LayoutSource.containsMixedFlow} reads it from frozen Starts).
	 * Returns {@code null} for the {@code InnerTableParams} family
	 * (which does not extend {@code AbstractTextParams} and has no flow),
	 * equivalent to the old live log's {@code params instanceof AbstractTextParams} check.
	 */
	WritingMode flowOrNull();

	/** Also freezes layout attributes outside params/pos when recording a box. */
	static BoxRecipe freeze(final LayoutSource.BoxKind kind,
			final net.zamasoft.foliojet.layout.box.INonReplacedBox box) {
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox marker) {
			return new Marker(BlockParamsTemplate.freeze(marker.getBlockParams()),
					InlinePosTemplate.freeze(marker.getInlinePos()), marker.overlaysFollowingBlock());
		}
		final Pos pos = kind == LayoutSource.BoxKind.TABLE
				? ((net.zamasoft.foliojet.layout.box.impl.TableBox) box).getBlockBox().getPos() : box.getPos();
		return freeze(kind, box.getParams(), pos);
	}

	/**
	 * Freezes the corresponding variant from kind and live params/pos
	 * (E-6 increment 3b-4, recording-time freeze).
	 * The kind check in {@code StyleBuilder.boxKind} (exact runtime-class match) guarantees valid casts.
	 */
	static BoxRecipe freeze(final LayoutSource.BoxKind kind, final Params params, final Pos pos) {
		return switch (kind) {
		case FLOW -> new Flow(BlockParamsTemplate.freeze((BlockParams) params), FlowPosTemplate.freeze((FlowPos) pos));
		// MulticolumnBlockBox extends FlowBlockBox, so it uses the same
		// BlockParams/FlowPos as BoxKind.FLOW (confirmed in existing code).
		case MULTICOL -> new Multicol(BlockParamsTemplate.freeze((BlockParams) params),
				FlowPosTemplate.freeze((FlowPos) pos));
		case INLINE -> new Inline(InlineParamsTemplate.freeze((InlineParams) params),
				InlinePosTemplate.freeze((InlinePos) pos));
		// OutsideMarkerBox uses BlockParams/InlinePos (confirmed in existing code).
		case MARKER -> new Marker(BlockParamsTemplate.freeze((BlockParams) params),
				InlinePosTemplate.freeze((InlinePos) pos));
		// FloatBlockBox uses BlockParams/FloatPos (confirmed in existing code).
		case FLOAT_BLOCK -> new FloatBlock(BlockParamsTemplate.freeze((BlockParams) params),
				FloatPosTemplate.freeze((FloatPos) pos));
		// InlineBlockBox uses BlockParams/InlinePos (confirmed in existing code).
		case INLINE_BLOCK -> new InlineBlock(BlockParamsTemplate.freeze((BlockParams) params),
				InlinePosTemplate.freeze((InlinePos) pos));
		// InsideMarkerBox uses BlockParams/InlinePos (confirmed in existing code).
		case INSIDE_MARKER -> new InsideMarker(BlockParamsTemplate.freeze((BlockParams) params),
				InlinePosTemplate.freeze((InlinePos) pos));
		// TableBox uses TableParams + the inner blockBox's FlowPos.
		// The outer TableBox.getPos() always returns TablePos (not FlowPos), so
		// the recording side (RecordingLayoutSink.start) must pass TableBox.getBlockBox().getPos().
		// BoxKind.TABLE is recorded only when the inner blockBox has a plain FlowPos,
		// making the cast valid (G-1 recording contract corrected, restored 2026-07-30).
		case TABLE -> new Table(TableParamsTemplate.freeze((TableParams) params),
				FlowPosTemplate.freeze((FlowPos) pos));
		// TableRowGroupBox uses InnerTableParams/TableRowGroupPos (confirmed in existing code).
		case TABLE_ROW_GROUP -> new TableRowGroup(InnerTableParamsTemplate.freeze((InnerTableParams) params),
				TableRowGroupPosTemplate.freeze((TableRowGroupPos) pos));
		// TableRowBox uses InnerTableParams/TableRowPos (confirmed in existing code).
		case TABLE_ROW -> new TableRow(InnerTableParamsTemplate.freeze((InnerTableParams) params),
				TableRowPosTemplate.freeze((TableRowPos) pos));
		// TableCellBox uses BlockParams/TableCellPos (confirmed in existing code).
		case TABLE_CELL -> new TableCell(BlockParamsTemplate.freeze((BlockParams) params),
				TableCellPosTemplate.freeze((TableCellPos) pos));
		// TableColumnGroupBox uses InnerTableParams/TableColumnPos (confirmed in existing code).
		case TABLE_COLUMN_GROUP -> new TableColumnGroup(InnerTableParamsTemplate.freeze((InnerTableParams) params),
				TableColumnPosTemplate.freeze((TableColumnPos) pos));
		// TableColumnBox uses the same InnerTableParams/TableColumnPos as
		// TableColumnGroupBox (confirmed in existing code).
		case TABLE_COLUMN -> new TableColumn(InnerTableParamsTemplate.freeze((InnerTableParams) params),
				TableColumnPosTemplate.freeze((TableColumnPos) pos));
		// AbsoluteBlockBox uses BlockParams/AbsolutePos (E-6 increment 4e;
		// AbsolutePosTemplate already has established use in ReplacedRecipe.Absolute).
		case ABSOLUTE -> new Absolute(BlockParamsTemplate.freeze((BlockParams) params),
				AbsolutePosTemplate.freeze((AbsolutePos) pos));
		// GridBox uses GridParams/FlowPos (Grid G0c).
		case GRID -> new Grid(GridParamsTemplate.freeze((net.zamasoft.foliojet.layout.box.params.GridParams) params),
				FlowPosTemplate.freeze((FlowPos) pos));
		// FlexBox uses FlexParams/FlowPos (Flex F0c).
		case FLEX -> new Flex(FlexParamsTemplate.freeze((net.zamasoft.foliojet.layout.box.params.FlexParams) params),
				FlowPosTemplate.freeze((FlowPos) pos));
		// Table captions use FlowBlockBox + TableCaptionPos (caption recipes C1).
		case CAPTION -> new Caption(BlockParamsTemplate.freeze((BlockParams) params),
				TableCaptionPosTemplate.freeze((net.zamasoft.foliojet.layout.box.params.TableCaptionPos) pos));
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

	/**
	 * Table caption ({@code FlowBlockBox} + {@code TableCaptionPos} ; caption recipes C1).
	 * A context-dependent kind: replay requires a preceding TABLE Start established within the same range
	 * (C2's context-complete gate is authoritative).
	 */
	record Caption(BlockParamsTemplate params, TableCaptionPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.CAPTION;
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
	 * Multi-column block ({@code MulticolumnBlockBox}).
	 * Extends {@code FlowBlockBox} , so uses the same {@code BlockParams} /{@code FlowPos}
	 * as {@link BoxKind#FLOW} (confirmed in existing code).
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
	 * Outside list marker ({@code OutsideMarkerBox}).
	 * Uses {@code BlockParams} /{@code InlinePos} (confirmed in existing code).
	 */
	record Marker(BlockParamsTemplate params, InlinePosTemplate pos, boolean overlaysFollowingBlock) implements BoxRecipe {
		public Marker(final BlockParamsTemplate params, final InlinePosTemplate pos) {
			this(params, pos, false);
		}

		public BoxKind kind() {
			return BoxKind.MARKER;
		}

		public WritingMode flowOrNull() {
			return this.params.flow();
		}
	}

	/**
	 * Floating block ({@code FloatBlockBox}).
	 * Uses {@code BlockParams} /{@code FloatPos} (confirmed in existing code).
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
	 * Inline block ({@code InlineBlockBox}).
	 * Uses {@code BlockParams} /{@code InlinePos} (confirmed in existing code).
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
	 * Inside marker ({@code InsideMarkerBox}).
	 * Uses {@code BlockParams} /{@code InlinePos} (confirmed in existing code).
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
	 * Table ({@code TableBox}).
	 * Uses {@code TableParams} and <b>the inner blockBox's</b> {@code FlowPos}
	 * (the outer {@code TableBox.getPos()} is always {@code TablePos} and has no positioning kind;
	 * {@code RecordingLayoutSink} must pass the inner pos).
	 * Removed after the G-1 investigation, then restored with user approval of the table-set implementation
	 * (2026-07-30, revision of the G-1 decision).
	 */
	record Table(TableParamsTemplate params, FlowPosTemplate pos) implements BoxRecipe {
		public BoxKind kind() {
			return BoxKind.TABLE;
		}

		public WritingMode flowOrNull() {
			return this.params.flow();
		}
	}

	/** Grid container (Grid G0c, 2026-07-31). */
	record Grid(GridParamsTemplate params, FlowPosTemplate pos) implements BoxRecipe {
		@Override
		public BoxKind kind() {
			return BoxKind.GRID;
		}

		@Override
		public WritingMode flowOrNull() {
			return this.params.flow();
		}
	}

	/** Flex container (Flex F0c, 2026-08-02). */
	record Flex(FlexParamsTemplate params, FlowPosTemplate pos) implements BoxRecipe {
		@Override
		public BoxKind kind() {
			return BoxKind.FLEX;
		}

		@Override
		public WritingMode flowOrNull() {
			return this.params.flow();
		}
	}

	/**
	 * Table row group ({@code TableRowGroupBox}).
	 * Uses {@code InnerTableParams} /{@code TableRowGroupPos} (confirmed in existing code).
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
	 * Table row ({@code TableRowBox}).
	 * Uses {@code InnerTableParams} /{@code TableRowPos} (confirmed in existing code).
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
	 * Table cell ({@code TableCellBox}).
	 * Reuses the same {@code BlockParams} as existing {@link BoxKind#FLOW} , etc.,
	 * and uses {@code TableCellPos} (confirmed in existing code).
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
	 * Table column group ({@code TableColumnGroupBox}).
	 * Uses {@code InnerTableParams} /{@code TableColumnPos} (confirmed in existing code).
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
	 * Table column ({@code TableColumnBox}).
	 * Uses the same {@code InnerTableParams} /{@code TableColumnPos}
	 * as {@code TableColumnGroup} (confirmed in existing code).
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
	 * Absolutely positioned block ({@code AbsoluteBlockBox}).
	 * Uses {@code BlockParams} /{@code AbsolutePos} (E-6 increment 4e, 2026-07-24).
	 * Recording primarily enables sealing the absolute builder's own body range
	 * (making {@code endOf} available; former Opaque recording gave {@code TwoPassSealReject.NO_RANGE} ).
	 * The {@code LayoutSource
	 * .containsAbsolute} gate still makes replay of ranges <b>containing</b> absolute positioning fall back
	 * (to prevent duplicate anchoring/deferred bind; see that method's Javadoc).
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
