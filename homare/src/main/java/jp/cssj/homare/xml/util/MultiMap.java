/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jp.cssj.homare.xml.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * A MultiValueMap decorates another map, allowing it to have more than one
 * value for a key.
 * <p>
 * A <code>MultiMap</code> is a Map with slightly different semantics. Putting a
 * value into the map will add the value to a Collection at that key. Getting a
 * value will return a Collection, holding all the values put to that key.
 * <p>
 * This implementation is a decorator, allowing any Map implementation to be
 * used as the base.
 * <p>
 * In addition, this implementation allows the type of collection used for the
 * values to be controlled. By default, an <code>ArrayList</code> is used,
 * however a <code>Class</code> to instantiate may be specified, or a factory
 * that returns a <code>Collection</code> instance.
 * <p>
 * <strong>Note that MultiValueMap is not synchronized and is not
 * thread-safe.</strong> If you wish to use this map from multiple threads
 * concurrently, you must use appropriate synchronization. This class may throw
 * exceptions when accessed by concurrent threads without synchronization.
 * 
 * @author James Carman
 * @author Christopher Berry
 * @author James Strachan
 * @author Steve Downey
 * @author Stephen Colebourne
 * @author Julien Buret
 * @author Serhiy Yevtushenko
 * @version $Revision$ $Date$
 * @since Commons Collections 3.2
 */
public class MultiMap<K, V> extends AbstractMapDecorator<K, Object> {
	// -----------------------------------------------------------------------
	/**
	 * Creates a MultiValueMap based on a <code>HashMap</code> and storing the
	 * multiple values in an <code>ArrayList</code>.
	 */
	public MultiMap() {
		super(new HashMap<K, Object>());
	}

	// -----------------------------------------------------------------------
	/**
	 * Clear the map.
	 */
	public void clear() {
		// If you believe that you have GC issues here, try uncommenting this
		// code
		// Set pairs = getMap().entrySet();
		// Iterator pairsIterator = pairs.iterator();
		// while (pairsIterator.hasNext()) {
		// Map.Entry keyValuePair = (Map.Entry) pairsIterator.next();
		// Collection coll = (Collection) keyValuePair.getValue();
		// coll.clear();
		// }
		getMap().clear();
	}

	/**
	 * Removes a specific value from map.
	 * <p>
	 * The item is removed from the collection mapped to the specified key. Other
	 * values attached to that key are unaffected.
	 * <p>
	 * If the last value for a key is removed, <code>null</code> will be returned
	 * from a subsequant <code>get(key)</code>.
	 * 
	 * @param key
	 *            the key to remove from
	 * @param value
	 *            the value to remove
	 * @return the value removed (which was passed in), null if nothing removed
	 */
	public boolean remove(Object key, Object value) {
		Collection<Object> valuesForKey = getCollection(key);
		if (valuesForKey == null) {
			return false;
		}
		boolean removed = valuesForKey.remove(value);
		if (removed == false) {
			return false;
		}
		if (valuesForKey.isEmpty()) {
			remove(key);
		}
		return true;
	}

	/**
	 * Checks whether the map contains the value specified.
	 * <p>
	 * This checks all collections against all keys for the value, and thus could be
	 * slow.
	 * 
	 * @param value
	 *            the value to search for
	 * @return true if the map contains the value
	 */
	public boolean containsValue(Object value) {
		Set<Entry<K, Object>> pairs = getMap().entrySet();
		if (pairs == null) {
			return false;
		}
		Iterator<Entry<K, Object>> pairsIterator = pairs.iterator();
		while (pairsIterator.hasNext()) {
			Entry<K, Object> keyValuePair = pairsIterator.next();
			@SuppressWarnings("unchecked")
			Collection<Object> coll = (Collection<Object>) keyValuePair.getValue();
			if (coll.contains(value)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds the value to the collection associated with the specified key.
	 * <p>
	 * Unlike a normal <code>Map</code> the previous value is not replaced. Instead
	 * the new value is added to the collection stored against the key.
	 * 
	 * @param key
	 *            the key to store against
	 * @param value
	 *            the value to add to the collection at the key
	 * @return the value added if the map changed and null if the map did not change
	 */
	public Object put(K key, Object value) {
		boolean result = false;
		Collection<Object> coll = getCollection(key);
		if (coll == null) {
			coll = new ArrayList<Object>(1);
			result = coll.add(value);
			if (coll.size() > 0) {
				// only add if non-zero size to maintain class state
				getMap().put(key, coll);
				result = false;
			}
		} else {
			result = coll.add(value);
		}
		return (result ? value : null);
	}

	/**
	 * Checks whether the collection at the specified key contains the value.
	 * 
	 * @param value
	 *            the value to search for
	 * @return true if the map contains the value
	 */
	public boolean containsValue(K key, Object value) {
		Collection<Object> coll = getCollection(key);
		if (coll == null) {
			return false;
		}
		return coll.contains(value);
	}

	/**
	 * Gets the collection mapped to the specified key. This method is a convenience
	 * method to typecast the result of <code>get(key)</code>.
	 * 
	 * @param key
	 *            the key to retrieve
	 * @return the collection mapped to the key, null if no mapping
	 */
	@SuppressWarnings("unchecked")
	public Collection<Object> getCollection(Object key) {
		return (Collection<Object>) getMap().get(key);
	}

	/**
	 * Gets the size of the collection mapped to the specified key.
	 * 
	 * @param key
	 *            the key to get size for
	 * @return the size of the collection at the key, zero if key not in map
	 */
	public int size(Object key) {
		Collection<Object> coll = getCollection(key);
		if (coll == null) {
			return 0;
		}
		return coll.size();
	}

	/**
	 * Gets the total size of the map by counting all the values.
	 * 
	 * @return the total size of the map counting all values
	 */
	public int totalSize() {
		int total = 0;
		Collection<Object> values = getMap().values();
		for (Iterator<Object> it = values.iterator(); it.hasNext();) {
			@SuppressWarnings("unchecked")
			Collection<Object> coll = (Collection<Object>) it.next();
			total += coll.size();
		}
		return total;
	}
}
