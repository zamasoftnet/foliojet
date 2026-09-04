package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.rescue.RescuePolicy;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** avoid連鎖末尾のoversized救済と、単体で収まる場合の受容制限を固定します。 */
public class AvoidChainOversizedTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final Pattern FRAME = Pattern.compile(
			"(?m)^  x=(-?[0-9.]+) y=(-?[0-9.]+) (artifact )?"
					+ "AbsoluteRectFrame\\[w=([0-9.]+) h=([0-9.]+)\\] "
					+ "clip=\\[(-?[0-9.]+) (-?[0-9.]+) ([0-9.]+) ([0-9.]+)\\]$");

	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public AvoidChainOversizedTest(final String name) {
		super(name);
	}

	public void testVerticalAvoidChainKeepsHeadingsWithFirstSlice() throws Exception {
		final List<String> pages = render("avoid-chain-oversized.html");
		assertAvoidChainAndSlices(pages, "縦組み大見出し", "縦組み小見出し", "縦組み後続本文", true);
	}

	public void testHorizontalAvoidChainKeepsHeadingsWithFirstSlice() throws Exception {
		final List<String> pages = render("avoid-chain-oversized-horizontal.html");
		assertAvoidChainAndSlices(pages, "横組み大見出し", "横組み小見出し", "横組み後続本文", false);
	}

	public void testVerticalAvoidChainKeepsFigureWholeWhenItFitsAlone() throws Exception {
		final List<String> pages = render("avoid-chain-fits-alone.html");
		assertFitsAloneLimitation(pages, "単体収容縦組み大見出し", "単体収容縦組み小見出し",
				"単体収容縦組み後続本文");
	}

	public void testHorizontalAvoidChainKeepsFigureWholeWhenItFitsAlone() throws Exception {
		final List<String> pages = render("avoid-chain-fits-alone-horizontal.html");
		assertFitsAloneLimitation(pages, "単体収容横組み大見出し", "単体収容横組み小見出し",
				"単体収容横組み後続本文");
	}

	public void testFittingFigureStillMovesWithBothHeadings() throws Exception {
		final List<String> pages = render("avoid-chain-fitting.html");
		final int h2Page = onlyPageContaining(pages, "対照大見出し");
		final int h3Page = onlyPageContaining(pages, "対照小見出し");
		final List<Integer> figurePages = framePages(pages);
		assertEquals("見出し2つは同じページ", h2Page, h3Page);
		assertEquals("収まる図版は分割されない", 1, figurePages.size());
		assertEquals("見出しと図版は一緒に次ページへ移動する", h2Page, figurePages.get(0).intValue());
		assertEquals("前置きが1ページ目、連鎖は2ページ目", 1, h2Page);
		assertFalse("収まる対照図版に救済clipを付けない", pages.get(h2Page).contains("AbsoluteRectFrame[")
				&& pages.get(h2Page).contains(" clip=["));
	}

	private static void assertFitsAloneLimitation(final List<String> pages, final String h2, final String h3,
			final String after) {
		// 受容する制限(2026-09-04): 単体で収まる図版は救済分割せず、境界avoidを緩和して
		// 見出しを前ページ、図版だけを次ページに置く。
		final int h2Page = onlyPageContaining(pages, h2);
		final int h3Page = onlyPageContaining(pages, h3);
		final List<Integer> figurePages = framePages(pages);
		assertEquals("見出し2つは同じページ", h2Page, h3Page);
		assertEquals("単体で収まる図版は分割されない", 1, figurePages.size());
		final int figurePage = figurePages.get(0).intValue();
		assertEquals("見出しは図版の直前のページ", h2Page + 1, figurePage);
		assertFalse("図版のページに見出しを残さない",
				pages.get(figurePage).contains(h2) || pages.get(figurePage).contains(h3));
		// 後続本文は図版と同じページに続くか(図版の後ろに余白があれば)、次のページ
		final int afterPage = onlyPageContaining(pages, after);
		assertTrue("後続本文は図版のページ以降: " + afterPage, afterPage == figurePage || afterPage == figurePage + 1);
		assertFalse("単体で収まる図版に救済clipを付けない", FRAME.matcher(pages.get(figurePage)).find());
	}

	private static void assertAvoidChainAndSlices(final List<String> pages, final String h2, final String h3,
			final String after, final boolean pageAxisIsX) {
		final int h2Page = onlyPageContaining(pages, h2);
		final int h3Page = onlyPageContaining(pages, h3);
		final List<Integer> figurePages = framePages(pages);
		assertTrue("図版は複数断片になる", figurePages.size() >= 2);
		assertEquals("見出し2つは同じページ", h2Page, h3Page);
		assertEquals("最初の図版断片は見出しと同じページ", h2Page, figurePages.get(0).intValue());
		assertTrue("見出しだけのページを作らない", FRAME.matcher(pages.get(h2Page)).find());
		for (int i = 1; i < figurePages.size(); ++i) {
			assertEquals("図版断片の間に別ページを挟まない", figurePages.get(i - 1).intValue() + 1,
					figurePages.get(i).intValue());
		}
		final int lastSlicePage = figurePages.get(figurePages.size() - 1).intValue();
		assertEquals("後続本文は最終断片の後に続く", lastSlicePage, onlyPageContaining(pages, after));
		assertSlicesCoverFrame(pages, figurePages, pageAxisIsX);
	}

	private static void assertSlicesCoverFrame(final List<String> pages, final List<Integer> figurePages,
			final boolean pageAxisIsX) {
		double covered = 0;
		double frameExtent = -1;
		for (int i = 0; i < figurePages.size(); ++i) {
			final Matcher matcher = FRAME.matcher(pages.get(figurePages.get(i).intValue()));
			assertTrue("図版断片のclip付きフレームがない", matcher.find());
			assertEquals("先頭断片だけが実内容", i > 0, matcher.group(3) != null);
			final double frameStart = number(matcher, pageAxisIsX ? 1 : 2);
			final double extent = number(matcher, pageAxisIsX ? 4 : 5);
			final double clipStart = number(matcher, pageAxisIsX ? 6 : 7);
			final double clipExtent = number(matcher, pageAxisIsX ? 8 : 9);
			if (frameExtent < 0) {
				frameExtent = extent;
			} else {
				assertEquals("全断片は同じ元図版を参照する", frameExtent, extent, 0.01);
			}
			covered += Math.max(0,
					Math.min(frameStart + extent, clipStart + clipExtent) - Math.max(frameStart, clipStart));
			assertFalse("1ページに図版断片は1つだけ", matcher.find());
		}
		assertEquals("断片のclipは元図版を過不足なく覆う", frameExtent, covered, 0.05);
	}

	private static double number(final Matcher matcher, final int group) {
		return Double.parseDouble(matcher.group(group));
	}

	private static int onlyPageContaining(final List<String> pages, final String needle) {
		final List<Integer> found = pagesContaining(pages, needle);
		assertEquals(needle + "は1ページだけに描画される", 1, found.size());
		return found.get(0).intValue();
	}

	private static List<Integer> framePages(final List<String> pages) {
		final List<Integer> found = new ArrayList<>();
		for (int i = 0; i < pages.size(); ++i) {
			if (pages.get(i).contains("AbsoluteRectFrame[")) {
				found.add(Integer.valueOf(i));
			}
		}
		return found;
	}

	private static List<Integer> pagesContaining(final List<String> pages, final String needle) {
		final List<Integer> found = new ArrayList<>();
		for (int i = 0; i < pages.size(); ++i) {
			if (pages.get(i).contains(needle)) {
				found.add(Integer.valueOf(i));
			}
		}
		return found;
	}

	private static List<String> render(final String fixture) throws Exception {
		final String name = fixture.substring(0, fixture.length() - ".html".length());
		final File outDir = new File("local/unittest/avoid-chain-oversized/" + name);
		outDir.mkdirs();
		final File[] old = outDir.listFiles();
		if (old != null) {
			for (final File file : old) {
				file.delete();
			}
		}
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		final File pdf = new File(outDir, "out.pdf");
		try (RescuePolicy.Scope scope = RescuePolicy.ENABLED.scoped();
				OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, new File("files/unittest/0480-rescue-split/" + fixture),
						"text/html", null);
			} finally {
				session.close();
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}
		final File[] dumps = outDir.listFiles((dir, filename) -> filename.endsWith(".txt"));
		assertNotNull("表示リストが出力されていない", dumps);
		Arrays.sort(dumps);
		final List<String> pages = new ArrayList<>();
		for (final File dump : dumps) {
			pages.add(Files.readString(dump.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
