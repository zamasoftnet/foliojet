package jp.cssj.test.unit.builder;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.builder.impl.RowLayoutEngine;

/** Incremental rowspan窓の実測軸を固定します。 */
public class RowspanPageSizeMeasurementTest extends TestCase {
	public void testVerticalUsesWidthAndHorizontalUsesHeight() {
		final TableCellBox cellBox = new TableCellBox(new BlockParams(), new TableCellPos(), new FlowContainer());
		cellBox.setWidth(81);
		cellBox.setHeight(123);

		assertEquals("縦書きのページ軸", 81,
				RowLayoutEngine.demandPageSize(RowLayoutEngine.measuredRowspanPageSize(cellBox, true),
						cellBox.getBlockParams(), cellBox, true),
				0);
		assertEquals("横書きのページ軸", 123,
				RowLayoutEngine.demandPageSize(RowLayoutEngine.measuredRowspanPageSize(cellBox, false),
						cellBox.getBlockParams(), cellBox, false),
				0);
	}
}
