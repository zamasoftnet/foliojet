package net.zamasoft.foliojet.css.style.running;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.internal.CSSJImageValue;

/**
 * 計算済みのCSS値を、数値・文字列・不変の列と辞書だけへ切り離したスナップショットです。
 * プロパティの型名と各成分を保存し、contentの式も評価せず保存します。
 * 絶対長はUAを必要としないpt値へ正規化します。
 */
public final class StyleSnapshot {
	/** 元の値型と、その値を構成する切り離し済み成分です。 */
	public record FrozenValue(String type, Map<String, Object> fields) {
		public FrozenValue {
			fields = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(fields));
		}
	}

	private static final ClassValue<List<Field>> FIELDS = new ClassValue<List<Field>>() {
		protected List<Field> computeValue(final Class<?> type) {
			final List<Field> fields = new ArrayList<Field>();
			for (Class<?> c = type; c != Object.class && c != null; c = c.getSuperclass()) {
				for (final Field field : c.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers())) {
						field.setAccessible(true);
						fields.add(field);
					}
				}
			}
			return List.copyOf(fields);
		}
	};

	private final Map<String, FrozenValue> properties;
	private final Map<String, String> attributes;
	private final Set<String> declared;
	private final String elementName;
	private final List<String> imageUris;
	private final CSSJImageValue.SvgSource svgSource;
	private final String language;
	private final String baseURI;
	private final long textBytes;

	private StyleSnapshot(final Map<String, FrozenValue> properties, final Map<String, String> attributes,
			final Set<String> declared, final String elementName, final List<String> imageUris,
			final CSSJImageValue.SvgSource svgSource, final String language, final String baseURI, final long textBytes) {
		this.properties = Collections.unmodifiableMap(properties);
		this.attributes = Collections.unmodifiableMap(attributes);
		this.declared = Set.copyOf(declared);
		this.elementName = elementName;
		this.imageUris = List.copyOf(imageUris);
		this.svgSource = svgSource;
		this.language = language;
		this.baseURI = baseURI;
		this.textBytes = textBytes;
	}

	/** カスケード済みの値と属性をコピーします。liveオブジェクトは一切保存しません。 */
	public static StyleSnapshot capture(final CSSStyle style) {
		return new Copier().capture(style, RunningCapture.MAX_TEXT_BYTES);
	}

	/** コピー前に残予算を確認する。型名・成分名など共有するスキーマは含めません。 */
	private static final class Budget {
		final long limit;
		long used;

		Budget(final long limit) {
			this.limit = limit;
		}

		void add(final long bytes) {
			if (bytes > this.limit - this.used) {
				throw new IllegalArgumentException("text bytes");
			}
			this.used += bytes;
		}

		String text(final String text) {
			if (text != null) {
				this.add((long) text.length() * 2);
			}
			return text;
		}
	}

	/** 捕捉中だけ、特性ごとの直近値を共有します。キャッシュは特性数を超えません。 */
	static final class Copier {
		private record Entry(Value value, FrozenValue frozen, List<String> images, long bytes) {
		}

		private final Map<PrimitivePropertyInfo, Entry> previous = new java.util.HashMap<PrimitivePropertyInfo, Entry>();

		StyleSnapshot capture(final CSSStyle style, final long remaining) {
			final Budget budget = new Budget(remaining);
			final Map<String, FrozenValue> properties = new LinkedHashMap<String, FrozenValue>(ElementPropertySet.getCodeSize());
			final Set<String> declared = new java.util.HashSet<String>();
			final List<String> images = new ArrayList<String>();
			for (final PrimitivePropertyInfo info : ElementPropertySet.getPrimitiveProperties()) {
				// 画像本体は共有しない。インラインSVGは下の切り離したXMLを保持する。
				if (info == CSSJInternalImage.INFO && style.get(info) instanceof CSSJImageValue) {
					continue;
				}
				if (style.isDeclared(info)) {
					declared.add(info.getName());
				}
				final Value value = style.get(info);
				Entry entry = this.previous.get(info);
				if (entry == null || entry.value() != value || !cacheable(value)) {
					final List<String> uris = new ArrayList<String>();
					final long before = budget.used;
					entry = new Entry(value, (FrozenValue) freeze(value, uris, budget), List.copyOf(uris),
							budget.used - before);
					this.previous.put(info, entry);
				} else {
					budget.add(entry.bytes());
				}
				properties.put(info.getName(), entry.frozen());
				if (info.getName().contains("image") || "content".equals(info.getName())) {
					images.addAll(entry.images());
				}
			}
			final Map<String, String> attributes = new LinkedHashMap<String, String>();
			final var ce = style.getCSSElement();
			final var origin = ce.isPseudoElement() && style.getParentStyle() != null
					? style.getParentStyle().getCSSElement() : ce;
			if (origin.atts != null) {
				for (int i = 0; i < origin.atts.getLength(); ++i) {
					attributes.put(budget.text(origin.atts.getQName(i)), budget.text(origin.atts.getValue(i)));
				}
				final String src = ce.isPseudoElement() ? null : origin.atts.getValue("src");
				if (src != null) {
					String resolved;
					try {
						resolved = style.getUserAgent().getDocumentContext().getBaseURI().resolve(src).toString();
					} catch (final IllegalArgumentException e) {
						resolved = src;
					}
					images.add(budget.text(resolved));
				}
			}
			String language = null;
			for (CSSStyle ancestor = style; ancestor != null; ancestor = ancestor.getParentStyle()) {
				if (ancestor.getCSSElement().lang != null) {
					language = budget.text(ancestor.getCSSElement().lang.toLanguageTag());
					break;
				}
			}
			CSSJImageValue.SvgSource svg = null;
			if (style.get(CSSJInternalImage.INFO) instanceof CSSJImageValue image && image.getSvgSource() != null) {
				svg = image.getSvgSource();
				if (svg.document() == null) {
					throw new IllegalArgumentException("SVG text bytes or serialization");
				}
				budget.text(svg.document());
				budget.text(svg.baseURI());
			}
			final URI baseURI = style.getUserAgent().getDocumentContext().getBaseURI();
			return new StyleSnapshot(properties, attributes, declared, budget.text(ce.lName), images,
					svg, language, baseURI == null ? null : budget.text(baseURI.toString()), budget.used);
		}
	}

	private static boolean cacheable(final Value value) {
		if (value == null || value instanceof Enum<?> || value instanceof AbsoluteLengthValue) {
			return true;
		}
		for (final Field field : FIELDS.get(value.getClass())) {
			final Class<?> type = field.getType();
			if (!Modifier.isFinal(field.getModifiers())
					|| !(type.isPrimitive() || type == String.class || type.isEnum())) {
				return false;
			}
		}
		return true;
	}

	private static Object freeze(final Object value, final List<String> images, final Budget budget) {
		if (value instanceof String text) {
			return budget.text(text);
		}
		if (value == null || value instanceof Number
				|| value instanceof Boolean || value instanceof Character) {
			budget.add(8);
			return value;
		}
		if (value instanceof AbsoluteLengthValue length) {
			budget.add(8);
			return new FrozenValue(AbsoluteLengthValue.class.getName(), Map.of("pt", length.getLength()));
		}
		if (value instanceof net.zamasoft.foliojet.css.value.FlexBasisValue basis
				&& (basis.isAuto() || basis.isContent())) {
			return new FrozenValue(basis.getClass().getName(), Map.of("keyword", basis.isAuto() ? "auto" : "content"));
		}
		if (value instanceof Enum<?> constant) {
			return new FrozenValue(constant.getDeclaringClass().getName(), Map.of("name", constant.name()));
		}
		if (value instanceof URI uri) {
			final String text = budget.text(uri.toString());
			images.add(text);
			return text;
		}
		if (value instanceof java.awt.geom.AffineTransform transform) {
			final double[] matrix = new double[6];
			transform.getMatrix(matrix);
			return freeze(matrix, images, budget);
		}
		if (value.getClass().isArray()) {
			final List<Object> items = new ArrayList<Object>();
			for (int i = 0; i < Array.getLength(value); ++i) {
				budget.add(8);
				items.add(freeze(Array.get(value, i), images, budget));
			}
			return Collections.unmodifiableList(items);
		}
		if (value instanceof Iterable<?> values) {
			final List<Object> items = new ArrayList<Object>();
			for (final Object item : values) {
				budget.add(8);
				items.add(freeze(item, images, budget));
			}
			return Collections.unmodifiableList(items);
		}
		if (value instanceof Map<?, ?> values) {
			final Map<String, Object> items = new LinkedHashMap<String, Object>();
			for (final var entry : values.entrySet()) {
				items.put(budget.text(String.valueOf(entry.getKey())), freeze(entry.getValue(), images, budget));
			}
			return Collections.unmodifiableMap(items);
		}
		// 値モデル以外が混入したら参照を共有せず、捕捉を失敗させる。
		final String type = value.getClass().getName();
		if (!(value instanceof Value) && !type.startsWith("net.zamasoft.foliojet.css.value.")
				&& !type.startsWith("net.zamasoft.pdfg2d.gc.")) {
			throw new IllegalArgumentException("running snapshot: unsupported value " + type);
		}
		final Map<String, Object> fields = new LinkedHashMap<String, Object>();
		try {
			for (final Field field : FIELDS.get(value.getClass())) {
				fields.put(field.getName(), freeze(field.get(value), images, budget));
			}
		} catch (final IllegalAccessException e) {
			throw new IllegalArgumentException("running snapshot: " + type, e);
		}
		return new FrozenValue(type, fields);
	}

	public Map<String, FrozenValue> properties() {
		return this.properties;
	}

	public Map<String, String> attributes() {
		return this.attributes;
	}

	/** 論理/物理プロパティ等の優先判定に使う、原位置での明示宣言です。 */
	public Set<String> declared() {
		return this.declared;
	}

	/** 全プロパティ・属性・画像ソースのコピー時に消費した予算です。 */
	public long textBytes() {
		return this.textBytes;
	}

	public String language() {
		return this.language;
	}

	/** 捕捉時の文書URIです。EPUBの章が進んでも相対参照の基底を維持します。 */
	public String baseURI() {
		return this.baseURI;
	}

	/** R2はこのXMLと基底URIから新品のSVG画像を読み込みます。DOM/GVTは保持しません。 */
	public CSSJImageValue.SvgSource svgSource() {
		return this.svgSource;
	}

	public String elementName() {
		return this.elementName;
	}

	public List<String> imageUris() {
		return this.imageUris;
	}
}
