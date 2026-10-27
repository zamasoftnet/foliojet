package jp.cssj.test.unit.displaylist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Documents of the sweep's writing-mode mirror check (mirror-v1, 2026-10-27,
 * docs/design/2026-10-27-sweep-writing-mode-mirror.md).
 *
 * <p>
 * The other sweep checks see only how a layout breaks (lost, off the paper, duplicated, thrown), never where things
 * should be, so a wrong margin, alignment or cell size passes them. Instead of a right answer this uses the same
 * document in the other writing mode: one abstract document, written in logical directions, is written out twice,
 * horizontal-tb and vertical-rl ({@link Family#ROTATE}: the page turned a quarter clockwise), or horizontal-tb with
 * vertical-lr islands and its transpose, vertical-lr with horizontal-tb islands ({@link Family#TRANSPOSE}). Every
 * declaration is written as the logical property or as the physical one the element's own writing mode maps it to,
 * chosen at random per variant, so both variants lay out the same and any difference is a defect of one of them.
 * </p>
 *
 * <p>
 * Text is ASCII tokens set {@code text-orientation: sideways}. A vertical line is laid out on its central baseline even
 * so (a line of horizontal writing on its alphabetic one), so nothing whose place in a line depends on the baseline is
 * generated: no font sizes mixed in a line, inline-blocks or inline images. A transpose also moves the line's over side
 * to its other edge, so the transpose family aligns cells only in the middle.
 * </p>
 */
final class MirrorDocumentGenerator {
	static final int VERSION = 1;

	enum Family {
		ROTATE, TRANSPOSE
	}

	/** A writing mode and the physical names of its logical sides and sizes (direction ltr). */
	enum Mode {
		TB("horizontal-tb", "top", "bottom", "left", "right", "width", "height"),
		RL("vertical-rl", "right", "left", "top", "bottom", "height", "width"),
		LR("vertical-lr", "left", "right", "top", "bottom", "height", "width");

		final String css, blockStart, blockEnd, inlineStart, inlineEnd, inlineSize, blockSize;

		Mode(final String css, final String blockStart, final String blockEnd, final String inlineStart,
				final String inlineEnd, final String inlineSize, final String blockSize) {
			this.css = css;
			this.blockStart = blockStart;
			this.blockEnd = blockEnd;
			this.inlineStart = inlineStart;
			this.inlineEnd = inlineEnd;
			this.inlineSize = inlineSize;
			this.blockSize = blockSize;
		}

		String side(final String logical) {
			return switch (logical) {
			case "block-start" -> this.blockStart;
			case "block-end" -> this.blockEnd;
			case "inline-start" -> this.inlineStart;
			case "inline-end" -> this.inlineEnd;
			default -> throw new IllegalArgumentException(logical);
			};
		}

		String size(final String logical) {
			return switch (logical) {
			case "inline" -> this.inlineSize;
			case "block" -> this.blockSize;
			default -> throw new IllegalArgumentException(logical);
			};
		}
	}

	/**
	 * @param width     content width of the horizontal variant's page (pt), its height in the vertical variant
	 * @param height    content height of the horizontal variant's page (pt)
	 * @param confined  the tokens of each cell of an auto table, to be drawn inside the cell's frame
	 */
	record Document(int seed, Family family, String horizontal, String vertical, double width, double height,
			List<String> tokens, List<Set<String>> confined) {
	}

	private static final String[] SIDES = { "block-start", "block-end", "inline-start", "inline-end" };
	private static final String[] PHYSICAL = { "top", "right", "bottom", "left" };

	/** A logical property: a box side ({@code margin-block-start}, {@code border-inline-end-width}) or a size. */
	private static final Pattern LOGICAL = Pattern
			.compile("(margin|padding|border|inset)-(block|inline)-(start|end)(-width)?|(min-|max-)?(inline|block)-size");

	/** An element of the abstract document; style keys are logical properties. */
	private static final class Node {
		final String tag;
		final String attrs;
		final Map<String, String> style = new LinkedHashMap<>();
		final List<Object> children = new ArrayList<>();
		/** Turns to the other axis (transpose family). */
		boolean orthogonal;

		Node(final String tag, final String attrs) {
			this.tag = tag;
			this.attrs = attrs;
		}

		Node css(final String property, final String value) {
			this.style.put(property, value);
			return this;
		}

		Node add(final Object child) {
			this.children.add(child);
			return this;
		}
	}

	private static final class Gen {
		final Random r;
		final Family family;
		final List<String> tokens = new ArrayList<>();
		final List<Set<String>> confined = new ArrayList<>();
		int elements;

		Gen(final Random r, final Family family) {
			this.r = r;
			this.family = family;
		}

		boolean rotate() {
			return this.family == Family.ROTATE;
		}

		boolean chance(final int percent) {
			return this.r.nextInt(100) < percent;
		}

		String pt(final int from, final int to) {
			return (from + this.r.nextInt(to - from + 1)) + "pt";
		}

		String token() {
			final String t = "T" + (this.tokens.size() + 1);
			this.tokens.add(t);
			return t;
		}

		/** Tokens with spaces, now and then a line break, into {@code into}. */
		void words(final Node into, final int n, final boolean breaks) {
			for (int i = 0; i < n; ++i) {
				if (i > 0) {
					into.add(breaks && this.chance(12) ? new Node("br", "") : " ");
				}
				into.add(this.token());
			}
		}

		/** Margins, padding, borders and a background, each now and then. */
		void box(final Node n, final int percent) {
			for (final String side : SIDES) {
				if (this.chance(percent)) {
					n.css("margin-" + side, this.chance(85) ? this.pt(0, 12) : "auto");
				}
			}
			for (final String side : SIDES) {
				if (this.chance(percent)) {
					n.css("padding-" + side, this.pt(0, 10));
				}
			}
			if (this.chance(percent)) {
				n.css("border-style", "solid");
				for (final String side : SIDES) {
					n.css("border-" + side + "-width", this.pt(0, 4));
				}
			}
			if (this.chance(percent)) {
				n.css("background", "#ddd");
			}
		}

		void blocks(final Node parent, final int depth) {
			final int n = 1 + this.r.nextInt(depth == 0 ? 6 : 3);
			for (int i = 0; i < n && this.elements < 70; ++i) {
				this.block(parent, depth);
			}
		}

		void block(final Node parent, final int depth) {
			++this.elements;
			final int kind = this.r.nextInt(depth >= 3 ? 3 : 15);
			switch (kind) {
			case 0, 1, 2 -> parent.add(this.paragraph());
			case 3 -> {
				final Node div = new Node("div", "");
				this.box(div, 40);
				if (this.chance(25)) {
					div.css("inline-size", this.chance(70) ? this.pt(30, 200) : (20 + this.r.nextInt(81)) + "%");
				}
				if (this.chance(15)) {
					div.css("block-size", this.pt(20, 120));
				}
				this.blocks(div, depth + 1);
				parent.add(div);
			}
			case 4 -> parent.add(this.floatBox(depth));
			case 5, 6 -> parent.add(this.table(depth));
			case 7 -> parent.add(this.list());
			case 8 -> {
				final Node div = new Node("div", "").css("column-count", String.valueOf(2 + this.r.nextInt(2)))
						.css("column-gap", this.pt(0, 12));
				this.box(div, 20);
				this.blocks(div, depth + 1);
				parent.add(div);
			}
			case 9 -> parent.add(this.flex(depth));
			case 10 -> parent.add(this.grid(depth));
			case 11 -> parent.add(this.image());
			case 12 -> parent.add(this.positioned(depth));
			case 13 -> {
				final Node div = new Node("div", "");
				if (this.chance(40)) {
					div.css("break-before", "page");
				}
				if (this.chance(40)) {
					div.css("break-inside", "avoid");
				}
				if (this.chance(40)) {
					div.css("block-size", this.pt(20, 80)).css("overflow", "hidden");
				}
				this.box(div, 30);
				this.blocks(div, depth + 1);
				parent.add(div);
			}
			default -> {
				if (!this.rotate()) {
					// An island of the other axis (transpose family only).
					final Node div = new Node("div", "");
					div.orthogonal = true;
					this.box(div, 30);
					if (this.chance(50)) {
						div.css("block-size", this.pt(20, 160));
					}
					if (this.chance(30)) {
						div.css("inline-size", this.pt(30, 200));
					}
					this.blocks(div, depth + 1);
					parent.add(div);
				} else {
					parent.add(this.paragraph());
				}
			}
			}
		}

		Node paragraph() {
			final Node p = new Node("p", "");
			this.box(p, 25);
			if (this.chance(40)) {
				p.css("text-align", new String[] { "start", "end", "center", "justify", "line-left", "line-right" }[this.r
						.nextInt(6)]);
			}
			if (this.chance(20)) {
				p.css("clear", new String[] { "left", "right", "both" }[this.r.nextInt(3)]);
			}
			this.words(p, 2 + this.r.nextInt(24), true);
			return p;
		}

		Node floatBox(final int depth) {
			final Node f = new Node("div", "").css("float", this.chance(50) ? "line-left" : "line-right");
			this.box(f, 30);
			f.css("inline-size", this.chance(75) ? this.pt(20, 120) : (20 + this.r.nextInt(61)) + "%");
			if (this.chance(25)) {
				f.css("block-size", this.pt(15, 100));
			}
			if (depth < 2 && this.chance(30)) {
				this.blocks(f, depth + 1);
			} else {
				f.add(this.paragraph());
			}
			return f;
		}

		Node list() {
			// Numbered: a vertical list's disc marker keeps its horizontal box (10x7pt at 10pt), its advance 3pt short of
			// the horizontal one's (2026-10-27, recorded only).
			final Node l = new Node(this.chance(50) ? "ul" : "ol", "").css("list-style-type",
					this.chance(50) ? "decimal" : "lower-alpha");
			this.box(l, 20);
			for (int i = 0, n = 1 + this.r.nextInt(3); i < n; ++i) {
				final Node li = new Node("li", "");
				this.words(li, 1 + this.r.nextInt(8), true);
				l.add(li);
			}
			return l;
		}

		Node flex(final int depth) {
			final Node f = new Node("div", "").css("display", "flex");
			if (this.chance(40)) {
				f.css("flex-direction", this.chance(50) ? "column" : "row");
			}
			if (this.chance(40)) {
				f.css("flex-wrap", "wrap");
			}
			if (this.chance(40)) {
				f.css("gap", this.pt(0, 10));
			}
			if (this.chance(30)) {
				f.css("justify-content", new String[] { "start", "end", "center", "space-between" }[this.r.nextInt(4)]);
			}
			if (this.chance(30)) {
				f.css("align-items", new String[] { "start", "end", "center", "stretch" }[this.r.nextInt(4)]);
			}
			if (this.chance(25)) {
				f.css("block-size", this.pt(30, 150));
			}
			this.box(f, 20);
			for (int i = 0, n = 2 + this.r.nextInt(3); i < n; ++i) {
				final Node item = new Node("div", "").css("background", "#eee");
				if (this.chance(40)) {
					item.css("flex", (this.r.nextInt(3)) + " 1 " + this.pt(0, 60));
				}
				if (this.chance(25)) {
					item.css("inline-size", this.pt(20, 100));
				}
				this.box(item, 20);
				if (depth < 2 && this.chance(25)) {
					this.blocks(item, depth + 1);
				} else {
					this.words(item, 1 + this.r.nextInt(6), true);
				}
				f.add(item);
			}
			return f;
		}

		Node grid(final int depth) {
			final int columns = 1 + this.r.nextInt(3);
			final StringBuilder template = new StringBuilder();
			for (int i = 0; i < columns; ++i) {
				template.append(i == 0 ? "" : " ").append(this.chance(50) ? "1fr" : this.pt(20, 80));
			}
			final Node g = new Node("div", "").css("display", "grid").css("grid-template-columns", template.toString());
			if (this.chance(40)) {
				g.css("gap", this.pt(0, 10) + " " + this.pt(0, 10));
			}
			this.box(g, 20);
			for (int i = 0, n = 1 + this.r.nextInt(5); i < n; ++i) {
				final Node item = new Node("div", "").css("background", "#eee");
				this.box(item, 15);
				if (depth < 2 && this.chance(20)) {
					this.blocks(item, depth + 1);
				} else {
					this.words(item, 1 + this.r.nextInt(5), true);
				}
				g.add(item);
			}
			return g;
		}

		Node image() {
			final Node img = new Node("img", "src=\"" + RandomDocumentFuzzTest.RED_PNG_URI + "\" alt=\"\"")
					.css("display", "block").css("inline-size", this.pt(5, 80)).css("block-size", this.pt(5, 80));
			if (this.chance(25)) {
				img.css("float", this.chance(50) ? "line-left" : "line-right");
			}
			this.box(img, 35);
			return img;
		}

		Node positioned(final int depth) {
			final Node rel = new Node("div", "").css("position", "relative");
			if (this.chance(40)) {
				rel.css("inset-block-start", this.pt(-10, 10)).css("inset-inline-start", this.pt(-10, 10));
			}
			this.box(rel, 20);
			rel.add(this.paragraph());
			final Node abs = new Node("div", "").css("position", "absolute").css("background", "#ccc")
					.css("inline-size", this.pt(10, 80));
			abs.css(this.chance(50) ? "inset-block-start" : "inset-block-end", this.pt(0, 30));
			abs.css(this.chance(50) ? "inset-inline-start" : "inset-inline-end", this.pt(0, 30));
			this.words(abs, 1 + this.r.nextInt(4), true);
			rel.add(abs);
			return rel;
		}

		Node table(final int depth) {
			final boolean confine = this.chance(50);
			final Node t = new Node("table", "");
			final boolean fixed = !confine && this.chance(30);
			if (fixed) {
				t.css("table-layout", "fixed").css("inline-size", this.pt(60, 240));
			}
			if (!confine && this.chance(30)) {
				t.css("border-collapse", "collapse");
			} else if (confine) {
				// Each cell draws its own frame (border-collapse is inherited from a table around this one)
				t.css("border-collapse", "separate");
			}
			if (!"collapse".equals(t.style.get("border-collapse")) && this.chance(50)) {
				t.css("border-spacing", this.pt(0, 6) + " " + this.pt(0, 6));
			}
			for (final String side : SIDES) {
				if (this.chance(30)) {
					t.css("margin-" + side, this.pt(0, 12));
				}
			}
			if (this.chance(15)) {
				t.css("margin-inline-start", "auto").css("margin-inline-end", "auto");
			}
			if (this.chance(20)) {
				final Node caption = new Node("caption", "");
				if (this.chance(50)) {
					caption.css("caption-side", "bottom");
				}
				this.words(caption, 1 + this.r.nextInt(4), false);
				t.add(caption);
			}
			final int rows = 1 + this.r.nextInt(3), columns = 1 + this.r.nextInt(3);
			for (int row = 0; row < rows; ++row) {
				final Node tr = new Node("tr", "");
				for (int column = 0; column < columns; ++column) {
					final boolean span = column + 1 < columns && this.chance(10);
					final Node cell = new Node(this.chance(20) ? "th" : "td", span ? "colspan=\"2\"" : "");
					if (span) {
						++column;
					}
					cell.css("border-style", "solid");
					for (final String side : SIDES) {
						cell.css("border-" + side + "-width", this.chance(70) ? "1pt" : this.pt(0, 4));
						if (this.chance(40)) {
							cell.css("padding-" + side, this.pt(0, 10));
						}
					}
					if (this.rotate() ? this.chance(50) : this.chance(20)) {
						cell.css("vertical-align", this.rotate()
								? new String[] { "top", "middle", "bottom", "baseline" }[this.r.nextInt(4)]
								: "middle");
					}
					if (fixed && this.chance(30)) {
						cell.css("inline-size", this.pt(20, 80));
					}
					final int first = this.tokens.size();
					if (!this.rotate() && this.chance(35)) {
						// Content of the other axis, on more than one line now and then (triage §113).
						if (this.chance(50)) {
							cell.orthogonal = true;
							this.words(cell, 1 + this.r.nextInt(4), true);
						} else {
							final Node island = new Node("div", "");
							island.orthogonal = true;
							this.words(island, 1 + this.r.nextInt(4), true);
							cell.add(island);
						}
					} else if (!confine && depth < 2 && this.chance(15)) {
						this.blocks(cell, depth + 1);
					} else {
						this.words(cell, 1 + this.r.nextInt(5), true);
					}
					if (confine && first < this.tokens.size()) {
						this.confined.add(Set.copyOf(this.tokens.subList(first, this.tokens.size())));
					}
					tr.add(cell);
				}
				t.add(tr);
			}
			return t;
		}
	}

	static Document generate(final int seed) {
		final Random r = new Random(0x6d6972726f72L ^ seed * 0x9E3779B97F4A7C15L);
		final Family family = r.nextInt(3) == 0 ? Family.TRANSPOSE : Family.ROTATE;
		final Gen g = new Gen(r, family);
		final Node body = new Node("body", "");
		g.blocks(body, 0);
		final int width = 120 + r.nextInt(281), height = 120 + r.nextInt(381);
		final int[] margin = new int[4];
		for (int i = 0; i < 4; ++i) {
			margin[i] = r.nextInt(4) * 5;
		}
		final int font = 7 + r.nextInt(6);
		final double line = Math.round(font * (1.2 + r.nextInt(4) * 0.1) * 2) / 2.0;
		final String horizontal = write(seed, body, Mode.TB, Mode.LR, width, height, margin, font, line, 1);
		final String vertical;
		if (family == Family.ROTATE) {
			// A quarter turn clockwise: the left edge goes to the top, the top to the right.
			vertical = write(seed, body, Mode.RL, Mode.TB, height, width,
					new int[] { margin[3], margin[0], margin[1], margin[2] }, font, line, 2);
		} else {
			// The transpose: top and left trade places, as do bottom and right.
			vertical = write(seed, body, Mode.LR, Mode.TB, height, width,
					new int[] { margin[3], margin[2], margin[1], margin[0] }, font, line, 2);
		}
		return new Document(seed, family, horizontal, vertical, width, height, List.copyOf(g.tokens),
				List.copyOf(g.confined));
	}

	/**
	 * @param margin top, right, bottom and left page margins
	 */
	private static String write(final int seed, final Node body, final Mode root, final Mode island, final int width,
			final int height, final int[] margin, final int font, final double line, final int variant) {
		final StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\" />\n<title>fuzz mirror-v").append(VERSION)
				.append(' ').append(seed).append(' ').append(root.css).append("</title>\n<style>\n");
		s.append("@page{size:").append(width + margin[1] + margin[3]).append("pt ").append(height + margin[0] + margin[2])
				.append("pt;margin:").append(margin[0]).append("pt ").append(margin[1]).append("pt ").append(margin[2])
				.append("pt ").append(margin[3]).append("pt}\n");
		s.append("html{writing-mode:").append(root.css).append(";text-orientation:sideways}\n");
		s.append("body{margin:0;font:").append(font).append("pt/").append(line).append("pt serif}\n");
		s.append("</style></head>\n");
		final Random declarations = new Random(seed * 31L + variant);
		write(s, body, root, root, island, declarations);
		s.append("\n</html>\n");
		return s.toString();
	}

	private static void write(final StringBuilder s, final Node n, final Mode mode, final Mode root, final Mode island,
			final Random d) {
		final Mode own = n.orthogonal ? (mode == root ? island : root) : mode;
		s.append('<').append(n.tag);
		if (!n.attrs.isEmpty()) {
			s.append(' ').append(n.attrs);
		}
		final String style = style(n, own, d);
		if (!style.isEmpty()) {
			s.append(" style=\"").append(style).append('"');
		}
		if (n.tag.equals("br") || n.tag.equals("img")) {
			s.append(" />");
			return;
		}
		s.append('>');
		for (final Object child : n.children) {
			if (child instanceof Node c) {
				write(s, c, own, root, island, d);
			} else {
				s.append(child);
			}
		}
		s.append("</").append(n.tag).append('>');
		if (!n.tag.equals("span")) {
			s.append('\n');
		}
	}

	/** The declarations of an element in its own writing mode, each logical or physical at random. */
	private static String style(final Node n, final Mode m, final Random d) {
		final StringBuilder s = new StringBuilder();
		if (n.orthogonal) {
			s.append("writing-mode:").append(m.css).append(';');
		}
		final Set<String> done = new HashSet<>();
		for (final String box : new String[] { "margin", "padding" }) {
			boolean all = true;
			for (final String side : SIDES) {
				all &= n.style.containsKey(box + "-" + side);
			}
			if (all && d.nextInt(3) == 0) {
				// The physical shorthand (top right bottom left), as stylesheets written for one writing mode have it.
				s.append(box).append(':');
				for (int i = 0; i < 4; ++i) {
					for (final String side : SIDES) {
						if (m.side(side).equals(PHYSICAL[i])) {
							s.append(i == 0 ? "" : " ").append(n.style.get(box + "-" + side));
							done.add(box + "-" + side);
						}
					}
				}
				s.append(';');
			}
		}
		for (final Map.Entry<String, String> e : n.style.entrySet()) {
			if (!done.contains(e.getKey())) {
				s.append(declaration(e.getKey(), e.getValue(), m, d)).append(';');
			}
		}
		return s.toString();
	}

	private static String declaration(final String property, final String value, final Mode m, final Random d) {
		final Matcher l = LOGICAL.matcher(property);
		if (l.matches()) {
			if (d.nextBoolean()) {
				return property + ':' + value;
			}
			if (l.group(1) != null) {
				final String side = m.side(l.group(2) + "-" + l.group(3));
				final String name = l.group(1).equals("inset") ? side
						: l.group(1) + "-" + side + (l.group(4) == null ? "" : l.group(4));
				return name + ':' + value;
			}
			return (l.group(5) == null ? "" : l.group(5)) + m.size(l.group(6)) + ':' + value;
		}
		return switch (property) {
		case "float" -> "float:" + (value.equals("line-left") ? d.nextBoolean() ? "left" : "inline-start"
				: d.nextBoolean() ? "right" : "inline-end");
		case "text-align" -> "text-align:"
				+ (value.equals("line-left") ? "left" : value.equals("line-right") ? "right" : value);
		default -> property + ':' + value;
		};
	}
}
