package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * stretch(align-items既定)の伸長テストです(Flex F3c——cross autoの
 * itemが行高40ptまで伸び、**authoredの背景がitemサイズへ追随する**
 * =takeover設計(F1d)の狙いの実証)。
 */
public class FlexStretchBackgroundTest extends AbstractTestCase {
	public FlexStretchBackgroundTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/stretch-card-background.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** 背景付きauto高itemが行高40ptへ伸長している。 */
	public boolean check_card(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(40.0, box.getPageExtent(WritingMode.TB), 0.1);
			return true;
		}
		return false;
	}
}
