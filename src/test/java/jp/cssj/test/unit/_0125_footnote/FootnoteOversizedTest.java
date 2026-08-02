package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * 脚注F4/F6の境界: どのページにも入らない<b>atomicな</b>巨大脚注
 * (800pt+page-break-inside:avoid——固定高ブロックは高さ切断できるため
 * avoidで分割を禁じたもの)の契約です。
 *
 * <p>
 * <b>2026-08-02に契約が変わった。</b> 従来は型付きエラー
 * ({@code FootnoteOverflowException})で変換を失敗させていたが、
 * {@code ARCHITECTURE.md} §5.13(2026-07-26/27のユーザー裁定)が
 * 「<b>変換が失敗することは常にエンジンの不具合</b>。版面が破綻した文書の
 * 除外は変換の失敗には適用しない」と定めているため、<b>失敗させず、
 * 警告して溢れさせて置く</b>へ縮退させた。改良後の生成器による掃過では、
 * この型の失敗が2,000シード中531件(失敗全体の77%)を占めていた。
 * </p>
 *
 * <p>
 * ここで固定するのは「<b>変換が成功すること</b>」だけである。置かれた結果
 * (紙面外へ溢れる)の見た目は問わない——版面より大きい脚注をどう見せるかは
 * 組版を指定した側の責任(§5.13)。
 * </p>
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
		this.transcode();
	}
}
