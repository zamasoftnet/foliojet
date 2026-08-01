package jp.cssj.test.unit._0520_facturx;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * 電子インボイス(Factur-X/ZUGFeRD)出力のテストです(2026-08-02、
 * PLAN §2の時限1位——仏2026-09受信義務)。PDF/A-3B+添付XMLの
 * AFRelationship=Alternative+XMPのfx:拡張スキーマ(検証器が見る
 * 3点セット)を生成PDFのバイト列で固定する。
 */
public class FacturXTest extends AbstractTestCase {
	public FacturXTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		this.session.property("output.pdf.version", "1.7A-3");
		this.session.property("output.pdf.compression", "none");
		this.session.property("output.pdf.attachments.0.uri",
				new File("files/unittest/0520-facturx/factur-x.xml").toURI().toString());
		this.session.property("output.pdf.attachments.0.mime-type", "text/xml");
		this.session.property("output.pdf.attachments.0.relationship", "alternative");
		this.session.property("output.pdf.facturx.conformance-level", "EN 16931");
		File file = new File("files/unittest/0520-facturx/invoice.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		final String pdf = new String(Files.readAllBytes(this.file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue("添付のAFRelationshipがAlternativeであること",
				pdf.contains("/AFRelationship /Alternative") || pdf.contains("/AFRelationship/Alternative"));
		assertTrue("添付ファイル名factur-x.xmlが存在すること", pdf.contains("factur-x.xml"));
		assertTrue("XMPにfx:ConformanceLevelが出ること", pdf.contains("EN 16931"));
		assertTrue("XMPにfx:DocumentTypeが出ること", pdf.contains("INVOICE"));
		assertTrue("Factur-X名前空間が出ること", pdf.contains("factur-x"));
	}
}
