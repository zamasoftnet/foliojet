package net.zamasoft.foliojet.layout.util;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.css3.ConicGradientValue;
import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.foliojet.css.value.css3.GradientStops;
import net.zamasoft.foliojet.css.value.css3.LinearGradientValue;
import net.zamasoft.foliojet.css.value.css3.RadialGradientValue;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxShadow;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UAContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.NoOpGC;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;
import net.zamasoft.pdfg2d.gc.paint.SpreadMethod;

/**
 * 出力先が厳密に描ける機能は厳密経路を、描けなければ近似して2822を
 * 報告することの検証です(2026-08-29)。
 */
public class ExactRenderingTest extends TestCase {
	/** 能力を指定でき、呼び出しを記録するGC。 */
	private static class FakeGC extends NoOpGC {
		final EnumSet<Capability> caps;
		final List<double[]> blurred = new ArrayList<>();
		final List<Paint> paints = new ArrayList<>();
		final List<GroupEffects> effects = new ArrayList<>();
		final List<BlendMode> imageBlends = new ArrayList<>();
		int fills = 0, groups = 0;
		private BlendMode blend = BlendMode.NORMAL;

		FakeGC(final EnumSet<Capability> caps) {
			super(null);
			this.caps = caps;
		}

		@Override
		public boolean supports(final Capability capability) {
			return this.caps.contains(capability);
		}

		@Override
		public void fillBlurred(final Shape shape, final double sigma) {
			this.blurred.add(new double[] { sigma });
		}

		@Override
		public void fill(final Shape shape) {
			++this.fills;
		}

		@Override
		public void setFillPaint(final Paint paint) {
			this.paints.add(paint);
		}

		@Override
		public void setBlendMode(final BlendMode mode) {
			this.blend = mode;
		}

		@Override
		public BlendMode getBlendMode() {
			return this.blend;
		}

		@Override
		public void drawImage(final Image image, final GroupEffects effects) {
			this.effects.add(effects);
			this.imageBlends.add(this.blend);
		}

		@Override
		public void drawImage(final Image image) {
			this.effects.add(null);
			this.imageBlends.add(this.blend);
		}

		@Override
		public GroupImageGC createGroupImage(final double width, final double height) {
			++this.groups;
			return new FakeGroup(this.caps);
		}
	}

	private static final class FakeGroup extends FakeGC implements GroupImageGC {
		FakeGroup(final EnumSet<Capability> caps) {
			super(caps);
		}

		@Override
		public Image finish() {
			return new Image() {
				public double getWidth() {
					return 1;
				}

				public double getHeight() {
					return 1;
				}

				public void drawTo(final GC gc) {
					// nothing
				}

				public String getAltString() {
					return null;
				}
			};
		}
	}

	private static FakeGC exact() {
		return new FakeGC(EnumSet.allOf(GC.Capability.class));
	}

	private static FakeGC approx() {
		return new FakeGC(EnumSet.noneOf(GC.Capability.class));
	}

	private static final Rectangle2D BOX = new Rectangle2D.Double(0, 0, 100, 50);

	// ------------------------------------------------------------------
	// box-shadow
	// ------------------------------------------------------------------

	private static RectFrame shadowFrame(final boolean inset) {
		return RectFrame.create(null, null, null, null,
				new BoxShadow[] { new BoxShadow(2, 3, 8, 1, RGBAColor.create(0, 0, 0, 0.5f), inset) }, null);
	}

	public void testBoxShadowBlurIsExactWhenSupported() throws GraphicsException {
		final FakeGC gc = exact();
		BoxDecorationRenderer.drawOuterShadows(gc, shadowFrame(false), 0, 0, 100, 50);
		assertEquals("ぼかしは1回のfillBlurred", 1, gc.blurred.size());
		assertEquals("σ=blur/2", 4.0, gc.blurred.get(0)[0], 1e-9);
		assertEquals(0, gc.fills);
	}

