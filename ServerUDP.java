import java.net.*;
import java.util.*;

public class ServerUDP {
    private static final int PORT = 5001;
    // Mapeo de clientes: dirección -> nombre de usuario
    private static final Map<SocketAddress, String> clients = new HashMap<>();

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("[SERVIDOR-UDP] Escuchando en el puerto " + PORT + "...");

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // recibe mensaje

                String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8").trim();
                SocketAddress clientAddr = packet.getSocketAddress();

                // Si el cliente no está registrado, el primer mensaje es el nombre
                if (!clients.containsKey(clientAddr)) {
                    clients.put(clientAddr, message);
                    System.out.println("[SERVIDOR-UDP] Nuevo usuario: " + message + " desde " + clientAddr);
                    send(socket, "[SERVIDOR] Bienvenido " + message + "!", clientAddr);
                    continue;
                }

                String user = clients.get(clientAddr);

                // ---- COMANDOS ----
                if (message.equalsIgnoreCase("/listar")) {
                    String lista = String.join(", ", clients.values());
                    send(socket, "[SERVIDOR] Usuarios conectados: " + lista, clientAddr);
                    continue;
                }

                if (message.equalsIgnoreCase("/quitar")) {
                    send(socket, "[SERVIDOR] Desconectado. ¡Hasta luego!", clientAddr);
                    System.out.println("[SERVIDOR-UDP] " + user + " se ha desconectado.");
                    clients.remove(clientAddr);
                    continue;
                }

                // ---- MENSAJE NORMAL ----
                String fullMsg = "[" + user + "] " + message;
                System.out.println(fullMsg);
                broadcast(socket, fullMsg, clientAddr);
            }
        } catch (Exception e) {
            System.err.println("[SERVIDOR-UDP] Error: " + e.getMessage());
        }
    }

    // Enviar mensaje a un cliente
    private static void send(DatagramSocket socket, String msg, SocketAddress addr) throws Exception {
        byte[] data = msg.getBytes("UTF-8");
        DatagramPacket packet = new DatagramPacket(data, data.length, addr);
        socket.send(packet);
    }

    // Reenviar mensaje a todos menos al emisor
    private static void broadcast(DatagramSocket socket, String msg, SocketAddress exclude) throws Exception {
        byte[] data = msg.getBytes("UTF-8");
        for (SocketAddress addr : clients.keySet()) {
            if (!addr.equals(exclude)) {
                DatagramPacket packet = new DatagramPacket(data, data.length, addr);
                socket.send(packet);
            }
        }
    }
}
