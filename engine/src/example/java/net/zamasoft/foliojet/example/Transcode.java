package net.zamasoft.foliojet.example;

import java.io.File;
import java.net.URI;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.driver.DirectDriver;

public class Transcode {
	public static void main(String[] args) throws Exception {
		// 接続する
		System.setProperty(DirectDriver.DEFAULT_PROFILE_FILE_KEY,
				"src/conf/profiles/default.properties");
		try(CTISession session = new DirectDriver().getSession(URI
				.create("copper:direct:"), null)){
			final File outFile = new File("test.pdf");
			CTISessionHelper.setResultFile(session, outFile);
			session.property("input.include", "http://copper-pdf.com/**");
			session.transcode(URI.create("http://copper-pdf.com/"));
		}
	}
}
