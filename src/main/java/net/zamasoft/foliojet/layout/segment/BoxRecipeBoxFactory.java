package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
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
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * A factory that reconstructs actual {@code IBox} instances from {@link BoxRecipe}
 * (introduced 2026-07-22, M6d-A3d).
 *
 * <p>
 * E-6 increment 3b-1 (2026-07-24): Centralized the former {@code SourceReplayer.newBox}
 * (the paired implementation reading directly from {@code LayoutSource.Start} )
 * here as the construction kernel by kind ({@link #create(LayoutSource.BoxKind, Params, Pos)}).
 * The kernel is also the single place defining TableBox's alias structure:
 * the outer box and inner FlowBlockBox share TableParams.
 * The recipe version ({@link #create(BoxRecipe)}) materializes templates, then delegates to the kernel;
 * it never touches old {@code LayoutSource} objects.
 * </p>
 */
public final class BoxRecipeBoxFactory {
	private BoxRecipeBoxFactory() {
	}

	/**
	 * Number of {@code TableBox} reconstructions during replay (G-1 investigation, 2026-07-25;
	 * restored with user approval of the table-set implementation on 2026-07-30).
	 * All callers of this factory are replay-driven ({@code SegmentExecutor}), so this directly counts
	 * tables rebuilt through source replay.
	 * Used to prove the table replay consumer (T-c) actually runs.
	 * At G-1, this was always 0 (no consumer), which established that recipe recording alone was pointless.
	 */
	public static final java.util.concurrent.atomic.AtomicLong TABLE_REPLAYS = new java.util.concurrent.atomic.AtomicLong();

	/**
	 * Number of CAPTION recipe materializations (caption recipes C1;
	 * becomes >0 when C4 enables table-context replay).
	 * Proves the path actually runs, analogous to TABLE_REPLAYS.
	 */
	public static final java.util.concurrent.atomic.AtomicLong CAPTION_REPLAYS = new java.util.concurrent.atomic.AtomicLong();

	/** Number of {@code GridBox} reconstructions during replay (Grid G0c; G7 observation point). */
	public static final java.util.concurrent.atomic.AtomicLong GRID_REPLAYS = new java.util.concurrent.atomic.AtomicLong();

	/** Number of {@code FlexBox} reconstructions during replay (Flex F0c; observes that the path actually runs). */
	public static final java.util.concurrent.atomic.AtomicLong FLEX_REPLAYS = new java.util.concurrent.atomic.AtomicLong();

	/** For tests: also notifies from DirectSession's conversion thread. Tests handle saving/restoring. */
	static volatile java.util.function.Consumer<BoxRecipe.PlacedTable> placedTableReplayObserver;

	/** Materializes {@code recipe}'s templates and returns the corresponding fresh {@code IBox}. */
	public static INonReplacedBox create(final BoxRecipe recipe) {
		return switch (recipe) {
		case BoxRecipe.Flow r -> create(LayoutSource.BoxKind.FLOW, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Multicol r ->
			create(LayoutSource.BoxKind.MULTICOL, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Inline r -> create(LayoutSource.BoxKind.INLINE, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Marker r -> {
			final OutsideMarkerBox marker = (OutsideMarkerBox) create(LayoutSource.BoxKind.MARKER,
					r.params().materialize(), r.pos().materialize());
			marker.setOverlaysFollowingBlock(r.overlaysFollowingBlock());
			yield marker;
		}
		case BoxRecipe.FloatBlock r ->
			create(LayoutSource.BoxKind.FLOAT_BLOCK, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.InlineBlock r ->
			create(LayoutSource.BoxKind.INLINE_BLOCK, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.InsideMarker r ->
			create(LayoutSource.BoxKind.INSIDE_MARKER, r.params().materialize(), r.pos().materialize());
		// The kernel handles Table params sharing (aliasing), so
		// only one materialize call per template is needed here.
		case BoxRecipe.Table r -> create(LayoutSource.BoxKind.TABLE, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.PlacedTable r -> {
			TABLE_REPLAYS.incrementAndGet();
			final var params = r.params().materialize();
			final net.zamasoft.foliojet.layout.box.AbstractBlockBox block = switch (r.placement()) {
			case BoxRecipe.InlineBlock p -> new InlineBlockBox(params, p.pos().materialize());
			case BoxRecipe.FloatBlock p -> new FloatBlockBox(params, p.pos().materialize());
			case BoxRecipe.Absolute p -> new AbsoluteBlockBox(params, p.pos().materialize());
			default -> throw new IllegalArgumentException("table placement: " + r.placement().kind());
			};
			final TableBox table = new TableBox(params, block);
			final var observer = placedTableReplayObserver;
			if (observer != null) observer.accept(r);
			yield table;
		}
		case BoxRecipe.TableRowGroup r ->
			create(LayoutSource.BoxKind.TABLE_ROW_GROUP, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableRow r ->
			create(LayoutSource.BoxKind.TABLE_ROW, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableCell r ->
			create(LayoutSource.BoxKind.TABLE_CELL, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableColumnGroup r ->
			create(LayoutSource.BoxKind.TABLE_COLUMN_GROUP, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableColumn r ->
			create(LayoutSource.BoxKind.TABLE_COLUMN, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Absolute r ->
			create(LayoutSource.BoxKind.ABSOLUTE, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Grid r -> create(LayoutSource.BoxKind.GRID, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Flex r -> create(LayoutSource.BoxKind.FLEX, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Caption r ->
			create(LayoutSource.BoxKind.CAPTION, r.params().materialize(), r.pos().materialize());
		};
	}

	/**
	 * The construction kernel creating a fresh box of the same kind from kind and params/pos
	 * (moved from the former {@code SourceReplayer.newBox} in E-6 increment 3b-1;
	 * the factory paired with {@code StyleBuilder.boxKind} ).
	 * E-6 increment 3b-4 also changed {@code LayoutSource.Start} to retain recipes frozen at recording time,
	 * so no caller still passes live params/pos directly.
	 * Recipe-driven materialization results ({@link #create(BoxRecipe)}) pass through here,
	 * the sole location of construction logic for each kind.
	 */
	public static INonReplacedBox create(final LayoutSource.BoxKind kind, final Params params, final Pos pos) {
		return switch (kind) {
		case FLOW -> new FlowBlockBox((BlockParams) params, (FlowPos) pos);
		case MULTICOL -> new MulticolumnBlockBox((BlockParams) params, (FlowPos) pos);
		case INLINE -> new InlineBox((InlineParams) params, (InlinePos) pos);
		case MARKER -> new OutsideMarkerBox((BlockParams) params, (InlinePos) pos);
		case FLOAT_BLOCK -> new FloatBlockBox((BlockParams) params, (FloatPos) pos);
		case INLINE_BLOCK -> new InlineBlockBox((BlockParams) params, (InlinePos) pos);
		case INSIDE_MARKER -> new InsideMarkerBox((BlockParams) params, (InlinePos) pos);
		case TABLE -> {
			// The outer TableBox and inner FlowBlockBox share TableParams.
			// (The single definition of the alias structure, identical for live/recipe-driven construction.
			// Recording eligibility requires params aliasing to match this reconstruction.)
			final TableParams tableParams = (TableParams) params;
			TABLE_REPLAYS.incrementAndGet();
			yield new TableBox(tableParams, new FlowBlockBox(tableParams, (FlowPos) pos));
		}
		case TABLE_ROW_GROUP -> new TableRowGroupBox((InnerTableParams) params, (TableRowGroupPos) pos);
		case TABLE_ROW -> new TableRowBox((InnerTableParams) params, (TableRowPos) pos);
		case TABLE_CELL -> new TableCellBox((BlockParams) params, (TableCellPos) pos, new FlowContainer());
		case TABLE_COLUMN_GROUP -> new TableColumnGroupBox((InnerTableParams) params, (TableColumnPos) pos);
		case TABLE_COLUMN -> new TableColumnBox((InnerTableParams) params, (TableColumnPos) pos);
		// E-6 increment 4e: Absolutely positioned block (same construction as StyleBuilder:1166)
		case ABSOLUTE -> new AbsoluteBlockBox((BlockParams) params, (AbsolutePos) pos);
		// Grid G0c: Count GRID_REPLAYS to prove the replay consumer actually runs.
		// (Analogous to TABLE_REPLAYS.)
		case GRID -> {
			GRID_REPLAYS.incrementAndGet();
			yield new net.zamasoft.foliojet.layout.box.impl.GridBox(
					(net.zamasoft.foliojet.layout.box.params.GridParams) params, (FlowPos) pos);
		}
		// Flex F0c: Reconstruction and observation of actual execution, analogous to GRID
		case FLEX -> {
			FLEX_REPLAYS.incrementAndGet();
			yield new net.zamasoft.foliojet.layout.box.impl.FlexBox(
					(net.zamasoft.foliojet.layout.box.params.FlexParams) params, (FlowPos) pos);
		}
		// Caption recipes C1: Prove the replay consumer actually runs (becomes >0 in C4).
		case CAPTION -> {
			CAPTION_REPLAYS.incrementAndGet();
			yield new FlowBlockBox((BlockParams) params,
					(net.zamasoft.foliojet.layout.box.params.TableCaptionPos) pos);
		}
		};
	}

	/**
	 * Materializes {@link ReplacedRecipe} 's templates and returns the corresponding fresh
	 * {@link AbstractReplacedBox} (introduced 2026-07-22, M6d-A).
	 * Paired with {@link #create} , but a separate method because its return type is
	 * {@code AbstractReplacedBox} , not {@code INonReplacedBox}
	 * (see "Items still unstarted" in the `development record`).
	 */
	public static AbstractReplacedBox createReplaced(final ReplacedRecipe recipe) {
		return switch (recipe) {
		case ReplacedRecipe.Inline r -> new InlineReplacedBox(r.params().materialize(), r.pos().materialize());
		case ReplacedRecipe.Flow r -> new FlowReplacedBox(r.params().materialize(), r.pos().materialize());
		case ReplacedRecipe.Float r -> new FloatReplacedBox(r.params().materialize(), r.pos().materialize());
		case ReplacedRecipe.Absolute r -> new AbsoluteReplacedBox(r.params().materialize(), r.pos().materialize());
		};
	}
}
