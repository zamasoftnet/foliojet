package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.container.ContainerType;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.ua.ContainerFacts;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * コンテナクエリ単位({@code cqw} / {@code cqi})の長さです(2026-08-15段6——
 * css-contain-3、docs/history/2026-08-15-container-queries-design.md §5)。
 * {@code RelativeLengthValue}(em/ex/rem/ch)と同じく、解析時には解決せず
 * 使用値計算時(computed value)に{@link #toAbsoluteLength}で絶対長さへ
 * 変換する({@code ValueUtils.emExToAbsoluteLength}が両方を扱う——45箇所の
 * プロパティ実装が既にそこを通るため、個々のプロパティを変更していない)。
 *
 * <p>
 * 仕様上、{@code cqw}は物理的な幅軸、{@code cqi}は書字方向のインライン軸
 * (縦書きでは高さ)を指し、{@code container-type: size}のコンテナでは
 * 別々の値になりうる。本実装は{@code container-type: inline-size}だけを
 * 対象とし(設計§4)、{@code ContainerFacts}が保持する事実も
 * used inline-size 1個だけなので、<b>{@code cqw}と{@code cqi}は同じ値に
 * 解決する</b>単純化を取る。{@code container-type: size}コンテナへの対応
 * (両軸の区別)は将来の課題。
 * </p>
 *
 * <p>
 * 最も近い祖先のクエリコンテナ(名前指定なし、{@code container-type:
 * inline-size}であること)を{@code CSSStyle.getParentStyle()}チェーンで
 * 探す。見つからない、または実測値が未確定({@code NaN}、パス1相当)なら
 * 仕様どおり0として解決する(CSS Containment 3「コンテナが無ければ
 * cqw/cqi等は0として計算する」)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContainerRelativeLengthValue implements LengthValue {
	private final Unit unit;

	private final double value;

	private ContainerRelativeLengthValue(Unit unit, double value) {
		this.unit = unit;
		this.value = value;
	}

	public static ContainerRelativeLengthValue of(Unit unit, double value) {
		return new ContainerRelativeLengthValue(unit, value);
	}

	public Unit getUnit() {
		return this.unit;
	}

	public double getValue() {
		return this.value;
	}

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style) {
		final UserAgent ua = style.getUserAgent();
		final ContainerFacts facts = ua.getUAContext().getContainerFacts();
		for (CSSStyle s = style.getParentStyle(); s != null; s = s.getParentStyle()) {
			final CSSElement ce = s.getCSSElement();
			if (ce == null || ce.elementKey < 0) {
				continue;
			}
			if (ContainerType.get(s) != ContainerTypeValue.INLINE_SIZE) {
				continue;
			}
			if (!facts.isInlineSizeContainer(ce.elementKey)) {
				continue;
			}
			final double inlineSize = facts.getInlineSize(ce.elementKey);
			if (Double.isNaN(inlineSize)) {
				break;
			}
			return AbsoluteLengthValue.create(ua, inlineSize * this.value / 100);
		}
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isNegative() {
		return this.value < 0;
	}

	public boolean isZero() {
		return this.value == 0;
	}

	public String toString() {
		return this.value + this.unit.name().toLowerCase(java.util.Locale.ROOT);
	}
}
