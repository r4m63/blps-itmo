package blps.itmo.platform.grpc;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * ЖИЗНЕННЫЙ ЦИКЛ gRPC СЕРВЕРА В SPRING BOOT.
 *
 * Проблема: Spring Boot по умолчанию не знает про gRPC сервер.
 * Netty (HTTP сервер) стартует на порту 8080, а gRPC сервер нужно
 * поднять на отдельном порту (например, 19081).
 *
 * Решение: SmartLifecycle — специальный интерфейс Spring,
 * который позволяет запустить gRPC сервер ПОСЛЕ того как
 * весь Spring контекст создан.
 *
 * Порядок старта:
 * 1. Spring поднимает все бины
 * 2. SmartLifecycle.start() вызывается автоматически
 * 3. gRPC сервер стартует на указанном порту
 * 4. При остановке приложения вызывается stop()
 *
 * Использование:
 * - В каждом микросервисе, который хочет принимать gRPC вызовы,
 *   этот бин создается автоматически (через @Component)
 * - Все, что нужно — добавить @GrpcService к своим gRPC реализациям
 * - Они найдутся через List<BindableService> services
 */
@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private final List<BindableService> services;  // все @GrpcService бины
    private final int port;                        // gRPC порт (например, 19081)
    private final boolean enabled;                 // включен ли сервер
    private Server server;                         // gRPC сервер
    private boolean running;                       // запущен ли

    /**
     * Конструктор — Spring автоматически передаст:
     * - все бины, реализующие BindableService (ваши gRPC сервисы)
     * - порт из конфига (app.grpc.server.port)
     * - флаг включения (app.grpc.server.enabled)
     */
    public GrpcServerLifecycle(List<BindableService> services,
                               @Value("${app.grpc.server.port:0}") int port,
                               @Value("${app.grpc.server.enabled:true}") boolean enabled) {
        this.services = services;
        this.port = port;
        this.enabled = enabled;
    }

    /**
     * Запускает gRPC сервер.
     * Spring вызывает этот метод автоматически после инициализации всех бинов
     */
    @Override
    public void start() {
        // Проверяем, нужно ли запускаться
        if (!enabled || services.isEmpty() || port <= 0) {
            return;  // не запускаем
        }

        // Создаем gRPC сервер на указанном порту
        ServerBuilder<?> builder = ServerBuilder.forPort(port);

        // Регистрируем все gRPC сервисы (LoginGrpcService, ClaimGrpcService и т.д.)
        services.forEach(builder::addService);

        try {
            // Запускаем сервер
            server = builder.build().start();
            running = true;

            // Логируем успешный старт (обычно добавляют логи сюда)
            // log.info("gRPC server started on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start gRPC server on port " + port, e);
        }
    }

    /**
     * Останавливает gRPC сервер при завершении приложения.
     * graceful shutdown — дает текущим запросам завершиться
     */
    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();  // мягкое завершение (ждет текущие запросы)
            server = null;
        }
        running = false;
    }

    /**
     * Возвращает, запущен ли сервер.
     * Используется Spring для управления жизненным циклом
     */
    @Override
    public boolean isRunning() {
        return running;
    }
}