package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.builder.impl.FootnoteOverflowException;

/**
 * 脚注F4の境界: 空ページの最大脚注領域(≈755.9pt)にも収まらない
 * 800ptの脚注は、次ページへ送り続けず型付き失敗になる
 * (consult-codex-2026-07-31-footnote-f4.txt 検証fixture 6の単体超過側。
 * 累積超過の送りはFootnoteCarryInTestが固定)。
 */
public class FootnoteOversizedTest extends AbstractTestCase {
	public FootnoteOversizedTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-oversized.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	@Override
	public void testDocument() throws Exception {
		try {
			this.transcode();
			fail("an oversized footnote must fail with a typed error");
		} catch (Exception e) {
			// 変換経路(TranscoderException)はcause鎖でなくメッセージへ畳む
			// ため、FootnoteOverflowExceptionのメッセージで判定する
			Throwable t = e;
			boolean found = false;
			while (t != null) {
				if (t instanceof FootnoteOverflowException
						|| (t.getMessage() != null && t.getMessage().contains("footnote too large"))) {
					found = true;
					break;
				}
				t = t.getCause();
			}
			assertTrue("the failure must carry the FootnoteOverflowException message: " + e, found);
		}
	}
}
