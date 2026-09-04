package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.WritingModeVariantValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * CSS Writing Modes の標準 {@code writing-mode} です。
 *
 * <p>
 * {@code direction} は独立したプロパティなので、この shorthand からは変更しません。
 * 旧来の結合した展開は {@link WritingModeShorthand} にだけ残します。
 * </p>
 */
public class WritingModeStandard extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new WritingModeStandard();

	protected WritingModeStandard() {
		super("writing-mode");
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { BlockFlow.INFO, WritingModeVariant.INFO };
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri,
			final Primitives primitives) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			final String value = ident.lower();
			if (value.equals("horizontal-tb") || value.equals("lr") || value.equals("lr-tb")
					|| value.equals("rl") || value.equals("rl-tb")) {
				primitives.set(BlockFlow.INFO, BlockFlowValue.TB_VALUE);
				primitives.set(WritingModeVariant.INFO, WritingModeVariantValue.NORMAL_VALUE);
				return;
			}
			if (value.equals("vertical-rl") || value.equals("tb") || value.equals("tb-rl")) {
				primitives.set(BlockFlow.INFO, BlockFlowValue.RL_VALUE);
				primitives.set(WritingModeVariant.INFO, WritingModeVariantValue.NORMAL_VALUE);
				return;
			}
			if (value.equals("vertical-lr")) {
				primitives.set(BlockFlow.INFO, BlockFlowValue.LR_VALUE);
				primitives.set(WritingModeVariant.INFO, WritingModeVariantValue.NORMAL_VALUE);
				return;
			}
			if (value.equals("sideways-rl")) {
				primitives.set(BlockFlow.INFO, BlockFlowValue.RL_VALUE);
				primitives.set(WritingModeVariant.INFO, WritingModeVariantValue.SIDEWAYS_RL_VALUE);
				return;
			}
			if (value.equals("sideways-lr")) {
				primitives.set(BlockFlow.INFO, BlockFlowValue.LR_VALUE);
				primitives.set(WritingModeVariant.INFO, WritingModeVariantValue.SIDEWAYS_LR_VALUE);
				return;
			}
		}
		throw new PropertyException();
	}
}
