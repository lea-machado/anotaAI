package br.com.anotaai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AnotaAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnotaAiApplication.class, args);
    }
}