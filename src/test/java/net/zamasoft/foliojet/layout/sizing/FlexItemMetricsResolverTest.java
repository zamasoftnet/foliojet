package net.zamasoft.foliojet.layout.sizing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.FlexBasisValue;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Tests derivation of flex base size, hypothetical main size, and automatic minimum size
 * (Flex F1b; validation conditions in consult-codex-2026-08-02-flexbox.txt Q3:
 * definite/auto/content/indefinite percentages, box-sizing, overflow, min-size:auto).
 */
public class FlexItemMetricsResolverTest extends TestCase {

	private static final double NONE = Double.POSITIVE_INFINITY;

	private static UserAgent ua() {
		return (UserAgent) java.lang.reflect.Proxy.newProxyInstance(FlexItemMetricsResolverTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					throw new UnsupportedOperationException(method.toString());
				});
	}

	private static FlexBasisValue pt(final double length) {
		return FlexBasisValue.size(AbsoluteLengthValue.create(ua(), length, Unit.PT));
	}

	private static FlexItemMetricsResolver.Input input(final FlexBasisValue basis, final double preferred,
			final double min, final double max, final double frame, final double margin, final boolean borderBox,
			final boolean scrollable, final double minContent, final double maxContent, final double container) {
		return new FlexItemMetricsResolver.Input(0, 1, 1, basis, preferred, min, max, frame, margin, borderBox,
				scrollable, minContent, maxContent, container);
	}

	/** A definite basis is the base as-is (min/max apply only when clamping hypothetical, not base; §9.2.3/§9.3). */
	public void testDefiniteBasis() {
		final FlexItemMetrics m = FlexItemMetricsResolver
				.resolve(input(pt(100), Double.NaN, 0, 60, 0, 0, false, false, 10, 200, Double.NaN));
		assertEquals(100.0, m.flexBaseMain(), 0);
		assertEquals(60.0, m.hypotheticalMain(), 0);
	}

	/** Resolve a percentage basis when the container's main axis is definite. */
	public void testPercentageBasisDefiniteContainer() {
		final FlexItemMetrics m = FlexItemMetricsResolver.resolve(input(
				FlexBasisValue.size(PercentageValue.create(50)), Double.NaN, 0, NONE, 0, 0, false, false, 0, 200, 400));
		assertEquals(200.0, m.flexBaseMain(), 0);
	}

	/** Treat a percentage basis as auto if the container is indefinite (width→max-content). */
	public void testPercentageBasisIndefiniteContainer() {
		final FlexItemMetrics widthWins = FlexItemMetricsResolver.resolve(input(
				FlexBasisValue.size(PercentageValue.create(50)), 120, 0, NONE, 0, 0, false, false, 0, 200,
				Double.NaN));
		assertEquals(120.0, widthWins.flexBaseMain(), 0);
		final FlexItemMetrics contentWins = FlexItemMetricsResolver.resolve(input(
				FlexBasisValue.size(PercentageValue.create(50)), Double.NaN, 0, NONE, 0, 0, false, false, 0, 200,
				Double.NaN));
		assertEquals(200.0, contentWins.flexBaseMain(), 0);
	}

	/** basis:auto uses width, or max-content if width is also auto. */
	public void testAutoBasis() {
		assertEquals(120.0, FlexItemMetricsResolver
				.resolve(input(FlexBasisValue.AUTO_VALUE, 120, 0, NONE, 0, 0, false, false, 0, 200, Double.NaN))
				.flexBaseMain(), 0);
		assertEquals(200.0, FlexItemMetricsResolver
				.resolve(input(FlexBasisValue.AUTO_VALUE, Double.NaN, 0, NONE, 0, 0, false, false, 0, 200,
						Double.NaN))
				.flexBaseMain(), 0);
	}

	/** Do not pass an indefinite percentage sentinel to §9.7, even if adding max-content turns it into Infinity. */
	public void testInfiniteMaxContentFallsBackToAutomaticMinimum() {
		final FlexItemMetrics m = FlexItemMetricsResolver.resolve(input(FlexBasisValue.AUTO_VALUE,
				Double.NaN, Double.NaN, NONE, 0, 0, false, false, 408, Double.POSITIVE_INFINITY, 576));
		assertEquals(408.0, m.flexBaseMain(), 0);
		assertEquals(408.0, m.hypotheticalMain(), 0);
	}

	/** basis:content uses max-content even when width is specified. */
	public void testContentBasis() {
		assertEquals(200.0, FlexItemMetricsResolver
				.resolve(input(FlexBasisValue.CONTENT_VALUE, 120, 0, NONE, 0, 0, false, false, 0, 200, Double.NaN))
				.flexBaseMain(), 0);
	}

	/** box-sizing:border-box subtracts the frame to obtain the inner content-box size (basis/width/min/max). */
	public void testBorderBoxNormalization() {
		final FlexItemMetrics m = FlexItemMetricsResolver
				.resolve(input(pt(100), Double.NaN, 30, 90, 20, 5, true, false, 0, 200, Double.NaN));
		assertEquals(80.0, m.flexBaseMain(), 0);
		assertEquals(10.0, m.minMain(), 0);
		assertEquals(70.0, m.maxMain(), 0);
		// outerMainExtra=margin+frame
		assertEquals(25.0, m.outerMainExtra(), 0);
	}

	/** min-width:auto (§4.5): non-scrollable uses min(min-content, definite width). */
	public void testAutomaticMinimum() {
		// Preferred size is definite and smaller than min-content → use preferred.
		assertEquals(50.0, FlexItemMetricsResolver
				.resolve(input(pt(10), 50, Double.NaN, NONE, 0, 0, false, false, 80, 200, Double.NaN)).minMain(), 0);
		// preferred auto→min-content
		assertEquals(80.0, FlexItemMetricsResolver
				.resolve(input(pt(10), Double.NaN, Double.NaN, NONE, 0, 0, false, false, 80, 200, Double.NaN))
				.minMain(), 0);
		// Clamp further by a definite max.
		assertEquals(60.0, FlexItemMetricsResolver
				.resolve(input(pt(10), Double.NaN, Double.NaN, 60, 0, 0, false, false, 80, 200, Double.NaN))
				.minMain(), 0);
	}

	/** The automatic minimum for scrollable items is 0. */
	public void testScrollableAutomaticMinimumZero() {
		assertEquals(0.0, FlexItemMetricsResolver
				.resolve(input(pt(10), Double.NaN, Double.NaN, NONE, 0, 0, false, true, 80, 200, Double.NaN))
				.minMain(), 0);
	}

	/** An explicit min takes precedence over the automatic minimum (§4.5 applies only to min:auto). */
	public void testExplicitMinWins() {
		assertEquals(5.0, FlexItemMetricsResolver
				.resolve(input(pt(10), Double.NaN, 5, NONE, 0, 0, false, false, 80, 200, Double.NaN)).minMain(), 0);
	}

	/** hypothetical is base clamped by min/max; outer sizes add extra. */
	public void testHypotheticalAndOuter() {
		final FlexItemMetrics m = FlexItemMetricsResolver
				.resolve(input(pt(100), Double.NaN, 0, NONE, 8, 12, false, false, 0, 200, Double.NaN));
		assertEquals(100.0, m.hypotheticalMain(), 0);
		assertEquals(120.0, m.outerHypotheticalMain(), 0);
		assertEquals(120.0, m.outerBaseMain(), 0);
	}
}
