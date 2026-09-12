package jp.cssj.test.unit._0510_text_spacing;

import static jp.cssj.test.unit._0510_text_spacing.InkGapTestSupport.*;
import static net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.*;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker;
import net.zamasoft.pdfg2d.font.GlyphBounds;
import net.zamasoft.pdfg2d.gc.font.*;

public class InkGapResolverTest extends TestCase {
	public void testBuiltinAj17HasNoOutlineAndUsesNominalTrim() {
		final var s = style(10, FontStyle.Direction.LTR);
		final var manager = net.zamasoft.pdfg2d.pdf.font.ConfigurablePDFFontSourceManager.getDefaultFontSourceManager();
		final var source = java.util.Arrays.stream(manager.lookup(null))
				.filter(f -> f.getFontName().contains("AJ17") && f.getDirection() == s.getDirection())
				.findFirst().orElseThrow();
		final var m = new net.zamasoft.pdfg2d.font.FontMetricsImpl(f -> f.createFont(), source, s);
		final int a = m.getFont().toGID('；'), b = m.getFont().toGID('（');
		assertTrue(Double.isNaN(inkStart(m, a, 10, s)));
		assertTrue(Double.isNaN(inkEnd(m, b, 10, s)));
		assertEquals(5.0, cappedPairTrim('；', m, a, 10, s, '（', m, b, 10, s), .001);
	}

	public void testIpaHorizontalDotAndVerticalSemicolon() throws Exception {
		final var horizontal = style(10, FontStyle.Direction.LTR);
		final var hm = realMetrics("ipagp.otf", horizontal);
		final int dot = hm.getFont().toGID('・');
		assertEquals(1.83, inkStart(hm, dot, 10, horizontal), .01);
		assertEquals(1.83, hm.getAdvance(dot) - inkEnd(hm, dot, 10, horizontal), .01);
		final var vertical = style(10, FontStyle.Direction.TB);
		final var vm = realMetrics("ipagp.otf", vertical);
		final int semi = vm.getFont().toGID('；'), me = vm.getFont().toGID('め');
		assertEquals(.47, vm.getAdvance(semi) - inkEnd(vm, semi, 10, vertical), .01);
		assertEquals(.43, inkStart(vm, me, 10, vertical), .01);
		assertEquals(.90, inkGap(vm, semi, 10, vertical, vm, me, 10, vertical, vm.getAdvance(semi)), .01);
		assertEquals(0.0, cappedPairTrim('；', vm, semi, 10, vertical, '（', vm,
				vm.getFont().toGID('（'), 10, vertical), 0);
	}

	public void testSignedOverhangIsCombinedBeforeClamping() {
		final var s = style(10, FontStyle.Direction.TB);
		final var m = metrics(s, s.getDirection(), gid -> gid == 1
				? new GlyphBounds(0, -500, 200, 140) : new GlyphBounds(0, gid == 2 ? -830 : -930, 200, -200), 880, 0, 0);
		assertEquals(10.2, inkEnd(m, 1, 10, s), 1e-9);
		assertEquals(.3, inkGap(m, 1, 10, s, m, 2, 10, s, 10), 1e-9);
		assertEquals(.3, cappedPairTrim('；', m, 1, 10, s, '（', m, 2, 10, s), 1e-9);
		assertEquals(0.0, cappedPairTrim('；', m, 1, 10, s, '（', m, 3, 10, s), 1e-9);
	}

	public void testSidewaysUsesFontAxisAndPlacement() {
		final var s = style(10, FontStyle.Direction.TB);
		final var m = metrics(s, FontStyle.Direction.LTR,
				gid -> new GlyphBounds(100.125, -700, 800.875, 100), 600, -50, 0);
		assertEquals(.50125, inkStart(m, 1, 10, s), 1e-9);
		assertEquals(7.50875, inkEnd(m, 1, 10, s), 1e-9);
	}

