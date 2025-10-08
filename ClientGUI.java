import javax.swing.*;      // Interfaz gráfica (ventanas, botones, etc.)
import java.awt.*;         // Layouts y componentes
import java.awt.event.*;   // Eventos (clicks, enter, cierre de ventana)
import java.io.*;          // Streams de entrada/salida
import java.net.Socket;    // Clase principal para conectar con el servidor

public class ClientGUI {
    // ----- Configuración fija -----
    private static final String HOST = "127.0.0.1";  // <-- Cambia por la IP real del servidor
    private static final int PORT = 5000;               // Puerto que usa el servidor

    // ----- Ventana de LOGIN -----
    private JFrame loginFrame;          // Ventana donde el usuario escribe su nombre
    private JTextField userField;       // Campo para escribir el nombre de usuario
    private JButton connectBtn;         // Botón para conectar
    private JLabel welcomeLabel;        // Etiqueta de bienvenida

    // ----- Ventana de CHAT -----
    private JFrame chatFrame;           // Ventana principal del chat
    private JTextArea chatArea;         // Muestra los mensajes del chat
    private JTextField inputField;      // Donde el usuario escribe sus mensajes
    private JButton sendBtn, listBtn, quitBtn; // Botones para enviar, listar y salir
    private JLabel statusLabel;         // Muestra el estado (Conectado/Desconectado)

    // ----- Red -----
    private Socket socket;              // Conexión con el servidor
    private BufferedReader in;          // LECTOR: recibe texto del servidor
    private PrintWriter out;            // ESCRITOR: envía texto al servidor

    // ----- Estado -----
    private volatile boolean running = false; // Controla el bucle del hilo lector

    // Punto de entrada
    public static void main(String[] args) {
        // Iniciar la aplicación en el hilo de eventos de Swing
        SwingUtilities.invokeLater(() -> new ClientGUI().showLogin());
    }

    // ---------------- Ventana de LOGIN ----------------
    private void showLogin() {
        loginFrame = new JFrame("Cliente de Chat - Conexión");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(360, 200);
        loginFrame.setLocationRelativeTo(null); // Centrar la ventana

        // Mensaje fijo de bienvenida
        welcomeLabel = new JLabel("Bienvenido al servidor, ingrese un usuario:");
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Campo para el nombre de usuario
        userField = new JTextField();
        connectBtn = new JButton("Conectar");

        // Panel que organiza los componentes
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(welcomeLabel, BorderLayout.NORTH);
        panel.add(userField, BorderLayout.CENTER);
        panel.add(connectBtn, BorderLayout.SOUTH);

        // Acción al presionar "Conectar"
        connectBtn.addActionListener(e -> connect());

        loginFrame.getContentPane().add(panel);
        loginFrame.setVisible(true);
    }

    // ---------------- Conexión al servidor ----------------
    private void connect() {
        String user = userField.getText().trim(); // Leer el nombre

        if (user.isEmpty()) {
            JOptionPane.showMessageDialog(loginFrame, "Ingrese un nombre de usuario.",
                    "Falta usuario", JOptionPane.WARNING_MESSAGE);
            return;
        }

        connectBtn.setEnabled(false); // Deshabilitar para evitar doble click

        // Usamos un hilo separado para que la UI no se congele
        new Thread(() -> {
            try {
                // 1) Crear el socket y conectarse al servidor
                socket = new Socket(HOST, PORT);

                // 2) Preparar los flujos de entrada y salida
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));   // Lee del servidor
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true); // Envía al servidor

                // 3) Leer el primer mensaje del servidor (por ejemplo, bienvenida)
                String first = in.readLine();

                // 4) Crear la ventana del chat
                createChatWindow(user, first);

                // 5) Enviar el nombre de usuario al servidor
                out.println(user);

                // 6) Arrancar el hilo que escucha los mensajes del servidor
                running = true;
                new Thread(this::readerLoop, "ReaderThread").start();

                // 7) Cerrar login y mostrar la ventana del chat
                SwingUtilities.invokeLater(() -> {
                    loginFrame.dispose();
                    chatFrame.setVisible(true);
                });

            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    connectBtn.setEnabled(true);
                    JOptionPane.showMessageDialog(loginFrame,
                            "No se pudo conectar al servidor: " + ex.getMessage(),
                            "Error de conexión", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // ---------------- Ventana del CHAT ----------------
    private void createChatWindow(String user, String firstMsg) {
        chatFrame = new JFrame("Chat - " + user + " @ " + HOST + ":" + PORT);
        chatFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        chatFrame.setSize(640, 480);
        chatFrame.setLocationRelativeTo(null); // Centrar ventana

        // Área para mostrar mensajes recibidos
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        // Campo para escribir mensajes y botones
        inputField = new JTextField();
        sendBtn = new JButton("Enviar");
        listBtn = new JButton("/listar");
        quitBtn = new JButton("/quitar");
        statusLabel = new JLabel("Conectado");

        // Paneles para organizar la interfaz
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        JPanel right = new JPanel(new GridLayout(3, 1, 6, 6));
        right.add(sendBtn);
        right.add(listBtn);
        right.add(quitBtn);

        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(right, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        chatFrame.getContentPane().add(new JScrollPane(chatArea), BorderLayout.CENTER);
        chatFrame.getContentPane().add(bottom, BorderLayout.SOUTH);
        chatFrame.getContentPane().add(statusLabel, BorderLayout.NORTH);

        // Acción de enviar mensaje
        ActionListener sendAction = e -> {
            String text = inputField.getText().trim();
            if (text.isEmpty() || out == null) return;
            out.println(text);          // Enviar al servidor
            inputField.setText("");     // Limpiar campo

            if ("/quitar".equalsIgnoreCase(text)) {
                closeClient();          // Cerrar si el usuario quiere salir
            }
        };

        sendBtn.addActionListener(sendAction);
        inputField.addActionListener(sendAction);

        // Botón /listar
        listBtn.addActionListener(e -> {
            if (out != null) out.println("/listar");
        });

        // Botón /quitar
        quitBtn.addActionListener(e -> {
            if (out != null) out.println("/quitar");
            closeClient();
        });

        // Cierre de ventana
        chatFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (out != null) out.println("/quitar");
                closeClient();
            }
        });

        // Mostrar mensaje inicial del servidor
        if (firstMsg != null && !firstMsg.isEmpty()) {
            appendChat(firstMsg);
        }
    }

    // ---------------- Hilo lector ----------------
    private void readerLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                appendChat(line); // Mostrar mensaje recibido
            }
        } catch (IOException ignored) {
        } finally {
            appendChat("[CLIENTE] Conexión cerrada.");
            closeClient();
        }
    }

    // Agregar mensajes al chat de forma segura desde cualquier hilo
    private void appendChat(String msg) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(msg + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    // Cerrar la conexión y limpiar recursos
    private void closeClient() {
        running = false;
        SwingUtilities.invokeLater(() -> statusLabel.setText("Desconectado"));
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        if (out != null) out.close();
    }
}
