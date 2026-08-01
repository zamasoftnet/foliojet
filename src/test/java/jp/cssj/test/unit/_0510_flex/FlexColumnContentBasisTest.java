package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;

/**
 * columnのbasis:content境界テストです(Flex F4c——F4c答申の判定漏れ①:
 * basis:contentは主軸指定(height:40pt)があっても内容高を要求するため
 * 常にコンテナ単位fallback。record数=bind数、部分Flex配置なし)。
 */
public class FlexColumnContentBasisTest extends AbstractTestCase {
	public FlexColumnContentBasisTest(String name) {
		super(name);
	}

	private double baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long contentBefore = FlexBuilder.FLEX_COLUMN_FALLBACKS_CONTENT_BASIS.get();
		final long recordsBefore = FlexBuilder.FLEX_ITEM_RECORDS.get();
		final long bindsBefore = FlexBuilder.FLEX_ITEM_BINDS.get();
		File file = new File("files/unittest/0510-flex/column-content-basis.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("basis:contentは常にfallback", contentBefore + 1,
				FlexBuilder.FLEX_COLUMN_FALLBACKS_CONTENT_BASIS.get());
		assertEquals("record数=bind数", FlexBuilder.FLEX_ITEM_RECORDS.get() - recordsBefore,
				FlexBuilder.FLEX_ITEM_BINDS.get() - bindsBefore);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** fallback=単一列積み(部分Flex配置がない——qはpの指定高40pt直下)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 40, y, 0.1);
			return true;
		}
		return false;
	}
}
