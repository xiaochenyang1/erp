package com.tuowei.erp;

import com.tuowei.erp.common.config.AppProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.tuowei.erp.**.mapper")
@ConfigurationPropertiesScan(basePackageClasses = AppProperties.class)
@EnableScheduling
public class ErpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpServerApplication.class, args);
    }
}
