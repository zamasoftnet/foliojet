package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.css3.WordBreakValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WordWrap.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class WordBreak extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WordBreak();

	public static byte get(CSSStyle style) {
		return ((WordBreakValue) style.get(INFO)).getWordBreak();
	}

	protected WordBreak() {
		super("word-break");
	}

	public Value getDefault(CSSStyle style) {
		return WordBreakValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("normal")) {
				return WordBreakValue.NORMAL_VALUE;
			} else if (ident.equals("break-all")) {
				return WordBreakValue.BREAK_ALL_VALUE;
			} else if (ident.equals("keep-all")) {
				return WordBreakValue.KEEP_ALL_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}

}