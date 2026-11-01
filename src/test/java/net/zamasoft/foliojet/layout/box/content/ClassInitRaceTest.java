package net.zamasoft.foliojet.layout.box.content;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.TestCase;

/**
 * The first conversions of a server start at once, so the classes they meet first initialize at once (2026-11-01). A
 * parent whose initializer creates its own subtype deadlocks when another thread has started from that subtype: each
 * holds one initialization lock and waits for the other's, and no interrupt or timeout reaches them. A sweep's JVM hung
 * this way on BreakMode, with every later conversion queued behind the same two classes.
 *
 * <p>
 * The class names are strings, and each round defines the classes again in a loader of its own, so each round is a
 * first initialization.
 * </p>
 */
public class ClassInitRaceTest extends TestCase {
	private static final String BREAK_MODE = "net.zamasoft.foliojet.layout.box.content.BreakMode";

	private static final String BREAK_TOKEN = "net.zamasoft.foliojet.layout.box.content.BreakToken";

	/** The unfixed classes deadlocked by round 175 (BreakMode) and round 1 (BreakToken) in each of five runs. */
	private static final int ROUNDS = 1_000;

	public void testBreakModeFromItsSubclasses() throws Exception {
		assertNoDeadlock(BREAK_MODE, BREAK_MODE + "$AutoBreakMode", BREAK_MODE + "$ForceBreakMode");
	}

	public void testBreakTokenFromItsSubtype() throws Exception {
		assertNoDeadlock(BREAK_TOKEN, BREAK_TOKEN + "$MidFlow", BREAK_TOKEN);
	}

	private static void assertNoDeadlock(final String prefix, final String... entries) throws Exception {
		for (int round = 0; round < ROUNDS; ++round) {
			final ClassLoader loader = new FreshLoader(prefix);
			final List<Class<?>> classes = new ArrayList<Class<?>>();
			for (final String entry : entries) {
				final Class<?> c = Class.forName(entry, false, loader);
				// Links without initializing, so that the threads below meet at initialization itself.
				c.getDeclaredMethods();
				classes.add(c);
			}
			final CyclicBarrier start = new CyclicBarrier(entries.length);
			final CountDownLatch done = new CountDownLatch(entries.length);
			final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
			for (final Class<?> c : classes) {
				final Thread thread = new Thread(() -> {
					try {
						start.await();
						Class.forName(c.getName(), true, loader);
					} catch (final Throwable e) {
						failure.compareAndSet(null, e);
					} finally {
						done.countDown();
					}
				}, "class-init-race");
				// A deadlocked thread can never be stopped; it must not keep the test JVM alive.
				thread.setDaemon(true);
				thread.start();
			}
			assertTrue("class initialization deadlocked in round " + round + ": " + classes,
					done.await(10, TimeUnit.SECONDS));
			assertNull("class initialization failed in round " + round + ": " + failure.get(), failure.get());
		}
	}

	/** Defines the classes under the prefix itself, so that they start uninitialized in each round. */
	private static final class FreshLoader extends ClassLoader {
		private static final Map<String, byte[]> BYTES = new ConcurrentHashMap<String, byte[]>();

		private final String prefix;

		FreshLoader(final String prefix) {
			super(ClassInitRaceTest.class.getClassLoader());
			this.prefix = prefix;
		}

		@Override
		protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
			if (!name.startsWith(this.prefix)) {
				return super.loadClass(name, resolve);
			}
			synchronized (this.getClassLoadingLock(name)) {
				Class<?> c = this.findLoadedClass(name);
				if (c == null) {
					final byte[] bytes = BYTES.computeIfAbsent(name, FreshLoader::read);
					c = this.defineClass(name, bytes, 0, bytes.length);
				}
				return c;
			}
		}

		private static byte[] read(final String name) {
			try (InputStream in = ClassInitRaceTest.class.getClassLoader()
					.getResourceAsStream(name.replace('.', '/') + ".class")) {
				return in.readAllBytes();
			} catch (final IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
