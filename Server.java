import java.io.*;                     // Para leer y escribir datos (BufferedReader, PrintWriter, etc.)
import java.net.*;                     // Para la comunicación en red (ServerSocket, Socket)
import java.util.List;                 // Lista de clientes conectados
import java.util.concurrent.CopyOnWriteArrayList; // Lista segura para uso concurrente entre hilos

// --------------------- CLASE PRINCIPAL DEL SERVIDOR ---------------------
public class Server {
    private final int port;            // Puerto en el que escuchará el servidor
    // Lista de clientes conectados, segura para acceso concurrente
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // Constructor: asigna el puerto al servidor
    public Server(int port) {
        this.port = port;
    }

    // Método para iniciar el servidor
    public void start() {
        System.out.println("[SERVIDOR] Iniciando en puerto " + port + " ...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVIDOR] Escuchando conexiones...");
            while (true) {
                Socket socket = serverSocket.accept(); // Espera hasta que un cliente se conecte
                System.out.println("[SERVIDOR] Cliente conectado: " + socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);                 // Agregar cliente a la lista
                new Thread(handler).start();          // Iniciar el hilo del cliente
            }
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Error: " + e.getMessage());
        }
    }

    // -------------------- MÉTODOS DE UTILIDAD DEL SERVIDOR --------------------

    // Enviar un mensaje a todos los clientes conectados (excepto a quien lo envió)
    public void broadcast(String msg, ClientHandler exclude) {
        for (ClientHandler c : clients) {
            if (c != exclude) {       // No reenviar al emisor
                c.send(msg);          // Enviar mensaje al cliente
            }
        }
    }

    // Obtener lista de usuarios conectados
    public String listUsers() {
        StringBuilder sb = new StringBuilder();
        for (ClientHandler c : clients) {
            if (c.getUsername() != null) {  // Solo usuarios que ya tienen nombre
                if (sb.length() > 0) sb.append(", ");
                sb.append(c.getUsername());
            }
        }
        return sb.toString();
    }

    // Eliminar cliente desconectado y notificar a los demás
    public void remove(ClientHandler handler) {
        clients.remove(handler);
        if (handler.getUsername() != null) {
            broadcast("[SERVIDOR] " + handler.getUsername() + " se ha desconectado.", null);
            System.out.println("[SERVIDOR] " + handler.getUsername() + " desconectado.");
        }
    }

    // Punto de entrada principal del programa
    public static void main(String[] args) {
        int port = 5000; // Puerto por defecto
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]); // Permite pasar otro puerto como argumento
        }
        new Server(port).start(); // Crear e iniciar el servidor
    }

    // --------------------- CLASE INTERNA: HANDLER DE CADA CLIENTE ---------------------
    private static class ClientHandler implements Runnable {
        private final Socket socket;         // Socket específico del cliente
        private final Server server;         // Referencia al servidor para usar sus métodos
        private BufferedReader in;           // Para leer datos que envía el cliente
        private PrintWriter out;             // Para enviar datos al cliente
        private String username;             // Nombre de usuario de este cliente

        public ClientHandler(Socket socket, Server server) {
            this.socket = socket;
            this.server = server;
        }

        public String getUsername() { return username; }

        // Enviar mensaje al cliente
        public void send(String msg) {
            try { out.println(msg); } catch (Exception ignored) {}
        }

        @Override
        public void run() {
            try {
                // Streams en UTF-8
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                // ---------------- PEDIR NOMBRE DE USUARIO ----------------
                out.println("[SERVIDOR] Bienvenido. Ingrese su nombre de usuario:");
                String candidate;
                while (true) {
                    candidate = in.readLine();
                    if (candidate == null) throw new IOException("Cliente cerró antes de enviar usuario.");
                    candidate = candidate.trim();
                    if (candidate.isEmpty()) {
                        out.println("[SERVIDOR] Nombre vacío. Intente de nuevo:");
                        continue;
                    }
                    this.username = candidate;
                    break;
                }

                out.println("[SERVIDOR] Conectado como: " + username + ". Comandos: /listar o listar, /quitar o quitar");
                server.broadcast("[SERVIDOR] " + username + " se ha unido al chat.", this);

                // ---------------- BUCLE PRINCIPAL: ESCUCHAR MENSAJES ----------------
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // Log de depuración para ver exactamente qué llegó
                    System.out.println("[DEBUG] Recibido de " + username + ": '" + line + "'");

                    // ---- Comandos (acepta con y sin slash) ----
                    String lower = line.toLowerCase();
                    if (lower.equals("/listar") || lower.equals("listar")) {
                        String users = server.listUsers();
                        if (users.isEmpty()) users = "(sin usuarios)";
                        out.println("[SERVIDOR] Usuarios conectados: " + users);
                        continue;
                    }
                    if (lower.equals("/quitar") || lower.equals("quitar")) {
                        out.println("[SERVIDOR] Desconectando. ¡Hasta luego!");
                        break;
                    }

                    // ---- Mensaje normal ----
                    String msg = "[" + username + "] " + line;
                    System.out.println(msg);
                    server.broadcast(msg, this);
                }

            } catch (IOException e) {
                // caída inesperada del cliente
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                server.remove(this);
            }
        }
    }
}
