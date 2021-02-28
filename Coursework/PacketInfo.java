import java.net.DatagramPacket;

public class PacketInfo{

    private DatagramPacket packet;
    private boolean ack;
    private int packetNo;
    private double timeOfTransmission;

    public PacketInfo(DatagramPacket packet, int packetNo, double timeOfTransmission){
        this.packet = packet;
        ack = false;
        this.packetNo = packetNo;
        this.timeOfTransmission = 0;
    }

    public DatagramPacket getPacket(){
        return packet;
    }

    public boolean getAck(){
        return ack;
    }

    public int getPacketNo(){
        return packetNo;
    }

    public double getTimeOfTransmission(){
        return timeOfTransmission;
    }

    public void setAck(boolean ack){
        this.ack = ack;
    }

    public void setTimeOfTransmission(double timeOfTransmission){
        this.timeOfTransmission = timeOfTransmission;
    }

}