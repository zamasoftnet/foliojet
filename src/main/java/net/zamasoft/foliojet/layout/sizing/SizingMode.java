package net.zamasoft.foliojet.layout.sizing;

/**
 * サイズ決定のモードです。
 *
 * @author MIYABE Tatsuhiko
 */
public enum SizingMode {
	/** 確定寸法でのレイアウト(bind/再生パス)。 */
	DEFINITE,
	/** 最小内容寸法の実測。 */
	MIN_CONTENT,
	/** 最大内容寸法の実測。 */
	MAX_CONTENT,
	/** fit-content(shrink-to-fit)による寸法決定。 */
	FIT_CONTENT;
}
