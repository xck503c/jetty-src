package org.eclipse.jetty.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public abstract class AbstractBuffer implements Buffer{

    protected final static String
            __IMMUTABLE = "IMMUTABLE",
            __READONLY = "READONLY",
            __READWRITE = "READWRITE",
            __VOLATILE = "VOLATILE";

    protected int getIndex;
    protected int putIndex;
    protected int markIndex;

    protected int hash;
    protected int _hashGet;
    protected int _hashPut;

    protected int _access; //访问模式
    protected boolean _volatile;

    //缓存
    protected View _view;
    protected String _string;

    public AbstractBuffer(int access, boolean isVolatile) {
        if (access == 0 && isVolatile) {
            throw new IllegalArgumentException("IMMUTABLE && VOLATILE");
        } else {
            this.setMarkIndex(-1);
            this._access = access;
            this._volatile = isVolatile;
        }
    }

    @Override
    public byte[] asArray(){
        int length = length();
        byte[] bytes = new byte[length<0?0:length];
        byte[] array = array(); //拿到完整数组
        if (array!=null) {
            System.arraycopy(array, getIndex, bytes, 0, bytes.length);
        }else {
            peek(getIndex, bytes, 0, bytes.length);
        }
        return bytes;
    }

    public ByteArrayBuffer duplicate(int access) {
        Buffer b=this.buffer();
        if (this instanceof Buffer.CaseInsensitve || b instanceof Buffer.CaseInsensitve)
            return new ByteArrayBuffer.CaseInsensitive(asArray(), 0, length(),access);
        else
            return new ByteArrayBuffer(asArray(), 0, length(), access);
    }

    public Buffer asNonVolatileBuffer() {
        return (Buffer)(!this.isVolatile() ? this : this.duplicate(this._access));
    }

    public Buffer asImmutableBuffer() {
        return (Buffer)(this.isImmutable() ? this : this.duplicate(0));
    }

    public Buffer asReadOnlyBuffer() {
        return (Buffer)(this.isReadOnly() ? this : new View(this, this.markIndex(), this.getIndex(), this.putIndex(), 1));
    }

    public Buffer asMutableBuffer() {
        if (!this.isImmutable()) {
            return this;
        } else {
            Buffer b = this.buffer();
            return (Buffer)(b.isReadOnly() ? this.duplicate(2) : new View(b, this.markIndex(), this.getIndex(), this.putIndex(), this._access));
        }
    }

    @Override
    public Buffer buffer(){
        return this;
    }

    @Override
    public void clear(){
        setMarkIndex(-1);
        setGetIndex(0);
        setPutIndex(0);
    }

    @Override
    public void compact(){
        if(isReadOnly()){
            throw new IllegalStateException(__READONLY);
        }

        int s = markIndex() >= 0 ? markIndex : getIndex;
        if(s > 0){
            byte[] array = array();
            int length = putIndex - s; //移动的长度
            if(length > 0){
                if(array!=null){
                    System.arraycopy(array(), s, array, 0, length);
                }else {
                    poke(0, peek(s, length));
                }
            }
            //设置mark为
            if (markIndex() > 0) setMarkIndex(markIndex() - s); //设置为0？
            setGetIndex(getIndex() - s); //为什么不直接写0
            setPutIndex(putIndex() - s);
        }
    }

    /**
     * 1. 等值比较
     * 2. instanceof比较
     * 3. 是否实现了CaseInsensitve==>equalsIgnoreCase
     * 4. 长度比较
     * 5. 是否实现了AbstractBuffer，hash值比较
     * 6. 利用peek遍历比较
     * @param obj
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (obj==this)
            return true;

        // reject non buffers;
        if (obj == null || !(obj instanceof Buffer)) return false;
        Buffer b = (Buffer) obj;

        if (this instanceof Buffer.CaseInsensitve ||  b instanceof Buffer.CaseInsensitve)
            return equalsIgnoreCase(b);

        // reject different lengths
        if (b.length() != length()) return false;

        // reject AbstractBuffer with different hash value
        if (hash != 0 && obj instanceof AbstractBuffer) {
            AbstractBuffer ab = (AbstractBuffer) obj;
            if (ab.hash != 0 && hash != ab.hash) return false;
        }

        // Nothing for it but to do the hard grind.
        int get=getIndex();
        int bi=b.putIndex();
        for (int i = putIndex(); i-->get;) {
            byte b1 = peek(i);
            byte b2 = b.peek(--bi);
            if (b1 != b2) return false;
        }
        return true;
    }

    public boolean equalsIgnoreCase(Buffer b) {
        if (b==this)
            return true;

        // reject different lengths
        if (b.length() != length()) return false;

        // reject AbstractBuffer with different hash value
        if (hash != 0 && b instanceof AbstractBuffer) {
            AbstractBuffer ab = (AbstractBuffer) b;
            if (ab.hash != 0 && hash != ab.hash) return false;
        }

        // Nothing for it but to do the hard grind.
        int get=getIndex();
        int bi=b.putIndex();

        byte[] array = array();
        byte[] barray= b.array();
        //比较方式一个是用peek，一个是用array
        if (array!=null && barray!=null) {
            for (int i = putIndex(); i-->get;) {
                byte b1 = array[i];
                byte b2 = barray[--bi];
                if (b1 != b2) {
                    //转换为统一大写
                    if ('a' <= b1 && b1 <= 'z') b1 = (byte) (b1 - 'a' + 'A');
                    if ('a' <= b2 && b2 <= 'z') b2 = (byte) (b2 - 'a' + 'A');
                    if (b1 != b2) return false;
                }
            }
        }
        else {
            for (int i = putIndex(); i-->get;) {
                byte b1 = peek(i);
                byte b2 = b.peek(--bi);
                if (b1 != b2) {
                    if ('a' <= b1 && b1 <= 'z') b1 = (byte) (b1 - 'a' + 'A');
                    if ('a' <= b2 && b2 <= 'z') b2 = (byte) (b2 - 'a' + 'A');
                    if (b1 != b2) return false;
                }
            }
        }
        return true;
    }

    public byte get() {
        return peek(getIndex++);
    }

    @Override
    public int get(byte[] b, int offset, int length) {
        int gi = this.getIndex();
        int l = length();
        if(l<=0) return -1;

        if(length>l){
            length = l;
        }

        length = peek(gi, b, offset, length);
        if(length > 0){
            setGetIndex(gi+length);
        }
        return length;
    }

    @Override
    public Buffer get(int length) {
        int gi = this.getIndex();
        Buffer view = this.peek(gi, length);
        this.setGetIndex(gi + length);
        return view;
    }

    @Override
    public final int getIndex() {
        return getIndex;
    }

    @Override
    public boolean hasContent() {
        return putIndex > getIndex;
    }

    @Override
    public int hashCode() {
        if (hash == 0 || _hashGet!=getIndex || _hashPut!=putIndex) {
            int get=getIndex();
            byte[] array = array();
            if (array==null) {
                for (int i = putIndex(); i-- >get;) {
                    byte b = peek(i);
                    if ('a' <= b && b <= 'z')
                        b = (byte) (b - 'a' + 'A');
                    hash = 31 * hash + b;
                }
            }
            else {
                for (int i = putIndex(); i-- >get;) {
                    byte b = array[i];
                    if ('a' <= b && b <= 'z')
                        b = (byte) (b - 'a' + 'A');
                    hash = 31 * hash + b;
                }
            }
            if (hash == 0)
                hash = -1;
            _hashGet=getIndex;
            _hashPut=putIndex;

        }
        return hash;
    }

    @Override
    public boolean isImmutable() {
        return _access <= IMMUTABLE;
    }

    @Override
    public boolean isReadOnly() {
        return _access <= READONLY;
    }

    @Override
    public boolean isVolatile() {
        return _volatile;
    }

    @Override
    public int length(){
        return putIndex-getIndex;
    }

    @Override
    public void mark() {
        setMarkIndex(getIndex - 1);
    }

    @Override
    public void mark(int offset) {
        setMarkIndex(getIndex + offset);
    }

    @Override
    public int markIndex() {
        return markIndex;
    }

    @Override
    public byte peek() {
        return peek(getIndex);
    }

    public Buffer peek(int index, int length) {
        //如果不存在就创建，如果存在就根据条件更新，有什么意义
        if (this._view == null) {
            this._view = new View(this, -1, index, index + length, this.isReadOnly() ? 1 : 2);
        } else {
            this._view.update(this.buffer());
            this._view.setMarkIndex(-1);
            this._view.setGetIndex(0);
            this._view.setPutIndex(index + length);
            this._view.setGetIndex(index);
        }

        return this._view;
    }

    public int poke(int index, Buffer src) {
        this.hash = 0;
        int length = src.length(); //源buffer长度
        if (index + length > this.capacity()) { //限定长度不能超过现在缓冲区可以放入的长度
            length = this.capacity() - index;
        }

        byte[] src_array = src.array();
        byte[] dst_array = this.array();
        if (src_array != null && dst_array != null) {
            System.arraycopy(src_array, src.getIndex(), dst_array, index, length);
        } else {
            int s;
            int i;
            if (src_array != null) {
                s = src.getIndex();

                for(i = 0; i < length; ++i) {
                    this.poke(index++, src_array[s++]);
                }
            } else if (dst_array != null) {
                s = src.getIndex();

                for(i = 0; i < length; ++i) {
                    dst_array[index++] = src.peek(s++);
                }
            } else {
                s = src.getIndex();

                for(i = 0; i < length; ++i) {
                    this.poke(index++, src.peek(s++));
                }
            }
        }

        return length;
    }

    /**
     * 从原数组的指定偏移量，copy指定长度到目标数组
     * @param index
     * @param b
     * @param offset
     * @param length
     * @return
     */
    public int poke(int index, byte[] b, int offset, int length) {
        this.hash = 0;
        if (index + length > this.capacity()) {
            length = this.capacity() - index;
        }

        byte[] dst_array = this.array();
        if (dst_array != null) {
            System.arraycopy(b, offset, dst_array, index, length);
        } else {
            int s = offset;

            for(int i = 0; i < length; ++i) {
                this.poke(index++, b[s++]);
            }
        }

        return length;
    }

    public int put(Buffer src) {
        int pi = this.putIndex();
        int l = this.poke(pi, src);
        this.setPutIndex(pi + l);
        return l;
    }

    public void put(byte b) {
        int pi = this.putIndex();
        this.poke(pi, b);
        this.setPutIndex(pi + 1);
    }

    public int put(byte[] b, int offset, int length) {
        int pi = this.putIndex();
        int l = this.poke(pi, b, offset, length);
        this.setPutIndex(pi + l);
        return l;
    }

    public int put(byte[] b) {
        int pi = this.putIndex();
        int l = this.poke(pi, b, 0, b.length);
        this.setPutIndex(pi + l);
        return l;
    }

    public final int putIndex() {
        return this.putIndex;
    }

    public void reset() {
        if (this.markIndex() >= 0) {
            this.setGetIndex(this.markIndex());
        }

    }

    public void setGetIndex(int getIndex) {
        this.getIndex = getIndex;
        this.hash = 0;
    }

    public void setMarkIndex(int index) {
        this.markIndex = index;
    }

    public void setPutIndex(int putIndex) {
        this.putIndex = putIndex;
        this.hash = 0;
    }

    public int skip(int n) {
        if (this.length() < n) {
            n = this.length();
        }

        this.setGetIndex(this.getIndex() + n);
        return n;
    }

    public Buffer slice() {
        return this.peek(this.getIndex(), this.length());
    }

    public Buffer sliceFromMark() {
        return this.sliceFromMark(this.getIndex() - this.markIndex() - 1);
    }

    public Buffer sliceFromMark(int length) {
        if (this.markIndex() < 0) {
            return null;
        } else {
            Buffer view = this.peek(this.markIndex(), length);
            this.setMarkIndex(-1);
            return view;
        }
    }

    public int space() {
        return this.capacity() - this.putIndex;
    }

    public String toDetailString() {
        StringBuilder buf = new StringBuilder();
        buf.append("[");
        buf.append(super.hashCode());
        buf.append(",");
        buf.append(this.buffer().hashCode());
        buf.append(",m=");
        buf.append(this.markIndex());
        buf.append(",g=");
        buf.append(this.getIndex());
        buf.append(",p=");
        buf.append(this.putIndex());
        buf.append(",c=");
        buf.append(this.capacity());
        buf.append("]={");
        int i;
        if (this.markIndex() >= 0) {
            for(i = this.markIndex(); i < this.getIndex(); ++i) {
                byte b = this.peek(i);
//                TypeUtil.toHex(b, buf);
            }

            buf.append("}{");
        }

        i=0;
        for(i = this.getIndex(); i < this.putIndex(); ++i) {
            byte b = this.peek(i);
//            TypeUtil.toHex(b, buf);
            if (i++ == 50 && this.putIndex() - i > 20) {
                buf.append(" ... ");
                i = this.putIndex() - 20;
            }
        }

        buf.append('}');
        return buf.toString();
    }

    public String toString() {
        if (this.isImmutable()) {
            if (this._string == null) {
                this._string = new String(this.asArray(), 0, this.length());
            }

            return this._string;
        } else {
            return new String(this.asArray(), 0, this.length());
        }
    }

    public String toString(String charset) {
        try {
            byte[] bytes = this.array();
            return bytes != null ? new String(bytes, this.getIndex(), this.length(), charset) : new String(this.asArray(), 0, this.length(), charset);
        } catch (Exception var3) {
            return new String(this.asArray(), 0, this.length());
        }
    }

    public String toString(Charset charset) {
        try {
            byte[] bytes = this.array();
            return bytes != null ? new String(bytes, this.getIndex(), this.length(), charset) : new String(this.asArray(), 0, this.length(), charset);
        } catch (Exception var3) {
            return new String(this.asArray(), 0, this.length());
        }
    }

    public String toDebugString() {
        return this.getClass() + "@" + super.hashCode();
    }

    public void writeTo(OutputStream out) throws IOException {
        byte[] array = this.array();
        if (array != null) {
            out.write(array, this.getIndex(), this.length());
        } else {
            int len = this.length();
            byte[] buf = new byte[len > 1024 ? 1024 : len];

            int l;
            for(int offset = this.getIndex; len > 0; len -= l) {
                l = this.peek(offset, buf, 0, len > buf.length ? buf.length : len);
                out.write(buf, 0, l);
                offset += l;
            }
        }

        this.clear();
    }

    public int readFrom(InputStream in, int max) throws IOException {
        byte[] array = this.array();
        int s = this.space();
        if (s > max) {
            s = max;
        }

        if (array != null) {
            int l = in.read(array, this.putIndex, s);
            if (l > 0) {
                this.putIndex += l;
            }

            return l;
        } else {
            byte[] buf = new byte[s > 1024 ? 1024 : s];

            byte total;
            int l;
            for(total = 0; s > 0; s -= l) {
                l = in.read(buf, 0, buf.length);
                if (l < 0) {
                    return total > 0 ? total : -1;
                }

                int p = this.put(buf, 0, l);

                assert l == p;
            }

            return total;
        }
    }
}
