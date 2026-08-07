package net.zamasoft.foliojet.layout.box.params;

/**
 * background-sizeの{@code contain}/{@code cover}キーワード形式です
 * (2026-08-06)。
 *
 * <p>
 * この2つは箱の実寸(paddingボックス)と画像の縦横比を比較しないと解決
 * できないため、{@code <length>|<percentage>|auto}用の{@link Dimension}
 * (LengthTypeが4値ちょうどで2ビットへ詰められており、Length/Insets/Offset
 * とも共用する既存の枠組みを壊せない)には乗せられない。箱の実寸が分かる
 * 描画時点({@code Background}の塗り処理)まで種別だけ運び、そこで初めて
 * 実寸を計算する。
 * </p>
 */
public enum BackgroundFit {
	NONE, CONTAIN, COVER
}
