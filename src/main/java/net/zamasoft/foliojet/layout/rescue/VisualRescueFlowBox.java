package net.zamasoft.foliojet.layout.rescue;

import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * 通常フロー用の救済断片アダプタです(2026-07-25新設、増分3。
 * <b>まだ本番経路へは配線されていません</b>)。
 *
 * <p>
 * {@link VisualRescueBox}に{@link IFlowBox}の2メソッドを足すだけの薄い
 * ラッパーです。改ページ禁止は断片の端にだけ意味があります:
 * </p>
 *
 * <ul>
 * <li>{@code avoidBreakBefore} — 先頭断片だけが元ボックスの指定を引き継ぐ。
 * 継続断片の前は救済分割そのものが作った切断面であり、そこを「禁止」に
 * すると前進が止まる。</li>
 * <li>{@code avoidBreakAfter} — 最終断片だけが元ボックスの指定を引き継ぐ。
 * 中間断片の後は同じ理由で禁止にしない。</li>
 * </ul>
 */
public class VisualRescueFlowBox extends VisualRescueBox implements IFlowBox {

	/**
	 * @param source           レイアウト済みの元ボックス
	 * @param progression      ページ軸を決める書字方向
	 * @param sourcePageExtent 元ボックスのページ方向の占有量
	 * @param offset           この断片が始まるページ方向位置
	 * @param sliceExtent      この断片の占有量
	 */
	public VisualRescueFlowBox(final IFlowBox source, final WritingMode progression, final double sourcePageExtent,
			final double offset, final double sliceExtent) {
		super(source, progression, sourcePageExtent, offset, sliceExtent);
	}

	private IFlowBox flowSource() {
		return (IFlowBox) this.getSource();
	}

	public boolean avoidBreakBefore() {
		return this.isFirstFragment() && this.flowSource().avoidBreakBefore();
	}

	public boolean avoidBreakAfter() {
		return this.isLastFragment() && this.flowSource().avoidBreakAfter();
	}
}
