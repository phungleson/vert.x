/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerFileUpload;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.HttpVersion;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetSocket;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpServerRequest implements HttpServerRequest {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpServerRequest.class);

  private final ServerConnection conn;
  private final HttpRequest request;
  private final HttpServerResponse response;

  private HttpVersion version;
  private String method;
  private String uri;
  private String path;
  private String query;
  private URI juri;

  private Handler<Buffer> dataHandler;
  private Handler<Throwable> exceptionHandler;

  //Cache this for performance
  private MultiMap params;
  private MultiMap headers;
  private URI absoluteURI;

  private NetSocket netSocket;
  private Handler<io.netty.handler.codec.http.multipart.Attribute> attrHandler;
  private Handler<HttpServerFileUpload> uploadHandler;
  private Handler<Void> endHandler;
  private MultiMap attributes = new CaseInsensitiveMultiMap();
  private final HttpPostRequestDecoder decoder;

  DefaultHttpServerRequest(final ServerConnection conn,
                           final HttpRequest request,
                           final HttpServerResponse response) {
    this.conn = conn;
    this.request = request;
    this.response = response;

    String contentType = request.headers().get(HttpHeaders.Names.CONTENT_TYPE);
    if (contentType != null) {
      if (contentType.toLowerCase().startsWith(HttpHeaders.Values.MULTIPART_FORM_DATA)
                  || contentType.toLowerCase().startsWith(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED)) {
        decoder = new HttpPostRequestDecoder(new DataFactory(), request);
      } else {
        decoder = null;
      }
    } else {
      decoder = null;
    }

  }


  @Override
  public HttpVersion version() {
    if (version == null) {
      io.netty.handler.codec.http.HttpVersion nettyVersion = request.getProtocolVersion();
      if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
        version = HttpVersion.HTTP_1_0;
      } else if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_1) {
        version = HttpVersion.HTTP_1_1;
      } else {
        throw new IllegalStateException("Unsupported HTTP version: " + nettyVersion);
      }
    }
    return version;
  }

  @Override
  public String method() {
    if (method == null) {
      method = request.getMethod().toString();
    }
    return method;
  }

  @Override
  public String uri() {
    if (uri == null) {
      uri = request.getUri();
    }
    return uri;
  }

  URI juri() {
    if (juri == null) {
      try {
        juri = new URI(uri());
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid uri " + uri()); //Should never happen
      }
    }
    return juri;
  }

  @Override
  public String path() {
    if (path == null) {
      path = juri().getPath();
    }
    return path;
  }

  @Override
  public String query() {
    if (query == null) {
      query = juri().getQuery();
    }
    return query;
  }

  @Override
  public HttpServerResponse response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    if (headers == null) {
      headers = new HttpHeadersAdapter(request.headers());
    }
    return headers;
  }

  @Override
  public MultiMap params() {
    if (params == null) {
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri());
      Map<String, List<String>> prms = queryStringDecoder.parameters();
      params = new CaseInsensitiveMultiMap();
      if (!prms.isEmpty()) {
        for (Map.Entry<String, List<String>> entry: prms.entrySet()) {
          params.add(entry.getKey(), entry.getValue());
        }
      }
    }
    return params;
  }

  @Override
  public HttpServerRequest dataHandler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
    return this;
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public HttpServerRequest pause() {
    conn.pause();
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    conn.resume();
    return this;
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  @Override
  public InetSocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public URI absoluteURI() {
    if (absoluteURI == null) {
      try {
        URI uri = juri();
        String scheme = uri.getScheme();
        if (scheme != null && (scheme.startsWith("http:") || scheme.startsWith("https"))) {
          absoluteURI = uri;
        } else {
          absoluteURI = new URI(conn.getServerOrigin() + uri);
        }
      } catch (URISyntaxException e) {
        log.error("Failed to create abs uri", e);
      }
    }
    return absoluteURI;
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.getPeerCertificateChain();
  }

  @Override
  public HttpServerRequest bodyHandler(final Handler<Buffer> bodyHandler) {
    final Buffer body = new Buffer();
    dataHandler(new Handler<Buffer>() {
      public void handle(Buffer buff) {
        body.appendBuffer(buff);
      }
    });
    endHandler(new VoidHandler() {
      public void handle() {
        bodyHandler.handle(body);
      }
    });
    return this;
  }

  @Override
  public NetSocket netSocket() {
    if (netSocket == null) {
      netSocket = conn.createNetSocket();
    }
    return netSocket;
  }

  void handleData(Buffer data) {
    if (decoder != null) {
      try {
        decoder.offer(new DefaultHttpContent(data.getByteBuf().duplicate()));
      } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
        handleException(e);
      }
    }
    if (dataHandler != null) {
      dataHandler.handle(data);
    }
  }


  void handleEnd() {
    if (decoder != null) {
      try {
        decoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
      } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
        handleException(e);
      }
    }
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    }
  }

  @Override
  public HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> handler) {
    this.uploadHandler = handler;
    return this;
  }

  @Override
  public MultiMap formAttributes() {
    return attributes;
  }

  private final static class NettyFileUpload implements FileUpload {

    private final DefaultHttpServerFileUpload upload;


    private String name;
    private String filename;
    private String contentType;
    private String contentTransferEncoding;
    private Charset charset;
    private boolean completed;

    private NettyFileUpload(DefaultHttpServerFileUpload upload, String name, String filename, String contentType, String contentTransferEncoding, Charset charset) {
      this.upload = upload;
      this.name = name;
      this.filename = filename;
      this.contentType = contentType;
      this.contentTransferEncoding = contentTransferEncoding;
      this.charset = charset;
    }

    @Override
    public void setContent(ByteBuf channelBuffer) throws IOException {
      completed = true;
      upload.receiveData(new Buffer(channelBuffer));
      upload.complete();
    }

    @Override
    public void addContent(ByteBuf channelBuffer, boolean last) throws IOException {
      upload.receiveData(new Buffer(channelBuffer));
      if (last) {
        completed = true;
        upload.complete();
      }
    }

    @Override
    public void setContent(File file) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCompleted() {
      return completed;
    }

    @Override
    public long length() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete() {
      throw new UnsupportedOperationException();
    }

    @Override
    public byte[] get() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf getChunk(int i) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getString() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getString(Charset charset) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setCharset(Charset charset) {
      this.charset = charset;
    }

    @Override
    public Charset getCharset() {
      return charset;
    }

    @Override
    public boolean renameTo(File file) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInMemory() {
      return false;
    }

    @Override
    public File getFile() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public HttpDataType getHttpDataType() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(InterfaceHttpData o) {
      return 0;
    }

    @Override
    public String getFilename() {
      return filename;
    }

    @Override
    public void setFilename(String filename) {
      this.filename = filename;
    }

    @Override
    public void setContentType(String contentType) {
      this.contentType = contentType;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public void setContentTransferEncoding(String contentTransferEncoding) {
      this.contentTransferEncoding = contentTransferEncoding;
    }

    @Override
    public String getContentTransferEncoding() {
      return contentTransferEncoding;
    }

    @Override
    public ByteBuf getByteBuf() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileUpload copy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileUpload retain() {
      return this;
    }

    @Override
    public FileUpload retain(int increment) {
      return this;
    }

    @Override
    public ByteBuf content() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int refCnt() {
      return 1;
    }

    @Override
    public boolean release() {
      return false;
    }

    @Override
    public boolean release(int decrement) {
      return false;
    }
  }


  private class InternalMemoryAttribute extends MemoryAttribute {

    private boolean notified;
    private InternalMemoryAttribute(String name) {
      super(name);
    }

    private InternalMemoryAttribute(String name, String value) throws IOException {
      super(name, value);
    }

    @Override
    public void setValue(String value) throws IOException {
      super.setValue(value);
      attributeCreated();
    }

    private void attributeCreated() {
      if (!notified && isCompleted()) {
        notified = true;
        attributes.add(getName(), getValue());
      }
    }

    @Override
    public void setContent(ByteBuf buffer) throws IOException {
      super.setContent(buffer);
      attributeCreated();
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
      super.setContent(inputStream);
      attributeCreated();
    }

    @Override
    public void setContent(File file) throws IOException {
      super.setContent(file);
      attributeCreated();
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last) throws IOException {
      super.addContent(buffer, last);
      attributeCreated();
    }
  }


  private class DataFactory implements HttpDataFactory {

    @Override
    public io.netty.handler.codec.http.multipart.Attribute createAttribute(HttpRequest httpRequest, String name) {
      return new InternalMemoryAttribute(name);
    }

    @Override
    public io.netty.handler.codec.http.multipart.Attribute createAttribute(HttpRequest httpRequest,
                                                                           String name, String value) {
      try {
        return new InternalMemoryAttribute(name, value);
      } catch (IOException e) {
        // Will never happen for in memory
        return null;
      }
    }

    @Override
    public FileUpload createFileUpload(HttpRequest httpRequest, String name, String filename, String contentType, String contentTransferEncoding, Charset charset, long size) {
      DefaultHttpServerFileUpload upload = new DefaultHttpServerFileUpload(conn.vertx(), DefaultHttpServerRequest.this, name, filename, contentType, contentTransferEncoding, charset,
                                                        size);
      NettyFileUpload nettyUpload = new NettyFileUpload(upload, name, filename, contentType,
                                                                 contentTransferEncoding, charset);
      if (uploadHandler != null) {
        uploadHandler.handle(upload);
      }
      return nettyUpload;

    }

    @Override
    public void removeHttpDataFromClean(HttpRequest httpRequest, InterfaceHttpData interfaceHttpData) {
    }

    @Override
    public void cleanRequestHttpDatas(HttpRequest httpRequest) {
    }

    @Override
    public void cleanAllHttpDatas() {
    }
  }
}
