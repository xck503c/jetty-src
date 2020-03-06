package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.AbstractBuffer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

public class RandomAccessFileBuffer extends AbstractBuffer {
    final RandomAccessFile _file;
    final FileChannel _channel;
    final int _capacity;

    public RandomAccessFileBuffer(File file) throws FileNotFoundException {
        super(2, true);

        assert file.length() <= 2147483647L;

        this._file = new RandomAccessFile(file, "rw");
        this._channel = this._file.getChannel();
        this._capacity = 2147483647;
        this.setGetIndex(0);
        this.setPutIndex((int)file.length());
    }

    public RandomAccessFileBuffer(File file, int capacity) throws FileNotFoundException {
        super(2, true);

        assert (long)capacity >= file.length();

        assert file.length() <= 2147483647L;

        this._capacity = capacity;
        this._file = new RandomAccessFile(file, "rw");
        this._channel = this._file.getChannel();
        this.setGetIndex(0);
        this.setPutIndex((int)file.length());
    }

    public RandomAccessFileBuffer(File file, int capacity, int access) throws FileNotFoundException {
        super(access, true);

        assert (long)capacity >= file.length();

        assert file.length() <= 2147483647L;

        this._capacity = capacity;
        this._file = new RandomAccessFile(file, access == 2 ? "rw" : "r");
        this._channel = this._file.getChannel();
        this.setGetIndex(0);
        this.setPutIndex((int)file.length());
    }

    public byte[] array() {
        return null;
    }

    public int capacity() {
        return this._capacity;
    }

    public void clear() {
        try {
            RandomAccessFile var1 = this._file;
            synchronized(this._file) {
                super.clear();
                this._file.setLength(0L);
            }
        } catch (Exception var4) {
            throw new RuntimeException(var4);
        }
    }

    public byte peek() {
        RandomAccessFile var1 = this._file;
        synchronized(this._file) {
            byte var10000;
            try {
                if ((long)this.getIndex != this._file.getFilePointer()) {
                    this._file.seek((long)this.getIndex);
                }

                var10000 = this._file.readByte();
            } catch (Exception var4) {
                throw new RuntimeException(var4);
            }

            return var10000;
        }
    }

    public byte peek(int index) {
        RandomAccessFile var2 = this._file;
        synchronized(this._file) {
            byte var10000;
            try {
                this._file.seek((long)index);
                var10000 = this._file.readByte();
            } catch (Exception var5) {
                throw new RuntimeException(var5);
            }

            return var10000;
        }
    }

    public int peek(int index, byte[] b, int offset, int length) {
        RandomAccessFile var5 = this._file;
        synchronized(this._file) {
            int var10000;
            try {
                this._file.seek((long)index);
                var10000 = this._file.read(b, offset, length);
            } catch (Exception var8) {
                throw new RuntimeException(var8);
            }

            return var10000;
        }
    }

    public void poke(int index, byte b) {
        RandomAccessFile var3 = this._file;
        synchronized(this._file) {
            try {
                this._file.seek((long)index);
                this._file.writeByte(b);
            } catch (Exception var6) {
                throw new RuntimeException(var6);
            }

        }
    }

    public int poke(int index, byte[] b, int offset, int length) {
        RandomAccessFile var5 = this._file;
        synchronized(this._file) {
            int var10000;
            try {
                this._file.seek((long)index);
                this._file.write(b, offset, length);
                var10000 = length;
            } catch (Exception var8) {
                throw new RuntimeException(var8);
            }

            return var10000;
        }
    }

    public int writeTo(WritableByteChannel channel, int index, int length) throws IOException {
        RandomAccessFile var4 = this._file;
        synchronized(this._file) {
            return (int)this._channel.transferTo((long)index, (long)length, channel);
        }
    }
}
