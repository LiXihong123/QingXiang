package com.qingxiang;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.qingxiang.mapper")
@SpringBootApplication
public class QingXiangApplication {

    public static void main(String[] args) {
        SpringApplication.run(QingXiangApplication.class, args);
    }

}
