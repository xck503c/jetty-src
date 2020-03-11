package org.eclipse.jetty.util;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
 * 百度的机器翻译
 * 为字符串键优化的映射实现。。这个字符串映射已经过优化，可以映射小的字符串集，其中最常访问的字符串已经
 * 先放在映射中。
 * 它还有一个好处，就是可以通过子字符串或char和byte数组的来查找条目。这可以防止只是为了在映射中查找
 * 而创建许多字符串对象。
 *
 * 总结一下，这个实现就是一棵树，从上到下，上层是下层的公共前缀，用char数组存储，用来寻找比较；
 * 会根据情况进行分裂，来达到目的，分裂的目的是为了提取公共前缀，但是如果put的key不是这个公共前缀
 * 那这分裂的父节点就不会值
 *
 * 可以避免存储重复的数据，而且提供了一个最佳匹配的方法，就是只要找到前缀即可，不需要完全符合
 */
public class StringMap extends AbstractMap {

    protected static final int __HASH_WIDTH = 17;

    //保存各个节点的set
    protected HashSet _entrySet=new HashSet(3);
    protected Node _root = new Node(); //默认root节点
    protected NullEntry _nullEntry = null; //默认不创建？
    protected Object _nullValue = null; //null的默认值

    protected int _width = __HASH_WIDTH; //子节点的树宽，实现结构是数组的长度
    protected boolean _ignoreCase = false; //是否对大小敏感，默认是false-敏感

    //不可修改的视图，有空可以看看实现
    protected Set _umEntrySet=Collections.unmodifiableSet(_entrySet);

    public StringMap(){}

    public StringMap(boolean ignoreCase){
        this();
        _ignoreCase = ignoreCase;
    }

    public StringMap(boolean ignoreCase,int width) {
        this();
        _ignoreCase=ignoreCase;
        _width=width;
    }

    public void set_ignoreCase(boolean _ignoreCase) {
        if(_root._children!=null){
            throw new IllegalStateException("Must be set before first put");
        }
        this._ignoreCase = _ignoreCase;
    }

    public boolean is_ignoreCase() {
        return _ignoreCase;
    }

    /**
     * 设置的宽度越大，运行越快，因为少了冲突，但是会使用更多内存，毕竟数组是连续分配
     */
    public void setWidth(int width) {
        _width=width;
    }

    public int getWidth() {
        return _width;
    }

    @Override
    public Object put(Object key, Object value) {
        if (key==null)
            return put(null,value);
        return put(key.toString(),value);
    }

    public Object put(String key, Object value){
        if(key == null){
            //为null的始终只有一个键值对
            Object oldValue = this._nullValue;
            this._nullValue = value;
            if(_nullEntry == null){
                _nullValue = new NullEntry();
                _entrySet.add(_nullEntry);
            }

            return oldValue;
        }

        Node node = this._root;
        int ni = -1; //相同部分偏移量
        Node prev = null;
        Node parent = null;

        //寻找各个前缀
        charLoop:
        for(int i=0; i<key.length(); i++){
            char c = key.charAt(i);
            //定位子节点
            if(ni == -1){
                parent = node;
                prev = null;
                ni = 0;
                node = node._children==null?null:node._children[c%_width];
            }

            //找到对应子节点就进入循环，没有就跳过创建新节点
            while (node!=null){
                if(node._char[ni] == c || (is_ignoreCase()&&node._ochar[ni] == c)){
                    prev = null;
                    ni++; //递增公共前缀偏移量
                    if(ni == node._char.length){
                        ni = -1; //全部符合，跳到下一个找他的子节点
                    }
                    continue charLoop;
                }

                //如果第一个就不符合，那就是冲突
                if(ni == 0){
                    prev = node;
                    node = node._next;
                }else {
                    node.split(this, ni); //说明需要分裂了，因为有char不一样
                    i--;
                    ni = -1; //分裂之后，到该公共下面继续寻找子节点
                    continue charLoop;
                }
            }

            //没有找到，那就创建
            node = new Node(_ignoreCase,key,i);

            //看看是哪种情况
            if(prev!=null){
                prev._next = node;
            }else if(parent!=null){
                if(parent._children == null){
                    parent._children = new Node[_width];
                }
                parent._children[c%_width] = node;
                int oi=node._ochar[0]%_width;
                if (node._ochar!=null && node._char[0]%_width!=oi) {
                    if (parent._children[oi]==null) {
                        parent._children[oi] = node;
                    } else {
                        Node n=parent._children[oi];
                        while(n._next!=null)
                            n=n._next;
                        n._next=node;
                    }
                }
            }else {
                _root = node;
            }
            break;
        }

        if(node != null){
            if(ni > 0){ //说明原节点存在公共情况，需要分裂原有的节点
                node.split(this, ni);
            }

            Object old = node._value;
            node._key=key;
            node._value=value;
            _entrySet.add(node);
            return old;
        }
        return null;
    }

