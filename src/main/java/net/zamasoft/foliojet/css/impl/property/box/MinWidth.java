package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class MinWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MinWidth();

	public static Value get(CSSStyle style) {
		PrimitivePropertyInfo info;
		boolean image = CSSJInternalImage.getImage(style) != null;
		if (image) {
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
					info = MinHeight.INFO;
					break;
				default:
					info = INFO;
					break;
				}
				break;
			case CSSJDirectionModeValue.VERTICAL_RL:
				switch (BlockFlow.get(style)) {
				case WritingMode.TB:
					info = MinHeight.INFO;
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
		if (style.isDeclared(info)) {
			return style.get(info);
		}
		// -cssj-direction-modeによる回転(foliojet4独自・既定無効)が効いていない
		// 場合のみ、標準の論理プロパティinline-size/block-sizeをフォールバックとして
		// 見る(両方の仕組みを重ねると解釈が曖昧になるため。docs/PLAN.md参照)。
		if (!image && CSSJDirectionMode.get(style) == CSSJDirectionModeValue.PHYSICAL) {
			PrimitivePropertyInfo logicalInfo = BlockFlow.get(style).isVertical() ? MinBlockSize.INFO : MinInlineSize.INFO;
			if (style.isDeclared(logicalInfo)) {
				return style.get(logicalInfo);
			}
		}
		return style.get(info);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toLength(MinWidth.get(style));
	}

	private MinWidth() {
		super("min-width");
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}