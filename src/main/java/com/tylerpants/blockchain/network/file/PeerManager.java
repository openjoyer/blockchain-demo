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
    private static final String HARDCODED_PEERS_PATH = "src/main/resources/hardcoded-peers.txt";

    private static List<PeerRecord> hardcodedPeers;

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


    // Точка входа для получения списка пиров
    public List<PeerRecord> discoverPeers(int limit) {
        List<PeerRecord> peers = new ArrayList<>();

        // 1. Сначала hardcoded пиры
        peers.addAll(hardcodedPeers.stream()
                .map(p -> new PeerRecord(p.getAddress().getAddress(), p.getPort(), System.currentTimeMillis(), 100, "hardcoded"))
                .toList());

        // 2. Последние рабочие из peers.dat
        peers.addAll(getBestPeers(10));

        return peers.stream()
                .distinct()
                .limit(limit)
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

    private List<PeerRecord> getHardcodedPeersFromFile() throws IOException {
        List<PeerRecord> list = new ArrayList<>();
        BufferedReader  reader = new BufferedReader(new FileReader(HARDCODED_PEERS_PATH));
        while (reader.ready()) {
            String[] s = reader.readLine().split(":");
            String ip = s[0];
            int port = Integer.parseInt(s[1]);
            InetSocketAddress i = new InetSocketAddress(ip, port);

            PeerRecord peer = new PeerRecord(i.getAddress().getAddress(), port, System.currentTimeMillis(), 100, "hardcoded");
            list.add(peer);
        }
        return list;
    }

    private void saveHardcodedPeers() {
        hardcodedPeers.forEach(peer -> addPeer(peer.getAddress(), peer.getPort(), "hardcoded"));
    }

    private void loadPeers() throws IOException {
        hardcodedPeers = getHardcodedPeersFromFile();

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

            saveHardcodedPeers();
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
            if (source.equals("hardcoded")) {
                peer.setScore(100);
            }
            peers.put(key, peer);
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
    private List<PeerRecord> getBestPeers(int limit) {
        lock.readLock().lock();
        try {
            return peers.values().stream()
                    .filter(p -> p.getScore() >= 0)   // Исключаем забаненных
                    .filter(p -> !p.getSource().equals("hardcoded"))   // Исключаем hadcoded адреса
                    .sorted(Comparator.comparingInt(PeerRecord::getScore).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
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