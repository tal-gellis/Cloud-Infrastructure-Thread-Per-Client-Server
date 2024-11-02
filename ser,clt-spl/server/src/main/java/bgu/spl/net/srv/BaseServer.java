package bgu.spl.net.srv;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;



public abstract class BaseServer<T> implements Server<T> {

    protected final int port;
    protected final Supplier<BidiMessagingProtocol<T>> protocolFactory;
    protected final Supplier<MessageEncoderDecoder<T>> encdecFactory;
    protected ServerSocket sock;
    public static ConcurrentHashMap<Integer,String> loggedIn;   
    public Connections<T> connections;
    int connectionsCounter;

    public BaseServer(
            int port,
            Supplier<BidiMessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
		this.sock = null;

        
    }

    @Override
    public void serve() {

        try (ServerSocket serverSock = new ServerSocket(port)) {
			System.out.println("BASE Server started");
            this.sock = serverSock;
            connections = new ConnectionsImpl<T>();
            loggedIn = new ConcurrentHashMap<Integer,String>();
            connectionsCounter = 0;
            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSock = serverSock.accept();
                BlockingConnectionHandler<T> handler = new BlockingConnectionHandler<T>(
                        clientSock,
                        encdecFactory.get(),
                        protocolFactory.get()
                        );
                connectionsCounter++;
                handler.protocol.start(connectionsCounter,connections);
                connections.connect(connectionsCounter, handler);
                execute(handler);
            }
        } catch (IOException ex) {
        }

        System.out.println("server closed!!!");
    }

    @Override
    public void close() throws IOException {
		if (sock != null)
			sock.close();
    }

    protected abstract void execute(BlockingConnectionHandler<T>  handler);

}