	public void testBoxShadowBlurIsApproximatedOtherwise() throws GraphicsException {
		final FakeGC gc = approx();
		BoxDecorationRenderer.drawOuterShadows(gc, shadowFrame(false), 0, 0, 100, 50);
		assertEquals(0, gc.blurred.size());
		assertEquals("12段の同心塗り", BoxDecorationRenderer.BLUR_STEPS.length, gc.fills);
	}

	public void testInsetShadowBlurIsExactWhenSupported() throws GraphicsException {
		final FakeGC gc = exact();
		BoxDecorationRenderer.drawInsetShadows(gc, shadowFrame(true), 0, 0, 100, 50);
		assertEquals(1, gc.blurred.size());
		assertEquals(4.0, gc.blurred.get(0)[0], 1e-9);
	}

	// ------------------------------------------------------------------
	// gradients
	// ------------------------------------------------------------------

	private static GradientStops stops(final double a, final double b) {
		return GradientStops.ofFractions(new double[] { a, b },
				new Color[] { RGBColor.create(1f, 0, 0), RGBColor.create(0, 0, 1f) });
	}

	public void testConicGradientUsesConicPaintWhenSupported() throws GraphicsException {
		final ConicGradientValue conic = new ConicGradientValue(Math.PI / 2, PercentageValue.create(50),
				PercentageValue.create(50), stops(0, 1), false);
		final FakeGC gc = exact();
		conic.fill(gc, BOX, BOX);
		assertEquals(1, gc.paints.size());
		assertTrue(gc.paints.get(0) instanceof ConicGradient);
		final ConicGradient paint = (ConicGradient) gc.paints.get(0);
		assertEquals(50.0, paint.cx(), 1e-9);
		assertEquals(25.0, paint.cy(), 1e-9);
		assertEquals(Math.PI / 2, paint.startAngle(), 1e-9);
		assertEquals(1, gc.fills);

		final FakeGC wedges = approx();
		conic.fill(wedges, BOX, BOX);
		assertTrue("扇形で近似", wedges.fills > 100);
		for (final Paint p : wedges.paints) {
			assertTrue(p instanceof Color);
		}
	}

	public void testRepeatingLinearGradientUsesRepeatSpreadWhenSupported() throws GraphicsException {
		final LinearGradientValue linear = new LinearGradientValue(Math.PI / 2, stops(0, 0.1), true);
		final FakeGC gc = exact();
		linear.fill(gc, BOX, BOX);
		final LinearGradient paint = (LinearGradient) gc.paints.get(0);
		assertEquals(SpreadMethod.REPEAT, paint.spread());
		// 1周期=勾配線(幅100)の10%
		assertEquals(10.0, Math.hypot(paint.x2() - paint.x1(), paint.y2() - paint.y1()), 1e-6);
		assertEquals(0.0, paint.fractions()[0], 1e-9);
		assertEquals(1.0, paint.fractions()[paint.fractions().length - 1], 1e-9);

		final FakeGC unrolled = approx();
		linear.fill(unrolled, BOX, BOX);
		final LinearGradient expanded = (LinearGradient) unrolled.paints.get(0);
		assertEquals(SpreadMethod.PAD, expanded.spread());
		assertTrue("周期を展開する", expanded.fractions().length >= 20);
	}

	public void testRepeatingRadialGradientUsesRepeatSpreadWhenSupported() throws GraphicsException {
		final RadialGradientValue radial = new RadialGradientValue(true, RadialGradientValue.Size.FARTHEST_CORNER,
				null, null, PercentageValue.create(50), PercentageValue.create(50), stops(0, 0.25), true);
		final FakeGC gc = exact();
		radial.fill(gc, BOX, BOX);
		final RadialGradient paint = (RadialGradient) gc.paints.get(0);
		assertEquals(SpreadMethod.REPEAT, paint.spread());
		assertEquals(0.25 * Math.hypot(50, 25), paint.radius(), 1e-6);
	}

