package net.zamasoft.foliojet.layout.box.impl;

import java.util.List;

import junit.framework.TestCase;

/** row subgridの一時link契約を固定します。 */
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
				// この試験では登録契約だけを使う。
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

		// sinkへ渡すrowは親のrowStartを足さない子ローカル値。
		consumed.sink().contribute(1, 2, 30);
		assertEquals(1, contribution[0]);
		assertEquals(2, contribution[1]);
	}
}
