package bgu.spl.net.impl.tftp;

import java.util.Scanner;

import bgu.spl.net.impl.tftp.TftpEncoderDecoder.Opcode;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class keyboardThread implements Runnable {
    private Socket socket;
    BufferedOutputStream out;
    static boolean terminateKeyboard;

    public keyboardThread(Socket s) {
        socket = s;
    }
    @Override
    public void run() {
        try{
        Scanner scanner = new Scanner(System.in);
        out = new BufferedOutputStream(socket.getOutputStream());
            while (!terminateKeyboard) {
                // Wait for keyboard input
                String userInput = scanner.nextLine();
                byte[] toSend = process(userInput);
                
                if(toSend != null) {
                    short opShort =( short ) ((( short ) toSend [0]) << 8 | ( short ) ( toSend [1])& 0x00ff );
                    out.write(toSend);
                    out.flush();
                    if (opShort == 1 || opShort == 6) {
                        try{
                            synchronized(TftpClient.lock){
                                TftpClient.lock.wait();
                            }
                        }
                        catch(InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            scanner.close();

        }
        catch(IOException  ex) {
            
        }
    }

    public byte[] process(String msg) {
        // Split the message into words
        String[] words = msg.split("\\s+");

        // Check the first word to determine the request type
        
            String requestType = words[0];

            switch (requestType) {
                case "LOGRQ":
                    if (words.length >= 2) {
                        String username = words[1];
                        TftpClient.lastCommand = Opcode.LOGRQ;
                        return logReq(username);
                    } 
                    break;
                case "DELRQ":
                    if (words.length >= 2) {
                        String filename = words[1];
                        TftpClient.lastCommand = Opcode.DELRQ;
                        return delReq(filename);
                    } 
                    break;
                case "RRQ":
                    if (words.length >= 2) {
                        String filename = words[1];
                        for (int i = 2 ; i<words.length ; i++) {
                            filename = filename+" " +words[i];
                        }
                        TftpClient.lastCommand = Opcode.RRQ;
                        return readReq(filename);
                    } 
                    break;
                case "WRQ":
                    if (words.length >= 2) {
                        String filename = words[1];
                        for (int i = 2 ; i<words.length ; i++) {
                            filename = filename+" " +words[i];
                        }
                        TftpClient.lastCommand = Opcode.WRQ;
                        return writeReq(filename);
                    } 
                    break;
                case "DIRQ":
                    TftpClient.lastCommand = Opcode.DIRQ;
                    return dirq();
                    
                case "DISC":
                     TftpClient.lastCommand = Opcode.DISC;
                    return disc();
                    
                default:
                    return null;
            }
            return null;

    }

    private byte[] logReq(String username) {
        short opShort = 7;
        byte[] opBy = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
        byte[] name = username.getBytes();
        byte[][] arrays = {opBy,name};
        int counter = 0;
        byte[] packet = new byte[opBy.length+name.length+1];
        packet[packet.length-1] = (byte) 0;
        for (byte[] arr : arrays) {
            int currArrSize = arr.length;
            for(int i = 0; i<currArrSize ; i++) {
                packet[counter] = arr[i];
                counter++;
            }
        } 
        return packet;
    }

    private byte[] delReq(String filename) {
        short opShort = 8;
        byte[] opBy = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
        byte[] name = filename.getBytes();
        byte[][] arrays = {opBy,name};
        int counter = 0;
        byte[] packet = new byte[opBy.length+name.length+1];
        packet[packet.length-1] = (byte) 0;
        for (byte[] arr : arrays) {
            int currArrSize = arr.length;
            for(int i = 0; i<currArrSize ; i++) {
                packet[counter] = arr[i];
                counter++;
            }
        } 
        return packet;
    }
    

    private byte[] readReq(String filename) {
        short opShort = 1;
        Path currentDirectory = Paths.get("");
        Path filePath = currentDirectory.resolve(filename);
        TftpClient.lastFileRec = filePath;
        TftpClient.lastFileRecName = filename;
        try {
            if (Files.exists(filePath)) {
                System.out.println("file already exists");
                return null;
            } else {
                Files.createFile(filePath);
               

            }
        } catch (IOException e) {
            
        }
        byte[] opBy = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
        byte[] name = filename.getBytes();
        byte[][] arrays = {opBy,name};
        int counter = 0;
        byte[] packet = new byte[opBy.length+name.length+1];
        packet[packet.length-1] = (byte) 0;
        for (byte[] arr : arrays) {
            int currArrSize = arr.length;
            for(int i = 0; i<currArrSize ; i++) {
                packet[counter] = arr[i];
                counter++;
            }
        } 
        return packet;
    }

    private byte[] writeReq(String filename) {
        Path myPath = Paths.get("").toAbsolutePath();
        File folder = new File(myPath.toString());
        File[] files = folder.listFiles();
        boolean exist = false;
        for (File f : files){
            if (f.getName().equals(filename)){
                exist = true;
                try{
                TftpClient.nextFileToSend = Files.readAllBytes(f.toPath());
                TftpClient.nextFileToSendName = filename;
                }
                catch(IOException e){}
                TftpClient.blockIndexSent = 0;
                break;
            }
        }
        if (!exist) {
            System.out.println("file does not exists");
            return null;
        }
        else{
            short opShort = 2;
            byte[] opBy = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
            byte[] name = filename.getBytes();
            byte[][] arrays = {opBy,name};
            int counter = 0;
            byte[] packet = new byte[opBy.length+name.length+1];
            packet[packet.length-1] = (byte) 0;
            for (byte[] arr : arrays) {
                int currArrSize = arr.length;
                for(int i = 0; i<currArrSize ; i++) {
                    packet[counter] = arr[i];
                    counter++;
                }
            } 
            return packet;
        }
    }

    private byte[] dirq() {
        short opShort = 6;
        byte[] opBy = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
        TftpClient.lastFileRec = null;
        return opBy;
    }

    private byte[] disc() {
        short opShort = 10;
        byte[] opBy = new byte []{( byte ) ( opShort >> 8) , ( byte ) ( opShort & 0xff ) };
        terminateKeyboard = true;
        return opBy;
    }
}
