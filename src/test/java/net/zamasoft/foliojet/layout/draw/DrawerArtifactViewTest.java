package net.zamasoft.foliojet.layout.draw;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.NoOpGC;

/**
 * {@link Drawer}のartifact属性と共有ビューの単体テストです
 * (2026-07-25新設、救済分割・増分2。<b>まだ本番経路からは
 * artifact属性が立ちません</b>)。
 *
 * <p>
 * ここで固定する最重要の性質は「共有ビューを使っても表示リストの順序が
 * 変わらない」ことです。現在の{@link Drawer#draw}は通常のDrawableを
 * 先に描いてから子Drawerをz順で安定ソートするため、artifact用に
 * ラッパーDrawerを<b>子として</b>追加すると既存の重なり順が変わって
 * しまいます(答申§3)。そのため共有ビュー方式にしています。
 * </p>
 */
public class DrawerArtifactViewTest extends TestCase {

	/** 描画順を記録するだけのDrawableです。 */
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
	// 既定は非artifact(既存挙動の不変)
	// ------------------------------------------------------------------

	/** 何もしなければartifactは立たず、ダンプも従来どおり。 */
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
	// 共有ビュー
	// ------------------------------------------------------------------

	/** ビューは新しいz階層を作らず、同じ表示リストへ流し込む。 */
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

		// ビュー自身は何も持たない(全部オーナー側の表示リストにある)
		assertEquals("drawer z=0 artifact\n", dump(view));
		assertEquals("drawer z=0\n" //
				+ "  x=0.00 y=0.00 marker before\n" //
				+ "  x=0.00 y=0.00 artifact marker artifact\n" //
				+ "  x=0.00 y=0.00 marker after\n", dump(drawer));
	}

	/** 追加順(=描画順)はビューを挟んでも一切変わらない。 */
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

	/** ビューを繰り返し取っても同じビューで、ビューのビューは自分自身。 */
	public void testArtifactViewIsStable() {
		final Drawer drawer = new Drawer(0);
		final Drawer view = drawer.artifactView();
		assertSame(view, drawer.artifactView());
		assertSame(view, view.artifactView());
	}

	// ------------------------------------------------------------------
	// 子への伝播
	// ------------------------------------------------------------------

	/** ビュー経由で追加した子Drawerはartifactになる(子孫まで伝播)。 */
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

		// 追加済みの内容も、後から追加する内容も、両方artifactになる
		grandChild.visitDrawable(new Marker("late", log), 0, 0);
		assertTrue(dump(root).contains("artifact marker late"));
	}

	/** 追加より前に入っていたDrawableも遡ってartifactになる。 */
	public void testAlreadyAddedDrawablesAreMarkedOnPropagation() {
		final List<String> log = new ArrayList<>();
		final Drawer root = new Drawer(0);
		final Drawer child = new Drawer(1);
		child.visitDrawable(new Marker("early", log), 0, 0);
		root.artifactView().visitDrawer(child);
		assertTrue(dump(root).contains("artifact marker early"));
	}

	/** 通常の追加では子へ伝播しない。 */
	public void testPlainVisitDrawerDoesNotPropagate() {
		final Drawer root = new Drawer(0);
		final Drawer child = new Drawer(1);
		root.visitDrawer(child);
		assertFalse(child.isArtifact());
	}

	/** ビューをartifactにしてもオーナーの通常内容は巻き込まない。 */
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
	// z順
	// ------------------------------------------------------------------

	/** 子Drawerのz順ソートは共有ビューの有無で変わらない。 */
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

		// 負のz-indexの子(-5)は自分のDrawableより先、正の子(5)は後(CSS 2.1 Appendix E ③→④…⑦、2026-09-05)
		assertEquals(List.of("low", "own", "high"), viaView);
	}
}
