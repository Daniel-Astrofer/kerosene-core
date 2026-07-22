package source.auth.model.entity;

import jakarta.persistence.*;

@Entity
@Table(schema = "auth", name = "users_device")
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private UserDataBase user;

    public UserDevice() {
    }

    public UserDevice(Long id, UserDataBase user) {
        this.id = id;
        this.user = user;
    }

    public UserDataBase getUser() {
        return user;
    }

    public void setUser(UserDataBase user) {
        this.user = user;
    }

    public Long getId() {
        return id;
    }
}
