package net.zamasoft.foliojet.layout.box.params;

/**
 * Flex content distribution (justify-content/align-content)
 * (Flex F3a, 2026-08-02; consult-codex-2026-08-02-flexbox.txt Q2).
 * A separate type from {@link BoxAlignment} (self alignment): space-* values do not mix with
 * Grid stretch resolution ({@code BoxAlignment.resolve}). flex-start/flex-end map to START/END
 * until reverse is introduced in F5b (mapping in BoxStyleMapper).
 *
 * @author MIYABE Tatsuhiko
 */
public enum FlexContentAlignment {
	NORMAL, START, CENTER, END, STRETCH, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY;

	/**
	 * Leading offset when distributing free space {@code free} among n fragments
	 * (shared arithmetic for justify-content §9.5/align-content §9.6;
	 * negative free space becomes 0 = safe start).
	 */
	public double leadingOffset(final double free, final int count) {
		if (free <= 0 || count <= 0) {
			return 0;
		}
		return switch (this) {
		case CENTER -> free / 2;
		case END -> free;
		case SPACE_AROUND -> count > 1 ? free / (count * 2) : free / 2;
		case SPACE_EVENLY -> free / (count + 1);
		default -> 0; // NORMAL/START/STRETCH/SPACE_BETWEEN (a single fragment uses start)
		};
	}

	/** Additional spacing inserted between fragments (as above). */
	public double betweenOffset(final double free, final int count) {
		if (free <= 0 || count <= 1) {
			return 0;
		}
		return switch (this) {
		case SPACE_BETWEEN -> free / (count - 1);
		case SPACE_AROUND -> free / count;
		case SPACE_EVENLY -> free / (count + 1);
		default -> 0;
		};
	}
}
