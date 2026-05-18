package jp.cssj.test.unit._1000_TABLE;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

public class RootTableTest extends AbstractTestCase {
	public RootTableTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1000-TABLE/root-table.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
}
