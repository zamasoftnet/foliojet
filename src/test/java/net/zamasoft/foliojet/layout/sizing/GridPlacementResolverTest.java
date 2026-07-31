package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver.GridArea;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver.Result;

/**
 * {@link GridPlacementResolver}(Grid G4a)の純粋計算テストです
 * (consult-codex-2026-07-31-grid-g4.txt Q4のケース一覧)。
 */
public class GridPlacementResolverTest extends TestCase {

	private static final GridLineValue A = GridLineValue.AUTO_VALUE;

	private static GridItemSpec auto() {
		return GridItemSpec.AUTO;
	}

	private static GridItemSpec col(final GridLineValue start, final GridLineValue end) {
		return GridItemSpec.of(start, end, A, A);
	}

	private static GridArea area(final Result result, final int index) {
		return ((Result.Resolved) result).plan().areas().get(index);
	}

	/** 全autoはG3のsource-order配置と厳密一致(col=i%n、row=i/n)。 */
	public void testAllAutoMatchesSourceOrder() {
		final Result result = GridPlacementResolver.resolve(List.of(auto(), auto(), auto(), auto(), auto()), 2);
		assertTrue(result instanceof Result.Resolved);
		for (int i = 0; i < 5; ++i) {
			assertEquals(new GridArea(i % 2, i / 2, 1, 1), area(result, i));
		}
		assertEquals(3, ((Result.Resolved) result).plan().rowCount());
	}

	/** 正負の列線番号とline/line・line/span・span/line。 */
	public void testColumnLineForms() {
		// 3列。1/3=2列span、-2/-1=最終列、2/span 2、span 2/4
		final Result result = GridPlacementResolver.resolve(List.of( //
				col(GridLineValue.line(1), GridLineValue.line(3)), //
				col(GridLineValue.line(-2), GridLineValue.line(-1)), //
				col(GridLineValue.line(2), GridLineValue.span(2)), //
				col(GridLineValue.span(2), GridLineValue.line(4))), 3);
		assertTrue(result.toString(), result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 0, 2, 1), area(result, 0));
		assertEquals(new GridArea(2, 0, 1, 1), area(result, 1));
		assertEquals(new GridArea(1, 1, 2, 1), area(result, 2)); // 行0は0-1占有済み→sparseで行1
		assertEquals(new GridArea(1, 2, 2, 1), area(result, 3)); // span2/線4=列1..3、行2へ
	}

	/** 逆順は交換・同一線はspan 1・span/spanはend無視。 */
	public void testConflictNormalization() {
		final Result result = GridPlacementResolver.resolve(List.of( //
				col(GridLineValue.line(3), GridLineValue.line(1)), // 逆順→1/3
				col(GridLineValue.line(2), GridLineValue.line(2)), // 同一線→span1@col1
				col(GridLineValue.span(2), GridLineValue.span(3))), 3); // span/span→auto span2
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 0, 2, 1), area(result, 0));
		assertEquals(new GridArea(1, 1, 1, 1), area(result, 1)); // (1,0)占有→行1
		assertEquals(new GridArea(0, 2, 2, 1), area(result, 2)); // cursorは戻らない
	}

	/** sparse: cursorは戻らず、前行の穴を後続itemが埋めない。 */
	public void testSparseCursorNoBackfill() {
		final Result result = GridPlacementResolver.resolve(List.of( //
				col(GridLineValue.line(3), A), // 列2
				auto(), // cursor(0,3)→次行(1,0)
				auto()), 3);
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(2, 0, 1, 1), area(result, 0));
		assertEquals(new GridArea(0, 1, 1, 1), area(result, 1)); // 行0の穴(0,1)は埋めない
		assertEquals(new GridArea(1, 1, 1, 1), area(result, 2));
	}

	/** 明示行(正)・行内sparse・空行、両軸definiteの重複は許可。 */
	public void testExplicitRows() {
		final Result result = GridPlacementResolver.resolve(List.of( //
				GridItemSpec.of(GridLineValue.line(1), A, GridLineValue.line(3), A), // (0,2)
				GridItemSpec.of(GridLineValue.line(1), A, GridLineValue.line(3), A), // 重複→(0,2)
				GridItemSpec.of(A, A, GridLineValue.line(3), A), // 行definite列auto→(1,2)
				auto()), 2); // (0,0)
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 2, 1, 1), area(result, 0));
		assertEquals(new GridArea(0, 2, 1, 1), area(result, 1)); // 重複可
		assertEquals(new GridArea(1, 2, 1, 1), area(result, 2));
		assertEquals(new GridArea(0, 0, 1, 1), area(result, 3));
		assertEquals(3, ((Result.Resolved) result).plan().rowCount());
	}

	/** implicit columnが要る指定はUnsupported(clamp禁止)。 */
	public void testUnsupportedImplicitColumn() {
		assertTrue(GridPlacementResolver.resolve(List.of(col(GridLineValue.line(4), A)), 3) //
				instanceof Result.Unsupported); // 線4開始=explicit外
		assertTrue(GridPlacementResolver.resolve(List.of(col(GridLineValue.line(-1), GridLineValue.span(1))), 3) //
				instanceof Result.Unsupported); // -1/span1=末端の外側
		assertTrue(GridPlacementResolver.resolve(List.of(col(A, GridLineValue.span(4))), 3) //
				instanceof Result.Unsupported); // spanが列数超
		assertTrue(GridPlacementResolver.resolve(List.of(col(GridLineValue.line(-5), A)), 3) //
				instanceof Result.Unsupported); // explicit先頭より前
	}

	/** 負の行番号・巨大値はUnsupported。 */
	public void testUnsupportedRows() {
		assertTrue(GridPlacementResolver.resolve( //
				List.of(GridItemSpec.of(A, A, GridLineValue.line(-1), A)), 2) instanceof Result.Unsupported);
		assertTrue(GridPlacementResolver.resolve( //
				List.of(GridItemSpec.of(A, A, GridLineValue.line(2000000000), A)), 2) //
				instanceof Result.Unsupported);
		// span/lineの逆算で行頭より前
		assertTrue(GridPlacementResolver.resolve( //
				List.of(GridItemSpec.of(A, A, GridLineValue.span(3), GridLineValue.line(2))), 2) //
				instanceof Result.Unsupported);
	}
}
