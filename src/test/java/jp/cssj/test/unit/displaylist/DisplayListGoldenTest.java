package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Golden comparison tests for display lists (Drawer dumps).
 * Detect regressions in layout geometry and paint order more strictly than image comparisons.
 *
 * <p>
 * Baselines reside under files/unittest/display-list-golden/.
 * To update them for an intentional layout change, delete the relevant directory
 * and run this test to regenerate it (the run that regenerates it is marked as failed).
 * </p>
 */
public class DisplayListGoldenTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * Target documents covering blocks, floats, tables, vertical writing, multi-column layout, and generated content.
	 */
	private static final String[] DOCUMENTS = { //
			"0125-footnote/footnote-bottom-nested-hosts.html", //
			// Fixed-width children erased their siblings' intrinsic sizes (2026-08-04).
			// Cover floats/cells/absolute positioning/flex/inline-block side by side.
			"0080-width/intrinsic-fixed-sibling.html", //
			// Negative width/height attributes (2026-08-05). attr() resolves only at computed-value time,
			// bypassing the nonnegative check in parseValue and collapsing tables to their min-content width.
			"0080-width/negative-attr-width.html", //
			// An img that failed to load ignored CSS width/height and
			// collapsed to 0x0 (2026-08-06). See the fixture's own comments for details.
			"0080-width/broken-image-declared-size.html", //
			// Relative positioning across page breaks (2026-08-06). When containers on finalized pages
			// escaped the sizing traversal, they were silently output with zero offsets.
			"0170-position/relative-offset-after-break.html", //
			// Check that width:100% on an absolutely positioned child resolves to the same width
			// for button and div parents (2026-08-07, found in yahoo.co.jp's search suggestions dropdown).
			"0170-position/absolute-width-in-button.html", //
			"0120-float/auto-width.html", //
			"0120-float/sliver-overflow-stays.html", //
			"0120-float/floats-only-block-first-child-move.html", //
			"0120-float/table-beside-start-float.html", //
			// Place an end-side float encountered mid-line on the same line (2026-08-08, kabutan).
			// If it fits, use the current line's top (variants A–E); otherwise,
			// use the next band as before (variant F).
			"0120-float/float-end-midline.html", //
			// A float immediately after a margin:auto table (2026-08-05). Asymmetric adjustments
			// of the line-axis cursor sent the float to x=-106.75 (outside the paper on the left).
			"0120-float/after-auto-margin-table.html", //
			"0120-float/nested-float-shrink.html", //
			"0120-float/collapse-float-measure.html", //
			"0120-float/float-in-moved-block.html", //
			// shape-outside: circle(50%) (css-shapes-1, 2026-08-29). Lines start along the circle's
			// chords and return to the left edge below the circle.
			"0120-float/shape-outside-circle.html", //
			"0460-segment-restyle/mid-paragraph.html", //
			"0460-segment-restyle/moved-blocks.html", //
			"0460-segment-restyle/text-tail-avoid.html", //
			"0460-segment-restyle/float-in-moved.html", //
			"0460-segment-restyle/float-split-in-chain.html", //
			"0460-segment-restyle/float-uncut-before-prefix.html", //
			"0460-segment-restyle/nested-break-in-replay.html", //
			"0460-segment-restyle/moved-table-caption.html", //
			"0390-writing-mode/vert-cell-specified-pagebreak.html", //
			"0390-writing-mode/vert-fixed-colgroup-spacing.html", //
			"0390-writing-mode/orthogonal-cell-fixed.html", //
			// CSS Writing Modes: verify that mixed/upright/sideways change run separation
			// for vertical/horizontal font sources and the logical inline advance.
			"0390-writing-mode/text-orientation.html", //
			// An orthogonal block's page-axis % uses its parent's line axis (locks in the 2026-08-10 fix).
			"0390-writing-mode/orthogonal-page-axis-percent.html", //
			// The caption box uses the table's border box (not its margin box).
			// Captions extended outward on tables with a left margin (2026-08-30).
			"0240-table/caption-table-margin.html", //
			// SVG document <text> (Batik → MyGVTGlyphVector): horizontal/vertical, bold, synthetic italic (2026-09-14).
			"0480-svg-text/inline-svg-text.html", //
			// Computed values of font-relative units cap / ic / ric / rlh (2026-08-30).
			// These require the real font's cap-height and the root line-height, so declaration
			// parsing tests cannot cover them.
			"3020-VALUE/font-relative-units.html", //
			"0240-table/z-order.html", //
			// A tall, single-row wrapper table splits its row content within the first page's remaining space
			// (2026-08-27). Regression for removing the UA default page-break-inside:avoid on cells.
			// The entire table moved to the next page, leaving the first page almost blank.
			// (kawasaki-ombuds)
			"0240-table/single-row-split-after-line.html", //
			"0240-table/rowspan-after-empty-row.html", //
			"0240-table/rowspan-after-short-row.html", //
			// Retained row emission. RetentionHighWaterReportTest verifies activation and retention bounds.
			"0240-table/row-streaming-emit.html", //
			"0240-table/row-streaming-caption.html", //
			"0240-table/row-streaming-group-height.html", //
			// Verify rule coordinates when table attributes (frame/rules/align/valign/bordercolor)
			// are mapped to CSS (2026-08-04).
			"0240-table/frame-rules.html", //
			"3080-MODERN-CSS/layer-important.html", //
			// content-visibility (2026-08-11): hidden omits only the contents.
			"3080-MODERN-CSS/content-visibility.html", //
			// Intrinsic sizing keywords max-content/min-content/fit-content(L)
			// (2026-08-29). Verify that normal-flow blocks stop at their content width,
			// and verify widths for floats, inline-blocks, absolute positioning, and page-break continuations.
			"3080-MODERN-CSS/width-max-content.html", //
			"3080-MODERN-CSS/width-min-content.html", //
			"3080-MODERN-CSS/width-fit-content.html", //
			"3080-MODERN-CSS/width-intrinsic-float-inline-abs.html", //
			"3080-MODERN-CSS/width-intrinsic-page-split.html", //
			// Viewport units, env(), display aliases, % grid tracks,
			// currentColor(2026-08-29)
			"3080-MODERN-CSS/viewport-env-aliases.html", //
			"0242-table-height/percent-rowspan-groups.html", //
			"0242-table-height/group-size-empty-rows.html", //
			"0242-table-height/zero-percent-row-rowspan.html", //
			"0330-table-border/collapse-asymmetric-fixed.html", //
			"0330-table-border/collapse-group-inner-lines.html", //
			"0330-table-border/collapse-multi-groups.html", //
			"0330-table-border/collapse-rowspan-spacing.html", //
			"0330-table-border/collapse-illegal.html", //
			"0240-table/absolute.html", //
			"0219-pagebreak-table-inrow/valign-split.html", //
			// Ruby = annotated text (spec decision on 2026-07-25). Since units are smaller than lines,
			// paragraph line splitting naturally separates them at page boundaries (the old box model's
			// special contract allowing same-direction ruby body text to split no longer exists).
			"3060-RUBY/ruby-split-through.html", //
			// Directly verify ruby unit construction (multiple rb/rt mappings, fragment formatting,
			// nesting, malformed content on only one side, and vertical writing).
			"3060-RUBY/ruby-annotation.html", //
			"3060-RUBY/ruby-advanced.html", // CSS Ruby Level 1: merge/align/overhang/position/rtc
			"3060-RUBY/warichu.html", // JLREQ 3.4: horizontal/vertical two-line warichu and kinsoku (line-breaking rules) via Copper extensions.
			// JLREQ 3.5.5/3.5.6: subscripts/superscripts and distributed alignment using standard HTML/CSS;
			// 3.6.3: place ruby between lines while preserving the reference line position.
			"0510-text-spacing/jlreq-composed-features.html", //
			"0219-pagebreak-table-inrow/valign-split-vert.html", //
			"0390-writing-mode/border-collapse.html", //
			"0390-writing-mode/absolute.html", //
			"0400-column-count/nest.html", //
			"0350-line-height/small-line-height.html", //
			"0140-content/counters.html", //
			// Skip unreadable @font-face src formats (2026-08-05).
			// Asynchronous loading prevented fallback to the next candidate on failure.
			"1080-FONT/font-face-format.html", //
			"0450-hyphens/hyphens.html", //
			// Hyphenation hyphens on the line that fills the type area and closes a block (2026-09-01).
			// Unlike ordinary line overflow, this uses drawLine(true), which prevented materialization.
			"0450-hyphens/hyphen-at-page-break.html", //
			"0450-hyphens/word-then-paren.html", //
			// Nested flex (2026-08-05). An item's own declared width was not included in intrinsic sizing,
			// leaving negative free space and collapsing inner items to zero width.
			"0510-flex/nested-flex-child-lost.html", //
			"0510-flex/float-item.html", //
			// Resolve actual padding/margin sizes for flex items (2026-08-04). Both disappeared entirely
			// along the line axis; they happened to work vertically through a separate path,
			// so checking only one direction cannot catch this.
			"0510-flex/item-padding-margin.html", //
			// Block-level approximation of inline-flex (2026-08-11).
			"0510-flex/inline-flex.html", //
			// Tate-chu-yoko: all fits within 1em; horizontal and manually set horizontal writing retain
			// their natural width (2026-08-11).
			"0500-ext-css/text-combine-all.html", //
			// Assignments in resolveRelativeOffset overwrote Flex/Grid main-axis placement
			// (2026-08-06). See the Javadoc in AbstractContainerBox.java.
			// The second and subsequent items collapsed to the origin (x=0) and overlapped.
			"0510-flex/row-position-static-offset.html", //
			// Flex line splitting (2026-08-07, Bug C). Forced splits across lines left items
			// in the same line staggered instead of aligned on a common cut line.
			"0510-flex/row-split-across-break.html", //
			// Do not re-resolve item auto margins on continuation fragments (2026-08-27). During restyle
			// reconstruction, calculateSize re-resolved margin:0 auto using block rules;
			// a double position shift plus frame expansion in restoreExtents shrank the inner size
			// with each generation (asahi.com article body columns collapsed to one character wide).
			"0510-flex/auto-margin-item-fragmentation.html", //
			// Intrinsic table-cell widths for flex containers with padding (2026-08-08).
			// IntrinsicMeasurer omitted lineFrame from flex/grid/table contributions,
			// narrowing them by the frame width and clipping GitHub filenames.
			"0510-flex/padded-flex-in-auto-cell.html", //
			// Move a flex line containing absolutely positioned children intact across a page break (2026-08-08,
			// yahoo). Verify that both cells align at the top even after restyle reconstruction, and a min-width
			// item containing only an absolutely positioned number retains its size.
			"0510-flex/pushed-row-absolute-child.html", //
			// Resolve flex-basis:calc(50% - 16px) (2026-08-08, asahi). The old implementation
			// treated every calc as auto and collapsed to min-content.
			"0510-flex/basis-calc.html", //
			// Continuation fragments retain the used width after flex shrinking across page breaks (2026-08-08,
			// asahi). Re-resolving the percentage in the specified width:100% pushed the fixed-width sidebar
			// outside the paper.
			"0510-flex/split-item-keeps-flexed-width.html", //
			// A percentage-sized replaced element contributes 0 to min-content (2026-08-08, asahi).
			// Previously, its natural width raised the minimum and prevented the flex item from shrinking.
			"0510-flex/percent-image-min-content.html", //
			// Specified line-axis sizes for nested flex container items (2026-08-08, asahi).
			// The neutral wrapper takes over the size; the child resolves it by filling the wrapper.
			"0510-flex/nested-container-item-width.html", //
			// Minimum contributions of nested containers with min-width (2026-08-08, NHK navigation).
			// Previously, flex-shrink reduced them below min-width, overlapping the adjacent background.
			"0510-flex/min-width-nested-container.html", //
			// min/max-width with box-sizing:border-box includes the frame (2026-08-29). Normal-flow
			// blocks (FlowBlockBox) added the frame twice and expanded to 116px.
			"0510-flex/min-width-border-box-block.html", //
			// Main-axis auto margins on nested container items (2026-08-09). The neutral wrapper
			// did not take over authored auto margins, disabling right alignment with .ml-auto
			// (an actual bug in the 5ch.io header).
			"0510-flex/auto-margin-nested-container.html", //
			// Page-break splitting of flex lines with fixed-height items (2026-08-09).
			// Without specifiedPageAxis, FragmentState did not split the specified height into a remainder;
			// continuations re-resolved the full specified height, expanding the line.
			"0510-flex/fixed-height-item-split.html", //
			// Percentage-width replaced elements as flex items (2026-08-09). The neutral wrapper did not
			// take over authored sizes, leaving width:100% on an svg without intrinsic dimensions at the
			// two-pass measured value of 0 (the latter half of NHK navigation's empty chevron box issue).
			"0510-flex/percent-replaced-item.html", //
			// Line boxes containing spaces do not exceed the specified line-height (2026-08-09,
			// pdfg2d). WhiteSpace control used the largest metrics in the font list, unlike words
			// (first font), so only lines containing spaces grew by 4–5%.
			"0350-line-height/space-run-height.html", //
			// place-items/place-self/place-content shorthands (2026-08-09).
			// Invalid place-items:center declarations on NHK navigation buttons left icons
			// aligned to the top left.
			"0500-grid/place-shorthand.html", //
			// Grid extensions (2026-08-29, 50-site sweep): grid-area+grid-template-areas+
			// line names+grid-template-rows, % and repeat(N,%) tracks, auto-fill/auto-fit,
			// grid-auto-flow:column+grid-auto-columns+grid-gap aliases.
			"0500-grid/area-template.html", //
			"0500-grid/percent-repeat-tracks.html", //
			"0500-grid/auto-fill.html", //
			"0500-grid/auto-flow-column.html", //
			// Both ends of minmax() (css-grid-1 §11.5), subgrid (css-grid-2),
			// grid-template/grid shorthands, image-set() (2026-08-29).
			"0500-grid/minmax.html", //
			"0500-grid/subgrid.html", //
			"0500-grid/subgrid-rows.html", //
			"0500-grid/grid-shorthand.html", //
			"0500-grid/row-stretch.html", //
			"3080-MODERN-CSS/image-set.html", //
			// aspect-ratio (2026-08-29): definite width→height, definite height→width, replaced elements,
			// border-box, content overflow.
			"3080-MODERN-CSS/aspect-ratio.html", //
			"0470-margin-boxes/margin-boxes.html", //
			// Left/right margin boxes in vertical writing (2026-09-06, user handoff §4). Through 19056,
			// flow was fixed to TB, wrapping running headers at the band width and overlapping them at one x.
			// vertical-align uses y; x centers in the band; padding/margin do not wrap text (measured in Vivliostyle).
			"0470-margin-boxes/vertical-side-boxes.html", //
			// Case sensitivity of attribute prefix/suffix/substring matching (2026-08-05). Both sides
			// were lowercased, making li[type^="a"] and li[type^="A"] both match.
			"3000-SELECTOR/attr-prefix-case.html", //
			// Carry percentage translate components through freeze/materialize (2026-08-08,
			// yahoo search button magnifier). Verify that translateY(-50%) on an absolutely
			// positioned box in an inline context passing through record/replay appears in
			// the golden tf= matrix (dump coordinates precede GC transforms, so without tf=
			// output no transform regression is visible).
			"0490-transform/percent-translate-inline-context.html", //
			// Percentage translate combined with other functions (2026-08-29). translate(-50%,-50%)
			// scale(1.1) was entirely invalidated. Also verify cross terms and 3D reduction.
			"0490-transform/percent-translate-with-scale.html", //
			// Individual transform properties translate/rotate/scale and zoom (2026-08-29).
			// Verify composition order T·R·S·transform, transformation of percentage components by R·S,
			// and zoom scaling about the top-left origin (expected matrices are in the fixture).
			"0490-transform/individual-properties.html", //
			// tab-size (2026-08-29). Numbers as multiples of space width, lengths, and 0. Tabs on the
			// second line align with the same tab stops as the first line.
			"0050-white-space/tab-size.html", //
			// UA default dialog:not([open]) (2026-08-07). Verify that native dialogs
			// without an open attribute do not appear on the paper.
			"0130-display/dialog-closed.html", //
			// Closed details renders only summary (2026-08-08). Found when bbc.com's
			// no-JS navigation became visible. Verify the contrast with open details.
			"0130-display/details-closed.html", //
			// display:contents (2026-08-07). Verify box transparency, inheritance, flex item generation,
			// wrappers inside tables, and treating replaced elements as none. Regression for MDN's
			// main{display:contents}, which lost all body text.
			"0130-display/contents-basic.html", //
			"3000-SELECTOR/nth.html", //
			"3000-SELECTOR/dir.html", //
			"3000-SELECTOR/html5-elements.html", //
			"3080-MODERN-CSS/initial-unset.html", //
			"3080-MODERN-CSS/calc.html", //
			// Font-relative units in calc() (2026-08-03). em/rem resolve only at computed-value time,
			// so carry them separately from absolute and percentage components.
			"3080-MODERN-CSS/calc-font-relative.html", //
			// calc() containing font-relative units in font-size/line-height
			// (2026-08-09). Regression for dropping the root font-size reduction idiom calc(1em * 0.625),
			// which enlarged all rem sizes by 1.6 (e-gov), and for ClassCastException
			// in line-height mixing percentages.
			"3080-MODERN-CSS/calc-font-size.html", //
			// lh units (2026-08-27). Verify standalone use, use in calc, line-height self-reference
			// (based on the inherited value), and approximate transform resolution. Regression for MDN's
			// external-link icon (translateY(calc(.5lh - .5em))), which shifted upward.
			"3080-MODERN-CSS/lh-unit.html", //
			// HTML fragments without body (2026-08-09, e-Gov legislation HTML). TagBalancer injected
			// synthesized html/body inside open elements, corrupting the tree.
			"3030-FRAGMENT/body-less-fragment.html", //
			// Gradient approximation for mask-image (2026-08-09). Approximate the body-text
			// excerpt fade-out idiom with box clipping.
			// Regression for ignored masks on 5ch.io, where overflowing body text overlapped later content.
			"3080-MODERN-CSS/mask-image-clip.html", //
			// Browser-like clipping for overflow:scroll/auto (owner decision on 2026-08-09).
			// Previously, overflow was drawn as is, so absolutely positioned tab headings and similar
			// elements overlapped fully expanded content (asahi p-tab).
			"0025-selector/invalid-list.html", //
			"0040-overflow/scroll-clip.html", //
			"0040-overflow/axis-properties.html", //
			// text-overflow: ellipsis (2026-08-29). Verify end clipping on nowrap lines and additional
			// ellipsis drawing, the clip default, and no effect with overflow:visible.
			"0040-overflow/text-overflow-ellipsis.html", //
			// line-clamp / -webkit-line-clamp (2026-08-29). Ellipsis at line N's end,
			// suppression from line N+1 (block height=N lines), and no ellipsis for paragraphs shorter than N.
			"0040-overflow/line-clamp.html", //
			// text-decoration-style/-thickness/text-underline-offset/-position
			// (2026-08-29). Decoration geometry is absent from display lists, so verify only line placement
			// (TextDecorationStyleTest checks pixels for line styles).
			"0160-text-decoration/decoration-styles.html", //
			// text-shadow blur (2026-08-29). Shadows are absent from display lists, so verify only
			// geometry (TextShadowBlurTest checks the blur's spread using pixels).
			"0150-text-shadow/blur.html", //
			// clip-path: path() (2026-08-29). SVG paths in px convert to pt and appear in
			// clip rectangles with the reference box's top-left corner as the origin.
			"3080-MODERN-CSS/clip-path-path.html", //
			// mix-blend-mode (2026-08-29). Approximation per drawable; absent from display lists,
			// so verify only geometry (MixBlendModeTest checks compositing results using pixels).
			"3080-MODERN-CSS/mix-blend-mode.html", //
			// Radial/conic/repeating gradients, multiple backgrounds, and filter (2026-08-29).
			// Fill summaries (bg=) and filter text (filter=) appear on frame drawables.
			"3080-MODERN-CSS/gradients.html", //
			"3080-MODERN-CSS/filter.html", //
			// Do not inject the UA's ZWSP (::before) into button flex/grid containers
			// (2026-08-09). The ZWSP became a separate item and pushed the icon outside the box.
			"3080-MODERN-CSS/button-flex-grid-content.html", //
			// Typed attr() (2026-08-03). Foundation for handling HTML presentational attributes through CSS.
			"3080-MODERN-CSS/typed-attr.html", //
			// Logical border properties (2026-08-03). Twelve properties including border-block-end.
			"3080-MODERN-CSS/logical-borders.html", //
			"3000-SELECTOR/is-not-where-sibling.html", //
			"3070-AT-RULE/media-supports.html", //
			// @page marks / bleed (2026-08-02). Verify coordinates: the paper expands by the
			// CSS-specified bleed, and the type area moves inward by that amount.
			"3070-AT-RULE/page-marks-bleed.html", //
			"3080-MODERN-CSS/logical-properties.html", //
			"3000-SELECTOR/is-not-where-descendant.html", //
			"3080-MODERN-CSS/var.html", //
			// CSS Nesting (2026-08-02). Use element widths to verify descendant combinators,
			// & (leading/nonleading/compound), relative selectors, selector-list Cartesian products,
			// three-level nesting, and preserved order of declarations after nested rules.
			"3080-MODERN-CSS/nesting.html", //
			// @counter-style (2026-08-02). Verify cyclic/fixed (out-of-range fallback)/
			// additive/numeric+pad+negative/alphabetic/extends representations,
			// prefix/suffix, and the counter() path (without prefix/suffix).
			"3080-MODERN-CSS/counter-style.html", //
			// Standard-name aliases and counter-set (2026-08-02). Verify overflow-wrap
			// normal/break-word/anywhere, counter-set assigning existing counters without nesting,
			// and unknown names creating counters on that element.
			"3080-MODERN-CSS/standard-aliases.html", //
			// Form control geometry (2026-08-02). Verify button-label placement inside the box,
			// and dimensions of input, select, and multiline controls.
			"3080-MODERN-CSS/form-controls.html", //
			// /cover and /contain in the background shorthand, and GC scoping for background colors
			// with alpha (2026-08-27; two cases of black video thumbnails on asahi.com).
			"3080-MODERN-CSS/bg-shorthand-size-and-alpha.html", //
			// inset shorthand and absolute centering with inset:0+margin:auto
			// (2026-08-27; asahi.com play icons shifted to the top left).
			"3080-MODERN-CSS/inset-center.html", //
			// The contain constraint for SVG backgrounds with only viewBox (2026-08-27;
			// the Re:Ron logo in asahi.com's footer overflowed its box at its original size).
			"3080-MODERN-CSS/bg-svg-intrinsic.html", //
			// Verify gaps found from warnings on real sites together (2026-08-29): flow-root,
			// 8-digit hex, padding:inherit, word-break:break-word, margin-inline,
			// -webkit-mask, four-value background position, font-kerning, isolate,
			// and vendor-prefixed multi-column layout.
			"3080-MODERN-CSS/real-site-gaps.html", //
			// Presence and annotations of drawables for box-shadow (outer/inner/multiple/rounded)
			// and outline (offset/auto) (2026-08-29; BoxDecorationTest checks fill counts).
			"3080-MODERN-CSS/box-shadow-outline.html", //
			// Float avoidance for flex/grid containers (2026-08-27; independent formatting contexts
			// do not overlap floats; overlapping labels in asahi.com's footer).
			"0510-flex/container-avoids-float.html", //
			// Visual rescue splitting (2026-07-25, increment 5). The only path that geometrically cuts
			// replaced elements overflowing even at the page start and sends them to the next page.
			// Verify fragment coordinates, artifact markers, and page counts here.
			// SVG written directly in HTML (2026-08-06). Without xmlns, namespace declarations
			// did not reach the builder, and the entire SVG was not drawn.
			// Children (fallback content) of an object that cannot load are drawn
			// (2026-08-07). Introducing AltTextImage (2026-08-06) made object a replaced
			// box and removed its children; this surfaced as missing eyes in acid2.
			"3050-IMG/object-fallback.html", //
			// object-fit/object-position(2026-08-27)。cover/contain/none/
			// Verify actual drawing rectangles and clipping for scale-down, and object-position
			// keywords, percentages, lengths, and single values (y=center).
			// Regression for ignored cover stretching thumbnails on jigensha.info.
			"3050-IMG/object-fit.html", //
			"3050-IMG/inline-svg-implicit-ns.html", //
			// Size inline SVG with only viewBox (no width/height attributes) via CSS classes/
			// inline style (2026-08-06). Attribute lookup was limited to the XHTML
			// namespace, missing attributes in foreign content.
			"3050-IMG/svg-css-size-no-attrs.html", //
			// Class selectors with backslash escapes (2026-08-06). ph-css returned
			// raw selector strings without resolving CSS identifier escapes,
			// so classes from Tailwind variant prefixes (hover:, lg:, [&_svg]:, etc.)
			// never matched HTML class attributes (which have no escapes).
			"3050-IMG/svg-escaped-class-selector.html", //
			"3050-IMG/rescue-tall.html", //
			"3050-IMG/rescue-tall-vert.html", //
			"3050-IMG/rescue-exact.html", //
			"3050-IMG/rescue-huge.html", //
			"3050-IMG/rescue-absolute.html", //
			"3050-IMG/rescue-column.html", //
			// Visual rescue splitting (2026-07-25, increments 6/7). Cover the extended types:
			// oversized lines, writing-direction-mismatched blocks, table cells, multi-column layout,
			// and floats; no extra page for an exactly divisible height.
			"0480-rescue-split/huge-font-line.html", //
			"0480-rescue-split/huge-font-exact.html", //
			// Footnotes (F0–F5, 2026-07-31). Verify call/marker labels, footnote-area coordinates,
			// per-page numbering, body-text shortening, and retained carry-in numbers, including paint order.
			"0125-footnote/footnote-f1.html", //
			"0125-footnote/footnote-pagereset.html", //
			"0125-footnote/footnote-pagelimit.html", //
			"0125-footnote/footnote-carryin.html", //
			"0125-footnote/footnote-vertical-rl.html", //
			"0125-footnote/footnote-bottom-vertical-rl.html", // F-1: Carry-over notes into the horizontal bottom band.
			"0125-footnote/footnote-bottom-columns.html", // F-4: Two vertical columns and the page-wide bottom band.
			"0125-footnote/footnote-columns-block-end.html", // F-8: block-end footnotes within multi-column layout.
			"0125-footnote/footnote-columns-block-end-horizontal.html", // F-8e: Horizontal writing also uses column ends.
			"0125-footnote/footnote-columns-block-end-carry.html", // F-8e increment 5: Carry-over across pages in multi-column layout.
			"0125-footnote/footnote-columns-block-end-balance.html", // F-8e increment 6: Collection before balancing.
			"0125-footnote/footnote-bottom-fixed-height.html", // F-7: Fixed bands on all pages and FIFO forwarding.
			// Page floats (2026-08-02). Verify coordinates: float: bottom sits at the type area's
			// bottom edge (above any footnotes), float: top sits at the next page's start,
			// and subsequent flow starts below it.
			"0125-footnote/page-float.html", //
			"0125-footnote/page-margin-note-horizontal.html", // JLREQ sidenotes in horizontal writing (logical line-end side).
			"0125-footnote/page-margin-note-vertical.html", // JLREQ headnotes in vertical writing (logical line-start side).
			// Grid G1 (2026-07-31). Verify column/row starts, gaps, total Grid height
			// (the following block's position) for fixed tracks, and G0 fallback plus atomic
			// page forwarding for ineligible Grid (1fr).
			"0500-grid/fixed-2x2.html", //
			"0500-twopass-range/t4b-cell-parent.html",
			"0500-twopass-range/t4b-flex-anonymous.html",
			"0500-twopass-range/t4b-flex-column-nowrap.html",
			"0500-twopass-range/t4b-flex-column-wrap.html",
			"0500-twopass-range/t4b-flex-middle-normal.html",
			"0500-twopass-range/t4b-flex-middle-pushed.html",
			"0500-twopass-range/t4b-flex-neutral-column.html",
			"0500-twopass-range/t4b-flex-neutral-row.html",
			"0500-twopass-range/t4b-flex-neutral-sealed-float.html",
			"0500-twopass-range/t4b-flex-neutral-sealed-inline-block.html",
			"0500-twopass-range/t4b-flex-row-nowrap.html",
			"0500-twopass-range/t4b-flex-row-wrap.html",
			"0500-twopass-range/t4b-flex-sealed-float.html",
			"0500-twopass-range/t4b-flex-sealed-inline-block.html",
			"0500-twopass-range/t4b-flex-takeover-content.html",
			"0500-twopass-range/t4b-float-parent.html",
			"0500-twopass-range/t4b-grid-anonymous.html",
			"0500-twopass-range/t4b-grid-neutral-roots.html",
			"0500-twopass-range/t4b-grid-neutral-sealed-float.html",
			"0500-twopass-range/t4b-grid-neutral-sealed-inline-block.html",
			"0500-twopass-range/t4b-grid-sealed-float.html",
			"0500-twopass-range/t4b-grid-sealed-inline-block.html",
			"0500-twopass-range/t4b-grid-span-areas.html",
			"0500-twopass-range/t4b-grid-takeover-content.html",
			"0500-twopass-range/t4b-grid-tracks-column.html",
			"0500-twopass-range/t4b-grid-tracks-fixed.html",
			"0500-twopass-range/t4b-grid-tracks-fr.html",
			"0500-twopass-range/t4b-item-lifecycle.html",
			"0500-twopass-range/t4b-anon-flex-fit-content-mixed.html",
			"0500-twopass-range/t4b-anon-flex-fit-content-text.html",
			"0500-twopass-range/t4b-anon-flex-max-content-mixed.html",
			"0500-twopass-range/t4b-anon-flex-max-content-text.html",
			"0500-twopass-range/t4b-anon-grid-fit-content-mixed.html",
			"0500-twopass-range/t4b-anon-grid-fit-content-text.html",
			"0500-twopass-range/t4b-anon-grid-max-content-mixed.html",
			"0500-twopass-range/t4b-anon-grid-max-content-text.html",
			"0500-twopass-range/t4b-table-absolute-in-table.html",
			"0500-twopass-range/t4b-table-absolute-table.html",
			"0500-twopass-range/t4b-table-float-across-pages.html",
			"0500-twopass-range/t4b-table-float-in-fixed-cell.html",
			"0500-twopass-range/t4b-table-float-in-table.html",
			"0500-twopass-range/t4b-table-inline-table.html",
			"0500-twopass-range/huge-grid.html", // T2: Retention and page breaks for oversized Grid.
			"0500-twopass-range/t4b-ledger-owners.html",
			"0500-twopass-range/anon-whitespace.html", // T3b: Synthetic events for anonymous items.
			"0500-twopass-range/anon-text-absolute.html", // T3b: Synthetic events for anonymous items.
			"0500-twopass-range/anon-float-text.html", // T3b: Synthetic events for anonymous items.
			"0500-twopass-range/anon-generated.html", // T3b: Synthetic events for anonymous items.
			"0500-twopass-range/anon-before.html", // T3b: Synthetic events for anonymous items.
			"0500-twopass-range/anon-nested-in-range.html", // T3b: Synthetic events for anonymous items.
			// Nonstandard approximate support for minmax()/max()/min() (2026-08-06).
			// See GridTemplateTracks.java's class Javadoc: use only the maximum and discard
			// the minimum. Fill a support gap found in actual yahoo.co.jp CSS.
			"0500-grid/minmax-max-approx.html", //
			"0500-grid/mixed-items.html", //
			"0500-grid/auto-columns.html", //
			"0500-grid/intrinsic-auto-fr.html", //
			"0500-grid/grid-in-float.html", //
			"0500-grid/grid-in-float-shrink.html", //
			"0500-grid/nested-grid.html", //
			"0500-grid/explicit-columns.html", //
			"0500-grid/explicit-rows-sparse.html", //
			"0500-grid/explicit-overlap.html", //
			"0500-grid/empty-row-gap.html", //
			"0500-grid/explicit-placement-in-float.html", //
			"0500-grid/row-span-auto.html", //
			"0500-grid/alignment-items.html", //
			"0500-grid/alignment-content.html", //
			"0500-grid/alignment-in-float.html", //
			"0500-grid/oversized-atomic.html", //
			"0500-grid/atomic-move-rowspan.html", //
			"0500-grid/row-split-carry.html", //
			"0500-grid/row-split-force.html", //
			"0500-grid/min-height-slack.html", //
			"0500-grid/row-split-pinned-order.html", //
			// Blockification of display for absolute positioning (2026-08-02). Verify that position:absolute
			// display(flex/grid/table-row/table-cell/inline-block/list-item)
			// does not throw, and static positions are determined as for blocks.
			// Regression for a crash on a real page (yahoo.co.jp).
			"0500-grid/absolute-flex-grid.html", //
			// Japanese text spacing A2 (2026-07-31). Verify text-autospace gaps (0.125em)
			// at run-boundary x coordinates (off/on/numeric-only/suppressed by explicit spaces).
			"0510-text-spacing/autospace-horizontal.html", //
			// Quarter-em spacing before Latin text/digits after proportional punctuation (IPAPGothic's "、"=0.5em) (2026-09-14).
			"0510-text-spacing/autospace-proportional-punctuation.html", //
			"0510-text-spacing/jlreq-justify-priority.html", // JLREQ 3.8.4 four-stage line expansion.
			"0510-text-spacing/jlreq-shrink-priority.html", // JLREQ 3.8.3 six-stage line compression.
			// Subtract the half-em already removed by consecutive-punctuation trimming from compression capacity (2026-09-11).
			// Without subtraction, ） in "）。" lost all spacing, and 。 intruded into ）'s glyph bounds.
			"0510-text-spacing/jlreq-shrink-trimmed-pair.html", //
			// Half-em/quarter-em compression spaces are blank areas in full-width punctuation glyphs. Proportional punctuation
			// (IPA P Gothic's ・=0.5em, （【】=0.55em) lacks them, so exclude them from capacity (2026-09-11).
			// Including them caused overlaps of -0.25em for ・（ and 0.2em for 文【 (per-font randomized tests).
			"0510-text-spacing/jlreq-shrink-proportional.html", //
			"0510-text-spacing/jlreq-shrink-vertical-colon.html", // Bound pairTrim and line compression by the gap between glyph bounds.
			// Resolve logical margin/padding for tate-chu-yoko in the line's (vertical) writing direction (2026-09-11).
			// Previously, margin-inline-start affected the physical left side.
			"0390-writing-mode/tcy-logical-margin.html", //
			"0510-text-spacing/autospace-vertical.html", //
			"0510-text-spacing/autospace-in-float.html", //
			// Carry text-autospace into the M2c actual-measurement wrapper (2026-08-08).
			// Verify that inline-block shrink-to-fit measurement fits on one line at a width
			// including the Latin→CJK boundary gap (kabutan regression).
			"0510-text-spacing/autospace-inline-block-measured.html", //
			// Japanese text spacing T1b (2026-07-31/2026-08-23). Verify the contrast among normal/trim-start/
			// space-all at line starts and between consecutive punctuation marks.
			"0510-text-spacing/trim-pairs.html", //
			// Flow notes vertically into the bottom band of vertical writing (2026-09-11). Previously, notes missed page 1,
			// body text grew from 1 to 3 pages, and notes flowed to the type area's lower left and off the paper.
			"0125-footnote/footnote-bottom-vertical-band.html", //
			// Top band (headnotes). Lower the body-text start by the band's size (2026-09-11).
			"0125-footnote/footnote-top-vertical-band.html", //
			"0125-footnote/footnote-top-horizontal-band.html", //
			// Width table for built-in CID-keyed fonts. The half-width range extended through CID 635,
			// so only 　、。 (CID 633–635) advanced at half width, making characters immediately
			// after punctuation appear cramped (2026-09-11).
			"0510-text-spacing/builtin-fullwidth-punctuation.html", //
			// CSS Text 4 line-edge policy. Verify unconditional line-end trimming for trim-both/auto,
			// and space-first differences among the first line, forced breaks, and automatic wrapping.
			"0510-text-spacing/trim-line-edges.html", //
			// Japanese text spacing T2/H1 (2026-07-31). Verify line compression for hanging
			// punctuation at line ends (allow-end) and conditional half-width line-end trimming.
			"0510-text-spacing/hanging-end.html", //
			// JLREQ E/F: verify punctuation trimming across style-run boundaries, suppressed expansion
			// after middle dots, and flush starts of block-initial lines in horizontal/vertical writing.
			"0510-text-spacing/jlreq-boundaries.html", //
			// Named pages N1b (2026-07-31). Verify that the root's page name enables @page chapter's
			// running header, chapter:first wins on the first page by specificity,
			// and unnamed @page margins are inherited and combined.
			"0520-named-page/named-margin-box.html", //
			// N2a: Verify forced page breaks on page-name transitions (no double forwarding
			// when combined with author breaks) and return to unnamed pages.
			"0520-named-page/transition-no-double-break.html", //
			// N3/N4: A landscape chapter using @page size (variable page sizes). Verify that
			// the running header's center (=page width/2) moves back and forth across
			// portrait→landscape→portrait.
			"0520-named-page/landscape-section.html", //
			// N2b: Verify that transitions propagate even when a name changes deep inside an
			// unnamed wrapper, producing three sections for A→B→A.
			"0520-named-page/nested-transition.html", //
			// N2b: Name transition immediately after an explicit page break (at the page start).
			// Verify that the blank page with the old name is dropped and rebuilt with
			// the new name's running header and landscape dimensions.
			"0520-named-page/head-transition.html", //
			// N2b: Named content at the document start. Verify that the initial unnamed blank page
			// is replaced, using the new name's running header and dimensions from page 1.
			"0520-named-page/doc-head-transition.html", //
			// leader() L1 (2026-07-31): verify dotted/solid/custom, allocation of remaining space,
			// and phase alignment from the line end (dots align vertically on one-/two-digit lines).
			"0530-leader/basic.html", //
			// leader() H1: Verify wrapping of long chapter titles (leader only on the final line)
			// and the contrast with short lines.
			"0530-leader/wrap.html", //
			// leader() H1: Verify that multiple leaders on one line share the remaining space equally.
			"0530-leader/multiple.html", //
			// leader() H1: Leader lines have ≈0 justification expansion (they consume free space first);
			// verify that justification on ordinary lines does not regress.
			"0530-leader/justify.html", //
			// leader() V1: Verify allocation (axis-neutral) and vertical drawing in vertical-rl.
			"0530-leader/vertical.html", //
			// leader() H1: A table of contents across page breaks (small pages via @page size).
			// Verify that range replay re-drives Leader events without leaking width.
			"0530-leader/replay.html", //
			"0500-grid/atomic-move.html", //
			"0480-rescue-split/tall-inline-block.html", //
			"0480-rescue-split/orthogonal-block.html", //
			"0480-rescue-split/orthogonal-block-exact.html", //
			"0480-rescue-split/cell-tall-image.html", //
			"0480-rescue-split/cell-tall-image-exact.html", //
			"0480-rescue-split/column-tall-block.html", //
			"0480-rescue-split/column-tall-block-exact.html", //
			// Fragment coordinates when the page itself uses vertical writing (added 2026-07-25).
			// Every existing vertical-writing fixture used a vertical block in a horizontal page,
			// never exercising the vertical branch of VisualRescueBox.sourceDrawX.
			"0480-rescue-split/vertical-page-rl.html", //
			// vertical-lr mirrors vertical-rl (LR implemented on 2026-07-25).
			"0480-rescue-split/vertical-page-lr.html", //
			"0390-writing-mode/vertical-lr-blocks.html", //
			// Spanning cells (2026-07-25, three cases found by independent review and randomized checks).
			// (a) rowspan crossed row groups and erased the first cell of tbody.
			// (b) Spanning-cell coordinates used an RL-only formula, shifting outside the table in vertical-lr.
			"0495-span/rowspan-crosses-rowgroup.html", //
			"0495-span/rowspan-crosses-rowgroup-second-column.html", //
			"0495-span/rowspan-vertical-lr.html", //
			"0495-span/rowspan-vertical-rl.html", //
			// (c) Spanning cells did not appear on the next page after a forced page break.
			"0495-span/rowspan-forced-break.html", //
			// (d) rowspan after an empty row stops after two rows: **unresolved**.
			// The golden records the current (incorrect) output, not correctness.
			// A fix will produce a diff; update the golden then.
			"0495-span/rowspan-after-empty-row.html", //
			// (e) colspan exceeding the column count × collapsed borders, vertical writing × fixed × rowspan.
			// Both were identified in independent review but **could not be reproduced**.
			// The golden ensures that future changes are noticed.
			"0495-span/colspan-beyond-columns-collapse.html", //
			"0495-span/rowspan-vertical-fixed.html", //
			"0480-rescue-split/float-tall.html", //
			"0480-rescue-split/float-exact.html", //
	};

	/**
	 * Documents requiring processing.pass-count&gt;=2 (:has()/:last-child variants using
	 * STRUCTURE_SCAN; see the development plan's "Two-pass control mode").
	 * Keep these separate from DOCUMENTS above: with the default pass-count=1,
	 * imposing pass-count=2 on every document would increase costs for unrelated documents.
	 */
	private static final String[] MULTI_PASS_DOCUMENTS = { //
			"3000-SELECTOR/last-child-family.html", //
			"3000-SELECTOR/has.html", //
	};

	record CorpusDocument(String path, int passCount) { }

	/** Share the golden target documents and pass counts with other full-corpus observation tests. */
	static List<CorpusDocument> corpusDocuments() {
		final List<CorpusDocument> documents = new ArrayList<>();
		for (final String doc : DOCUMENTS) documents.add(new CorpusDocument(doc, 1));
		for (final String doc : MULTI_PASS_DOCUMENTS) documents.add(new CorpusDocument(doc, 2));
		return List.copyOf(documents);
	}

	public void testDisplayLists() throws Exception {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.reset();
		List<String> failures = new ArrayList<>();
		for (final CorpusDocument doc : corpusDocuments()) {
			if (!selectedByFilter(doc.path())) {
				continue;
			}
			checkDocument(doc.path(), doc.passCount(), failures);
		}
		if (System.getProperty("foliojet.displayListFilter") == null) {
			reportTwoPassRangeBind(failures);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	/** Quickly rerun only display-list fixtures using comma-separated substring matches. */
	private static boolean selectedByFilter(final String doc) {
		final String filter = System.getProperty("foliojet.displayListFilter");
		if (filter == null || filter.isBlank()) {
			return true;
		}
		for (final String token : filter.split(",")) {
			if (!token.isBlank() && doc.contains(token.trim())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Corpus measurement report and wiring verification for TwoPass ranges
	 * (E-6 increment 4b, enabled by default in production). Golden equality alone can miss inactive
	 * range replay, so verify that range bind actually fires in this corpus.
	 * Report seal/consume/subsume/discard balances since T1 to stderr and check for unterminated ranges.
	 * RangeHandle's state machine prevents double termination.
	 */
	private static void reportTwoPassRangeBind(List<String> failures) {
		final long seals = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get();
		final long rangeBinds = net.zamasoft.foliojet.layout.fragment.ContinuationStats.RANGE_FIRST_BINDS.get();
		final long cellSeals = net.zamasoft.foliojet.layout.fragment.ContinuationStats.CELL_RANGE_SEALS.get();
		final long cellRangeBinds = net.zamasoft.foliojet.layout.fragment.ContinuationStats.CELL_RANGE_BINDS.get();
		final StringBuilder s = new StringBuilder();
		s.append("[E-6 two-pass range bind / golden corpus]\n");
		s.append("  RANGE_FIRST_BINDS=").append(rangeBinds).append('\n');

		s.append("  TWO_PASS_SEALS_ELIGIBLE=").append(seals).append('\n');
		s.append("  CELL_RANGE_SEALS=").append(cellSeals).append('\n');
		s.append("  CELL_RANGE_BINDS=").append(cellRangeBinds).append('\n');

		// E-6 increment 5b-2: Per-table adoption rate of table Pass C (incremental row-by-row bind).
		final long passCTables = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TABLE_PASS_C_TABLES.get();
		final long legacyBindRows = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TABLE_LEGACY_BINDROWS.get();
		s.append("  TABLE_PASS_C_TABLES=").append(passCTables).append('\n');
		s.append("  TABLE_LEGACY_BINDROWS=").append(legacyBindRows).append('\n');
		s.append("  TABLE_PASS_B_CELL_MEASURES=")
				.append(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TABLE_PASS_B_CELL_MEASURES.get())
				.append('\n');
		long total = seals;
		for (final net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject r : net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject
				.values()) {
			final long count = net.zamasoft.foliojet.layout.fragment.ContinuationStats.twoPassSealRejects(r);
			total += count;
			s.append("  REJECT_").append(r).append('=').append(count).append('\n');
		}
		s.append("  ELIGIBLE_RATE=").append(seals).append('/').append(total).append('\n');
		System.err.print(s);
		if (rangeBinds == 0) {
			failures.add("TwoPass range bindがgoldenコーパスで一度も発火していません(空虚な緑)");
		}
		// DP increment 3: Child seals absorbed by parent ranges (SUBSUMED) release their leases
		// without binding. Including T1 scratch disposal, seals == consumed + subsumed + abandoned.
		final long subsumed = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TWO_PASS_SEALS_SUBSUMED.get();
		System.err.println("  TWO_PASS_SEALS_SUBSUMED=" + subsumed);
		final long consumed = ContinuationStats.TWO_PASS_RANGES_CONSUMED.get();
		final long abandoned = ContinuationStats.TWO_PASS_SEALS_ABANDONED.get();
		System.err.println("  TWO_PASS_RANGES_CONSUMED=" + consumed);
		System.err.println("  TWO_PASS_SEALS_ABANDONED=" + abandoned);
		// T1: Balance observation is a necessary condition. RangeHandle itself rejects double termination.
		System.err.println("  RANGE_BALANCE_DELTA=" + (seals - consumed - subsumed - abandoned));
		if (seals != consumed + subsumed + abandoned) {
			failures.add("未終端のRangeHandle: seals=" + seals + " consumed=" + consumed
					+ " subsumed=" + subsumed + " abandoned=" + abandoned);
		}

		// E-6 increment 5a: Verify table-cell range wiring and 1:1 leases. The corpus includes
		// auto tables (0240/0242/0330, etc.), so cell seals actually fire.
		if (cellSeals == 0) {
			failures.add("表セルのrange seal(E-6増分5a)がgoldenコーパスで一度も発火していません(空虚な緑)");
		}
		// Table subsumption (codex increment 5): Cell seals absorbed by parent ranges (SUBSUMED)
		// release leases without binding, so completion requires seals == binds + subsumed.
		final long cellSubsumed = net.zamasoft.foliojet.layout.fragment.ContinuationStats.CELL_RANGE_SEALS_SUBSUMED
				.get();
		System.err.println("  CELL_RANGE_SEALS_SUBSUMED=" + cellSubsumed);
		final long cellAbandoned = ContinuationStats.CELL_RANGE_SEALS_ABANDONED.get();
		System.err.println("  CELL_RANGE_SEALS_ABANDONED=" + cellAbandoned);
		System.err.println("  CELL_RANGE_BALANCE_DELTA="
				+ (cellSeals - cellRangeBinds - cellSubsumed - cellAbandoned));
		if (cellSeals != cellRangeBinds + cellSubsumed + cellAbandoned) {
			failures.add("未終端のセルリース: seals=" + cellSeals + " binds=" + cellRangeBinds
					+ " subsumed=" + cellSubsumed + " abandoned=" + cellAbandoned);
		}

		// E-6 increment 5b-2: Verify table Pass C wiring (incremental row-by-row bind). The corpus
		// includes Retained tables with all real cells eligible, so no Pass C activation is a vacuous pass.
		if (passCTables == 0) {
			failures.add("表Pass C(E-6増分5b-2)がgoldenコーパスで一度も発火していません(空虚な緑)");
		}
		// Row-by-row bind and upfront binding of all cells are separate contracts depending on table measurability.
		// Continue to verify that upfront binding of all cells does not occur in this corpus.
		if (legacyBindRows != 0) {
			failures.add("表の全セル先行bind(旧経路)がgoldenコーパスで発火しました(増分10の0固定の退行): "
					+ legacyBindRows);
		}
	}

	private void checkDocument(String doc, int passCount, List<String> failures) throws Exception {
		String name = doc.replace('/', '_').replace(".html", "");
		File outDir = new File("local/unittest/display-list/" + name);
		deleteChildren(outDir);
		File goldenDir = new File("files/unittest/display-list-golden/" + name);

		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			this.transcode(new File("files/unittest/" + doc), name, passCount);
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + doc, pages);
		assertTrue("表示リストが出力されていません: " + doc, pages.length > 0);

		if (!goldenDir.isDirectory()) {
			// Initial generation of baseline data.
			goldenDir.mkdirs();
			for (File page : pages) {
				Files.copy(page.toPath(), new File(goldenDir, page.getName()).toPath());
			}
			failures.add(doc + ": 基準データを生成しました。内容を確認してコミットしてください: " + goldenDir);
			return;
		}

		File[] goldenPages = goldenDir.listFiles((d, n) -> n.endsWith(".txt"));
		if (goldenPages.length != pages.length) {
			failures.add(
					doc + ": ページ数が基準と異なります (golden=" + goldenPages.length + ", actual=" + pages.length + ")");
			return;
		}
		for (File golden : goldenPages) {
			Path actual = new File(outDir, golden.getName()).toPath();
			String expected = Files.readString(golden.toPath(), StandardCharsets.UTF_8);
			String got = Files.readString(actual, StandardCharsets.UTF_8);
			if (!expected.equals(got)) {
				failures.add(doc + "/" + golden.getName() + ": 表示リストが基準と一致しません (expected=" + golden
						+ ", actual=" + actual + ")");
			}
		}
	}

	private void transcode(File source, String name, int passCount) throws Exception {
		File pdf = new File("local/unittest/display-list/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (passCount > 1) {
					session.property("processing.pass-count", String.valueOf(passCount));
				}
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}

	private static void deleteChildren(File dir) {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			child.delete();
		}
	}
}
