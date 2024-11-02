package bgu.spl.net.srv;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
//import bgu.spl.net.impl.tftp.TftpEncoderDecoder;
//import bgu.spl.net.impl.tftp.TftpProtocol;
//import bgu.spl.net.impl.tftp.TftpServer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    public final BidiMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encDec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;
    //public int id;
    //private Connections<byte[]> connections;
    // public ConcurrentHashMap<String,Integer> loggedIn;


    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> encDec, BidiMessagingProtocol<T> protocol) {
        this.sock = sock;
        this.encDec = encDec;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;
            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                T nextMessage = encDec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    protocol.process(nextMessage); 
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(T msg) {
        try{
        out.write((encDec.encode(msg)));
        out.flush();
        }
        catch (IOException ex){
            ex.printStackTrace();
        }
    }
}
