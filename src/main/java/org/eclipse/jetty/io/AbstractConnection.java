package org.eclipse.jetty.io;

import java.io.IOException;

/**
 * 抽象Connection
 */
public abstract class AbstractConnection implements Connection{

    private final long timeStamp; //创建连接的时间
    protected EndPoint endPoint;

    public AbstractConnection(EndPoint endPoint){
        this.endPoint = endPoint;
        timeStamp = System.currentTimeMillis();
    }

    public AbstractConnection(EndPoint endPoint, long timeStamp){
        this.endPoint = endPoint;
        this.timeStamp = timeStamp;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public EndPoint getEndPoint() {
        return endPoint;
    }

    @Override
    public void onIdleExpired(long idleForMs){
        try {
            //超时了，先关掉输出流，这里如果先关闭输出就不能响应了？
            if(!endPoint.isInputShutdown() && !endPoint.isOutPutShutdown()){
                endPoint.shutdownOutput();
            }else {
                endPoint.close();
            }
        } catch (IOException e) {
            try {
                endPoint.close();
            } catch (IOException e1) {
            }
        }
    }
}
