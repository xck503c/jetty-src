package org.eclipse.jetty.io;

/**
 * 缓冲源：管理缓冲的回收和分配
 * 1. 表示一个缓冲池或者其他缓冲源，抽象特定类型的缓冲区的创建
 * 2. 支持大缓冲区和小缓冲区的概念，但是术语没有绝对意义，必须看上下文
 */
public interface Buffers {
    enum Type{
        BYTE_ARRAY, DIRECT, INDIRECT;
    }

    Buffer getHeader();

    Buffer getBuffer();

    /**
     * 租借指定大小缓冲区
     * @param size
     * @return
     */
    Buffer getBuffer(int size);

    /**
     * 归还租借的缓冲区
     * @param buffer
     */
    void returnBuffer(Buffer buffer);
}
