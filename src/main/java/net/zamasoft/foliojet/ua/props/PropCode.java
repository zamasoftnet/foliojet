package net.zamasoft.foliojet.ua.props;

import java.util.Locale;

/**
 * コード型プロパティの選択肢です。プロパティ値の識別子は
 * 既定でenum定数名の小文字ケバブ表記になります。
 */
public interface PropCode {
	public default String ident() {
		return ((Enum<?>) this).name().toLowerCase(Locale.ROOT).replace('_', '-');
	}
}
