package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.NoOpGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 負のz-indexを親の表示リストより前へ置く順序を固定します。 */
public class DrawerNegativeZOrderTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testNegativeBeforeFlowBackgroundAndPositiveAfter() throws Exception {
		final List<String> order = new ArrayList<>();
		final Drawer parent = new Drawer(0);
		final Drawer negative = new Drawer(-1);
		final Drawer positive = new Drawer(1);
		negative.visitDrawable(new Marker("absolute-negative", order), 0, 0);
		parent.visitDrawable(new Marker("flow-background", order), 0, 0);
		positive.visitDrawable(new Marker("absolute-positive", order), 0, 0);
		// 挿入順に依存しないことも同時に固定する。
		parent.visitDrawer(positive);
		parent.visitDrawer(negative);

		parent.draw(new NoOpGC(null));
		assertEquals(List.of("absolute-negative", "flow-background", "absolute-positive"), order);

		final StringBuilder dump = new StringBuilder();
		parent.dump(dump, "");
		assertTrue(dump.indexOf("absolute-negative") < dump.indexOf("flow-background"));
		assertTrue(dump.indexOf("flow-background") < dump.indexOf("absolute-positive"));
	}

	public void testAbsoluteZIndexAroundInFlowBackground() throws Exception {
		// CSS 2.1 Appendix E: 親の背景(①②)→負の子 stacking context(③)→親の内容(④〜⑥)→正の子(⑦)
		final String negative = render(-1);
		assertOrder("負のabsoluteは親背景の後・親内容の前", negative, true);

		final String positive = render(1);
		assertOrder("正のabsoluteは親内容の後", positive, false);
	}

	private static void assertOrder(final String message, final String dump, final boolean negative) {
		final int absolute = dump.indexOf("Text[\"ABS");
		final int background = dump.indexOf("AbsoluteRectFrame[w=100.00 h=30.00]");
		final int content = dump.indexOf("Text[\"FLOW");
		assertTrue("absoluteがありません:\n" + dump, absolute >= 0);
		assertTrue("親背景がありません:\n" + dump, background >= 0);
		assertTrue("通常フロー内容がありません:\n" + dump, content >= 0);
		assertTrue(message + "(背景が先):\n" + dump, background < absolute);
		assertEquals(message + ":\n" + dump, negative, absolute < content);
	}

	private static String render(final int zIndex) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:160pt 80pt;margin:10pt}html,body{margin:0}"
				+ ".parent{position:relative;z-index:0;width:100pt;height:30pt;background:#ccc}"
				+ ".absolute{position:absolute;left:0;top:0;z-index:" + zIndex + "}"
				+ "</style></head><body><div class='parent'><span class='absolute'>ABS</span>FLOW</div>"
				+ "</body></html>";
		final File dir = new File("local/unittest/drawer-z-" + zIndex);
		dir.mkdirs();
		final File page = new File(dir, "page-0001.txt");
		page.delete();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try (AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///drawer-z-" + zIndex + ".html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		assertTrue("表示リストがありません", page.isFile());
		return Files.readString(page.toPath(), StandardCharsets.UTF_8);
	}

	private record Marker(String name, List<String> order) implements Drawable {
		@Override
		public void draw(final GC gc, final double x, final double y) {
			this.order.add(this.name);
		}

		@Override
		public String describe() {
			return this.name;
		}
	}
}
