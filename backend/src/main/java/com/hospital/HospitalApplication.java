package com.hospital;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 医疗后台管理系统启动类。
 *
 * <p>分层约定：controller → service → mapper，禁止跨层直连、禁止跨模块操作对方 Mapper。</p>
 */
@EnableAsync
@MapperScan("com.hospital.**.mapper")
@ConfigurationPropertiesScan("com.hospital")
@SpringBootApplication
public class HospitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalApplication.class, args);
    }
}
