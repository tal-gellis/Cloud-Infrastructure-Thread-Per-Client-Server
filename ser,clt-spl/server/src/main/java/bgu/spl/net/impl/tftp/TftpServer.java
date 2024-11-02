package bgu.spl.net.impl.tftp;
import bgu.spl.net.srv.Server;

public class TftpServer {

    public static void main(String[] args) {
        Server<byte[]> myServer = Server.threadPerClient(
                Integer.parseInt(args[0]),
                () -> new TftpProtocol(),
                TftpEncoderDecoder::new
        );
        myServer.serve();
    }
}

