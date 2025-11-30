package source.auth.application.service.user.contract;

import source.auth.dto.UserDTO;
import source.auth.model.entity.UserDataBase;

import java.util.List;
import java.util.Optional;

public interface UserServiceContract {

    List<UserDataBase> listar();

    Optional<UserDataBase> buscarPorId(Long id);

    void createUserInDataBase(UserDataBase user);

    void deletar(Long id);

    UserDataBase fromDTO(UserDTO userDTO);

    Optional<UserDataBase> findByUsername(String username);
}
