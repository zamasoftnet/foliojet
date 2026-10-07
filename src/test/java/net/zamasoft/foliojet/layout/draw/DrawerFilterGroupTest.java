package net.zamasoft.foliojet.layout.draw;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.NoOpGC;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Tests that {@code filter} on a {@link Drawer} groups its subtree into a single layer
 * (added 2026-09-03, filter-element-group-design.md).
 *
 * <p>
 * A spy GC records layer opening/closing and effect application to lock down that (1) nested layers
 * close from the inside out, (2) drawables within a layer see only effects remaining after excluding
 * the enclosing layer's effects, (3) page-output drawables use the page GC even within layers, and
 * (4) outputs without layer support and the old {@code draw(GC)} do not create layers.
 * </p>
 */
public class DrawerFilterGroupTest extends TestCase {

	/** A GC supporting all capabilities that records layer opening/closing and effects. */
	private static class SpyGC extends NoOpGC {
		final List<String> log;
		final String name;

		SpyGC(final String name, final List<String> log) {
			super(null);
			this.name = name;
			this.log = log;
		}

		@Override
		public boolean supports(final Capability capability) {
			return true;
		}

		@Override
		public void transform(final java.awt.geom.AffineTransform at) {
			super.transform(at);
			final double[] m = new double[6];
			at.getMatrix(m);
			this.log.add(String.format(java.util.Locale.ROOT, "transform %s [%.2f %.2f %.2f %.2f %.2f %.2f]", this.name,
					m[0] + 0.0, m[1] + 0.0, m[2] + 0.0, m[3] + 0.0, m[4] + 0.0, m[5] + 0.0));
		}

		@Override
		public GroupImageGC createFilterGroup(final double width, final double height) {
			final String groupName = this.name + "/g" + this.log.size();
			this.log.add("open " + groupName + " " + (int) width + "x" + (int) height);
			return new SpyGroup(groupName, this.log);
		}

		@Override
		public GroupEffectsResult drawGroupEffects(final Image image, final GroupEffects effects) {
			this.log.add("effects on " + this.name + " from " + image.getAltString() + " blur=" + effects.blurSigma()
					+ " matrix=" + (effects.colorMatrix() != null));
			return GroupEffectsResult.VECTOR;
		}
	}

	private static final class SpyGroup extends SpyGC implements GroupImageGC {
		SpyGroup(final String name, final List<String> log) {
			super(name, log);
		}

		@Override
		public Image finish() {
			this.log.add("close " + this.name);
			return new NoOpGC.NoOpImage(1, 1) {
				@Override
				public String getAltString() {
					return SpyGroup.this.name;
				}
			};
		}
	}

	/** A Drawable that records the GC name and the effective filter's textual representation when drawing. */
	private static final class Probe implements Drawable {
		private final String name;
		private final FilterValue filter;
		private final List<String> log;

		Probe(final String name, final FilterValue filter, final List<String> log) {
			this.name = name;
			this.filter = filter;
			this.log = log;
		}

		public void draw(final GC gc, final double x, final double y) {
			final FilterValue f = FilterScope.effective(gc, this.filter);
			this.log.add(this.name + " on " + spyName(gc) + " filter=" + (f.isNone() ? "none" : f.declared));
		}
	}

	/** A stand-in for a page-output drawable (such as a link annotation). */
	private static final class PageProbe implements PageOutputDrawable {
		private final List<String> log;

		PageProbe(final List<String> log) {
			this.log = log;
		}

		public void draw(final GC gc, final double x, final double y) {
			this.log.add("page-output on " + spyName(gc) + (gc.getTransform().isIdentity() ? " identity" : " transformed"));
		}
	}

	private static String spyName(GC gc) {
		while (gc != null) {
			if (gc instanceof SpyGC spy) {
				return spy.name;
			}
			gc = gc instanceof net.zamasoft.foliojet.layout.util.DelegatingGC d ? d.delegate() : null;
		}
		return "?";
	}

	private static final float[] GRAY = { .2126f, .7152f, .0722f, 0, 0, .2126f, .7152f, .0722f, 0, 0, .2126f, .7152f,
			.0722f, 0, 0, 0, 0, 0, 1, 0 };

