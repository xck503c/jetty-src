package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.Buffer;

import java.nio.ByteBuffer;

public interface NIOBuffer extends Buffer {
    ByteBuffer getByteBuffer();

    boolean isDirect();
}
