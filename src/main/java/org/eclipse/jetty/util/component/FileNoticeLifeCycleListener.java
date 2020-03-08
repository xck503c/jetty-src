package org.eclipse.jetty.util.component;

import java.io.FileWriter;
import java.io.IOException;

/**
 * 文件通知，生命周期变化监听器
 */
public class FileNoticeLifeCycleListener implements LifeCycle.Listener {
    //输出的位置
    private final String _filename;

    public FileNoticeLifeCycleListener(String filename){
        _filename = filename;
    }

    /**
     * 总的调用
     * @param action 状态
     * @param lifeCycle 当前组件
     */
    public void writeState(String action, LifeCycle lifeCycle){
        try {
            //true-表示追加
            FileWriter out = new FileWriter(_filename, true);
            out.append(action).append(" ").append(lifeCycle.toString()).append("\n");
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void lifeCycleStarting(LifeCycle event) {
        this.writeState("STARTING", event);
    }

    public void lifeCycleStarted(LifeCycle event) {
        this.writeState("STARTED", event);
    }

    public void lifeCycleFailure(LifeCycle event, Throwable cause) {
        this.writeState("FAILED", event);
    }

    public void lifeCycleStopping(LifeCycle event) {
        this.writeState("STOPPING", event);
    }

    public void lifeCycleStopped(LifeCycle event) {
        this.writeState("STOPPED", event);
    }
}
