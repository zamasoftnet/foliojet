package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * Verify the sweep oracle's <b>exclusion predicates</b> (introduced 2026-07-28).
 *
 * <p>
 * Exclusion predicates are <b>the specification boundary itself</b>: once a document returns
 * {@code true} here, any later breakage is counted as the layout author's responsibility.
 * Overly broad predicates <b>silently hide real defects among exclusions</b>,
 * making a sweep of 30 million documents falsely claim "zero failures".
 * {@code ARCHITECTURE.md} §5.13 requires mechanically computable predicates
 * because humans cannot notice these problems by inspecting cases individually.
 * </p>
 *
 * <p>
 * Therefore these tests give equal weight to verifying exclusions and
 * <b>verifying non-exclusions</b>.
 * </p>
 */
public class FuzzOraclePredicateTest extends TestCase {

	public FuzzOraclePredicateTest(String name) {
		super(name);
	}

	private static String doc(final String bodyStyle, final String body) {
		return "<html><head><style>body{margin:0;font:normal 6pt/1.2 serif" + bodyStyle + "}</style></head><body>"
				+ body + "</body></html>";
	}

	// ------------------------------------------------------------------
	// hasOrthogonalFlow: whether two axis orientations occur.
	// ------------------------------------------------------------------

	/** Shape of seed 194970: horizontal writing inside vertical writing. */
	public void testVerticalBodyWithHorizontalChildIsOrthogonal() {
		assertTrue(RandomDocumentFuzzTest.hasOrthogonalFlow(
				doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:horizontal-tb\">T0</div>")));
	}

	/** The default (no declaration) is horizontal, so a vertical child makes the flow orthogonal. */
	public void testDefaultHorizontalBodyWithVerticalChildIsOrthogonal() {
		assertTrue(RandomDocumentFuzzTest
				.hasOrthogonalFlow(doc("", "<div style=\"writing-mode:vertical-rl\">T0</div>")));
	}

	/** All-vertical writing is not orthogonal. */
	public void testAllVerticalIsNotOrthogonal() {
		assertFalse(RandomDocumentFuzzTest.hasOrthogonalFlow(
				doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:vertical-rl\">T0</div>")));
	}

	/**
	 * <b>Opposite directions on the same axis are not orthogonal.</b>{@code vertical-lr}
	 * inside {@code vertical-rl} is {@code SAME_AXIS_DIRECTION_CHANGE}.
	 * The line-axis length stays unchanged, so it cannot excuse overflow.
	 */
	public void testSameAxisDirectionChangeIsNotOrthogonal() {
		assertFalse(RandomDocumentFuzzTest.hasOrthogonalFlow(
				doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:vertical-lr\">T0</div>")));
	}

	/** A document without any {@code writing-mode} is not orthogonal. */
	public void testNoWritingModeIsNotOrthogonal() {
		assertFalse(RandomDocumentFuzzTest.hasOrthogonalFlow(doc("", "<div>T0</div>")));
	}

	// ------------------------------------------------------------------
	// pageAxisIsY: the paper's page axis.
	// ------------------------------------------------------------------

	public void testHorizontalBodyPaginatesAlongY() {
		assertTrue(RandomDocumentFuzzTest.pageAxisIsY(doc("", "<div>T0</div>")));
		assertTrue(RandomDocumentFuzzTest.pageAxisIsY(doc(";writing-mode:horizontal-tb", "<div>T0</div>")));
	}

	public void testVerticalBodyPaginatesAlongX() {
		assertFalse(RandomDocumentFuzzTest.pageAxisIsY(doc(";writing-mode:vertical-rl", "<div>T0</div>")));
		assertFalse(RandomDocumentFuzzTest.pageAxisIsY(doc(";writing-mode:vertical-lr", "<div>T0</div>")));
	}

	// ------------------------------------------------------------------
	// Exclusion conditions (use the same expressions as the oracle itself).
	// ------------------------------------------------------------------

	/** Exact copy of the condition in {@code assertNoUnexplainedOffPage}. */
	private static boolean excluded(final String html, final boolean worstIsY) {
		return worstIsY != RandomDocumentFuzzTest.pageAxisIsY(html) && RandomDocumentFuzzTest.hasOrthogonalFlow(html);
	}

	/**
	 * <b>The main point</b>: in a vertical-writing document, overflow along the <b>line axis (y)</b>
	 * is excluded, but overflow along the <b>page axis (x)</b> is <b>not excluded</b>.
	 *
	 * <p>
	 * Page-axis overflow can be fixed by pagination; failure to do so is an engine defect.
	 * Losing this distinction would excuse all overflow in documents containing orthogonal flow.
	 * </p>
	 */
	public void testOnlyLineAxisOverflowIsExcludedInVerticalDocument() {
		final String html = doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:horizontal-tb\">T0</div>");
		assertTrue("縦書き文書のy方向は行軸——除外されるべき", excluded(html, true));
		assertFalse("縦書き文書のx方向はページ軸——除外してはいけない", excluded(html, false));
	}

	/** Horizontal-writing documents swap the axes (x=line axis, y=page axis). */
	public void testOnlyLineAxisOverflowIsExcludedInHorizontalDocument() {
		final String html = doc("", "<div style=\"writing-mode:vertical-rl\">T0</div>");
		assertTrue("横書き文書のx方向は行軸——除外されるべき", excluded(html, false));
		assertFalse("横書き文書のy方向はページ軸——除外してはいけない", excluded(html, true));
	}

	/**
	 * <b>Without orthogonal flow, exclude neither axis.</b>
	 * Only documents containing orthogonal flow can use this exclusion.
	 */
	public void testWithoutOrthogonalFlowNothingIsExcluded() {
		final String html = doc(";writing-mode:vertical-rl", "<div>T0</div>");
		assertFalse(excluded(html, true));
		assertFalse(excluded(html, false));
	}

	// ------------------------------------------------------------------
	// hasUntypesettableOppositeProgression: exclude same-axis reverse progression only at untypesettable widths.
	// ------------------------------------------------------------------

	/** Minimal seed 36607. Chrome also places content outward from the paper's right edge. */
	public void testZeroWidthOppositeVerticalProgressionIsExcluded() {
		final String html = doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:0pt\">"
						+ "<div style=\"writing-mode:vertical-rl;width:24pt\">T7</div></div>");
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(html));
	}

	/** Original seed 36607. An 86 pt-wide child sits in a reversed 48 pt box. */
	public void testPositiveWidthOppositeProgressionWithWiderChildIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:48pt\"><div style=\"width:86pt\">T0</div></div>")));
	}

	/**
	 * Fit seed 12262395 (2026-10-08): the reversed box's width in em ({@code 8em}, 48 pt at this 6 pt font) and a
	 * 113 pt inline-block inside it. Chrome also puts every drawing off the paper.
	 */
	public void testEmWidthOppositeProgressionWithWiderInlineBlockIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-lr",
				"<div style=\"writing-mode:vertical-rl;width:8em;min-width:8em;max-width:90%;\"><div>"
						+ "<p><span style=\"display:inline-block;width:113pt;height:101pt\">T2</span></p></div></div>")));
	}

	/** The last width wins: {@code calc()} with a percentage cannot be read, so the earlier 48 pt bounds nothing. */
	public void testOppositeProgressionWidthOverriddenByCalcIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:48pt;width:calc(35% + 8em)\"><div style=\"width:86pt\">T0</div></div>")));
	}

	/** Minimal seed 82162. A 1 pt width cannot hold even one 10 pt character. */
	public void testTooNarrowOppositeVerticalProgressionIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(
				doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:vertical-lr;width:1pt\">T0</div>")));
	}

	/** Same-axis reversal alone is not excluded. If descendants fit the width, check as an ordinary type area. */
	public void testPositiveWidthOppositeVerticalProgressionIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:80pt\">T0</div>")));
	}

	/** Even zero width is not excluded when progression direction is unchanged. */
	public void testZeroWidthSameVerticalProgressionIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-rl;width:0pt\">T0</div>")));
	}

	/** Vertical writing uses width as its page axis, so height:0 on the other axis does not trigger exclusion. */
	public void testZeroHeightOppositeVerticalProgressionIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;height:0pt\">T0</div>")));
	}

	/** The existing separate predicate handles orthogonal flow; do not mix it with zero-width same-axis reversal. */
	public void testZeroWidthOrthogonalFlowIsNotOppositeProgression() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:horizontal-tb;width:0pt\">T0</div>")));
	}

	/** Do not mistakenly combine declarations on separate branches into one exclusion condition. */
	public void testZeroWidthAndOppositeProgressionInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"width:0pt\">T0</div><div style=\"writing-mode:vertical-lr;width:80pt\">T1</div>")));
	}

	// ------------------------------------------------------------------
	// hasUntypesettableOrthogonalFlow: exclude only when the axis change and narrow width belong to the same element.
	// ------------------------------------------------------------------

	/** Minimal seed 372387. Zero-width horizontal writing inside vertical writing cannot hold even one character. */
	public void testZeroWidthOrthogonalFlowIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:horizontal-tb;width:0pt\">T0</div>")));
	}

	/** Minimal seeds such as 266476. Orthogonal flow 4 pt wide with 10 pt characters is also untypesettable. */
	public void testTooNarrowOrthogonalFlowIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(
				doc("", "<div style=\"writing-mode:vertical-rl;width:4pt\">T0</div>")));
	}

	/** Check orthogonal flow at or above the layout lower bound normally. */
	public void testUsableWidthOrthogonalFlowIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(
				doc("", "<div style=\"writing-mode:vertical-rl;width:80pt\">T0</div>")));
	}

	/** Leave same-axis direction changes to their dedicated predicate. */
	public void testNarrowSameAxisFlowIsNotOrthogonalExclusion() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:0pt\">T0</div>")));
	}

	/** Do not combine narrow width and orthogonal writing declarations on separate branches. */
	public void testNarrowWidthAndOrthogonalFlowInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(doc("",
				"<div style=\"width:0pt\">T0</div><div style=\"writing-mode:vertical-rl;width:80pt\">T1</div>")));
	}

	/** Height alone on the other physical axis does not establish an untypesettable width. */
	public void testZeroHeightOrthogonalFlowIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(
				doc("", "<div style=\"writing-mode:vertical-rl;height:0pt\">T0</div>")));
	}

	// ------------------------------------------------------------------
	// hasOverwideFloat: exclude only left/right floats wider than their actual containing width.
	// ------------------------------------------------------------------

	/** Minimal seed 132786. A 126 pt right float sits in a 99 pt parent. */
	public void testFloatWiderThanExplicitParentIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"writing-mode:horizontal-tb;width:99pt\"><div style=\"float:right;width:126pt\">T0</div></div>")));
	}

	/** Minimal seed 143513. Three columns in 110 pt content width are about 25.3 pt each after gaps. */
	public void testFloatWiderThanComputedColumnIsExcluded() {
		final String html = "<?jp.cssj.property name=\"output.page-width\" value=\"120pt\"?>"
				+ "<html><head><style>@page{margin:5pt}body{font:normal 10pt/1.2 serif}</style></head><body>"
				+ "<div style=\"column-count:3;column-gap:17pt\"><div style=\"float:right;width:89pt\">T0</div></div>"
				+ "</body></html>";
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(html));
	}

	/** Seed 865035. A descendant of an auto-width float exceeds the containing width. */
	public void testAutoWidthFloatWithOverwideDescendantIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:48pt\"><div style=\"float:right\"><div style=\"width:55pt\">T0</div></div></div>")));
	}

	/** The same width relationship without a float ancestor does not trigger this specific exclusion. */
	public void testOverwideDescendantWithoutFloatIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(
				shrinkerDoc("<div style=\"width:48pt\"><div><div style=\"width:55pt\">T0</div></div></div>")));
	}

	/** Do not exclude an auto-width float whose descendants fit the containing width. */
	public void testAutoWidthFloatWithFittingDescendantIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:55pt\"><div style=\"float:right\"><div style=\"width:55pt\">T0</div></div></div>")));
	}

	/** A float matching its parent's width is an ordinary type area, so do not exclude it. */
	public void testFloatFittingExplicitParentIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(
				shrinkerDoc("<div style=\"width:30pt\"><div style=\"float:right;width:30pt\">T0</div></div>")));
	}

	/** Do not mistakenly relate widths on separate branches as parent and child. */
	public void testWideFloatAndNarrowBoxInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:20pt\">T0</div><div style=\"float:right;width:30pt\">T1</div>")));
	}

	/** {@code float:none} does not trigger this specific exclusion even when wider than its parent. */
	public void testNonFloatingWideBoxIsNotExcludedAsOverwideFloat() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:20pt\"><div style=\"float:none;width:30pt\">T0</div></div>")));
	}

	/** Do not exclude floats that fit the calculated column width. */
	public void testFloatFittingComputedColumnIsNotExcluded() {
		final String html = "<?jp.cssj.property name=\"output.page-width\" value=\"120pt\"?>"
				+ "<html><head><style>@page{margin:5pt}body{font:normal 10pt/1.2 serif}</style></head><body>"
				+ "<div style=\"column-count:3;column-gap:17pt\"><div style=\"float:right;width:25pt\">T0</div></div>"
				+ "</body></html>";
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(html));
	}

	/** Minimal seed 78906. A float without a specified width sits inside a zero-width ancestor. */
	public void testFloatInsideNarrowContainerIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(
				shrinkerDoc("<div style=\"width:0pt\"><div><div style=\"float:left\">T0</div></div></div>"),
				48));
	}

	/** Do not exclude when the narrow box and float are on separate branches. */
	public void testNarrowContainerAndFloatInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(
				shrinkerDoc("<div style=\"width:0pt\">T0</div><div style=\"float:left\">T1</div>"), 48));
	}

	/** Do not exclude a float inside an ancestor at or above the layout lower bound. */
	public void testFloatInsideUsableContainerIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(
				shrinkerDoc("<div style=\"width:48pt\"><div style=\"float:left\">T0</div></div>"), 48));
	}

	/** Do not exclude float:none even inside a narrow ancestor. */
	public void testFloatNoneInsideNarrowContainerIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(
				shrinkerDoc("<div style=\"width:0pt\"><div style=\"float:none\">T0</div></div>"), 48));
	}

	/** Even through detailed dumps, count this as its specific exclusion type rather than a failure. */
	public void testDetailedDumpClassifiesZeroWidthOppositeProgression() throws Exception {
		final String html = doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:0pt\"><div>T0</div></div>");
		final RandomDocumentFuzzTest.Generated generated = new RandomDocumentFuzzTest.Generated(html, List.of("T0"),
				Set.of(), 595, 842, 0, false, false);
		final File dump = writeDump("  x=595.80 y=0.50 Text[\"T0\" asc=3.00 desc=2.00] w=4.00 h=5.00\n");
		try {
			try {
				RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated, new File[] { dump }, dump);
				fail("幅0の同軸逆進行フローは専用除外になるべき");
			} catch (final AssertionError e) {
				assertEquals("(除外)同軸逆進行フローの組版不能幅", RandomDocumentFuzzTest.classify(e));
			}
		} finally {
			dump.delete();
		}
	}

	/** The all-drawing-off-paper path also specifically excludes untypesettable orthogonal-flow widths. */
	public void testDetailedDumpClassifiesUntypesettableOrthogonalFlow() throws Exception {
		final String html = doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:horizontal-tb;width:27pt\"><ol><li>T0</li></ol></div>");
		final RandomDocumentFuzzTest.Generated generated = new RandomDocumentFuzzTest.Generated(html, List.of("T0"),
				Set.of(), 300, 150, 27, false, false);
		final File dump = writeDump("  x=303.00 y=6.72 Text[\"T0\" asc=5.15 desc=2.05] w=6.67 h=7.20\n");
		try {
			try {
				RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated, new File[] { dump }, dump);
				fail("組版不能幅の直交フローは専用除外になるべき");
			} catch (final AssertionError e) {
				assertEquals("(除外)直交フローの組版不能幅", RandomDocumentFuzzTest.classify(e));
			}
		} finally {
			dump.delete();
		}
	}

	// ------------------------------------------------------------------
	// rectangleIntersectsPage: rectangle test for all drawing being off the paper.
	// ------------------------------------------------------------------

	public void testRectangleWhollyInsidePageIntersects() {
		assertTrue(RandomDocumentFuzzTest.rectangleIntersectsPage(10, 10, 5, 5, 60, 60));
	}

	/** A glyph intersecting the paper counts as visible drawing even if its origin is outside. */
	public void testOutsideOriginWithInkInsideIntersects() {
		assertTrue(RandomDocumentFuzzTest.rectangleIntersectsPage(-1, 10, 2, 5, 60, 60));
	}

	/** Merely touching the paper edge with no area does not count as visible. */
	public void testRectangleTouchingEdgeDoesNotIntersect() {
		assertFalse(RandomDocumentFuzzTest.rectangleIntersectsPage(60, 10, 2, 5, 60, 60));
		assertFalse(RandomDocumentFuzzTest.rectangleIntersectsPage(-2, 10, 2, 5, 60, 60));
	}

	/**
	 * A bounding rectangle intersecting the paper does not count as off-paper placement even with a distant origin.
	 */
	public void testWideDrawingReachingPageIsNotBeyondWholePage() {
		assertTrue(RandomDocumentFuzzTest.distanceBeyondWholePage(-70, 124, 60) <= 0);
	}

	/** Count as off-paper placement when even the nearer rectangle edge is at least one paper dimension away. */
	public void testDetachedDrawingIsBeyondWholePage() {
		assertEquals(10.0, RandomDocumentFuzzTest.distanceBeyondWholePage(-80, 10, 60));
		assertEquals(10.0, RandomDocumentFuzzTest.distanceBeyondWholePage(130, 10, 60));
	}

	/** Include only inputs without specified widths that use the UA default 20ex in the allowance. */
	public void testDefaultTextControlsContributeIntrinsicWidth() {
		assertEquals(124.0, RandomDocumentFuzzTest.defaultTextControlWidth("<input />"));
		assertEquals(124.0, RandomDocumentFuzzTest.defaultTextControlWidth("<textarea></textarea>"));
	}

	/** Omit small controls. Inputs with size use that size's actual dimensions, not the default 20ex. */
	public void testNonDefaultTextControlsDoNotContributeIntrinsicWidth() {
		assertEquals(0.0, RandomDocumentFuzzTest.defaultTextControlWidth("<input type=\"radio\" />"));
		assertEquals(40.0, RandomDocumentFuzzTest.defaultTextControlWidth("<input size=\"6\" />"));
	}

	/**
	 * Seed 4608881: an input with size=16 is 100 pt (16ex + 4 pt frame). It does not rotate in vertical writing,
	 * so it makes a line 100 pt thick on 60 pt-wide paper. Ignoring it misses an author-specified size
	 * (2026-09-17). Use the widest one.
	 */
	public void testSizedTextInputContributesItsOwnWidth() {
		assertEquals(100.0, RandomDocumentFuzzTest
				.defaultTextControlWidth("<input type=\"text\" value=\"x\" size=\"16\" /><input type=\"radio\" />"));
		assertEquals(124.0, RandomDocumentFuzzTest
				.defaultTextControlWidth("<input type=\"text\" size=\"4\" /><textarea></textarea>"));
	}

	/** Minimal seed 473924. Match only actual nesting: flex ancestor→three columns→table. */
	public void testFlexMulticolTableIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasFlexMulticolTable(doc("",
				"<div style=\"display:flex\"><div style=\"column-count:3\"><table><tr><td>T0</td></tr></table></div></div>")));
	}

	/** A multi-column table outside flex does not trigger this specific exclusion. */
	public void testMulticolTableOutsideFlexIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(
				doc("", "<div style=\"column-count:3\"><table><tr><td>T0</td></tr></table></div>")));
	}

	/** Do not combine flex and a multi-column table on separate branches. */
	public void testFlexAndMulticolTableInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(doc("",
				"<div style=\"display:flex\">T0</div><div style=\"column-count:3\"><table><tr><td>T1</td></tr></table></div>")));
	}

	/** One column, no table, and grid each fail to trigger this specific exclusion. */
	public void testOtherIntrinsicContainersAreNotFlexMulticolTable() {
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(doc("",
				"<div style=\"display:flex\"><div style=\"column-count:1\"><table><tr><td>T0</td></tr></table></div></div>")));
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(
				doc("", "<div style=\"display:flex\"><div style=\"column-count:3\">T0</div></div>")));
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(doc("",
				"<div style=\"display:grid\"><div style=\"column-count:3\"><table><tr><td>T0</td></tr></table></div></div>")));
	}

	/** Find tokens on the paper, including parsing of detailed dumps. */
	public void testDetailedDumpFindsVisibleToken() throws Exception {
		final File dump = writeDump("  x=-1.00 y=10.00 Text[\"T0\" asc=3.00 desc=2.00] w=2.00 h=5.00\n");
		try {
			RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated(), new File[] { dump }, dump);
		} finally {
			dump.delete();
		}
	}

	/** Documents containing only images/forms and no text also count as visible drawing. */
	public void testDetailedDumpFindsVisibleNonTextDrawing() throws Exception {
		final File dump = writeDump("  x=10.00 y=10.00 AbsoluteRectFrame[w=20.00 h=20.00]\n");
		try {
			RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated(), new File[] { dump }, dump);
		} finally {
			dump.delete();
		}
	}

	/** The new invariant actually fails when every token rectangle is off the paper. */
	public void testDetailedDumpRejectsAllTokensOffPage() throws Exception {
		final File dump = writeDump("  x=60.00 y=10.00 Text[\"T0\" asc=3.00 desc=2.00] w=2.00 h=5.00\n");
		try {
			try {
				RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated(), new File[] { dump }, dump);
				fail("全描画が紙面外なら失敗しなければならない");
			} catch (final AssertionError e) {
				assertTrue(String.valueOf(e.getMessage()).contains("全描画が紙面外"));
			}
		} finally {
			dump.delete();
		}
	}

	// ------------------------------------------------------------------
	// FuzzShrinker.analyze: subtrees allowed to reorder reading order.
	// ------------------------------------------------------------------

	public void testShrinkerTreatsAbsolutePositionedDescendantsAsReorderable() {
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(shrinkerDoc(
				"<div style=\"position:absolute;top:0;left:0\"><p>T0</p></div><p>T1</p>"));
		assertNotNull(generated);
		assertEquals(Set.of("T0"), generated.reorderable());
	}

	public void testShrinkerDoesNotTreatFloatNoneDescendantsAsReorderable() {
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker
				.analyze(shrinkerDoc("<div style=\"float:none\"><p>T0</p></div>"));
		assertNotNull(generated);
		assertTrue(generated.reorderable().isEmpty());
	}

	// findUnfittableContent: content that physically cannot fit in the type area (user decision on 2026-09-17).
	// shrinkerDoc has a 50x50 pt content area and 6 pt text (layout lower bound 48 pt).

	private static final String LONG_RUBY = "<p><ruby class=\"fuzz-long-ruby\">"
			+ "T10 T11 T12 T13 T14 T15 T16 T17 T18 T19 T20 T21<rt>T22</rt></ruby></p>";

	/** Unsplittable ruby with 12 words (lower bound 131.7 pt) cannot fit a 50 pt line. */
	public void testLongRubyBeyondLineIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_RUBY,
				RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(LONG_RUBY)));
	}

	/** Ruby in vertical writing has a vertical line axis (y), so compare with paper height. Shape of seed 2031709. */
	public void testLongRubyInVerticalFlowUsesPageHeight() {
		final String vertical = "<div style=\"writing-mode:vertical-rl\">" + LONG_RUBY + "</div>";
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_RUBY,
				RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(vertical)));
		// Fits with enough height (width remains narrow).
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(vertical)
				.replace("name=\"output.page-height\" value=\"60pt\"", "name=\"output.page-height\" value=\"842pt\"")));
	}

	/** Do not exclude ruby short enough to fit the line (A4). */
	public void testLongRubyThatFitsIsNotUnfittable() {
		final String html = shrinkerDoc(LONG_RUBY).replace("value=\"60pt\"", "value=\"595pt\"");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(html));
	}

	/** Do not count short ruby (without a class). */
	public void testShortRubyIsNotUnfittable() {
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<p><ruby>T0<rt>T1</rt></ruby></p>")));
	}

	/** min-width:8em (48 pt) fits 50 pt, but not a table (−4 pt) cell (−2 pt). Shape of seed 5210679. */
	public void testMinWidthBeyondCellIsUnfittable() {
		final String box = "<div style=\"width:calc(35% + 8em);min-width:8em;max-width:90%;\">T0</div>";
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(box)));
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_MIN_WIDTH, RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<table><tr><td>" + box + "</td></tr></table>")));
	}

	/** min-width wider than an explicitly sized box. Do not associate it with narrow boxes on other branches. */
	public void testMinWidthIsComparedWithItsOwnAncestors() {
		final String box = "<div style=\"min-width:8em\">T0</div>";
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_MIN_WIDTH, RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"width:30pt\">" + box + "</div>")));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"width:30pt\">T1</div>" + box)));
	}

	/**
	 * A box with min-width can use that minimum even with a narrower explicit width (a child with the same minimum
	 * fits).
	 */
	public void testMinWidthWidensItsOwnContent() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(
				"<div style=\"width:30pt;min-width:8em\"><div style=\"min-width:8em\">T0</div></div>")));
	}

	// --- Do not classify fitting documents as unfittable (counterexamples from codex review on 2026-09-17). ---

	private static String wide(final String body) {
		return shrinkerDoc(body).replace("name=\"output.page-width\" value=\"60pt\"",
				"name=\"output.page-width\" value=\"210pt\"");
	}

	/**
	 * With 200 pt content width, 12-word ruby (lower bound 131.7 pt) fits. Premise for the following counterexamples.
	 */
	public void testLongRubyFitsTwoHundredPoints() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(wide(LONG_RUBY)));
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_RUBY, RandomDocumentFuzzTest
				.findUnfittableContent(wide("<div style=\"width:30pt\">" + LONG_RUBY + "</div>")));
	}

	/**
	 * Last width declaration wins. A final percentage is statically unknown, so do not narrow using an earlier pt
	 * value.
	 */
	public void testLaterPercentageWidthOverridesEarlierLength() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"width:30pt;width:80%;min-width:8em;max-width:90%\">" + LONG_RUBY + "</div>")));
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_RUBY, RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"width:80%;width:30pt\">" + LONG_RUBY + "</div>")));
	}

	/** width has no effect on non-replaced inline elements, so it does not cap their content width. */
	public void testInlineWidthDoesNotBoundItsContent() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"display:inline;width:30pt\">" + LONG_RUBY + "</div>")));
	}

	/** A flex item's width is only its basis before growing. Do not assume columns inside the item are narrow. */
	public void testFlexItemWidthDoesNotBoundItsContent() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(wide("<div style=\"display:flex\">"
				+ "<div style=\"flex:1 1 auto;width:8em\"><div style=\"column-count:2\">T0</div></div></div>")));
		// The flex container's own width is definite.
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_COLUMN, RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"display:flex;width:60pt\"><div><div style=\"column-count:2\">T0</div></div></div>")));
	}

	/** Tables and cells expand to fit content, so do not narrow by their width. */
	public void testTableWidthDoesNotBoundItsContent() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				wide("<table style=\"width:30pt\"><tr><td style=\"width:30pt\">" + LONG_RUBY + "</td></tr></table>")));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"display:table;width:30pt\">" + LONG_RUBY + "</div>")));
	}

	/**
	 * Collapsed tables have no spacing; subtract only half of each border (1 pt total). A 48 pt box fits a 50 pt
	 * table's 49 pt cell.
	 */
	public void testCollapsedTableDoesNotLoseSpacing() {
		final String cell = "<table><tr><td><div style=\"width:8em;min-width:8em\">T0</div></td></tr></table>";
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_MIN_WIDTH,
				RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(cell)));
		final String collapsed = shrinkerDoc(cell).replace("<style>", "<style>table{border-collapse:collapse}");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(collapsed));
		// The same 48 pt box cannot fit in a table cell (47 pt) inside a 48 pt container (seed 2129171 shape).
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_MIN_WIDTH, RandomDocumentFuzzTest.findUnfittableContent(
				collapsed.replace("<table>", "<div style=\"width:8em\"><table>").replace("</table>", "</table></div>")));
	}

	/** Read float widths with the last declaration winning; use min-width if it takes precedence. */
	public void testNarrowFloatUsesTheEffectiveWidth() {
		assertFalse(RandomDocumentFuzzTest.hasNarrowFloat(
				shrinkerDoc("<div style=\"float:left;width:22pt;width:80%\">T0</div>"), 48));
		assertFalse(RandomDocumentFuzzTest.hasNarrowFloat(
				shrinkerDoc("<div style=\"float:left;width:22pt;min-width:8em\">T0</div>"), 48));
		assertTrue(RandomDocumentFuzzTest.hasNarrowFloat(
				shrinkerDoc("<div style=\"float:left;width:80%;width:22pt\">T0</div>"), 48));
	}

	/**
	 * Two columns in 50 pt with a 5 pt gap yield 22.5 pt, below the 48 pt lower bound. Exclude neither one column nor
	 * columns above the bound.
	 */
	public void testNarrowColumnIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_COLUMN, RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc("<div style=\"column-count:2;column-gap:5pt\">T0</div>")));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"column-count:1\">T0</div>")));
		final String wide = shrinkerDoc("<div style=\"column-count:2;column-gap:5pt\">T0</div>")
				.replace("value=\"60pt\"", "value=\"595pt\"");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(wide));
	}

	/** Multi-column layout in vertical writing divides the height. */
	public void testVerticalColumnsDivideHeight() {
		final String html = shrinkerDoc(
				"<div style=\"writing-mode:vertical-rl;column-count:2;column-gap:5pt\">T0</div>")
				.replace("name=\"output.page-width\" value=\"60pt\"", "name=\"output.page-width\" value=\"595pt\"");
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_COLUMN,
				RandomDocumentFuzzTest.findUnfittableContent(html));
		final String tall = shrinkerDoc(
				"<div style=\"writing-mode:vertical-rl;column-count:2;column-gap:5pt\">T0</div>")
				.replace("name=\"output.page-height\" value=\"60pt\"", "name=\"output.page-height\" value=\"842pt\"");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(tall));
	}

	/**
	 * Generator v2 inserts writing-mode between float and width. Do not miss cases due to declaration order (seeds
	 * 3767082/4709606).
	 */
	public void testNarrowFloatIsFoundRegardlessOfDeclarationOrder() {
		final String html = shrinkerDoc(
				"<div style=\"float:right;writing-mode:horizontal-tb;width:22pt;\"><ol><li>T0</li></ol></div>");
		assertTrue(RandomDocumentFuzzTest.hasNarrowFloat(html, 48));
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableFloat(html));
		assertFalse(RandomDocumentFuzzTest.hasNarrowFloat(shrinkerDoc(
				"<div style=\"float:right;writing-mode:horizontal-tb;width:48pt;\">T0</div>"), 48));
		assertFalse(RandomDocumentFuzzTest.hasNarrowFloat(shrinkerDoc(
				"<div style=\"float:none;width:22pt;\">T0</div><div style=\"float:left\">T1</div>"), 48));
	}

	/**
	 * Measure how much off-paper checking the exclusion predicates cover (only with -Dfoliojet.unfittableRate=count).
	 * Compare counts of documents excluded for overflow on either axis with the old predicates alone
	 * and after adding unfittable content.
	 */
	public void testUnfittableDetectorRate() {
		final int n = Integer.getInteger("foliojet.unfittableRate", 0).intValue();
		if (n == 0) {
			return;
		}
		final java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
		for (int seed = 0; seed < n; ++seed) {
			final RandomDocumentFuzzTest.Generated g = RandomDocumentFuzzTest.generate(5_250_000 + seed, true);
			final String html = g.html();
			final boolean anyAxis = g.beyondEngineControl()
					|| RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(html)
					|| RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(html)
					|| legacyUntypesettableFloat(html)
					|| RandomDocumentFuzzTest.hasFlexMulticolTable(html)
					|| RandomDocumentFuzzTest.orthogonalAxisChanges(html) >= 2;
			int before = 0, after = 0;
			final String unfittable = RandomDocumentFuzzTest.findUnfittableContent(html);
			counts.merge("reason:" + unfittable, 1, Integer::sum);
			for (final boolean failureIsY : new boolean[] { false, true }) {
				final boolean base = anyAxis || (failureIsY != RandomDocumentFuzzTest.pageAxisIsY(html)
						&& RandomDocumentFuzzTest.hasOrthogonalFlow(html));
				before += base ? 1 : 0;
				after += base || unfittable != null || RandomDocumentFuzzTest.hasUntypesettableFloat(html) ? 1 : 0;
			}
			counts.merge("before:excludedAxes=" + before, 1, Integer::sum);
			counts.merge("after:excludedAxes=" + after, 1, Integer::sum);
		}
		System.out.println("[unfittableRate] n=" + n + " " + counts);
	}

	/**
	 * Pre-2026-09-17 predicate for floats of untypesettable width (requires adjacent float/width). For
	 * activation-rate comparison.
	 */
	private static boolean legacyUntypesettableFloat(final String html) {
		final java.util.regex.Matcher fm = java.util.regex.Pattern.compile("font:normal (\\d+)pt").matcher(html);
		if (!fm.find()) {
			return false;
		}
		final double least = Double.parseDouble(fm.group(1)) * 8;
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("float:[a-z]+;(?:width|height):(\\d+)pt").matcher(html);
		while (m.find()) {
			if (Double.parseDouble(m.group(1)) < least) {
				return true;
			}
		}
		return RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(html, least)
				|| RandomDocumentFuzzTest.hasOverwideFloat(html);
	}

	// Minimum main-axis size of a nonwrapping flex line (seed 9321740, 2026-09-28).
	// Vertical writing, 150 pt line length: table (min-content≧108.3 pt), plus flex-shrink:0 items at 35% and calc(25% + 8pt).

	private static final String FLEX_TABLE = "<table><tbody>\n"
			+ "<tr><td>T0</td><td rowspan=\"2\">T1</td><td rowspan=\"2\">T2</td><td>T3</td></tr>\n"
			+ "<tr><td>T4</td><td>T5</td><td colspan=\"3\">T6</td><td>T7</td></tr>\n"
			+ "<tr><td>T8</td><td rowspan=\"1\">T9</td><td rowspan=\"2\">T10</td><td>T11</td></tr>\n"
			+ "<tr><td>T12</td><td colspan=\"3\">T13</td><td rowspan=\"1\">T14</td><td>T15</td></tr>\n"
			+ "</tbody></table>\n";

	private static String flexLineDoc(final String pageHeight, final String containerStyle) {
		return "<?jp.cssj.property name=\"output.page-width\" value=\"300pt\"?>\n"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"" + pageHeight + "\"?>\n"
				+ "<html><head><style>\n@page{margin:0pt}\n"
				+ "body{margin:0;font:normal 13pt/1.2 serif;writing-mode:vertical-lr}\n"
				+ "p,div,td{margin:0;padding:0}\ntable{border-collapse:separate;table-layout:fixed}\n"
				+ "td{border:1pt solid black}\n</style></head><body data-fuzz-generator=\"2\">\n"
				+ "<div style=\"display:flex;position:relative;" + containerStyle + "gap:0pt;\">\n" + FLEX_TABLE
				+ "<div data-fuzz-role=\"layout-item\" style=\"flex:1 0 35%;width:min-content;min-width:8em;max-width:90%;\">\n"
				+ "<p>T16</p>\n</div>\n"
				+ "<div data-fuzz-role=\"layout-item\" style=\"flex:2 0 calc(25% + 8pt);\">\n"
				+ "<p><ruby>T24<rt>T25</rt></ruby></p>\n</div>\n</div>\n</body></html>";
	}

	/** Seed 9321740 shape: the ruby item starts at table 108.3 + 35%×150 = 160.8 pt, beyond the paper (150 pt). */
	public void testNowrapFlexLineBeyondLineIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_FLEX_LINE, RandomDocumentFuzzTest
				.findUnfittableContent(flexLineDoc("150pt", "flex-direction:row;flex-wrap:nowrap;")));
	}

	/** At a 400 pt line length, the ruby item can start at 108.3 + 140 = 248.3 pt. */
	public void testNowrapFlexLineWithinLongLineIsNotUnfittable() {
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(flexLineDoc("400pt", "flex-direction:row;flex-wrap:nowrap;")));
	}

	/** Wrapping flex and column-direction flex do not sum to the line length. */
	public void testWrappingOrColumnFlexIsNotUnfittable() {
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(flexLineDoc("150pt", "flex-direction:row;flex-wrap:wrap;")));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(flexLineDoc("150pt", "flex-direction:column;flex-wrap:nowrap;")));
	}

	/**
	 * Table lower bound comes from row 2 (six cells including T1/T2 rowspans): 16.3×6 + 1.5×7 = 108.3 pt.
	 * Omit row 4 because T13 (colspan 3) overlaps T10 (rowspan 2).
	 */
	public void testTableMinContentLowerBound() {
		final String html = flexLineDoc("150pt", "");
		final int from = html.indexOf("<table>") + "<table>".length();
		assertEquals(108.3, RandomDocumentFuzzTest.tableMinContentLowerBound(html, from, 13, false), 1e-9);
		// Collapsed-border tables count neither borders nor spacing: six single-digit cells in row 2, 14.3×6 = 85.8 pt.
		assertEquals(85.8, RandomDocumentFuzzTest.tableMinContentLowerBound(html, from, 13, true), 1e-9);
	}

	/** Content width 50 pt, margin 5 pt: item 4 starts at 24 pt×3+2 pt×3 = 78 pt, beyond the paper edge (55 pt). */
	private static final String FLEX_OVER = "<div style=\"display:flex;flex-direction:row;flex-wrap:nowrap;gap:2pt\">"
			+ "<div style=\"flex:0 0 24pt\"><p>T1</p></div><div style=\"flex:0 0 24pt\"><p>T2</p></div>"
			+ "<div style=\"flex:0 0 24pt\"><p>T3</p></div><div style=\"flex:0 0 24pt\"><p>T4</p></div></div>";

	public void testTopLevelNowrapFlexBeyondPageIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_FLEX_LINE,
				RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(FLEX_OVER)));
	}

	/**
	 * Even if summed box sizes exceed the paper edge, do not exclude when the last text-bearing item starts
	 * on the paper (second counterexample from codex review on 2026-09-28). Item 3 starts at 24+2×2 = 28 pt,
	 * so its text fits. The sum 24+48+4 = 76 pt includes empty space.
	 */
	public void testTrailingEmptyBoxOverflowIsNotUnfittable() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(
				"<div style=\"display:flex;flex-direction:row;flex-wrap:nowrap;gap:2pt;\"><p>T0</p>"
						+ "<div style=\"flex:0 0 24pt;\"><p>T1</p></div><div style=\"flex:0 0 8em;\"><p>T2</p></div></div>")));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(FLEX_OVER.replace("<div style=\"flex:0 0 24pt\"><p>T4</p></div>", ""))));
	}

	/**
	 * Counterexamples from codex review on 2026-09-28. Inside tables, containers expand to fit content;
	 * absolute/relative offsets can return text to the paper; display:none, floats, or container dimensions
	 * make line length indeterminate. Exclude none of these.
	 */
	public void testFlexLineCounterexamplesAreNotUnfittable() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc("<table><tbody><tr><td>T0" + FLEX_OVER + "</td></tr></tbody></table>")));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(
				FLEX_OVER + "<div style=\"position:absolute;top:20pt;left:0pt\">X</div>")));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc(FLEX_OVER.replace("gap:2pt", "gap:2pt;position:relative;left:-10pt"))));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<p style=\"display:none\">T9</p>" + FLEX_OVER)));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"float:left\">T9</div>" + FLEX_OVER)));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc(FLEX_OVER.replace("gap:2pt", "gap:2pt;width:200pt"))));
	}

	/**
	 * Third counterexample: reverse flex inside the evidence item can overflow its text before the item's
	 * start, onto the paper. In a reverse container, long text in an item starting off the paper can overflow
	 * toward the paper. Exclude neither case.
	 */
	public void testFlexLineEvidenceItemMustBePlain() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(FLEX_OVER.replace("<p>T4</p>",
				"<div style=\"display:flex;width:20pt;flex-direction:row-reverse;flex-wrap:nowrap;gap:0pt\">"
						+ "<p><span style=\"display:inline-block;width:150pt;height:10pt\">T4</span></p></div>"))));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc(FLEX_OVER.replace("flex-direction:row;", "flex-direction:row-reverse;"))));
		// An item with only size declarations is valid evidence.
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_FLEX_LINE, RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc(FLEX_OVER.replace("flex:0 0 24pt\"><p>T4", "flex:0 0 24pt;min-width:8em;max-width:90%\"><p>T4"))));
	}

	// Minimum table width exceeding the paper's line length (seed 10376223, 2026-09-29). A 9-column table on 120 pt paper
	// (100 pt content width) placed T20 at x=269.48 (262.78 in Chrome).

	/**
	 * The seed document itself. Columns 0–4 each have a 21.2 pt lower bound (T25–T27, T21, nested-table words),
	 * so T4's column starts at 1.5 + 22.7×5 = 115 pt, beyond the paper edge (110 pt).
	 */
	public void testSeedTableBeyondPageIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(RandomDocumentFuzzTest.generate(10_376_223, true).html()));
	}

	/**
	 * 2026-10-07, fit seed 11606508 (v2 seed 2028400733): a table cell on 60 pt paper contains a nested table
	 * with overlapping cells and {@code <ul style="list-style-…">}. List marker declarations do not narrow
	 * the width, and sums from nonoverlapping rows still bound an overlapping table. T5's column thus starts
	 * off the paper (Copper x=135, Chrome x=133.5). Previously both contributed 0, falsely reporting a defect.
	 */
	public void testSeedTableWithListAndOverlappingNestedTableIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(RandomDocumentFuzzTest.generate(2_028_400_733, true, false, false).html()));
	}

	/**
	 * Content width 50 pt, margin 5 pt; word lower bound T1x=9.6 pt, spacing 1.5 pt: column j starts at
	 * 1.5+11.1j. Colspan reduces row 1's sum; row 2 determines the columns. T15's column (j=5)
	 * starts at 57 pt, beyond the paper edge (55 pt).
	 */
	private static final String TABLE_OVER = "<table><tbody>\n<tr><td colspan=\"3\">T1</td><td colspan=\"3\">T2</td></tr>\n"
			+ "<tr><td>T10</td><td>T11</td><td>T12</td><td>T13</td><td>T14</td><td>T15</td></tr>\n</tbody></table>\n";

	public void testTopLevelTableBeyondPageIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN,
				RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(TABLE_OVER)));
		// Column j's start depends only on earlier columns: the first row's colspan cell (j=3) starts at 1.5+33.3=34.8 pt, on paper.
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc(TABLE_OVER.replace("<td>T15</td>", ""))));
	}

	/**
	 * Do not exclude documents with indeterminate table placement or cells that cannot serve as evidence:
	 * table attributes, a table not directly under body, floats/display:none/position offsets in the document,
	 * overlapping cells, or an evidence cell with style/dir or not starting with a word.
	 */
	public void testTableColumnCounterexamplesAreNotUnfittable() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc(TABLE_OVER.replace("<table>", "<table style=\"margin-left:-60pt\">"))));
		// Evaluate tables inside divs with only frames/margins (testPlainWrapper); skip divs that can change writing direction.
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc("<div dir=\"rtl\">" + TABLE_OVER + "</div>")));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"float:left\">T9</div>" + TABLE_OVER)));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<p style=\"display:none\">T9</p>" + TABLE_OVER)));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc(TABLE_OVER + "<div style=\"position:relative;left:-10pt\">T9</div>")));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc(TABLE_OVER.replace("<td>T15</td>", "<td>T15<p style=\"margin-left:-60pt\">T16</p></td>"))));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc(TABLE_OVER.replace("<td>T15</td>", "<td dir=\"rtl\">T15</td>"))));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc(TABLE_OVER.replace("<td>T15</td>", "<td><b>T15</b></td>"))));
	}

	/**
	 * Counterexample from codex review on 2026-09-29: a 4×4 table with 7 pt text on 120 pt paper (zero margin).
	 * T7 starts in column 10, but column 7 can have zero width because only T3 (colspan 3) covers it.
	 * The 131.0 pt lower-bound sum is within 120 pt×1.1=132 pt; Copper shrinks the columns to fit the paper.
	 */
	public void testSlightlyOverwideTableIsNotUnfittable() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				"<?jp.cssj.property name=\"output.page-width\" value=\"120pt\"?>\n"
						+ "<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n"
						+ "<html><head><style>\n@page{margin:0pt}\n"
						+ "body{margin:0;font:normal 7pt/1.2 serif;writing-mode:horizontal-tb}\n"
						+ "p,div,td{margin:0;padding:0}\ntable{border-collapse:separate;table-layout:auto}\n"
						+ "td{border:1pt solid black}\n</style></head><body data-fuzz-generator=\"2\">\n<table><tbody>\n"
						+ "<tr><td colspan=\"3\" rowspan=\"2\">T0</td><td rowspan=\"2\">T1</td><td>T2</td>"
						+ "<td colspan=\"3\" rowspan=\"2\">T3</td></tr>\n"
						+ "<tr><td rowspan=\"3\">T4</td><td>T5</td><td>T6</td><td>T7</td></tr>\n"
						+ "<tr><td rowspan=\"2\">T8</td><td>T9</td><td rowspan=\"2\">T10</td><td>T11</td></tr>\n"
						+ "<tr><td>T12</td><td>T13</td><td>T14</td><td>T15</td></tr>\n</tbody></table>\n</body></html>"));
	}

	/**
	 * Second counterexample from codex review on 2026-09-29: T7 (rowspan 3) at row 2's right edge was not
	 * carried into row 3 onward by Copper at the time, because there was no rowspan just before the end
	 * of the short row 3 (T6's second column). Thus row 4's T15 fit on paper in column 6. Since c803652d
	 * that day (fill vacant slots with anonymous cells), Copper follows the grid and puts T15 in column 7
	 * (x=144.32, Chrome 140.16), off the paper. On 2026-10-07, stop simulating Copper's placement and classify
	 * this as unfittable (the grid gives a 142.5 pt lower bound >132 pt, and T15 starts at 123 pt).
	 */
	public void testTableWithGapBeforeRowspanIsUnfittable() {
		final String html = "<?jp.cssj.property name=\"output.page-width\" value=\"120pt\"?>\n"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n"
				+ "<html><head><style>\n@page{margin:0pt}\n"
				+ "body{margin:0;font:normal 10pt/1.2 serif;writing-mode:horizontal-tb}\n"
				+ "p,div,td{margin:0;padding:0}\ntable{border-collapse:separate;table-layout:auto}\n"
				+ "td{border:1pt solid black}\n</style></head><body data-fuzz-generator=\"2\">\n<table><tbody>\n"
				+ "<tr><td>T0</td><td>T1</td><td>T2</td><td>T3</td></tr>\n"
				+ "<tr><td>T4</td><td colspan=\"3\">T5</td><td colspan=\"2\">T6</td><td rowspan=\"3\">T7</td></tr>\n"
				+ "<tr><td>T8</td><td>T9</td><td colspan=\"2\" rowspan=\"2\">T10</td><td rowspan=\"2\">T11</td></tr>\n"
				+ "<tr><td>T12</td><td>T13</td><td>T14</td><td>T15</td></tr>\n</tbody></table>\n</body></html>";
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest.findUnfittableContent(html));
		final int from = html.indexOf("<table>") + "<table>".length();
		assertTrue(RandomDocumentFuzzTest.tableMinContentLowerBound(html, from, 10, false) > 132);
	}

	/**
	 * Even with overlapping table cells (row 2's T10 colspan 2 covers row 1's T1 rowspan 2 slot), later cells
	 * occupy their grid columns (Copper T15 x=124.24, Chrome 123.82; measured on 2026-10-07).
	 * Previously, overlapping tables were not evaluated.
	 */
	public void testTableWithOverlappingCellsIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc(TABLE_OVER.replace("<td colspan=\"3\">T1</td>", "<td>T0</td><td rowspan=\"2\">T1</td><td>T3</td>")
						.replace("<td>T10</td>", "<td colspan=\"2\">T10</td>"))));
	}

	// 2026-10-07, fit 11,750,000 onward stopped (BUILD 9770c839). The generator v2 seed is the document title's number.

	/**
	 * fit seed 11766015 (v2 791230356): the last column's cell (T11) on 60 pt paper contains a nested table.
	 * Every outer cell starts on paper, but nested T20 appears at x=124.66 (Chrome 124.18, or 119.18 minus
	 * margin). Treat nested-table cells as evidence too. Evaluation previously stopped on a supposed mismatch
	 * between the grid and the simulated Copper placement, although Copper also followed the grid.
	 */
	public void testSeedNestedTableCellBeyondPageIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(RandomDocumentFuzzTest.generate(791_230_356, true, false, false).html()));
	}

	/**
	 * fit seed 11866613 (v2 1797278838): a table directly inside {@code float:right}, in a div with only
	 * a frame and margins. Only a colspan 3 cell contains a nested table; count colspan cells in column
	 * lower bounds too. Copper aligns the right float, wider than the paper, to the content start, putting
	 * T20 at x=125.99; Chrome aligns it to the end, putting T0 at x=-81.94 (both off the paper).
	 */
	public void testSeedTableInRightFloatIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(RandomDocumentFuzzTest.generate(1_797_278_838, true, false, false).html()));
	}

	/** A right float requires evidence cells on both start/end sides; a left float needs only the start side. */
	public void testTableInFloatNeedsEvidenceOnTheSideItCanOverflow() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"float:right\">" + TABLE_OVER + "</div>")));
		// Only T10 ends before the paper's start when end-aligned. Without T10 as evidence, do not classify the right float.
		final String noStartEvidence = TABLE_OVER.replace("<td>T10</td>", "<td><b>T10</b></td>");
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"float:right\">" + noStartEvidence + "</div>")));
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"float:left\">" + noStartEvidence + "</div>")));
		// Do not evaluate floats with width/margins or documents containing other floats.
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"float:right;width:20pt\">" + TABLE_OVER + "</div>")));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(
				"<div style=\"float:left\">T9</div><div style=\"float:right\">" + TABLE_OVER + "</div>")));
		assertEquals('R', RandomDocumentFuzzTest.floatWrapperSide("div", " style=\"position:static;float:right;\""));
		assertEquals(RandomDocumentFuzzTest.NOT_FLOATED, RandomDocumentFuzzTest.floatWrapperSide("div",
				" style=\"float:left;margin-left:-30pt\""));
	}

	/**
	 * fit seed 11898581 (v2 249173394): a table in a div with
	 * {@code float:none;width:8em;min-width:8em;max-width:90%} on vertical-writing paper (line length 60 pt).
	 * Width properties in vertical writing size the block axis and do not change line length.
	 * T34 is at y=144.16 (Chrome 137.93).
	 */
	public void testSeedTableInVerticalBlockSizedWrapperIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(RandomDocumentFuzzTest.generate(249_173_394, true, false, false).html()));
		final String sized = " style=\"float:none;width:8em;min-width:8em;max-width:90%;\"";
		assertTrue(RandomDocumentFuzzTest.isPlainWrapper("div", sized, true));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", sized, false));
		assertTrue(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"height:20pt\"", false));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"height:20pt\"", true));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"float:left\"", true));
	}

	/**
	 * Do not exclude fit seed 11942560 (v2 647052772): a {@code vertical-lr} box on {@code vertical-rl}
	 * paper contains a {@code width:8em} (48 pt) inline-block with a {@code width:58pt} box inside,
	 * followed by a ruby paragraph. Copper aligns the inline-block baseline to its first line, overflowing
	 * to the paper's right (x=601.8 onward, paper 595). Chrome follows CSS 2.1 §10.8.1, aligns to the last line,
	 * and fits it on paper (x=536–594). This is a Copper difference, not author overflow (triage §22).
	 */
	public void testSeedInlineBlockBaselineInReversedRegionIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(
				RandomDocumentFuzzTest.generate(647_052_772, true, false, false).html()));
	}

	/**
	 * Only when descendants' lower-bound widths exceed the reversed element's explicit width. Omit widths max-width
	 * could reduce.
	 */
	public void testReversedElementWidthAgainstDescendantLowerBound() {
		final String head = ";writing-mode:vertical-rl";
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(head,
				"<div style=\"writing-mode:vertical-lr;width:48pt\"><div style=\"width:58pt\">T0</div></div>")));
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(head,
				"<div style=\"writing-mode:vertical-lr;width:48pt\"><div style=\"min-width:10em\">T0</div></div>")));
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(head,
				"<div style=\"writing-mode:vertical-lr;width:48pt\"><div style=\"width:58pt;max-width:90%\">T0</div></div>")));
		// Without a width on the reversed element, mismatched inner boxes are insufficient (the area may not lie at the paper edge).
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(head,
				"<div style=\"writing-mode:vertical-lr\"><div style=\"display:inline-block;width:8em\">"
						+ "<div style=\"width:58pt\">T0</div></div></div>")));
		// Boxes whose width declaration has no effect (non-replaced inline), and flex items that can grow.
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(head,
				"<div style=\"writing-mode:vertical-lr;width:48pt\"><span style=\"width:58pt\">T0</span></div>")));
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(head,
				"<div style=\"writing-mode:vertical-lr;width:48pt;display:flex\"><div style=\"width:58pt\">T0</div></div>")));
	}

	// 2026-10-08, fit 12,000,000 onward stopped (BUILD 80e89977).

	/**
	 * fit seeds 12002121 (v2 755060702) and 12248803 (v2 260686428): a 107 pt right float in a {@code horizontal-tb}
	 * box inside a {@code vertical-rl} box with {@code width:8em;min-width:8em;max-width:90%} (48 pt at 6 pt), and a
	 * 119 pt {@code vertical-rl} float inside a {@code vertical-lr} float with the same widths (64 pt at 8 pt).
	 * Every drawing lies off the paper in Copper (x=-59.0 and x=235.4) and in Chrome (x=-58.99 and x=236.74).
	 */
	public void testSeedFloatWiderThanEmSizedBoxIsExcluded() {
		assertTrue(RandomDocumentFuzzTest
				.hasOverwideFloat(RandomDocumentFuzzTest.generate(755_060_702, true, false, false).html()));
		assertTrue(RandomDocumentFuzzTest
				.hasOverwideFloat(RandomDocumentFuzzTest.generate(260_686_428, true, false, false).html()));
	}

	/** Widths are bounds: em at the body font size, min-width over width, and max-width that may narrow. */
	public void testOverwideFloatReadsWidthBounds() {
		final String sized = "<div style=\"width:8em;min-width:8em;max-width:90%\">";
		assertTrue(RandomDocumentFuzzTest
				.hasOverwideFloat(shrinkerDoc(sized + "<div style=\"float:right;width:49pt\">T0</div></div>")));
		assertFalse(RandomDocumentFuzzTest
				.hasOverwideFloat(shrinkerDoc(sized + "<div style=\"float:right;width:48pt\">T0</div></div>")));
		// A floated inline box is blockified, so its width applies.
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"display:inline;float:left;width:8em\"><div style=\"float:left;width:49pt\">T0</div></div>")));
		// min-width wins over a narrower width.
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:30pt;min-width:45pt\"><div style=\"float:right;width:40pt\">T0</div></div>")));
		// max-width may narrow the float below its width; min-width stays a lower bound.
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:30pt\"><div style=\"float:right;width:40pt;max-width:90%\">T0</div></div>")));
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:30pt\"><div style=\"float:right;width:40pt;min-width:6em;max-width:90%\">T0</div></div>")));
		// The last width wins: a percentage after a pt width is not an exact width.
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:20pt;width:80%\"><div style=\"float:right;width:40pt\">T0</div></div>")));
		// em inside a box whose font size changes is not the body font size.
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc("<div style=\"font-size:20pt\">" + sized
				+ "<div style=\"float:right;width:49pt\">T0</div></div></div>")));
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(
				shrinkerDoc("<small>" + sized + "<div style=\"float:right;width:49pt\">T0</div></div></small>")));
		// Widths of non-replaced inline boxes and flex items (which can grow) do not bound their descendants.
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(
				shrinkerDoc("<span style=\"width:20pt\"><span style=\"float:right;width:40pt\">T0</span></span>")));
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"display:flex\"><div style=\"width:20pt\"><div style=\"float:right;width:40pt\">T0</div></div></div>")));
	}

	/** Columns divide the width only in horizontal writing and only for a container that does not shrink to fit. */
	public void testOverwideFloatColumnsOnlyNarrowFillingHorizontalContainers() {
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(
				shrinkerDoc("<div style=\"column-count:2\"><div style=\"float:right;width:30pt\">T0</div></div>")));
		// Vertical columns stack along the height.
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"writing-mode:vertical-rl;column-count:2\"><div style=\"float:right;width:30pt\">T0</div></div>")));
		// An inline-block multicol container widens to its columns' minimum widths.
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"display:inline-block;column-count:2\"><div style=\"float:right;width:30pt\">T0</div></div>")));
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"display:inline-block;width:50pt;column-count:2\"><div style=\"float:right;width:30pt\">T0</div></div>")));
		// column-count applies only to block containers (codex review 2026-10-08): not to flex/grid containers or
		// non-replaced inline boxes, whose floats fit the 50 pt paper.
		final String float48 = "<div style=\"float:left;width:8em;min-width:8em;max-width:90%\">T0</div>";
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(
				shrinkerDoc("<div style=\"display:flex;column-count:2;column-gap:0pt\"><div>" + float48 + "</div></div>")));
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(
				shrinkerDoc("<div style=\"display:grid;column-count:2;column-gap:0pt\"><div>" + float48 + "</div></div>")));
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc("<span style=\"column-count:2;column-gap:0pt\">"
				+ "<span style=\"float:left;width:8em;min-width:8em;max-width:90%\">T0</span></span>")));
		// Second round: an orthogonal block's auto width is fit-content, so its columns widen to the float.
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(verticalShrinkerDoc("vertical-lr",
				"<div style=\"writing-mode:horizontal-tb\"><div style=\"column-count:2;column-gap:0pt\">" + float48
						+ "</div></div>")));
		// An inline box passes its container's width through.
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<span><div style=\"column-count:2;column-gap:0pt\">" + float48 + "</div></span>")));
	}

	/** Boxes that are not generated, or are positioned out of flow, have other containing blocks (codex review 2026-10-08). */
	public void testOverwideFloatIgnoresBoxesOutOfFlow() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"display:contents;width:8em\"><div style=\"float:right;width:49pt\">T0</div></div>")));
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:8em\"><div style=\"display:none\"><div style=\"float:left;min-width:10em\">T0</div></div></div><p>T1</p>")));
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc("<div style=\"position:relative;width:8em\">"
				+ "<div style=\"position:absolute;left:0pt\"><div style=\"float:left;width:49pt\">T0</div></div></div>")));
	}

	/**
	 * Each individual exclusion's sweep seed and profile still lead to its document, and no predicate excludes that
	 * document (otherwise the record is stale: fix the seed, or drop the record). Other documents do not match.
	 */
	public void testIndividualExclusionsStillMatchTheirSeeds() {
		for (final RandomDocumentFuzzTest.IndividualExclusion e : RandomDocumentFuzzTest.INDIVIDUAL_EXCLUSIONS) {
			final RandomDocumentFuzzTest.Generated doc = switch (e.profile()) {
			case "fit-v1" -> RandomDocumentFuzzTest.generateFit(e.seed(), true);
			case "standard" -> RandomDocumentFuzzTest.generate(e.seed(), true, false, false);
			default -> throw new AssertionError("unknown profile " + e.profile());
			};
			assertTrue(String.valueOf(e), doc.html().contains("<title>fuzz v2 " + e.document() + "</title>"));
			assertSame(e, RandomDocumentFuzzTest.individualExclusion(doc.html()));
			assertFalse(String.valueOf(e), RandomDocumentFuzzTest.offPageCheckExcused(doc));
		}
		assertNull(RandomDocumentFuzzTest.individualExclusion(RandomDocumentFuzzTest.generate(12_180_614, true, false, false).html()));
		assertNull(RandomDocumentFuzzTest.individualExclusion(shrinkerDoc("T0")));
	}

	/**
	 * fit seed 12137870 (v2 1658974826): a table on {@code vertical-rl} paper (line length 60 pt) inside
	 * {@code <div style="page-break-inside:avoid;margin:0pt">} and {@code <div style="display:block;writing-mode:vertical-lr;">}.
	 * T12 is at y=120.72 (Chrome lays the columns out up to y≈127 too).
	 */
	public void testSeedTableInSameAxisWrapperIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(RandomDocumentFuzzTest.generate(1_658_974_826, true, false, false).html()));
		assertTrue(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"page-break-inside:avoid;margin:0pt\"", true, true));
		assertTrue(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"display:block;writing-mode:vertical-lr;\"", true,
				true));
		// An orthogonal writing mode changes the line length; the three-argument form allows no writing mode.
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"writing-mode:horizontal-tb\"", false, true));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"writing-mode:vertical-rl\"", true, false));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"writing-mode:vertical-lr\"", true));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"display:inline-block\"", true, true));
	}

	/**
	 * fit seed 12072875 (v2 2124912302): on {@code vertical-rl} paper 60 pt wide, a
	 * {@code display:inline;float:left;writing-mode:horizontal-tb} float holds a table about 150 pt wide (a nested
	 * table in one cell). Copper puts its right edge at the content's right edge and T4 at x=-88.83; Chrome puts it
	 * left of the list before it (T4 x=-123).
	 */
	public void testSeedTableInOrthogonalFloatIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(RandomDocumentFuzzTest.generate(2_124_912_302, true, false, false).html()));
		final String floated = "<div style=\"float:right;writing-mode:horizontal-tb\">";
		// vertical-rl: the float is anchored at the right, so only end-side evidence counts (T10 ends before the paper).
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(verticalShrinkerDoc("vertical-rl", floated + TABLE_OVER + "</div>")));
		final String noEndEvidence = TABLE_OVER.replace("<td>T10</td>", "<td><b>T10</b></td>");
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(verticalShrinkerDoc("vertical-rl", floated + noEndEvidence + "</div>")));
		// vertical-lr: anchored at the left, so start-side evidence counts (T15 starts beyond the paper).
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(verticalShrinkerDoc("vertical-lr", floated + noEndEvidence + "</div>")));
		final String noStartEvidence = TABLE_OVER.replace("<td>T15</td>", "<td><b>T15</b></td>");
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(verticalShrinkerDoc("vertical-lr", floated + noStartEvidence + "</div>")));
		// An ancestor with the reversed block progression anchors the float on the other side.
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(verticalShrinkerDoc("vertical-rl",
				"<div style=\"writing-mode:vertical-lr\">" + floated + TABLE_OVER + "</div></div>")));
		// A negative table margin lets the table pass the float's edge (codex review 2026-10-08).
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(verticalShrinkerDoc("vertical-rl", floated + TABLE_OVER + "</div>")
				.replace("</style>", "table{margin-right:-40pt}</style>")));
	}

	/**
	 * Seed 10760020 (2026-09-29): a table in a div with only frame and margins
	 * ({@code margin:7pt;padding:2pt;border:1pt solid black}) has a nested table in column 4's cell.
	 * Its minimum width contributes to the cell lower bound, so T8/T28 columns start off the paper.
	 */
	public void testSeedTableInPlainWrapperWithNestedTableIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(RandomDocumentFuzzTest.generate(10_760_020, true).html()));
	}

	/** A permitted table wrapper is a div with only a style attribute declaring nonnegative frame and margins. */
	public void testPlainWrapper() {
		assertTrue(RandomDocumentFuzzTest.isPlainWrapper("div", ""));
		assertTrue(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"margin:7pt;padding:2pt;border:1pt solid black\""));
		assertTrue(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"margin-left:3pt;border-top-width:2pt;\""));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"margin-left:-20pt\""));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"margin:calc(10% - 30pt)\""));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"width:400pt\""));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " style=\"margin:2pt;direction:rtl\""));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " dir=\"rtl\""));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("div", " data-fuzz-role=\"cell-child\""));
		assertFalse(RandomDocumentFuzzTest.isPlainWrapper("p", ""));
		// Evaluate tables inside wrapper divs, but skip divs that could shift them toward the start.
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN, RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"margin:1pt;padding:1pt\"><div>" + TABLE_OVER + "</div></div>")));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"margin-left:-40pt\">" + TABLE_OVER + "</div>")));
	}

	/**
	 * Nested-table minimum width bounds the cell: even in a one-cell outer table, later inner columns lie off paper
	 * if too wide.
	 */
	public void testNestedTableWidensItsCell() {
		final String nested = "<table><tbody>\n<tr><td>T1<div><table><tbody>\n"
				+ "<tr><td>T10</td><td>T11</td><td>T12</td><td>T13</td><td>T14</td><td>T15</td></tr>\n"
				+ "</tbody></table>\n</div></td><td>T2</td></tr>\n</tbody></table>\n";
		// Inner table lower bound 1.5 + 11.1×6 = 68.1 pt → outer column 2 starts at 1.5 + 68.1 + 1.5 = 71.1 pt, beyond the paper edge (55 pt).
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_TABLE_COLUMN,
				RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(nested)));
		// If style appears inside (a container could narrow the width), do not count the inner table.
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc(nested.replace("<div>", "<div style=\"width:10pt\">"))));
	}

	/** The predicate's tolerance ratio must match production {@code AutoColumnWidths.MIN_OVERFLOW_TOLERANCE}. */
	public void testTableShrinkToleranceMatchesProduct() throws Exception {
		final java.lang.reflect.Field field = Class.forName("net.zamasoft.foliojet.layout.sizing.AutoColumnWidths")
				.getDeclaredField("MIN_OVERFLOW_TOLERANCE");
		field.setAccessible(true);
		assertEquals(field.getDouble(null), RandomDocumentFuzzTest.TABLE_SHRINK_TOLERANCE, 0);
	}

	/**
	 * Documents for codex soundness counterexamples (2026-10-07): paper, margins, body font size, writing direction,
	 * extra rules/body.
	 */
	private static String paperDoc(final int paper, final int margin, final int font, final String mode, final String css,
			final String body) {
		return "<?jp.cssj.property name=\"output.page-width\" value=\"" + paper + "pt\"?>\n"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"" + paper + "pt\"?>\n"
				+ "<html><head><style>\n@page{margin:" + margin + "pt}\nbody{margin:0;font:normal " + font
				+ "pt/1.2 serif;writing-mode:" + mode + "}\np,div,td{margin:0;padding:0}\n" + css
				+ "\n</style></head><body>\n" + body + "\n</body></html>";
	}

	/**
	 * codex counterexamples 1–3: reverse-area mismatch within margins, default inline/table tags, em based on
	 * inherited font size.
	 */
	public void testReversedRegionCodexCounterexamples() {
		final String within = "<div style=\"writing-mode:vertical-lr\"><div style=\"width:8em\"><div style=\"width:54pt\">T0</div>"
				+ "</div></div>";
		assertFalse(RandomDocumentFuzzTest
				.hasUntypesettableOppositeProgression(paperDoc(200, 10, 6, "vertical-rl", "", within)));
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(paperDoc(200, 0, 6, "vertical-rl", "",
				"<div style=\"writing-mode:vertical-lr\"><span style=\"width:8em\">T0<span style=\"width:10em\">T1</span>"
						+ "</span></div>")));
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(paperDoc(200, 0, 6, "vertical-rl", "",
				"<div style=\"writing-mode:vertical-lr\"><table style=\"width:8em\"><tbody><tr><td>"
						+ "<div style=\"width:10em\">T0</div></td></tr></tbody></table></div>")));
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(paperDoc(200, 0, 6, "vertical-rl", "",
				"<div style=\"writing-mode:vertical-lr;font-size:12pt\"><div style=\"width:8em\">"
						+ "<div style=\"width:58pt\">T0</div></div></div>")));
	}

	/** codex counterexamples 4–6: width in table rules (fixed layout), zero-width borders, row font size. */
	public void testTableColumnCodexCounterexamples() {
		final StringBuilder twelve = new StringBuilder("<table><tbody><tr>");
		for (int i = 10; i < 16; ++i) {
			twelve.append("<td colspan=\"2\">T").append(i).append("</td>");
		}
		twelve.append("</tr></tbody></table>");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(paperDoc(60, 5, 6, "horizontal-tb",
				"table{table-layout:fixed;width:100%;border-collapse:separate;border-spacing:0}\ntd{border:1pt solid black;padding:0}",
				twelve.toString())));
		final StringBuilder sixteen = new StringBuilder("<table><tbody><tr>");
		for (int i = 0; i < 8; ++i) {
			sixteen.append("<td colspan=\"2\">T").append(i).append("</td>");
		}
		sixteen.append("</tr></tbody></table>");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(paperDoc(60, 5, 6, "horizontal-tb",
				"table{border-collapse:separate;border-spacing:0}\ntd{border:1pt none;padding:0}", sixteen.toString())));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(paperDoc(110, 5, 12, "horizontal-tb",
				"table{border-collapse:separate;table-layout:auto}\ntd{border:1pt solid black;padding:0}",
				"<div style=\"float:right\"><table><tbody><tr style=\"font-size:6pt\"><td>T10</td><td>T11</td><td>T12</td>"
						+ "<td>T13</td><td>T14</td><td>T15</td></tr></tbody></table></div>")));
		// Second round: max-width in cell rules (caps minimum cell width), small inside cells (UA reduces font size).
		final StringBuilder seven = new StringBuilder("<div style=\"float:right\"><table><tbody><tr>");
		for (int i = 10; i < 17; ++i) {
			seven.append("<td colspan=\"2\">T").append(i).append("</td>");
		}
		seven.append("</tr></tbody></table></div>");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(paperDoc(70, 5, 6, "horizontal-tb",
				"table{border-collapse:separate;border-spacing:0}\ntd{border:1pt solid black;max-width:6pt}",
				seven.toString())));
		final StringBuilder small = new StringBuilder("<table><tbody><tr>");
		for (int i = 100; i < 108; ++i) {
			small.append("<td colspan=\"2\"><small>T").append(i).append("</small></td>");
		}
		small.append("<td colspan=\"2\">T0</td></tr></tbody></table>");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(paperDoc(100, 5, 6, "horizontal-tb",
				"table{border-collapse:separate;border-spacing:0}", small.toString())));
		assertTrue(RandomDocumentFuzzTest.plainTableRules(paperDoc(60, 5, 6, "horizontal-tb",
				"table{border-collapse:collapse;table-layout:fixed}\ntd{border:1pt solid black}", "")));
		assertFalse(RandomDocumentFuzzTest.plainTableRules(paperDoc(60, 5, 6, "horizontal-tb",
				"table{table-layout:fixed;width:100%}", "")));
	}

	private static String shrinkerDoc(final String body) {
		return "<?jp.cssj.property name=\"output.page-width\" value=\"60pt\"?>"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"60pt\"?>"
				+ "<html><head><style>@page{margin:5pt}body{margin:0;font:normal 6pt/1.2 serif}</style></head>"
				+ "<body>" + body + "</body></html>";
	}

	/** {@link #shrinkerDoc} on paper whose body has the writing mode {@code mode}. */
	private static String verticalShrinkerDoc(final String mode, final String body) {
		return shrinkerDoc(body).replace("font:normal 6pt/1.2 serif}",
				"font:normal 6pt/1.2 serif;writing-mode:" + mode + "}");
	}

	private static RandomDocumentFuzzTest.Generated generated() {
		return new RandomDocumentFuzzTest.Generated("<html><body>T0</body></html>", List.of("T0"), Set.of(), 60,
				60, 0, false, false);
	}

	private static File writeDump(final String line) throws Exception {
		final File dump = File.createTempFile("fuzz-visible-", ".txt");
		Files.writeString(dump.toPath(), line, StandardCharsets.UTF_8);
		return dump;
	}
}
