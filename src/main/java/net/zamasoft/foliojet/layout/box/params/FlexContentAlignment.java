package net.zamasoft.foliojet.layout.box.params;

/**
 * Flexのcontent distribution(justify-content/align-content)です
 * (Flex F3a、2026-08-02——consult-codex-2026-08-02-flexbox.txt Q2)。
 * {@link BoxAlignment}(self alignment)とは別型——space-*系はGridの
 * stretch解決({@code BoxAlignment.resolve})と混ざらない。
 * flex-start/flex-endはF5bのreverse導入までSTART/ENDへ写像される
 * (マッピングはBoxStyleMapper)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum FlexContentAlignment {
	NORMAL, START, CENTER, END, STRETCH, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY;

	/**
	 * n個の断片へ余白{@code free}を分配したときの先頭オフセットです
	 * (justify-content §9.5/align-content §9.6の共通算術。負余白は
	 * 0=safe start)。
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
		default -> 0; // NORMAL/START/STRETCH/SPACE_BETWEEN(単数はstart)
		};
	}

	/** 断片間へ挿入される追加間隔です(同上)。 */
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
