package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.css3.WordWrapValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WordWrap.java 1570 2018-07-11 05:50:15Z miyabe $
 */
public class WordWrap extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WordWrap();

	public static byte get(CSSStyle style) {
		return ((WordWrapValue) style.get(INFO)).getWordWrap();
	}

	protected WordWrap() {
		super("-cssj-word-wrap");
	}

	public Value getDefault(CSSStyle style) {
		return WordWrapValue.NORMAL_VALUE;
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
				return WordWrapValue.NORMAL_VALUE;
			} else if (ident.equals("break-word")) {
				return WordWrapValue.BREAK_WORD_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}

}