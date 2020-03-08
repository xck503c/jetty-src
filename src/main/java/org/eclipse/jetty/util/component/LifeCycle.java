package org.eclipse.jetty.util.component;

import java.util.EventListener;

/**
 * 生命周期
 */
public interface LifeCycle {
    void start() throws Exception;

    void stop() throws Exception;

    boolean isRunning(); //正在启动或者已经启动

    boolean isStarted(); //已经启动

    boolean isStarting(); //正在启动

    boolean isStopped(); //已经停止

    boolean isStopping(); //正在停止

    boolean isFailed(); //出现异常停止

    void addLifeCycleListener(Listener listner);
    void removeLifeCycleListener(Listener listner);

    interface Listener extends EventListener{
        void lifeCycleStarting(LifeCycle lifeCycle);

        void lifeCycleStarted(LifeCycle lifeCycle);

        void lifeCycleStopping(LifeCycle lifeCycle);

        void lifeCycleStopped(LifeCycle lifeCycle);

        void lifeCycleFailure(LifeCycle lifeCycle, Throwable throwable);
    }
}
