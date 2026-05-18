package net.zamasoft.foliojet.epub.util;

import java.lang.Character.UnicodeBlock;

import net.zamasoft.foliojet.epub.EPubFile;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class WritingModeHelper {
	static final AttributesImpl TCY = new AttributesImpl();
	static {
		TCY.addAttribute("", "class", "class", "CDATA", "x-epub-tcy pre");
	}

	public static void characters(char[] ch, int off, int len, ContentHandler handler) throws SAXException {
		toSimpleKansuji(ch, off, len);
		int end = off + len;
		int run = 0;
		for (int i = off; i < end; ++i) {
			char c = ch[i];
			if (c >= '0' && c <= '9') {
				++run;
				continue;
			}
			if (run == 2) {
				UnicodeBlock b = UnicodeBlock.of(c);
				if (!(b == UnicodeBlock.CJK_COMPATIBILITY || b == UnicodeBlock.CJK_COMPATIBILITY_FORMS
						|| b == UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
						|| b == UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
						|| b == UnicodeBlock.CJK_RADICALS_SUPPLEMENT || b == UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
						|| b == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
						|| b == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
						|| b == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B || b == UnicodeBlock.LETTERLIKE_SYMBOLS
						|| b == UnicodeBlock.HIRAGANA || b == UnicodeBlock.KATAKANA
						|| b == UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
						|| b == UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS)) {
					++run;
					continue;
				}
				int tlen = i - off - run;
				if (tlen > 0) {
					toFullWidthNumber(ch, off, tlen);
					handler.characters(ch, off, tlen);
				}
				handler.startElement(EPubFile.XHTML_NS, "span", "span", TCY);
				handler.characters(ch, i - run, run);
				handler.endElement(EPubFile.XHTML_NS, "span", "span");
				len -= i - off;
				off = i;
			}
			run = 0;
		}
		if (len == 0) {
			return;
		}
		toFullWidthNumber(ch, off, len);
		handler.characters(ch, off, len);
	}

	private static final String from = "０１２３４５６７８９“”．－";

	private static final String to = "〇一二三四五六七八九〝〟・―";

	public static void toSimpleKansuji(char[] ch, int off, int len) {
		int end = off + len;
		for (int i = off; i < end; ++i) {
			if (ch[i] == 'h' && (end - i >= 5) && ch[i + 1] == 't' && ch[i + 2] == 't' && ch[i + 3] == 'p'
					&& ch[i + 4] == ':') {
				for (i += 5; i < end; ++i) {
					if (Character.isWhitespace(ch[i]) || ch[i] > 255) {
						break;
					}
				}
				if (i >= end) {
					break;
				}
			}
			ch[i] = toSimpleKansuji(ch[i]);
		}
	}

	public static char toSimpleKansuji(char c) {
		int ix = from.indexOf(c);
		if (ix != -1) {
			c = to.charAt(ix);
		}
		return c;
	}

	private static final String from2 = "0123456789.,";

	private static final String to2 = "０１２３４５６７８９・，";

	public static void toFullWidthNumber(char[] ch, int off, int len) {
		int end = off + len;
		for (int i = off; i < end; ++i) {
			if (ch[i] == 'h' && (end - i >= 5) && ch[i + 1] == 't' && ch[i + 2] == 't' && ch[i + 3] == 'p'
					&& ch[i + 4] == ':') {
				for (i += 5; i < end; ++i) {
					if (Character.isWhitespace(ch[i]) || ch[i] > 255) {
						break;
					}
				}
				if (i >= end) {
					break;
				}
			}
			ch[i] = toFullWidthNumber(ch[i]);
		}
	}

	public static char toFullWidthNumber(char c) {
		int ix = from2.indexOf(c);
		if (ix != -1) {
			c = to2.charAt(ix);
		}
		return c;
	}
}
