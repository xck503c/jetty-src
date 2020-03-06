package org.eclipse.jetty.io.util.component;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractLifeCycle implements LifeCycle{
    private final Object _lock = new Object();

    private volatile int _state = 0;
    //定义状态
    private final int _FAILED = -1;
    private final int _STOPPED = 0;
    private final int _STARTING = 1;
    private final int _STARTED = 2;
    private final int _STOPPING = 3;
    //不让外部访问，子类可以访问，当前组件持有的监听器
    protected final CopyOnWriteArrayList<Listener> _listeners = new CopyOnWriteArrayList<Listener>();

    public AbstractLifeCycle() {}

    //给子类实现的，用于初始化和销毁资源用(因为有些子类不实现所以这里给出默认实现)
    //调用通过start/stop方法统一调用，所以用protected，不能通过对象访问
    protected void doStart() throws Exception {}
    protected void doStop() throws Exception {}

    //为了避免被继承所以用final修饰，启动入口
    public final void start () throws Exception{
        //为了避免并发问题，这里同步
        synchronized (_lock){
            //若已经启动了，则不再启动
            if(_state == _STARTED || _state == _STARTING) return;

            try {
                setStarting();
                doStart();
                setStarted();
            } catch (Exception e) {
                setFailed(e);
                throw e;
            } catch (Error error){
                setFailed(error);
                throw error;
            }
        }
    }

    public final void stop () throws Exception{
        synchronized (_lock){
            if(_state == _STOPPING || _state == _STOPPED) return;

            try {
                setStopping();
                doStop();
                setStopped();
            } catch (Exception e) {
                setFailed(e);
                throw e;
            } catch (Error error){
                setFailed(error);
                throw error;
            }
        }
    }

    public boolean isRunning() { //是否启动了
        int state = this._state; //为了避免中途被改变
        return state == _STARTED || state == _STARTING;
    }
    //五种状态
    public boolean isStarted() { return this._state == _STARTED; }
    public boolean isStarting() { return this._state == _STARTING; }
    public boolean isStopping() { return this._state == _STOPPING; }
    public boolean isStopped() { return this._state == _STOPPED; }
    public boolean isFailed() { return this._state == _FAILED; }

    public void addLifeCycleListener(Listener listener){
        _listeners.add(listener);
    }
    public void removeLifeCycleListener(Listener listener){
        _listeners.remove(listener);
    }

    //五种状态解释
    public String getState() {
        switch(this._state) {
            case _FAILED:
                return "FAILED";
            case _STOPPED:
                return "STOPPED";
            case _STARTING:
                return "STARTING";
            case _STARTED:
                return "STARTED";
            case _STOPPING:
                return "STOPPING";
            default:
                return null;
        }
    }

    public static String getState(LifeCycle lc) {
        if (lc.isStarting()) {
            return "STARTING";
        } else if (lc.isStarted()) {
            return "STARTED";
        } else if (lc.isStopping()) {
            return "STOPPING";
        } else {
            return lc.isStopped() ? "STOPPED" : "FAILED";
        }
    }

    //五种set方法设置状态，为了避免被访问设置为private
    private void setStarted(){
        _state = _STARTED;
        //状态变更，通知listener调用指定方法
        Iterator i$ = this._listeners.iterator();

        while(i$.hasNext()) {
            Listener listener = (Listener)i$.next();
            listener.lifeCycleStarted(this);
        }
    }

    private void setStarting(){
        _state = _STARTING;
        Iterator i$ = this._listeners.iterator();

        while(i$.hasNext()) {
            Listener listener = (Listener)i$.next();
            listener.lifeCycleStarting(this);
        }
    }

    private void setStopping(){
        _state = _STOPPING;
        Iterator i$ = this._listeners.iterator();

        while(i$.hasNext()) {
            Listener listener = (Listener)i$.next();
            listener.lifeCycleStopping(this);
        }
    }

    private void setStopped(){
        _state = _STOPPED;
        Iterator i$ = this._listeners.iterator();

        while(i$.hasNext()) {
            Listener listener = (Listener)i$.next();
            listener.lifeCycleStopped(this);
        }
    }

    private void setFailed(Throwable th){
        _state = _FAILED;
        Iterator i$ = this._listeners.iterator();

        while(i$.hasNext()) {
            Listener listener = (Listener)i$.next();
            listener.lifeCycleFailure(this, th);
        }
    }

    //无论咋样，作为一个抽象类都要给个默认实现
    //监听器的作用：在状态更改的时候调用，换句话说就是：状态监听器
    //e.g FileNoticeLifeCycleListener：
    //  在状态变更的时候，调用当前组件(具有生命周期)所持有的监听器，输出到文件中
    //  action(state) lifecycle.toString
    public abstract static class AbstractLifeCycleListener implements Listener {
        public AbstractLifeCycleListener() {
        }

        public void lifeCycleFailure(LifeCycle event, Throwable cause) {
        }

        public void lifeCycleStarted(LifeCycle event) {
        }

        public void lifeCycleStarting(LifeCycle event) {
        }

        public void lifeCycleStopped(LifeCycle event) {
        }

        public void lifeCycleStopping(LifeCycle event) {
        }
    }
}
