import java.net.*;
import java.util.Scanner;

public class ClientUDP {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Ingrese la IP del servidor: ");
        String serverIP = sc.nextLine();

        System.out.print("Ingrese el puerto del servidor: ");
        int serverPort = Integer.parseInt(sc.nextLine());

        System.out.print("Ingrese su nombre de usuario: ");
        String username = sc.nextLine();

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress serverAddr = InetAddress.getByName(serverIP);

            // Enviar nombre de usuario al servidor
            send(socket, username, serverAddr, serverPort);

            // Hilo para escuchar mensajes entrantes
            Thread listener = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (true) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                        System.out.println(msg);
                    } catch (Exception e) {
                        break;
                    }
                }
            });
            listener.setDaemon(true);
            listener.start();

            // Enviar mensajes al servidor
            System.out.println("Conectado al chat UDP. Escriba mensajes o use /listar o /quitar");
            while (true) {
                String msg = sc.nextLine();
                send(socket, msg, serverAddr, serverPort);
                if (msg.equalsIgnoreCase("/quitar")) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[CLIENTE-UDP] Error: " + e.getMessage());
        }

        sc.close();
    }

    private static void send(DatagramSocket socket, String msg, InetAddress addr, int port) throws Exception {
        byte[] data = msg.getBytes("UTF-8");
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        socket.send(packet);
    }
}
