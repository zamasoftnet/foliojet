package net.zamasoft.foliojet.xml.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;

public final class CharsetDetector {
	private static final int MAX_BUFF_SIZE = 8192;
	private static final Charset[] CHARSETS = { Charset.forName("ISO-2022-JP"), Charset.forName("x-eucJP-Open"),
			Charset.forName("Windows-31J"), Charset.forName("UTF-8"), };

	private CharsetDetector() {
		// unused
	}

	public static Charset detectCharset(InputStream in) throws IOException {
		in.mark(MAX_BUFF_SIZE);
		byte[] b = new byte[MAX_BUFF_SIZE];
		// Read until the sample is full or the input ends. One read() of a slow stream (a network body) may give only
		// what has arrived, and a sample that ends in the ASCII head of the document is taken for ISO-2022-JP. Until
		// 2026-11-11 the comment filter that sat on every HTML input filled each read, and this relied on it.
		int len = in.readNBytes(b, 0, b.length);
		in.reset();
		if (len == 0) {
			return CHARSETS[CHARSETS.length - 1];
		}
		ByteBuffer bbuff = ByteBuffer.wrap(b, 0, len);
		CharBuffer cbuff = CharBuffer.allocate(len);

		for (int i = 0; i < CHARSETS.length; ++i) {
			Charset cs = CHARSETS[i];
			CoderResult r = cs.newDecoder().decode(bbuff, cbuff, false);
			if (!r.isMalformed() && !r.isUnmappable()) {
				return cs;
			}
		}
		return CHARSETS[CHARSETS.length - 1];
	}
}
