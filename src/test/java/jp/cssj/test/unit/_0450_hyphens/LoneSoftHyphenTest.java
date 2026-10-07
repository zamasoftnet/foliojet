package jp.cssj.test.unit._0450_hyphens;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Verifies that a document with a block directly under body containing only a soft hyphen (U+00AD)
 * converts without an exception and does not lose subsequent content.
 *
 * <p>
 * Before {@code BreakableBuilder.flush()} had a null guard, this document caused a
 * {@code NullPointerException} (fixed on 2026-07-25).
 * The same kind of defect in {@code BlockBuilder.flush()} was fixed on 2026-07-24.
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
