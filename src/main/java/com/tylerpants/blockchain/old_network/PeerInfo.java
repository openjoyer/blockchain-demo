package com.tylerpants.blockchain.old_network;

import lombok.Getter;
import lombok.Setter;

import java.net.Socket;

@Getter
@Setter
public class PeerInfo {
    private final Socket socket;
    private long lastActive;
    private long lastPingNonce;
    private long lastPingTime; // Время отправки последнего ping
    private int pingTimeoutCount; // Количество пропущенных pong

    private int reconnectAttempts = 0;
    private long lastReconnectTime = 0;
    private long nextReconnectDelay = 1000; // Начальная задержка 1 секунда

    public void updateReconnectAttempts() {
        this.reconnectAttempts++;
        // Экспоненциальный backoff с ограничением максимум 5 минут
        this.nextReconnectDelay = Math.min(300_000, 1000 * (1 << Math.min(30, reconnectAttempts)));
        this.lastReconnectTime = System.currentTimeMillis();
    }

    public boolean canAttemptReconnect() {
        return System.currentTimeMillis() >= lastReconnectTime + nextReconnectDelay;
    }

    public void resetReconnectAttempts() {
        this.reconnectAttempts = 0;
        this.nextReconnectDelay = 1000;
    }

    public PeerInfo(Socket socket) {
        this.socket = socket;
        this.lastActive = System.currentTimeMillis();
        this.lastPingNonce = -1;
        this.lastPingTime = 0;
        this.pingTimeoutCount = 0;
    }

    public void updateLastActive() {
        this.lastActive = System.currentTimeMillis();
        this.pingTimeoutCount = 0; // Сброс счетчика при любой активности
    }
}