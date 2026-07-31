package jp.cssj.test.unit._0520_named_page;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code @page size}(名前付きページN3/N4)がPDFのページ毎
 * {@code /MediaBox}まで届くことを、生成PDFのバイト列で検証します
 * (consult-codex-2026-07-31-named-pages.txt N5)。display-list goldenは
 * レイアウト座標を固定するが、PDF層のページ寸法はここでしか見えない。
 */
public class PageSizeMediaBoxTest extends AbstractTestCase {

	public PageSizeMediaBoxTest(String name) {
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
		// Not used; the test drives its own transcode.
	}

	private static final Pattern MEDIA_BOX = Pattern
			.compile("/MediaBox\\s*\\[\\s*0\\s+0\\s+([0-9.]+)\\s+([0-9.]+)\\s*\\]");

	public void testLandscapeSectionMediaBoxes() throws Exception {
		CTISessionHelper.transcodeFile(this.session,
				new File("files/unittest/0520-named-page/landscape-section.html"), "text/html", null);
		this.session.close();
		this.closed = true;
		final String pdf = new String(Files.readAllBytes(this.file.toPath()), StandardCharsets.ISO_8859_1);

		final List<double[]> boxes = new ArrayList<>();
		final Matcher m = MEDIA_BOX.matcher(pdf);
		while (m.find()) {
			boxes.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) });
		}
		assertEquals("3 pages must each carry a MediaBox", 3, boxes.size());
		final double a4w = 595.28, a4h = 841.89;
		assertPageSize("page 1 (portrait)", a4w, a4h, boxes.get(0));
		assertPageSize("page 2 (landscape)", a4h, a4w, boxes.get(1));
		assertPageSize("page 3 (portrait)", a4w, a4h, boxes.get(2));
	}

	private static void assertPageSize(final String label, final double w, final double h, final double[] actual) {
		assertEquals(label + " width", w, actual[0], 0.1);
		assertEquals(label + " height", h, actual[1], 0.1);
	}
}
