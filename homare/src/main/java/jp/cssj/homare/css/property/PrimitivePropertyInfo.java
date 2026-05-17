package jp.cssj.homare.css.property;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.value.Value;

/**
 * 分解不可能なプロパティです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: PrimitivePropertyInfo.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface PrimitivePropertyInfo extends PropertyInfo {
	/**
	 * デフォルトで親要素の値を継承する場合はtrueを返します。
	 * 
	 * @return
	 */
	public boolean isInherited();

	/**
	 * isInheritがfalseの場合はデフォルトの値を、isInheritがtrueの場合はルート要素の値を返します。
	 * 
	 * @param style
	 * @return
	 */
	public Value getDefault(CSSStyle style);

	/**
	 * 計算済みの値を返します。
	 * 
	 * @param value
	 * @param style
	 * @return
	 */
	public Value getComputedValue(Value value, CSSStyle style);

	public PrimitivePropertyInfo getEffectiveInfo(CSSStyle style);
}