package jp.cssj.test.unit._0130_display;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

public class AllNoneTest extends AbstractTestCase {
	public AllNoneTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0130-display/all-none.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
}
