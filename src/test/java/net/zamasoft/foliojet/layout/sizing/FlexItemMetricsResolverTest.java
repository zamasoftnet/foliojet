package net.zamasoft.foliojet.layout.sizing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.FlexBasisValue;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * flex base size/hypothetical main size/自動最小サイズの導出テストです
 * (Flex F1b——consult-codex-2026-08-02-flexbox.txt Q3の検証条件:
 * definite/auto/content/%未確定、box-sizing、overflow、min-size:auto)。
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

	/** definite basisはそのままbase(min/maxはbaseへ未適用、hypotheticalでclamp——§9.2.3/§9.3)。 */
	public void testDefiniteBasis() {
		final FlexItemMetrics m = FlexItemMetricsResolver
				.resolve(input(pt(100), Double.NaN, 0, 60, 0, 0, false, false, 10, 200, Double.NaN));
		assertEquals(100.0, m.flexBaseMain(), 0);
		assertEquals(60.0, m.hypotheticalMain(), 0);
	}

	/** %basisはコンテナ主軸definiteなら解決。 */
	public void testPercentageBasisDefiniteContainer() {
		final FlexItemMetrics m = FlexItemMetricsResolver.resolve(input(
				FlexBasisValue.size(PercentageValue.create(50)), Double.NaN, 0, NONE, 0, 0, false, false, 0, 200, 400));
		assertEquals(200.0, m.flexBaseMain(), 0);
	}

	/** %basisはコンテナindefiniteならauto扱い(width→max-content)。 */
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

	/** basis:autoはwidth、widthもautoならmax-content。 */
	public void testAutoBasis() {
		assertEquals(120.0, FlexItemMetricsResolver
				.resolve(input(FlexBasisValue.AUTO_VALUE, 120, 0, NONE, 0, 0, false, false, 0, 200, Double.NaN))
				.flexBaseMain(), 0);
		assertEquals(200.0, FlexItemMetricsResolver
				.resolve(input(FlexBasisValue.AUTO_VALUE, Double.NaN, 0, NONE, 0, 0, false, false, 0, 200,
						Double.NaN))
				.flexBaseMain(), 0);
	}

	/** 未確定%の番兵がmax-content加算でInfinityになっても§9.7へ渡さない。 */
	public void testInfiniteMaxContentFallsBackToAutomaticMinimum() {
		final FlexItemMetrics m = FlexItemMetricsResolver.resolve(input(FlexBasisValue.AUTO_VALUE,
				Double.NaN, Double.NaN, NONE, 0, 0, false, false, 408, Double.POSITIVE_INFINITY, 576));
		assertEquals(408.0, m.flexBaseMain(), 0);
		assertEquals(408.0, m.hypotheticalMain(), 0);
	}

	/** basis:contentはwidthがあってもmax-content。 */
	public void testContentBasis() {
		assertEquals(200.0, FlexItemMetricsResolver
				.resolve(input(FlexBasisValue.CONTENT_VALUE, 120, 0, NONE, 0, 0, false, false, 0, 200, Double.NaN))
				.flexBaseMain(), 0);
	}

	/** box-sizing:border-boxは枠を引いてcontent-box内寸へ(basis/width/min/max全て)。 */
	public void testBorderBoxNormalization() {
		final FlexItemMetrics m = FlexItemMetricsResolver
				.resolve(input(pt(100), Double.NaN, 30, 90, 20, 5, true, false, 0, 200, Double.NaN));
		assertEquals(80.0, m.flexBaseMain(), 0);
		assertEquals(10.0, m.minMain(), 0);
		assertEquals(70.0, m.maxMain(), 0);
		// outerMainExtra=margin+frame
		assertEquals(25.0, m.outerMainExtra(), 0);
	}

	/** min-width:auto(§4.5): 非scrollableはmin(min-content, definite width)。 */
	public void testAutomaticMinimum() {
		// preferred definiteかつmin-contentより小さい→preferred
		assertEquals(50.0, FlexItemMetricsResolver
				.resolve(input(pt(10), 50, Double.NaN, NONE, 0, 0, false, false, 80, 200, Double.NaN)).minMain(), 0);
		// preferred auto→min-content
		assertEquals(80.0, FlexItemMetricsResolver
				.resolve(input(pt(10), Double.NaN, Double.NaN, NONE, 0, 0, false, false, 80, 200, Double.NaN))
				.minMain(), 0);
		// definite maxでさらにclamp
		assertEquals(60.0, FlexItemMetricsResolver
				.resolve(input(pt(10), Double.NaN, Double.NaN, 60, 0, 0, false, false, 80, 200, Double.NaN))
				.minMain(), 0);
	}

	/** scrollableの自動最小は0。 */
	public void testScrollableAutomaticMinimumZero() {
		assertEquals(0.0, FlexItemMetricsResolver
				.resolve(input(pt(10), Double.NaN, Double.NaN, NONE, 0, 0, false, true, 80, 200, Double.NaN))
				.minMain(), 0);
	}

	/** 明示minは自動最小より優先(§4.5はmin:autoのみ)。 */
	public void testExplicitMinWins() {
		assertEquals(5.0, FlexItemMetricsResolver
				.resolve(input(pt(10), Double.NaN, 5, NONE, 0, 0, false, false, 80, 200, Double.NaN)).minMain(), 0);
	}

	/** hypotheticalはbaseをmin/maxでclampした値、outer系はextra加算。 */
	public void testHypotheticalAndOuter() {
		final FlexItemMetrics m = FlexItemMetricsResolver
				.resolve(input(pt(100), Double.NaN, 0, NONE, 8, 12, false, false, 0, 200, Double.NaN));
		assertEquals(100.0, m.hypotheticalMain(), 0);
		assertEquals(120.0, m.outerHypotheticalMain(), 0);
		assertEquals(120.0, m.outerBaseMain(), 0);
	}
}
