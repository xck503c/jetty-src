package org.eclipse.jetty.http;

/**
 * https://github.com/eclipse/jetty.project/blob/jetty-8.1.x/jetty-http/src/main/java/org/eclipse/jetty/http/HttpCookie.java
 * https://www.cnblogs.com/bq-med/p/8603664.html
 *
 * 表示Cookie中的一个键值对，有属性：
 * 1. cookie生效的域名，cookie所属请求路径
 * 2. 过期的时间点，最大存活时间，不指定就是当前session
 * 3. secure字段会该cookie只能用于https
 * 4. httponly，只能用于http协议，其余脚本无法访问如js
 */
public class HttpCookie {
    private final String _name; //键值对
    private final String _value;
    private final String _comment; //注释？
    private final String _domain;
    private final int _maxAge;
    private final String _path;
    private final boolean _secure;
    private final int _version;
    private final boolean _httpOnly;

    public HttpCookie(String name, String value) {
        super();
        _name = name;
        _value = value;
        _comment = null;
        _domain = null;
        _httpOnly = false;
        _maxAge = -1;
        _path = null;
        _secure = false;
        _version = 0;
    }

    /* ------------------------------------------------------------ */
    public HttpCookie(String name, String value, String domain, String path) {
        super();
        _name = name;
        _value = value;
        _comment = null;
        _domain = domain;
        _httpOnly = false;
        _maxAge = -1;
        _path = path;
        _secure = false;
        _version = 0;

    }

    /* ------------------------------------------------------------ */
    public HttpCookie(String name, String value, int maxAge) {
        super();
        _name = name;
        _value = value;
        _comment = null;
        _domain = null;
        _httpOnly = false;
        _maxAge = maxAge;
        _path = null;
        _secure = false;
        _version = 0;
    }

    /* ------------------------------------------------------------ */
    public HttpCookie(String name, String value, String domain, String path, int maxAge, boolean httpOnly, boolean secure) {
        super();
        _comment = null;
        _domain = domain;
        _httpOnly = httpOnly;
        _maxAge = maxAge;
        _name = name;
        _path = path;
        _secure = secure;
        _value = value;
        _version = 0;
    }

    /* ------------------------------------------------------------ */
    public HttpCookie(String name, String value, String domain, String path, int maxAge, boolean httpOnly, boolean secure, String comment, int version) {
        super();
        _comment = comment;
        _domain = domain;
        _httpOnly = httpOnly;
        _maxAge = maxAge;
        _name = name;
        _path = path;
        _secure = secure;
        _value = value;
        _version = version;
    }

    /* ------------------------------------------------------------ */
    /** Get the name.
     * @return the name
     */
    public String getName() {
        return _name;
    }

    /* ------------------------------------------------------------ */
    /** Get the value.
     * @return the value
     */
    public String getValue() {
        return _value;
    }

    /* ------------------------------------------------------------ */
    /** Get the comment.
     * @return the comment
     */
    public String getComment() {
        return _comment;
    }

    /* ------------------------------------------------------------ */
    /** Get the domain.
     * @return the domain
     */
    public String getDomain() {
        return _domain;
    }

    /* ------------------------------------------------------------ */
    /** Get the maxAge.
     * @return the maxAge
     */
    public int getMaxAge() {
        return _maxAge;
    }

    /* ------------------------------------------------------------ */
    /** Get the path.
     * @return the path
     */
    public String getPath() {
        return _path;
    }

    /* ------------------------------------------------------------ */
    /** Get the secure.
     * @return the secure
     */
    public boolean isSecure() {
        return _secure;
    }

    /* ------------------------------------------------------------ */
    /** Get the version.
     * @return the version
     */
    public int getVersion() {
        return _version;
    }

    /* ------------------------------------------------------------ */
    /** Get the isHttpOnly.
     * @return the isHttpOnly
     */
    public boolean isHttpOnly() {
        return _httpOnly;
    }
}
