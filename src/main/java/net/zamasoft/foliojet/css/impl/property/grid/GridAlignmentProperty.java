package net.zamasoft.foliojet.css.impl.property.grid;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.BoxAlignmentValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Box Alignment系プロパティです(Grid G5a、2026-07-31——
 * consult-codex-2026-07-31-grid-g5.txt Q1)。サブセット:
 * <ul>
 * <li>justify-items / align-items: normal | start | center | end | stretch(既定normal)</li>
 * <li>justify-self / align-self: auto+同上(既定auto)</li>
 * <li>justify-content / align-content: normal | start | center | end | stretch(既定normal)</li>
 * </ul>
 * baseline系・space-*・left/right・self-start/self-end・safe/unsafe prefixは
 * 宣言無効(startへ丸めない)。
 *
 * @author MIYABE Tatsuhiko
 */
public class GridAlignmentProperty extends AbstractPrimitivePropertyInfo {

	private static final List<BoxAlignmentValue> ITEMS_VALUES = List.of(BoxAlignmentValue.NORMAL,
			BoxAlignmentValue.START, BoxAlignmentValue.CENTER, BoxAlignmentValue.END, BoxAlignmentValue.STRETCH);

	private static final List<BoxAlignmentValue> SELF_VALUES = List.of(BoxAlignmentValue.AUTO,
			BoxAlignmentValue.NORMAL, BoxAlignmentValue.START, BoxAlignmentValue.CENTER, BoxAlignmentValue.END,
			BoxAlignmentValue.STRETCH);

	public static final PrimitivePropertyInfo JUSTIFY_ITEMS = new GridAlignmentProperty("justify-items",
			ITEMS_VALUES, BoxAlignmentValue.NORMAL);

	public static final PrimitivePropertyInfo ALIGN_ITEMS = new GridAlignmentProperty("align-items", ITEMS_VALUES,
			BoxAlignmentValue.NORMAL);

	public static final PrimitivePropertyInfo JUSTIFY_SELF = new GridAlignmentProperty("justify-self", SELF_VALUES,
			BoxAlignmentValue.AUTO);

	public static final PrimitivePropertyInfo ALIGN_SELF = new GridAlignmentProperty("align-self", SELF_VALUES,
			BoxAlignmentValue.AUTO);

	public static final PrimitivePropertyInfo JUSTIFY_CONTENT = new GridAlignmentProperty("justify-content",
			ITEMS_VALUES, BoxAlignmentValue.NORMAL);

	public static final PrimitivePropertyInfo ALIGN_CONTENT = new GridAlignmentProperty("align-content",
			ITEMS_VALUES, BoxAlignmentValue.NORMAL);

	public static BoxAlignmentValue get(CSSStyle style, PrimitivePropertyInfo info) {
		return (BoxAlignmentValue) style.get(info);
	}

	private final List<BoxAlignmentValue> accepted;

	private final BoxAlignmentValue defaultValue;

	protected GridAlignmentProperty(final String name, final List<BoxAlignmentValue> accepted,
			final BoxAlignmentValue defaultValue) {
		super(name);
		this.accepted = accepted;
		this.defaultValue = defaultValue;
	}

	public Value getDefault(CSSStyle style) {
		return this.defaultValue;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		for (final BoxAlignmentValue value : this.accepted) {
			if (tokens.eat(value.toString())) {
				if (tokens.hasNext()) {
					// safe/unsafe等の複合はサブセット外(宣言無効)
					throw new PropertyException();
				}
				return value;
			}
		}
		throw new PropertyException();
	}
}
