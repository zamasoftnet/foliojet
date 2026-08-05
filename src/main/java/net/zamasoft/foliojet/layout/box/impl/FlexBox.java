package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.PageAtomicBox;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;

/**
 * Flexコンテナです(Flex F0b、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt。{@link GridBox}と同型)。
 *
 * <p>
 * ページング上は正規のblock({@code BoxType.BLOCK}/{@code PosType.FLOW})の
 * まま——rescue・描画・フレーム処理を{@link FlowBlockBox}から継承し、
 * {@link PageAtomicBox}でページ軸の構造分割だけを型付きで禁じる
 * (css-flexbox-1 §10の断片化はinformativeのため非対応が正当。
 * 入らなければ丸ごと送り→visual rescue)。
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
		final net.zamasoft.foliojet.layout.box.params.FlexParams params = this.getFlexParams();
		final FlowPos pos = this.getFlowPos();
		return (state, container) -> new FlexBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
