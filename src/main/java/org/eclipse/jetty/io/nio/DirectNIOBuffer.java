package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.AbstractBuffer;
import org.eclipse.jetty.io.Buffer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class DirectNIOBuffer extends AbstractBuffer implements NIOBuffer {
    protected final ByteBuffer _buf;
    private ReadableByteChannel _in;
    private InputStream _inStream;
    private WritableByteChannel _out;
    private OutputStream _outStream;

    public DirectNIOBuffer(int size) {
        super(2, false);
        this._buf = ByteBuffer.allocateDirect(size);
        this._buf.position(0);
        this._buf.limit(this._buf.capacity());
    }

    public DirectNIOBuffer(ByteBuffer buffer, boolean immutable) {
        super(immutable ? 0 : 2, false);
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException();
        } else {
            this._buf = buffer;
            this.setGetIndex(buffer.position());
            this.setPutIndex(buffer.limit());
        }
    }

    public DirectNIOBuffer(File file) throws IOException {
        super(1, false);
        FileInputStream fis = null;
        FileChannel fc = null;

        try {
            fis = new FileInputStream(file);
            fc = fis.getChannel();
            this._buf = fc.map(FileChannel.MapMode.READ_ONLY, 0L, file.length());
            this.setGetIndex(0);
            this.setPutIndex((int)file.length());
            this._access = 0;
        } finally {
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException var10) {
                }
            }

            fis.close();
        }

    }

    public boolean isDirect() {
        return true;
    }

    public byte[] array() {
        return null;
    }

    public int capacity() {
        return this._buf.capacity();
    }

    public byte peek(int position) {
        return this._buf.get(position);
    }

    public int peek(int index, byte[] b, int offset, int length) {
        int l = length;
        if (index + length > this.capacity()) {
            l = this.capacity() - index;
            if (l == 0) {
                return -1;
            }
        }

        if (l < 0) {
            return -1;
        } else {
            try {
                this._buf.position(index);
                this._buf.get(b, offset, l);
            } finally {
                this._buf.position(0);
            }

            return l;
        }
    }

    public void poke(int index, byte b) {
        if (this.isReadOnly()) {
            throw new IllegalStateException("READONLY");
        } else if (index < 0) {
            throw new IllegalArgumentException("index<0: " + index + "<0");
        } else if (index > this.capacity()) {
            throw new IllegalArgumentException("index>capacity(): " + index + ">" + this.capacity());
        } else {
            this._buf.put(index, b);
        }
    }

    public int poke(int index, Buffer src) {
        if (this.isReadOnly()) {
            throw new IllegalStateException("READONLY");
        } else {
            byte[] array = src.array();
            if (array != null) {
                return this.poke(index, array, src.getIndex(), src.length());
            } else {
                Buffer src_buf = src.buffer();
                if (src_buf instanceof DirectNIOBuffer) {
                    ByteBuffer src_bytebuf = ((DirectNIOBuffer)src_buf)._buf;
                    if (src_bytebuf == this._buf) {
                        src_bytebuf = this._buf.duplicate();
                    }

                    int var8;
                    try {
                        this._buf.position(index);
                        int space = this._buf.remaining();
                        int length = src.length();
                        if (length > space) {
                            length = space;
                        }

                        src_bytebuf.position(src.getIndex());
                        src_bytebuf.limit(src.getIndex() + length);
                        this._buf.put(src_bytebuf);
                        var8 = length;
                    } finally {
                        this._buf.position(0);
                        src_bytebuf.limit(src_bytebuf.capacity());
                        src_bytebuf.position(0);
                    }

                    return var8;
                } else {
                    return super.poke(index, src);
                }
            }
        }
    }

    public int poke(int index, byte[] b, int offset, int length) {
        if (this.isReadOnly()) {
            throw new IllegalStateException("READONLY");
        } else if (index < 0) {
            throw new IllegalArgumentException("index<0: " + index + "<0");
        } else {
            if (index + length > this.capacity()) {
                length = this.capacity() - index;
                if (length < 0) {
                    throw new IllegalArgumentException("index>capacity(): " + index + ">" + this.capacity());
                }
            }

            int var6;
            try {
                this._buf.position(index);
                int space = this._buf.remaining();
                if (length > space) {
                    length = space;
                }

                if (length > 0) {
                    this._buf.put(b, offset, length);
                }

                var6 = length;
            } finally {
                this._buf.position(0);
            }

            return var6;
        }
    }

    public ByteBuffer getByteBuffer() {
        return this._buf;
    }

    public int readFrom(InputStream in, int max) throws IOException {
        if (this._in == null || !this._in.isOpen() || in != this._inStream) {
            this._in = Channels.newChannel(in);
            this._inStream = in;
        }

        if (max < 0 || max > this.space()) {
            max = this.space();
        }

        int p = this.putIndex();

        byte var8;
        try {
            int len = 0;
            int total = 0;
            int available = max;
            int var7 = 0;

            while(total < max) {
                this._buf.position(p);
                this._buf.limit(p + available);
                len = this._in.read(this._buf);
                if (len < 0) {
                    this._in = null;
                    this._inStream = in;
                    break;
                }

                if (len > 0) {
                    p += len;
                    total += len;
                    available -= len;
                    this.setPutIndex(p);
                    var7 = 0;
                } else if (var7++ > 1) {
                    break;
                }

                if (in.available() <= 0) {
                    break;
                }
            }

            if (len >= 0 || total != 0) {
                int var14 = total;
                return var14;
            }

            var8 = -1;
        } catch (IOException var12) {
            this._in = null;
            this._inStream = in;
            throw var12;
        } finally {
            if (this._in != null && !this._in.isOpen()) {
                this._in = null;
                this._inStream = in;
            }

            this._buf.position(0);
            this._buf.limit(this._buf.capacity());
        }

        return var8;
    }

    public void writeTo(OutputStream out) throws IOException {
        if (this._out == null || !this._out.isOpen() || out != this._outStream) {
            this._out = Channels.newChannel(out);
            this._outStream = out;
        }

        ByteBuffer var2 = this._buf;
        synchronized(this._buf) {
            try {
                int var3 = 0;

                while(this.hasContent() && this._out.isOpen()) {
                    this._buf.position(this.getIndex());
                    this._buf.limit(this.putIndex());
                    int len = this._out.write(this._buf);
                    if (len < 0) {
                        break;
                    }

                    if (len > 0) {
                        this.skip(len);
                        var3 = 0;
                    } else if (var3++ > 1) {
                        break;
                    }
                }
            } catch (IOException var10) {
                this._out = null;
                this._outStream = null;
                throw var10;
            } finally {
                if (this._out != null && !this._out.isOpen()) {
                    this._out = null;
                    this._outStream = null;
                }

                this._buf.position(0);
                this._buf.limit(this._buf.capacity());
            }

        }
    }
}
