package jp.cssj.test.unit.displaylist;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.test.unit.displaylist.MirrorDocumentGenerator.Family;

/**
 * Compares the two layouts of a writing-mode mirror document (mirror-v1, 2026-10-27, {@link MirrorDocumentGenerator}):
 * the same drawings in the same order on the same pages, each box of the horizontal variant turned or transposed onto
 * the vertical one. Text is compared by the box of its run; ascent and descent are not, a vertical line having its own.
 */
final class MirrorComparison {
	/** Dumps round to 0.01pt, and the two variants sum their lengths in different orders. */
	private static final double TOLERANCE = 0.05;

	private static final Pattern ITEM = Pattern.compile("^\\s*x=(-?[\\d.]+) y=(-?[\\d.]+) (.*)$");
	private static final Pattern SIZE = Pattern.compile("w=([\\d.]+) h=([\\d.]+)");
	private static final Pattern CLIP = Pattern.compile("clip=\\[(-?[\\d.]+) (-?[\\d.]+) ([\\d.]+) ([\\d.]+)\\]");
	private static final Pattern KIND = Pattern.compile("^(artifact )?(\\w+)(?:\\[\"([^\"]*)\")?");

	/** One line of a page's dump: a drawing (box and clip may be null) or a drawer. */
	record Item(String line, String kind, double[] box, double[] clip) {
	}

	private MirrorComparison() {
	}

