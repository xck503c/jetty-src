package org.eclipse.jetty.io;

import java.io.IOException;

/**
 * 抽象Connection
 */
public abstract class   AbstractConnection implements Connection{

    private final long timeStamp; //创建连接的时间
    protected EndPoint _endp;

    public AbstractConnection(EndPoint endPoint){
        this._endp = endPoint;
        timeStamp = System.currentTimeMillis();
    }

    public AbstractConnection(EndPoint endPoint, long timeStamp){
        this._endp = endPoint;
        this.timeStamp = timeStamp;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public EndPoint getEndPoint() {
        return _endp;
    }

    @Override
    public void onIdleExpired(long idleForMs){
        try {
            //超时了，先关掉输出流，这里如果先关闭输出就不能响应了？
            if(!_endp.isInputShutdown() && !_endp.isOutPutShutdown()){
                _endp.shutdownOutput();
            }else {
                _endp.close();
            }
        } catch (IOException e) {
            try {
                _endp.close();
            } catch (IOException e1) {
            }
        }
    }
}
