package jp.cssj.test.unit._0150_text_shadow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.TextShadow;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * <b>text-shadowのx/yオフセットが独立に効く</b>ことを固定します
 * (2026-08-18新設)。
 *
 * <p>
 * {@code css.impl.property.text.TextShadow.get()}のy計算がコピーミスで
 * {@code src[i].x}を参照しており、yが常にxと同値になっていた。
 * {@code text-shadow: 0 1px}(Prismの定番配色)の影が本体と<b>完全に
 * 同座標</b>へ落ち、テキスト全体が二重描画になっていた——reveal.jsの
 * ドキュメントサイトのコードブロックで、版面監査が重なり319対を
 * 報告した実欠陥。
 * </p>
 */
public class OffsetTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0150-text-shadow/offsets.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public OffsetTest(String name) {
		super(name);
	}

	private static TextShadow shadowOf(IBox box) {
		final TextShadow[] shadows = ((AbstractTextParams) box.getParams()).textShadows;
		assertNotNull("影が無い", shadows);
		assertEquals(1, shadows.length);
		return shadows[0];
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		final TextShadow shadow = shadowOf(box);
		assertEquals(2.0, shadow.x, 0.01);
		assertEquals(3.0, shadow.y, 0.01);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		final TextShadow shadow = shadowOf(box);
		assertEquals(0.0, shadow.x, 0.01);
		assertEquals(1.0, shadow.y, 0.01);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		final TextShadow shadow = shadowOf(box);
		assertEquals(4.0, shadow.x, 0.01);
		assertEquals(0.0, shadow.y, 0.01);
		return true;
	}
}
