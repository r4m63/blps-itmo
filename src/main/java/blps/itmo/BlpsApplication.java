package blps.itmo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Penalty Claim API", version = "v1", description = "Сервис заявок на компенсацию штрафов"))
public class BlpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlpsApplication.class, args);
    }

}
