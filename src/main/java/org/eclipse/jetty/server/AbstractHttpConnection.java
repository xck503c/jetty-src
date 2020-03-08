package org.eclipse.jetty.server;

import org.eclipse.jetty.io.AbstractConnection;

/**
 * https://github.com/eclipse/jetty.project/blob/jetty-8.1.x/jetty-server/src/main/java/org/eclipse/jetty/server/AbstractHttpConnection.java
 * http://www.blogjava.net/DLevin/archive/2014/03/29/411667.html
 *
 * 1. 由Connector创建，表示http客户端到服务端的连接，HttpConnection。它的主要功能是
 * 将Rquest和Response实例与EndPoint相关联；
 * 2. Connection同时也是jetty在不使用pooling的情况下，回收对象的主要机制；
 * 包括Request，Response，HttpParser，HttpGenerator和HttpFields，实在翻译不来，后面看完代码再来翻译；
 * The {@link Request},  {@link Response}, {@link HttpParser}, {@link HttpGenerator}
 *  * and {@link HttpFields} instances are all recycled for the duraction of
 *  * a connection. Where appropriate, allocated buffers are also kept associated
 *  * with the connection via the parser and/or generator.
 * 3. 连接状态由三个独立的机制保持：请求状态，响应状态，继续状态；对于每个请求，这三个都要确保按照顺序
 * 全部完成；
 * 4. HttpConnection支持协议升级。
 * (1) 如果请求完成，响应代码为101(switch协议)，则会去检查请求属性
 * org.eclipse.jetty.io.Connection，以查看是否有新的连接实例。
 * (2) 如果是(if so)，则会从handle返回，并进一步处理。
 * (3) 对于不使用响应101的switching protocols(如CONNECT)，应该在handle前发送响应
 * 然后将状态码改为101。新连接的实现应该很小心地从(HttpParser)http.getParser()).getHeaderBuffer()
 * 和(HttpParser)http.getParser()).getBodyBuffer()提取缓存数据以初始化连接
 */
public abstract class AbstractHttpConnection extends AbstractConnection {

}
