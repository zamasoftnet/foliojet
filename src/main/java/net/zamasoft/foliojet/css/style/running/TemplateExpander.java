package net.zamasoft.foliojet.css.style.running;

import java.awt.geom.AffineTransform;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.xml.sax.helpers.AttributesImpl;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.css.impl.property.box.CSSPosition;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.content.Quotes;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.style.StyleBoxEmitter;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.AttrValue;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.ElementFunctionValue;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.LeaderValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.QuotesValue;
import net.zamasoft.foliojet.css.value.QuoteValue;
import net.zamasoft.foliojet.css.value.RunningPositionValue;
import net.zamasoft.foliojet.css.value.StringFunctionValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TargetCounterValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.image.Image;

/** 未評価の生成内容を表示頁の値で解決し、新しい組版イベント列へ展開します。 */
public final class TemplateExpander {
	private final UserAgent ua;
	private final PageValueSnapshot page;
	private final Set<String> dropped = new java.util.LinkedHashSet<String>();
	private int quoteLevel;

	private record Frame(CSSStyle style, CSSStyle parent, boolean hidden, boolean replaced) {
	}

	public TemplateExpander(final UserAgent ua, final PageValueSnapshot page) {
		this.ua = ua;
		this.page = page;
	}

	/** 警告して既定値へ落とした特性名です。診断用の不変コピーを返します。 */
	public Set<String> droppedProperties() {
		return Set.copyOf(this.dropped);
	}

	public List<SegmentEvent> expand(final RunningTemplate template, final CSSStyle container) {
		return this.expand(template, container, false);
	}

	/** legacyの根だけをfixedへ写し、子孫の配置指定は保存します。 */
	public List<SegmentEvent> expand(final RunningTemplate template, final CSSStyle container, final boolean fixed) {
		try (final var scope = this.ua.getUAContext().isolateImageMaps()) {
			return this.expandTemplate(template, container, fixed);
		}
	}

	private List<SegmentEvent> expandTemplate(final RunningTemplate template, final CSSStyle container, final boolean fixed) {
		this.quoteLevel = 0;
		final List<SegmentEvent> events = new ArrayList<SegmentEvent>();
		final StyleBoxEmitter.Replay replay = new StyleBoxEmitter.Replay(this.ua, container, this.page.right(), events::add);
		final Deque<Frame> stack = new ArrayDeque<Frame>();
		for (final RunningTemplate.Event event : template.events()) {
			switch (event) {
			case RunningTemplate.Start start -> {
				final CSSStyle parent = replay.getCurrentStyle();
				final boolean suppressed = !stack.isEmpty() && (stack.peek().hidden() || stack.peek().replaced());
				if (suppressed) {
					stack.push(new Frame(parent, parent, true, false));
					continue;
				}
				final CSSStyle style = this.restore(start.style(), parent, start.pseudo());
				if (fixed && stack.isEmpty()) {
					style.restoreComputed(CSSPosition.INFO, PositionValue.FIXED_VALUE, true);
					style.restoreComputed(Display.INFO, DisplayValue.BLOCK_VALUE, true);
				}
				if (!stack.isEmpty()) {
					// 層で適用済みの効果をidentityで除外できるよう、復元した親へ結び直す。
					final var info = net.zamasoft.foliojet.css.impl.property.box.Filter.INFO;
					final var inherited = (net.zamasoft.foliojet.css.value.css3.FilterValue) parent.get(info);
					final var own = ((net.zamasoft.foliojet.css.value.css3.FilterValue) style.get(info)).own();
					style.restoreComputed(info, inherited.compose(own), start.style().declared().contains(info.getName()));
				}
				final Value[] content = Content.get(style);
				final boolean hidden = Display.get(style) == DisplayValue.NONE
						|| (("before".equals(start.pseudo()) || "after".equals(start.pseudo())) && content == null);
				final boolean replaced = CSSJInternalImage.getImage(style) != null;
				stack.push(new Frame(style, parent, hidden, replaced));
				if (!hidden) {
					replay.start(style);
					if (!replaced && content != null) {
						this.content(template, start.style(), content, replay, events);
					}
					final String imageText = CSSJInternalImage.getText(style);
					if (!replaced && imageText != null) {
						replay.text(imageText, true);
					}
				}
			}
			case RunningTemplate.End end -> {
				final Frame frame = stack.pop();
				if (!frame.hidden()) {
					replay.end(frame.parent());
				}
			}
			case RunningTemplate.Text text -> {
				if (!stack.isEmpty() && !stack.peek().hidden() && !stack.peek().replaced()) {
					replay.text(text.text(), false);
				}
			}
			case RunningTemplate.Token token -> {
				// 内側runningは独立に代入済みです。外側からは再生しません。
			}
			}
		}
		if (!stack.isEmpty()) {
			throw new IllegalArgumentException("running: unbalanced template " + template.name());
		}
		return List.copyOf(events);
	}

