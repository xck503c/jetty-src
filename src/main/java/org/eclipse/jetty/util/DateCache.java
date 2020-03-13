package org.eclipse.jetty.util;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**  Date Format Cache.
 * Computes String representations of Dates and caches
 * the results so that subsequent requests within the same minute
 * will be fast.
 * 计算字符串的日期表示形式，并且缓存其结果以便于后续的快速请求
 *
 * Only format strings that contain either "ss" or "ss.SSS" are
 * handled.
 * 仅处理包含ss或者ss.SSS的格式的字符串
 *
 * The timezone of the date may be included as an ID with the "zzz"
 * format string or as an offset with the "ZZZ" format string.
 * 日期的时区可以做为ID放在zzz格式的字符串中，也可以做为偏移量包含在ZZZ格式的字符串中
 *
 * If consecutive calls are frequently very different, then this
 * may be a little slower than a normal DateFormat.
 * 当然如果连续调用有很大的不同，则会比普通的DataFormat慢；
 *
 * 目前看不懂下面的代码作用，后续结合其他部分使用一起看
 */
public class DateCache {
    public static String DEFAULT_FORMAT="EEE MMM dd HH:mm:ss zzz yyyy";
    private static long __hitWindow=60*60;

    private String _formatString; //该缓存所使用的格式字符串
    private String _tzFormatString;
    private SimpleDateFormat _tzFormat;

    private String _minFormatString;
    private SimpleDateFormat _minFormat;

    private String _secFormatString;
    private String _secFormatString0;
    private String _secFormatString1;

    private long _lastMinutes = -1;
    private long _lastSeconds = -1;
    private int _lastMs = -1;
    private String _lastResult = null;

    private Locale _locale	= null; //国家
    private DateFormatSymbols	_dfs	= null;

    /* ------------------------------------------------------------ */
    /** Constructor.
     * Make a DateCache that will use a default format. The default format
     * generates the same results as Date.toString().
     */
    public DateCache()
    {
        this(DEFAULT_FORMAT);
        getFormat().setTimeZone(TimeZone.getDefault());
    }

    /* ------------------------------------------------------------ */
    /** Constructor.
     * Make a DateCache that will use the given format
     */
    public DateCache(String format)
    {
        _formatString=format;
        setTimeZone(TimeZone.getDefault());

    }

    /* ------------------------------------------------------------ */
    public DateCache(String format,Locale l)
    {
        _formatString=format;
        _locale = l;
        setTimeZone(TimeZone.getDefault());
    }

    /* ------------------------------------------------------------ */
    public DateCache(String format,DateFormatSymbols s)
    {
        _formatString=format;
        _dfs = s;
        setTimeZone(TimeZone.getDefault());
    }

    /* ------------------------------------------------------------ */
    /** Set the timezone.
     * @param tz TimeZone
     */
    public synchronized void setTimeZone(TimeZone tz) {
        setTzFormatString(tz);
        if( _locale != null ) {
            _tzFormat=new SimpleDateFormat(_tzFormatString,_locale);
            _minFormat=new SimpleDateFormat(_minFormatString,_locale);
        }
        else if( _dfs != null ) {
            _tzFormat=new SimpleDateFormat(_tzFormatString,_dfs);
            _minFormat=new SimpleDateFormat(_minFormatString,_dfs);
        }
        else {
            _tzFormat=new SimpleDateFormat(_tzFormatString);
            _minFormat=new SimpleDateFormat(_minFormatString);
        }
        _tzFormat.setTimeZone(tz);
        _minFormat.setTimeZone(tz);
        _lastSeconds=-1;
        _lastMinutes=-1;
    }

    /* ------------------------------------------------------------ */
    public TimeZone getTimeZone() {
        return _tzFormat.getTimeZone();
    }

    /* ------------------------------------------------------------ */
    /** Set the timezone.
     * @param timeZoneId TimeZoneId the ID of the zone as used by
     * TimeZone.getTimeZone(id)
     */
    public void setTimeZoneID(String timeZoneId) {
        setTimeZone(TimeZone.getTimeZone(timeZoneId));
    }

