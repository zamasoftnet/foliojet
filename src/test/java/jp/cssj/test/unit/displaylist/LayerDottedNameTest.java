package jp.cssj.test.unit.displaylist;

import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.parser.AtRulePreludeRewriter;

/**
 * Cascade layers with dotted names, and the order of nested layers (2026-10-09).
 *
 * <p>
 * ph-css 8.2.1 cannot read {@code @layer a.b {...}} or {@code @layer a.b, c.d;}: the block was dropped silently, with
 * the outer layer around it, and a dotted statement took the next rule with it. daisyui's utilities
 * ({@code @layer daisyui.l1.l2}) and Docusaurus's theme ({@code @layer docusaurus.theme-classic}) did not apply. The
 * layers were also ordered as independent names by first appearance; CSS Cascade 5 §6.4 orders them as a tree. The
 * expected margins are Chrome 151's for {@code files/unittest/3070-AT-RULE/layer-dotted-names.html} at 794px.
 * </p>
 */
public class LayerDottedNameTest extends TestCase {
	public void testSameAsChrome() throws Exception {
		final Map<String, double[]> texts = EndTagReopenTest
				.texts("files/unittest/3070-AT-RULE/layer-dotted-names.html", "layer-dotted-names");
		// A dotted block name is read.
		assertX(texts, "P1", 10);
		// A dotted statement fixes s (and s.t) before u, and does not take the next rule with it.
		assertX(texts, "P2", 30);
		// The parent's own rules beat its sublayers.
		assertX(texts, "P3", 40);
		// "q.r" inside "o" is the layer o.q.r, which comes before z.
		assertX(texts, "P4", 65);
		// v.k, declared after w, sits with v, before w.
		assertX(texts, "P5", 90);
		// Unlayered rules beat every layer.
		assertX(texts, "P6", 110);
		// The outer layer survives the dotted layer inside it.
		assertX(texts, "P7", 130);
		assertX(texts, "P8", 140);
		// !important reverses the order: the sublayer beats its parent's own rules.
		assertX(texts, "P9", 160);
	}

	public void testRewrite() {
		assertEquals("@layer daisyui\\.l1\\.l2{a{b:c}} @layer a\\.b, c\\.d; .e{f:g}",
				AtRulePreludeRewriter.rewrite("@layer daisyui.l1.l2{a{b:c}} @layer a.b, c.d; .e{f:g}", s -> false));
		assertEquals(List.of("docusaurus", "theme-classic"),
				AtRulePreludeRewriter.layerNamePath("docusaurus\\.theme-classic"));
		assertEquals(List.of("a", "b"), AtRulePreludeRewriter.layerNamePath("\\61 \\.b"));
		// Class selectors, numbers and strings outside the prelude are left alone.
		final String untouched = ".a.b{width:1.5em;content:\"@layer x.y\"} @layer c{.d.e{f:g}}";
		assertSame(untouched, AtRulePreludeRewriter.rewrite(untouched, s -> false));
	}

	private static void assertX(final Map<String, double[]> texts, final String text, final double xPx) {
		final double[] at = texts.get(text);
		assertNotNull(text + " is not drawn: " + texts.keySet(), at);
		assertEquals(text + " x", xPx * 0.75, at[0], 0.5);
	}
}
