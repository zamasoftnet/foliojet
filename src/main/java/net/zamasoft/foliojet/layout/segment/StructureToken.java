package net.zamasoft.foliojet.layout.segment;

import org.xml.sax.Attributes;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.StructureElement;

/**
 * {@code Params.element}のfreeze結果です(2026-07-24新設、E-6増分3b-4
 * ——{@code docs/history/2026-07-24-e6-remaining-design-decision.md}の
 * StructureToken裁定)。
 *
 * <p>
 * {@link CSSElement}をそのまま{@link ParamsFields}に保持すると、
 * {@code CSSElement.precedingElement}チェーン経由で過去の要素列を
 * まるごと引き留める(保持の問題——共有自体は安全)。そこで記録時
 * freezeでは、レイアウト後の読み手({@link StructureElement}のjavadoc
 * 参照)が必要とする4フィールドだけを持つこの軽量tokenへ切り離す。
 * {@code atts}参照はイベント数線形の軽量メタデータとして許容
 * (codex裁定——{@code CSSElement}自体が描画時読み出しのため保持して
 * いる同じ参照であり、新たな保持は増えない)。
 * </p>
 *
 * <p>
 * <b>identity契約</b>: 同じ論理要素({@code elementKey})のtokenは
 * 再生セッション内でintern({@code SegmentExecutor})され、同一
 * インスタンスが渡る——Tagged PDFの構造タグ二重開き防止
 * ({@code PageBox.beginStruct}のidentity set。例: {@code <li>}の
 * principal boxとmarker boxは同じ要素を共有する)をlive経路と同じ
 * 形で保つ。
 * </p>
 */
public record StructureToken(long elementKey, String lName, String id, Attributes atts) implements StructureElement {

	/**
	 * 記録時freezeです({@link ParamsFields#freeze}から呼ばれる)。
	 *
	 * <ul>
	 * <li>{@code null}・freeze済みtokenはそのまま。</li>
	 * <li>{@code elementKey < 0}の{@link CSSElement}(擬似要素・匿名要素・
	 * at-page側)は<b>そのまま保持する</b>——これらは全て{@code CSSElement}の
	 * static singleton定数({@code precedingElement}を持たず保持は無害。
	 * 動的生成は{@code CSSProcessor}のelementKey採番経路のみ——2026-07-24
	 * 実査)であり、singleton共有こそがlive挙動のidentity(例: 匿名表の
	 * {@code ANON_TABLE}共有によるタグ二重開き防止)と完全に一致する。
	 * tokenへ写すと逆にidentityが壊れる(-1は擬似要素間で衝突するため
	 * internできない)。</li>
	 * <li>それ以外(文書中の実要素)は4フィールドだけのtokenへ写す。</li>
	 * </ul>
	 */
	public static StructureElement freeze(final StructureElement element) {
		if (element == null || element instanceof StructureToken) {
			return element;
		}
		if (element.elementKey() < 0) {
			return element;
		}
		return new StructureToken(element.elementKey(), element.lName(), element.id(), element.atts());
	}
}
