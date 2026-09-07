package net.zamasoft.foliojet.layout;

/** Bのページ確定時に値へ写す報告。木・資源・可変ラベルは保持しません。 */
public record FootnotePageProbeReport(long generation, String pageName, boolean emitted,
		long eventId, int charOffset, double innerWidth, double innerHeight,
		net.zamasoft.foliojet.layout.box.params.WritingMode flow,
		java.util.Set<Long> callIds, java.util.Map<Long, Double> measuredHeights,
		java.util.Set<Long> unmeasuredIds, double h0, long deliveredEvents,
		Completion completion, long pageBytes) {
	public enum Completion { DRAW_PAGE, END_OF_INPUT }

	public FootnotePageProbeReport {
		callIds = java.util.Set.copyOf(callIds);
		measuredHeights = java.util.Map.copyOf(measuredHeights);
		unmeasuredIds = java.util.Set.copyOf(unmeasuredIds);
	}

	/** 確定木に載った呼び出しの既知の高さだけ。未計測IDを0と判断してはいけません。 */
	public double measuredHeight() {
		double height = 0;
		for (final long id : new java.util.TreeSet<>(this.callIds)) {
			final Double measured = this.measuredHeights.get(id);
			if (measured != null) height += measured;
		}
		return height;
	}
}
