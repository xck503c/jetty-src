package org.eclipse.jetty.io;

import java.io.IOException;

public interface Connection {
    Connection handle() throws IOException;
}