	public void testPeriodStartsAtGradientStart() {
		// 停止が25%から始まる周期50%: 始点(0%)の色は周期関数として補間される
		final GradientStops.Period p = GradientStops.ofFractions(new double[] { 0.25, 0.75 },
				new Color[] { RGBColor.create(1f, 0, 0), RGBColor.create(0, 0, 1f) }).resolvePeriod(100);
		assertEquals(0.5, p.length(), 1e-9);
		assertEquals(0.0, p.fractions()[0], 1e-9);
		assertEquals(1.0, p.fractions()[p.fractions().length - 1], 1e-9);
		for (int i = 1; i < p.fractions().length; ++i) {
			assertTrue(p.fractions()[i] > p.fractions()[i - 1]);
		}
		final Color first = p.colors()[0], last = p.colors()[p.colors().length - 1];
		assertEquals(first.getRed(), last.getRed(), 1e-6);
		assertEquals(first.getBlue(), last.getBlue(), 1e-6);
		assertEquals("始点は赤と青の中間", 0.5f, first.getRed(), 1e-3);
	}

	public void testPeriodKeepsHardStops() {
		// repeating-linear-gradient(#036 0 50%, #9cf 50% 100%): 位相がそろって
		// いるので周期は元の停止そのまま。末尾の停止が位置0へ回り込んで
		// 縞がぼやける欠陥の回帰(2026-08-29、PNG出力の実測で発見)
		final Color dark = RGBColor.create(0, 0.2f, 0.4f), light = RGBColor.create(0.6f, 0.8f, 1f);
		final GradientStops.Period p = GradientStops
				.ofFractions(new double[] { 0, 0.5, 0.5, 1 }, new Color[] { dark, dark, light, light })
				.resolvePeriod(100);
		assertEquals(1.0, p.length(), 1e-9);
		assertEquals(4, p.colors().length);
		assertEquals("周期の先頭は暗い色", dark.getBlue(), p.colors()[0].getBlue(), 1e-6);
		assertEquals("周期の末尾は明るい色", light.getBlue(), p.colors()[3].getBlue(), 1e-6);
		// 前半は暗い色のまま(境目まで明るくならない)
		assertEquals(dark.getRed(), p.colors()[1].getRed(), 1e-6);
		assertEquals(light.getRed(), p.colors()[2].getRed(), 1e-6);
		assertTrue("ハードストップの位置はほぼ同じ", Math.abs(p.fractions()[2] - p.fractions()[1]) < 1e-4);
		for (int i = 1; i < p.fractions().length; ++i) {
			assertTrue(p.fractions()[i] > p.fractions()[i - 1]);
		}
	}

	public void testPeriodKeepsHardStopsWhenShifted() {
		// 位相がずれている場合(25%から始まる周期50%)も、回り込んだ停止が
		// 同じ位置の停止より前に来て、進行方向の色が入れ替わらない
		final Color dark = RGBColor.create(0, 0, 0), light = RGBColor.create(1f, 1f, 1f);
		final GradientStops.Period p = GradientStops
				.ofFractions(new double[] { 0.25, 0.5, 0.5, 0.75 }, new Color[] { dark, dark, light, light })
				.resolvePeriod(100);
		assertEquals(0.5, p.length(), 1e-9);
		// 位相0(=勾配線の始点)は周期の後半にあたるので明るい色
		assertEquals("始点は明るい色", 1f, p.colors()[0].getRed(), 1e-3);
		for (int i = 1; i < p.fractions().length; ++i) {
			assertTrue(p.fractions()[i] > p.fractions()[i - 1]);
		}
	}

	public void testRepeatUnrollingReportsOnlyWhenCapped() {
		assertFalse(stops(0, 0.1).resolve(100, true, 1).capped());
		assertTrue(stops(0, 0.001).resolve(100, true, 1).capped());
	}

