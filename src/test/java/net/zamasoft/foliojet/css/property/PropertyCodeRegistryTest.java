package net.zamasoft.foliojet.css.property;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

/**
 * 「全ての解釈可能プロパティはカスケード用コードを持つ」の静的な網羅
 * 検査です(2026-08-01)。
 *
 * <p>
 * カスケード用コード({@link ElementPropertySet#getCode})は
 * {@code ElementPropertySet}の静的登録(reg/regCode)だけが割り当てる
 * 単一台帳で、他のPropertySet(@page・@font-face)に名前だけ登録された
 * プロパティは{@code CSSStyle.set/get}で<b>黙って落ちる</b>——@page
 * {@code size}(2026-07-31)で実際に踏み、長いデバッグになった罠。
 * このテストは新しいプロパティの登録漏れをテスト時に即検出する
 * ({@code CSSStyle.set/get}側の-ea assertと二重の防護)。
 * </p>
 */
public class PropertyCodeRegistryTest extends TestCase {

	private static void assertAllCoded(final String setName, final PropertySet set, final List<String> missing) {
		for (final PropertyInfo info : set.registeredInfos()) {
			if (info instanceof PrimitivePropertyInfo primitive) {
				if (ElementPropertySet.getCode(primitive) < 0) {
					missing.add(setName + ": " + info.getName());
				}
			}
			// 非プリミティブ(shorthand)はparseが構成要素のEntryへ分解して
			// からsetされる。構成要素は各セットが名前でも登録している
			// (put(Shorthand.INFO, 構成要素...)の慣習)ため、この列挙で
			// プリミティブとして検査される
		}
	}

	public void testEveryParseablePropertyHasCascadeCode() {
		final List<String> missing = new ArrayList<>();
		assertAllCoded("element", ElementPropertySet.getInstance(), missing);
		assertAllCoded("page", PagePropertySet.getInstance(), missing);
		assertAllCoded("font-face", FontFacePropertySet.getInstance(), missing);
		assertTrue("カスケード用コード未割当の解釈可能プロパティ(ElementPropertySetへのregCode漏れ): " + missing,
				missing.isEmpty());
	}
}
