package net.zamasoft.foliojet.driver;

import net.zamasoft.foliojet.ua.HttpStatusSource;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;

// Split from MyHttpSourceResolver.java on 2026-09-02 (body only moved; design review: 10 classes, 1,560 lines).
class MySource extends InputLimitedSource implements HttpStatusSource {
	final SourceResolver resolver;

	MySource(Source source, SourceResolver resolver, InputByteBudget budget) {
		super(source, budget);
		this.resolver = resolver;
	}

	public void release() {
		this.resolver.release(this.source);
	}

	@Override
	public int httpStatus() {
		return this.source instanceof HttpStatusSource http ? http.httpStatus() : -1;
	}
}
