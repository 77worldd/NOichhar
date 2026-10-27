package com.noichhar.app;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Runs as the shell UID, not the VPN app UID (which is intentionally excluded from its own VPN). */
public final class DnsProbe {
    public static void main(String[] args) throws Exception {
        InetAddress dns = InetAddress.getByName(args[0]);
        byte[] query = query(args[1]);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(8000);
            socket.send(new DatagramPacket(query, query.length, dns, 53));
            DatagramPacket packet = new DatagramPacket(new byte[512], 512);
            socket.receive(packet);
            check(packet.getData(), packet.getLength());
            System.out.println("UDP_BLOCKED_OK");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(dns, 53), 8000);
            socket.setSoTimeout(8000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeShort(query.length); out.write(query); out.flush();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            byte[] response = new byte[in.readUnsignedShort()];
            in.readFully(response);
            check(response, response.length);
            System.out.println("TCP_BLOCKED_OK");
        }
        // app_process may otherwise retain framework threads after main returns.
        System.exit(0);
    }

    private static byte[] query(String domain) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0xCAFE); out.writeShort(0x0100); out.writeShort(1);
        out.writeShort(0); out.writeShort(0); out.writeShort(0);
        for (String label : domain.split("\\.")) {
            byte[] encoded = label.getBytes(StandardCharsets.US_ASCII);
            out.writeByte(encoded.length); out.write(encoded);
        }
        out.writeByte(0); out.writeShort(1); out.writeShort(1);
        return bytes.toByteArray();
    }

    private static void check(byte[] response, int length) {
        if (length < 12 || (response[0] & 255) != 0xCA || (response[1] & 255) != 0xFE ||
            (response[2] & 0x80) == 0 || (response[3] & 0x0F) != 3) {
            throw new AssertionError("Expected an NXDOMAIN response for the original query");
        }
    }
}
