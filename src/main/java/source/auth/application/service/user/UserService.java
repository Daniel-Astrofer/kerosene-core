package source.auth.application.service.user;

import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.UserDTO;
import source.auth.model.entity.UserDataBase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service("ServiceFromUser")
public class UserService implements UserServiceContract {

    private final UserRepository repository;

    public UserService(UserRepository repository) {

        this.repository = repository;
    }

    public List<UserDataBase> listar() {
        return repository.findAll();
    }

    public Optional<UserDataBase> buscarPorId(Long id) {
        return repository.findById(id);
    }

    public Optional<UserDataBase> findByUsername(String username) {

        return repository.findByUsername(username);
    }

    public void createUserInDataBase(UserDataBase user) {
        user.setUsername(user.getUsername().toLowerCase());
        repository.save(user);
    }

    public void deletar(Long id) {
        repository.deleteById(id);
    }

    public UserDataBase fromDTO(UserDTO userDTO) {
        UserDataBase user = new UserDataBase();
        user.setPassphrase(userDTO.getPassphrase());
        user.setUsername(userDTO.getUsername());
        user.setTOTPSecret(userDTO.getTotpSecret());


        return user;
    }


}