	/** 計算値と明示宣言の区別を復元します。カスケードや継承の再計算は行いません。 */
	public CSSStyle restore(final StyleSnapshot snapshot, final CSSStyle parent, final String pseudo) {
		final AttributesImpl attributes = new AttributesImpl();
		snapshot.attributes().forEach((name, value) -> attributes.addAttribute("", name, name, "CDATA", value));
		final CSSElement element = new CSSElement(null, snapshot.elementName(), null, null, null,
				snapshot.language() == null ? null : Locale.forLanguageTag(snapshot.language()), null,
				pseudo == null ? attributes : null, null, -1, -1);
		final CSSStyle style = CSSStyle.getCSSStyle(this.ua, parent, element);
		for (final PrimitivePropertyInfo info : ElementPropertySet.getPrimitiveProperties()) {
			final StyleSnapshot.FrozenValue frozen = snapshot.properties().get(info.getName());
			if (frozen == null) {
				continue;
			}
			try {
				style.restoreComputed(info, (Value) this.thaw(frozen, Value.class), snapshot.declared().contains(info.getName()));
			} catch (final ReflectiveOperationException | IllegalArgumentException e) {
				if (this.dropped.add(info.getName())) {
					this.warn("cannot restore " + info.getName() + " (" + frozen.type() + "): " + e.getMessage());
				}
			}
		}
		if (style.get(CSSPosition.INFO) instanceof RunningPositionValue) {
			style.restoreComputed(CSSPosition.INFO, PositionValue.STATIC_VALUE, false);
		}
		try {
			final var svg = snapshot.svgSource();
			if (svg != null) {
				try (final var scope = this.ua.getUAContext().isolateImageMaps()) {
					final var factory = new org.apache.batik.anim.dom.SAXSVGDocumentFactory(
							org.apache.batik.util.XMLResourceDescriptor.getXMLParserClassName());
					final var document = factory.createDocument(svg.baseURI(), new java.io.StringReader(svg.document()));
					Image image = new net.zamasoft.foliojet.ua.impl.svg.SVGImageLoader()
							.getImage(svg.baseURI(), document, this.ua);
					final double scale = net.zamasoft.foliojet.css.util.LengthUtils.convert(this.ua, 1.0,
							net.zamasoft.foliojet.css.token.Unit.PX, net.zamasoft.foliojet.css.token.Unit.PT);
					if (scale != 1) {
						image = new net.zamasoft.pdfg2d.gc.image.util.TransformedImage(image,
								AffineTransform.getScaleInstance(scale, scale));
					}
					CSSJInternalImage.setImage(style, image);
				}
			} else if (pseudo == null && snapshot.attributes().containsKey("src")) {
				final String src = snapshot.attributes().get("src");
				final URI uri = net.zamasoft.foliojet.css.html.HTMLStyle.imageURI(
						this.ua.getDocumentContext().getEncoding(), baseURI(snapshot), src);
				CSSJInternalImage.setImage(style, this.image(uri));
			}
		} catch (final java.io.IOException | java.net.URISyntaxException | IllegalArgumentException e) {
			this.warn("image: " + e.getMessage());
		}
		return style;
	}

