package cn.org.hentai.server.rds.controller;

import cn.org.hentai.server.rds.BaseMessageController;
import cn.org.hentai.server.rds.TentacleDesktopSession;
import cn.org.hentai.tentacle.protocol.Message;
import cn.org.hentai.tentacle.util.ByteUtils;
import cn.org.hentai.tentacle.util.Log;

/**
 * Created by matrixy on 2019/1/13.
 */
public class UploadFileResponseController extends BaseMessageController
{
    @Override
    public boolean authenticateRequired()
    {
        return true;
    }

    @Override
    public Message service(TentacleDesktopSession session, Message msg) throws Exception
    {
        byte result = msg.getBodyBytes()[0];
        if (result != 0x00) throw new RuntimeException(String.format("upload file failed: %x", result));
        return null;
    }
}
