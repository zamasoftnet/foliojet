package net.zamasoft.foliojet.layout.text.bidi;

import java.util.List;

import net.zamasoft.foliojet.layout.box.impl.InlineBox;

/**
 * 整形済み run/cluster と並行して Folio 側に保持する UBA メタデータ。
 * pdfg2d の Text にはレイアウト固有情報を持ち込まない。
 */
public record BidiSlice(long paragraphId, int syntheticStart, int syntheticLimit, byte paragraphLevel,
		byte level, List<InlineBox> inlineAncestry) {
	public BidiSlice {
		inlineAncestry = List.copyOf(inlineAncestry);
	}
}
