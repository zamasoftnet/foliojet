package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.container.ContainerType;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.ua.ContainerFacts;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * A length in container query units ({@code cqw} / {@code cqi}) (2026-08-15 stage 6;
 * css-contain-3, development record §5). Like {@code RelativeLengthValue} (em/ex/rem/ch),
 * not resolved at parse time; converted to absolute length by {@link #toAbsoluteLength}
 * at used-value computation (computed value). {@code ValueUtils.emExToAbsoluteLength}
 * handles both; 45 property implementations already use it, so individual properties
 * were not changed.
 *
 * <p>
 * The specification defines {@code cqw} as the physical width axis and {@code cqi} as
 * the writing mode's inline axis (height in vertical writing); they can differ for
 * {@code container-type: size} containers. This implementation supports only
 * {@code container-type: inline-size} (design §4), and {@code ContainerFacts} stores
 * only one used inline-size, so it simplifies by <b>resolving {@code cqw} and {@code cqi}
 * to the same value</b>. Supporting {@code container-type: size} containers
 * (distinguishing both axes) is future work.
 * </p>
 *
 * <p>
 * Searches the {@code CSSStyle.getParentStyle()} chain for the nearest ancestor query
 * container (no name specified; must have {@code container-type: inline-size}).
 * If absent, or its measured value is unknown ({@code NaN}, equivalent to pass 1),
 * resolves to 0 as specified (CSS Containment 3: without a container, cqw/cqi, etc.
 * compute to 0).
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
