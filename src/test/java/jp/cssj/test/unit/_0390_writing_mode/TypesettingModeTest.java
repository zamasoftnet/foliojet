package jp.cssj.test.unit._0390_writing_mode;

import java.lang.reflect.Proxy;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisStyle;
import net.zamasoft.foliojet.css.impl.property.text.TextOrientation;
import net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.TextOrientationValue;
import net.zamasoft.foliojet.css.value.WritingModeVariantValue;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode.InlineProgression;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode.PhysicalSide;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/** 物理 flow と組版モードを分離する共通判定の単体テストです。 */
public class TypesettingModeTest extends TestCase {
	private static UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(TypesettingModeTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> switch (method.getName()) {
				case "message" -> null;
				case "toString" -> "TypesettingModeTest.UserAgent";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> throw new UnsupportedOperationException(method.toString());
				});
	}

	private static CSSStyle style(final WritingMode flow,
			final net.zamasoft.foliojet.layout.box.params.WritingModeVariant variant,
			final byte direction) {
		final CSSStyle style = CSSStyle.getCSSStyle(ua(), null, CSSElement.ANON);
		style.set(BlockFlow.INFO, switch (flow) {
		case TB -> BlockFlowValue.TB_VALUE;
		case RL -> BlockFlowValue.RL_VALUE;
		case LR -> BlockFlowValue.LR_VALUE;
		});
		style.set(WritingModeVariant.INFO, switch (variant) {
		case NORMAL -> WritingModeVariantValue.NORMAL_VALUE;
		case SIDEWAYS_CW -> WritingModeVariantValue.SIDEWAYS_RL_VALUE;
		case SIDEWAYS_CCW -> WritingModeVariantValue.SIDEWAYS_LR_VALUE;
		});
		style.set(Direction.INFO, direction == AbstractTextParams.DIRECTION_LTR
				? DirectionValue.LTR_VALUE : DirectionValue.RTL_VALUE);
		return style;
	}

	public void testAllFlowVariantDirectionCombinations() {
		for (final WritingMode flow : WritingMode.values()) {
			for (final net.zamasoft.foliojet.layout.box.params.WritingModeVariant variant
					: net.zamasoft.foliojet.layout.box.params.WritingModeVariant.values()) {
				for (final byte direction : new byte[] { AbstractTextParams.DIRECTION_LTR,
						AbstractTextParams.DIRECTION_RTL }) {
					final boolean horizontal = !flow.isVertical()
							|| variant != net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL;
					assertEquals(horizontal, TypesettingMode.isHorizontal(flow, variant));
					assertEquals(!horizontal, TypesettingMode.isVertical(flow, variant));

					final BlockParams params = new BlockParams();
					params.flow = flow;
					params.writingModeVariant = variant;
					params.direction = direction;
					assertEquals(horizontal, params.isHorizontalTypesetting());
					assertEquals(!horizontal, params.isVerticalTypesetting());
					assertSame(variant, params.getGlyphRotation());
					assertSame(TypesettingMode.inlineProgression(flow, variant, direction),
							params.getInlineProgression());
					assertEquals(params.getInlineProgression().sign(), params.getInlineProgressionSign());
					assertSame(TypesettingMode.overSide(flow, variant), params.getTypesettingOverSide());
				}
			}
		}
	}

	public void testSidewaysFourQuadrantsAndOverSides() {
		for (final WritingMode flow : new WritingMode[] { WritingMode.RL, WritingMode.LR }) {
			assertSame(InlineProgression.TOP_TO_BOTTOM,
					TypesettingMode.inlineProgression(flow,
							net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW,
							AbstractTextParams.DIRECTION_LTR));
			assertSame(InlineProgression.BOTTOM_TO_TOP,
					TypesettingMode.inlineProgression(flow,
							net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW,
							AbstractTextParams.DIRECTION_RTL));
			assertSame(InlineProgression.BOTTOM_TO_TOP,
					TypesettingMode.inlineProgression(flow,
							net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CCW,
							AbstractTextParams.DIRECTION_LTR));
			assertSame(InlineProgression.TOP_TO_BOTTOM,
					TypesettingMode.inlineProgression(flow,
							net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CCW,
							AbstractTextParams.DIRECTION_RTL));
		}
		assertSame(PhysicalSide.RIGHT, TypesettingMode.overSide(WritingMode.RL,
				net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW));
		assertSame(PhysicalSide.LEFT, TypesettingMode.overSide(WritingMode.LR,
				net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CCW));
	}

	public void testFontDirectionUsesHorizontalModelForSideways() {
		for (final WritingMode flow : WritingMode.values()) {
			for (final net.zamasoft.foliojet.layout.box.params.WritingModeVariant variant
					: net.zamasoft.foliojet.layout.box.params.WritingModeVariant.values()) {
				for (final byte direction : new byte[] { AbstractTextParams.DIRECTION_LTR,
						AbstractTextParams.DIRECTION_RTL }) {
					final FontStyle.Direction expected = flow.isVertical()
							&& variant == net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL
									? FontStyle.Direction.TB
									: direction == AbstractTextParams.DIRECTION_LTR
											? FontStyle.Direction.LTR : FontStyle.Direction.RTL;
					assertSame(expected, Direction.getFontDirection(style(flow, variant, direction)));
				}
			}
		}
	}

	public void testSidewaysUsedTextOrientationAndEmphasisDefault() {
		for (final net.zamasoft.foliojet.layout.box.params.WritingModeVariant variant
				: new net.zamasoft.foliojet.layout.box.params.WritingModeVariant[] {
						net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW,
						net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CCW }) {
			assertSame(FontStyle.TextOrientation.MIXED,
					TypesettingMode.usedTextOrientation(variant, FontStyle.TextOrientation.UPRIGHT));
			assertSame(FontStyle.TextOrientation.MIXED,
					TypesettingMode.usedTextOrientation(variant, FontStyle.TextOrientation.SIDEWAYS));
			final CSSStyle style = style(variant
					== net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW
							? WritingMode.RL : WritingMode.LR, variant, AbstractTextParams.DIRECTION_LTR);
			final TextOrientationValue computed = variant
					== net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW
							? TextOrientationValue.UPRIGHT : TextOrientationValue.SIDEWAYS;
			style.set(TextOrientation.INFO, computed);
			assertSame("computed text-orientation must be preserved", computed, style.get(TextOrientation.INFO));
			style.set(TextEmphasisStyle.INFO, TextEmphasisStyle.AUTO_FILLED);
			assertSame(TextEmphasisStyle.FILLED_CIRCLE, style.get(TextEmphasisStyle.INFO));
		}

		assertSame(FontStyle.TextOrientation.UPRIGHT,
				TypesettingMode.usedTextOrientation(
						net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL,
						FontStyle.TextOrientation.UPRIGHT));
		final CSSStyle vertical = style(WritingMode.RL,
				net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL,
				AbstractTextParams.DIRECTION_LTR);
		vertical.set(TextEmphasisStyle.INFO, TextEmphasisStyle.AUTO_FILLED);
		assertSame(TextEmphasisStyle.FILLED_SESAME, vertical.get(TextEmphasisStyle.INFO));
	}
}
