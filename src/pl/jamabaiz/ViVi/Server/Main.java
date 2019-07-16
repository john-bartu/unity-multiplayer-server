package pl.jamabaiz.ViVi.Server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws Exception {
        try (var listener = new ServerSocket(9000)) {
            System.out.println("Server running...");
            ExecutorService pool = Executors.newFixedThreadPool(20);
            while (true) {
                pool.execute(new Client(listener.accept()));
            }
        }
    }

    enum COMMAND {
        PING
    }

    private static class Client extends Thread {
        long lastAvailableTime = System.currentTimeMillis();
        volatile boolean shutdown = false;
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        Client(Socket socket) {
            this.socket = socket;
        }

        private void shutdown() {
            shutdown = true;
        }

        @Override

        public void run() {
            System.out.println("Connected: " + socket);
            try {

                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                Thread timeOutController = new Thread(() -> {
                    while ((System.currentTimeMillis() - lastAvailableTime) < 100000) {
                        try {
                            sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    System.out.println("TimeOut: " + socket + " not busy for 30+ seconds");
                    try {
                        inputStream.close();
                        outputStream.close();
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    shutdown();
                });

                timeOutController.start();

                while (socket.isConnected() && !shutdown) {

                    int length;

                    if ((length = inputStream.read()) == -1) break;

                    int commandId = inputStream.read();
                    COMMAND command = COMMAND.values()[commandId];

                    byte[] data = inputStream.readNBytes(length - 1);

                    System.out.println("CMD: " + commandId + " - " + Arrays.toString(data));

                    lastAvailableTime = System.currentTimeMillis();

                    switch (command) {
                        case PING: {
                            answerPing(data);
                            break;
                        }
                    }

                }

            } catch (Exception e) {
                System.out.println("Error: " + socket);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Closed: " + socket);
                }
                System.out.println("Closed: " + socket);
            }
        }

        private void sendPacket(COMMAND command, byte[] data) throws IOException {
            outputStream.write(data.length + 1);
            outputStream.write(command.ordinal());
            outputStream.write(data);

            System.out.println("[SEND]: " + command.ordinal() + Arrays.toString(data));
        }

        private void answerPing(byte[] data) throws IOException {
            if (new String(data).equals("ping")) {
                byte[] answerData = "pong".getBytes();
                sendPacket(COMMAND.PING, answerData);
            } else {
                throw new IOException("Message doesn't match ping");
            }

        }
    }

}
