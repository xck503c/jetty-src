package org.eclipse.jetty.io.main;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

public class HttpTest {
    public static void main(String[] args) throws Exception{
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost("http://127.0.0.1:8888");
        StringEntity entity = new StringEntity("{\"name\":\"xck\"}", "UTF-8");
        httpPost.setEntity(entity);

        httpPost.setHeader("Content-Type", "application/json;charset=utf8");
        httpClient.execute(httpPost);
    }
}
