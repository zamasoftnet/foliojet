package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;

/**
 * 匿名item(直接テキスト)混在時のコンテナ単位fallbackテストです
 * (Flex F1d——答申の段階的fallback規則: item 1件だけの縮退は禁止、
 * FLEX_FALLBACKS_BY_REASONでsilent fallbackを防ぐ)。テキストが
 * 失われないことも固定する。
 */
public class FlexMixedItemsFallbackTest extends AbstractTestCase {
	public FlexMixedItemsFallbackTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		final long anonBefore = FlexBuilder.fallbacksByReason(FlexBuilder.FallbackReason.ANONYMOUS_ITEM);
		final long recordsBefore = FlexBuilder.FLEX_ITEM_RECORDS.get();
		final long bindsBefore = FlexBuilder.FLEX_ITEM_BINDS.get();
		File file = new File("files/unittest/0510-flex/mixed-items.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("匿名混在はコンテナ単位fallback", anonBefore + 1,
				FlexBuilder.fallbacksByReason(FlexBuilder.FallbackReason.ANONYMOUS_ITEM));
		final long records = FlexBuilder.FLEX_ITEM_RECORDS.get() - recordsBefore;
		assertEquals("record数=bind数", records, FlexBuilder.FLEX_ITEM_BINDS.get() - bindsBefore);
		assertEquals("record数=3(alpha/p/omega)", 3, records);
	}

	/** コンテナ内の全内容が存在する(テキスト非損失)。 */
	public boolean check_ff(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			final String text = buff.toString();
			assertTrue("all content must be present: " + text,
					text.contains("alpha") && text.contains("p") && text.contains("omega"));
			return true;
		}
		return false;
	}
}
