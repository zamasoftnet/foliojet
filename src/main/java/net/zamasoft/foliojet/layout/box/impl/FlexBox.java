package net.zamasoft.foliojet.layout.box.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.PageAtomicBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Flexコンテナです(Flex F0b、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt。{@link GridBox}と同型)。
 *
 * <p>
 * ページング上は正規のblock({@code BoxType.BLOCK}/{@code PosType.FLOW})の
 * まま——rescue・描画・フレーム処理を{@link FlowBlockBox}から継承し、
 * 既定では{@link PageAtomicBox}でページ軸の構造分割を型付きで禁じる
 * (css-flexbox-1 §10の断片化はinformativeのため非対応が正当。
 * 入らなければ丸ごと送り→visual rescue)。
 * </p>
 *
 * <p>
 * <b>行分割(2026-08-07、Bug C)</b>: {@code flex-direction:row}(主軸=行
 * 方向)で{@link #setFlexLines}によりline境界情報が設定されている場合に
 * 限り、{@link #hasRowSplitLines()}がtrueになり、
 * {@code PaginationContract}の特例(同ファイル参照)を通じて
 * {@link #split}が実際に呼ばれる——テーブルの行が「同一物理切断線に
 * 揃えて強制分割し、揃わないitemは丸ごと次断片へ」という契約
 * ({@code TableRowGroupBox}/{@code TableRowBox}参照)を、rowspanの無い
 * 単純化版として適用する。line境界が無ければ(column-direction、
 * またはF4c/F4dの対象外構成)従来通りPageAtomicBoxのまま——丸ごと送りか
 * visual rescueへ落ちる。この使い分けは意図的で、column-directionの
 * nowrapは元々「itemが縦に積まれるだけ」でCSS的に正しい挙動のため、
 * わざわざ行分割機構を適用する理由が無い(調査時の結論)。
 * </p>
 *
 * <p>
 * F0時点の内容配置は単一列の通常フロー(=FlowBlockBoxの挙動そのまま)。
 * 行分割・伸縮・整列はF1以降で{@code FlexBuilder}が担う。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexBox extends FlowBlockBox implements PageAtomicBox {

	/**
	 * 1本のflex行(line)のページ軸帳簿です(2026-08-07、Bug C)。
	 *
	 * @param startFlow コンテナのflow一覧上でこの行の先頭itemが占める
	 *                   0基点の位置(=直前までの行のitemCountの累計)
	 * @param itemCount この行のitem数
	 * @param pageSize  この行のページ軸(cross軸、row-directionでは
	 *                   縦方向)の確定寸法——{@code FlexBuilder.placeRow}の
	 *                   {@code lineExtents[li]}と同じ値
	 */
	public record Line(int startFlow, int itemCount, double pageSize) {
	}

	/**
	 * 行境界(視覚順=cross軸順、{@code FlexBuilder.placeRow}が
	 * {@code addFlow}した順序と一致)。nullまたは空は「行分割の対象外」を
	 * 意味し、{@link #split}は旧来のatomicフォールバックへ倒れる。
	 */
	private List<Line> lines;

	/**
	 * {@link #lines}の各行に属するitemの実体(コンテナのflow一覧と同じ
	 * 順序)。line内のitemを直接{@code split}するのに使う——container
	 * 側はflowの入れ替え(移送)しか提供しないため(container越しに個々の
	 * itemを取り出す汎用APIが無く、Flex専用にこちらで並行して持つ方が
	 * 単純)。
	 */
	private List<FlexItemBox> lineItems;

	public FlexBox(final FlexParams params, final FlowPos pos) {
		super(params, pos);
	}

	public final FlexParams getFlexParams() {
		return (FlexParams) this.params;
	}

	protected FlexBox(final FlexParams params, final FlowPos pos,
			final net.zamasoft.foliojet.layout.box.params.Dimension size,
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize,
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame,
			final net.zamasoft.foliojet.layout.box.content.Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	/**
	 * 行境界情報を設定します({@code FlexBuilder.placeRow}が配置直後に
	 * 一度だけ呼ぶ)。
	 */
	public final void setFlexLines(final List<Line> lines, final List<FlexItemBox> lineItems) {
		this.lines = lines;
		this.lineItems = lineItems;
	}

	/**
	 * 行分割の対象か(2026-08-07)。falseなら{@code PaginationContract}が
	 * 従来のPageAtomicBox経路(丸ごと送り/visual rescue)を使う。
	 */
	public final boolean hasRowSplitLines() {
		return this.lines != null && !this.lines.isEmpty();
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
		final FlexParams params = this.getFlexParams();
		final FlowPos pos = this.getFlowPos();
		return (state, container) -> new FlexBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}

	/**
	 * flex行のページ方向切断です(2026-08-07、Bug C)。
	 *
	 * <p>
	 * {@code TableRowGroupBox.split}/{@code TableRowBox.split}と同じ形
	 * (「収まる行は素通り、境界行で全itemを同一物理線へ揃えて強制分割、
	 * それ以降の行は丸ごと次断片へ」)だが、rowspan(セル連結)が無い分
	 * 大幅に単純——境界行のitemは{@link #lineItems}から直接取り出して
	 * 分割するだけで、連結セルのような「切断線を遡って再計算する」
	 * 処理が要らない。
	 * </p>
	 *
	 * <p>
	 * cross軸位置(どの行に属すか)はitem自身ではなく{@code Container.Flow}
	 * 側の帳簿なので、収まる行のitemは一切触らない(元のcontainerに
	 * 残ったまま)。強制分割で生まれた継続itemは{@code fragmentRecipe}が
	 * 新規生成するため主軸位置({@code baseOffsetX}/{@code Y})を引き継が
	 * ない——{@link FlexItemBox#getFlexLineOffset}で元の値を読み、
	 * {@link FlexItemBox#setFlexLineOffset}で明示的に復元する。
	 * </p>
	 */
	public final SplitResult split(double pageLimit, final BreakMode mode, final byte flags) {
		// TableRowBox.splitと違い、ここはBoxType.BLOCKの汎用経路
		// (FlowContainer.splitPageAxisのcase BLOCK)を通常のブロックと共有する
		// ため、FLAGS_LASTを伴う呼び出しが実際に起こりうる(2026-08-07、
		// RandomDocumentFuzzTestの列組みで実測: AssertionErrorで変換全体が
		// 落ちた)。table行がFLAGS_LASTを見ないのはTableRowGroupBoxが
		// 呼び出し前に除いているという発呼側の契約であって、split自身の
		// 制約ではない——下のxflags構築が元々FLAGS_LASTを運ばないため、
		// 検査を外すだけで安全に扱える
		if (!this.hasRowSplitLines()) {
			// 通常はPaginationContractの特例により、line境界が無ければこの
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

		final double totalPageLimit = pageLimit;
		double remaining = pageLimit;
		int boundary = this.lines.size() - 1;
		for (int li = 0; li < this.lines.size(); ++li) {
			final Line line = this.lines.get(li);
			if (li < this.lines.size() - 1 && LayoutUtils.compare(remaining, line.pageSize()) > 0) {
				remaining -= line.pageSize();
				continue;
			}
			boundary = li;
			break;
		}

		final byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
		final Line boundaryLine = this.lines.get(boundary);
		final FlexItemBox[] boundaryItems = new FlexItemBox[boundaryLine.itemCount()];
		for (int k = 0; k < boundaryItems.length; ++k) {
			boundaryItems[k] = this.lineItems.get(boundaryLine.startFlow() + k);
		}

		SplitResult[] probed = new SplitResult[boundaryItems.length];
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

		if (!anySplit) {
			// 境界行の誰も分割できない=行全体を境界とみなし、丸ごと持ち越す
			if (boundary == 0) {
				return (flags & IPageBreakableBox.FLAGS_FIRST) != 0 ? SplitResult.KEEP : SplitResult.MOVE;
			}
			final double keptExtent = totalPageLimit - remaining;
			final net.zamasoft.foliojet.layout.box.content.FlexRowContainer cont = new net.zamasoft.foliojet.layout.box.content.FlexRowContainer();
			((Container) this.container).migrateFlowsFrom(boundaryLine.startFlow(), cont, keptExtent);
			cont.anchorCurrent();
			final AbstractContainerBox continuation = this.splitPage(cont, keptExtent, false);
			if (continuation instanceof FlexBox contFlex) {
				contFlex.setFlexLines(shiftLines(this.lines, boundary),
						this.lineItems.subList(boundaryLine.startFlow(), this.lineItems.size()));
			}
			return new SplitResult.Split(continuation);
		}

		// 境界行:未分割(Keep判定)だったitemも強制分割する
		final byte forcedFlags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
		final FlexItemBox[] remainders = new FlexItemBox[boundaryItems.length];
		for (int k = 0; k < boundaryItems.length; ++k) {
			final SplitResult r = probed[k] instanceof SplitResult.Split ? probed[k]
					: boundaryItems[k].split(remaining, mode, forcedFlags);
			if (!(r instanceof SplitResult.Split(final IPageBreakableBox remainder))
					|| !(remainder instanceof FlexItemBox typedRemainder)) {
				throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
						"FlexItemBox.split with FLAGS_SPLIT must return Split(FlexItemBox) but was " + r);
			}
			remainders[k] = typedRemainder;
		}

		final net.zamasoft.foliojet.layout.box.content.FlexRowContainer cont = new net.zamasoft.foliojet.layout.box.content.FlexRowContainer();
		final boolean vertical = flow.isVertical();
		double newLinePageSize = 0;
		for (int k = 0; k < remainders.length; ++k) {
			remainders[k].setFlexLineOffset(boundaryItems[k].getFlexLineOffset(vertical), vertical);
			cont.addFlow(remainders[k], 0);
			newLinePageSize = Math.max(newLinePageSize, remainders[k].getPageExtent(flow));
		}
		final List<FlexItemBox> contItems = new ArrayList<>(remainders.length
				+ (this.lineItems.size() - (boundaryLine.startFlow() + boundaryItems.length)));
		for (final FlexItemBox rem : remainders) {
			contItems.add(rem);
		}
		final List<Line> contLines = new ArrayList<>();
		contLines.add(new Line(0, boundaryItems.length, newLinePageSize));

		if (boundary + 1 < this.lines.size()) {
			final Line nextLine = this.lines.get(boundary + 1);
			((Container) this.container).migrateFlowsFrom(nextLine.startFlow(), cont, totalPageLimit);
			int shift = boundaryItems.length;
			for (int j = boundary + 1; j < this.lines.size(); ++j) {
				final Line old = this.lines.get(j);
				contLines.add(new Line(shift, old.itemCount(), old.pageSize()));
				shift += old.itemCount();
			}
			contItems.addAll(this.lineItems.subList(nextLine.startFlow(), this.lineItems.size()));
		}

		cont.anchorCurrent();
		final AbstractContainerBox continuation = this.splitPage(cont, totalPageLimit, false);
		if (continuation instanceof FlexBox contFlex) {
			contFlex.setFlexLines(contLines, contItems);
		}
		return new SplitResult.Split(continuation);
	}

	/**
	 * {@code lines}の{@code fromIndex}行目以降を、先頭を0基点として
	 * 付け替えたリストにします({@link #split}が行全体を丸ごと次断片へ
	 * 持ち越す際に使う)。
	 */
	private static List<Line> shiftLines(final List<Line> lines, final int fromIndex) {
		final List<Line> result = new ArrayList<>(lines.size() - fromIndex);
		int shift = 0;
		for (int j = fromIndex; j < lines.size(); ++j) {
			final Line old = lines.get(j);
			result.add(new Line(shift, old.itemCount(), old.pageSize()));
			shift += old.itemCount();
		}
		return result;
	}
}
