package net.zamasoft.foliojet.layout.draw;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.NoOpGC;

/**
 * Unit tests for {@link Drawer}'s artifact attribute and shared views
 * (added 2026-07-25, rescue splitting, increment 2. <b>The production path does not set
 * the artifact attribute yet</b>).
 *
 * <p>
 * The most important property locked down here is that shared views do not change display-list order.
 * The current {@link Drawer#draw} draws regular Drawables first, then stably sorts child Drawers by z,
 * so adding an artifact wrapper Drawer <b>as a child</b> would change the existing stacking order
 * (recommendation §3). This is why shared views are used.
 * </p>
 */
public class DrawerArtifactViewTest extends TestCase {

	/** A Drawable that only records drawing order. */
	private static final class Marker implements Drawable {
		private final String name;
		private final List<String> log;

		Marker(final String name, final List<String> log) {
			this.name = name;
			this.log = log;
		}

		public void draw(final GC gc, final double x, final double y) {
			this.log.add(this.name);
		}

		public String describe() {
			return "marker " + this.name;
		}
	}

	private static String dump(final Drawer drawer) {
		final StringBuilder sb = new StringBuilder();
		drawer.dump(sb, "");
		return sb.toString();
	}

	// ------------------------------------------------------------------
	// Non-artifact by default (preserves existing behavior).
	// ------------------------------------------------------------------

	/** Without intervention, artifact stays unset and dumps remain as before. */
	public void testPlainDrawerIsNotArtifact() {
		final List<String> log = new ArrayList<>();
		final Drawer drawer = new Drawer(0);
		assertFalse(drawer.isArtifact());
		drawer.visitDrawable(new Marker("a", log), 1, 2);
		final String dumped = dump(drawer);
		assertEquals("drawer z=0\n  x=1.00 y=2.00 marker a\n", dumped);
		assertFalse("artifactの印は出ない", dumped.contains("artifact"));
	}

	// ------------------------------------------------------------------
	// Shared views.
	// ------------------------------------------------------------------

	/** A view feeds the same display list without creating a new z level. */
	public void testArtifactViewSharesTheDisplayList() {
		final List<String> log = new ArrayList<>();
		final Drawer drawer = new Drawer(0);
		drawer.visitDrawable(new Marker("before", log), 0, 0);
		final Drawer view = drawer.artifactView();
		assertNotSame(drawer, view);
		assertTrue(view.isArtifact());
		assertFalse("オーナーはartifactにならない", drawer.isArtifact());
		view.visitDrawable(new Marker("artifact", log), 0, 0);
		drawer.visitDrawable(new Marker("after", log), 0, 0);

		// The view itself holds nothing (everything is in the owner's display list).
		assertEquals("drawer z=0 artifact\n", dump(view));
		assertEquals("drawer z=0\n" //
				+ "  x=0.00 y=0.00 marker before\n" //
				+ "  x=0.00 y=0.00 artifact marker artifact\n" //
				+ "  x=0.00 y=0.00 marker after\n", dump(drawer));
	}

	/** Insertion order (= drawing order) remains completely unchanged when a view is involved. */
	public void testArtifactViewPreservesDrawOrder() {
		final List<String> viaView = new ArrayList<>();
		final Drawer withView = new Drawer(0);
		withView.visitDrawable(new Marker("1", viaView), 0, 0);
		withView.artifactView().visitDrawable(new Marker("2", viaView), 0, 0);
		withView.visitDrawable(new Marker("3", viaView), 0, 0);
		withView.draw(new NoOpGC(null));

		final List<String> plain = new ArrayList<>();
		final Drawer withoutView = new Drawer(0);
		withoutView.visitDrawable(new Marker("1", plain), 0, 0);
		withoutView.visitDrawable(new Marker("2", plain), 0, 0);
		withoutView.visitDrawable(new Marker("3", plain), 0, 0);
		withoutView.draw(new NoOpGC(null));

		assertEquals(plain, viaView);
		assertEquals(List.of("1", "2", "3"), viaView);
	}

	/** Repeated requests return the same view, and a view's view is itself. */
	public void testArtifactViewIsStable() {
		final Drawer drawer = new Drawer(0);
		final Drawer view = drawer.artifactView();
		assertSame(view, drawer.artifactView());
		assertSame(view, view.artifactView());
	}

	// ------------------------------------------------------------------
	// Propagation to children.
	// ------------------------------------------------------------------

	/** Child Drawers added through a view become artifacts (propagates to descendants). */
	public void testArtifactPropagatesToChildDrawers() {
		final List<String> log = new ArrayList<>();
		final Drawer root = new Drawer(0);
		final Drawer child = new Drawer(1);
		final Drawer grandChild = new Drawer(2);
		child.visitDrawer(grandChild);
		root.artifactView().visitDrawer(child);
		assertTrue(child.isArtifact());
		assertTrue(grandChild.isArtifact());
		assertFalse(root.isArtifact());

		// Both existing content and subsequently added content become artifacts.
		grandChild.visitDrawable(new Marker("late", log), 0, 0);
		assertTrue(dump(root).contains("artifact marker late"));
	}

	/** Drawables already present before addition also become artifacts retroactively. */
	public void testAlreadyAddedDrawablesAreMarkedOnPropagation() {
		final List<String> log = new ArrayList<>();
		final Drawer root = new Drawer(0);
		final Drawer child = new Drawer(1);
		child.visitDrawable(new Marker("early", log), 0, 0);
		root.artifactView().visitDrawer(child);
		assertTrue(dump(root).contains("artifact marker early"));
	}

	/** Normal addition does not propagate to children. */
	public void testPlainVisitDrawerDoesNotPropagate() {
		final Drawer root = new Drawer(0);
		final Drawer child = new Drawer(1);
		root.visitDrawer(child);
		assertFalse(child.isArtifact());
	}

	/** Marking a view as an artifact does not affect the owner's regular content. */
	public void testViewDoesNotContaminateTheOwner() {
		final List<String> log = new ArrayList<>();
		final Drawer owner = new Drawer(0);
		owner.visitDrawable(new Marker("normal", log), 0, 0);
		final Drawer view = owner.artifactView();
		view.visitDrawable(new Marker("decor", log), 0, 0);
		assertFalse(owner.isArtifact());
		final String dumped = dump(owner);
		assertTrue(dumped.contains("y=0.00 marker normal"));
		assertTrue(dumped.contains("artifact marker decor"));
	}

	// ------------------------------------------------------------------
	// z order.
	// ------------------------------------------------------------------

	/** Child Drawer sorting by z order is unchanged by the presence of a shared view. */
	public void testChildOrderingIsUnchangedByTheView() {
		final List<String> viaView = new ArrayList<>();
		final Drawer root = new Drawer(0);
		final Drawer high = new Drawer(5);
		high.visitDrawable(new Marker("high", viaView), 0, 0);
		final Drawer low = new Drawer(-5);
		low.visitDrawable(new Marker("low", viaView), 0, 0);
		root.visitDrawable(new Marker("own", viaView), 0, 0);
		root.artifactView().visitDrawer(high);
		root.visitDrawer(low);
		root.draw(new NoOpGC(null));

		// Negative z-index children (-5) precede own Drawables; positive (5) follow (CSS 2.1 Appendix E ③→④…⑦, 2026-09-05).
		assertEquals(List.of("low", "own", "high"), viaView);
	}
}
