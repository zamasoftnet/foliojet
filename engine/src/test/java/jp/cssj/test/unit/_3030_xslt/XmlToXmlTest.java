package jp.cssj.test.unit._3030_xslt;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

public class XmlToXmlTest extends AbstractTestCase {
	public XmlToXmlTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3030-xslt/xml-to-xml.xml");
		CTISessionHelper.transcodeFile(this.session, file, "text/xml", null);
	}
}
