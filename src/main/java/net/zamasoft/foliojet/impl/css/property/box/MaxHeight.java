package net.zamasoft.foliojet.impl.css.property.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.impl.css.property.text.BlockFlow;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class MaxHeight extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaxHeight();

	public static Value get(CSSStyle style) {
		PrimitivePropertyInfo info;
		if (CSSJInternalImage.getImage(style) != null) {
			// 画像には回転を適用しない
			info = INFO;
		} else {
			// 回転
			switch (CSSJDirectionMode.get(style)) {
			case CSSJDirectionModeValue.PHYSICAL:
				info = INFO;
				break;
			case CSSJDirectionModeValue.HORIZONTAL_TB:
				switch (BlockFlow.get(style)) {
				case WritingMode.RL:
				case WritingMode.LR:
					info = MaxWidth.INFO;
					break;
				default:
					info = INFO;
					break;
				}
				break;
			case CSSJDirectionModeValue.VERTICAL_RL:
				switch (BlockFlow.get(style)) {
				case WritingMode.TB:
					info = MaxWidth.INFO;
					break;
				default:
					info = INFO;
					break;
				}
				break;
			default:
				throw new IllegalStateException();
			}
		}
		return style.get(info);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toLength(MaxHeight.get(style));
	}

	private MaxHeight() {
		super("max-height");
	}

	private Value getDefault(UserAgent ua) {
		// return KeywordValue.NONE;
		return ua.getMaxSize();
	}

	public Value getDefault(CSSStyle style) {
		return this.getDefault(style.getUserAgent());
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isNone(lu)) {
			return this.getDefault(ua);
		}
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}