import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        // Uso: java Client <host> <puerto>
        String host = "127.0.0.1";
        int port = 5000;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);

        System.out.println("[CLIENTE] Conectando a " + host + ":" + port + " ...");

        try (Socket socket = new Socket(host, port)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            Scanner scanner = new Scanner(System.in, "UTF-8");

            // Hilo para leer mensajes del servidor y mostrarlos en consola
            Thread reader = new Thread(() -> {
                try {
                    String srvMsg;
                    while ((srvMsg = in.readLine()) != null) {
                        System.out.println(srvMsg);
                    }
                } catch (IOException ignored) {
                } finally {
                    System.out.println("[CLIENTE] Conexión cerrada por el servidor.");
                }
            });
            reader.setDaemon(true);
            reader.start();

            // Enviar lo que se escriba por consola
            while (true) {
                String line = scanner.nextLine();
                out.println(line);
                if (line.trim().equalsIgnoreCase("/quitar")) {
                    break; // cerrar desde cliente
                }
            }

            System.out.println("[CLIENTE] Saliendo...");
        } catch (IOException e) {
            System.err.println("[CLIENTE] Error: " + e.getMessage());
        }
    }
}
