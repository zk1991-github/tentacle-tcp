package cn.org.hentai.server.rds;

import cn.org.hentai.server.controller.FileDownloadController;
import cn.org.hentai.server.rds.coder.TentacleMessageDecoder;
import cn.org.hentai.server.util.ByteHolder;
import cn.org.hentai.server.wss.TentacleDesktopWSS;
import cn.org.hentai.tentacle.protocol.Command;
import cn.org.hentai.tentacle.protocol.Message;
import cn.org.hentai.tentacle.protocol.Packet;
import cn.org.hentai.tentacle.util.ByteUtils;
import cn.org.hentai.tentacle.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * Created by matrixy on 2019/1/8.
 */
public class TentacleDesktopSession extends Thread
{
    private Socket connection = null;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;

    private Client clientInfo = null;

    public TentacleDesktopSession(Socket connection)
    {
        this.connection = connection;
    }

    public void run()
    {
        try
        {
            inputStream = connection.getInputStream();
            outputStream = connection.getOutputStream();

            ByteHolder buffer = new ByteHolder(1024 * 1024 * 10);
            byte[] block = new byte[512];

            long lastActiveTime = System.currentTimeMillis();
            while (!this.isClosed())
            {
                int readableBytes = inputStream.available();
                if (readableBytes > 0)
                {
                    lastActiveTime = System.currentTimeMillis();
                    for (int i = 0, l = (int)Math.ceil(readableBytes / 512f); i < l; i++)
                    {
                        int len = inputStream.read(block, 0, i == l - 1 ? 512 : readableBytes % 512);
                        if (len > 0) buffer.write(block, 0, len);
                    }

                    while (true)
                    {
                        Message msg = TentacleMessageDecoder.read(buffer);
                        if (null == msg) break;

                        handle(msg);
                    }
                    continue;
                }

                long idleTime = System.currentTimeMillis() - lastActiveTime;
                if (idleTime > 5000)
                {
                    Log.debug(String.format("Client Timeout: %s", this.getRemoteAddress().toString()));
                    break;
                }
                Thread.sleep(10);
            }
        }
        catch(Exception e)
        {
            Log.error(e);
        }
        finally
        {
            this.close();
            SessionManager.removeSession(this);
        }
    }

    private boolean isClosed()
    {
        return this.isInterrupted() || connection.isClosed() || connection.isConnected() == false;
    }

    private final void handle(Message msg)
    {
        BaseMessageController controller = TentacleDesktopSessionHandler.getController(msg.getCommand());
        if (null == controller)
        {
            throw new RuntimeException(String.format("unknown command: %x", msg.getCommand()));
        }

        try
        {
            Message resp = controller.service(this, msg);
            if (resp != null) this.send(resp);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }

        if (controller.shouldDisconnectAfterConverse())
        {
            this.interrupt();
        }
    }

    // ???websocket?????????????????????
    private TentacleDesktopWSS websocketContext = null;

    public TentacleDesktopWSS getWebsocketContext()
    {
        return websocketContext;
    }

    /**
     * ???websocket??????????????????
     * @param websocketSession
     */
    public void bind(TentacleDesktopWSS websocketSession)
    {
        if (this.websocketContext != null) throw new RuntimeException("????????????????????????????????????????????????");
        this.websocketContext = websocketSession;
        Client info = this.getClient();
        info.setControlling(true);

        // ????????????????????????????????????
        // body??????????????????????????????????????????????????????????????????
        // 0x01 : ????????????
        // 0x00 : ??????
        // 0x03 : ????????????
        Message msg = new Message().withCommand(Command.CONTROL_REQUEST).withBody(new byte[] { 0x01, 0x00, 0x03 });
        this.send(msg);
    }

    /**
     * ?????????websocket????????????????????????????????????????????????????????????
     */
    public void unbind()
    {
        try
        {
            Message msg = new Message().withCommand(Command.CLOSE_REQUEST).withBody("CLOSE".getBytes());
            this.send(msg);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        try
        {
            getClient().setControlling(false);
        }
        catch(Exception ex) { }
        this.websocketContext = null;
    }

    // ??????????????????????????????
    public void getClipboardData()
    {
        this.send(new Message().withCommand(Command.GET_CLIPBOARD).withBody("GET".getBytes()));
    }

    // ??????????????????????????????
    public void setClipboardData(String text)
    {
        byte[] data = null;
        try
        {
            data = text.getBytes("UTF-8");
        }
        catch(UnsupportedEncodingException ex) { }
        Packet p = Packet.create(4 + data.length).addInt(data.length).addBytes(data);
        this.send(new Message().withCommand(Command.SET_CLIPBOARD).withBody(p));
    }

    // ?????????????????????????????????
    public void listFiles(String filePath)
    {
        byte[] data = null;

        try
        {
            data = filePath.getBytes("UTF-8");
        }
        catch(UnsupportedEncodingException e) { }

        Packet p = Packet.create(4 + data.length).addInt(data.length).addBytes(data);
        this.send(new Message().withCommand(Command.LIST_FILES).withBody(p));
    }

    // ?????????????????????????????????????????????????????????????????????
    private FileDownloadController fileDownloadController = null;

    public void downloadFile(String path, String name, FileDownloadController controller)
    {
        if (this.fileDownloadController != null) throw new RuntimeException("????????????????????????????????????");
        byte[] bPath = null, bName = null;
        try
        {
            bPath = path.getBytes("UTF-8");
            bName = name.getBytes("UTF-8");
        }
        catch(UnsupportedEncodingException ex) { }
        Packet p = Packet.create(8 + bPath.length + bName.length)
                .addInt(bPath.length)
                .addBytes(bPath)
                .addInt(bName.length)
                .addBytes(bName);
        this.send(new Message().withCommand(Command.DOWNLOAD_FILE).withBody(p));
        this.fileDownloadController = controller;
    }

    // ???????????????????????????40960????????????????????????????????????????????????FileDownloadController??????
    public void sendFileFragment(byte[] block)
    {
        this.fileDownloadController.receivePartial(block);
        // ????????????????????????????????????????????????????????????????????????????????????????????????
        // ??????????????????FileDownloadController???????????????
        if (block.length == 0) this.fileDownloadController = null;
    }

    // ??????HID????????????????????????
    public void sendHIDCommand(byte hidType, byte eventType, byte key, short x, short y, int timestamp)
    {
        Packet p = Packet.create(11).addByte(hidType)
                .addByte(eventType)
                .addByte(key)
                .addShort(x)
                .addShort(y)
                .addInt(timestamp);

        Message msg = new Message()
                .withCommand(Command.HID_COMMAND)
                .withBody(p);
        this.send(msg);
    }

    public SocketAddress getRemoteAddress()
    {
        return this.connection.getRemoteSocketAddress();
    }

    public synchronized void send(Message message)
    {
        try
        {
            byte[] body = message.getBodyBytes();

            outputStream.write("HENTAI".getBytes());
            outputStream.write(message.getCommand());
            outputStream.write(ByteUtils.toBytes(body.length));
            outputStream.write(body);
            outputStream.flush();
        }
        catch(Exception ex)
        {
            if (ex instanceof SocketException || ex instanceof IOException)
            {
                Log.error(ex);
                this.close();
            }
            throw new RuntimeException(ex);
        }
    }

    public void close()
    {
        try { inputStream.close(); } catch(Exception e) { }
        try { outputStream.close(); } catch(Exception e) { }
        try { connection.close(); } catch(Exception e) { }
    }

    public Client getClient()
    {
        return this.clientInfo;
    }

    public void setClient(Client clientInfo)
    {
        this.clientInfo = clientInfo;
    }
}
