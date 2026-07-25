package net.zamasoft.foliojet.layout.rescue;

import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * 浮動ボックス用の救済断片アダプタです(2026-07-25新設、増分3。
 * <b>まだ本番経路へは配線されていません</b>)。
 *
 * <p>
 * {@link VisualRescueBox}に{@link IFloatBox#getFloatPos()}を足すだけの
 * 薄いラッパーです。配置パラメータは元ボックスのものをそのまま返します
 * ——断片は元ボックスのParams・Posを一切変更しません。排除域の高さは
 * 断片の占有量({@code sliceExtent})になりますが、それは
 * {@code getPageExtent()}から導かれるため、ここで持つ状態はありません。
 * </p>
 */
public class VisualRescueFloatBox extends VisualRescueBox implements IFloatBox {

	/**
	 * @param source           レイアウト済みの元ボックス
	 * @param progression      ページ軸を決める書字方向
	 * @param sourcePageExtent 元ボックスのページ方向の占有量
	 * @param offset           この断片が始まるページ方向位置
	 * @param sliceExtent      この断片の占有量
	 */
	public VisualRescueFloatBox(final IFloatBox source, final WritingMode progression, final double sourcePageExtent,
			final double offset, final double sliceExtent) {
		super(source, progression, sourcePageExtent, offset, sliceExtent);
	}

	public FloatPos getFloatPos() {
		return ((IFloatBox) this.getSource()).getFloatPos();
	}
}