	private static URI baseURI(final StyleSnapshot snapshot) {
		return URI.create(snapshot.baseURI() == null ? "" : snapshot.baseURI());
	}

	private Image image(final URI uri) throws java.io.IOException {
		try (final var scope = this.ua.getUAContext().isolateImageMaps()) {
			final var source = this.ua.resolve(uri);
			try {
				return this.ua.getImage(source);
			} finally {
				this.ua.release(source);
			}
		}
	}

	private void content(final RunningTemplate template, final StyleSnapshot snapshot, final Value[] contents,
			final StyleBoxEmitter.Replay replay, final List<SegmentEvent> events) {
		final StringBuilder text = new StringBuilder();
		for (final Value value : contents) {
			switch (value) {
			case StringValue string -> text.append(string.getString());
			case CounterValue counter -> text.append(this.format(this.page.counter(counter.getName()), counter.getStyle()));
			case CountersValue counters -> text.append(this.join(this.page.counters(counters.getName()),
					counters.getDelimiter(), counters.getStyle()));
			case StringFunctionValue string -> text.append(this.page.string(string.getName(), string.getMode()));
			case AttrValue attr -> text.append(snapshot.attributes().getOrDefault(attr.getName(), ""));
			case QuoteValue quote -> {
				final boolean opening = quote == QuoteValue.OPEN_QUOTE_VALUE || quote == QuoteValue.NO_OPEN_QUOTE_VALUE;
				final boolean emit = quote == QuoteValue.OPEN_QUOTE_VALUE || quote == QuoteValue.CLOSE_QUOTE_VALUE;
				final boolean available = opening || this.quoteLevel > 0;
				if (!opening && available) {
					--this.quoteLevel;
				}
				final Value[] quotes = Quotes.get(replay.getCurrentStyle());
				if (emit && available && quotes != null && quotes.length > 0) {
					final QuotesValue pair = (QuotesValue) quotes[Math.min(this.quoteLevel, quotes.length - 1)];
					text.append(opening ? pair.getOpen() : pair.getClose());
				}
				if (opening) {
					++this.quoteLevel;
				}
			}
			case TargetCounterValue target -> {
				String ref = target.getType() == TargetCounterValue.ATTR
						? snapshot.attributes().get(target.getRef()) : target.getRef();
				if (ref != null) {
					if (ref.indexOf('#') == -1 && (target.getType() == TargetCounterValue.REF || !"href".equals(target.getRef()))) {
						ref = "#" + ref;
					}
					try {
						final URI uri = net.zamasoft.zstream.resolver.util.URIHelper.resolve(
								this.ua.getDocumentContext().getEncoding(), baseURI(snapshot), ref);
						final List<Integer> numbers = this.page.targetCounters(uri, target.getCounter(), target.getSeparator() != null);
						if (!numbers.isEmpty()) {
							text.append(target.getSeparator() == null ? this.format(numbers.get(0), target.getNumberStyleType())
									: this.join(numbers.stream().distinct().toList(), target.getSeparator(), target.getNumberStyleType()));
						}
					} catch (final java.net.URISyntaxException | IllegalArgumentException e) {
						this.warn("target-counter: " + ref);
					}
				}
			}
			case LeaderValue leader -> {
				replay.text(text.toString(), true);
				text.setLength(0);
				events.add(new SegmentEvent.Leader(leader.getPattern()));
			}
			case URIValue uri -> {
				replay.text(text.toString(), true);
				text.setLength(0);
				final CSSStyle parent = replay.getCurrentStyle();
				final CSSStyle image = parent.inheritAnonStyle(CSSElement.ANON);
				image.set(Display.INFO, DisplayValue.INLINE_VALUE);
				try {
					CSSJInternalImage.setImage(image, this.image(uri.getURI()));
					replay.start(image);
					replay.end(parent);
				} catch (final java.io.IOException e) {
					this.warn("content image: " + uri.getURI());
				}
			}
			case ElementFunctionValue element -> this.warn(template.name().equals(element.name())
					? "recursive element(" + element.name() + ")" : "element() is only allowed in margin boxes");
			default -> this.warn("unsupported content: " + value);
			}
		}
		replay.text(text.toString(), true);
	}