    /* ------------------------------------------------------------ */
    private synchronized void setTzFormatString(final  TimeZone tz ) {
        int zIndex = _formatString.indexOf( "ZZZ" );
        if( zIndex >= 0 ) {
            //提取ZZZ
            String ss1 = _formatString.substring( 0, zIndex );
            String ss2 = _formatString.substring( zIndex+3 );
            //获取相对于本初子午线的偏移量，单位毫秒，本初子午线是0时区，北京是东8区，
            // 根据东加西减原则,所以如果子午线是0时，那北京时间就是8时
            int tzOffset = tz.getRawOffset();

            StringBuilder sb = new StringBuilder(_formatString.length()+10);
            sb.append(ss1);
            sb.append("'");
            //正就是东边要+
            if( tzOffset >= 0 )
                sb.append( '+' );
            else {
                tzOffset = -tzOffset;
                sb.append( '-' );
            }

            // 分钟
            int raw = tzOffset / (1000*60);		// Convert to seconds
            //小时
            int hr = raw / 60;
            //多余分钟
            int min = raw % 60;

            //如果小于10，前面补0
            if( hr < 10 )
                sb.append( '0' );
            sb.append( hr );
            if( min < 10 )
                sb.append( '0' );
            sb.append( min );
            sb.append( '\'' );

            sb.append(ss2); //例如：yyyy-MM-dd '+0800'
            _tzFormatString=sb.toString();
        }
        else
            _tzFormatString=_formatString;
        setMinFormatString();
    }


    /* ------------------------------------------------------------ */
    private void setMinFormatString() {
        int i = _tzFormatString.indexOf("ss.SSS");
        int l = 6;
        if (i>=0) //不支持毫秒
            throw new IllegalStateException("ms not supported");
        i = _tzFormatString.indexOf("ss"); //这里少了一个判断，就是如果连ss都没有
        l=2;

        // Build a formatter that formats a second format string
        String ss1=_tzFormatString.substring(0,i);
        String ss2=_tzFormatString.substring(i+l);
        _minFormatString =ss1+"'ss'"+ss2; //yyyy-MM-dd hh:mm:'ss' '+0800'
    }

    /* ------------------------------------------------------------ */
    /** Format a date according to our stored formatter.
     * @param inDate
     * @return Formatted date
     */
    public synchronized String format(Date inDate)
    {
        return format(inDate.getTime());
    }

    /* ------------------------------------------------------------ */
    /** Format a date according to our stored formatter.
     * @param inDate
     * @return Formatted date
     */
    public synchronized String format(long inDate) {
        long seconds = inDate / 1000; //换成秒

        // Is it not suitable to cache? 是否适合缓存
        //判断的条件是在上一次缓存的秒，second~second+__hitWindow之间
        if (seconds<_lastSeconds ||
                _lastSeconds>0 && seconds>_lastSeconds+__hitWindow) {
            // It's a cache miss 未命中缓存
            Date d = new Date(inDate);
            return _tzFormat.format(d);

        }

        // Check if we are in the same second
        // and don't care about millis
        if (_lastSeconds==seconds ) //如果秒一样的就不用在意毫秒
            return _lastResult;

        Date d = new Date(inDate);

        // Check if we need a new format string
        long minutes = seconds/60; //缓存分钟。。。
        if (_lastMinutes != minutes) {
            _lastMinutes = minutes;
            _secFormatString=_minFormat.format(d);

            int i=_secFormatString.indexOf("ss");
            int l=2;
            _secFormatString0=_secFormatString.substring(0,i);
            _secFormatString1=_secFormatString.substring(i+l);
        }

        // Always format if we get here
        _lastSeconds = seconds; //缓存最近一次的秒级别
        StringBuilder sb=new StringBuilder(_secFormatString.length());
        sb.append(_secFormatString0);
        int s=(int)(seconds%60);
        if (s<10)
            sb.append('0');
        sb.append(s);
        sb.append(_secFormatString1);
        _lastResult=sb.toString();

        return _lastResult;
    }

    /* ------------------------------------------------------------ */
    /** Format to string buffer.
     * @param inDate Date the format
     * @param buffer StringBuilder
     */
    public void format(long inDate, StringBuilder buffer)
    {
        buffer.append(format(inDate));
    }

    /* ------------------------------------------------------------ */
    /** Get the format.
     */
    public SimpleDateFormat getFormat()
    {
        return _minFormat;
    }

    /* ------------------------------------------------------------ */
    public String getFormatString()
    {
        return _formatString;
    }

    /* ------------------------------------------------------------ */
    public String now()
    {
        long now=System.currentTimeMillis();
        _lastMs=(int)(now%1000);
        return format(now);
    }

    /* ------------------------------------------------------------ */
    public int lastMs()
    {
        return _lastMs;
    }
}
