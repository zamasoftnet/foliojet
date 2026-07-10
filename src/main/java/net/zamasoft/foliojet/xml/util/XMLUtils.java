package net.zamasoft.foliojet.xml.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.ParseException;

import net.zamasoft.foliojet.css.parser.InputSource;
import net.zamasoft.zstream.resolver.Source;

import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: XMLUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class XMLUtils {
	private XMLUtils() {
		// unused
	}

	public static DefaultHandler DEFAULT_HANDLER_INSTANCE = new DefaultHandler();

	public static InputSource toSACInputSource(Source source, String charset, String mediaTypes, String title)
			throws IOException {
		String encoding = source.getEncoding();
		Reader reader;
		if (encoding != null) {
			if (source.isReader()) {
				reader = source.getReader();
			} else {
				reader = new InputStreamReader(source.getInputStream(), encoding);
			}
		} else {
			if (charset != null) {
				encoding = charset;
				if (source.isReader()) {
					reader = source.getReader();
				} else {
					reader = new InputStreamReader(source.getInputStream(), charset);
				}
			} else {
				if (source.isReader()) {
					reader = source.getReader();
				} else {
					reader = new InputStreamReader(source.getInputStream());
				}
			}
		}
		InputSource inputSource = new InputSource(reader);
		inputSource.setEncoding(encoding);
		inputSource.setMedia(mediaTypes);
		inputSource.setTitle(title);
		inputSource.setURI(source.getURI().toString());
		return inputSource;
	}

	public static org.xml.sax.InputSource toSAXInputSource(Source source) throws IOException {
		org.xml.sax.InputSource inputSource;
		if (source.isReader()) {
			inputSource = new org.xml.sax.InputSource(source.getReader());
		} else {
			inputSource = new org.xml.sax.InputSource(source.getInputStream());
		}
		inputSource.setSystemId(source.getURI().toString());
		return inputSource;
	}

	/**
	 * BOMをチェックして、エンコーディングを返します。
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 */
	public static String checkBOM(InputStream in) throws IOException {
		in.mark(3);
		String encoding = null;
		int c = in.read();
		if (c == 0xFE) {
			if (in.read() == 0xFF) {
				encoding = "UTF-16BE";
			}
		} else if (c == 0xFF) {
			if (in.read() == 0xFE) {
				encoding = "UTF-16LE";
			}
		} else if (c == 0xEF) {
			if (in.read() == 0xBB && in.read() == 0xBF) {
				encoding = "UTF-8";
			}
		}
		if (encoding == null) {
			in.reset();
		}
		return encoding;
	}

	public static String checkXMLDeclEncoding(InputStream in) throws IOException {
		in.mark(100);
		for (int i = 0; i < 10; ++i) {
			if (in.read() != '<') {
				continue;
			}
			if (in.read() != '?') {
				continue;
			}
			if (in.read() != 'x') {
				continue;
			}
			if (in.read() != 'm') {
				continue;
			}
			if (in.read() != 'l') {
				continue;
			}
			if (in.read() != ' ') {
				continue;
			}
			StringBuilder buff = new StringBuilder();
			for (; i < 80; ++i) {
				int c = in.read();
				if (c != '>') {
					buff.append(c);
					continue;
				}
				in.reset();
				AttributesImpl attsi = new AttributesImpl();
				try {
					parsePseudoAttributes(buff.toString(), attsi);
					String encoding = attsi.getValue("encoding");
					return encoding == null ? "UTF-8" : encoding;
				} catch (ParseException e) {
					return null;
				}
			}
		}
		in.reset();
		return null;
	}

	public static org.xml.sax.InputSource toInputSource(Source source) throws IOException {
		org.xml.sax.InputSource inputSource;
		if (source.isReader()) {
			inputSource = new org.xml.sax.InputSource(new BufferedReader(source.getReader()));
		} else {
			// BOMチェック
			InputStream in = new BufferedInputStream(source.getInputStream());
			String encoding = checkBOM(in);

			if (encoding != null) {
				Reader r = new InputStreamReader(in, encoding);
				inputSource = new org.xml.sax.InputSource(r);
			} else {
				inputSource = new org.xml.sax.InputSource(in);
			}
		}
		inputSource.setSystemId(source.getURI().toString());
		return inputSource;
	}

	/**
	 * PI などで使用される擬似属性を解析します。
	 * 
	 * @param ch
	 * @param off
	 * @param len
	 * @param atts
	 * @return
	 */
	public static String parsePseudoAttributes(char[] ch, int off, int len, AttributesImpl atts) throws ParseException {
		StringBuilder data = null, name = null, value = null, escape = null;
		short state = 0;
		char delim = '"';
		for (int i = 0; i < len; ++i) {
			char c = ch[i + off];
			switch (state) {
			case 0:// 初期状態
				if (c == '[') {
					data = new StringBuilder();
					state = 9;
				} else if (!Character.isWhitespace(c) && c != ',') {
					name = new StringBuilder();
					name.append(c);
					state = 1;
				}
				break;

			case 1:// 名前を解析中
				if (c == '=') {
					value = new StringBuilder();
					state = 2;
				} else if (!Character.isWhitespace(c)) {
					name.append(c);
				}
				break;

			case 2:// 値を解析前
				if (c == '"' || c == '\'') {
					delim = c;
					state = 3;
				} else if (!Character.isWhitespace(c)) {
					value.append(c);
					delim = 0;
					state = 3;
				}
				break;

			case 3:// 値を解析中
				if (c == delim || (delim == 0 && (Character.isWhitespace(c) || c == ','))) {
					String nameStr = name.toString();
					atts.addAttribute("", nameStr, nameStr, "CDATA", value.toString());
					name = value = null;
					state = 0;
				} else if (c == '&') {
					escape = new StringBuilder();
					state = 4;
				} else {
					value.append(c);
					if (i == len - 1) {
						String nameStr = name.toString();
						atts.addAttribute("", nameStr, nameStr, "CDATA", value.toString());
					}
				}
				break;

			case 4:// エスケープを解析中
				if (c == '#') {
					state = 6;
				} else {
					escape.append(c);
					state = 5;
				}
				break;

			case 5:// 定義済みエンティティを解析中
				if (c == ';') {
					String escapeStr = escape.toString();
					escape = null;
					if (escapeStr.equals("amp")) {
						value.append('&');
					} else if (escapeStr.equals("lt")) {
						value.append('<');
					} else if (escapeStr.equals("gt")) {
						value.append('>');
					} else if (escapeStr.equals("quot")) {
						value.append('"');
					} else if (escapeStr.equals("apos")) {
						value.append('\'');
					} else {
						throw new ParseException("不正なエンティティです:" + escapeStr, i);
					}
					state = 3;
				} else {
					escape.append(c);
				}
				break;

			case 6:// キャラクタ参照を解析中
				if (c == 'x' || c == 'X') {
					state = 8;
				} else {
					escape.append(c);
					state = 7;
				}
				break;

			case 7:// 10進ユニコードを解析中
				if (c == ';') {
					String escapeStr = escape.toString();
					escape = null;
					try {
						int unicode = Integer.parseInt(escapeStr);
						value.append((char) unicode);
					} catch (NumberFormatException e) {
						throw new ParseException("不正なエンティティです:" + escapeStr, i);
					}
					state = 3;
				} else {
					escape.append(c);
				}
				break;

			case 8:// 16進ユニコードを解析中
				if (c == ';') {
					String escapeStr = escape.toString();
					escape = null;
					try {
						int unicode = Integer.parseInt(escapeStr, 16);
						value.append((char) unicode);
					} catch (NumberFormatException e) {
						throw new ParseException("不正なエンティテです:" + escapeStr, i);
					}
					state = 3;
				} else {
					escape.append(c);
				}
				break;

			case 9:// データを解析中
				if (c == ']') {
					if (++i >= len) {
						break;
					}
					if (ch[i + off] != ']') {
						--i;
					}
					data.append(c);
				} else {
					data.append(c);
				}
				break;
			}
		}
		return data == null ? "" : data.toString();
	}

	public static String parsePseudoAttributes(String str, AttributesImpl atts) throws ParseException {
		char[] ch = str.toCharArray();
		return parsePseudoAttributes(ch, 0, ch.length, atts);
	}

	/**
	 * 擬似属性値に適したエスケープを行います。
	 * 
	 * @param val
	 * @return
	 */
	public static String escapePseudeAttr(String val) {
		StringBuilder result = new StringBuilder(val.length());
		for (int i = 0; i < val.length(); ++i) {
			char c = val.charAt(i);
			switch (c) {
			case '&':
				result.append("&amp;");
				break;
			case '"':
				result.append("&quot;");
				break;
			case '\'':
				result.append("&apos;");
				break;
			default:
				result.append(c);
				break;
			}
		}
		return result.toString();
	}

	/**
	 * 擬似属性のデータ部に適したエスケープを行います。
	 * 
	 * @param val
	 * @return
	 */
	public static String escapePseudeData(String val) {
		StringBuilder result = new StringBuilder(val.length());
		for (int i = 0; i < val.length(); ++i) {
			char c = val.charAt(i);
			switch (c) {
			case ']':
				result.append("]]");
				break;
			default:
				result.append(c);
				break;
			}
		}
		return result.toString();
	}
}
