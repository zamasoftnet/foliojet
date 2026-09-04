package jp.cssj.test.unit._3090_bidi;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.IBox;

/** HTMLのdir/bdi/bdoを段落単位UBAへ写す規則の試験。 */
public class HtmlDirBidiTest extends AbstractTestCase {
	public HtmlDirBidiTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/3090-bidi/html-dir.html"),
				"text/html", null);
	}

	public boolean check_dir_rtl(final IBox box, final int page, final double x, final double y) {
		return checkLine(box, "דגABCבא", "אבABCגד");
	}

	public boolean check_dir_auto(final IBox box, final int page, final double x, final double y) {
		return checkLine(box, "Lבא-12R", "L12-אבR");
	}

	public boolean check_bdi_isolate(final IBox box, final int page, final double x, final double y) {
		return checkLine(box, "Lבא-12R", "L12-אבR");
	}

	public boolean check_bdo_override(final IBox box, final int page, final double x, final double y) {
		return checkLine(box, "L21-CBAR", "LABC-12R");
	}

	public boolean check_ltr_default(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertTrue("paragraph bidi must be enabled by default", line.isParagraphBidiEnabled());
		assertTrue("pure LTR paragraph must not build visualContents", line.getVisualContents().isEmpty());
		assertNull("pure LTR paragraph must not build LogicalLineEmission", line.getLogicalLineEmission());
		assertNull("pure LTR paragraph must not collect visual sidecar text", line.getLogicalLineVisualText());
		return true;
	}

	private static boolean checkLine(final IBox box, final String visual, final String logical) {
		if (!(box instanceof AbstractLineBox line)) {
			return false;
		}
		assertEquals(visual, line.getLogicalLineVisualText());
		assertEquals(logical, logicalText(line));
		return true;
	}

	private static String logicalText(final IBox box) {
		final StringBuilder text = new StringBuilder();
		box.getText(text);
		return text.toString();
	}
}
