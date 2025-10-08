#!/usr/bin/env python3
import socket
import threading
import queue
import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext

# Codificación usada para enviar/recibir texto
ENCODING = "utf-8"

class ChatClientGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Cliente de Chat (Python)")

        # --- Estado/red ---
        self.sock = None                 # socket TCP hacia el servidor
        self.reader_thread = None        # hilo que lee mensajes del servidor
        self.stop_event = threading.Event()  # bandera para cortar el hilo lector
        self.msg_queue = queue.Queue()   # cola segura para pasar mensajes del hilo lector a la GUI

        # Construye la ventana de login
        self._build_login()

        # Bucle: cada 80 ms vaciamos la cola y pintamos mensajes en el chat (no bloquea la UI)
        self.root.after(80, self._poll_queue)

    # ======================== LOGIN ========================
    def _build_login(self):
        """Arma la pantalla de conexión (host, puerto, usuario)."""
        self.login = ttk.Frame(self.root, padding=12)
        self.login.grid(row=0, column=0, sticky="nsew")

        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)

        # Host/IP
        ttk.Label(self.login, text="Host/IP del servidor:").grid(row=0, column=0, sticky="w")
        self.host_var = tk.StringVar(value="127.0.0.1")
        ttk.Entry(self.login, textvariable=self.host_var, width=28).grid(row=0, column=1, sticky="ew")

        # Puerto
        ttk.Label(self.login, text="Puerto:").grid(row=1, column=0, sticky="w", pady=(6,0))
        self.port_var = tk.StringVar(value="5000")
        ttk.Entry(self.login, textvariable=self.port_var, width=10).grid(row=1, column=1, sticky="w", pady=(6,0))

        # Usuario
        ttk.Label(self.login, text="Usuario:").grid(row=2, column=0, sticky="w", pady=(6,0))
        self.user_var = tk.StringVar()
        user_entry = ttk.Entry(self.login, textvariable=self.user_var, width=28)
        user_entry.grid(row=2, column=1, sticky="ew", pady=(6,0))
        user_entry.focus()

        # Botón conectar
        self.connect_btn = ttk.Button(self.login, text="Conectar", command=self.connect)
        self.connect_btn.grid(row=3, column=0, columnspan=2, sticky="ew", pady=(12,0))

        for i in range(2):
            self.login.columnconfigure(i, weight=1)

    # ======================== CHAT ========================
    def _build_chat(self, title):
        """Arma la ventana principal del chat (área de mensajes, entrada, botones)."""
        self.chat = ttk.Frame(self.root, padding=8)
        self.chat.grid(row=0, column=0, sticky="nsew")
        self.root.title(title)

        # Área de texto: solo lectura, con scroll
        self.text = scrolledtext.ScrolledText(self.chat, wrap="word", height=18, state="disabled")
        self.text.grid(row=0, column=0, columnspan=3, sticky="nsew", padx=(0,0), pady=(0,6))

        # Campo de entrada + botones
        self.entry_var = tk.StringVar()
        entry = ttk.Entry(self.chat, textvariable=self.entry_var)
        entry.grid(row=1, column=0, sticky="ew", padx=(0,6))
        entry.bind("<Return>", lambda e: self.send_message())  # Enter = enviar

        send_btn  = ttk.Button(self.chat, text="Enviar",  command=self.send_message)
        list_btn  = ttk.Button(self.chat, text="/listar", command=lambda: self.send_command("/listar"))
        quit_btn  = ttk.Button(self.chat, text="/quitar", command=lambda: self.send_command("/quitar"))
        send_btn.grid(row=1, column=1, sticky="ew")
        list_btn.grid(row=1, column=2, sticky="ew", padx=(6,0))
        quit_btn.grid(row=2, column=2, sticky="ew", pady=(6,0))

        # Estado arriba/abajo (Conectado/Desconectado)
        self.status = ttk.Label(self.chat, text="Conectado", anchor="w")
        self.status.grid(row=2, column=0, columnspan=2, sticky="ew", pady=(6,0))

        # Hacer la grilla flexible
        self.chat.columnconfigure(0, weight=1)
        self.chat.rowconfigure(0, weight=1)

        # Al cerrar la ventana, hacemos cierre ordenado
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    # ======================== CONEXIÓN ========================
    def connect(self):
        """Valida datos y dispara la conexión en un hilo aparte."""
        host = self.host_var.get().strip()
        port_s = self.port_var.get().strip()
        user = self.user_var.get().strip()

        if not host or not port_s or not user:
            messagebox.showwarning("Datos faltantes", "Complete host, puerto y usuario.")
            return

        # Validar puerto
        try:
            port = int(port_s)
            if not (1 <= port <= 65535):
                raise ValueError
        except ValueError:
            messagebox.showerror("Puerto inválido", "Ingrese un puerto válido (1-65535).")
            return

        # Deshabilito botón para evitar doble click
        self.connect_btn.config(state="disabled")

        # Conectar en background para no congelar la interfaz
        threading.Thread(target=self._do_connect, args=(host, port, user), daemon=True).start()

    def _do_connect(self, host, port, user):
        """Establece el socket, lee la bienvenida, envía el usuario e inicia el hilo lector."""
        try:
            # 1) Crear socket TCP y conectar
            self.sock = socket.create_connection((host, port), timeout=10)

            # 2) Leer PRIMERA línea (bienvenida) con recv() hasta '\n'
            first = self._readline_blocking()
            if first:
                self.msg_queue.put(first)

            # 3) Enviar el usuario (el servidor lo espera como primera entrada del cliente)
            self._send_line(user)

            # 4) Cambiar a la ventana de chat (en el hilo de la UI)
            self.root.after(0, lambda: (self.login.destroy(),
                                        self._build_chat(f"Chat - {user} @ {host}:{port}")))

            # 5) Iniciar hilo lector que junta líneas con recv()
            self.stop_event.clear()
            self.reader_thread = threading.Thread(target=self._reader_loop, daemon=True)
            self.reader_thread.start()

        except Exception as e:
            # Si falla la conexión, reactivamos el botón y mostramos error
            self.root.after(0, lambda: (
                self.connect_btn.config(state="normal"),
                messagebox.showerror("Error de conexión", f"No se pudo conectar a {host}:{port}\n\n{e}")
            ))

    # ======================== LECTURA (robusta con recv) ========================
    def _readline_blocking(self):
        """
        Lee una línea terminada en LF usando recv().
        Evita problemas de makefile("r") en Windows (que a veces 'cierra' antes de tiempo).
        """
        buf = b""
        self.sock.settimeout(10)  # pequeño timeout para la bienvenida
        try:
            while True:
                chunk = self.sock.recv(4096)
                if not chunk:
                    return ""  # el servidor cerró
                buf += chunk
                if b"\n" in buf:
                    line, _, _ = buf.partition(b"\n")
                    return line.decode(ENCODING, errors="replace").rstrip("\r")
        except Exception:
            return ""
        finally:
            self.sock.settimeout(None)

    def _reader_loop(self):
        """
        Hilo lector: recibe datos del socket, acumula en un buffer y
        entrega línea por línea (separadas por '\n') a la cola de mensajes.
        """
        buf = b""
        try:
            while not self.stop_event.is_set():
                data = self.sock.recv(4096)
                if not data:
                    break  # cierre real del servidor
                buf += data
                # extraer todas las líneas completas disponibles
                while b"\n" in buf:
                    line, _, buf = buf.partition(b"\n")
                    msg = line.decode(ENCODING, errors="replace").rstrip("\r")
                    self.msg_queue.put(msg)
        except Exception:
            pass
        finally:
            # Avisamos a la UI que terminó la conexión
            self.stop_event.set()
            self.msg_queue.put("[CLIENTE] Conexión cerrada por el servidor.")

    def _poll_queue(self):
        """Vacía la cola de mensajes y los agrega al área de texto."""
        while True:
            try:
                msg = self.msg_queue.get_nowait()
            except queue.Empty:
                break
            self._append(msg)
        # Reprogramar el chequeo
        self.root.after(80, self._poll_queue)

    # ======================== ENVÍO ========================
    def _send_line(self, text: str) -> bool:
        """
        Envía una línea al servidor (terminada en '\n') usando sendall.
        Esto es compatible con el servidor Java que usa readLine().
        """
        try:
            self.sock.sendall((text + "\n").encode(ENCODING))
            return True
        except Exception as e:
            messagebox.showerror("Error enviando", str(e))
            return False

    def send_message(self):
        """Toma el texto del input y lo envía; con '/quitar' actualiza el estado local."""
        txt = self.entry_var.get().strip()
        if not txt or not self.sock:
            return
        if self._send_line(txt):
            self.entry_var.set("")
            if txt.lower() == "/quitar":
                self.status.config(text="Desconectado")

    def send_command(self, cmd):
        """Atajos para /listar y /quitar desde botones."""
        if self.sock:
            self._send_line(cmd)

    # ======================== UTILIDADES DE UI ========================
    def _append(self, text):
        """Inserta texto al final del área de chat (modo solo lectura)."""
        if not hasattr(self, "text"):
            return
        self.text.config(state="normal")
        self.text.insert("end", text + "\n")
        self.text.see("end")
        self.text.config(state="disabled")

    def _on_close(self):
        """Cierre ordenado al salir de la ventana."""
        try:
            if self.sock:
                self._send_line("/quitar")  # pedimos salida limpia al servidor
        except Exception:
            pass
        try:
            if self.sock:
                self.sock.shutdown(socket.SHUT_RDWR)
        except Exception:
            pass
        try:
            if self.sock:
                self.sock.close()
        except Exception:
            pass
        self.stop_event.set()
        self.root.destroy()

# ======================== MAIN ========================
def main():
    root = tk.Tk()
    # Escalado leve para que se vea más cómodo en pantallas HiDPI
    try:
        root.tk.call("tk", "scaling", 1.15)
    except Exception:
        pass
    ChatClientGUI(root)
    root.mainloop()

if __name__ == "__main__":
    main()
