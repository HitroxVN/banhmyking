package com.banhmyking.banhmyking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BanhmykingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BanhmykingApplication.class, args);
    }

}
