package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Flex F0a (consult-codex-2026-08-02-flexbox.txt): regression test that display:flex parses and
 * degrades to a normal block without losing content while layout is not yet wired up.
 * Even after F0b makes it atomic, "all items are output" remains an invariant.
 */
public class F0ParseTest extends AbstractTestCase {
	public F0ParseTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/f0-parse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}

	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}
}
