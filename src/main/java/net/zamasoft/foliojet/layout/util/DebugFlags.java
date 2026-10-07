package net.zamasoft.foliojet.layout.util;

/**
 * Switches developer diagnostic output (consolidated scattered {@code System.getProperty} calls on 2026-10-05).
 *
 * <p>
 * Determined at startup by {@code -Dfoliojet.debug.<name>}, regardless of value (enabled if specified).
 * Does not change during conversion; previously, system properties were read on every float received
 * and every box split.
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