	public void testSyntheticWeightTableAndOptOut() {
		final int[] divisors = {28, 24, 20, 16, 12};
		for (int i = 0; i < divisors.length; ++i) {
			final var s = new FontStyleImpl(FontFamilyList.SERIF, 10, FontStyle.Style.NORMAL,
					FontStyle.Weight.values()[i + 4], FontStyle.Direction.LTR,
					FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
			final var m = metrics(s, s.getDirection(), gid -> new GlyphBounds(100, -700, 800, 100), 880, 0, 0);
			assertEquals(1 - 5.0 / divisors[i], inkStart(m, 1, 10, s), 1e-9);
			assertEquals(8 + 5.0 / divisors[i], inkEnd(m, 1, 10, s), 1e-9);
			final var off = new FontStyleImpl(s.getFamily(), 10, s.getStyle(), s.getWeight(), s.getDirection(),
					s.getPolicy(), FontFeatureSet.EMPTY, false, false);
			assertEquals(1.0, inkStart(m, 1, 10, off), 1e-9);
		}
	}

	/**
	 * 合成斜体は shear の幾何どおりに片側ずつ広げる(横: x' = x − 0.25y なので
	 * 上端(y<0)が右へ、下端(y>0)が左へ。縦: y' = y + 0.25x なので右端(x>0)が下へ)。
	 * 両端を一律に広げると、autospace の追い込みの容量が不要に減って
	 * 行内の配分が変わる(imageTest の 0060-inline/000-border で 0.245pt、2026-09-12)。
	 */
	public void testSyntheticItalicExpandsAlongTheShear() {
		for (final var direction : new FontStyle.Direction[] {FontStyle.Direction.LTR, FontStyle.Direction.TB}) {
			final var s = new FontStyleImpl(FontFamilyList.SERIF, 10, FontStyle.Style.ITALIC,
					FontStyle.Weight.W_400, direction, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
			final var m = metrics(s, direction, gid -> new GlyphBounds(100, -700, 800, 100), 880, 0, 0);
			// 横: 前端は下端(maxY=100)の分だけ左へ 0.25×100、後端は上端(−700)の分だけ右へ 0.25×700。
			// 縦: 前端は minX が正なので動かず、後端は maxX=800 の分だけ下へ 0.25×800。
			assertEquals(direction == FontStyle.Direction.TB ? 1.8 : .75, inkStart(m, 1, 10, s), 1e-9);
			assertEquals(direction == FontStyle.Direction.TB ? 11.8 : 9.75, inkEnd(m, 1, 10, s), 1e-9);
		}
	}

	public void testBlankBoundsFallBackAndZeroNominalDoesNotMeasure() {
		final var s = style(10, FontStyle.Direction.LTR);
		final var blank = metrics(s, s.getDirection(), gid -> null, 880, 0, 0);
		assertTrue(Double.isNaN(inkStart(blank, 1, 10, s)));
		assertTrue(Double.isNaN(inkGap(blank, 1, 10, s, blank, 2, 10, s, 10)));
		assertEquals(5.0, cappedPairTrim('；', blank, 1, 10, s, '（', blank, 2, 10, s), 0);
		final var unmeasured = metrics(s, s.getDirection(), gid -> { throw new AssertionError("unneeded bounds"); }, 880, 0, 0);
		assertEquals(0.0, cappedPairTrim('体', unmeasured, 1, 10, s, 'め', unmeasured, 2, 10, s), 0);
	}

	public void testRealCappedPairPureAcrossTrackingSplitAndRunTrims() throws Exception {
		final var s = style(10, FontStyle.Direction.TB);
		final var m = realMetrics("gothic-inkgap-test.ttf", s);
		final var run = text(s, m, "；（");
		final int a = run.glyphIds[0], b = run.glyphIds[1];
		final var tracker = new AutospaceTracker();
		tracker.glyphAdded(run, 10, run.chars, 0, (byte) 1, a);
		final double trim = tracker.trimBefore(run.chars, 1, b, run, m, 10, s);
		assertEquals(3.5, trim, .01);
		run.setLetterSpacing(.5);
		applyRunTrims(run);
		assertEquals(-trim, run.xAdvances().get(1), 1e-9);
		assertEquals(17.5, run.getAdvance(), .01);
		final var head = run.split(1);
		assertEquals(-trim, run.xAdvances().get(0), 1e-9);
		final double inverse = cappedPairTrim('；', head.getFontMetrics(), a, 10, head.getFontStyle(),
				'（', run.getFontMetrics(), b, 10, run.getFontStyle());
		assertEquals(trim, inverse, 0);
		run.addXAdvance(0, inverse);
		assertEquals(0.0, run.xAdvances().get(0), 1e-9);
		assertEquals(3.5, inkStart(m, b, 10, s), .01);
	}
}
