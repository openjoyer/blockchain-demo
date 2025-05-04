package com.tylerpants.blockchain.old_network;

import com.tylerpants.blockchain.chain.Block;
import com.tylerpants.blockchain.network.MessageException;
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
import java.util.stream.Collectors;

public class NetworkNode {

    private int number; // TEST ONLY
    private static final int DEFAULT_PORT = 8333;
    private static final String NETWORK_MAGIC = "f9beb4d9";
    private static final int MAX_PEERS = 125;

    private final String host;
    private final int port;
    private final Map<Socket, PeerInfo> connectedPeers = new ConcurrentHashMap<>();
    private final Map<PeerAddress, PeerInfo> failedPeers = new ConcurrentHashMap<>();
    private final BlockingQueue<NetworkMessage> messageQueue = new LinkedBlockingQueue<>();
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    // Храним цепочку блоков (упрощённо)
    private final List<Block> blockchain = Collections.synchronizedList(new ArrayList<>());

    public NetworkNode(int number, String host, int port) {
        this.number = number;
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
        System.out.println("["+number+"] Node started on " + host + " : " + port);

        // Поток для приёма входящих соединений
        new Thread(this::acceptConnections).start();

        // Поток для обработки сообщений
        new Thread(this::processMessages).start();

        // Поток для поддержания соединений
        new Thread(this::maintainConnections).start();
    }

