package net.zamasoft.foliojet.ua.props;

import java.util.EnumSet;
import java.util.Set;

public enum OutputPdfVersion implements PropCode {
	V1_2("1.2"),

	V1_3("1.3"),

	V1_4("1.4"),

	V1_5("1.5"),

	V1_6("1.6"),

	V1_7("1.7"),

	V1_4A1("1.4A-1"),

	V1_4X1("1.4X-1"),

	/** PDF/X-3:2003 (ISO 15930-6, based on PDF 1.4). */
	V1_4X3("1.4X-3"),

	V1_7A2("1.7A-2"),

	V1_7A2U("1.7A-2u"),

	V1_7A2A("1.7A-2a"),

	V1_7A3("1.7A-3"),

	V1_7A3A("1.7A-3a"),

	V2_0A4("2.0A-4"),

	V1_6X4("1.6X-4"),

	V2_0X6("2.0X-6"),

	V1_7UA1("1.7UA-1"),

	V2_0UA2("2.0UA-2"),

	V2_0("2.0");

	private final String ident;

	private OutputPdfVersion(String ident) {
		this.ident = ident;
	}

	public String ident() {
		return this.ident;
	}

	private static final Set<OutputPdfVersion> EMBED_REQUIRED = EnumSet.of(V1_4A1, V1_4X1, V1_4X3, V1_7A2, V1_7A2U,
			V1_7A2A, V1_7A3, V1_7A3A, V2_0A4, V1_6X4, V2_0X6, V1_7UA1, V2_0UA2);

	private static final Set<OutputPdfVersion> TAGGING_REQUIRED = EnumSet.of(V1_7A2A, V1_7A3A, V1_7UA1, V2_0UA2);

	/**
	 * True for profiles requiring font embedding (PDF/A, PDF/X, PDF/UA).
	 */
	public boolean requiresFontEmbedding() {
		return EMBED_REQUIRED.contains(this);
	}

	/**
	 * Whether to keep bookmarks and links disabled by default (2026-10-04).
	 * PDF/UA-2 requires internal destinations to be structure destinations (SD)
	 * (ISO 14289-2 §8.8), but Copper bookmarks and internal links target page coordinates,
	 * so enabling them by default would violate conformance.
	 * Use explicitly specified values unchanged.
	 */
	public boolean keepsNavigationOffByDefault() {
		return this == V2_0UA2;
	}

	/**
	 * True for PDF/X profiles.
	 */
	public boolean isPdfX() {
		return this == V1_4X1 || this == V1_4X3 || this == V1_6X4 || this == V2_0X6;
	}

	/**
	 * Returns the warning name for PDF/X based on PDF 1.4 (X-1a, X-3); otherwise null.
	 * Neither permits transparency, link annotations, or encryption.
	 */
	public String pdf14PdfXName() {
		return switch (this) {
		case V1_4X1 -> "PDF/X-1a";
		case V1_4X3 -> "PDF/X-3";
		default -> null;
		};
	}

	/**
	 * True for profiles requiring logical structure (tagged PDF).
	 */
	public boolean requiresTagging() {
		return TAGGING_REQUIRED.contains(this);
	}
}
