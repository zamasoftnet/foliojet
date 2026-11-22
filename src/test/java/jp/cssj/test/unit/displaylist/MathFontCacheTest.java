package jp.cssj.test.unit.displaylist;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import net.sourceforge.jeuclid.font.DefaultFontFactory;

/**
 * The font cache of the MathML renderer, which every conversion of the process shares.
 *
 * <p>
 * A formula can name any font family. Each name that is not installed was registered as a font of its own and kept for
 * the life of the process; and the array of a name seen for the first time was put in the cache before its fallback
 * element was set, so that another thread asking for the same name could find it empty.
 * </p>
 */
public class MathFontCacheTest extends TestCase {
	private static final float SIZE = 12f;

	private static DefaultFontFactory factory() {
		final DefaultFontFactory factory = new DefaultFontFactory();
		// The first lookup loads the installed fonts
		factory.getFont(Font.SERIF, Font.PLAIN, SIZE);
		return factory;
	}

	/** The names kept for fonts that are neither installed nor registered. */
	private static int requestedNames(final DefaultFontFactory factory) throws Exception {
		final Field field = DefaultFontFactory.class.getDeclaredField("requestedFonts");
		field.setAccessible(true);
		final Map<?, ?> requested = (Map<?, ?>) field.get(factory);
		synchronized (requested) {
			return requested.size();
		}
	}

	public void testUnknownFontNamesDoNotAccumulate() throws Exception {
		final DefaultFontFactory factory = factory();
		final int before = factory.listFontNames().size();
		for (int i = 0; i < 5000; ++i) {
			assertNotNull(factory.getFont("copper-test-missing-" + i, Font.PLAIN, SIZE));
		}
		assertEquals("names that are not installed were registered as fonts", before,
				factory.listFontNames().size());
		final int kept = requestedNames(factory);
		assertTrue("names that are not installed are kept without a bound: " + kept, kept <= 1000);
	}

	public void testAnUnknownNameStillYieldsAFontOfTheStyleAndSize() {
		final DefaultFontFactory factory = factory();
		for (int round = 0; round < 2; ++round) {
			final Font bold = factory.getFont("copper-test-missing", Font.BOLD, 14f);
			assertEquals(Font.BOLD, bold.getStyle());
			assertEquals(14f, bold.getSize2D(), 0.001f);
			final Font plain = factory.getFont("copper-test-missing", Font.PLAIN, 10f);
			assertEquals(Font.PLAIN, plain.getStyle());
			assertEquals(10f, plain.getSize2D(), 0.001f);
		}
	}

	public void testAnInstalledFamilyKeepsItsStyles() {
		final DefaultFontFactory factory = factory();
		final Font italic = factory.getFont(Font.SERIF, Font.ITALIC, 20f);
		assertEquals(Font.ITALIC, italic.getStyle());
		assertEquals(20f, italic.getSize2D(), 0.001f);
		final String family = italic.getFamily(java.util.Locale.ROOT);
		assertTrue(family, Font.SERIF.equalsIgnoreCase(family));
	}

	/** Threads that ask for a name at the same time all get a font, whichever of them sees the name first. */
	public void testConcurrentFirstRequestsAllGetAFont() throws Exception {
		final DefaultFontFactory factory = factory();
		final int threads = 8;
		final ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			for (int round = 0; round < 200; ++round) {
				final String name = "copper-test-race-" + round;
				final CyclicBarrier start = new CyclicBarrier(threads);
				final List<Future<Font>> fonts = new ArrayList<Future<Font>>();
				for (int t = 0; t < threads; ++t) {
					final int style = t % 4;
					fonts.add(pool.submit(() -> {
						start.await(30, TimeUnit.SECONDS);
						return factory.getFont(name, style, SIZE);
					}));
				}
				for (int t = 0; t < threads; ++t) {
					final Font font = fonts.get(t).get(60, TimeUnit.SECONDS);
					assertNotNull(font);
					assertEquals(t % 4, font.getStyle());
				}
			}
		} finally {
			pool.shutdownNow();
		}
	}
}
