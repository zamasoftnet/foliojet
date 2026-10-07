package net.zamasoft.foliojet.layout;

/** A report copied into values when B finalizes a page. Retains no trees, resources, or mutable labels. */
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

	/** Only known heights of calls on the finalized tree. Never interpret an unmeasured ID as zero. */
	public double measuredHeight() {
		double height = 0;
		for (final long id : new java.util.TreeSet<>(this.callIds)) {
			final Double measured = this.measuredHeights.get(id);
			if (measured != null) height += measured;
		}
		return height;
	}
}
