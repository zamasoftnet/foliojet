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
 * Box Alignment properties (Grid G5a, 2026-07-31:
 * consult-codex-2026-07-31-grid-g5.txt Q1). Subset:
 * <ul>
 * <li>justify-items / align-items: normal | start | center | end | stretch (default normal)</li>
 * <li>justify-self / align-self: auto + the above (default auto)</li>
 * <li>justify-content / align-content: normal | start | center | end | stretch (default normal)</li>
 * </ul>
 * 2026-08-29 (about 300 occurrences on 22 of 50 real sites were rejected): maps baseline variants
 * ({@code baseline}/{@code first baseline}/{@code last baseline}) to {@code flex-start},
 * {@code self-start}/{@code self-end} to {@code start}/{@code end},
 * and {@code left}/{@code right} for justify-* to {@code start}/{@code end}.
 * Reads and discards leading {@code safe}/{@code unsafe}
 * (paper has no scrolling, so overflow behavior does not differ).
 *
 * @author MIYABE Tatsuhiko
 */
public class GridAlignmentProperty extends AbstractPrimitivePropertyInfo {

	private static final List<BoxAlignmentValue> ITEMS_VALUES = List.of(BoxAlignmentValue.NORMAL,
			BoxAlignmentValue.START, BoxAlignmentValue.CENTER, BoxAlignmentValue.END, BoxAlignmentValue.STRETCH,
			BoxAlignmentValue.FLEX_START, BoxAlignmentValue.FLEX_END);

	private static final List<BoxAlignmentValue> SELF_VALUES = List.of(BoxAlignmentValue.AUTO,
			BoxAlignmentValue.NORMAL, BoxAlignmentValue.START, BoxAlignmentValue.CENTER, BoxAlignmentValue.END,
			BoxAlignmentValue.STRETCH, BoxAlignmentValue.FLEX_START, BoxAlignmentValue.FLEX_END);

	/** Content properties (justify-content/align-content) also accept space-* (Flex F3a). */
	private static final List<BoxAlignmentValue> CONTENT_VALUES = List.of(BoxAlignmentValue.NORMAL,
			BoxAlignmentValue.START, BoxAlignmentValue.CENTER, BoxAlignmentValue.END, BoxAlignmentValue.STRETCH,
			BoxAlignmentValue.FLEX_START, BoxAlignmentValue.FLEX_END, BoxAlignmentValue.SPACE_BETWEEN,
			BoxAlignmentValue.SPACE_AROUND, BoxAlignmentValue.SPACE_EVENLY);

	public static final GridAlignmentProperty JUSTIFY_ITEMS = new GridAlignmentProperty("justify-items",
			ITEMS_VALUES, BoxAlignmentValue.NORMAL);

	public static final GridAlignmentProperty ALIGN_ITEMS = new GridAlignmentProperty("align-items", ITEMS_VALUES,
			BoxAlignmentValue.NORMAL);

	public static final GridAlignmentProperty JUSTIFY_SELF = new GridAlignmentProperty("justify-self", SELF_VALUES,
			BoxAlignmentValue.AUTO);

	public static final GridAlignmentProperty ALIGN_SELF = new GridAlignmentProperty("align-self", SELF_VALUES,
			BoxAlignmentValue.AUTO);

	public static final GridAlignmentProperty JUSTIFY_CONTENT = new GridAlignmentProperty("justify-content",
			CONTENT_VALUES, BoxAlignmentValue.NORMAL);

	public static final GridAlignmentProperty ALIGN_CONTENT = new GridAlignmentProperty("align-content",
			CONTENT_VALUES, BoxAlignmentValue.NORMAL);

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
		final BoxAlignmentValue value = this.eatValue(tokens);
		if (value == null || tokens.hasNext()) {
			// Unknown keywords or combinations such as safe/unsafe are outside the subset (invalid declaration).
			throw new PropertyException();
		}
		return value;
	}

	/**
	 * Consumes and returns an accepted keyword at the start of the stream
	 * (for place-* shorthands, 2026-08-09). Returns null if absent.
	 */
	public BoxAlignmentValue eatValue(final TokenStream tokens) {
		final int mark = tokens.position();
		// Read and discard <overflow-position> (safe|unsafe) (2026-08-29).
		final boolean overflow = tokens.eat("safe") || tokens.eat("unsafe");
		for (final BoxAlignmentValue value : this.accepted) {
			if (tokens.eat(value.toString())) {
				return value;
			}
		}
		// <baseline-position>: [first|last]? baseline → flex-start approximation.
		if (!overflow) {
			final boolean firstLast = tokens.eat("first") || tokens.eat("last");
			if (tokens.eat("baseline")) {
				return BoxAlignmentValue.FLEX_START;
			}
			if (firstLast) {
				tokens.rewind(mark);
				return null;
			}
		}
		if (tokens.eat("self-start")) {
			return BoxAlignmentValue.START;
		}
		if (tokens.eat("self-end")) {
			return BoxAlignmentValue.END;
		}
		if (this.getName().startsWith("justify-")) {
			if (tokens.eat("left")) {
				return BoxAlignmentValue.START;
			}
			if (tokens.eat("right")) {
				return BoxAlignmentValue.END;
			}
		}
		tokens.rewind(mark);
		return null;
	}
}
