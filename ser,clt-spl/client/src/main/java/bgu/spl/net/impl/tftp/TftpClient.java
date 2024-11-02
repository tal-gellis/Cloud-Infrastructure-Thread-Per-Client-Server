package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;

import bgu.spl.net.impl.tftp.TftpEncoderDecoder.Opcode;
public class TftpClient {
    static Object lock;
    static Path lastFileRec;
    static String lastFileRecName;
    static byte[] nextFileToSend;
    static String nextFileToSendName;
    static int blockIndexSent;
    static Opcode lastCommand;
    
public static void main(String[] args) {
        if (args.length != 2) {
            return;
        }
        lock = new Object();
        lastFileRec = null;
        String ipAddress = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket(ipAddress, port)) {
            // Use the socket for client-side operations
            Thread outThread = new Thread(new keyboardThread(socket));
            Thread inThread = new Thread(new listeningThread(socket));
    
            outThread.start();
            inThread.start();

    
            // Wait for both threads to finish
            outThread.join();
            inThread.join();

        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
    }
}



