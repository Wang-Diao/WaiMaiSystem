package com.itheima.reggie.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@Data
public class MailConfig {
    private String to;
    private final String from = "810414696@qq.com";
    private String subject = "验证码";
    private String content;
    private final List<String> addressList = new ArrayList<>();
}
