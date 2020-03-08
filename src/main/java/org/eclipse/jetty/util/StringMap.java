package org.eclipse.jetty.util;

import java.util.AbstractMap;

/**
 * Map implementation Optimized for Strings keys.. This String Map has been
 * optimized for mapping small sets of Strings where the most frequently accessed
 * Strings have been put to the map first.
 *
 * It also has the benefit that it can look up entries by substring or sections of
 * char and byte arrays.  This can prevent many String objects from being created
 * just to look up in the map.
 *
 * This map is NOT synchronized.
 */
public class StringMap extends AbstractMap {

}