	static List<Item> parse(final String dump) {
		final List<Item> items = new ArrayList<>();
		for (final String line : dump.split("\n")) {
			if (line.isBlank()) {
				continue;
			}
			final Matcher m = ITEM.matcher(line);
			if (!m.matches()) {
				items.add(new Item(line, line.trim(), null, null));
				continue;
			}
			final String rest = m.group(3);
			final Matcher k = KIND.matcher(rest);
			final String kind = k.find()
					? (k.group(1) == null ? "" : "artifact ") + k.group(2) + (k.group(3) == null ? "" : "\"" + k.group(3) + "\"")
					: rest;
			double[] box = null;
			final Matcher s = SIZE.matcher(rest);
			if (s.find()) {
				box = new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
						Double.parseDouble(s.group(1)), Double.parseDouble(s.group(2)) };
			}
			double[] clip = null;
			final Matcher c = CLIP.matcher(rest);
			if (c.find()) {
				clip = new double[] { Double.parseDouble(c.group(1)), Double.parseDouble(c.group(2)),
						Double.parseDouble(c.group(3)), Double.parseDouble(c.group(4)) };
			}
			items.add(new Item(line, kind, box, clip));
		}
		return items;
	}

	/**
	 * A box of the horizontal variant in the vertical one.
	 *
	 * @param height content height of the horizontal variant's page, the vertical one's content width
	 */
	static double[] map(final Family family, final double height, final double[] b) {
		return family == Family.ROTATE ? new double[] { height - b[1] - b[3], b[0], b[3], b[2] }
				: new double[] { b[1], b[0], b[3], b[2] };
	}

	/**
	 * The first difference of two layouts, or null.
	 *
	 * @param horizontal page dumps of the horizontal variant
	 * @param vertical   page dumps of the vertical variant
	 */
	static String compare(final Family family, final double height, final List<String> horizontal,
			final List<String> vertical) {
		if (horizontal.size() != vertical.size()) {
			return "頁数 " + horizontal.size() + " と " + vertical.size();
		}
		for (int p = 0; p < horizontal.size(); ++p) {
			final List<Item> h = parse(horizontal.get(p)), v = parse(vertical.get(p));
			for (int i = 0; i < Math.min(h.size(), v.size()); ++i) {
				final Item a = h.get(i), b = v.get(i);
				final String where = "page-" + (p + 1) + " の " + (i + 1) + " 番目: ";
				if (!a.kind.equals(b.kind)) {
					return where + "「" + a.line.trim() + "」と「" + b.line.trim() + "」";
				}
				final String box = differs(family, height, a.box, b.box);
				if (box != null) {
					return where + a.kind + " " + box;
				}
				final String clip = differs(family, height, a.clip, b.clip);
				if (clip != null) {
					return where + a.kind + " の clip " + clip;
				}
			}
			if (h.size() != v.size()) {
				return "page-" + (p + 1) + " の描画の数 " + h.size() + " と " + v.size();
			}
		}
		return null;
	}

	private static String differs(final Family family, final double height, final double[] a, final double[] b) {
		if (a == null || b == null) {
			return a == b ? null : "片方にだけ寸法 " + format(a) + " と " + format(b);
		}
		final double[] expected = map(family, height, a);
		for (int j = 0; j < 4; ++j) {
			if (Math.abs(expected[j] - b[j]) > TOLERANCE) {
				return "横 " + format(a) + " を写すと " + format(expected) + "、縦は " + format(b);
			}
		}
		return null;
	}

	/**
	 * The first token of a cell of an auto table drawn outside its cell, or null (triage §113: the content of an
	 * orthogonal cell, sized as one line, ran into the next cell or out of the table). A cell's frame on a page is the
	 * smallest frame drawn around its first token there: its other tokens are in it, and no two cells share it (a line
	 * run whole into the next cell is in that cell's frame). Content of another box run over the table, out of a box
	 * the author sized, does not count.
	 *
	 * @param cells the tokens of each cell
	 */
	static String confinement(final List<String> pages, final List<Set<String>> cells) {
		for (int p = 0; p < pages.size(); ++p) {
			final List<Item> items = parse(pages.get(p));
			final List<double[]> frames = new ArrayList<>();
			for (final Item item : items) {
				if (item.box != null && item.kind.equals("BackgroundBorder")) {
					frames.add(item.box);
				}
			}
			final java.util.Map<double[], Integer> owner = new java.util.IdentityHashMap<>();
			final java.util.Map<Integer, double[]> frameOf = new java.util.HashMap<>();
			for (final Item item : items) {
				if (item.box == null || !item.kind.startsWith("Text\"")) {
					continue;
				}
				final String text = item.kind.substring(5, item.kind.length() - 1);
				int cell = -1;
				for (final String token : text.split(" ")) {
					for (int c = 0; cell == -1 && c < cells.size(); ++c) {
						if (cells.get(c).contains(token)) {
							cell = c;
						}
					}
				}
				if (cell == -1) {
					continue;
				}
				final String where = "page-" + (p + 1) + " の " + text + " " + format(item.box);
				final double[] own = frameOf.get(cell);
				if (own != null) {
					if (!inside(item.box, own)) {
						return where + " が同じセルの枠 " + format(own) + " の外";
					}
					continue;
				}
				final double[] frame = smallest(item.box, frames);
				if (frame == null) {
					return where + " がどのセルの枠にも入らない";
				}
				final Integer other = owner.putIfAbsent(frame, cell);
				if (other != null && other != cell) {
					return where + " が別のセル(" + cells.get(other) + ")の枠 " + format(frame) + " に入る";
				}
				frameOf.put(cell, frame);
			}
		}
		return null;
	}

	private static boolean inside(final double[] b, final double[] f) {
		return b[0] >= f[0] - TOLERANCE && b[1] >= f[1] - TOLERANCE && b[0] + b[2] <= f[0] + f[2] + TOLERANCE
				&& b[1] + b[3] <= f[1] + f[3] + TOLERANCE;
	}

	private static double[] smallest(final double[] b, final List<double[]> frames) {
		double[] found = null;
		for (final double[] f : frames) {
			if (inside(b, f) && (found == null || f[2] * f[3] < found[2] * found[3])) {
				found = f;
			}
		}
		return found;
	}

	private static String format(final double[] b) {
		return b == null ? "なし"
				: String.format(Locale.ROOT, "[%.2f %.2f %.2f %.2f]", b[0], b[1], b[2], b[3]);
	}
}
