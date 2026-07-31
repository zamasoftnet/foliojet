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
 * §8.5(auto-placement、既定のsparseのみ——grid-auto-flow未実装のため
 * denseは実装しない)のサブセットで解決する。
 *
 * <p>
 * fail closed(答申Q5): 未対応指定(implicit columnが要る線・span、
 * 上限超過等)は例外ではなく{@link Result.Unsupported}を返す——
 * <b>1件だけauto化してはならない</b>(occupancyとcursorを通じて後続全
 * itemへ伝播する)。呼び出し側はcontainer単位でsource-order配置
 * (G3: col=i%n、row=i/n)へ戻す。
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
		/** 負の行番号(G4サブセット外)。 */
		NEGATIVE_ROW
	}

	/** 1軸の正規化結果(zero-based開始track。nullはauto)。 */
	private record AxisPlacement(Integer definiteStart, int span) {
	}

	/**
	 * 全itemの配置を解決します。
	 *
	 * @param items       source-orderの各item指定
	 * @param columnCount explicit列数(正)
	 * @return 解決結果
	 */
	public static Result resolve(final List<GridItemSpec> items, final int columnCount) {
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
			// 行はexplicit track数0(implicit auto rows)。負番号は不可
			final Object row = normalizeAxis(spec.rowStart(), spec.rowEnd(), -1);
			if (row instanceof Reason reason) {
				return new Result.Unsupported(i, reason);
			}
			rows[i] = (AxisPlacement) row;
			// 列の範囲検証(implicit column未対応——答申Q5: clamp禁止)
			final AxisPlacement c = cols[i];
			if (c.span > n || (c.definiteStart != null && (c.definiteStart < 0 || c.definiteStart + c.span > n))) {
				return new Result.Unsupported(i, Reason.NEEDS_IMPLICIT_COLUMN);
			}
			final AxisPlacement r = rows[i];
			if (r.definiteStart != null && r.definiteStart < 0) {
				// span/lineの逆算等で行頭より前へ出た(implicit先頭行は未対応)
				return new Result.Unsupported(i, Reason.NEGATIVE_ROW);
			}
			if (r.span > LIMIT || (r.definiteStart != null && r.definiteStart + r.span > LIMIT)) {
				return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
			}
		}

		final GridArea[] areas = new GridArea[items.size()];
		final List<BitSet> occupancy = new ArrayList<>();

		// (1) 両軸definite: そのまま配置(重複は許可、source order描画)
		for (int i = 0; i < items.size(); ++i) {
			if (cols[i].definiteStart != null && rows[i].definiteStart != null) {
				areas[i] = new GridArea(cols[i].definiteStart, rows[i].definiteStart, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
			}
		}
		// (2) 行definite・列auto: 指定行内のsparse cursor(行ごとに前進のみ)
		final Map<Integer, Integer> rowCursor = new HashMap<>();
		for (int i = 0; i < items.size(); ++i) {
			if (cols[i].definiteStart == null && rows[i].definiteStart != null) {
				final int row = rows[i].definiteStart;
				int col = rowCursor.getOrDefault(row, 0);
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
		// auto-placement cursor(sparse: 戻らない)により配置
		int curRow = 0, curCol = 0;
		for (int i = 0; i < items.size(); ++i) {
			if (areas[i] != null) {
				continue;
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
		return new Result.Resolved(new Plan(List.of(areas), n, rowCount));
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
	 * 線番号のzero-based線indexです(auto/spanはnull)。負番号は
	 * explicit末端基準(-1→N)。{@code explicitTracks<0}の軸(行)で
	 * 負番号なら{@code MIN_VALUE}(番兵——呼び出し側がReasonへ変換)。
	 */
	private static Integer lineIndex(final GridLineValue value, final int explicitTracks) {
		if (value.isAuto() || value.isSpan()) {
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

	private static void mark(final List<BitSet> occupancy, final GridArea area) {
		for (int r = area.row(); r < area.row() + area.rowSpan(); ++r) {
			while (occupancy.size() <= r) {
				occupancy.add(new BitSet());
			}
			occupancy.get(r).set(area.column(), area.column() + area.columnSpan());
		}
	}

	private static boolean occupied(final List<BitSet> occupancy, final int row, final int rowSpan, final int col,
			final int colSpan) {
		for (int r = row; r < row + rowSpan; ++r) {
			if (r >= occupancy.size()) {
				continue;
			}
			final BitSet bits = occupancy.get(r);
			final int next = bits.nextSetBit(col);
			if (next >= 0 && next < col + colSpan) {
				return true;
			}
		}
		return false;
	}
}
