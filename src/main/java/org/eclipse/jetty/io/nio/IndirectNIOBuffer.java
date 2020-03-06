package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.ByteArrayBuffer;

import java.nio.ByteBuffer;

public class IndirectNIOBuffer extends ByteArrayBuffer implements NIOBuffer {
    protected final ByteBuffer _buf;

    public IndirectNIOBuffer(int size) {
        super(size, 2, false);
        this._buf = ByteBuffer.wrap(this._bytes);
        this._buf.position(0);
        this._buf.limit(this._buf.capacity());
    }

    public IndirectNIOBuffer(ByteBuffer buffer, boolean immutable) {
        super(buffer.array(), 0, 0, immutable ? 0 : 2, false);
        if (buffer.isDirect()) {
            throw new IllegalArgumentException();
        } else {
            //这个构造函数很有意思，如果传入一个存在的buffer，先更新jetty自己的指针，然后在清理jdk的指针
            this._buf = buffer;
            this.getIndex = buffer.position();
            this.putIndex = buffer.limit();
            buffer.position(0);
            buffer.limit(buffer.capacity());
        }
    }

    public ByteBuffer getByteBuffer() {
        return this._buf;
    }

    public boolean isDirect() {
        return false;
    }
}
