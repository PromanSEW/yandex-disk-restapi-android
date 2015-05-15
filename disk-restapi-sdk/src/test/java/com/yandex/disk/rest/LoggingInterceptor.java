/*
* Copyright (c) 2015 Yandex
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.yandex.disk.rest;

import com.google.common.io.ByteStreams;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import okio.Buffer;

public class LoggingInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);

    private static final String SEND_PREFIX = " >> ";
    private static final String RECEIVE_PREFIX = " << ";

    private boolean logWire;

    public LoggingInterceptor(boolean logWire) {
        this.logWire = logWire;
        logger.info("REST API logging started: logWire=" + logWire);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String hash = Integer.toHexString(chain.hashCode());
        String sendPrefix = hash + SEND_PREFIX;
        String receivePrefix = hash + RECEIVE_PREFIX;

        if (logWire) {
            RequestBody requestBody = request.body();
            if (requestBody != null) {
                logger.info(sendPrefix + "request: " + requestBody);
                Buffer buffer = new Buffer();
                requestBody.writeTo(buffer);
                byte[] requestBuffer = ByteStreams.toByteArray(buffer.inputStream());
                logBuffer(sendPrefix, requestBuffer);
            }
            request = request.newBuilder()
                    .removeHeader("Accept-Encoding")
                    .addHeader("Accept-Encoding", "")
                    .build();
        }

        logger.info(sendPrefix + request.method() + " " + request.url());
        logger.info(sendPrefix + "on " + chain.connection());
        logHeaders(sendPrefix, request.headers());

        Response response = chain.proceed(request);
        logger.info(receivePrefix + response.protocol() + " " + response.code()
                + " " + response.message());
        logHeaders(receivePrefix, response.headers());

        if (logWire) {
            ResponseBody body = response.body();
            byte[] responseBuffer = ByteStreams.toByteArray(body.byteStream());
            response = response.newBuilder()
                    .body(ResponseBody.create(body.contentType(), responseBuffer))
                    .build();
            logBuffer(receivePrefix, responseBuffer);
        }

        return response;
    }

    private void logBuffer(String prefix, byte[] buf) {
        if (buf.length < 10240) {       // assume binary output: magic number from RestClientTest
            logger.info(prefix + new String(buf));
        } else {
            logger.info(prefix + "[" + buf.length + " bytes]");
        }
    }

    private void logHeaders(String prefix, Headers headers) {
        for (String name : headers.names()) {
            List<String> values = headers.values(name);
            for (String value : values) {
                logger.info(prefix + name + ": " + value);
            }
        }
    }
}