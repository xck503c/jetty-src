package org.eclipse.jetty.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * https://github.com/eclipse/jetty.project/blob/jetty-8.1.x/jetty-io/src/main/java/org/eclipse/jetty/io/Buffer.java
 * jetty自己定义了一个，操作字节缓冲区的接口
 *
 * 1. 这是一个字节缓冲区，工作方式和字节FIFO类似。
 * 2. 缓冲区有两个指针，可以通过put和get进行操作。缓冲区的有效区间始终在getIndex和putIndex之间
 * 3. 该接口的设计和java.nio buffers类似但是并不依赖。后者可用于支持该接口实现。
 * 二者的区别在于java的需要访问一个put数据需要先flip
 * 4. 对于下列的不等式，对于该缓存区始终成立
 * markValue <= getIndex <= putIndex <= capacity
 */
public interface Buffer {
    int IMMUTABLE = 0;
    int READONLY = 1;
    int READWRITE = 2;
    boolean VOLATILE = true;
    boolean NON_VOLATILE = false;

    //返回底层字节数组
    byte[] array();

    //返回从getInex到putIndex区间的字节
    byte[] asArray();

    //获取底层缓冲区，也就是获取被包装的缓冲区，如果有的话，没有就返回自己就行了
    Buffer buffer();

    //a non volatile version of this <code>Buffer</code> value
    Buffer asNonVolatileBuffer();

    //返回只读Buffer(View)，也是从getInex-putIndex
    Buffer asReadOnlyBuffer();

    Buffer asImmutableBuffer();

    Buffer asMutableBuffer();

    //缓冲区容量，putIndex的最大值
    int capacity();

    //空闲空间，还可以放入多少个字节，capacity-putIndex
    int space();

    //清空，将两个指针置为0
    void clear();

    //压缩，将标记位置或者getIndex，到putIndex移动到开头
    void compact();

    //从getIndex上获取一个字节，并递增
    byte get();

    //批量获取，放入传递的字节数组中，返回实际读取的字节数
    int get(byte[] b, int offset, int length);

    Buffer get(int length);

    int getIndex();

    //是否还有读取的空间
    boolean hasContent();

    //比较
    boolean equalsIgnoreCase(Buffer buffer);

    boolean isImmutable();

    boolean isReadOnly();

    boolean isVolatile();

    //putIndex()-getIndex()
    int length();

    //标记当前getIndex
    void mark();

    void mark(int offset);

    int markIndex();

    //从getIndex上查看但是并不递增
    byte peek();

    //查看指定索引字节
    byte peek(int index);

    Buffer peek(int index, int length);

    int peek(int index, byte[] b, int offset, int length);

    //将src放入以指定index开始
    int poke(int index, Buffer src);

    void poke(int index, byte b);

    int poke(int index, byte b[], int offset, int length);

    int put(Buffer src);

    void put(byte b);

    int put(byte[] b,int offset, int length);

    int put(byte[] b);

    int putIndex();

    //将当前getIndex置为make标记
    void reset();

    void setGetIndex(int newStart);

    void setMarkIndex(int newMark);

    void setPutIndex(int newLimit);

    //获取的时候跳过字节数
    int skip(int n);

    //a volitile <code>Buffer</code> from the postion to the putIndex.
    Buffer slice();

    //mark->putIndex
    Buffer sliceFromMark();

    //mark->mark+len
    Buffer sliceFromMark(int length);

    //获取详细详细，状态和内容
    String toDetailString();

    //将当前缓冲区数据写入output
    void writeTo(OutputStream out) throws IOException;

    int readFrom(InputStream in, int max) throws IOException;

    String toString(String charset);

    String toString(Charset charset);

    //Buffers implementing this interface should be compared with case insensitive equals
    public interface CaseInsensitve{}
}
