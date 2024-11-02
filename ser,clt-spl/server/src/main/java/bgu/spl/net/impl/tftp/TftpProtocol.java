package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder.Opcode;
import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    public int id;
    public Connections<byte[]> connections;
    public boolean shouldTerminate;
    private File newFileRec;
    private int blockIndexRec;
    private byte[] nextFileToSend;
    private int blockIndexSent;
    private boolean loggedInFlag;


    private final String relativePath = "Files";


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.id = connectionId;
        this.connections = connections;
        this.shouldTerminate = false;
        blockIndexRec = 1;
        blockIndexSent = 1;
        loggedInFlag = false;

    }

    @Override
    public void process(byte[] message) {
        // Creating an op and a msg from message
        short opIndex = ( short ) ((( short ) message[0]) << 8 | ( short ) ( message[1]) & 0x00ff );
        Opcode op = Opcode.fromU16(opIndex);
        byte[] msg = Arrays.copyOfRange(message, 2, message.length);
        //Now checking different cases
        if (op == Opcode.None) { 
            connections.send(id, errorPacket(4));
        }
        else if (op == Opcode.LOGRQ){
            String name = new String(msg);
            if(loggedInFlag || BaseServer.loggedIn.containsValue(name)) {
                connections.send(id, errorPacket(7));
            }
            else{
                BaseServer.loggedIn.put(id,name);
                loggedInFlag = true;
                connections.send(id, ackPacket(0));
            }
        }
        else if (op == Opcode.DISC){
            BaseServer.loggedIn.remove(id);
            connections.send(id, ackPacket(0));
            connections.disconnect(id);
            shouldTerminate=true;
        }
        else if (loggedInFlag){

            if (op == Opcode.DIRQ) {
                ArrayList<Byte> strings = new ArrayList<Byte>();
                File folder = new File(relativePath);
                File[] files = folder.listFiles();
                for (File f: files) {
                    byte[] fileNameBytes = f.getName().getBytes();
                    for(Byte b : fileNameBytes) {
                        strings.add(b);
                    }
                    strings.add((byte) 0);
                }
                byte[] data = new byte[strings.size()];
                int i = 0;
                for(byte b : strings) {
                    data[i] = b;
                    i++;
                }
                nextFileToSend=data;
                blockIndexSent=1;
                sendData();
            }
            else if (op == Opcode.RRQ){
                String name = new String(msg);
                Path myPath = Paths.get("").toAbsolutePath();
                Path path = myPath.resolve("Files");
                File folder = new File(path.toString());
                File[] files = folder.listFiles();
                boolean found = false;
                for (File f: files) {
                    if (name.equals(f.getName())){
                        try{
                            found = true;
                            nextFileToSend = Files.readAllBytes(f.toPath());
                            blockIndexSent = 1 ;
                            sendData();
                        }
                        catch(IOException e){}
                    }
                    if(found)
                        break;
                }
                if(!found)
                    connections.send(id, errorPacket(1));
            }
            else if (op == Opcode.WRQ) {
                String name = new String(msg);
                Path myPath = Paths.get("").toAbsolutePath();
                Path path = myPath.resolve("Files");
                File folder = new File(path.toString());
                boolean found =false;
                for(File f: folder.listFiles()) {
                    if((f.getName()).equals(name)) {
                        found = true;
                        connections.send(id, errorPacket(5));
                        break;
                    }
                }

                if(!found){
                    newFileRec = new File(relativePath, name);
                    blockIndexRec = 1;
                    connections.send(id, ackPacket(0));
                }
            }
            else if (op == Opcode.DATA) {

                short receivedBlockNumber = (short) (((short) msg[2]) << 8 | (short) (msg[3]) & 0x00ff);
                if (receivedBlockNumber == blockIndexRec) {
                    byte[] data = Arrays.copyOfRange(msg,4,msg.length);

                    // Write the data to the file
                    try (FileOutputStream fos = new FileOutputStream(newFileRec, true)) {
                        fos.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Send acknowledgment packet for the received block number
                    connections.send(id, ackPacket(blockIndexRec));
                    // Increment the block number for the next iteration
                    blockIndexRec++;
                    if (data.length < 512) {
                        connections.sendAll(createBcast((byte) 1, newFileRec.getName()));
                    }
                }
                else {
                    connections.send(id, errorPacket(2));
                }
            }
            else if (op == Opcode.DELRQ){
                String name = new String(msg);
                Path myPath = Paths.get("").toAbsolutePath();
                Path path = myPath.resolve("Files");
                File folder = new File(path.toString());
                File[] files = folder.listFiles();
                boolean found = false;
                for (File f: files) {
                    if (name.equals(f.getName())){
                        try{
                            found = true;
                            Files.delete(f.toPath());
                            connections.send(id, ackPacket(0));
                            connections.sendAll(createBcast((byte)0, name));
                        }
                        catch(IOException e){}
                    }
                }
                if(!found)
                    connections.send(id, errorPacket(1));
            }
            else if (op == Opcode.ACK) {
                short blockNumber = ( short ) ((( short ) message[2]) << 8 | ( short ) ( message[3])& 0x00ff );
                if (blockNumber == (blockIndexSent-1)) {
                    sendData();
                }
                else if(blockNumber > (blockIndexSent-1)) {
                    connections.send(id, errorPacket(0));
                }
            }
        }
        else if (!loggedInFlag && op != Opcode.None &op != Opcode.LOGRQ ) {
            connections.send(id, errorPacket(6));
        }
    }
    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 
    public byte[] errorPacket(int e) {
        short opShort = 5;
        byte[] op = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
        byte[] errCase = new byte []{( byte ) ( e >> 8) , ( byte ) ( e & 0xff ) };
        byte[] msg=new byte[1];
        if (e==0){
           msg = "Not defined.".getBytes();
        }
        if (e==1) {
            msg = "File not found.".getBytes();
        }
        if (e==2) {
            msg = "Access violation.".getBytes();
        }
        if (e==3) {
            msg = "Disc full.".getBytes();
        }
        if (e==4) {
            msg = "Illegal Tftp operation.".getBytes();
        }
        if (e==5) {
            msg = "File already exists.".getBytes();
        }
        if (e==6) {
            msg = "User not logged in.".getBytes();
        }
        if (e==7) {
            msg = "User already logged in.".getBytes();
        }
        byte[][] arrays = {op,errCase,msg};
        int counter = 0;
        byte[] packet = new byte[op.length+errCase.length+msg.length+1];
        for (byte[] arr : arrays) {
            int currArrSize = arr.length;
            for(int i = 0; i<currArrSize ; i++) {
                packet[counter] = arr[i];
                counter++;
            }
        }
        packet[packet.length-1] = (byte) 0; 
        return packet;
    }
    public byte[] ackPacket(int block) {
        short opShort = 4;
        byte[] op = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
        byte[] blockCase = new byte []{( byte ) ( block >> 8) , ( byte ) ( block & 0xff ) };
        byte[] packet = new byte[op.length+blockCase.length];
        packet[0]=op[0];
        packet[1]=op[1];
        packet[2]=blockCase[0];
        packet[3]=blockCase[1];
        return packet;
    }
    public void sendData() {
        short opShort = 3;
        int numOfBlocks = (int) (1 + nextFileToSend.length/512);
        if (numOfBlocks>= blockIndexSent) {
            int startingIndex = (blockIndexSent-1)*512;
            int endingIndex = Math.min(blockIndexSent*512,nextFileToSend.length);

            short sizeShort = (short) (endingIndex - startingIndex);
            byte[] op = new byte []{( byte ) ( opShort >> 8 & 0xff) , ( byte ) ( opShort & 0xff ) };
            byte[] size = new byte []{( byte ) ( sizeShort >> 8) , ( byte ) ( sizeShort & 0xff ) };

            byte[] blockNumber = new byte []{( byte ) ( blockIndexSent >> 8) , ( byte ) ( blockIndexSent & 0xff ) };
            byte[] nextDataPack = Arrays.copyOfRange(nextFileToSend, startingIndex, endingIndex);
            byte[][] arrays = {op,size,blockNumber,nextDataPack};
            int counter = 0;
            byte[] packet = new byte[op.length+size.length+blockNumber.length+nextDataPack.length];
            for (byte[] arr : arrays) {
                int currArrSize = arr.length;
                for(int i = 0; i<currArrSize ; i++) {
                    packet[counter] = arr[i];
                    counter++;
                }
            }
            connections.send(id, packet);
            blockIndexSent++;
        }

    }

    private byte[] createBcast(byte type, String name) {
        short opShort = 9;
        byte[] op = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
        byte[] typeArr = {type};
        byte[] nameArr = name.getBytes();
        byte[][] arrays = {op,typeArr,nameArr};
        int counter = 0;
        byte[] packet = new byte[op.length+typeArr.length+nameArr.length+1];
        for (byte[] arr : arrays) {
            int currArrSize = arr.length;
            for(int i = 0; i<currArrSize ; i++) {
                packet[counter] = arr[i];
                counter++;
            }
        }
        packet[packet.length-1] = (byte) 0;
        return packet;
    }
}