package com.student;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;

/**
 * IMKQAS医疗知识问答系统主应用类
 *
 * @author 系统
 * @version 1.0
 */
@SpringBootApplication
@MapperScan("com.student.mapper")
public class ImkqasApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImkqasApplication.class, args);
    }
}