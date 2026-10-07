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
import net.zamasoft.foliojet.ua.JsonText;

/**
 * Writes {@code index.json}, the top-level descriptor for EPUB Paged SVG (2026-09-02).
 *
 * <p>
 * Each item's output ({@code items/NNNN/}) is the same bundle as a standalone document conversion;
 * the {@code manifest.json} format is unchanged. Only this descriptor sits above them,
 * holding item order, cumulative page numbers, binding direction, table of contents, and metadata.
 * Design: {@code docs/epub-paged-svg-design.md} §3.
 * </p>
 */
final class PagedSvgIndex {
	private PagedSvgIndex() {
	}

	/** Item directory name. Fixed by position in the spine (excluded items also consume numbers). */
	static String itemPrefix(final int index) {
		return String.format(Locale.ROOT, "items/%04d/", index);
	}

	/**
	 * @param documents  description of the whole document set
	 * @param pageCounts completed item index → page count
	 * @param binding    binding direction ({@code left}/{@code right}/{@code single})
	 */
	static byte[] json(final DocumentSet documents, final Map<Integer, Integer> pageCounts, final String binding) {
		final StringBuilder json = new StringBuilder(1024);
		json.append("{\n  \"version\":1,\n  \"mediaType\":\"application/vnd.copper.paged-svg\",")
				.append("\n  \"composition\":\"epub\",\n  \"binding\":");
		json.append(JsonText.quoted(binding));
		json.append(",\n  \"pageProgressionDirection\":");
		json.append(JsonText.quoted(documents.pageProgressionDirection()));
		// Cumulative page numbers. Exclude skipped and unfinished items.
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
			json.append(JsonText.quoted(entry.getKey()));
			json.append(':');
			json.append(JsonText.quoted(entry.getValue()));
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
			json.append(JsonText.quoted(unit.idref() == null ? "" : unit.idref()));
			json.append(",\"uri\":");
			json.append(JsonText.quoted(path));
			json.append(",\"included\":").append(unit.included());
			final Integer pages = pageCounts.get(unit.index());
			if (unit.included() && pages != null) {
				json.append(",\"manifest\":");
				json.append(JsonText.quoted(itemPrefix(unit.index()) + "manifest.json"));
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
			json.append(JsonText.quoted(entry.label() == null ? "" : entry.label()));
			final URI uri = entry.uri();
			final String path = uri == null ? null : uri.getPath();
			if (path != null) {
				json.append(",\"uri\":");
				json.append(JsonText.quoted(path));
				final Integer item = pathToIndex.get(path);
				if (item != null) {
					json.append(",\"item\":").append(item);
				}
			}
			if (entry.fragment() != null) {
				json.append(",\"fragment\":");
				json.append(JsonText.quoted(entry.fragment()));
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
