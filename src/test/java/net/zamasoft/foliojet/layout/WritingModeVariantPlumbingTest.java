package net.zamasoft.foliojet.layout;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.WritingModeVariantValue;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.ContinuationCapability;
import net.zamasoft.foliojet.layout.fragment.OpenPathScan;
import net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.ua.UserAgent;

/** sideways の非表示 model/replay plumbing を固定する単体テストです。 */
public class WritingModeVariantPlumbingTest extends TestCase {
	private static final URI BASE_URI = URI.create("file:///dev/null/");

	private static UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(WritingModeVariantPlumbingTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> switch (method.getName()) {
				case "message" -> null;
				case "toString" -> "WritingModeVariantPlumbingTest.UserAgent";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> throw new UnsupportedOperationException(method.toString());
				});
	}

	private static List<CssToken> tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList declarations = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull(declarations);
		final List<CSSDeclaration> all = declarations.getAllDeclarations();
		assertEquals(1, all.size());
		return Tokens.fromExpression(all.get(0).getExpression());
	}

	private static Entry[] parse(final String name, final String value) {
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name,
				tokens(name + ": " + value), ua(), BASE_URI, false);
		assertNotNull(name + ": " + value, property);
		assertTrue(property instanceof CompositeProperty);
		return ((CompositeProperty) property).getEntries();
	}

	private static Value longhand(final String name, final String value, final PrimitivePropertyInfo info) {
		for (final Entry entry : parse(name, value)) {
			if (entry.getPrimitivePropertyInfo() == info) {
				return entry.getValue();
			}
		}
		fail(name + ": " + value + " が " + info.getName() + " を設定していない");
		return null;
	}

	public void testPropertyParsesAndDefaultsWithoutEnablingStandardSideways() {
		assertTrue(WritingModeVariant.INFO.isInherited());
		assertSame(WritingModeVariantValue.NORMAL_VALUE, WritingModeVariant.INFO.getDefault(null));
		assertSame(net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL,
				new BlockParams().writingModeVariant);
		assertSame(net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW,
				WritingModeVariantValue.SIDEWAYS_RL_VALUE.getWritingModeVariant());
		assertSame(net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CCW,
				WritingModeVariantValue.SIDEWAYS_LR_VALUE.getWritingModeVariant());
		assertSame(WritingModeVariantValue.NORMAL_VALUE,
				longhand("-cssj-writing-mode-variant", "normal", WritingModeVariant.INFO));
		final CSSStyle parent = CSSStyle.getCSSStyle(ua(), null, CSSElement.ANON);
		parent.set(WritingModeVariant.INFO, WritingModeVariantValue.SIDEWAYS_RL_VALUE);
		final CSSStyle child = CSSStyle.getCSSStyle(ua(), parent, CSSElement.ANON);
		assertSame(net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW,
				WritingModeVariant.get(child));

		// 分離 batch(2026-09-04)で標準 writing-mode は sideways を受理する(BlockFlow+variant、direction 不変)
		assertSame(BlockFlowValue.RL_VALUE, longhand("writing-mode", "sideways-rl", BlockFlow.INFO));
		assertSame(WritingModeVariantValue.SIDEWAYS_RL_VALUE,
				longhand("writing-mode", "sideways-rl", WritingModeVariant.INFO));
		assertSame(BlockFlowValue.LR_VALUE, longhand("writing-mode", "sideways-lr", BlockFlow.INFO));
		assertSame(WritingModeVariantValue.SIDEWAYS_LR_VALUE,
				longhand("writing-mode", "sideways-lr", WritingModeVariant.INFO));
		for (final Entry entry : parse("writing-mode", "sideways-rl")) {
			assertNotSame("standard writing-mode must not set direction", Direction.INFO,
					entry.getPrimitivePropertyInfo());
		}
	}

	public void testLegacyWritingModeAndTextCombineResetVariantToNormal() {
		final String[] legacyValues = { "lr-tb", "lr", "horizontal-tb", "rl-tb", "rl", "tb-rl", "tb",
				"vertical-rl", "tb-lr", "vertical-lr" };
		for (final String value : legacyValues) {
			assertSame(value, WritingModeVariantValue.NORMAL_VALUE,
					longhand("-cssj-writing-mode", value, WritingModeVariant.INFO));
		}
		// legacy の既存 Direction/BlockFlow 展開は変更しない。
		assertSame(DirectionValue.RTL_VALUE,
				longhand("-cssj-writing-mode", "vertical-lr", Direction.INFO));
		assertSame(BlockFlowValue.LR_VALUE,
				longhand("-cssj-writing-mode", "vertical-lr", BlockFlow.INFO));
		assertSame(WritingModeVariantValue.NORMAL_VALUE,
				longhand("text-combine-upright", "all", WritingModeVariant.INFO));
		assertSame(DirectionValue.LTR_VALUE,
				longhand("text-combine-upright", "all", Direction.INFO));
		assertSame(BlockFlowValue.TB_VALUE,
				longhand("text-combine-upright", "all", BlockFlow.INFO));
	}

	/** 箱のコンストラクタは {@code assert fontStyle != null} を持つので、試験用の最小 FontStyle を与える。 */
	private static final net.zamasoft.pdfg2d.gc.font.FontStyle DUMMY_FONT_STYLE = new net.zamasoft.pdfg2d.gc.font.FontStyleImpl(
			net.zamasoft.pdfg2d.gc.font.FontFamilyList.SERIF, 12, net.zamasoft.pdfg2d.gc.font.FontStyle.Style.NORMAL,
			net.zamasoft.pdfg2d.gc.font.FontStyle.Weight.W_400, net.zamasoft.pdfg2d.gc.font.FontStyle.Direction.LTR,
			net.zamasoft.pdfg2d.gc.font.FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);

	public void testTextParamsFreezeAndReplayCopiesRoundTripVariant() {
		final BlockParams source = new BlockParams();
		source.fontStyle = DUMMY_FONT_STYLE;
		source.flow = WritingMode.LR;
		source.writingModeVariant = net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CCW;
		source.direction = AbstractTextParams.DIRECTION_RTL;

		final BlockParams materialized = BlockParamsTemplate.freeze(source).materialize();
		assertSame(source.writingModeVariant, materialized.writingModeVariant);
		assertSame(source.writingModeVariant,
				SourceReplayer.createMeasureWrapperParams(source).writingModeVariant);

		final MeasurePageGenerator pages = new MeasurePageGenerator(null, source, 100, 200);
		assertSame(source.writingModeVariant, pages.nextPage().getBlockParams().writingModeVariant);
	}

	public void testFragmentSignatureAndContinuationIncludeVariant() {
		final OpenPathSnapshot.FragmentSignature normal = new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class,
				WritingMode.RL, net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL, 1);
		final OpenPathSnapshot.FragmentSignature sideways = new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class,
				WritingMode.RL, net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW, 1);
		assertFalse(normal.equals(sideways));

		final BlockParams params = new BlockParams();
		params.fontStyle = DUMMY_FONT_STYLE;
		params.flow = WritingMode.RL;
		params.writingModeVariant = net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW;
		final FlowBlockBox box = new FlowBlockBox(params, new FlowPos());
		assertSame(ContinuationCapability.PLAIN_FLOW, ContinuationCapability.classify(box, WritingMode.RL,
				net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW));
		assertSame(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE,
				ContinuationCapability.classify(box, WritingMode.RL,
						net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL));

		final BlockParams anchorParams = new BlockParams();
		anchorParams.fontStyle = DUMMY_FONT_STYLE;
		anchorParams.flow = WritingMode.RL;
		final FlowBlockBox anchor = new FlowBlockBox(anchorParams, new FlowPos());
		final OpenPathScan scan = OpenPathScan.capture(List.of(anchor, box), new BreakMode.AutoBreakMode(box));
		assertSame(net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL,
				scan.snapshot().anchorWritingModeVariant());
		assertSame(net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CW,
				scan.snapshot().levels().get(1).writingModeVariant());
		assertSame(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE,
				scan.snapshot().firstBarrier().orElseThrow().reason());
	}
}
