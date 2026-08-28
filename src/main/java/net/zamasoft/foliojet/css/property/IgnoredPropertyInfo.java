package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 受理はするが何もしないプロパティ(2026-08-29)。
 *
 * <p>
 * {@code @font-face}の{@code font-display}・{@code size-adjust}等、値の
 * 検証をする価値も警告する価値もない記述子に使う。どんな値でも成功し、
 * 展開結果は空。
 * </p>
 */
public final class IgnoredPropertyInfo extends AbstractPropertyInfo {
	public IgnoredPropertyInfo(final String name) {
		super(name);
	}

	@Override
	public Property parse(final TokenStream tokens, final UserAgent ua, final URI uri, final boolean important)
			throws PropertyException {
		return new CompositeProperty(this.getName(), new CompositeProperty.Entry[0], uri, important);
	}
}