	private static FilterValue own(final String declared, final double blur) {
		return new FilterValue(1f, GRAY, blur, null, declared);
	}

	private static BlockParams params(final FilterValue composed) {
		final BlockParams params = new BlockParams();
		params.filter = composed;
		return params;
	}

	public void testNestedGroupsCloseInnerFirstAndProbesSeeOnlyTheRemainder() throws GraphicsException {
		final List<String> log = new ArrayList<>();
		final FilterValue outer = own("outer", 0);
		final FilterValue inner = own("inner", 2);
		final FilterValue innerComposed = outer.compose(inner);
		// Preserve the own declarations of descendants that cannot have layers (such as ::first-line).
		final FilterValue leaf = own("leaf", 0);
		final FilterValue leafComposed = innerComposed.compose(leaf);

		final Drawer root = new Drawer(0);
		root.visitDrawable(new Probe("root", FilterValue.NONE, log), 0, 0);
		final Drawer a = new Drawer(params(outer));
		root.visitDrawer(a);
		a.visitDrawable(new Probe("a", outer, log), 0, 0);
		final Drawer b = new Drawer(params(innerComposed));
		a.visitDrawer(b);
		b.visitDrawable(new Probe("b", innerComposed, log), 0, 0);
		b.visitDrawable(new Probe("b-leaf", leafComposed, log), 0, 0);
		b.visitDrawable(new PageProbe(log), 0, 0);
		a.visitDrawable(new Probe("a-after", outer, log), 0, 0);

		root.draw(new SpyGC("page", log), 100, 200);

		assertEquals(String.join("\n", log), List.of(//
				"root on page filter=none", //
				"open page/g1 100x200", //
				"a on page/g1 filter=none", //
				"a-after on page/g1 filter=none", //
				"open page/g1/g4 100x200", //
				"b on page/g1/g4 filter=none", //
				"b-leaf on page/g1/g4 filter=leaf", //
				"page-output on page identity", //
				"close page/g1/g4", //
				"effects on page/g1 from page/g1/g4 blur=2.0 matrix=true", //
				"close page/g1", //
				"effects on page from page/g1 blur=0.0 matrix=true"), log);
	}

	public void testSameDeclarationOnParentAndChildOpensTwoGroups() throws GraphicsException {
		final List<String> log = new ArrayList<>();
		// Parsed values for the same rule are shared, so forElement() copies them for each element.
		final FilterValue shared = own("shared", 0);
		final FilterValue parent = shared.forElement();
		final FilterValue child = parent.compose(shared.forElement());
		assertNotSame(parent, child.own());

		final Drawer root = new Drawer(0);
		final Drawer a = new Drawer(params(parent));
		root.visitDrawer(a);
		final Drawer b = new Drawer(params(child));
		a.visitDrawer(b);
		b.visitDrawable(new Probe("b", child, log), 0, 0);
		root.draw(new SpyGC("page", log), 10, 10);

		assertEquals(2, log.stream().filter(s -> s.startsWith("open ")).count());
		assertTrue(log.toString(), log.contains("b on page/g0/g1 filter=none"));
	}

	public void testNoGroupWithoutCapabilityOrPageSize() throws GraphicsException {
		final List<String> log = new ArrayList<>();
		final FilterValue outer = own("outer", 0);
		final Drawer root = new Drawer(0);
		final Drawer a = new Drawer(params(outer));
		root.visitDrawer(a);
		a.visitDrawable(new Probe("a", outer, log), 0, 0);

		// The old draw(GC) does not create layers (per-drawable approximation).
		root.draw(new SpyGC("page", log));
		assertEquals(List.of("a on page filter=outer"), log);

		// Do not create layers if the output lacks GROUP_FILTER.
		log.clear();
		root.draw(new SpyGC("plain", log) {
			@Override
			public boolean supports(final Capability capability) {
				return false;
			}
		}, 10, 10);
		assertEquals(List.of("a on plain filter=outer"), log);
	}

