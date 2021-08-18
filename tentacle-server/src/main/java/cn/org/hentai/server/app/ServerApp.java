package cn.org.hentai.server.app;

import cn.org.hentai.server.util.BeanUtils;
import cn.org.hentai.tentacle.util.Configs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

/**
 * Created by matrixy on 2017-12-12.
 */
@ComponentScan(value = {"cn.org.hentai"})
@SpringBootApplication
public class ServerApp
{
    @Autowired
    private Environment env;

    public static void main(String[] args) throws Exception
    {
        ApplicationContext context = SpringApplication.run(ServerApp.class, args);
        BeanUtils.init(context);
        Configs.init("/application.properties");
        RemoteDesktopApp.init();
        // new Thread(new RemoteDesktopServer()).start();
    }

    @Bean
    public DataSource dataSource()
    {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(env.getProperty("spring.datasource.url"));
        return dataSource;
    }

    @Autowired
    private RequestListener requestListener;

    @Bean
    public ServletListenerRegistrationBean<RequestListener> servletListenerRegistrationBean()
    {
        ServletListenerRegistrationBean<RequestListener> servletListenerRegistrationBean = new ServletListenerRegistrationBean<RequestListener>();
        servletListenerRegistrationBean.setListener(requestListener);
        return servletListenerRegistrationBean;
    }
}
