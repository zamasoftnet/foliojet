package net.zamasoft.foliojet.layout.box.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.PageAtomicBox;
import net.zamasoft.foliojet.layout.box.RowSplitBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.RowSplitContainer;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Gridコンテナです(Grid G0、2026-07-31——
 * consult-codex-2026-07-31-grid.txt §3)。
 *
 * <p>
 * ページング上は正規のblock({@code BoxType.BLOCK}/{@code PosType.FLOW})の
 * まま——rescue・描画・フレーム処理を{@link FlowBlockBox}から継承し、
 * 既定では{@link PageAtomicBox}でページ軸の構造分割を型付きで禁じる
 * (入らなければ丸ごと送り→visual rescue)。
 * </p>
 *
 * <p>
 * <b>行分割(2026-08-10、G6)</b>: {@code GridBuilder.bind}により行境界
 * 情報が設定されている場合に限り、{@link #hasRowSplitLines()}がtrueに
 * なり、{@code PaginationContract}の特例({@link RowSplitBox})を通じて
 * {@link #split}が実際に呼ばれる——{@link FlexBox#split}(2026-08-07、
 * Bug C)をgridへ移植した2例目で、大元はテーブルの行契約
 * ({@code TableRowGroupBox}/{@code TableRowBox})。「収まる行は素通り、
 * 境界行で全itemを同一物理切断線へ揃えて強制分割、以降の行は丸ごと
 * 次断片へ」。帳簿が無い構成(rowSpan&gt;1、flow順が行優先でない明示
 * 配置、縦書き、align-contentの先頭余白あり)は従来通りatomicのまま
 * ——丸ごと送りかvisual rescueへ落ちる。min-height由来の余りは
 * align-content:stretch既定で行高へ分配済みのため、行分割がそのまま
 * 処理する(gigazine.netの先頭白紙ページの根治)。
 * </p>
 *
 * <p>
 * G0時点の内容配置は単一列の通常フロー(=FlowBlockBoxの挙動そのまま。
 * template=noneの意味論)。トラック解決とitem配置はG1以降で
 * {@code GridBuilder}が担う。
 * </p>
 */
public class GridBox extends FlowBlockBox implements PageAtomicBox, RowSplitBox {

	/**
	 * 1本のgrid行のページ軸帳簿です(2026-08-10、G6行分割)。
	 *
	 * <p>
	 * {@link FlexBox.Line}との違いは{@code start}を明示すること——gridは
	 * rowGap・空行(explicit-rows-sparse)・min-height分配で行の間隔が
	 * 一様でないため、累積和ではなく配置済みの行開始位置をそのまま運ぶ。
	 * </p>
	 *
	 * @param startFlow コンテナのflow一覧上でこの行の先頭itemが占める
	 *                   0基点の位置
	 * @param itemCount この行のitem数
	 * @param start     行のページ軸開始位置(コンテナ内辺原点、
	 *                   {@code GridBuilder.bind}の{@code rowStarts[r]})
	 * @param extent    行のページ軸寸法(align-content:stretchの分配込み)
	 * @param itemsEnd  行内のitem実端(行開始からの相対、align-selfの
	 *                   オフセット込みの最大)。min-height由来の分配等で
	 *                   {@code extent}がこれより大きいとき、差分は空白——
	 *                   切断線が空白内に落ちたらitemを切らず空白を切る
	 *                   (slack split)ための帳簿
	 */
	public record Row(int startFlow, int itemCount, double start, double extent, double itemsEnd) {
	}

	/**
	 * 行境界(行優先順、{@code GridBuilder.bind}が{@code addFlow}した順序と
	 * 一致)。nullまたは空は「行分割の対象外」を意味し、{@link #split}は
	 * 旧来のatomicフォールバックへ倒れる。
	 */
	private List<Row> rows;

	/**
	 * {@link #rows}の各行に属するitemの実体(コンテナのflow一覧と同じ
	 * 順序)。行内のitemを直接{@code split}するのに使う
	 * ({@code FlexBox.lineItems}と同じ理由)。
	 */
	private List<GridItemBox> rowItems;

	/**
	 * トラック配置({@code GridBuilder.bind})が実際に走ったか。走って
	 * いなければ中身は単一列の通常フロー(TwoPass不活性のG0退行)で、
	 * 守るべきトラック配置が無いため原子契約を主張しない
	 * ({@link #isPageAtomicNow})。
	 */
	private boolean trackLayout;

	public GridBox(final GridParams params, final FlowPos pos) {
		super(params, pos);
		// gridのitem配置(行方向トラック位置+行開始位置)は汎用のrestyle
		// 再構築(逐次積み上げ)で壊れるため、アンカーで復元するコンテナを
		// 使う(2026-08-10。FlexBoxが2026-08-08に同じ理由で導入したものの
		// 一般化——行分割の継続断片だけでなく、絶対配置子を含むgridの
		// ページ跨ぎ丸ごと移動でも同じ再構築経路を通る)
		this.container = new RowSplitContainer();
		this.container.setBox(this);
	}

	public final GridParams getGridParams() {
		return (GridParams) this.params;
	}

	protected GridBox(final GridParams params, final FlowPos pos,
			final net.zamasoft.foliojet.layout.box.params.Dimension size,
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize,
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame,
			final net.zamasoft.foliojet.layout.box.content.Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	/**
	 * 行境界情報を設定します({@code GridBuilder.bind}が配置直後に一度だけ
	 * 呼ぶ。分割時は継続断片の帳簿の付け替えにも使う)。
	 */
	public final void setGridRows(final List<Row> rows, final List<GridItemBox> rowItems) {
		this.rows = rows;
		this.rowItems = rowItems;
	}

	/**
	 * 行分割の対象か(2026-08-10)。falseなら{@code PaginationContract}が
	 * 従来のPageAtomicBox経路(丸ごと送り/visual rescue)を使う。
	 */
	public final boolean hasRowSplitLines() {
		return this.rows != null && !this.rows.isEmpty();
	}

	/** トラック配置が走ったことを記録します({@code GridBuilder.bind}が呼ぶ)。 */
	public final void markTrackLayout() {
		this.trackLayout = true;
	}

	/**
	 * 原子契約はトラック配置が実際に走ったgridだけが主張する
	 * (2026-08-10——設計判断は{@link PageAtomicBox#isPageAtomicNow}に集約)。
	 */
	@Override
	public final boolean isPageAtomicNow() {
		return this.trackLayout;
	}

	/**
	 * grid行のページ方向切断です(2026-08-10、G6)。
	 *
	 * <p>
	 * {@link FlexBox#split}と同じ形(「収まる行は素通り、境界行で全itemを
	 * 同一物理切断線へ揃えて強制分割、それ以降の行は丸ごと次断片へ」)。
	 * 境界の判定だけ累積和でなく{@link Row#start}の直接比較——rowGap・
	 * 空行・align-content分配で行間隔が一様でないため。切断線が最終行より
	 * 後(末尾余白内)に落ちた場合は、空の継続断片が余白だけを運ぶ。
	 * </p>
	 */
	public final SplitResult split(double pageLimit, final BreakMode mode, final byte flags) {
		if (!this.hasRowSplitLines()) {
			// 通常はPaginationContractの特例により、行境界が無ければこの
			// メソッド自体が呼ばれない(atomic経路へ回る)。防御的フォールバック
			return (flags & IPageBreakableBox.FLAGS_FIRST) != 0 ? SplitResult.KEEP : SplitResult.MOVE;
		}
		final WritingMode flow = this.getBlockParams().flow;
		pageLimit -= this.frame.getFramePageStart(flow);
		if (LayoutUtils.compare(pageLimit, 0) < 0) {
			return SplitResult.MOVE;
		}
		if (LayoutUtils.compare(pageLimit, this.getPageExtent(flow)) >= 0) {
			return SplitResult.KEEP;
		}
		if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0
				&& this.getBlockParams().pageBreakInside == PageBreakMode.AVOID) {
			return SplitResult.MOVE;
		}

		// 切断線を跨ぐ(crosses)か、切断線以降に始まる最初の行
		int boundary = -1;
		boolean crosses = false;
		for (int ri = 0; ri < this.rows.size(); ++ri) {
			final Row row = this.rows.get(ri);
			if (LayoutUtils.compare(pageLimit, row.start()) <= 0) {
				boundary = ri;
				break;
			}
			if (LayoutUtils.compare(pageLimit, row.start() + row.extent()) < 0) {
				boundary = ri;
				crosses = true;
				break;
			}
		}
		if (boundary < 0) {
			// 全行が切断線の手前に収まる(切断線は末尾余白内)——空の
			// 継続断片が残りの余白を運ぶ
			final RowSplitContainer cont = new RowSplitContainer();
			cont.anchorCurrent();
			final AbstractContainerBox continuation = this.splitPage(cont, pageLimit, false);
			if (continuation instanceof GridBox contGrid) {
				contGrid.markTrackLayout();
			}
			return new SplitResult.Split(continuation);
		}

		final byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
		final Row boundaryRow = this.rows.get(boundary);
		if (crosses) {
			final double remaining = pageLimit - boundaryRow.start();
			final GridItemBox[] boundaryItems = new GridItemBox[boundaryRow.itemCount()];
			for (int k = 0; k < boundaryItems.length; ++k) {
				boundaryItems[k] = this.rowItems.get(boundaryRow.startFlow() + k);
			}
			final SplitResult[] probed = new SplitResult[boundaryItems.length];
			boolean anySplit = (flags & IPageBreakableBox.FLAGS_SPLIT) != 0;
			if (!anySplit) {
				for (int k = 0; k < boundaryItems.length; ++k) {
					final SplitResult r = boundaryItems[k].split(remaining, mode, xflags);
					probed[k] = r;
					if (r instanceof SplitResult.Split) {
						anySplit = true;
					}
				}
			}
			if (!anySplit && LayoutUtils.compare(boundaryRow.itemsEnd(), remaining) <= 0) {
				// 境界行のitemは全て切断線の手前に収まり、はみ出しているのは
				// 行末尾の空白(min-height由来のalign-content:stretch分配等)
				// だけ——itemを切らず空白の中で切る(slack split)。
				// gigazine.netの「min-height:800pxのgridが小さな内容ごと
				// 次ページへ丸ごと沈む」形の根治点
				final RowSplitContainer cont = new RowSplitContainer();
				final List<Row> contRows = new ArrayList<>();
				final List<GridItemBox> contItems = new ArrayList<>();
				if (boundary + 1 < this.rows.size()) {
					final Row nextRow = this.rows.get(boundary + 1);
					((Container) this.container).migrateFlowsFrom(nextRow.startFlow(), cont, pageLimit);
					int shift = 0;
					for (int j = boundary + 1; j < this.rows.size(); ++j) {
						final Row old = this.rows.get(j);
						contRows.add(new Row(shift, old.itemCount(), old.start() - pageLimit, old.extent(),
								old.itemsEnd()));
						shift += old.itemCount();
					}
					contItems.addAll(this.rowItems.subList(nextRow.startFlow(), this.rowItems.size()));
				}
				cont.anchorCurrent();
				final AbstractContainerBox continuation = this.splitPage(cont, pageLimit, false);
				if (continuation instanceof GridBox contGrid) {
					contGrid.markTrackLayout();
					if (!contRows.isEmpty()) {
						contGrid.setGridRows(contRows, contItems);
					}
				}
				return new SplitResult.Split(continuation);
			}
			if (anySplit) {
				// 境界行: 未分割(Keep判定)だったitemも強制分割する
				final byte forcedFlags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
				final GridItemBox[] remainders = new GridItemBox[boundaryItems.length];
				// 分割前のitem高(下の残余下限の計算用)
				final double[] preExtents = new double[boundaryItems.length];
				for (int k = 0; k < boundaryItems.length; ++k) {
					preExtents[k] = boundaryItems[k].getPageExtent(flow);
				}
				for (int k = 0; k < boundaryItems.length; ++k) {
					final SplitResult r = probed[k] instanceof SplitResult.Split ? probed[k]
							: boundaryItems[k].split(remaining, mode, forcedFlags);
					if (!(r instanceof SplitResult.Split(final IPageBreakableBox remainder))
							|| !(remainder instanceof GridItemBox typedRemainder)) {
						throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
								"GridItemBox.split with FLAGS_SPLIT must return Split(GridItemBox) but was " + r);
					}
					remainders[k] = typedRemainder;
				}
				final RowSplitContainer cont = new RowSplitContainer();
				double newRowExtent = 0;
				for (int k = 0; k < remainders.length; ++k) {
					remainders[k].setGridLineOffset(boundaryItems[k].getGridLineOffset());
					cont.addFlow(remainders[k], 0);
					newRowExtent = Math.max(newRowExtent, remainders[k].getPageExtent(flow));
				}
				// **継続行の高さは残余の量を下回らせない**(2026-08-17)。
				// remainderはこの時点では未レイアウト(アンカー復元前)で、
				// getPageExtentがほぼ0を返しうる。それを帳簿に書くと、次の
				// splitの境界探索が「全行が切断線の手前に収まる」と誤読して
				// **空の継続断片**を返し、残余(1itemが複数ページぶんの
				// 文書では数万pt)が頭断片に積み残って紙外へ描かれる
				// (eLife論文で実測: 95ページぶんが3ページ目に積み上がった)。
				// 幾何学的に、残余は「元の行の高さ − このページで消費した量」
				// を下回らない。
				newRowExtent = Math.max(newRowExtent, boundaryRow.extent() - remaining);
				// さらに、item単位では「分割前のitem高 − 保持側の実測高」を
				// 下回らない(2026-08-18)。切断は不可分な内容(行・原子ブロック)を
				// 丸ごと残余へ送るため、保持側の実消費は利用可能量remainingより
				// 小さくなりうる——上の下限(extent−remaining)だけだと残余を
				// 過小記帳し、次ページで後続行が継続行の実内容に重なる
				// (smolcssで実測: 保持側が原子のデモ箱を送って~65pt早く終わり、
				// 次の記事の本文が前の記事のフッタに重なった)
				for (int k = 0; k < boundaryItems.length; ++k) {
					newRowExtent = Math.max(newRowExtent,
							preExtents[k] - boundaryItems[k].getPageExtent(flow));
				}
				final List<GridItemBox> contItems = new ArrayList<>(
						remainders.length + this.rowItems.size() - (boundaryRow.startFlow() + boundaryItems.length));
				for (final GridItemBox rem : remainders) {
					contItems.add(rem);
				}
				final List<Row> contRows = new ArrayList<>();
				contRows.add(new Row(0, boundaryItems.length, 0, newRowExtent, newRowExtent));
				if (boundary + 1 < this.rows.size()) {
					final Row nextRow = this.rows.get(boundary + 1);
					((Container) this.container).migrateFlowsFrom(nextRow.startFlow(), cont, pageLimit);
					int shift = boundaryItems.length;
					for (int j = boundary + 1; j < this.rows.size(); ++j) {
						final Row old = this.rows.get(j);
						contRows.add(new Row(shift, old.itemCount(), old.start() - pageLimit, old.extent(),
								old.itemsEnd()));
						shift += old.itemCount();
					}
					contItems.addAll(this.rowItems.subList(nextRow.startFlow(), this.rowItems.size()));
				}
				cont.anchorCurrent();
				final AbstractContainerBox continuation = this.splitPage(cont, pageLimit, false);
				if (continuation instanceof GridBox contGrid) {
					contGrid.markTrackLayout();
					contGrid.setGridRows(contRows, contItems);
				}
				return new SplitResult.Split(continuation);
			}
			// 境界行の誰も分割できない=行全体を境界とみなし、丸ごと持ち越す
		}
		if (boundary == 0) {
			return (flags & IPageBreakableBox.FLAGS_FIRST) != 0 ? SplitResult.KEEP : SplitResult.MOVE;
		}
		final double keptExtent = boundaryRow.start();
		final RowSplitContainer cont = new RowSplitContainer();
		((Container) this.container).migrateFlowsFrom(boundaryRow.startFlow(), cont, keptExtent);
		cont.anchorCurrent();
		final AbstractContainerBox continuation = this.splitPage(cont, keptExtent, false);
		if (continuation instanceof GridBox contGrid) {
			contGrid.markTrackLayout();
			contGrid.setGridRows(shiftRows(this.rows, boundary, keptExtent),
					new ArrayList<>(this.rowItems.subList(boundaryRow.startFlow(), this.rowItems.size())));
		}
		return new SplitResult.Split(continuation);
	}

	/**
	 * {@code rows}の{@code fromIndex}行目以降を、flow位置と行開始位置を
	 * 0基点へ付け替えたリストにします({@link #split}が行を丸ごと次断片へ
	 * 持ち越す際に使う)。
	 */
	private static List<Row> shiftRows(final List<Row> rows, final int fromIndex, final double keptExtent) {
		final List<Row> result = new ArrayList<>(rows.size() - fromIndex);
		final int flowShift = rows.get(fromIndex).startFlow();
		for (int j = fromIndex; j < rows.size(); ++j) {
			final Row old = rows.get(j);
			result.add(new Row(old.startFlow() - flowShift, old.itemCount(), old.start() - keptExtent, old.extent(),
					old.itemsEnd()));
		}
		return result;
	}

	/**
	 * <b>継続断片も同じ種別で作る</b>(2026-08-05)。
	 *
	 * <p>
	 * {@link FlowBlockBox#fragmentRecipe()} は {@code new FlowBlockBox(...)} を
	 * 直に書いているので、<b>上書きしないと継続断片が素のブロックになる</b>。
	 * {@code ContinuationValidator} が種別の食い違いを検出して
	 * <b>変換全体を止める</b>——実地コーパス第23波の {@code ecma262}
	 * (ECMAScript仕様書、7.5MBの単一ページ)がこれで、出力2.9MBの途中で
	 * 落ちていた。{@code MulticolumnBlockBox} だけが上書きしていた。
	 * </p>
	 */
	@Override
	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final net.zamasoft.foliojet.layout.box.params.GridParams params = this.getGridParams();
		final FlowPos pos = this.getFlowPos();
		return (state, container) -> new GridBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