	/**
	 * A Drawer that adopts an element's transform creates its layer in element coordinates
	 * (2026-09-03, filter-local-space-design.md): apply P=T·Tr(B) to the outer GC only between
	 * createFilterGroup and drawGroupEffects, and apply Q=Tr(-B)·T⁻¹ as the layer's base transform.
	 * While drawing descendants, the page GC retains its transform from entry.
	 */
	public void testAdoptedTransformPlacesTheLayerInElementSpace() throws GraphicsException {
		final List<String> log = new ArrayList<>();
		final FilterValue outer = own("outer", 0);
		final BlockParams params = params(outer);
		final Drawer root = new Drawer(0);
		final Drawer a = new Drawer(params, new java.awt.geom.AffineTransform());
		a.adoptTransform(params, java.awt.geom.AffineTransform.getScaleInstance(2, 2));
		root.visitDrawer(a);
		a.visitDrawable(new Probe("a", outer, log), 0, 0);
		a.visitDrawable(new PageProbe(log), 0, 0);
		root.draw(new SpyGC("page", log), 100, 200);
		assertEquals(String.join("\n", log), List.of(//
				"transform page [2.00 0.00 0.00 2.00 0.00 0.00]", //
				"open page/g1 50x100", //
				"transform page/g1 [0.50 0.00 0.00 0.50 0.00 0.00]", //
				"a on page/g1 filter=none", //
				"page-output on page identity", //
				"close page/g1", //
				"transform page [2.00 0.00 0.00 2.00 0.00 0.00]", //
				"effects on page from page/g1 blur=0.0 matrix=true"), log);
	}

	/** An identity transform (nothing adopted and fallback is also identity) emits no transform commands. */
	public void testIdentityTransformEmitsNoTransformCommands() throws GraphicsException {
		final List<String> log = new ArrayList<>();
		final FilterValue outer = own("outer", 0);
		final BlockParams params = params(outer);
		final Drawer root = new Drawer(0);
		final Drawer a = new Drawer(params, new java.awt.geom.AffineTransform());
		a.adoptTransform(params, new java.awt.geom.AffineTransform());
		// Ignore adoption from another element's params.
		a.adoptTransform(params(outer), java.awt.geom.AffineTransform.getScaleInstance(3, 3));
		root.visitDrawer(a);
		a.visitDrawable(new Probe("a", outer, log), 0, 0);
		root.draw(new SpyGC("page", log), 100, 200);
		assertEquals(0, log.stream().filter(s -> s.startsWith("transform ")).count());
		assertTrue(log.toString(), log.contains("open page/g0 100x200"));
	}

	/**
	 * Nested Drawers sharing the same params (= the same FilterValue own), such as a table's outer
	 * positioning block and TableBox, open only one layer: the enclosing layer already applies the same own.
	 */
	public void testSharedParamsNestedDrawerOpensOneGroup() throws GraphicsException {
		final List<String> log = new ArrayList<>();
		final FilterValue outer = own("outer", 0);
		final BlockParams params = params(outer);
		final Drawer root = new Drawer(0);
		final Drawer wrapper = new Drawer(params, null);
		root.visitDrawer(wrapper);
		final Drawer table = new Drawer(params, null);
		wrapper.visitDrawer(table);
		table.visitDrawable(new Probe("cell", outer, log), 0, 0);
		root.draw(new SpyGC("page", log), 10, 10);
		assertEquals(log.toString(), 1, log.stream().filter(s -> s.startsWith("open ")).count());
		assertTrue(log.toString(), log.contains("cell on page/g0 filter=none"));
	}

	public void testDumpShowsTheFilterOnTheDrawerLine() {
		final Drawer root = new Drawer(0);
		root.visitDrawer(new Drawer(params(own("grayscale(1.00)", 0))));
		root.visitDrawer(new Drawer(params(FilterValue.NONE)));
		final StringBuilder sb = new StringBuilder();
		root.dump(sb, "");
		assertEquals("drawer z=0\n  drawer z=0 filter=[grayscale(1.00)]\n  drawer z=0\n", sb.toString());
	}
}
