package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.box.MixBlendMode;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;

/** {@code background-blend-mode} (Compositing 1)です。 */
public class BackgroundBlendMode extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundBlendMode();

	/** 先頭が最前面の背景画像に対応するブレンドモード列です。 */
	public record BlendModesValue(BlendMode[] modes) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.modes);
		}
	}

	private static final BlendModesValue NORMAL = new BlendModesValue(new BlendMode[] { BlendMode.NORMAL });

	public static BlendMode[] get(final CSSStyle style) {
		return ((BlendModesValue) style.get(INFO)).modes();
	}

	protected BackgroundBlendMode() {
		super("background-blend-mode");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return NORMAL;
	}

	@Override
	public boolean isInherited() {
		return false;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final List<BlendMode> modes = new ArrayList<>();
		for (final TokenStream layer : tokens.splitComma()) {
			final CssToken token = layer.next();
			final BlendMode mode = MixBlendMode.parseBlendMode(token);
			if (mode == null || layer.hasNext()) {
				throw new PropertyException();
			}
			modes.add(mode);
		}
		if (modes.isEmpty()) {
			throw new PropertyException();
		}
		if (modes.size() == 1 && modes.get(0) == BlendMode.NORMAL) {
			return NORMAL;
		}
		return new BlendModesValue(modes.toArray(new BlendMode[modes.size()]));
	}
}
