package cn.org.hentai.tentacle.system;

import cn.org.hentai.tentacle.graphic.Screenshot;

import java.awt.*;

/**
 * Created by matrixy on 2018/4/9.
 */
public final class LocalComputer
{
    static Robot robot = null;

    /**
     * 创建整屏截图
     * @return
     */
    public static Screenshot captureScreen()
    {
        return new Screenshot(robot.createScreenCapture(getScreenSize()));
    }

    /**
     * 获取屏幕分辨率
     * @return
     */
    public static Rectangle getScreenSize()
    {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return new Rectangle((int)screenSize.getWidth(), (int)screenSize.getHeight());
    }

    public static void init()
    {
        try
        {
            robot = new Robot();
        }
        catch(AWTException ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
