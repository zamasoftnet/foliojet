package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;

/**
 * 大量item(120個)の総合回帰です(Flex F2d——record数=bind数、
 * exact fitの行分割(40pt×5=200pt)が全24行で崩れない、末尾itemと
 * 後続ブロックの座標固定)。
 */
public class FlexWrapManyItemsTest extends AbstractTestCase {
	public FlexWrapManyItemsTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long recordsBefore = FlexBuilder.FLEX_ITEM_RECORDS.get();
		final long bindsBefore = FlexBuilder.FLEX_ITEM_BINDS.get();
		File file = new File("files/unittest/0510-flex/wrap-many-items.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("record数=120", recordsBefore + 120, FlexBuilder.FLEX_ITEM_RECORDS.get());
		assertEquals("bind数=120", bindsBefore + 120, FlexBuilder.FLEX_ITEM_BINDS.get());
	}

	public boolean check_first(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** 末尾item(#119)=24行目の5列目(x=+160、y=+23×20)。 */
	public boolean check_last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			assertEquals(this.baseY + 460, y, 0.1);
			return true;
		}
		return false;
	}

	/** 後続=24行×20ptの直後。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 480, y, 0.1);
			return true;
		}
		return false;
	}
}
