package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.column.ColumnGap;
import net.zamasoft.foliojet.css.impl.property.grid.RowGap;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.GapValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code gap}ショートハンドです(Grid G0)。
 * {@code <row-gap> [<column-gap>]}——1値なら両方に適用。固定長と
 * {@code normal}のみ。
 *
 * @author MIYABE Tatsuhiko
 */
public class GapShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new GapShorthand();

	protected GapShorthand() {
		super("gap");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(RowGap.INFO, global);
			primitives.set(ColumnGap.INFO, global);
			return;
		}
		final Value row = this.parseGap(tokens, ua);
		Value column = row;
		if (tokens.hasNext()) {
			column = this.parseGap(tokens, ua);
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		primitives.set(RowGap.INFO, row);
		primitives.set(ColumnGap.INFO, column);
	}

	private Value parseGap(final TokenStream tokens, final UserAgent ua) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isNormal(lu)) {
			return KeywordValue.NORMAL;
		}
		final Value value = GapValueUtils.toGap(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
