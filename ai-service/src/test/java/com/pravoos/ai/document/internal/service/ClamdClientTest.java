package com.pravoos.ai.document.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.ai.shared.config.MalwareScanProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ClamdClientTest {

  private static byte[] readUntilNullTerminatedChunkedPayload(InputStream in) throws IOException {
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    while (true) {
      byte[] lengthBytes = in.readNBytes(4);
      int length =
          ((lengthBytes[0] & 0xFF) << 24)
              | ((lengthBytes[1] & 0xFF) << 16)
              | ((lengthBytes[2] & 0xFF) << 8)
              | (lengthBytes[3] & 0xFF);
      if (length == 0) {
        return payload.toByteArray();
      }
      payload.write(in.readNBytes(length));
    }
  }

  @Test
  void scanStream_sendsInstreamProtocolAndReturnsCleanResponse() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      CompletableFuture<byte[]> received =
          CompletableFuture.supplyAsync(
              () -> {
                try (Socket socket = serverSocket.accept()) {
                  InputStream in = socket.getInputStream();
                  byte[] command =
                      in.readNBytes("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII).length);
                  byte[] payload = readUntilNullTerminatedChunkedPayload(in);
                  OutputStream out = socket.getOutputStream();
                  out.write("stream: OK\0".getBytes(StandardCharsets.US_ASCII));
                  out.flush();
                  ByteArrayOutputStream combined = new ByteArrayOutputStream();
                  combined.write(command);
                  combined.write(payload);
                  return combined.toByteArray();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });

      ClamdClient client =
          new ClamdClient(
              new MalwareScanProperties("localhost", serverSocket.getLocalPort(), 2000, false, 30));
      String response = client.scanStream("payload-bytes".getBytes(StandardCharsets.UTF_8));

      assertThat(response).isEqualTo("stream: OK");
      byte[] receivedBytes = received.join();
      assertThat(new String(receivedBytes, StandardCharsets.US_ASCII))
          .startsWith("zINSTREAM\0")
          .contains("payload-bytes");
    }
  }

  @Test
  void scanStream_reportsInfectedResponse() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      CompletableFuture<Void> serverDone =
          CompletableFuture.runAsync(
              () -> {
                try (Socket socket = serverSocket.accept()) {
                  InputStream in = socket.getInputStream();
                  in.readNBytes("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII).length);
                  readUntilNullTerminatedChunkedPayload(in);
                  OutputStream out = socket.getOutputStream();
                  out.write(
                      "stream: Eicar-Test-Signature FOUND\0".getBytes(StandardCharsets.US_ASCII));
                  out.flush();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });

      ClamdClient client =
          new ClamdClient(
              new MalwareScanProperties("localhost", serverSocket.getLocalPort(), 2000, false, 30));
      String response = client.scanStream("eicar".getBytes(StandardCharsets.UTF_8));

      assertThat(response).isEqualTo("stream: Eicar-Test-Signature FOUND");
      serverDone.join();
    }
  }

  @Test
  void version_sendsVersionCommandAndReturnsResponse() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      CompletableFuture<byte[]> received =
          CompletableFuture.supplyAsync(
              () -> {
                try (Socket socket = serverSocket.accept()) {
                  InputStream in = socket.getInputStream();
                  byte[] command =
                      in.readNBytes("zVERSION\0".getBytes(StandardCharsets.US_ASCII).length);
                  OutputStream out = socket.getOutputStream();
                  out.write("ClamAV 1.2.0/27000\0".getBytes(StandardCharsets.US_ASCII));
                  out.flush();
                  return command;
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });

      ClamdClient client =
          new ClamdClient(
              new MalwareScanProperties("localhost", serverSocket.getLocalPort(), 2000, false, 30));
      String response = client.version();

      assertThat(response).isEqualTo("ClamAV 1.2.0/27000");
      assertThat(new String(received.join(), StandardCharsets.US_ASCII)).isEqualTo("zVERSION\0");
    }
  }

  @Test
  void scanStream_throwsIoException_whenServerUnreachable() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      int unusedPort = serverSocket.getLocalPort();
      serverSocket.close();

      ClamdClient client =
          new ClamdClient(new MalwareScanProperties("localhost", unusedPort, 500, false, 30));

      assertThatThrownBy(() -> client.scanStream("payload".getBytes(StandardCharsets.UTF_8)))
          .isInstanceOf(IOException.class);
    }
  }

  @Test
  void scanStream_throwsIoException_whenServerClosesWithoutResponse() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      CompletableFuture<Void> serverDone =
          CompletableFuture.runAsync(
              () -> {
                try (Socket socket = serverSocket.accept()) {
                  InputStream in = socket.getInputStream();
                  in.readNBytes("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII).length);
                  readUntilNullTerminatedChunkedPayload(in);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });

      ClamdClient client =
          new ClamdClient(
              new MalwareScanProperties("localhost", serverSocket.getLocalPort(), 2000, false, 30));

      assertThatThrownBy(() -> client.scanStream("payload".getBytes(StandardCharsets.UTF_8)))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("closed connection");
      serverDone.join();
    }
  }
}
