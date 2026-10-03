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
 * 運用者が決めた、利用者が<b>緩められない</b>上限です(2026-10-03、共有サービスの資源の上限 増分2。
 * {@code copperpdf4/docs/design/shared-service-limits-design.md} §3-2)。
 *
 * <p>
 * プロファイル({@code jp.cssj.driver.default})の値は「クライアントが送らなかったときの既定」にすぎず、
 * クライアントの指定や文書中の処理命令で外せる。ここに書いた上限は、どこで設定された値に対しても
 * <b>小さい方</b>を取る——厳しくすることはできるが、緩めることはできない。
 * </p>
 *
 * <p>
 * 置き場所は{@value #FILE_KEY}(システムプロパティ)で指す{@code .properties}ファイル。書けるのは
 * 下の表の数値の上限だけで、表に無い名前・読めない値は{@link IOException}(サーバーは起動しない)。
 * 「無制限」の表し方はプロパティごとに違うので、比較の規則も表で決める:
 * </p>
 * <ul>
 * <li>入力・資源・画素数・出力の大きさ・頁数: 負=無制限、0 は「0 まで」</li>
 * <li>{@code processing.time-limit}・{@code processing.retained-text-limit}: 0 以下=無制限</li>
 * <li>{@code processing.concurrency}: 0 以下=自動(min(コア数, 4))</li>
 * </ul>
 */
public final class OperatorLimits {
	/** 上限ファイルを指すシステムプロパティ。 */
	public static final String FILE_KEY = "jp.cssj.driver.limits";

	/** 上限なし。 */
	public static final OperatorLimits NONE = new OperatorLimits(Collections.emptyMap());

	private enum Kind {
		/** 負が無制限。 */
		NEGATIVE_UNLIMITED,
		/** 0以下が無制限。 */
		NONPOSITIVE_UNLIMITED,
		/** 0以下が自動(min(コア数, 4))。 */
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

	/** 上限ファイルに書ける(数値の上限の)名前か。 */
	public static boolean isLimitProperty(final String name) {
		return SPECS.containsKey(name);
	}

	/** 上限を1つも持たないか。 */
	public boolean isEmpty() {
		return this.ceilings.isEmpty();
	}

	/** 上限を持つプロパティ名と上限値。 */
	public Map<String, Long> ceilings() {
		return this.ceilings;
	}

	private static File cachedFile;
	private static long cachedModified;
	private static OperatorLimits cached;

	/**
	 * {@value #FILE_KEY}が指す上限ファイルを読みます。指していなければ{@link #NONE}。
	 * 同じファイルは更新時刻が変わるまで読み直しません。
	 *
	 * @throws IOException 読めない、表に無い名前、数値でない値
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
	 * 上限の表を作ります。
	 *
	 * @param props  名前と上限値
	 * @param origin エラーに出す出どころ
	 * @throws IOException 表に無い名前、数値でない値
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
	 * 設定値に上限を掛けた実効値を返します。上限の無い名前はそのまま返します。
	 *
	 * @param name  プロパティ名
	 * @param value 設定値(未設定ならnull。読めない値は上限そのものになる)
	 * @return 実効値
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

	/**
	 * 指定がこのサーバーの上限より緩いかを返します(警告を出すため)。
	 */
	public boolean loosens(final String name, final String value) {
		final Long ceiling = this.ceilings.get(name);
		if (ceiling == null || value == null || value.isEmpty()) {
			return false;
		}
		return !String.valueOf(value.trim()).equals(this.clamp(name, value));
	}

	/**
	 * プロパティの表に上限を掛けた写しを返します(上限の名前は未設定でも実効値が入る)。
	 */
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
