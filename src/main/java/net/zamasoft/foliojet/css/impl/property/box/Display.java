package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class Display extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Display();

	public static byte get(CSSStyle style) {
		DisplayValue value = (DisplayValue) style.get(INFO);
		return value.getDisplay();
	}

	protected Display() {
		super("display");
	}

	/**
	 * Returns the effective parent display in the box tree, skipping display:contents ancestors
	 * (2026-08-07). Since contents elements create no box, anonymous box completion and flex/grid
	 * item detection must treat the nearest non-contents ancestor as the parent.
	 * Returns NONE if there is no parent (falls through to the caller's default branch).
	 */
	public static byte getFlattenedParentDisplay(CSSStyle style) {
		for (CSSStyle p = style.getParentStyle(); p != null; p = p.getParentStyle()) {
			final byte d = Display.get(p);
			if (d != DisplayValue.CONTENTS) {
				return d;
			}
		}
		return DisplayValue.NONE;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		byte display = ((DisplayValue) value).getDisplay();

		// display:contents (CSS Display 3 §2.5). Do not create a box for the element itself.
		// For replaced elements (img, etc.), the "contents" are the replaced content itself,
		// so contents behaves like none (per the specification). No box means float/position
		// do not apply; skip all conversions below.
		if (display == DisplayValue.CONTENTS) {
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.NONE_VALUE;
			}
			return DisplayValue.CONTENTS_VALUE;
		}

		// **Blockify floats placed at page level (footnotes and page floats)**
		// (2026-08-02, found during a sweep). They are placed separately from the type area
		// and always created as block boxes. If display remained a table type,
		// child tbody/tr elements required a "table box" and construction failed
		// for the same reason that absolute positioning blockifies display (CSS Display 3 §2.7).
		if (display != DisplayValue.NONE && CSSFloatValue.isPageLevel(CSSFloat.get(style))) {
			return DisplayValue.BLOCK_VALUE;
		}

		// Conversion for floats.
		switch (display) {
		case DisplayValue.INLINE_TABLE: {
			final byte position = CSSPosition.get(style);
			if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
				break;
			}
			if (CSSFloat.get(style) != CSSFloatValue.NONE) {
				value = DisplayValue.TABLE_VALUE;
				display = DisplayValue.TABLE;
			}
		}
			break;
		case DisplayValue.INLINE: {
			final short position = CSSPosition.get(style);
			if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
				value = DisplayValue.INLINE_BLOCK_VALUE;
				display = DisplayValue.INLINE_BLOCK;
				break;
			}
			if (CSSFloat.get(style) != CSSFloatValue.NONE) {
				value = DisplayValue.BLOCK_VALUE;
				display = DisplayValue.BLOCK;
			}
		}
			break;
		case DisplayValue.INLINE_BLOCK: {
			final short position = CSSPosition.get(style);
			if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
				break;
			}
			if (CSSFloat.get(style) != CSSFloatValue.NONE) {
				value = DisplayValue.BLOCK_VALUE;
				display = DisplayValue.BLOCK;
			}
		}
			break;
		case DisplayValue.TABLE_CAPTION:
			// Treat captions outside tables as blocks.
			final CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle != null) {
				switch (Display.getFlattenedParentDisplay(style)) {
				case DisplayValue.TABLE:
				case DisplayValue.TABLE_COLUMN_GROUP:
				case DisplayValue.TABLE_COLUMN:
				case DisplayValue.TABLE_ROW_GROUP:
				case DisplayValue.TABLE_HEADER_GROUP:
				case DisplayValue.TABLE_FOOTER_GROUP:
				case DisplayValue.TABLE_ROW:
					break;
				default:
					value = DisplayValue.BLOCK_VALUE;
					display = DisplayValue.BLOCK;
					break;
				}
			}
		case DisplayValue.TABLE_ROW_GROUP:
		case DisplayValue.TABLE_COLUMN:
		case DisplayValue.TABLE_COLUMN_GROUP:
		case DisplayValue.TABLE_HEADER_GROUP:
		case DisplayValue.TABLE_FOOTER_GROUP:
		case DisplayValue.TABLE_ROW:
		case DisplayValue.TABLE_CELL: {
			final short position = CSSPosition.get(style);
			if (CSSFloat.get(style) != CSSFloatValue.NONE
					|| (position != PositionValue.STATIC && position != PositionValue.RELATIVE
							&& position != PositionValue.STICKY)) {
				value = DisplayValue.BLOCK_VALUE;
				display = DisplayValue.BLOCK;
			}
		}
			break;

		case DisplayValue.NONE:
		case DisplayValue.BLOCK:
		case DisplayValue.LIST_ITEM:
		case DisplayValue.TABLE:
		case DisplayValue.GRID:
		case DisplayValue.FLEX:
			break;
		default:
			throw new IllegalStateException();
		}

		// Blockify direct Grid/Flex children (Grid G0: css-grid-1 §6; Flex F0a:
		// css-flexbox-1 §4, "flex items are blockified"). Promote inline children
		// to blocks instead of anonymous items.
		if ((display == DisplayValue.INLINE || display == DisplayValue.INLINE_BLOCK)
				&& !net.zamasoft.foliojet.xml.vocab.XHTML.BR_ELEM.equalsElement(style.getCSSElement())) {
			// Except <br>: it breaks the line of the anonymous item around it, as in Chrome (2026-10-08)
			final CSSStyle flexParent = style.getParentStyle();
			if (flexParent != null) {
				// Skip contents ancestors; their children become direct
				// flex/grid items (CSS Display 3 §2.5).
				final byte parentDisplay = Display.getFlattenedParentDisplay(style);
				if (parentDisplay == DisplayValue.GRID || parentDisplay == DisplayValue.FLEX) {
					value = DisplayValue.BLOCK_VALUE;
					display = DisplayValue.BLOCK;
				}
			}
		}

		// Conversion for replaced boxes.
		switch (display) {
		case DisplayValue.INLINE_TABLE:
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.INLINE_VALUE;
			}
			break;

		case DisplayValue.LIST_ITEM:
		case DisplayValue.TABLE:
		case DisplayValue.TABLE_ROW_GROUP:
		case DisplayValue.TABLE_HEADER_GROUP:
		case DisplayValue.TABLE_FOOTER_GROUP:
		case DisplayValue.TABLE_ROW:
		case DisplayValue.TABLE_CELL:
		case DisplayValue.TABLE_CAPTION:
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.BLOCK_VALUE;
			}
			break;

		case DisplayValue.TABLE_COLUMN:
		case DisplayValue.TABLE_COLUMN_GROUP:
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.NONE_VALUE;
			}
			break;

		case DisplayValue.GRID:
		case DisplayValue.FLEX:
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.BLOCK_VALUE;
			}
			break;

		case DisplayValue.INLINE:
		case DisplayValue.NONE:
		case DisplayValue.BLOCK:
		case DisplayValue.INLINE_BLOCK:
			break;
		default:
			throw new IllegalStateException();
		}

		// Conversion for tate-chu-yoko / vertical text within horizontal text.
		if (display == DisplayValue.INLINE) {
			CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle != null && BlockFlow.get(parentStyle).isVertical() != BlockFlow.get(style).isVertical()) {
				return DisplayValue.INLINE_BLOCK_VALUE;
			}
		}

		return value;
	}

	public Value getDefault(CSSStyle style) {
		return DisplayValue.INLINE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("none")) {
				return DisplayValue.NONE_VALUE;
			} else if (ident.equals("block")) {
				return DisplayValue.BLOCK_VALUE;
			} else if (ident.equals("inline")) {
				return DisplayValue.INLINE_VALUE;
			} else if (ident.equals("inline-block")) {
				return DisplayValue.INLINE_BLOCK_VALUE;
			} else if (ident.equals("list-item")) {
				return DisplayValue.LIST_ITEM_VALUE;
			} else if (ident.equals("contents")) {
				return DisplayValue.CONTENTS_VALUE;
			} else if (ident.equals("flow-root")) {
				return DisplayValue.FLOW_ROOT_VALUE;
				// run-in is unsupported (removed in 4; also at-risk in CSS Display 3).
				// Invalidate the entire declaration as an unsupported value (as modern browsers do).
			} else {
				if (ident.equals("table")) {
					return DisplayValue.TABLE_VALUE;
				} else if (ident.equals("inline-table")) {
					return DisplayValue.INLINE_TABLE_VALUE;
				} else if (ident.equals("table-row-group")) {
					return DisplayValue.TABLE_ROW_GROUP_VALUE;
				} else if (ident.equals("table-column")) {
					return DisplayValue.TABLE_COLUMN_VALUE;
				} else if (ident.equals("table-column-group")) {
					return DisplayValue.TABLE_COLUMN_GROUP_VALUE;
				} else if (ident.equals("table-header-group")) {
					return DisplayValue.TABLE_HEADER_GROUP_VALUE;
				} else if (ident.equals("table-footer-group")) {
					return DisplayValue.TABLE_FOOTER_GROUP_VALUE;
				} else if (ident.equals("table-row")) {
					return DisplayValue.TABLE_ROW_VALUE;
				} else if (ident.equals("table-cell")) {
					return DisplayValue.TABLE_CELL_VALUE;
				} else if (ident.equals("grid")) {
					return DisplayValue.GRID_VALUE;
				} else if (ident.equals("flex")) {
					// Flex F0a(consult-codex-2026-08-02-flexbox.txt)
					return DisplayValue.FLEX_VALUE;
				} else if (ident.equals("inline-flex")) {
					// **Approximate inline-level flex/grid with block-level flex/grid**
					// (2026-08-11). True inline-flex lays out the contents of an atomic inline box
					// using flex, requiring two boxes: outer inline-block and inner flex.
					// Until then, this is closer than the old behavior of discarding the declaration:
					// discarding it reverted the flex container to a plain block, so 13 navigation
					// items that should have been horizontal stacked vertically and covered 507 pt
					// of body text (measured on sankei.com global navigation).
					// Small inline-flex elements within a line (badges, etc.) produce more line breaks
					// than intended. The choice between these errors was based on
					// imageTest measurements.
					return DisplayValue.FLEX_VALUE;
				} else if (ident.equals("inline-grid")) {
					// Same approximation as inline-flex (see above).
					return DisplayValue.GRID_VALUE;
				} else if (ident.equals("table-caption")) {
					return DisplayValue.TABLE_CAPTION_VALUE;
				}
				// Prefixed aliases (2026-08-29). By far the most frequent unsupported values:
				// 4777 occurrences on 33 of 50 real sites.
				switch (ident) {
				case "-webkit-flex":
				case "-moz-flex":
				case "-ms-flexbox":
				case "-webkit-inline-flex":
				case "-ms-inline-flexbox":
					// 2012 flexbox. Same as current flex (inline-* uses the same approximation
					// as inline-flex above).
					return DisplayValue.FLEX_VALUE;
				case "-ms-grid":
					return DisplayValue.GRID_VALUE;
				case "-webkit-box":
				case "-moz-box":
				case "-webkit-inline-box":
					// 2009 flexbox. **Do not map to flex**.
					// The `display:-webkit-box; -webkit-line-clamp:N` line-clamping
					// idiom depends on the box being a block;
					// flex would arrange its contents in one row. Real sites always follow it with
					// the current `display:flex`, so where flex is needed,
					// that declaration wins by cascade order.
					return DisplayValue.BLOCK_VALUE;
				default:
					break;
				}
			}
		}
		throw new PropertyException();
	}

}
