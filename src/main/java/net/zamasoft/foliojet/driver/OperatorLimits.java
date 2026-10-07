package net.zamasoft.foliojet.driver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * Operator-defined limits that users <b>cannot relax</b> (2026-10-03, shared-service resource limits increment 2;
 * {@code copperpdf4/docs/design/shared-service-limits-design.md} §3-2).
 *
 * <p>
 * Profile ({@code jp.cssj.driver.default}) values are merely defaults when clients supply none,
 * and clients or document processing instructions can remove them. The limits here take
 * <b>the smaller value</b> regardless of where a setting originates: it can be tightened but never relaxed.
 * </p>
 *
 * <p>
 * Stored in the {@code .properties} file named by {@value #FILE_KEY} (a system property). Only
 * the numeric limits in the table below are allowed; unknown names or unreadable values cause
 * {@link IOException} (the server does not start). Since each property represents unlimited differently,
 * the table also defines comparison rules:
 * </p>
 * <ul>
 * <li>Input, resources, pixel count, output size, page count: negative=unlimited; zero means a limit of zero</li>
 * <li>{@code processing.time-limit} and {@code processing.retained-text-limit}: zero or less=unlimited</li>
 * <li>{@code processing.concurrency}: zero or less=automatic (min(core count, 4))</li>
 * </ul>
 */
public final class OperatorLimits {
	/** The system property pointing to the limits file. */
	public static final String FILE_KEY = "jp.cssj.driver.limits";

	/** No limits. */
	public static final OperatorLimits NONE = new OperatorLimits(Collections.emptyMap());

	private enum Kind {
		/** Negative means unlimited. */
		NEGATIVE_UNLIMITED,
		/** Zero or less means unlimited. */
		NONPOSITIVE_UNLIMITED,
		/** Zero or less means automatic (min(core count, 4)). */
		CONCURRENCY
	}

	private record Spec(Kind kind, long engineDefault) {
	}

	private static final Map<String, Spec> SPECS;
	static {
		final Map<String, Spec> specs = new LinkedHashMap<>();
		specs.put(UAProps.INPUT_SIZE_LIMIT.getName(),
				new Spec(Kind.NEGATIVE_UNLIMITED, UAProps.INPUT_SIZE_LIMIT.defaultLong));
		specs.put(UAProps.INPUT_RESOURCE_SIZE_LIMIT.getName(),
				new Spec(Kind.NEGATIVE_UNLIMITED, UAProps.INPUT_RESOURCE_SIZE_LIMIT.defaultLong));
		specs.put(UAProps.INPUT_RESOURCE_COUNT_LIMIT.getName(),
				new Spec(Kind.NEGATIVE_UNLIMITED, UAProps.INPUT_RESOURCE_COUNT_LIMIT.defaultInt));
		specs.put(UAProps.INPUT_IMAGE_PIXEL_LIMIT.getName(),
				new Spec(Kind.NEGATIVE_UNLIMITED, UAProps.INPUT_IMAGE_PIXEL_LIMIT.defaultLong));
		specs.put(UAProps.OUTPUT_IMAGE_PIXEL_LIMIT.getName(),
				new Spec(Kind.NEGATIVE_UNLIMITED, UAProps.OUTPUT_IMAGE_PIXEL_LIMIT.defaultLong));
		specs.put(UAProps.OUTPUT_SIZE_LIMIT.getName(),
				new Spec(Kind.NEGATIVE_UNLIMITED, UAProps.OUTPUT_SIZE_LIMIT.defaultLong));
		specs.put(UAProps.OUTPUT_PAGE_LIMIT.getName(),
				new Spec(Kind.NEGATIVE_UNLIMITED, UAProps.OUTPUT_PAGE_LIMIT.defaultInt));
		specs.put(UAProps.PROCESSING_TIME_LIMIT.getName(),
				new Spec(Kind.NONPOSITIVE_UNLIMITED, UAProps.PROCESSING_TIME_LIMIT.defaultLong));
		specs.put(UAProps.PROCESSING_RETAINED_TEXT_LIMIT.getName(),
				new Spec(Kind.NONPOSITIVE_UNLIMITED, UAProps.PROCESSING_RETAINED_TEXT_LIMIT.defaultLong));
		specs.put(UAProps.PROCESSING_CONCURRENCY.getName(),
				new Spec(Kind.CONCURRENCY, UAProps.PROCESSING_CONCURRENCY.defaultInt));
		SPECS = Collections.unmodifiableMap(specs);
	}

	private final Map<String, Long> ceilings;

	private OperatorLimits(final Map<String, Long> ceilings) {
		this.ceilings = ceilings;
	}

	/** Whether the name is an allowed numeric limit in the limits file. */
	public static boolean isLimitProperty(final String name) {
		return SPECS.containsKey(name);
	}

	/** Whether no limits are present. */
	public boolean isEmpty() {
		return this.ceilings.isEmpty();
	}

	/** Property names with limits and their limit values. */
	public Map<String, Long> ceilings() {
		return this.ceilings;
	}

	private static File cachedFile;
	private static long cachedModified;
	private static OperatorLimits cached;

	/**
	 * Reads the limits file named by {@value #FILE_KEY}. Returns {@link #NONE} if none is specified.
	 * Does not reread the same file until its modification time changes.
	 *
	 * @throws IOException if unreadable, a name is unknown, or a value is nonnumeric
	 */
	public static synchronized OperatorLimits current() throws IOException {
		final String path = System.getProperty(FILE_KEY);
		if (path == null || path.isEmpty()) {
			return NONE;
		}
		final File file = new File(path);
		final long modified = file.lastModified();
		if (cached != null && file.equals(cachedFile) && modified == cachedModified) {
			return cached;
		}
		final Properties props = new Properties();
		try (InputStream in = new FileInputStream(file)) {
			props.load(in);
		}
		final OperatorLimits limits = parse(props, file.toString());
		cached = limits;
		cachedFile = file;
		cachedModified = modified;
		return limits;
	}

	/**
	 * Builds the limits table.
	 *
	 * @param props  names and limit values
	 * @param origin the source to report in errors
	 * @throws IOException if a name is unknown or a value is nonnumeric
	 */
	public static OperatorLimits parse(final Properties props, final String origin) throws IOException {
		final Map<String, Long> ceilings = new HashMap<>();
		for (final String name : props.stringPropertyNames()) {
			if (!SPECS.containsKey(name)) {
				throw new IOException(origin + ": " + name + " は上限ファイルに書けません(書けるのは " + SPECS.keySet() + ")");
			}
			final String value = props.getProperty(name).trim();
			try {
				ceilings.put(name, Long.valueOf(value));
			} catch (final NumberFormatException e) {
				throw new IOException(origin + ": " + name + " の値 " + value + " は数値ではありません", e);
			}
		}
		return new OperatorLimits(Collections.unmodifiableMap(ceilings));
	}

	/**
	 * Returns the effective value after applying the limit to the setting.
	 * Returns values unchanged for names without limits.
	 *
	 * @param name  the property name
	 * @param value the setting (null if unset; an unreadable value becomes the limit itself)
	 * @return the effective value
	 */
	public String clamp(final String name, final String value) {
		final Long ceiling = this.ceilings.get(name);
		if (ceiling == null) {
			return value;
		}
		final Spec spec = SPECS.get(name);
		long requested = spec.engineDefault;
		if (value != null) {
			try {
				requested = Long.parseLong(value.trim());
			} catch (final NumberFormatException e) {
				return String.valueOf(ceiling);
			}
		}
		return String.valueOf(min(spec.kind, requested, ceiling.longValue()));
	}

	/** Returns whether the setting is less restrictive than this server's limit (for warnings). */
	public boolean loosens(final String name, final String value) {
		final Long ceiling = this.ceilings.get(name);
		if (ceiling == null || value == null || value.isEmpty()) {
			return false;
		}
		return !String.valueOf(value.trim()).equals(this.clamp(name, value));
	}

	/** Returns a copy of the property table with limits applied (limited properties get effective values even if unset). */
	public Map<String, String> clampAll(final Map<String, String> props) {
		if (this.ceilings.isEmpty()) {
			return props;
		}
		final Map<String, String> clamped = new HashMap<>(props);
		for (final String name : this.ceilings.keySet()) {
			clamped.put(name, this.clamp(name, props.get(name)));
		}
		return clamped;
	}

	private static long min(final Kind kind, final long requested, final long ceiling) {
		switch (kind) {
		case NEGATIVE_UNLIMITED: {
			final long r = requested < 0 ? Long.MAX_VALUE : requested;
			final long c = ceiling < 0 ? Long.MAX_VALUE : ceiling;
			final long m = Math.min(r, c);
			return m == Long.MAX_VALUE ? -1L : m;
		}
		case NONPOSITIVE_UNLIMITED: {
			final long r = requested <= 0 ? Long.MAX_VALUE : requested;
			final long c = ceiling <= 0 ? Long.MAX_VALUE : ceiling;
			final long m = Math.min(r, c);
			return m == Long.MAX_VALUE ? 0L : m;
		}
		case CONCURRENCY: {
			final long auto = Math.min(Runtime.getRuntime().availableProcessors(), 4);
			final long r = requested <= 0 ? auto : requested;
			final long c = ceiling <= 0 ? auto : ceiling;
			return Math.min(r, c);
		}
		default:
			throw new IllegalStateException(kind.toString());
		}
	}
}
