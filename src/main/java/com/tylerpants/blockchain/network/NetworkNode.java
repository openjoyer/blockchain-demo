package com.tylerpants.blockchain.network;

import com.tylerpants.blockchain.Block;
import com.tylerpants.blockchain.MessageException;
import com.tylerpants.blockchain.MessageHeader;
import com.tylerpants.blockchain.util.Utils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

public class NetworkNode {
    private static final int DEFAULT_PORT = 8333;
    private static final String NETWORK_MAGIC = "f9beb4d9";
    private static final int MAX_PEERS = 125;

    private final String host;
    private final int port;
    private final Map<Socket, PeerInfo> connectedPeers = new ConcurrentHashMap<>();
    private final BlockingQueue<NetworkMessage> messageQueue = new LinkedBlockingQueue<>();
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    // Храним цепочку блоков (упрощённо)
    private final List<Block> blockchain = Collections.synchronizedList(new ArrayList<>());

    public NetworkNode(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
        System.out.println("Node started on " + host + " : " + port);

        // Поток для приёма входящих соединений
        new Thread(this::acceptConnections).start();

        // Поток для обработки сообщений
        new Thread(this::processMessages).start();

        // Поток для поддержания соединений
        new Thread(this::maintainConnections).start();
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket peerSocket = serverSocket.accept();
                if (connectedPeers.size() < MAX_PEERS) {
                    PeerInfo peer = new PeerInfo(peerSocket);
                    System.out.println("New connection with socket: " + peer.getSocket().getInetAddress());
                    connectedPeers.put(peerSocket, peer);
                    executor.execute(() -> handlePeer(peer));
                } else {
                    peerSocket.close();
                }
            } catch (IOException e) {
                if (running) System.err.println("Accept error: " + e.getMessage());
            }
        }
    }

    private void handlePeer(PeerInfo peer) {
        try (InputStream in = peer.getSocket().getInputStream();
             OutputStream out = peer.getSocket().getOutputStream()) {

            // Обмен версиями
            sendVersionMessage(out, peer);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while (running && (bytesRead = in.read(buffer)) != -1) {
                byte[] message = Arrays.copyOf(buffer, bytesRead);
                messageQueue.put(new NetworkMessage(peer, message));
            }
        } catch (Exception e) {
            System.out.println("??????????????????????");
            System.err.println("Peer handling error: " + e.getMessage());
        } finally {
            connectedPeers.remove(peer.getSocket());
            try { peer.getSocket().close(); } catch (IOException ignored) {}
        }
    }

    private void processMessages() {
        while (running) {
            try {
                NetworkMessage msg = messageQueue.take();
                parseMessage(msg.getPeer(), msg.getData());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (MessageException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     *  ДОДЕЛАТЬ !!!!!!!!!!!!!!!!!!!!!!!!
     */
    private void parseMessage(PeerInfo peer, byte[] data) throws MessageException {
        // Реальная реализация должна разбирать заголовки Bitcoin-сообщений
        System.out.println("Received message from " + peer.getSocket().getInetAddress() + ": " + Utils.bytesToHex(data));


        if (verifyMessage(data)) {
            try (OutputStream out = peer.getSocket().getOutputStream()) {
                MessageHeader header = parseHeader(data);

                System.out.println(header);
                String command = header.getCommand();

                switch (command) {
                    case "version":
                        sendVerackMessage(out, peer);
                        break;
                    case "verack":
                        System.out.println("Verack!");
                        break;
                    case "tx":
                        break;
                    case "ping":
                        System.out.println("received Ping!");
                        sendPong(out, peer);
                        break;
                    case "pong":
                        System.out.println("received Pong!");
                        break;
                }
                // Здесь должна быть логика обработки разных типов сообщений:
                // version, verack, addr, inv, getdata, tx, block и т.д.

                // Здесь должна быть разборка сообщений по типам:
                // 1. Проверить magic number (f9beb4d9)
                // 2. Определить тип сообщения (первые 12 байт)
                // 3. Обработать payload

                //        version → обмен параметрами
                //        tx  →  передача транзакции
                //        verack → подтверждение
                //        block →  передача блока
                //        ping/pong → поддержание соединения
                //
                //        addr → список узлов
                //
                //        inv/getdata → обмен данными
            } catch (IOException e) {
                System.out.println("!!!!!!!!");;
                System.err.println("Peer handling error: " + e.getMessage());
            }
        }
        else {
            System.err.println("error receiving message");
        }
    }


    public boolean verifyMessage(byte[] fullMessage) {
        if (fullMessage.length < 24) {
            return false;
        }

        // Проверка Magic
        byte[] magic = Arrays.copyOfRange(fullMessage, 0, 4);
        if (!Arrays.equals(magic, Utils.hexStringToByteArray(NETWORK_MAGIC))) {
            return false;
        }

        // Получаем payload
        int payloadLength = ByteBuffer.wrap(fullMessage, 16, 4).getInt();
        if (fullMessage.length != 24 + payloadLength) {
            return false;
        }

        byte[] payload = Arrays.copyOfRange(fullMessage, 24, 24 + payloadLength);
        byte[] receivedChecksum = Arrays.copyOfRange(fullMessage, 20, 24);
        byte[] calculatedChecksum = calculateChecksum(payload);

        if (!Arrays.equals(receivedChecksum, calculatedChecksum)) {
            return false;
        }

        return true;
    }
//    public boolean verifyMessage(byte[] fullMessage) {
//        // 1. Проверка на null и минимальную длину
//        if (fullMessage == null || fullMessage.length < 24) {
//            System.err.println("Invalid message: too short or null");
//            return false;
//        }
//
//        // 2. Проверка Magic Number
//        byte[] magic = Arrays.copyOfRange(fullMessage, 0, 4);
//        if (!Arrays.equals(magic, Utils.hexStringToByteArray(NETWORK_MAGIC))) {
//            System.err.println("Invalid magic number: " + Utils.bytesToHex(magic));
//            return false;
//        }
//
//        try {
//            // 3. Чтение длины payload (little-endian)
//            int payloadLength = ByteBuffer.wrap(fullMessage, 16, 4)
//                    .order(ByteOrder.LITTLE_ENDIAN)
//                    .getInt();
//
//            // 5. Проверка общей длины сообщения
//            if (fullMessage.length != 24 + payloadLength) {
//                System.err.println("Length mismatch: header=" + fullMessage.length +
//                        ", expected=" + (24 + payloadLength));
//                return false;
//            }
//
//            // 6. Извлечение payload
//            byte[] payload = new byte[payloadLength];
//            System.arraycopy(fullMessage, 24, payload, 0, payloadLength);
//
//            // 7. Проверка контрольной суммы
//            byte[] receivedChecksum = Arrays.copyOfRange(fullMessage, 20, 24);
//            byte[] calculatedChecksum = calculateChecksum(payload);
//
//            if (!Arrays.equals(receivedChecksum, calculatedChecksum)) {
//                System.err.println("Checksum mismatch: received=" + Utils.bytesToHex(receivedChecksum) +
//                        ", calculated=" + Utils.bytesToHex(calculatedChecksum));
//                return false;
//            }
//
//                return true;
//        } catch (Exception e) {
//            System.err.println("Error verifying message: " + e.getMessage());
//            return false;
//        }
//    }

    private MessageHeader parseHeader(byte[] data) throws MessageException {
        if (data.length < 24) {
            throw new MessageException();
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] magic = new byte[4];
        buffer.get(magic);

        byte[] commandBytes = new byte[12];
        buffer.get(commandBytes);
        String command = new String(commandBytes, StandardCharsets.US_ASCII);

        int payloadLength = buffer.getInt();
        byte[] checksum = new byte[4];
        buffer.get(checksum);

        return new MessageHeader(magic, command, payloadLength, checksum);
    }

    private void maintainConnections() {
        while (running) {
            try {
                Thread.sleep(30000);

                // Отправляем ping неактивным пирам
                connectedPeers.values().stream()
                        .filter(p -> System.currentTimeMillis() - p.getLastActive() > 60000)
                        .forEach(p -> {
                            try {
                                sendPing(p.getSocket().getOutputStream(), p);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                // Подключаемся к новым пирам, если нужно
                if (connectedPeers.size() < 8) {
                    connectToKnownPeers();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void connectToKnownPeers() {
        // Здесь должна быть логика подключения к известным пирам
        // В реальной реализации используются DNS-семена или сохранённые адреса
        List<String> bootstrapNodes = List.of(
                "seed.bitcoin.sipa.be",
                "dnsseed.bluematt.me",
                "dnsseed.bitcoin.dashjr.org"
        );

        // Упрощённо - подключаемся к localhost для теста
        try {
            Socket socket = new Socket("127.0.0.1", DEFAULT_PORT);
//            if (socket.getInetAddress().isLoopbackAddress() && socket.getPort() == this.port) {
//                socket.close();
//                return;
//            }
            PeerInfo peer = new PeerInfo(socket);
            connectedPeers.put(socket, peer);
            executor.execute(() -> handlePeer(peer));
        } catch (IOException e) {
            System.err.println("Failed to connect to bootstrap node: " + e.getMessage());
        }
    }

    public byte[] calculateChecksum(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return new byte[4];
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            byte[] hash1 = digest.digest(payload);
            byte[] hash2 = digest.digest(hash1);
            return Arrays.copyOfRange(hash2, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }


    public void buildMessage(OutputStream out, String command, byte[] payload) throws IOException {
        ByteArrayOutputStream messageStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(messageStream);

        // 1. Network Magic
        dos.write(Utils.hexStringToByteArray(NETWORK_MAGIC));

        // 2. Command (12 байт)
        byte[] commandBytes = command.getBytes();
        dos.write(commandBytes);
        dos.write(new byte[12 - commandBytes.length]); // Дополнение нулями

        // 3. Payload Length
        dos.writeInt(payload.length);

        // 4. Checksum
        dos.write(calculateChecksum(payload));

        // 5. Payload
        dos.write(payload);

        out.write(messageStream.toByteArray());
        out.flush();
    }

    private void sendVersionMessage(OutputStream out, PeerInfo peer) throws IOException {
        String command = "version";

        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(payloadStream);


        // Timestamp
        dos.writeLong(System.currentTimeMillis() / 1000);
        // sender IP + port
        dos.write(peer.getSocket().getLocalAddress().getAddress());
        dos.write(peer.getSocket().getLocalPort());

        // receiver IP + port
        dos.write(peer.getSocket().getInetAddress().getAddress());
        dos.write(peer.getSocket().getPort());

        byte[] payload = payloadStream.toByteArray();

        buildMessage(out, command, payload);
    }


    private void sendVerackMessage(OutputStream out, PeerInfo peer) throws IOException {
        String command = "verack";
        byte[] payload = new byte[0];
        buildMessage(out, command, payload);
    }


    private void sendPing(OutputStream out, PeerInfo peer) throws IOException{
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(stream);

        String command = "ping";

        dos.writeLong(System.currentTimeMillis());

        // sender IP + port
        dos.write(peer.getSocket().getLocalAddress().getAddress());
        dos.write(peer.getSocket().getLocalPort());

        // receiver IP + port
        dos.write(peer.getSocket().getInetAddress().getAddress());
        dos.write(peer.getSocket().getPort());

        byte[] payload = stream.toByteArray();

        buildMessage(out, command, payload);
    }

    private void sendPong(OutputStream out, PeerInfo peer) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(stream);

        String command = "pong";

        dos.writeLong(System.currentTimeMillis());

        // sender IP + port
        dos.write(peer.getSocket().getLocalAddress().getAddress());
        dos.write(peer.getSocket().getLocalPort());

        // receiver IP + port
        dos.write(peer.getSocket().getInetAddress().getAddress());
        dos.write(peer.getSocket().getPort());

        byte[] payload = stream.toByteArray();
        buildMessage(out, command, payload);
    }

    public void shutdown() {
        running = false;
        executor.shutdownNow(); // Прерываем все потоки
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Forced shutdown");
            }
            serverSocket.close();
        } catch (Exception e) {
            System.err.println("Shutdown error: " + e.getMessage());
        }
    }

    @Getter
    @Setter
    private static class PeerInfo {
        private final Socket socket;
        private long lastActive;

        public PeerInfo(Socket socket) {
            this.socket = socket;
            this.lastActive = System.currentTimeMillis();
        }

    }

    @AllArgsConstructor
    @Getter
    @Setter
    private static class NetworkMessage {
        private final PeerInfo peer;
        private final byte[] data;
    }

    public static void main(String[] args) throws IOException {
        NetworkNode node = new NetworkNode("0.0.0.0",DEFAULT_PORT);
        node.start();

        // Добавляем shutdown hook для graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(node::shutdown));
    }
}