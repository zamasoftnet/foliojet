package jp.cssj.test.unit._1090_TITLE;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

public class TiltleInBodyTest extends AbstractTestCase {
	public TiltleInBodyTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1090-TITLE/title-in-body.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
}
