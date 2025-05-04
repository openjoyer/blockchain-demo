package com.tylerpants.blockchain.network.file;

import com.tylerpants.blockchain.network.dns.DnsSeeder;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.stream.Collectors;

public class PeerManager {
    private static final int MAGIC = 0x0B11DB07;
    private static final int RECORD_SIZE = 18; // 4+2+8+4
    private final Path peersFile;
    private final Map<String, PeerRecord> peers = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private static final String PATH = "src/main/resources/peers.dat";

    private static final List<InetSocketAddress> HARDCODED_PEERS = Arrays.asList(
            new InetSocketAddress("203.0.113.1", 8333),  // Ваша нода 1
            new InetSocketAddress("203.0.113.2", 8333),  // Ваша нода 2
            new InetSocketAddress("198.51.100.5", 8333)  // Резервная нода
    );

    private final DnsSeeder dnsSeeder = new DnsSeeder();


    public PeerManager(String dataDir) throws IOException {
        this.peersFile = Paths.get(dataDir, "peers.dat");
        loadPeers();
        startAutoSaver();
    }

    public PeerManager() throws IOException {
        this.peersFile = Paths.get(PATH);
        loadPeers();
        startAutoSaver();
    }

    public void loadHardcodedPeers() {
        HARDCODED_PEERS.forEach(peer -> {
            addPeer(peer.getAddress(), peer.getPort(), "hardcoded");
        });
    }

//    public void loadPeersFromFile() throws IOException {
//        Path file = Paths.get("config/peers.txt");
//        Files.readAllLines(file).forEach(line -> {
//            String[] parts = line.split(":");
//            addPeer(parts[0], Integer.parseInt(parts[1]), "file");
//        });
//    }

    public List<InetAddress> discoverDnsSeeds() {
        return dnsSeeder.queryDnsSeeds();
    }
    public List<PeerRecord> discoverPeers() {
        List<PeerRecord> peers = new ArrayList<>();

//        // 1. Сначала hardcoded пиры
//        peers.addAll(HARDCODED_PEERS);

        // 3. Последние рабочие из peers.dat
        peers.addAll(getBestPeers(10));

        return peers.stream()
                .distinct()
                .limit(10)  // Не более 10 адресов
                .collect(Collectors.toList());
    }

    public boolean isPortOpen(String ip, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    private void loadPeers() throws IOException {
        lock.writeLock().lock();
        try {
            if (!Files.exists(peersFile)) {
                Files.createFile(peersFile);
                return;
            }

            byte[] data = Files.readAllBytes(peersFile);
            if (data.length < 12) return;

            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            if (buffer.getInt() != MAGIC) throw new IOException("Invalid peers.dat");
            int version = buffer.getInt();
            int count = buffer.getInt();

            for (int i = 0; i < count; i++) {
                byte[] ip = new byte[4];
                buffer.get(ip);
                int port = Short.toUnsignedInt(buffer.getShort());
                long timestamp = buffer.getLong();
                int score = buffer.getInt();

                String key = getPeerKey(ip, port);
                PeerRecord peerRecord = new PeerRecord(ip, port, timestamp, score, "file");
//                System.out.println(peerRecord);
                peers.put(key, peerRecord);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Автосохранение каждые 5 минут
    private void startAutoSaver() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                savePeers();
            } catch (IOException e) {
                System.err.println("Auto-save failed: " + e.getMessage());
            }
        }, 5, 30, TimeUnit.SECONDS);
    }

    // Сохранение в файл
    public void savePeers() throws IOException {
        lock.readLock().lock();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(12 + peers.size() * RECORD_SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN);

            // Заголовок
            buffer.putInt(MAGIC);
            buffer.putInt(1); // Версия
            buffer.putInt(peers.size());

            // Записи
            peers.values().forEach(peer -> {
                buffer.put(peer.getIp());
                buffer.putShort((short) peer.getPort());
                buffer.putLong(peer.getTimestamp());
                buffer.putInt(peer.getScore());
            });

            Files.write(peersFile, buffer.array(), StandardOpenOption.TRUNCATE_EXISTING);
        } finally {
            lock.readLock().unlock();
        }
    }

    // Добавление нового пира
    public boolean addPeer(InetAddress address, int port, String source) {
        String key = getPeerKey(address, port);
        lock.writeLock().lock();
        try {
            if (peers.containsKey(key)) return false;

            PeerRecord peer = new PeerRecord(
                    address.getAddress(),
                    port,
                    System.currentTimeMillis(),
                    0, // Начальный рейтинг
                    source
            );
            peers.put(key, peer);
//            System.out.println("peers.dat -> added peer " + key);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Обновление рейтинга
    public void updatePeerScore(InetAddress address, int port, int delta) {
        String key = getPeerKey(address, port);
        lock.writeLock().lock();
        try {
            PeerRecord peer = peers.get(key);
            if (peer != null) {
                peer.setScore(Math.max(-1, peer.getScore() + delta));
//                System.out.println("peers.dat -> updated score "+ peer.getScore());
                peer.setTimestamp(System.currentTimeMillis());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Удаление пира
    public boolean removePeer(InetAddress address, int port) {
        String key = getPeerKey(address, port);
        lock.writeLock().lock();
        try {
            return peers.remove(key) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Получение списка лучших пиров
    public List<PeerRecord> getBestPeers(int limit) {
        lock.readLock().lock();
        try {
            return peers.values().stream()
                    .filter(p -> p.getScore() >= 0) // Исключаем забаненных
                    .sorted(Comparator.comparingInt(PeerRecord::getScore).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }


    // Все пиры
    public List<PeerRecord> getAllPeers() {
        lock.readLock().lock();
        try {
            return peers.values().stream().toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Вспомогательные методы
    private String getPeerKey(InetAddress address, int port) {
        return address.getHostAddress() + ":" + port;
    }

    private String getPeerKey(byte[] ip, int port) {
        try {
            return InetAddress.getByAddress(ip).getHostAddress() + ":" + port;
        } catch (UnknownHostException e) {
            return Arrays.toString(ip) + ":" + port;
        }
    }
}