    @Override
    public Object get(Object key) {
        if (key==null)
            return _nullValue;
        if (key instanceof String)
            return get((String)key);
        return get(key.toString());
    }

    public Object get(String key) {
        if (key==null)
            return _nullValue;

        Entry entry = getEntry(key,0,key.length());
        if (entry==null)
            return null;
        return entry.getValue();
    }

    /**
     * 根据put的思路，我们可以推出如何get，那就是遍历char，去root下定位是否有对应的子节点
     * ，如果有就是判断该节点的char数组是否匹配。如果发现有不一样，那就肯定没有；如果前缀一样那就继续找
     * @param key
     * @param offset
     * @param length
     * @return
     */
    public Entry getEntry(String key, int offset, int length){
        if (key==null)
            return _nullEntry;

        Node node = _root;
        int ni = -1;

        charLoop:
        for(int i=0; i<key.length();i++){
            char c = key.charAt(offset + i);
            if (ni==-1) { //定位子节点
                ni=0;
                node=(node._children==null)?null:node._children[c%_width];
            }

            while (node!=null) {
                // If it is a matching node, goto next char
                if (node._char[ni]==c || _ignoreCase&&node._ochar[ni]==c) {
                    ni++;
                    if (ni==node._char.length) //都匹配，去下层找
                        ni=-1;
                    continue charLoop;
                }

                // No char match, so if mid node then no match at all.
                if (ni>0) return null; //有一个不匹配，那就没有找到

                // try next in chain
                node=node._next;
            }
            return null; //如果定位不到那就返回null
        }

        //虽然有节点前缀是相同的，但是没用，还是找不到
        if (ni>0) return null;
        if (node!=null && node._key==null)
            return null;
        return node;
    }

    //同上
    public Entry getEntry(char[] key,int offset, int length) {
        if (key==null)
            return _nullEntry;

        Node node = _root;
        int ni=-1;

        // look for best match
        charLoop:
        for (int i=0;i<length;i++) {
            char c=key[offset+i];

            // Advance node
            if (ni==-1) {
                ni=0;
                node=(node._children==null)?null:node._children[c%_width];
            }

            // While we have a node to try
            while (node!=null) {
                // If it is a matching node, goto next char
                if (node._char[ni]==c || _ignoreCase&&node._ochar[ni]==c) {
                    ni++;
                    if (ni==node._char.length)
                        ni=-1;
                    continue charLoop;
                }

                // No char match, so if mid node then no match at all.
                if (ni>0) return null;

                // try next in chain
                node=node._next;
            }
            return null;
        }

        if (ni>0) return null;
        if (node!=null && node._key==null)
            return null;
        return node;
    }

    /**
     *
     * @param key
     * @param offset
     * @param maxLength
     * @return
     */
    public Entry getBestEntry(byte[] key, int offset, int maxLength){
        if (key==null)
            return _nullEntry;

        Node node = _root;
        int ni=-1;

        // look for best match
        charLoop:
        for (int i=0;i<maxLength;i++) {
            char c=(char)key[offset+i];

            // Advance node
            //寻找子节点
            if (ni==-1) {
                ni=0;

                Node child = (node._children==null)?null:node._children[c%_width];
                //多了一个判断，如果定位到子节点为空，那就直接返回，对照方法名字，是最佳匹配
                if (child==null && i>0)
                    return node; // This is the best match
                node=child;
            }

            // While we have a node to try
            while (node!=null) {
                // If it is a matching node, goto next char
                if (node._char[ni]==c || _ignoreCase&&node._ochar[ni]==c) {
                    ni++;
                    if (ni==node._char.length)
                        ni=-1;
                    continue charLoop;
                }

                // No char match, so if mid node then no match at all.
                if (ni>0) return null;

                // try next in chain
                node=node._next;
            }
            return null;
        }

        if (ni>0) return null; //如果是ab，但是节点是abc，就会进入这一步
        if (node!=null && node._key==null)
            return null;
        return node;
    }

    @Override
    public Object remove(Object key) {
        if (key==null)
            return remove(null);
        return remove(key.toString());
    }

    //和找的逻辑差不多
    public Object remove(String key) {
        if (key==null) { //key为null自己有一个存储地方
            Object oldValue=_nullValue;
            if (_nullEntry!=null) {
                _entrySet.remove(_nullEntry);
                _nullEntry=null;
                _nullValue=null;
            }
            return oldValue;
        }

        Node node = _root;
        int ni=-1;

        // look for best match
        charLoop:
        for (int i=0;i<key.length();i++) {
            char c=key.charAt(i);

            // Advance node
            if (ni==-1) {
                ni=0;
                node=(node._children==null)?null:node._children[c%_width];
            }

            // While we have a node to try
            while (node!=null) {
                // If it is a matching node, goto next char
                if (node._char[ni]==c || _ignoreCase&&node._ochar[ni]==c) {
                    ni++;
                    if (ni==node._char.length)
                        ni=-1;
                    continue charLoop;
                }

                // No char match, so if mid node then no match at all.
                if (ni>0) return null;

                // try next in chain
                node=node._next;
            }
            return null;
        }

        if (ni>0) return null;
        if (node!=null && node._key==null)
            return null;

        Object old = node._value;
        _entrySet.remove(node);
        node._value=null;
        node._key=null;

        return old;
    }

