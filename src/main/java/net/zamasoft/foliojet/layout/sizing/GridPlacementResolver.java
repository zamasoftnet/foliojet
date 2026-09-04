package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;

/**
 * Grid itemの配置解決です(Grid G4a、2026-07-31——
 * consult-codex-2026-07-31-grid-g4.txt Q2/Q3)。boxに依存しない純粋計算。
 * 明示線番号(正負)・span・autoの混在をCSS Grid §8.3.1(競合の正規化)+
 * §8.5(auto-placement)のサブセットで解決する。
 *
 * <p>
 * 2026-08-29の拡張: {@code grid-auto-flow}の{@code column}
 * (列方向カーソル——行を埋めてから次の列へ。必要な列は暗黙に増える)
 * と{@code dense}(各itemの探索をグリッド先頭から始める)、
 * {@code grid-template-rows}/{@code grid-template-areas}による明示行数
 * (負の行番号の基準)。線名は{@link GridLineNameResolver}で数値化済みの
 * ものを受け取る。
 * </p>
 *
 * <p>
 * fail closed(答申Q5): 未対応指定(行フローでexplicit columnの外へ出る
 * 線・span、上限超過等)は例外ではなく{@link Result.Unsupported}を返す
 * ——<b>1件だけauto化してはならない</b>(occupancyとcursorを通じて後続全
 * itemへ伝播する)。呼び出し側はcontainer単位でsource-order配置
 * (G3: col=i%n、row=i/n)へ戻す。行フローの暗黙列は呼び出し側
 * ({@code GridBuilder})が事前にトラックを足して{@code columnCount}へ
 * 含める。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridPlacementResolver {

	/** 行・列・spanの資源防御上限(repeat展開上限と同じ)。 */
	public static final int LIMIT = 4096;

	private GridPlacementResolver() {
		// static
	}

	/** item 1件の確定area(zero-based track index)。 */
	public record GridArea(int column, int row, int columnSpan, int rowSpan) {
	}

	/** 配置結果(source order)。 */
	public record Plan(List<GridArea> areas, int columnCount, int rowCount) {
	}

	/** 解決結果です。Unsupportedは例外にしない(bind前のフォールバック用)。 */
	public sealed interface Result {
		record Resolved(Plan plan) implements Result {
		}

		record Unsupported(int itemIndex, Reason reason) implements Result {
		}
	}

	/** 未対応理由(観測・テスト用)。 */
	public enum Reason {
		/** explicit gridの外の線・span(implicit columnが必要)。 */
		NEEDS_IMPLICIT_COLUMN,
		/** 行・列・spanの上限超過。 */
		LIMIT_EXCEEDED,
		/** 負の行番号(明示行が無いGridではサブセット外)。 */
		NEGATIVE_ROW
	}

	/** 1軸の正規化結果(zero-based開始track。nullはauto)。 */
	private record AxisPlacement(Integer definiteStart, int span) {
	}

	/**
	 * 全itemの配置を解決します(行フロー・sparse——従来互換)。
	 *
	 * @param items       source-orderの各item指定
	 * @param columnCount explicit列数(正)
	 * @return 解決結果
	 */
	public static Result resolve(final List<GridItemSpec> items, final int columnCount) {
		return resolve(items, columnCount, 0, false, false);
	}

	/**
	 * 全itemの配置を解決します(2026-08-29)。
	 *
	 * @param items        source-orderの各item指定(線名は数値化済み)
	 * @param columnCount  explicit列数(正。行フローの暗黙列は含めて渡す)
	 * @param explicitRows explicit行数(0なら明示行なし=負の行番号は不可)
	 * @param columnFlow   {@code grid-auto-flow: column}か
	 * @param dense        {@code dense}か
	 * @return 解決結果(列フローでは{@code Plan.columnCount}が使った列数)
	 */
	public static Result resolve(final List<GridItemSpec> items, final int columnCount, final int explicitRows,
			final boolean columnFlow, final boolean dense) {
		return resolve(items, columnCount, explicitRows, columnFlow, dense, null);
	}

	/**
	 * 行軸を固定範囲へ制限するsubgrid用の配置です(2026-09-03)。通常どおり
	 * 仮想implicit行へ配置した後、完成したareaだけを{@code boundRows}行へ
	 * clampします。
	 *
	 * @param boundRows 返却する行数(1以上)
	 */
	public static Result resolve(final List<GridItemSpec> items, final int columnCount, final int explicitRows,
			final boolean columnFlow, final boolean dense, final int boundRows) {
		if (boundRows <= 0 || boundRows > LIMIT) {
			throw new IllegalArgumentException("boundRows: " + boundRows);
		}
		return resolve(items, columnCount, explicitRows, columnFlow, dense, Integer.valueOf(boundRows));
	}

	private static Result resolve(final List<GridItemSpec> items, final int columnCount, final int explicitRows,
			final boolean columnFlow, final boolean dense, final Integer boundRows) {
		final int n = columnCount;
		final AxisPlacement[] cols = new AxisPlacement[items.size()];
		final AxisPlacement[] rows = new AxisPlacement[items.size()];
		for (int i = 0; i < items.size(); ++i) {
			final GridItemSpec spec = items.get(i);
			final Object col = normalizeAxis(spec.columnStart(), spec.columnEnd(), n);
			if (col instanceof Reason reason) {
				return new Result.Unsupported(i, reason);
			}
			cols[i] = (AxisPlacement) col;
			// 行: 明示行があれば負番号はその末端基準、無ければ不可
			final Object row = normalizeAxis(spec.rowStart(), spec.rowEnd(), explicitRows > 0 ? explicitRows : -1);
			if (row instanceof Reason reason) {
				return new Result.Unsupported(i, reason);
			}
			rows[i] = (AxisPlacement) row;
			final AxisPlacement c = cols[i];
			if (c.definiteStart != null && c.definiteStart < 0) {
				return new Result.Unsupported(i, Reason.NEEDS_IMPLICIT_COLUMN);
			}
			if (!columnFlow && (c.span > n || (c.definiteStart != null && c.definiteStart + c.span > n))) {
				// 列の範囲検証(implicit columnは呼び出し側が事前に足す——答申Q5: clamp禁止)
				return new Result.Unsupported(i, Reason.NEEDS_IMPLICIT_COLUMN);
			}
			if (c.span > LIMIT || (c.definiteStart != null && c.definiteStart + c.span > LIMIT)) {
				return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
			}
			final AxisPlacement r = rows[i];
			if (boundRows == null && r.definiteStart != null && r.definiteStart < 0) {
				// span/lineの逆算等で行頭より前へ出た(implicit先頭行は未対応)
				return new Result.Unsupported(i, Reason.NEGATIVE_ROW);
			}
			if (r.span > LIMIT || (r.definiteStart != null && r.definiteStart + r.span > LIMIT)) {
				return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
			}
		}

		final GridArea[] areas = new GridArea[items.size()];
		final Map<Integer, BitSet> occupancy = new HashMap<>();

		// (1) 両軸definite: そのまま配置(重複は許可、source order描画)
		for (int i = 0; i < items.size(); ++i) {
			if (cols[i].definiteStart != null && rows[i].definiteStart != null) {
				areas[i] = new GridArea(cols[i].definiteStart, rows[i].definiteStart, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
			}
		}
		if (columnFlow) {
			final Result result = resolveColumnFlow(items.size(), cols, rows, areas, occupancy, n, explicitRows, dense);
			return boundRows == null ? result : clampRows(result, boundRows);
		}
		// (2) 行definite・列auto: 指定行内のsparse cursor(行ごとに前進のみ。denseは常に先頭から)
		final Map<Integer, Integer> rowCursor = new HashMap<>();
		for (int i = 0; i < items.size(); ++i) {
			if (cols[i].definiteStart == null && rows[i].definiteStart != null) {
				final int row = rows[i].definiteStart;
				int col = dense ? 0 : rowCursor.getOrDefault(row, 0);
				while (col + cols[i].span <= n && occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
					++col;
				}
				if (col + cols[i].span > n) {
					// 行内に空きがない——implicit columnは作らない
					return new Result.Unsupported(i, Reason.NEEDS_IMPLICIT_COLUMN);
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				rowCursor.put(row, col + cols[i].span);
			}
		}
		// (3)(4) 残り(列definite・行auto/両軸auto)をsource orderで
		// auto-placement cursor(sparse: 戻らない。dense: 毎回先頭から)により配置
		int curRow = 0, curCol = 0;
		for (int i = 0; i < items.size(); ++i) {
			if (areas[i] != null) {
				continue;
			}
			if (dense) {
				curRow = 0;
				curCol = 0;
			}
			if (cols[i].definiteStart != null) {
				// 列definite: cursor列より戻るなら次行へ
				final int col = cols[i].definiteStart;
				if (col < curCol) {
					++curRow;
				}
				int row = curRow;
				while (occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
					++row;
					if (row + rows[i].span > LIMIT) {
						return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
					}
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				curRow = row;
				curCol = col + cols[i].span;
			} else {
				// 両軸auto: cursorから前方の空き矩形を探す
				int row = curRow, col = curCol;
				while (true) {
					if (col + cols[i].span > n) {
						++row;
						col = 0;
						if (row + rows[i].span > LIMIT) {
							return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
						}
						continue;
					}
					if (!occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
						break;
					}
					++col;
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				curRow = row;
				curCol = col + cols[i].span;
			}
		}

		int rowCount = 0;
		for (final GridArea area : areas) {
			rowCount = Math.max(rowCount, area.row() + area.rowSpan());
		}
		final Result result = new Result.Resolved(new Plan(List.of(areas), n, Math.max(rowCount, explicitRows)));
		return boundRows == null ? result : clampRows(result, boundRows);
	}

	/** 配置後のareaをbounded row axisへclampします。 */
	private static Result clampRows(final Result result, final int boundRows) {
		if (!(result instanceof Result.Resolved resolved)) {
			return result;
		}
		final List<GridArea> clamped = new ArrayList<>(resolved.plan().areas().size());
		for (final GridArea area : resolved.plan().areas()) {
			final int start = Math.max(0, Math.min(boundRows, area.row()));
			final int end = Math.max(0, Math.min(boundRows, area.row() + area.rowSpan()));
			if (start >= end) {
				clamped.add(new GridArea(area.column(), boundRows - 1, area.columnSpan(), 1));
			} else {
				clamped.add(new GridArea(area.column(), start, area.columnSpan(), end - start));
			}
		}
		return new Result.Resolved(new Plan(List.copyOf(clamped), resolved.plan().columnCount(), boundRows));
	}

	/**
	 * 列フロー({@code grid-auto-flow: column})の自動配置です(2026-08-29、
	 * css-grid-1 §8.5を列方向に読み替え)。行数は明示行数と確定配置の
	 * 行末端の大きいほう(最低1)で固定し、列は必要なだけ暗黙に増える
	 * ({@code Plan.columnCount}=使った列数)。
	 */
	private static Result resolveColumnFlow(final int count, final AxisPlacement[] cols,
			final AxisPlacement[] rows, final GridArea[] areas, final Map<Integer, BitSet> occupancy, final int n,
			final int explicitRows, final boolean dense) {
		int rowCount = Math.max(1, explicitRows);
		for (final GridArea area : areas) {
			if (area != null) {
				rowCount = Math.max(rowCount, area.row() + area.rowSpan());
			}
		}
		// (2') 列definite・行auto: 指定列内のカーソル(行方向へ前進。足りなければ暗黙行)
		final Map<Integer, Integer> colCursor = new HashMap<>();
		for (int i = 0; i < count; ++i) {
			if (cols[i].definiteStart != null && rows[i].definiteStart == null) {
				final int col = cols[i].definiteStart;
				int row = dense ? 0 : colCursor.getOrDefault(col, 0);
				while (occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
					++row;
					if (row + rows[i].span > LIMIT) {
						return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
					}
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				colCursor.put(col, row + rows[i].span);
				rowCount = Math.max(rowCount, row + rows[i].span);
			}
		}
		// (3')(4') 残り(行definite・列auto/両軸auto)を列方向カーソルで配置
		int curRow = 0, curCol = 0, usedColumns = n;
		for (int i = 0; i < count; ++i) {
			if (areas[i] != null) {
				usedColumns = Math.max(usedColumns, areas[i].column() + areas[i].columnSpan());
				continue;
			}
			if (dense) {
				curRow = 0;
				curCol = 0;
			}
			if (rows[i].definiteStart != null) {
				// 行definite: cursor行より戻るなら次列へ
				final int row = rows[i].definiteStart;
				if (row < curRow) {
					++curCol;
				}
				int col = curCol;
				while (occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
					++col;
					if (col + cols[i].span > LIMIT) {
						return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
					}
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				curCol = col;
				curRow = row + rows[i].span;
			} else {
				int row = curRow, col = curCol;
				while (true) {
					if (row + rows[i].span > rowCount) {
						++col;
						row = 0;
						if (col + cols[i].span > LIMIT) {
							return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
						}
						continue;
					}
					if (!occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
						break;
					}
					++row;
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				curCol = col;
				curRow = row + rows[i].span;
			}
			usedColumns = Math.max(usedColumns, areas[i].column() + areas[i].columnSpan());
		}
		return new Result.Resolved(new Plan(List.of(areas), usedColumns, rowCount));
	}

	/**
	 * 1軸の正規化です(CSS Grid §8.3.1の競合処理)。戻り値は
	 * {@link AxisPlacement}または{@link Reason}(未対応)。
	 *
	 * @param explicitTracks 負番号の基準となるexplicit track数。負なら
	 *                       この軸は負番号未対応(行——{@link Reason#NEGATIVE_ROW})
	 */
	private static Object normalizeAxis(final GridLineValue start, final GridLineValue end,
			final int explicitTracks) {
		final Integer startLine = lineIndex(start, explicitTracks);
		final Integer endLine = lineIndex(end, explicitTracks);
		if (startLine != null && startLine == Integer.MIN_VALUE || endLine != null && endLine == Integer.MIN_VALUE) {
			return Reason.NEGATIVE_ROW; // 番兵——行軸の負番号(サブセット外)
		}
		if (startLine != null && startLine > LIMIT || endLine != null && endLine > LIMIT) {
			return Reason.LIMIT_EXCEEDED;
		}
		if (startLine != null && endLine != null) {
			// line / line: 逆順は交換、同一線はend除去でspan 1
			int a = startLine, b = endLine;
			if (a == b) {
				return new AxisPlacement(a, 1);
			}
			if (a > b) {
				final int t = a;
				a = b;
				b = t;
			}
			return new AxisPlacement(a, b - a);
		}
		if (startLine != null) {
			if (end.isSpan()) {
				return new AxisPlacement(startLine, Math.min(LIMIT, end.getNumber()));
			}
			return new AxisPlacement(startLine, 1); // line / auto
		}
		if (endLine != null) {
			if (start.isSpan()) {
				final int span = Math.min(LIMIT, start.getNumber());
				return new AxisPlacement(endLine - span, span); // span / line: 逆算
			}
			return new AxisPlacement(endLine - 1, 1); // auto / line
		}
		// 両方auto/span——span/spanはend側を無視(§8.3.1)
		final int span = start.isSpan() ? Math.min(LIMIT, start.getNumber())
				: end.isSpan() ? Math.min(LIMIT, end.getNumber()) : 1;
		return new AxisPlacement(null, span);
	}

	/**
	 * 線番号のzero-based線indexです(auto/span/未解決の線名はnull)。負番号は
	 * explicit末端基準(-1→N)。{@code explicitTracks<0}の軸(行)で
	 * 負番号なら{@code MIN_VALUE}(番兵——呼び出し側がReasonへ変換)。
	 */
	private static Integer lineIndex(final GridLineValue value, final int explicitTracks) {
		if (value.isAuto() || value.isSpan() || value.isNamed()) {
			return null;
		}
		final int number = value.getNumber();
		if (number > 0) {
			return number - 1;
		}
		if (explicitTracks < 0) {
			return Integer.MIN_VALUE;
		}
		// -K → 1-based線N+2-K → zero-based N+1-K(-1=末端線N、-2=N-1)
		return explicitTracks + 1 + number;
	}

	private static void mark(final Map<Integer, BitSet> occupancy, final GridArea area) {
		for (int r = area.row(); r < area.row() + area.rowSpan(); ++r) {
			occupancy.computeIfAbsent(r, key -> new BitSet()).set(area.column(), area.column() + area.columnSpan());
		}
	}

	private static boolean occupied(final Map<Integer, BitSet> occupancy, final int row, final int rowSpan, final int col,
			final int colSpan) {
		for (int r = row; r < row + rowSpan; ++r) {
			final BitSet bits = occupancy.get(r);
			if (bits == null) {
				continue;
			}
			final int next = bits.nextSetBit(col);
			if (next >= 0 && next < col + colSpan) {
				return true;
			}
		}
		return false;
	}
}
