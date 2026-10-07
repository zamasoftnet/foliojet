package net.zamasoft.foliojet.layout.box.impl;

import java.util.List;

import junit.framework.TestCase;

/** Locks down the temporary link contract for row subgrid. */
public class RowSubgridLinkTest extends TestCase {

	public void testLinkIsConsumedAndClearedOnce() {
		final int[] contribution = { -1, -1 };
		final RowContributionSink sink = new RowContributionSink() {
			@Override
			public void contribute(final int row, final int span, final double extent) {
				contribution[0] = row;
				contribution[1] = span;
			}

			@Override
			public void whenRowsResolved(final RowGeometryFinalizer finalizer) {
				// This test uses only the registration contract.
			}
		};
		final RowSubgridLink link = new RowSubgridLink(7, 3, 4, List.of(List.of(), List.of(), List.of(),
				List.of()), sink);
		final GridItemBox.SubgridTracks tracks = new GridItemBox.SubgridTracks(new double[] { 10 }, 2,
				List.of(List.of(), List.of()), 4, List.of(), link);

		assertSame(link, tracks.link());
		final RowSubgridLink consumed = tracks.consumeRowSubgridLink();
		assertSame(link, consumed);
		assertNull(tracks.link());
		assertNull(tracks.consumeRowSubgridLink());

		// The row passed to the sink is child-local, without adding the parent's rowStart.
		consumed.sink().contribute(1, 2, 30);
		assertEquals(1, contribution[0]);
		assertEquals(2, contribution[1]);
	}
}
