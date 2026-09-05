package net.zamasoft.foliojet.layout.box;

/** 字形輪郭収集中の警告方針をスレッド単位で保持します。 */
final class TextShapeContext {
	private static final ThreadLocal<Integer> QUIET_DEPTH = new ThreadLocal<>();

	static void beginQuiet() {
		final Integer depth = QUIET_DEPTH.get();
		QUIET_DEPTH.set(depth == null ? 1 : depth + 1);
	}

	static void endQuiet() {
		final int depth = QUIET_DEPTH.get() - 1;
		if (depth == 0) {
			QUIET_DEPTH.remove();
		} else {
			QUIET_DEPTH.set(depth);
		}
	}

	static boolean warnIfMissing() {
		return QUIET_DEPTH.get() == null;
	}

	private TextShapeContext() {
		// 使用しない。
	}
}
