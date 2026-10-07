package net.zamasoft.foliojet.layout.box.impl;

import java.util.List;

/** Temporary connection information between a parent grid and a child row subgrid (2026-09-03). */
public record RowSubgridLink(int rowStart, int span, double parentRowGap, List<List<String>> rowLineNames,
		RowContributionSink sink) {
}
