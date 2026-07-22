package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.shared.config.MalwareScanProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Service
public class ClamdClient {

    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VERSION_COMMAND = "zVERSION\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_SIZE = 32 * 1024;
    private static final int MAX_RESPONSE_BYTES = 512;

    private final MalwareScanProperties properties;

    public ClamdClient(MalwareScanProperties properties) {
        this.properties = properties;
    }

    public String scanStream(byte[] content) throws IOException {
        try (Socket socket = openSocket()) {
            OutputStream out = socket.getOutputStream();
            out.write(INSTREAM_COMMAND);
            for (int offset = 0; offset < content.length; offset += CHUNK_SIZE) {
                int length = Math.min(CHUNK_SIZE, content.length - offset);
                out.write(intToBigEndian(length));
                out.write(content, offset, length);
            }
            out.write(intToBigEndian(0));
            out.flush();
            return readResponse(socket.getInputStream());
        }
    }

    public String version() throws IOException {
        try (Socket socket = openSocket()) {
            OutputStream out = socket.getOutputStream();
            out.write(VERSION_COMMAND);
            out.flush();
            return readResponse(socket.getInputStream());
        }
    }

    private Socket openSocket() throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(properties.host(), properties.port()), properties.timeoutMs());
            socket.setSoTimeout(properties.timeoutMs());
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream(MAX_RESPONSE_BYTES);
        byte[] buffer = new byte[MAX_RESPONSE_BYTES];
        int read;
        while (response.size() < MAX_RESPONSE_BYTES && (read = in.read(buffer)) > 0) {
            response.write(buffer, 0, read);
            if (buffer[read - 1] == 0) {
                break;
            }
        }
        if (response.size() == 0) {
            throw new IOException("clamd closed connection without response");
        }
        return response.toString(StandardCharsets.US_ASCII).replace("\0", "").trim();
    }

    private byte[] intToBigEndian(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }
}
