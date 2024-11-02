package bgu.spl.net.impl.tftp;

import java.util.ArrayList;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    ArrayList<Byte> bytes;
    int expLen;
    Opcode opcode;

    public TftpEncoderDecoder() {
        bytes = new ArrayList<Byte>();
        expLen = Integer.MAX_VALUE;
        opcode = Opcode.None;
    }

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if(bytes.size() >= expLen && nextByte == (byte) 0) {
            byte[] arr = new byte[bytes.size()];
            for(int i=0;i<bytes.size();i++){
                arr[i]=(byte) bytes.get(i);
            }
            bytes.clear();
            opcode=Opcode.None;
            expLen=Integer.MAX_VALUE;
            return(arr);
        }
        else{
            bytes.add(nextByte);
            if(bytes.size() == 2) {
                short b_short = ( short ) ((( short ) bytes.get(0)) << 8 | ( short ) ( bytes.get(1))& 0x00ff );
                opcode=Opcode.fromU16(b_short);
                if(opcode==Opcode.RRQ|opcode==Opcode.WRQ|opcode==Opcode.DIRQ|opcode==Opcode.LOGRQ|opcode==Opcode.DELRQ|opcode==Opcode.DISC)
                    expLen=2;
                else if(opcode==Opcode.BCAST)
                    expLen=3;
                else if(opcode==Opcode.ACK|opcode==Opcode.ERROR)
                    expLen=4;
                else if(opcode==Opcode.DATA)
                    expLen=6;
            }
            if(opcode==Opcode.DATA && bytes.size()==4){
                short size = (short) (((short) bytes.get(2)) << 8 | (short) (bytes.get(3)) & 0x00ff);
               // short size = (short) ((( short ) bytes.get(2)) << 8 | ( short ) ( bytes.get(3)) );
                if (size < 0) {
                    size = (short)(512 + size);
                }
                expLen=6+size;
                //System.out.println("explen is now: " + expLen);

            }   
            if(bytes.size()==expLen && (opcode==Opcode.DIRQ | opcode==Opcode.DISC | opcode==Opcode.ACK | opcode==Opcode.DATA)){
                byte[] arr = new byte[bytes.size()];
                for(int i=0;i<bytes.size();i++){
                    arr[i]=(byte) bytes.get(i);
                }
                bytes.clear();
                opcode=Opcode.None;
                expLen=Integer.MAX_VALUE;
                return(arr); 
            }
            if(opcode==Opcode.None && bytes.size()>=2 && nextByte== (byte) 0){
                byte[] arr = new byte[bytes.size()];
                for(int i=0;i<bytes.size();i++){
                    arr[i]=(byte) bytes.get(i);
                }
                bytes.clear();
                opcode=Opcode.None;
                expLen=Integer.MAX_VALUE;
                return(arr); 
            }

        }
        return null;   
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    public enum Opcode {
        RRQ(1),
        WRQ(2),
        DATA(3),
        ACK(4),
        ERROR(5),
        DIRQ(6),
        LOGRQ(7),
        DELRQ(8),
        BCAST(9),
        DISC(10),
        None(0);

       private final int value;

        Opcode(int value) {
            this.value = value;
        }
        public int getValue(){
            return value;
        }
    
        public static Opcode fromU16(int val) {
            switch (val) {
                case 1:
                    return RRQ;
                case 2:
                    return WRQ;
                case 3:
                    return DATA;
                case 4:
                    return ACK;
                case 5:
                    return ERROR;
                case 6:
                    return DIRQ;
                case 7:
                    return LOGRQ;
                case 8:
                    return DELRQ;
                case 9:
                    return BCAST;
                case 10:
                    return DISC;
                default:
                    return None;
            }
        }
    }
}

