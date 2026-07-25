package jp.cssj.test.unit._0450_hyphens;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 本文直下のブロックが soft hyphen(U+00AD)だけを含む文書が、
 * 例外なく変換され後続の内容が失われないことを確認します。
 *
 * <p>
 * {@code BreakableBuilder.flush()} に null ガードが無かった頃は、この文書で
 * {@code NullPointerException} になっていました(2026-07-25 修正)。
 * 同型の欠陥は {@code BlockBuilder.flush()} 側で 2026-07-24 に修正済みです。
 * </p>
 */
public class LoneSoftHyphenTest extends AbstractTestCase {
	public LoneSoftHyphenTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0450-hyphens/lone-soft-hyphen.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertEquals("ok", buff.toString().trim());
			return true;
		}
		return false;
	}
}
