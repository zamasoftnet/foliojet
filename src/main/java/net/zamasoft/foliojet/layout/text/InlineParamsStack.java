package net.zamasoft.foliojet.layout.text;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * グリフパイプラインを流れるインライン文脈(AbstractTextParams のネスト)です。
 * チェーンの先頭ステージ(CSSJTextUnitizer)が InlineQuad の通過で push/pop し、
 * 下流ステージ(WordHyphenator 等)は参照を共有して current() を読むだけです。
 *
 * <p>
 * 注意: これは「パイプライン寿命」の文脈です。quad の発生源
 * (StyledTextUnitizer)と、テキストブロックを跨いで生存する
 * BuilderGlyphHandler、行再構築時に動く TextBuilder は寿命が異なるため
 * 独自のスタックを持ちます(ARCHITECTURE.md §5.5)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class InlineParamsStack {
	private final List<AbstractTextParams> stack = new ArrayList<AbstractTextParams>();

	public InlineParamsStack(AbstractTextParams root) {
		this.stack.add(root);
	}

	public void push(AbstractTextParams params) {
		this.stack.add(params);
	}

	public void pop() {
		this.stack.remove(this.stack.size() - 1);
	}

	/**
	 * 現在のインライン文脈のパラメータを返します。
	 *
	 * @return スタック最上位のパラメータ
	 */
	public AbstractTextParams current() {
		return this.stack.get(this.stack.size() - 1);
	}
}
