package net.zamasoft.foliojet.css.value;

/**
 * {@code font-kerning}の値(css-fonts-4 §6.3、2026-08-29)。
 *
 * <p>
 * {@code auto}と{@code normal}はカーニングを使う(この実装では同じ)。
 * {@code none}はOpenTypeの{@code kern}機能を明示offにする。
 * {@code font-feature-settings}の明示指定があればそちらが優先する
 * (css-fonts-4 §7.1の優先順)。
 * </p>
 */
public enum FontKerningValue implements Value {
	AUTO_VALUE, NORMAL_VALUE, NONE_VALUE;
}
