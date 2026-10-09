package com.example.skyflowtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkyFlowTrackerServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyFlowTrackerServerApplication.class, args);
    }

}
