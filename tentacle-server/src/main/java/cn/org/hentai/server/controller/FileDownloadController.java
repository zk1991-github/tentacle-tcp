package cn.org.hentai.server.controller;

import cn.org.hentai.server.rds.SessionManager;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;

/**
 * Created by matrixy on 2019/1/4.
 */
@RequestMapping("/")
@Controller
public class FileDownloadController
{
    LinkedList<byte[]> blocks = new LinkedList<byte[]>();

    public void receivePartial(byte[] block)
    {
        synchronized (blocks)
        {
            blocks.addLast(block);
        }
    }

    @RequestMapping("/download")
    public void download(@RequestParam String path,
                         @RequestParam String name,
                         @RequestParam Long rdsId,
                         HttpSession session,
                         HttpServletResponse response)
    {
        // 当前会话验证
        if (session.getAttribute("isLogin") == null || ((Boolean)session.getAttribute("isLogin") != true))
        {
            PrintWriter out = null;
            try
            {
                out = response.getWriter();
            }
            catch(Exception e) { }
            out.println("<h1>Access Denied</h1>");
            out.flush();
            out.close();
            return;
        }

        blocks.clear();

        // 请求文件传送
        // TODO：应该将每一台受控端分开进行控制验证
        SessionManager.getSession(rdsId).downloadFile(path, name, this);

        response.addHeader("Content-Type", "application/octet-stream");
        try
        {
            response.addHeader("Content-Disposition", "attachment;filename*=UTF-8''" + java.net.URLEncoder.encode(name, "UTF-8"));
        }
        catch(UnsupportedEncodingException e) { }
        response.addHeader("Content-Transfer-Encoding", "binary");

        OutputStream writer = null;

        try
        {
            writer = response.getOutputStream();
        }
        catch(Exception e) { }

        while (true)
        {
            byte[] block = null;
            if (blocks.size() == 0)
            {
                // if (done) break;
                sleep(20);
                continue;
            }
            synchronized (blocks)
            {
                block = blocks.removeFirst();
            }
            if (block.length == 0) break;
            try
            {
                writer.write(block);
            }
            catch(Exception e) { throw new RuntimeException(e); }
        }
        try
        {
            writer.flush();
        }
        catch(IOException e) { }
    }

    private void sleep(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch(Exception e) { }
    }
}
