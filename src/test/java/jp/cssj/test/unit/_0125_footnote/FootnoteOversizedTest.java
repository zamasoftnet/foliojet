package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.builder.impl.FootnoteOverflowException;

/**
 * 脚注F4/F6の境界: どのページにも入らない<b>atomicな</b>巨大脚注
 * (800pt+page-break-inside:avoid——固定高ブロックは高さ切断できるため
 * avoidで分割を禁じたもの)は、送り続けず型付き失敗になる。分割可能な
 * 巨大脚注はF6が複数ページへ置く(FootnoteSplitTest)。
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
						|| (t.getMessage() != null && (t.getMessage().contains("footnote too large")
								|| t.getMessage().contains("footnote cannot be placed")))) {
					found = true;
					break;
				}
				t = t.getCause();
			}
			assertTrue("the failure must carry the FootnoteOverflowException message: " + e, found);
		}
	}
}
