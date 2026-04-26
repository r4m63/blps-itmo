package blps.itmo.auth.grpc;

import org.springframework.stereotype.Component;

import blps.itmo.auth.service.AuthUserService;
import blps.itmo.platform.events.RemoteUserView;
import blps.itmo.platform.grpc.GrpcErrors;
import blps.itmo.grpc.AuthRpcServiceGrpc;
import blps.itmo.grpc.DeactivateUserRequest;
import blps.itmo.grpc.EmptyRequest;
import blps.itmo.grpc.GetUserRequest;
import blps.itmo.grpc.ListUsersResponse;
import blps.itmo.grpc.LoginRequest;
import blps.itmo.grpc.LoginResponse;
import blps.itmo.grpc.UserDto;
import io.grpc.stub.StreamObserver;

@Component
public class AuthGrpcService extends AuthRpcServiceGrpc.AuthRpcServiceImplBase {

    private final AuthUserService authUserService;

    public AuthGrpcService(AuthUserService authUserService) {
        this.authUserService = authUserService;
    }

    @Override
    public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
        try {
            AuthUserService.LoginResult result = authUserService.login(
                    request.getUserId() == 0 ? null : request.getUserId(),
                    request.getEmail().isBlank() ? null : request.getEmail());
            responseObserver.onNext(LoginResponse.newBuilder()
                    .setToken(result.token())
                    .setUser(toDto(result.user()))
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void listUsers(EmptyRequest request, StreamObserver<ListUsersResponse> responseObserver) {
        try {
            ListUsersResponse.Builder response = ListUsersResponse.newBuilder();
            authUserService.listUsers().stream().map(this::toDto).forEach(response::addUsers);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserDto> responseObserver) {
        try {
            responseObserver.onNext(toDto(authUserService.getUserView(request.getUserId())));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void deactivateUser(DeactivateUserRequest request, StreamObserver<UserDto> responseObserver) {
        try {
            if (!request.getActorRole().isBlank() && !"ADMIN".equals(request.getActorRole())) {
                throw new IllegalArgumentException("ADMIN role is required");
            }
            responseObserver.onNext(toDto(authUserService.deactivateUser(request.getUserId(), request.getReason())));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    private UserDto toDto(RemoteUserView user) {
        return UserDto.newBuilder()
                .setId(user.getId())
                .setEmail(user.getEmail())
                .setRole(user.getRole())
                .setEnabled(user.isEnabled())
                .setPenaltyCount(user.getPenaltyCount())
                .build();
    }
}
