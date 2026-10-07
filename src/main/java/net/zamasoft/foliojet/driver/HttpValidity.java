package net.zamasoft.foliojet.driver;

import net.zamasoft.zstream.resolver.SourceValidity;

// Split from MyHttpSourceResolver.java on 2026-09-02 (body only moved; design review: 10 classes, 1,560 lines).
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
