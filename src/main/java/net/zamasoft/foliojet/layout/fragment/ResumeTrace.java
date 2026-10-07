package net.zamasoft.foliojet.layout.fragment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dumps page-break/column-break resume operations as text (for regression verification).
 *
 * <p>
 * Each slice of the Continuation migration (ARCHITECTURE §5.7) requires mechanical verification that the
 * resume operation sequence stays unchanged (or changes intentionally).
 * This trace records the branches and order of restyle traversal (box reenactment) and source replay,
 * and golden comparison (ResumeTraceGoldenTest) enforces semantic preservation.
 * </p>
 *
 * <p>
 * Enabled by setting the output directory in system property {@value #DIR_PROPERTY} .
 * When disabled, the cost is one property lookup.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ResumeTrace {
	private static final Logger LOG = Logger.getLogger(ResumeTrace.class.getName());

	public static final String DIR_PROPERTY = "net.zamasoft.foliojet.debug.resume-trace.dir";

	/**
	 * The buffer stack for resume traces. If replayed content overflows the new page, breaks nest within
	 * resume.
	 * With a single buffer, the inner begin would overwrite the outer recording (noted in external review).
	 * A nested break also leaves a "nested resume" line in the outer trace and writes its own separate file
	 * on completion (numbered in completion order).
	 */
	private static final ThreadLocal<java.util.ArrayDeque<StringBuilder>> buffers = ThreadLocal
			.withInitial(java.util.ArrayDeque::new);

	private static final java.util.concurrent.atomic.AtomicInteger breakCount = new java.util.concurrent.atomic.AtomicInteger();

	private ResumeTrace() {
		// utility
	}

	private static boolean enabled() {
		return System.getProperty(DIR_PROPERTY) != null;
	}

	/**
	 * Records the start of resume after a break (page or column).
	 *
	 * @param kind PAGE / COLUMN
	 */
	public static void begin(final String kind) {
		if (!enabled()) {
			return;
		}
		final StringBuilder outer = buffers.get().peek();
		if (outer != null) {
			outer.append("  nested resume ").append(kind).append('\n');
		}
		final StringBuilder buffer = new StringBuilder();
		buffer.append("resume ").append(kind).append('\n');
		buffers.get().push(buffer);
	}

	/**
	 * Records an operation during resume. Callers need not check enabled (ignored when disabled).
	 *
	 * @param depth depth on the ancestor chain (-1 if unknown)
	 * @param op operation name (replay-subtree / restyle-box / text-tail, etc.)
	 * @param what summary of the target (box kind, serial, etc.)
	 */
	public static void op(final int depth, final String op, final String what) {
		final StringBuilder sb = buffers.get().peek();
		if (sb == null) {
			return;
		}
		sb.append("  ");
		for (int i = 0; i < Math.max(0, depth); ++i) {
			sb.append(' ');
		}
		sb.append(op).append(' ').append(what).append('\n');
	}

	/**
	 * Records the end of resume and writes to a file if enabled.
	 */
	public static void end() {
		final StringBuilder sb = buffers.get().poll();
		if (sb == null) {
			return;
		}
		final String dir = System.getProperty(DIR_PROPERTY);
		if (dir == null) {
			return;
		}
		try {
			final File d = new File(dir);
			d.mkdirs();
			Files.writeString(
					new File(d, String.format(Locale.ROOT, "break-%04d.txt", breakCount.incrementAndGet())).toPath(),
					sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.log(Level.WARNING, "再開トレースをダンプできませんでした", e);
		}
	}

	/**
	 * For tests: resets the sequence number.
	 */
	public static void reset() {
		breakCount.set(0);
		buffers.get().clear();
	}
}
