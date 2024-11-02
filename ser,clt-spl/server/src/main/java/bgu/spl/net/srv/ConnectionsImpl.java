package bgu.spl.net.srv;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    private ConcurrentHashMap<Integer,ConnectionHandler<T>> connections;

    public ConnectionsImpl(){
        connections = new ConcurrentHashMap<Integer,ConnectionHandler<T>>();
    }
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        connections.put(connectionId, handler);
    }
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connections.get(connectionId);
        if(handler!=null){
            handler.send(msg);
            //System.out.println("message sent by server");
            return true;
        }
        if(handler == null) {
            System.out.println("handler is null.");
        }
        return false;
    }
    public void disconnect(int connectionId) {
        connections.remove(connectionId);
        
    }
    public void sendAll(T msg) {

        for(Integer ID : BaseServer.loggedIn.keySet()){
            send(ID, msg);
    }
    }

    
}