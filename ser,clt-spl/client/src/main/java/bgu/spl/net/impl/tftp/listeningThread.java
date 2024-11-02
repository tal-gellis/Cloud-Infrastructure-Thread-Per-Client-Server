package bgu.spl.net.impl.tftp;

import java.util.Arrays;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder.Opcode;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;;

public class listeningThread implements Runnable {
    public Socket socket;
    private TftpEncoderDecoder endec;
    public ArrayList<Byte> buffer;

    public listeningThread(Socket sock1){
        this.socket = sock1;
        endec = new TftpEncoderDecoder();
        buffer = new ArrayList<Byte>();
    }

    public void run(){
        try{
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            int read;
            
            while ((read=in.read())>=0) {
                byte[] nextMessage = endec.decodeNextByte((byte) read);
                
                if (nextMessage != null) {
                    
                    short opIndex = ( short ) ((( short ) nextMessage[0]) << 8 | ( short ) ( nextMessage[1]) & 0x00ff);
                    Opcode op = Opcode.fromU16(opIndex);
                    byte[] msg = Arrays.copyOfRange(nextMessage, 2, nextMessage.length);

                    if(op==Opcode.DATA){
                        short packetSize = ( short ) ((( short ) msg[0]) << 8 | ( short ) ( msg[1]) & 0x00ff);
                        short blockNumber = ( short ) ((( short ) msg[2]) << 8 | ( short ) ( msg[3])& 0x00ff );
                        byte[] data = Arrays.copyOfRange(msg, 4, msg.length);
                        ArrayList<Byte> name = new ArrayList<Byte>();
                        if(TftpClient.lastCommand == Opcode.RRQ){
                            File newFileRec = TftpClient.lastFileRec.toFile();
                            try (FileOutputStream fos = new FileOutputStream(newFileRec, true)) {
                                fos.write(data);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if(packetSize<512){
                                synchronized(TftpClient.lock){
                                TftpClient.lock.notifyAll();
                                System.out.println("RRQ " + TftpClient.lastFileRecName + " complete");
                                }
                            }
                            
                        }
                        if(TftpClient.lastCommand == Opcode.DIRQ){
                            for(byte next : data)
                                buffer.add((Byte) next);
                            if(packetSize<512){
                                synchronized(TftpClient.lock){
                                    TftpClient.lock.notifyAll();
                                }
                                for(byte next : buffer){
                                    if(next != 0)
                                        name.add(next);
                                    else if(next==0){
                                        byte[] byteName = new byte[name.size()];
                                        for (int i = 0; i < name.size(); i++) {
                                            byteName[i] = name.get(i);
                                        }
                                        String sName = new String(byteName);
                                        System.out.println(sName);
                                        name.clear();
                                    }
                                }
                                buffer.clear();
                            }
                        }
                        sendAck(blockNumber);
                    }
                    if(op==Opcode.ACK){
                        short receivedBlockNumber = ( short ) ((( short ) msg [0]) << 8 | ( short ) ( msg [1])& 0x00ff );
                        System.out.println("ACK " + receivedBlockNumber);
                        if (TftpClient.lastCommand == Opcode.WRQ && receivedBlockNumber == (TftpClient.blockIndexSent)) {
                            sendData();
                        }

                    }
                    if(op==Opcode.BCAST){
                        byte del = msg[0];
                        String ret = "BCAST ";
                        if(del==(byte)0)
                            ret = ret + "del ";
                        else if(del==(byte)1)
                            ret = ret + "add ";
                        String name = new String(Arrays.copyOfRange(msg,1,msg.length));
                        ret = ret + name;
                        System.out.println(ret);
                    }
                    if(op==Opcode.ERROR){
                        String ret = "Error ";
                        short errNum = ( short ) ((( short ) msg [0]) << 8 | ( short ) ( msg [1])& 0x00ff );
                        if(errNum==1){
                            try {
                                Files.delete(TftpClient.lastFileRec);
                            } 
                             catch (IOException e){}
                        }
                        ret = ret + errNum + " ";
                        String errMsg = new String(Arrays.copyOfRange(msg,2,msg.length));
                        ret = ret + errMsg;
                        System.out.println(ret);
                        synchronized(TftpClient.lock){
                            TftpClient.lock.notifyAll();
                        }
                    }
                }
            }
            keyboardThread.terminateKeyboard = true;

        }
        catch(IOException exception){}
    }
    public void sendAck(short blockNum){
        short opShort = 4;
        byte[] opBy = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
        byte[] block = new byte []{( byte ) ( blockNum >> 8) , ( byte ) ( blockNum & 0xff ) };
        byte[][] arrays = {opBy,block};
        int counter = 0;
        byte[] packet = new byte[opBy.length+block.length];
        for (byte[] arr : arrays) {
            int currArrSize = arr.length;
            for(int i = 0; i<currArrSize ; i++) {
                packet[counter] = arr[i];
                counter++;
            }
        } 
        try{
        BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        out.write(packet);
        out.flush();
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
    }
    public void sendData() {
        short opShort = 3;
        int numOfBlocks = (int) (1 + TftpClient.nextFileToSend.length/512);
        if (numOfBlocks > TftpClient.blockIndexSent) {
            int startingIndex = (TftpClient.blockIndexSent)*512;
            int endingIndex = Math.min((TftpClient.blockIndexSent+1)*512,TftpClient.nextFileToSend.length);
            short sizeShort = (short) (endingIndex - startingIndex);
            byte[] op = new byte []{( byte ) ( opShort >> 8 & 0xff) , ( byte ) ( opShort & 0xff ) };
            byte[] size = new byte []{( byte ) ( sizeShort >> 8) , ( byte ) ( sizeShort & 0xff ) };
            byte[] blockNumber = new byte []{( byte ) ( (TftpClient.blockIndexSent+1) >> 8) , ( byte ) ( (TftpClient.blockIndexSent+1) & 0xff ) };
            byte[] nextDataPack = Arrays.copyOfRange(TftpClient.nextFileToSend, startingIndex, endingIndex);
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
            try{
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            out.write(packet);
            out.flush();
            }
            catch(IOException ex) {
                ex.printStackTrace();
            }
            TftpClient.blockIndexSent++;
        }
        else {
            System.out.println("WRQ " + TftpClient.nextFileToSendName + " complete");
        }
    }
}