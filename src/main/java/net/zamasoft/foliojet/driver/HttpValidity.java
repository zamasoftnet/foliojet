package net.zamasoft.foliojet.driver;

import net.zamasoft.zstream.resolver.SourceValidity;

// 2026-09-02 に MyHttpSourceResolver.java から分けた(本文は移しただけ。設計レビュー「10クラス 1,560行」)。
class HttpValidity implements SourceValidity {
	private static final long serialVersionUID = 0;

	private final long lastModified;

	HttpValidity(long lastModified) {
		this.lastModified = lastModified;
	}

	public Validity getValid() {
		return Validity.UNKNOWN;
	}

	public Validity getValid(SourceValidity validity) {
		if (!(validity instanceof HttpValidity)) {
			return Validity.UNKNOWN;
		}
		long other = ((HttpValidity) validity).lastModified;
		if (this.lastModified == -1 || other == -1) {
			return Validity.UNKNOWN;
		}
		return this.lastModified == other ? Validity.VALID : Validity.INVALID;
	}
}
