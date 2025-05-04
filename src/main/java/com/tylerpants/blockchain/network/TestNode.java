package com.tylerpants.blockchain.network;

import com.tylerpants.blockchain.CmdManager;
import com.tylerpants.blockchain.network.file.PeerManager;
import com.tylerpants.blockchain.network.file.PeerRecord;
import com.tylerpants.blockchain.util.Utils;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

public class TestNode {
    private final int nodeId;
    private final String host;
    private final int port;

    private final Map<Socket, PeerInfo> connectedPeers = new ConcurrentHashMap<>();
    private final Map<PeerAddress, PeerInfo> failedPeers = new ConcurrentHashMap<>();
    private final BlockingQueue<NetworkMessage> messageQueue = new LinkedBlockingQueue<>();
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

    private final PeerManager peerManager;

    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private ScheduledExecutorService maintenanceScheduler;

    private static final String NETWORK_MAGIC = "f9beb4d9";
    private static final int MAX_PEERS = 30;
    private static final int MIN_PEERS = 1;
    private static final int DEFAULT_PORT = 8333;

    public TestNode(int nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;

        try {
            this.peerManager = new PeerManager();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
        System.out.printf("[%d] Node started on %s:%d\n", nodeId, host, port);

        // Запускаем обработчики
        new Thread(this::acceptConnections, "Acceptor-" + nodeId).start();
        new Thread(this::processMessages, "Processor-" + nodeId).start();
//        new Thread(this::listenUserInput, "Listener-" + nodeId).start();

        // Запускаем периодические задачи
        maintenanceScheduler = Executors.newScheduledThreadPool(1);
        maintenanceScheduler.scheduleAtFixedRate(this::maintainConnections, 0, 30, TimeUnit.SECONDS);

        // Подключаемся к bootstrap-узлам
        connectToKnownPeers();
    }

    // Основные компоненты узла --------------------------------------------------

    private void acceptConnections() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                if (connectedPeers.size() < MAX_PEERS) {
                    PeerInfo peer = new PeerInfo(socket);
                    connectedPeers.put(socket, peer);
                    executor.execute(() -> handlePeer(peer));

                    // Добавление пира в peers.dat
                    onConnectionSuccess(socket);
                    System.out.printf("[%d] Accepted connection from %s:%d, connections: %d\n",
                            nodeId, socket.getInetAddress(), socket.getPort(), connectedPeers.size());
                } else {
                    socket.close();
                }
            } catch (IOException e) {
                if (running) System.err.printf("[%d] Accept error: %s\n", nodeId, e.getMessage());
            }
        }
    }

    /**
     * Бредовая версия взаимодействия со старым блокчейном
     */
    private void listenUserInput() {
        CmdManager.init();
    }

    private void onConnectionSuccess(Socket socket) {
        peerManager.addPeer(
                socket.getInetAddress(),
                socket.getPort(),
                "incoming"
        );
        peerManager.updatePeerScore(
                socket.getInetAddress(),
                socket.getPort(),
                10 // Увеличиваем рейтинг
        );
    }

    private void onConnectionFailure(Socket socket) {
        peerManager.updatePeerScore(
                socket.getInetAddress(),
                socket.getPort(),
                -5 // Уменьшаем рейтинг
        );
    }

    private void cleanStalePeers() {
        peerManager.getBestPeers(Integer.MAX_VALUE).stream()
                .filter(p -> System.currentTimeMillis() - p.getTimestamp() > 30_000_000) // > 30 дней
                .forEach(p -> peerManager.removePeer(
                        p.getAddress(), // Нужно добавить метод getAddress() в PeerRecord
                        p.getPort()
                ));
        System.out.println("peers.dat -> cleaned stale peers");
    }


    private void handlePeer(PeerInfo peer) {
        try (InputStream in = peer.getSocket().getInputStream();
             OutputStream out = peer.getSocket().getOutputStream()) {

            // Обмен handshake-сообщениями
            sendVersion(out, peer);

            // Обработка входящих сообщений
            byte[] buffer = new byte[4096];
            int bytesRead;
            while (running && (bytesRead = in.read(buffer)) != -1) {
                byte[] message = Arrays.copyOf(buffer, bytesRead);
                messageQueue.put(new NetworkMessage(peer, message));
            }
        } catch (Exception e) {
            System.err.printf("[%d] Peer error: %s\n", nodeId, e.getMessage());
        } finally {
            disconnectPeer(peer);
        }
    }

    private void processMessages() {
        while (running) {
            try {
                NetworkMessage msg = messageQueue.take();
                processMessage(msg.getPeer(), msg.getData());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Backoff-механизм и поддержание соединений --------------------------------

    private void maintainConnections() {

        // 1. Очистка неактивных соединений
        connectedPeers.entrySet().removeIf(entry -> {
            PeerInfo peer = entry.getValue();
            if (System.currentTimeMillis() - peer.getLastActive() > 120_000) {
                disconnectPeer(peer);
                return true;
            }
            return false;
        });

        // 2. Отправка ping
        connectedPeers.values().forEach(peer -> {
            if (System.currentTimeMillis() - peer.getLastActive() > 60_000) {
                try {
                    sendPing(peer);
                } catch (IOException e) {
                    disconnectPeer(peer);
                }
            }
        });

        // 3. Поддержание минимального количества соединений
        if (connectedPeers.size() < MIN_PEERS) {
            connectToKnownPeers();
        }


    }

    private boolean isSelf(PeerAddress addr) {
        return addr.getHost().equals(this.host) && addr.getPort() == this.port;
    }

    private boolean isAlreadyConnected(PeerAddress address) {
        return connectedPeers.values().stream()
                .filter(peer -> peer.getSocket() != null && !peer.getSocket().isClosed())
                .anyMatch(peer -> {
                    InetAddress peerAddr = peer.getSocket().getInetAddress();
                    int peerPort = peer.getSocket().getPort();
                    return peerAddr.getHostAddress().equals(address.getHost())
                            && peerPort == address.getPort();
                });
    }

    private void connectToKnownPeers() {
        getNodesFromFile().stream()
                .filter(addr -> !isSelf(addr) && !isAlreadyConnected(addr))
                .filter(addr -> {
                    PeerInfo failed = failedPeers.get(addr);
                    return failed == null || failed.canAttemptReconnect();
                })
                .limit(MAX_PEERS - connectedPeers.size())
                .forEach(this::connectToPeer);
//        getBootstrapNodes().stream()
//                .filter(addr -> !isSelf(addr) && !isAlreadyConnected(addr))
//                .filter(addr -> {
//                    PeerInfo failed = failedPeers.get(addr);
//                    return failed == null || failed.canAttemptReconnect();
//                })
//                .limit(MAX_PEERS - connectedPeers.size())
//                .forEach(this::connectToPeer);
    }

    private List<PeerAddress> getNodesFromFile() {
        List<PeerRecord> peers = peerManager.getBestPeers(3);
        System.out.println(peers);
        List<PeerAddress> t = peers.stream().map(p -> {
            try {
                String host = InetAddress.getByAddress(p.getIp()).getHostAddress();
                int port = p.getPort();
                return new PeerAddress(host, port);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }

        }).toList();
        return t;
    }

    private void connectToPeer(PeerAddress addr) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(addr.getHost(), addr.getPort()), 5000);

            PeerInfo peer = new PeerInfo(socket);
            peer.resetReconnectAttempts();
            connectedPeers.put(socket, peer);
            executor.execute(() -> handlePeer(peer));

        } catch (IOException e) {
            PeerInfo peer = failedPeers.computeIfAbsent(addr, a -> new PeerInfo(null));
            if (peer.getReconnectAttempts() >= 9) {
                peer.resetReconnectAttempts();
                System.err.printf("[%d] Connect failed to %s, stop processing (%s)\n",
                        nodeId, addr, e.getMessage());
                onConnectionFailure(peer.getSocket());
            } else {
                peer.updateReconnectAttempts();
                System.err.printf("[%d] Connect failed to %s, next try in %ds\n",
                        nodeId, addr, peer.getNextReconnectDelay() / 1000);
            }
        }
    }


    private void disconnectPeer(PeerInfo peer) {
        if (peer == null) {
            return;
        }

        Socket socket = peer.getSocket();
        PeerAddress address = null;

        try {
            // Получаем адрес пира для логирования и сохранения в failedPeers
            if (socket != null && socket.getInetAddress() != null) {
                address = new PeerAddress(socket.getInetAddress().getHostAddress(), socket.getPort());
            }

            // Закрываем сокет
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.shutdownInput();
                    socket.shutdownOutput();
                } catch (IOException ignored) {}

                socket.close();
            }

            if (address != null) {
                System.out.printf("[%d] Disconnected peer %s:%d (handshake: %s)\n",
                        nodeId,
                        address.getHost(),
                        address.getPort(),
                        peer.isHandshakeCompleted() ? "completed" : "failed");
            }
        } catch (IOException e) {
            System.err.printf("[%d] Error disconnecting peer: %s\n", nodeId, e.getMessage());
        } finally {
            if (socket != null) {
                connectedPeers.remove(socket);
            }

            // Сохраняем в список неудачных подключений (если адрес известен)
            if (address != null && !peer.isHandshakeCompleted()) {
                failedPeers.compute(address, (addr, existingPeer) -> {
                    if (existingPeer == null) {
                        PeerInfo failed = new PeerInfo(null);
                        failed.updateReconnectAttempts();
                        return failed;
                    }
                    existingPeer.updateReconnectAttempts();
                    return existingPeer;
                });
            }
        }
    }

    // Обработка сообщений -----------------------------------------------------
    private void processMessage(PeerInfo peer, byte[] data) {
        try {
            if (!verifyMessage(data)) {
                System.err.printf("[%d] Invalid message from %s\n",
                        nodeId, peer.getSocket().getInetAddress());
                return;
            }

            MessageHeader header = parseHeader(data);
            byte[] payload = Arrays.copyOfRange(data, 24, 24 + header.getPayloadLength());

            switch (header.getCommand()) {
                case "version":
                    sendVerack(peer);
                    break;

                case "verack":
                    peer.setHandshakeCompleted(true);
                    System.out.printf("[%d] Handshake completed with %s:%d\n", nodeId, peer.getSocket().getInetAddress(), peer.getSocket().getPort());
                    break;

                case "ping":
                    System.out.printf("[%d] received Ping from %s:%d\n", nodeId,peer.getSocket().getInetAddress(), peer.getSocket().getPort());
                    ByteBuffer pingBuffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
                    long pingNonce = pingBuffer.getLong();
                    sendPong(peer, pingNonce);
                    break;

                case "pong":
                    System.out.printf("[%d] received Pong from %s:%d\n", nodeId,peer.getSocket().getInetAddress(), peer.getSocket().getPort());
                    break;

                default:
                    System.out.printf("[%d] Received %s from %s:%d\n",
                            nodeId, header.getCommand(), peer.getSocket().getInetAddress(), peer.getSocket().getPort());
            }
        } catch (Exception e) {
            System.err.printf("[%d] Message processing error: %s\n", nodeId, e.getMessage());
        }
    }

    // Вспомогательные методы --------------------------------------------------

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

    private byte[] calculateChecksum(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash1 = digest.digest(payload);
            byte[] hash2 = digest.digest(hash1);
            return Arrays.copyOfRange(hash2, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }


    // Отправка сообщений -------------------------------------------------------
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

    private void sendVersion(OutputStream out, PeerInfo peer) throws IOException {
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


    private void sendVerack(PeerInfo peer) throws IOException {
        String command = "verack";
        byte[] payload = new byte[0];
        buildMessage(peer.getSocket().getOutputStream(), command, payload);
    }

    private void sendPing(PeerInfo peer) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(stream);

        String command = "ping";
        long nonce = ThreadLocalRandom.current().nextLong();

        dos.writeLong(nonce);
        peer.setLastPingNonce(nonce);
        peer.setLastPingTime(System.currentTimeMillis()); // время отправки ping

        byte[] payload = stream.toByteArray();
        buildMessage(peer.getSocket().getOutputStream(), command, payload);
    }

    private void sendPong(PeerInfo peer, long nonce) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(stream);

        String command = "pong";
        dos.writeLong(nonce);

        byte[] payload = stream.toByteArray();
        buildMessage(peer.getSocket().getOutputStream(), command, payload);
    }

    // Завершение работы -------------------------------------------------------
    public void shutdown() {
        try {
            peerManager.savePeers();
        } catch (IOException e) {
            System.err.printf("[%d] Failed to save peers.dat: %s\n ", nodeId, e.getMessage());
        }

        running = false;
        maintenanceScheduler.shutdown();
        executor.shutdown();

        try {
            serverSocket.close();
            connectedPeers.keySet().forEach(this::closeSocket);
        } catch (IOException e) {
            System.err.printf("[%d] Shutdown error: %s\n", nodeId, e.getMessage());
        }
    }

    private void closeSocket(Socket s) {
        try {
            s.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    public static void main(String[] args) throws IOException {
//        TestNode node;
//        if (args.length == 0) {
//            node = new TestNode(1, "127.0.0.1", DEFAULT_PORT);
//        } else {
//            int port = Integer.parseInt(args[0]);
//            node = new TestNode(port, "0.0.0.0", port);
//        }
//
//        node.start();
//
//        Runtime.getRuntime().addShutdownHook(new Thread(node::shutdown));


        TestNode node1 = new TestNode(1, "0.0.0.0", 8333);
        TestNode node2 = new TestNode(2, "0.0.0.0", 8334);

        node1.start();
        node2.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            node1.shutdown();
            node2.shutdown();
        }));
    }
}