	// ------------------------------------------------------------------
	// filter / mix-blend-mode
	// ------------------------------------------------------------------

	private static PageBox pageBox(final UserAgent ua) {
		final BlockParams params = new BlockParams();
		params.size = Dimension.create(200, 100, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		params.fontStyle = new net.zamasoft.pdfg2d.gc.font.FontStyleImpl(
				net.zamasoft.pdfg2d.gc.font.FontFamilyList.SERIF, 12, net.zamasoft.pdfg2d.gc.font.FontStyle.Style.NORMAL,
				net.zamasoft.pdfg2d.gc.font.FontStyle.Weight.W_400, net.zamasoft.pdfg2d.gc.font.FontStyle.Direction.LTR,
				net.zamasoft.pdfg2d.gc.font.FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		return new PageBox(params, ua);
	}

	private static final class Box extends AbstractDrawable {
		Box(final PageBox pageBox) {
			super(pageBox, null, 1f, new AffineTransform());
		}

		@Override
		public void innerDraw(final GC gc, final double x, final double y) throws GraphicsException {
			gc.setFillPaint(RGBColor.create(0, 1f, 0));
			gc.fill(BOX);
		}
	}

	private static FilterValue grayBlur() {
		final float[] m = new float[20];
		m[0] = m[1] = m[2] = m[5] = m[6] = m[7] = m[10] = m[11] = m[12] = 1f / 3;
		m[18] = 1f;
		return new FilterValue(0.5f, m, 2.0, new FilterValue.DropShadow(1, 2, 6, RGBColor.create(0, 0, 0)),
				"grayscale(100%) blur(2pt) drop-shadow(1pt 2pt 6pt #000) opacity(50%)");
	}

	public void testFilterUsesGroupEffectsWhenSupported() throws GraphicsException {
		final FakeGC gc = exact();
		new Box(pageBox(null)).withFilter(grayBlur()).draw(gc, 0, 0);
		assertEquals(1, gc.groups);
		assertEquals(1, gc.effects.size());
		final GroupEffects e = gc.effects.get(0);
		assertNotNull(e);
		assertNotNull(e.colorMatrix());
		assertEquals(2.0, e.blurSigma(), 1e-9);
		assertEquals("drop-shadowのσは半径の半分", 3.0, e.dropShadow().sigma(), 1e-9);
		assertEquals(0.5, e.opacity(), 1e-6);
		assertEquals("内容は層へ描く(ページのGCには直接描かない)", 0, gc.fills);
	}

	public void testFilterFallsBackToPerDrawableOtherwise() throws GraphicsException {
		final FakeGC gc = approx();
		new Box(pageBox(null)).withFilter(grayBlur()).draw(gc, 0, 0);
		assertEquals("不透明度だけの透明化グループ", 1, gc.groups);
		assertEquals(1, gc.effects.size());
		assertNull("効果付きのdrawImageは使わない", gc.effects.get(0));
	}

	public void testBlendGroupWhenSupported() throws GraphicsException {
		final FakeGC gc = exact();
		new Box(pageBox(null)).withBlendMode(BlendMode.MULTIPLY).draw(gc, 0, 0);
		assertEquals("層にしてからブレンド", 1, gc.groups);
		assertEquals(BlendMode.MULTIPLY, gc.imageBlends.get(0));
		assertEquals("終了後は元に戻す", BlendMode.NORMAL, gc.getBlendMode());

		final FakeGC perDrawable = approx();
		new Box(pageBox(null)).withBlendMode(BlendMode.MULTIPLY).draw(perDrawable, 0, 0);
		assertEquals(0, perDrawable.groups);
		assertEquals(BlendMode.NORMAL, perDrawable.getBlendMode());
	}

	// ------------------------------------------------------------------
	// 2822
	// ------------------------------------------------------------------

	private final List<String[]> messages = new ArrayList<>();

	private UserAgent ua(final String outputType) {
		final UAContext context = new UAContext();
		return (UserAgent) Proxy.newProxyInstance(ExactRenderingTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getUAContext":
						return context;
					case "getProperty":
						return "output.type".equals(args[0]) ? outputType : null;
					case "message":
						if (((Short) args[0]).shortValue() == MessageCodes.WARN_APPROXIMATED_RENDERING) {
							this.messages.add((String[]) args[1]);
						}
						return null;
					case "toString":
						return "ExactRenderingTest.UserAgent";
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					default:
						throw new UnsupportedOperationException(method.toString());
					}
				});
	}

