package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zamasoft.foliojet.ua.MultiDocumentOutput.DocumentSet;
import net.zamasoft.foliojet.ua.MultiDocumentOutput.DocumentUnit;
import net.zamasoft.foliojet.ua.MultiDocumentOutput.TocEntry;

/**
 * EPUBのPaged SVGの上位仕様 {@code index.json} を書きます(2026-09-02)。
 *
 * <p>
 * 項目ごとの出力({@code items/NNNN/})は単一の文書を変換したときの
 * バンドルそのもので、{@code manifest.json}の形は変えない。これだけが
 * 上位にあり、項目の並び・累積のページ番号・綴じ方向・目次・メタデータを
 * 持つ。設計は{@code docs/epub-paged-svg-design.md} §3。
 * </p>
 */
final class PagedSvgIndex {
	private PagedSvgIndex() {
	}

	/** 項目のディレクトリ名。spine内の位置で固定(除外された項目も番号を消費する)。 */
	static String itemPrefix(final int index) {
		return String.format(Locale.ROOT, "items/%04d/", index);
	}

	/**
	 * @param documents  全体の記述
	 * @param pageCounts 組み終えた項目の位置→ページ数
	 * @param binding    綴じ方向({@code left}/{@code right}/{@code single})
	 */
	static byte[] json(final DocumentSet documents, final Map<Integer, Integer> pageCounts, final String binding) {
		final StringBuilder json = new StringBuilder(1024);
		json.append("{\n  \"version\":1,\n  \"mediaType\":\"application/vnd.copper.paged-svg\",")
				.append("\n  \"composition\":\"epub\",\n  \"binding\":");
		PagedSVGResources.quote(json, binding);
		json.append(",\n  \"pageProgressionDirection\":");
		PagedSVGResources.quote(json, documents.pageProgressionDirection());
		// 累積のページ番号。除外された項目と未完の項目は数えない
		int total = 0;
		final Map<Integer, Integer> firstPages = new HashMap<>();
		for (final DocumentUnit unit : documents.units()) {
			final Integer pages = pageCounts.get(unit.index());
			if (unit.included() && pages != null) {
				firstPages.put(unit.index(), total + 1);
				total += pages;
			}
		}
		json.append(",\n  \"pageCount\":").append(total);
		json.append(",\n  \"metadata\":{");
		int index = 0;
		for (final var entry : documents.metadata().entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			if (index++ != 0) {
				json.append(',');
			}
			json.append("\n    ");
			PagedSVGResources.quote(json, entry.getKey());
			json.append(':');
			PagedSVGResources.quote(json, entry.getValue());
		}
		if (index != 0) {
			json.append('\n');
		}
		json.append("  },\n  \"items\":[");
		final Map<String, Integer> pathToIndex = new HashMap<>();
		index = 0;
		for (final DocumentUnit unit : documents.units()) {
			if (index++ != 0) {
				json.append(',');
			}
			final String path = unit.uri() == null ? "" : unit.uri().getPath();
			pathToIndex.putIfAbsent(path, unit.index());
			json.append("\n    {\"index\":").append(unit.index()).append(",\"idref\":");
			PagedSVGResources.quote(json, unit.idref() == null ? "" : unit.idref());
			json.append(",\"uri\":");
			PagedSVGResources.quote(json, path);
			json.append(",\"included\":").append(unit.included());
			final Integer pages = pageCounts.get(unit.index());
			if (unit.included() && pages != null) {
				json.append(",\"manifest\":");
				PagedSVGResources.quote(json, itemPrefix(unit.index()) + "manifest.json");
				json.append(",\"firstPage\":").append(firstPages.get(unit.index())).append(",\"pageCount\":")
						.append(pages);
			}
			json.append('}');
		}
		json.append("\n  ],\n  \"toc\":[");
		appendToc(json, documents.toc(), pathToIndex, 2);
		json.append("\n  ]\n}\n");
		return json.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void appendToc(final StringBuilder json, final List<TocEntry> entries,
			final Map<String, Integer> pathToIndex, final int depth) {
		if (entries == null) {
			return;
		}
		for (int i = 0; i < entries.size(); ++i) {
			final TocEntry entry = entries.get(i);
			if (i != 0) {
				json.append(',');
			}
			json.append('\n').append("  ".repeat(depth)).append("{\"title\":");
			PagedSVGResources.quote(json, entry.label() == null ? "" : entry.label());
			final URI uri = entry.uri();
			final String path = uri == null ? null : uri.getPath();
			if (path != null) {
				json.append(",\"uri\":");
				PagedSVGResources.quote(json, path);
				final Integer item = pathToIndex.get(path);
				if (item != null) {
					json.append(",\"item\":").append(item);
				}
			}
			if (entry.fragment() != null) {
				json.append(",\"fragment\":");
				PagedSVGResources.quote(json, entry.fragment());
			}
			if (entry.children() != null && !entry.children().isEmpty()) {
				json.append(",\"children\":[");
				appendToc(json, entry.children(), pathToIndex, depth + 1);
				json.append('\n').append("  ".repeat(depth)).append(']');
			}
			json.append('}');
		}
	}
}
