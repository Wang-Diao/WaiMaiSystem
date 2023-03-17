package com.itheima.reggie.utils;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;


@Configuration
public class JavaMailUtil {

    @Bean(name = "mailConfig")
    public Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth", "true");
        //启用调试
        properties.setProperty("mail.debug", "true");
        //设置链接超时
        properties.setProperty("mail.smtp.timeout", "1000");
        //设置端口
        properties.setProperty("mail.smtp.port", "465");
        //设置ssl端口
        properties.setProperty("mail.smtp.socketFactory.port", "465");
        properties.setProperty("mail.smtp.socketFactory.fallback", "false");
        properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        return properties;
    }
}