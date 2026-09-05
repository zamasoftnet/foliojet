package jp.cssj.test.unit._9500_PROFILE;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;

/**
 * 出力インテント/レンダリングインテントの入口テストです(2026-08-02、
 * PLAN §2の2位)。/OutputIntents(識別名・レジストリ・ICC埋め込みの/N)と
 * コンテンツストリームのri演算子を生成PDFのバイト列で固定する。
 */
public class OutputIntentTest extends AbstractTestCase {
	public OutputIntentTest(String name) {
		super(name);
	}

	private boolean closed = false;

	@Override
	protected void tearDown() throws Exception {
		if (!this.closed) {
			super.tearDown();
		}
	}

	protected void transcode() throws Exception {
		final byte[] profile;
		try (InputStream in = PDFWriterImpl.class.getResourceAsStream(
				"/net/zamasoft/pdfg2d/pdf/impl/ISOcoated_v2_300_eci.icc")) {
			assertNotNull("ISO Coated v2 300% (ECI)プロファイルがクラスパスに必要です", in);
			profile = in.readAllBytes();
		}
		final Path iccFile = Files.createTempFile("foliojet-output-intent-", ".icc");
		try {
			Files.write(iccFile, profile);
			this.session.property("output.pdf.compression", "none");
			this.session.property("output.pdf.output-intent.identifier", "FOGRA39");
			this.session.property("output.pdf.output-intent.condition", "ISO Coated v2 300% (ECI)");
			this.session.property("output.pdf.output-intent.icc-profile", iccFile.toUri().toString());
			this.session.property("output.pdf.rendering-intent", "relative-colorimetric");
			File file = new File("files/unittest/9500-PROFILE/simple.html");
			CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
			this.session.close();
			this.closed = true;
		} finally {
			Files.deleteIfExists(iccFile);
		}
		final String pdf = new String(Files.readAllBytes(this.file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue("/OutputIntentsが出ること", pdf.contains("/OutputIntents"));
		assertTrue("出力条件識別名が出ること", pdf.contains("FOGRA39"));
		assertTrue("レジストリ名が出ること", pdf.contains("www.color.org"));
		assertTrue("人間可読名が出ること", pdf.contains("ISO Coated v2 300%"));
		assertTrue("ICCの色成分数/N 4が出ること", pdf.contains("/N 4"));
		assertTrue("既定レンダリングインテントのri演算子が出ること",
				pdf.contains("/RelativeColorimetric ri"));
	}
}