    @Override
    public Set entrySet() {
        return _umEntrySet;
    }

    /* ------------------------------------------------------------ */
    @Override
    public int size() {
        return _entrySet.size();
    }

    /* ------------------------------------------------------------ */
    @Override
    public boolean isEmpty() {
        return _entrySet.isEmpty();
    }

    /* ------------------------------------------------------------ */
    //判断是否存在也是用找去判断，如果不想每次都遍历一遍，可以直接用get
    @Override
    public boolean containsKey(Object key) {
        if (key==null)
            return _nullEntry!=null;
        return
            getEntry(key.toString(),0,key==null?0:key.toString().length())!=null;
    }

    /* ------------------------------------------------------------ */
    @Override
    public void clear() {
        _root=new Node();
        _nullEntry=null;
        _nullValue=null;
        _entrySet.clear();
    }

    private static class Node implements Entry{
        char[] _char; //关键词部分
        char[] _ochar; //如果是大小写不敏感，那就会存储
        Node _next; //拉链法
        Node[] _children; //子节点，也可以说一个子哈希表
        String _key;
        Object _value;

        Node(){}

        /**
         * 1. 根据offset来截取s的char，放入char数组中；
         * 2. ochar，是在大小不敏感的时候，存储相反的字符，比如说A，就是a
         * @param ignoreCase
         * @param s
         * @param offset
         */
        Node(boolean ignoreCase, String s, int offset){
            int len = s.length() - offset; //截取的长度
            _char = new char[len];
            _ochar = new char[len];
            for(int i=0; i<len; i++){
                char c=s.charAt(offset+i);
                _char[i]=c;
                if (ignoreCase) {
                    char o=c;
                    if (Character.isUpperCase(c))
                        o=Character.toLowerCase(c);
                    else if (Character.isLowerCase(c))
                        o=Character.toUpperCase(c);
                    _ochar[i]=o;
                }
            }
        }

        /**
         * 分裂，按照偏移量来分；
         * 注意一下，分裂的前半部分就是公共部分，后半部分就是不相同的部分
         * @param map
         * @param offset
         * @return
         */
        Node split(StringMap map, int offset){
            //1. 创建一个新的节点用来存放分裂后的后半部
            Node split = new Node();
            int splitLen = _char.length - offset;

            //2. 将分裂的两个部分放入到对应的数组中
            //放到中间变量中
            char[] tmp = this._char;
            //分别创建
            this._char=new char[offset];
            split._char = new char[splitLen];
            System.arraycopy(tmp,0,this._char,0,offset);
            System.arraycopy(tmp,offset,split._char,0,splitLen);

            //3. copy ochar数组
            //这个好像一直都不是空的吧？判断这个可能是为了非初始化调用
            if (this._ochar!=null) {
                tmp=this._ochar;
                this._ochar=new char[offset];
                split._ochar = new char[splitLen];
                System.arraycopy(tmp,0,this._ochar,0,offset);
                System.arraycopy(tmp,offset,split._ochar,0,splitLen);
            }

            /**
             * 4. 给新节点赋值，置空原节点key
             * 这里要注意：
             * (1). 如果是，进入该方法都是为了分裂，而分裂是为了提取公共部分，所以被分裂的节点的
             * 键值对自然就会交给子节点携带，而原节点就需要另外说了
             * (2). 因为放入的可能是abc，abcde，那分裂后，就需要吧这个abc交给源节点
             * (3). 但是如果是因为abc，abd，这种情况，那分裂abc之后，ab就是没有键值对的
             */
            split._key=this._key;
            split._value=this._value;
            this._key=null;
            this._value=null;
            if (map._entrySet.remove(this))
                map._entrySet.add(split);

            //5. 分裂之后，交付子节点
            //解释一下，例如你原来节点是abc，分裂后，变成父节点ab，子节点c，那你abc的子节点不就是c的子节点
            split._children=this._children;
            //6. split节点是子节点，所以要重新定位
            this._children=new Node[map._width];
            this._children[split._char[0]%map._width]=split;
            if (split._ochar!=null && this._children[split._ochar[0]%map._width]!=split)
                this._children[split._ochar[0]%map._width]=split;

            return split;
        }

        public Object getKey(){return _key;}
        public Object getValue(){return _value;}
        public Object setValue(Object o){Object old=_value;_value=o;return old;}
    }

    private class NullEntry implements Entry{
        public Object getKey() {
            return null;
        }

        public Object getValue() {
            return StringMap.this._nullValue;
        }

        public Object setValue(Object o) {
            Object old = StringMap.this._nullValue;
            StringMap.this._nullValue = o;
            return old;
        }
    }
}
