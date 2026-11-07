package com.marketplace.auth_service.perfil;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Receptor SMTP local para pruebas (FEAT-04-T3): escucha en un puerto libre de 127.0.0.1, acepta
 * mensajes sin autenticación ni TLS y los conserva en memoria. No reenvía nada. Habla lo justo del
 * protocolo (EHLO/HELO, MAIL, RCPT, DATA, RSET, NOOP, QUIT) para el {@code JavaMailSender} real.
 */
public final class SmtpLocalDePrueba implements AutoCloseable {

    /** Mensaje recibido; {@code datos} son los bytes crudos (cabeceras + cuerpo) tal como llegaron. */
    public record Recibido(String remitente, List<String> destinatarios, byte[] datos) {}

    private final ServerSocket servidor;
    private final List<Recibido> recibidos = new CopyOnWriteArrayList<>();
    private final Thread aceptador;
    private volatile boolean activo = true;

    private SmtpLocalDePrueba(ServerSocket servidor) {
        this.servidor = servidor;
        this.aceptador = new Thread(this::aceptar, "smtp-local-de-prueba");
        this.aceptador.setDaemon(true);
        this.aceptador.start();
    }

    public static SmtpLocalDePrueba iniciar() {
        try {
            return new SmtpLocalDePrueba(new ServerSocket(0, 20, InetAddress.getLoopbackAddress()));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo abrir el receptor SMTP local", e);
        }
    }

    public int puerto() {
        return servidor.getLocalPort();
    }

    public List<Recibido> recibidos() {
        return List.copyOf(recibidos);
    }

    public void limpiar() {
        recibidos.clear();
    }

    @Override
    public void close() {
        activo = false;
        try {
            servidor.close();
        } catch (IOException ignorado) {
            // Se cierra al terminar la clase de prueba.
        }
    }

    private void aceptar() {
        while (activo) {
            try {
                Socket conexion = servidor.accept();
                Thread t = new Thread(() -> atender(conexion), "smtp-local-de-prueba-conexion");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return; // servidor cerrado
            }
        }
    }

    private void atender(Socket conexion) {
        try (conexion; InputStream raw = new BufferedInputStream(conexion.getInputStream());
             OutputStream out = conexion.getOutputStream()) {
            conexion.setSoTimeout(10_000);
            responder(out, "220 localhost ESMTP prueba");
            String remitente = null;
            List<String> destinatarios = new ArrayList<>();
            String linea;
            while ((linea = leerLinea(raw)) != null) {
                String orden = linea.toUpperCase(java.util.Locale.ROOT);
                if (orden.startsWith("EHLO") || orden.startsWith("HELO")) {
                    responder(out, "250 localhost");
                } else if (orden.startsWith("MAIL FROM")) {
                    remitente = direccion(linea);
                    destinatarios = new ArrayList<>();
                    responder(out, "250 OK");
                } else if (orden.startsWith("RCPT TO")) {
                    destinatarios.add(direccion(linea));
                    responder(out, "250 OK");
                } else if (orden.equals("DATA")) {
                    responder(out, "354 Termina con <CRLF>.<CRLF>");
                    recibidos.add(new Recibido(remitente, List.copyOf(destinatarios), leerDatos(raw)));
                    responder(out, "250 OK");
                } else if (orden.equals("RSET") || orden.equals("NOOP")) {
                    responder(out, "250 OK");
                } else if (orden.equals("QUIT")) {
                    responder(out, "221 Adiós");
                    return;
                } else {
                    responder(out, "502 No implementado");
                }
            }
        } catch (IOException ignorado) {
            // Conexión cortada por el cliente o por el cierre del servidor.
        }
    }

    private static String direccion(String linea) {
        int a = linea.indexOf('<');
        int b = linea.indexOf('>', a + 1);
        return a >= 0 && b > a ? linea.substring(a + 1, b).strip() : "";
    }

    private static void responder(OutputStream out, String texto) throws IOException {
        out.write((texto + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    /** Lee hasta CRLF (o LF) sin conservar el terminador; {@code null} si la conexión terminó. */
    private static String leerLinea(InputStream in) throws IOException {
        var buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buffer.write(b);
        }
        if (b == -1 && buffer.size() == 0) return null;
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    /** Lee el cuerpo DATA hasta la línea con solo un punto, deshaciendo el doble punto inicial. */
    private static byte[] leerDatos(InputStream in) throws IOException {
        var datos = new ByteArrayOutputStream();
        String linea;
        while ((linea = leerLinea(in)) != null) {
            if (linea.equals(".")) break;
            if (linea.startsWith("..")) linea = linea.substring(1);
            datos.write((linea + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        return datos.toByteArray();
    }
}
