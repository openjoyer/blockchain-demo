package com.tylerpants.blockchain.network;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public class BitcoinProtocol {

    public static void main(String[] args) throws Exception {
        // Connect to the Bitcoin node
        try (Socket socket = new Socket("127.0.0.1", 8333)) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            // Create the payload for the version message
            String payload = reverseBytes(size(hexadecimal(70014), 4));       // protocol version
            payload += reverseBytes(size(hexadecimal(0), 8));                // services
            payload += reverseBytes(size(hexadecimal(1640961477), 8));       // time
            payload += reverseBytes(size(hexadecimal(0), 8));                // remote node services
            payload += "00000000000000000000ffff2e13894a";                   // remote node ipv6
            payload += size(hexadecimal(8333), 2);                           // remote node port
            payload += reverseBytes(size(hexadecimal(0), 8));                // local node services
            payload += "00000000000000000000ffff7f000001";                   // local node ipv6
            payload += size(hexadecimal(8333), 2);                           // local node port
            payload += reverseBytes(size(hexadecimal(0), 8));                // nonce
            payload += "00";                                                 // user agent
            payload += reverseBytes(size(hexadecimal(0), 4));                // last block

            // Create the message header
            String magicBytes = "f9beb4d9";
            String command = asciiToHex("version");
            String size = reverseBytes(size(hexadecimal(payload.length() / 2), 4));
            String checksum = calculateChecksum(payload);

            String header = magicBytes + command + size + checksum;

            // Combine the header and payload into a complete message
            String versionMessage = header + payload;

            // Send the version message
            output.write(hexStringToByteArray(versionMessage));
            System.out.println("version->");
            System.out.println(versionMessage);
            System.out.println();

            // Receive the version message response
            readMessage(input, "version");

            // Receive the verack message response
            readMessage(input, "verack");

            // Send the verack message
            String verackPayload = "";
            command = asciiToHex("verack");
            size = reverseBytes(size(hexadecimal(verackPayload.length() / 2), 4));
            checksum = calculateChecksum(verackPayload);
            String verackMessage = magicBytes + command + size + checksum + verackPayload;

            output.write(hexStringToByteArray(verackMessage));
            System.out.println("verack->");
            System.out.println(verackMessage);
            System.out.println();

            // Keep reading messages
            while (true) {
                processIncomingMessage(input, output);
            }
        }
    }

    private static void readMessage(InputStream input, String messageType) throws IOException {
        byte[] magicBytes = readBytes(input, 4);
        byte[] commandBytes = readBytes(input, 12);
        byte[] sizeBytes = readBytes(input, 4);
        byte[] checksumBytes = readBytes(input, 4);

        String command = new String(commandBytes).trim();
        int size = ByteBuffer.wrap(sizeBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] payloadBytes = readBytes(input, size);

        System.out.println("<-" + messageType);
        System.out.println("magic_bytes: " + bytesToHex(magicBytes));
        System.out.println("command:     " + command);
        System.out.println("size:        " + size);
        System.out.println("checksum:    " + bytesToHex(checksumBytes));
        System.out.println("payload:     " + bytesToHex(payloadBytes));
        System.out.println();
    }

    private static void processIncomingMessage(InputStream input, OutputStream output) throws Exception {
        StringBuilder buffer = new StringBuilder();

        while (true) {
            int byteRead = input.read();
            if (byteRead == -1) {
                System.out.println("Disconnected from the node.");
                System.exit(0);
            }

            String byteHex = String.format("%02x", byteRead);
            buffer.append(byteHex);

            if (buffer.length() == 8) {
                if (buffer.toString().equals("f9beb4d9")) {
                    byte[] commandBytes = readBytes(input, 12);
                    int size = ByteBuffer.wrap(readBytes(input, 4)).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                    byte[] checksumBytes = readBytes(input, 4);
                    byte[] payloadBytes = readBytes(input, size);

                    String command = new String(commandBytes).trim();
                    String payload = bytesToHex(payloadBytes);

                    System.out.println("<-" + command);
                    System.out.println("payload: " + payload);

                    if (command.equals("inv") || command.equals("ping")) {
                        String responseCommand = command.equals("inv") ? "getdata" : "pong";
                        String responsePayload = payload;

                        String responseCommandHex = asciiToHex(responseCommand);
                        String responseSize = reverseBytes(size(hexadecimal(responsePayload.length() / 2), 4));
                        String responseChecksum = calculateChecksum(responsePayload);
                        String responseMessage = "f9beb4d9" + responseCommandHex + responseSize + responseChecksum + responsePayload;

                        output.write(hexStringToByteArray(responseMessage));
                        System.out.println(responseCommand + "->");
                        System.out.println(responseMessage);
                    }
                    break;
                }
                buffer.setLength(0);
            }
        }
    }

    // Helper functions
    private static String hexadecimal(int number) {
        return Integer.toHexString(number);
    }

    private static String size(String data, int size) {
        return String.format("%" + (size * 2) + "s", data).replace(' ', '0');
    }

    private static String reverseBytes(String bytes) {
        StringBuilder reversed = new StringBuilder();
        for (int i = 0; i < bytes.length(); i += 2) {
            reversed.insert(0, bytes.substring(i, i + 2));
        }
        return reversed.toString();
    }

    private static String asciiToHex(String ascii) {
        StringBuilder hexString = new StringBuilder();
        for (char c : ascii.toCharArray()) {
            hexString.append(String.format("%02x", (int) c));
        }
        return String.format("%-24s", hexString).replace(' ', '0');
    }

    private static String calculateChecksum(String payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] firstHash = digest.digest(hexStringToByteArray(payload));
        byte[] secondHash = digest.digest(firstHash);
        return bytesToHex(secondHash).substring(0, 8);
    }

    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    private static byte[] readBytes(InputStream input, int length) throws IOException {
        byte[] buffer = new byte[length];
        int bytesRead = input.read(buffer);
        if (bytesRead != length) {
            throw new IOException("Unable to read enough bytes from the stream");
        }
        return buffer;
    }
}