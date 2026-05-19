package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AttrValue;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.css.value.NoneValue;
import net.zamasoft.foliojet.css.value.QuoteValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.css.value.ext.CSSJFirstHeadingValue;
import net.zamasoft.foliojet.css.value.ext.CSSJLastHeadingValue;
import net.zamasoft.foliojet.css.value.ext.CSSJPageRefValue;
import net.zamasoft.foliojet.css.value.ext.CSSJTitleValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Content.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Content extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Content();

	public static Value[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_NONE) {
			return null;
		}
		return ((ValueListValue) value).getValues();
	}

	protected Content() {
		super("content");
	}

	public Value getDefault(CSSStyle style) {
		return NoneValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
			String ident = lu.getStringValue();
			if (ident.equalsIgnoreCase("none") || ident.equalsIgnoreCase("normal")) {
				if (lu.getNextLexicalUnit() != null) {
					throw new PropertyException();
				}
				return NoneValue.NONE_VALUE;
			}
		}

		ArrayList<Value> values = new ArrayList<Value>();
		while (lu != null) {
			switch (lu.getLexicalUnitType()) {
			case LexicalUnit.SAC_STRING_VALUE: {// <string>
				values.add(new StringValue(lu.getStringValue()));
			}
				break;

			case LexicalUnit.SAC_URI: {// <uri> values.add(new
				try {
					values.add(ValueUtils.toURI(ua, uri, lu));
				} catch (URISyntaxException e) {
					ua.message(MessageCodes.WARN_BAD_LINK_URI, lu.getStringValue());
				}
			}
				break;

			case LexicalUnit.SAC_COUNTER_FUNCTION: {// <counter>
				CounterValue counter;
				LexicalUnit param = lu.getParameters();
				String id = param.getStringValue();
				param = param.getNextLexicalUnit();
				if (param == null) {
					counter = new CounterValue(id);
				} else {
					param = param.getNextLexicalUnit();
					switch (param.getLexicalUnitType()) {
					case LexicalUnit.SAC_IDENT:
					case LexicalUnit.SAC_STRING_VALUE:
						break;
					default:
						throw new PropertyException();
					}
					String listStyle = param.getStringValue();
					final ListStyleTypeValue styleType = GeneratedValueUtils.toListStyleType(listStyle);
					if (styleType == null) {
						throw new PropertyException();
					}
					counter = new CounterValue(id, styleType.getListStyleType());
				}
				values.add(counter);
			}
				break;

			case LexicalUnit.SAC_COUNTERS_FUNCTION: {// <counter>
				CountersValue counters;
				LexicalUnit param = lu.getParameters();
				String id = param.getStringValue();
				param = param.getNextLexicalUnit();
				param = param.getNextLexicalUnit();
				String delimiter = param.getStringValue();
				param = param.getNextLexicalUnit();
				if (param == null) {
					counters = new CountersValue(id, delimiter);
				} else {
					param = param.getNextLexicalUnit();
					String listStyle = param.getStringValue();
					final ListStyleTypeValue styleType = GeneratedValueUtils.toListStyleType(listStyle);
					if (styleType == null) {
						throw new PropertyException();
					}
					counters = new CountersValue(id, delimiter, styleType);
				}
				values.add(counters);
			}
				break;

			case LexicalUnit.SAC_ATTR: {// attr(x)
				values.add(new AttrValue(lu.getStringValue()));
			}
				break;

			case LexicalUnit.SAC_IDENT: {// quote
				String ident = lu.getStringValue();
				ident = ident.toLowerCase();
				if (ident.equals("open-quote")) {
					values.add(QuoteValue.OPEN_QUOTE_VALUE);
				} else if (ident.equals("close-quote")) {
					values.add(QuoteValue.CLOSE_QUOTE_VALUE);
				} else if (ident.equals("no-open-quote")) {
					values.add(QuoteValue.NO_OPEN_QUOTE_VALUE);
				} else if (ident.equals("no-close-quote")) {
					values.add(QuoteValue.NO_CLOSE_QUOTE_VALUE);
				} else if (ident.equals("-cssj-title")) {
					values.add(CSSJTitleValue.CSSJ_TITLE_VALUE);
				} else {
					throw new PropertyException();
				}
			}
				break;

			case LexicalUnit.SAC_FUNCTION:
				String funcName = lu.getFunctionName();
				if (funcName.equalsIgnoreCase("-cssj-heading") || funcName.equalsIgnoreCase("-cssj-last-heading")) {
					// -cssj-heading
					CSSJLastHeadingValue heading;
					LexicalUnit param = lu.getParameters();
					if (param == null) {
						heading = new CSSJLastHeadingValue(1);
					} else {
						if (param.getLexicalUnitType() != LexicalUnit.SAC_INTEGER
								|| param.getNextLexicalUnit() != null) {
							throw new PropertyException();
						}
						int level = param.getIntegerValue();
						heading = new CSSJLastHeadingValue(level);
					}
					values.add(heading);
					break;
				} else if (funcName.equalsIgnoreCase("-cssj-first-heading")) {
					// -cssj-first-heading
					CSSJFirstHeadingValue heading;
					LexicalUnit param = lu.getParameters();
					if (param == null) {
						heading = new CSSJFirstHeadingValue(1);
					} else {
						if (param.getLexicalUnitType() != LexicalUnit.SAC_INTEGER
								|| param.getNextLexicalUnit() != null) {
							throw new PropertyException();
						}
						int level = param.getIntegerValue();
						heading = new CSSJFirstHeadingValue(level);
					}
					values.add(heading);
					break;
				} else if (funcName.equalsIgnoreCase("-cssj-page-ref")) {
					CSSJPageRefValue pageRef;
					LexicalUnit param = lu.getParameters();
					if (param == null) {
						throw new PropertyException();
					}
					byte type;
					switch (param.getLexicalUnitType()) {
					case LexicalUnit.SAC_STRING_VALUE:
					case LexicalUnit.SAC_IDENT: {
						type = CSSJPageRefValue.REF;
					}
						break;
					case LexicalUnit.SAC_ATTR: {
						type = CSSJPageRefValue.ATTR;
					}
						break;
					default:
						throw new PropertyException("IDが必要です");
					}
					String ref = param.getStringValue();
					param = param.getNextLexicalUnit();
					if (param == null || param.getLexicalUnitType() != LexicalUnit.SAC_OPERATOR_COMMA) {
						throw new PropertyException("カンマが必要です");
					}
					param = param.getNextLexicalUnit();
					if (param == null || (param.getLexicalUnitType() != LexicalUnit.SAC_IDENT
							&& param.getLexicalUnitType() != LexicalUnit.SAC_STRING_VALUE)) {
						throw new PropertyException("カウンタ名が必要です");
					}
					String counter = param.getStringValue();
					short numberStyleType;
					String separator = null;
					param = param.getNextLexicalUnit();
					if (param == null) {
						numberStyleType = ListStyleTypeValue.DECIMAL;
					} else {
						if (param.getLexicalUnitType() != LexicalUnit.SAC_OPERATOR_COMMA) {
							throw new PropertyException("カンマが必要です");
						}
						param = param.getNextLexicalUnit();
						if (param == null || param.getLexicalUnitType() != LexicalUnit.SAC_IDENT) {
							throw new PropertyException("数字タイプが必要です");
						} else {
							String typeStr = param.getStringValue();
							ListStyleTypeValue typeValue = GeneratedValueUtils.toListStyleType(typeStr);
							if (typeValue == null) {
								throw new PropertyException("数字タイプが不正です");
							}
							numberStyleType = typeValue.getListStyleType();
						}
						param = param.getNextLexicalUnit();
						if (param != null) {
							if (param.getLexicalUnitType() != LexicalUnit.SAC_OPERATOR_COMMA) {
								throw new PropertyException("カンマが必要です");
							}
							param = param.getNextLexicalUnit();

							if (param == null || param.getLexicalUnitType() != LexicalUnit.SAC_STRING_VALUE)
								throw new PropertyException("区切り文字が必要です");
							separator = param.getStringValue();
						}
					}

					pageRef = new CSSJPageRefValue(type, ref, counter, numberStyleType, separator);
					values.add(pageRef);
					break;
				}

			default:
				throw new PropertyException();
			}

			lu = lu.getNextLexicalUnit();
		}
		if (values.isEmpty()) {
			throw new PropertyException();
		}
		return new ValueListValue((Value[]) values.toArray(new Value[values.size()]));
	}
}