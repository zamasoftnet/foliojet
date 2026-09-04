package net.zamasoft.foliojet.layout.box.impl;

import java.util.List;

/** 親gridと子row subgridの一時的な接続情報です(2026-09-03)。 */
public record RowSubgridLink(int rowStart, int span, double parentRowGap, List<List<String>> rowLineNames,
		RowContributionSink sink) {
}
