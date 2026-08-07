package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * flex行分割({@code page-break-inside: avoid})の契約テストです
 * (2026-08-07、Bug C以降)。
 *
 * <p>
 * F0b時点はflexが常時atomic(css-flexbox-1 §10の断片化はinformativeの
 * ため非対応)で、この文書はその既定挙動を確かめていた。Bug C
 * (テーブル行と同型の行分割、{@code FlexBox.split}参照)導入後は
 * 既定でflex行も強制分割されるため、代わりに明示{@code
 * page-break-inside: avoid}が正しく「丸ごと次ページへ」を強制する
 * ことを確かめる形へ更新した(fixture側に{@code page-break-inside:
 * avoid}を追加)。
 * </p>
 */
public class FlexAtomicTest extends AbstractTestCase {
	public FlexAtomicTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/atomic-move.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** page-break-inside: avoid の flex は分割されず丸ごと次ページへ。 */
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the flex container must move to page 2 as a whole", 2, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			final String text = buff.toString();
			assertTrue("all items must be present: " + text, text.contains("alpha") && text.contains("gamma"));
			return true;
		}
		return false;
	}
}
