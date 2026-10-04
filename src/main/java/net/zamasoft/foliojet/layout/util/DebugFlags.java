package net.zamasoft.foliojet.layout.util;

/**
 * 開発者向けの診断出力の切り替えです(2026-10-05 に各所の {@code System.getProperty} を集めた)。
 *
 * <p>
 * 起動時の {@code -Dfoliojet.debug.<名前>} で決まり、値は問わない(指定があれば有効)。変換の途中では変わらない
 * ——以前は浮動体を受け取るたび・箱を切るたびにシステムプロパティを引いていた。
 * </p>
 */
public final class DebugFlags {
	/** {@code -Dfoliojet.debug.floatTrace} */
	public static final boolean FLOAT_TRACE = on("floatTrace");

	/** {@code -Dfoliojet.debug.breakTrace} */
	public static final boolean BREAK_TRACE = on("breakTrace");

	/** {@code -Dfoliojet.debug.topFloat} */
	public static final boolean TOP_FLOAT = on("topFloat");

	/** {@code -Dfoliojet.debug.tableBasis} */
	public static final boolean TABLE_BASIS = on("tableBasis");

	/** {@code -Dfoliojet.debug.resumeDetail} */
	public static final boolean RESUME_DETAIL = on("resumeDetail");

	/** {@code -Dfoliojet.debug.rescueProbe} */
	public static final boolean RESCUE_PROBE = on("rescueProbe");

	/** {@code -Dfoliojet.debug.lineBasis} */
	public static final boolean LINE_BASIS = on("lineBasis");

	/** {@code -Dfoliojet.debug.breakFingerprint} */
	public static final boolean BREAK_FINGERPRINT = on("breakFingerprint");

	private DebugFlags() {
	}

	private static boolean on(final String name) {
		return System.getProperty("foliojet.debug." + name) != null;
	}
}
