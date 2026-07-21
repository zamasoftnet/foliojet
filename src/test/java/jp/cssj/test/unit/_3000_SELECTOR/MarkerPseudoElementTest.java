package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code ::marker}(2026-07-21新設、CSS Lists)のsmoke test。
 *
 * <p>
 * マーカー箱({@code OutsideMarkerBox}/{@code InsideMarkerBox})は実要素に
 * 対応しない合成boxであり、{@code CSSElement.MARKER}にはidが無いため、
 * 既存の{@code check_ID}コールバック機構(要素idベース)では直接検証
 * できない。このテストは「{@code ::marker}ルールが例外なくカスケード
 * 解決され、文書が完走すること」だけを固定する——スタイル適用ロジック
 * 自体は{@code ::before}/{@code ::after}と全く同じ仕組み
 * (CSSElement押し込み+merge+適用)であり、そちらは既存corpusで
 * 広く検証済み。
 * </p>
 */
public class MarkerPseudoElementTest extends AbstractTestCase {
	public MarkerPseudoElementTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/marker.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
}
