package org.eclipse.jetty.io.util.thread;

public interface ThreadPool {
    boolean dispatch(Runnable var1);

    int getThreads();

    int getIdleThreads();

    boolean isLowOnThreads();

    public interface SizedThreadPool extends ThreadPool {
        int getMinThreads();

        int getMaxThreads();

        void setMinThreads(int var1);

        void setMaxThreads(int var1);
    }
}
