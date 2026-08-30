package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code background-position-x}です。カンマ区切りは既存の多層背景と同じく
 * 全レイヤを検証し、描画で共有する先頭レイヤの値をX成分だけへ設定する。
 */
public class BackgroundPositionX extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundPositionX();

	protected BackgroundPositionX() {
		super("background-position-x");
	}

	public Value getDefault(final CSSStyle style) {
		return PercentageValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	@Override
	public PrimitivePropertyInfo getEffectiveInfo(final CSSStyle style) {
		return BackgroundPosition.INFO_X;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		return BackgroundPosition.parseAxisValues(tokens, ua, true);
	}
}
