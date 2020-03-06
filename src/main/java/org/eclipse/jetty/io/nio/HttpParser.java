package org.eclipse.jetty.io.nio;

import java.nio.ByteBuffer;

/**
 * http解析器
 */
public class HttpParser {
    private int state = -14;
    private long _contentPosition;
    SelectChannelEndPoint endPoint; //需要端点来获取Socket

    public HttpParser(SelectChannelEndPoint endPoint) {
        this.endPoint = endPoint;
    }

    public boolean isComplete(){
        return state == 0;
    }

    public boolean parseAvailable(){
        boolean progress = false;
        parseNext();

        return progress;
    }

    /**
     * 0-成功
     * 字节对应表：
     * <32和127 - 控制字符 13-回车键 10 换行键
     * 32 - 空格
     * 47 - 斜杠
     * 65~90 - A~Z
     * 97~122 - a~z
     * 示例
     * POST / HTTP/1.1
     * Content-Length: 0
     * Host: 127.0.0.1:8093
     * Connection: Keep-Alive
     * User-Agent: Apache-HttpClient/4.5.5 (Java/1.8.0_191)
     * Accept-Encoding: gzip,deflate
     */
    public int parseNext(){
        if(state == 0){
            return 0;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        endPoint.read(byteBuffer);
        byteBuffer.flip();
        byte[] b = byteBuffer.array();
        int len = b.length;

        //打印
        System.out.println(new String(b));

        int getIndex = 0;
        int readIndex = 0;
        while (getIndex<len&&len-->0&&state<0){
            byte ch = b[getIndex];
            switch (state){
                case -14:
                    if(ch > 32 || ch < 0){ //跳过所有的控制字符
                        byteBuffer.mark();
                        state = -13;
                    }
                    break;
                case -13: //解析http method
                    if(ch == 32){ //遇到空格，说明以及解析出请求方式了是GET还是POST或者其
                        byte[] tmp = new byte[getIndex - readIndex];
                        System.arraycopy(b, readIndex, tmp, 0, tmp.length);
                        readIndex = getIndex+1;
                        System.out.println("http method:" + new String(tmp));
                        state = -12;
                    }else if(ch < 32 && ch >= 0){ //出现控制字符
                        System.out.println("error byte is controller char");
                        return 0;
                    }
                    break;
                case -12:
                    if(ch < 32 && ch >= 0){
                        System.out.println("error byte is controller char");
                        return 0;
                    }else{
                        byteBuffer.mark();
                        state = -10;
                    }
                    break;
                case -10:
                    if(ch == 32){ //遇到下一个空格，说明以及遍历完了uri
                        byte[] tmp = new byte[getIndex - readIndex];
                        System.arraycopy(b, readIndex, tmp, 0, tmp.length);
                        readIndex = getIndex+1;
                        System.out.println("uri:" + new String(tmp));
                        state = -9;
                    }
                    break;
                case -9:
                    if (ch==13 || ch==10) {
                        byte[] tmp = new byte[getIndex - readIndex];
                        System.arraycopy(b, readIndex, tmp, 0, tmp.length);
                        readIndex = getIndex+1;
                        System.out.println("version:" + new String(tmp));
                        state = -6;
                    }
                    break;
                case -6:
                    int count = 1;
                    while(ch<=32 && ch>=0){
                        if(count == 3){
                            break;
                        }
                        count++;
                        readIndex++;
                        ch = b[++getIndex];
                    }
                    if(count == 3){
                        readIndex++;
                        state = -4;
                        break;
                    }
                    state = -5;
                    //heade5


                    break;
                case -5:
                    if (ch==13 || ch==10) {
                        byte[] tmp = new byte[getIndex - readIndex];
                        System.arraycopy(b, readIndex, tmp, 0, tmp.length);
                        readIndex = getIndex+1;
                        System.out.println(new String(tmp));
                        state = -6;
                    }
                    break;
                case -4:
                    if (ch<=32 && ch>=0) {
                        byte[] tmp = new byte[getIndex - readIndex];
                        System.arraycopy(b, readIndex, tmp, 0, tmp.length);
                        readIndex = getIndex+1;
                        System.out.println(new String(tmp));
                        state = -3;
                        break;
                    }
                default: break;
            }
            getIndex++;
        }

        return 1;
    }
}