	public void testApproximationIsReportedOncePerDocumentWithOutputType() throws GraphicsException {
		final GC gc = ApproximationGC.wrap(approx(), this.ua(null));
		BoxDecorationRenderer.drawOuterShadows(gc, shadowFrame(false), 0, 0, 100, 50);
		BoxDecorationRenderer.drawOuterShadows(gc, shadowFrame(false), 0, 0, 100, 50);
		BoxDecorationRenderer.drawInsetShadows(gc, shadowFrame(true), 0, 0, 100, 50);
		assertEquals("同じ近似は文書ごとに1回", 1, this.messages.size());
		assertEquals("box-shadow", this.messages.get(0)[0]);
		assertEquals("既定の出力形式", "application/pdf", this.messages.get(0)[1]);
		// 近似の内容は鍵ではなく利用者の言語の文面で入る
		assertEquals(MessageCodeUtils.detail(BoxDecorationRenderer.BLUR_DETAIL), this.messages.get(0)[2]);
		assertFalse("鍵がそのまま出ない", BoxDecorationRenderer.BLUR_DETAIL.equals(this.messages.get(0)[2]));

		// 別の機能は別に数える。FilterGCの内側からでも届く
		final GC filtered = new FilterGC(gc, FilterValue.NONE);
		new ConicGradientValue(0, PercentageValue.create(50), PercentageValue.create(50), stops(0, 1), false)
				.fill(filtered, BOX, BOX);
		assertEquals(2, this.messages.size());
		assertEquals("background-image", this.messages.get(1)[0]);
		assertTrue(this.messages.get(1)[2].contains("conic-gradient()"));
	}

	public void testNothingIsReportedWhenExact() throws GraphicsException {
		final GC gc = ApproximationGC.wrap(exact(), this.ua("image/png"));
		BoxDecorationRenderer.drawOuterShadows(gc, shadowFrame(false), 0, 0, 100, 50);
		new LinearGradientValue(0, stops(0, 0.1), true).fill(gc, BOX, BOX);
		new Box(pageBox(null)).withFilter(grayBlur()).withBlendMode(BlendMode.MULTIPLY).draw(gc, 0, 0);
		assertTrue(this.messages.isEmpty());
	}

	public void testOutputTypeAppearsInMessage() throws GraphicsException {
		final GC gc = ApproximationGC.wrap(approx(), this.ua("image/png"));
		new Box(pageBox(null)).withFilter(grayBlur()).withBlendMode(BlendMode.MULTIPLY).draw(gc, 0, 0);
		assertEquals(2, this.messages.size());
		for (final String[] m : this.messages) {
			assertEquals("image/png", m[1]);
		}
		assertEquals("mix-blend-mode", this.messages.get(0)[0]);
		assertEquals("filter", this.messages.get(1)[0]);
	}

	public void testRepeatCapIsReportedOnlyWhenHit() throws GraphicsException {
		final GC gc = ApproximationGC.wrap(approx(), this.ua(null));
		new LinearGradientValue(0, stops(0, 0.1), true).fill(gc, BOX, BOX);
		assertTrue("打ち切らなければ展開は厳密", this.messages.isEmpty());
		new LinearGradientValue(0, stops(0, 0.001), true).fill(gc, BOX, BOX);
		assertEquals(1, this.messages.size());
		assertEquals("background-image", this.messages.get(0)[0]);
	}
}
