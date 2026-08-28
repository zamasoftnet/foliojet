package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;

/**
 * Grid線名の数値化です(css-grid-1 §8.3、2026-08-29)。boxに依存しない
 * 純粋計算。{@code grid-template-columns/rows}の{@code [name]}と、
 * {@code grid-template-areas}が作る暗黙の{@code name-start}/{@code name-end}
 * を1つの線名表(zero-based線index→名前集合)にまとめ、
 * {@link GridItemSpec}の名前付き{@link GridLineValue}を整数線番号・
 * {@code span N}・{@code auto}へ写す。{@link GridPlacementResolver}は
 * 数値だけを扱う(役割分離)。
 *
 * <p>
 * 仕様の写し: {@code name}単独はstart側で{@code name-start}、end側で
 * {@code name-end}を先に探し、無ければ{@code name}そのもの
 * ({@code 1 name})。{@code N name}はN番目のその名の線(負は末尾から)。
 * {@code span N name}は反対側の確定線から数えてN番目のその名の線、
 * 無ければ{@code span N}。<b>近似</b>: 仕様は「足りない線は明示グリッドの
 * 外側の暗黙線が全てその名を持つ」と定めるが、未定義の名前は
 * {@code auto}へ落とす(印刷では未定義名で暗黙列を作るより自動配置の
 * ほうが見た目が崩れにくい。記録)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridLineNameResolver {

	private GridLineNameResolver() {
		// static
	}

	/**
	 * 名前付き線を数値化した{@link GridItemSpec}を返します(名前が無ければ
	 * そのまま)。
	 *
	 * @param spec        item指定
	 * @param columnLines 列軸の線名表(zero-based線index→名前)
	 * @param rowLines    行軸の線名表
	 */
	public static GridItemSpec resolve(final GridItemSpec spec, final List<List<String>> columnLines,
			final List<List<String>> rowLines) {
		if (!spec.columnStart().isNamed() && !spec.columnEnd().isNamed() && !spec.rowStart().isNamed()
				&& !spec.rowEnd().isNamed()) {
			return spec;
		}
		final GridLineValue[] cols = resolveAxis(spec.columnStart(), spec.columnEnd(), columnLines);
		final GridLineValue[] rows = resolveAxis(spec.rowStart(), spec.rowEnd(), rowLines);
		return GridItemSpec.of(cols[0], cols[1], rows[0], rows[1], spec.justifySelf(), spec.alignSelf());
	}

	/** 1軸の[start, end]を数値化します。 */
	static GridLineValue[] resolveAxis(final GridLineValue start, final GridLineValue end,
			final List<List<String>> lines) {
		// spanでない側を先に確定し、span N nameはその反対側から数える
		GridLineValue s = start.isNamed() && !start.isSpan() ? resolveLine(start, lines, true) : start;
		GridLineValue e = end.isNamed() && !end.isSpan() ? resolveLine(end, lines, false) : end;
		if (start.isNamed() && start.isSpan()) {
			s = resolveSpan(start, e, lines, true);
		}
		if (end.isNamed() && end.isSpan()) {
			e = resolveSpan(end, s, lines, false);
		}
		return new GridLineValue[] { s, e };
	}

	/** {@code name}単独・{@code N name}の数値化(1-based線番号、無ければauto)。 */
	private static GridLineValue resolveLine(final GridLineValue value, final List<List<String>> lines,
			final boolean startSide) {
		final String name = value.getName();
		if (value.getNumber() == 0) {
			// name単独: name-start/name-endを先に、無ければname
			int index = nth(lines, name + (startSide ? "-start" : "-end"), 1);
			if (index < 0) {
				index = nth(lines, name, 1);
			}
			return index < 0 ? GridLineValue.AUTO_VALUE : GridLineValue.line(index + 1);
		}
		final int index = nth(lines, name, value.getNumber());
		return index < 0 ? GridLineValue.AUTO_VALUE : GridLineValue.line(index + 1);
	}

	/**
	 * {@code span N name}: 反対側が確定していればそこからN番目のその名の線
	 * (見つからなければ{@code span N})。
	 */
	private static GridLineValue resolveSpan(final GridLineValue value, final GridLineValue opposite,
			final List<List<String>> lines, final boolean startSide) {
		final int count = Math.max(1, value.getNumber());
		if (opposite.isAuto() || opposite.isSpan() || opposite.isNamed()) {
			return GridLineValue.span(count);
		}
		final int from = opposite.getNumber() - 1; // zero-based線index(正の番号のみ数値化済み)
		if (from < 0) {
			return GridLineValue.span(count);
		}
		int found = 0;
		if (startSide) {
			// endから前方(小さいindex)へ
			for (int i = from - 1; i >= 0; --i) {
				if (i < lines.size() && lines.get(i).contains(value.getName()) && ++found == count) {
					return GridLineValue.line(i + 1);
				}
			}
		} else {
			for (int i = from + 1; i < lines.size(); ++i) {
				if (lines.get(i).contains(value.getName()) && ++found == count) {
					return GridLineValue.line(i + 1);
				}
			}
		}
		return GridLineValue.span(count);
	}

	/** N番目(1-based。負は末尾から)のnameを持つ線のzero-based index(無ければ-1)。 */
	private static int nth(final List<List<String>> lines, final String name, final int n) {
		if (n > 0) {
			int found = 0;
			for (int i = 0; i < lines.size(); ++i) {
				if (lines.get(i).contains(name) && ++found == n) {
					return i;
				}
			}
			return -1;
		}
		int found = 0;
		for (int i = lines.size() - 1; i >= 0; --i) {
			if (lines.get(i).contains(name) && --found == n) {
				return i;
			}
		}
		return -1;
	}
}
