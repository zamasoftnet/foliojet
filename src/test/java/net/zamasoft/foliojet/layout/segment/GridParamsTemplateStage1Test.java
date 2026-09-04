package net.zamasoft.foliojet.layout.segment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.GridParams;

/** row subgrid Stage 1で追加したgap種別の録画再生テストです。 */
public class GridParamsTemplateStage1Test extends TestCase {

	public void testGridGapNormalRoundTrips() {
		final GridParams source = new GridParams();
		source.rowGap = 0;
		source.rowGapNormal = false;
		source.columnGap = 4;
		source.columnGapNormal = true;

		final GridParams materialized = GridParamsTemplate.freeze(source).materialize();
		assertEquals(0.0, materialized.rowGap, 0);
		assertFalse(materialized.rowGapNormal);
		assertEquals(4.0, materialized.columnGap, 0);
		assertTrue(materialized.columnGapNormal);
	}
}
