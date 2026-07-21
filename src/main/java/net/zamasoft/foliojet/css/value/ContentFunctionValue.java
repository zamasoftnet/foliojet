package net.zamasoft.foliojet.css.value;

/**
 * GCPM {@code content()}(引数なし)。{@code string-set}の値リスト内
 * でのみ生成される——代入元要素自身の描画テキストに置き換わる
 * マーカーで、{@code content:}プロパティの評価には一切流れない。
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContentFunctionValue implements Value {
	public static final ContentFunctionValue INSTANCE = new ContentFunctionValue();

	private ContentFunctionValue() {
	}
}
