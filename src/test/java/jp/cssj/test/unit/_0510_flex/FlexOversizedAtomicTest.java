package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Flex F0b: ページより大きいatomicコンテナの救済経路です。
 * 無限ループ・内容消失なしに出力され(visual rescue)、後続の内容も
 * 失われないこと(クラッシュ排除の絶対要件)。
 */
public class FlexOversizedAtomicTest extends AbstractTestCase {
	public FlexOversizedAtomicTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/oversized-atomic.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		// ページ超過のatomicは救済分割で出力される——存在すれば良い
		return box.getType() == BoxType.BLOCK;
	}

	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		// 後続内容が失われない
		return box.getType() == BoxType.BLOCK;
	}
}