    public void connectToPeer(String host, int port) throws IOException {
        if (connectedPeers.size() >= MAX_PEERS) return;

        Socket socket = new Socket(host, port);
        PeerInfo peer = new PeerInfo(socket);
        connectedPeers.put(socket, peer);
        executor.execute(() -> handlePeer(peer));
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket peerSocket = serverSocket.accept();
                if (connectedPeers.size() < MAX_PEERS) {
                    PeerInfo peer = new PeerInfo(peerSocket);
                    System.out.println("["+number+"] New connection with socket: " + peer.getSocket().getInetAddress());
                    connectedPeers.put(peerSocket, peer);
                    executor.execute(() -> handlePeer(peer));
                } else {
                    peerSocket.close();
                }
            } catch (IOException e) {
                if (running) System.err.println("["+number+"] Accept error: " + e.getMessage());
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
            System.err.println("["+number+"] Peer handling error: " + e.getMessage());
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
        System.out.println("["+number+"] Received message from " + peer.getSocket().getInetAddress() + ": " + Utils.bytesToHex(data));


        if (verifyMessage(data)) {
            try (OutputStream out = peer.getSocket().getOutputStream()) {
                MessageHeader header = parseHeader(data);
                byte[] payload = Arrays.copyOfRange(data, 24, 24 + header.getPayloadLength());

                System.out.println("["+number+"]" + header);
                String command = header.getCommand();

                switch (command) {
                    case "version":
                        sendVerackMessage(out, peer);
                        break;
                    case "verack":
                        System.out.println("["+number+"] Verack!");
                        break;
                    case "tx":
                        break;
                    case "ping":
                        System.out.println("["+number+"] received Ping!");
                        ByteBuffer pingBuffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
                        long pingNonce = pingBuffer.getLong();
                        sendPong(out, peer, pingNonce);
                        break;
                    case "pong":
                        System.out.println("["+number+"] received Pong!");
                        ByteBuffer pongBuffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
                        long pongNonce = pongBuffer.getLong();
                        if (pongNonce == peer.getLastPingNonce()) {
                            peer.updateLastActive();
                            long pingTime = System.currentTimeMillis() - peer.getLastPingTime();
                            System.out.println("["+number+"] Ping time: " + pingTime + "ms");
                        }
                        break;
                }
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
                System.err.println("["+number+"] Peer handling error: " + e.getMessage());
            }
        }
        else {
            System.err.println("["+number+"] error receiving message");
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
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleWithFixedDelay(() -> {
            if (!running || Thread.currentThread().isInterrupted()) {
                scheduler.shutdown();
                return;
            }

            synchronized (connectedPeers) {
                // 1. Очистка неактивных соединений
                connectedPeers.entrySet().removeIf(entry -> {
                    PeerInfo peer = entry.getValue();
                    if (System.currentTimeMillis() - peer.getLastActive() > 120_000) {
                        try {
                            entry.getKey().close();
                            System.out.println("["+number+"] Closed inactive connection with " +
                                    entry.getKey().getInetAddress());
                        } catch (IOException ignored) {}
                        return true;
                    }
                    return false;
                });

                // 2. Отправка ping
                new ArrayList<>(connectedPeers.values()).forEach(peer -> {
                    if (System.currentTimeMillis() - peer.getLastActive() > 10000) {
                        try {
                            sendPing(peer.getSocket().getOutputStream(),peer);
                        } catch (IOException ignored) {}
                    }
                });

                // 3. Поддержание минимума соединений
                if (connectedPeers.size() < 1) {
                    System.out.println(number + ": проверка... " );

                    connectToKnownPeers();
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    /**
     * ПЕРЕДЕЛАТЬ !!!
     */
    private void connectToKnownPeers() {
        if (connectedPeers.size() >= MAX_PEERS) return;

        // Получаем список для подключения с учетом backoff
        List<PeerAddress> candidates = getBootstrapNodes().stream()
                .filter(addr -> !isSelf(addr))
                .filter(addr -> {
                    PeerInfo existing = findPeerByAddress(addr);
                    return existing == null || existing.canAttemptReconnect();
                })
                .sorted(Comparator.comparingInt(addr -> {
                            PeerInfo p = findPeerByAddress(addr);
                            return p != null ? p.getReconnectAttempts() : 0;
                        }))
                        .limit(3) // Пробуем не более 3 узлов за раз
                        .collect(Collectors.toList());

        for (PeerAddress candidate : candidates) {
            try {
                PeerInfo existingPeer = findPeerByAddress(candidate);
                if (existingPeer != null && !existingPeer.canAttemptReconnect()) {
                    continue;
                }

                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(candidate.getHost(), candidate.getPort()), 5000);

                PeerInfo peer = existingPeer != null ? existingPeer : new PeerInfo(socket);
                peer.resetReconnectAttempts();

                synchronized (connectedPeers) {
                    connectedPeers.put(socket, peer);
                }

                executor.execute(() -> handlePeer(peer));

            } catch (IOException e) {
                PeerInfo peer = findPeerByAddress(candidate);
                if (peer != null) {
                    peer.updateReconnectAttempts();
                } else {
                    peer = new PeerInfo(null);
                    peer.updateReconnectAttempts();
                    // Сохраняем в отдельную коллекцию для последующих попыток
                    failedPeers.put(candidate, peer);
                }
                System.out.println("["+number+"] Connect failed to " + candidate +
                        ", next attempt in " + (peer.getNextReconnectDelay()/1000) + "s");
            }
        }
    }

    private List<PeerAddress> getBootstrapNodes() {
        return List.of(
                new PeerAddress("127.0.0.1", 8333),
                new PeerAddress("127.0.0.1", 8334)
        );
    }

    private PeerInfo findPeerByAddress(PeerAddress addr) {
        return connectedPeers.values().stream()
                .filter(p -> p.getSocket() != null &&
                        p.getSocket().getInetAddress().getHostAddress().equals(addr.getHost()) &&
                        p.getSocket().getPort() == addr.getPort())
                .findFirst()
                .orElse(failedPeers.get(addr));
    }

    private boolean isSelf(PeerAddress addr) {
        return addr.getHost().equals(this.host) && addr.getPort() == this.port;
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
            throw new RuntimeException("["+number+"] SHA-256 not available", e);
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

    private void sendPing(OutputStream out, PeerInfo peer) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(stream);

        String command = "ping";
        long nonce = ThreadLocalRandom.current().nextLong();

        dos.writeLong(nonce);
        peer.setLastPingNonce(nonce);
        peer.setLastPingTime(System.currentTimeMillis()); // время отправки ping

        byte[] payload = stream.toByteArray();
        buildMessage(out, command, payload);
    }

    private void sendPong(OutputStream out, PeerInfo peer, long nonce) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(stream);

        String command = "pong";
        dos.writeLong(nonce);

        byte[] payload = stream.toByteArray();
        buildMessage(out, command, payload);
    }

    public void shutdown() {
        running = false;
        executor.shutdownNow(); // Прерываем все потоки
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("["+number+"] Forced shutdown");
            }
            serverSocket.close();
        } catch (Exception e) {
            System.err.println("["+number+"] Shutdown error: " + e.getMessage());
        }
    }

    @AllArgsConstructor
    @Getter
    @Setter
    private static class NetworkMessage {
        private final PeerInfo peer;
        private final byte[] data;
    }

    public static void main(String[] args) {

        // Нода 1 (порт 8333)
        new Thread(() -> {
            NetworkNode node1 = new NetworkNode(1,"0.0.0.0", 8333);
            try {
                node1.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Нода 2 (порт 8334)
        new Thread(() -> {
            NetworkNode node2 = new NetworkNode(2,"0.0.0.0", 8334);
            try {
                node2.start();
                // Подключаемся к первой ноде
                node2.connectToPeer("127.0.0.1", 8333);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Добавляем shutdown hook для graceful shutdown
//        Runtime.getRuntime().addShutdownHook(new Thread(node::shutdown));
    }
}