	private String format(final int value, final short style) {
		final String text = CounterStyles.of(this.ua).format(value, style);
		return text == null ? "" : text;
	}

	private String join(final List<Integer> values, final String separator, final short style) {
		final List<String> text = new ArrayList<String>(values.size());
		for (final int value : values) {
			text.add(this.format(value, style));
		}
		return String.join(separator == null ? "" : separator, text);
	}

	private void warn(final String message) {
		this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX,
				String.valueOf(this.ua.getDocumentContext().getBaseURI()), "running: " + message);
	}

	/** 値型の完全なコンストラクタです。引数順が異なる型は下の型別分岐で復元します。 */
	private record Shape(List<Field> fields, Constructor<?> constructor) {
	}

	private static final ClassValue<Shape> SHAPES = new ClassValue<Shape>() {
		protected Shape computeValue(final Class<?> type) {
			final List<Field> fields = new ArrayList<Field>();
			for (Class<?> c = type; c != Object.class && c != null; c = c.getSuperclass()) {
				for (final Field field : c.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers())) {
						fields.add(field);
					}
				}
			}
			try {
				final Constructor<?> constructor = type.getDeclaredConstructor(fields.stream().map(Field::getType).toArray(Class<?>[]::new));
				constructor.setAccessible(true);
				return new Shape(List.copyOf(fields), constructor);
			} catch (final NoSuchMethodException e) {
				throw new IllegalArgumentException("no complete value constructor: " + type.getName(), e);
			}
		}
	};

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object thaw(final Object frozen, final Class<?> expected) throws ReflectiveOperationException {
		if (frozen == null) {
			return null;
		}
		if (frozen instanceof StyleSnapshot.FrozenValue value) {
			if (value.type().equals(AbsoluteLengthValue.class.getName())) {
				return AbsoluteLengthValue.create(this.ua, ((Number) value.fields().get("pt")).doubleValue());
			}
			final Class<?> type = Class.forName(value.type());
			if (type.isEnum() && value.type().startsWith("net.zamasoft.")) {
				return Enum.valueOf((Class) type, (String) value.fields().get("name"));
			}
			if (!value.type().startsWith("net.zamasoft.foliojet.css.value.")
					&& !(value.type().startsWith("net.zamasoft.foliojet.css.impl.property.") && Value.class.isAssignableFrom(type))
					&& !value.type().startsWith("net.zamasoft.pdfg2d.gc.")
					&& !value.type().startsWith("net.zamasoft.foliojet.layout.box.params.")) {
				throw new IllegalArgumentException("not a value type: " + value.type());
			}
			if (type == FontFamilyValue.class) {
				final FontFamilyList list = (FontFamilyList) this.thaw(value.fields().get("list"), FontFamilyList.class);
				final FontFamily[] families = new FontFamily[list.getLength()];
				for (int i = 0; i < families.length; ++i) {
					families[i] = list.get(i);
				}
				return new FontFamilyValue(families);
			}
			if (type == net.zamasoft.foliojet.css.value.css3.UnicodeRangeValue.class) {
				final var list = (net.zamasoft.pdfg2d.gc.font.UnicodeRangeList) this.thaw(value.fields().get("list"),
						net.zamasoft.pdfg2d.gc.font.UnicodeRangeList.class);
				return new net.zamasoft.foliojet.css.value.css3.UnicodeRangeValue(list.includes());
			}
			if (type == CSSJFontPolicyValue.class) {
				final FontPolicyList list = (FontPolicyList) this.thaw(value.fields().get("list"), FontPolicyList.class);
				final FontPolicyList.FontPolicy[] policies = new FontPolicyList.FontPolicy[list.getLength()];
				for (int i = 0; i < policies.length; ++i) {
					policies[i] = list.get(i);
				}
				return new CSSJFontPolicyValue(policies);
			}
			if (type == net.zamasoft.pdfg2d.gc.font.FontFeatureSet.class) {
				// hashは派生成分なので、正規化する公開factoryで再計算します。
				return net.zamasoft.pdfg2d.gc.font.FontFeatureSet.of(
						(int[]) this.thaw(value.fields().get("tags"), int[].class),
						(int[]) this.thaw(value.fields().get("values"), int[].class));
			}
			if (type == TargetCounterValue.class) {
				return new TargetCounterValue((Byte) value.fields().get("type"),
						(String) value.fields().get("ref"), (String) value.fields().get("counter"),
						(Short) value.fields().get("numberStyleType"), (String) value.fields().get("separator"));
			}
			if (type == net.zamasoft.foliojet.css.value.css3.TextShadowValue.Shadow.class) {
				return new net.zamasoft.foliojet.css.value.css3.TextShadowValue.Shadow(
						(net.zamasoft.foliojet.css.value.LengthValue) this.thaw(value.fields().get("x"), Value.class),
						(net.zamasoft.foliojet.css.value.LengthValue) this.thaw(value.fields().get("y"), Value.class),
						(net.zamasoft.foliojet.css.value.LengthValue) this.thaw(value.fields().get("blur"), Value.class),
						(net.zamasoft.foliojet.css.value.ColorValue) this.thaw(value.fields().get("color"), Value.class));
			}
			if (type == net.zamasoft.foliojet.css.value.FlexBasisValue.class && value.fields().containsKey("keyword")) {
				return "auto".equals(value.fields().get("keyword"))
						? net.zamasoft.foliojet.css.value.FlexBasisValue.AUTO_VALUE
						: net.zamasoft.foliojet.css.value.FlexBasisValue.CONTENT_VALUE;
			}
			if (type == net.zamasoft.foliojet.css.value.ext.CSSJBreakRuleValue.class
					&& "".equals(value.fields().get("head")) && "".equals(value.fields().get("tail"))) {
				return net.zamasoft.foliojet.css.value.ext.CSSJBreakRuleValue.NONE_VALUE;
			}
			final Shape shape = SHAPES.get(type);
			final Object[] arguments = new Object[shape.fields().size()];
			for (int i = 0; i < arguments.length; ++i) {
				final Field field = shape.fields().get(i);
				if (!value.fields().containsKey(field.getName())) {
					throw new IllegalArgumentException("missing value field: " + field.getName());
				}
				arguments[i] = this.thaw(value.fields().get(field.getName()), field.getType());
			}
			return shape.constructor().newInstance(arguments);
		}
		if (frozen instanceof List<?> values) {
			if (expected == AffineTransform.class) {
				final double[] matrix = (double[]) this.thaw(frozen, double[].class);
				return new AffineTransform(matrix);
			}
			if (expected.isArray()) {
				final Object array = Array.newInstance(expected.componentType(), values.size());
				for (int i = 0; i < values.size(); ++i) {
					Array.set(array, i, this.thaw(values.get(i), expected.componentType()));
				}
				return array;
			}
			final List<Object> copy = new ArrayList<Object>(values.size());
			for (final Object value : values) {
				copy.add(this.thaw(value, Object.class));
			}
			return Collections.unmodifiableList(copy);
		}
		if (frozen instanceof Map<?, ?> values) {
			final Map<String, Object> copy = new LinkedHashMap<String, Object>();
			for (final var entry : values.entrySet()) {
				copy.put((String) entry.getKey(), this.thaw(entry.getValue(), Object.class));
			}
			return Collections.unmodifiableMap(copy);
		}
		if (expected == URI.class) {
			return URI.create((String) frozen);
		}
		return frozen;
	}
}
