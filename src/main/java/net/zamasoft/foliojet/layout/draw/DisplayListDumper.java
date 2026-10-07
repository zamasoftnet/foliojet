package net.zamasoft.foliojet.layout.draw;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dumps a page's display list as text (for regression validation).
 * Enable by setting system property {@value #DIR_PROPERTY} to the output directory.
 */
public final class DisplayListDumper {
	private static final Logger LOG = Logger.getLogger(DisplayListDumper.class.getName());

	public static final String DIR_PROPERTY = "net.zamasoft.foliojet.debug.display-list.dir";

	// Independent D7 observation hook that also reaches DirectSession's conversion thread.
	private static volatile java.util.function.BiConsumer<Drawer, Integer> pageObserver;

	/** For serial validation. Independent of golden format and destination; always restore on close. */
	public static AutoCloseable observePages(final java.util.function.BiConsumer<Drawer, Integer> observer) {
		final var saved = pageObserver;
		pageObserver = observer;
		return () -> pageObserver = saved;
	}

	/**
	 * Per-thread output destination (takes precedence over the system property).
	 *
	 * <p>
	 * System properties are process-wide, so <b>concurrent conversions overwrite each other's dump destinations</b>.
	 * Parallelizing long sweeps (millions of documents) requires separate destinations per conversion thread
	 * (2026-07-26).
	 * </p>
	 */
	private static final ThreadLocal<String> DIR_OVERRIDE = new ThreadLocal<>();

	/**
	 * Whether to include drawable widths/heights in display lists. Used only for visible-area checks in large sweeps;
	 * does not change normal golden dumps.
	 */
	private static final ThreadLocal<Boolean> DETAILED_GEOMETRY = new ThreadLocal<>();

	private DisplayListDumper() {
		// utility
	}

	/**
	 * Returns the output destination set for this thread ({@code null} if unset). Used to propagate it when layout
	 * runs on a different thread.
	 */
	public static String currentDir() {
		return DIR_OVERRIDE.get();
	}

	/** Whether this thread is configured to output detailed drawing dimensions. */
	public static boolean currentDetailedGeometry() {
		return Boolean.TRUE.equals(DETAILED_GEOMETRY.get());
	}

	/**
	 * Sets this thread's dump destination. Closing the returned handle restores the previous setting.
	 *
	 * <pre>
	 * try (var scope = DisplayListDumper.scopedDir(dir)) {
	 * 	// Only conversions on this thread output to dir.
	 * }
	 * </pre>
	 *
	 * @param dir output destination (null removes this thread's setting)
	 * @return handle that restores the previous setting on close
	 */
	public static AutoCloseable scopedDir(final String dir) {
		final String saved = DIR_OVERRIDE.get();
		if (dir == null) {
			DIR_OVERRIDE.remove();
		} else {
			DIR_OVERRIDE.set(dir);
		}
		return () -> {
			if (saved == null) {
				DIR_OVERRIDE.remove();
			} else {
				DIR_OVERRIDE.set(saved);
			}
		};
	}

	/**
	 * Appends drawing dimensions only to this thread's display lists. Closing the returned handle restores the
	 * previous setting.
	 */
	public static AutoCloseable scopedDetailedGeometry(final boolean enabled) {
		final Boolean saved = DETAILED_GEOMETRY.get();
		if (enabled) {
			DETAILED_GEOMETRY.set(Boolean.TRUE);
		} else {
			DETAILED_GEOMETRY.remove();
		}
		return () -> {
			if (saved == null) {
				DETAILED_GEOMETRY.remove();
			} else {
				DETAILED_GEOMETRY.set(saved);
			}
		};
	}

	/**
	 * Writes the page's display list if dumping is enabled.
	 */
	public static void dumpPage(Drawer drawer, int pageNumber) {
		final var observer = pageObserver;
		if (observer != null) {
			observer.accept(drawer, pageNumber);
		}
		String dir = DIR_OVERRIDE.get();
		if (dir == null) {
			dir = System.getProperty(DIR_PROPERTY);
		}
		if (dir == null) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		drawer.dump(sb, "");
		try {
			File d = new File(dir);
			d.mkdirs();
			Files.writeString(new File(d, String.format(Locale.ROOT, "page-%04d.txt", pageNumber)).toPath(),
					sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.log(Level.WARNING, "表示リストをダンプできませんでした", e);
		}
	}
}
