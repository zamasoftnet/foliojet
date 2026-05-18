package jp.cssj.test.unit.misc;

import net.zamasoft.zstream.resolver.util.URIHelper;
import junit.framework.TestCase;

public class URIHelperTest extends TestCase {
	public URIHelperTest(String name) {
		super(name);
	}

	public void testWindows() throws Exception {
		assertEquals("file:///D:/usr/apache-ant-1.8.0/bin/ant.bat", URIHelper
				.resolve("UTF-8", "file:/D:/usr/apache-ant-1.8.0/bin/",
						"ant.bat").toString());
		assertEquals("file:///D:/usr/apache-ant-1.8.0/bin/", URIHelper.resolve(
				"UTF-8", "ant.bat", "file:///D:/usr/apache-ant-1.8.0/bin/")
				.toString());
		assertEquals("file:///D:/usr/apache-ant-1.8.0/bin/", URIHelper.resolve(
				"UTF-8", "file:///H:/download/ebook/",
				"file:///D:/usr/apache-ant-1.8.0/bin/").toString());
		assertEquals("http://www.yahoo.co.jp/", URIHelper.resolve("UTF-8",
				"file:///H:/download/ebook/", "http://www.yahoo.co.jp/")
				.toString());
	}
}
