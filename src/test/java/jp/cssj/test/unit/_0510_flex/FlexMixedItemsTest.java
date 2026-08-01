package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;

/**
 * 匿名テキスト・ブロック混在itemの行配置テストです(Flex F1e——
 * F1dではコンテナ単位fallbackだったが、§9.7本配線により匿名itemも
 * content由来(max-content)で行内に配置される)。record数=bind数と
 * テキスト非損失、cross size=最大item高を固定する。
 */
public class FlexMixedItemsTest extends AbstractTestCase {
	public FlexMixedItemsTest(String name) {
		super(name);
	}

	private double baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long recordsBefore = FlexBuilder.FLEX_ITEM_RECORDS.get();
		final long bindsBefore = FlexBuilder.FLEX_ITEM_BINDS.get();
		File file = new File("files/unittest/0510-flex/mixed-items.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		final long records = FlexBuilder.FLEX_ITEM_RECORDS.get() - recordsBefore;
		assertEquals("record数=bind数", records, FlexBuilder.FLEX_ITEM_BINDS.get() - bindsBefore);
		assertEquals("record数=3(alpha/p/omega)", 3, records);
	}

	/** コンテナ内の全内容が存在する(テキスト非損失)。 */
	public boolean check_ff(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseY = y;
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			final String text = buff.toString();
			assertTrue("all content must be present: " + text,
					text.contains("alpha") && text.contains("p") && text.contains("omega"));
			return true;
		}
		return false;
	}

	/** 後続ブロックはコンテナ高(=行のcross size最大20pt)の直後。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}
}
