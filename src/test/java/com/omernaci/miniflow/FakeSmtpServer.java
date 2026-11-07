package com.omernaci.miniflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class FakeSmtpServer implements AutoCloseable {

    record Mail(String from, List<String> to, String data) {
    }

    private final ServerSocket socket;
    private final Thread thread;
    final List<Mail> mails = new CopyOnWriteArrayList<>();

    FakeSmtpServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        thread = new Thread(this::acceptLoop, "fake-smtp");
        thread.setDaemon(true);
        thread.start();
    }

    int port() {
        return socket.getLocalPort();
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                handle(client);
            } catch (IOException e) {
                if (socket.isClosed()) {
                    return;
                }
            }
        }
    }

    private void handle(Socket client) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        Writer out = new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8);
        reply(out, "220 fake-smtp ESMTP");
        String from = null;
        List<String> to = new CopyOnWriteArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            String upper = line.toUpperCase();
            if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
                reply(out, "250 fake-smtp");
            } else if (upper.startsWith("MAIL FROM:")) {
                from = line.substring(10).trim();
                reply(out, "250 OK");
            } else if (upper.startsWith("RCPT TO:")) {
                to.add(line.substring(8).trim());
                reply(out, "250 OK");
            } else if (upper.equals("DATA")) {
                reply(out, "354 End data with <CR><LF>.<CR><LF>");
                StringBuilder data = new StringBuilder();
                String d;
                while ((d = in.readLine()) != null && !d.equals(".")) {
                    data.append(d.startsWith("..") ? d.substring(1) : d).append("\n");
                }
                mails.add(new Mail(from, List.copyOf(to), data.toString()));
                to.clear();
                reply(out, "250 OK queued");
            } else if (upper.equals("QUIT")) {
                reply(out, "221 Bye");
                return;
            } else {
                reply(out, "250 OK");
            }
        }
    }

    private static void reply(Writer out, String text) throws IOException {
        out.write(text + "\r\n");
        out.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
