package source.auth.model.entity;


import jakarta.persistence.*;

@Entity
@Table(schema = "auth", name = "users_device")
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id",
            nullable = false)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private UserDataBase user;

    @Column(name = "ip_address",
            nullable = false)

    private String ipAddress;

    @Column(name = "device_hash",
            nullable = false)

    private String deviceHash;


    public UserDevice() {
    }

    public UserDevice(Long id, UserDataBase user, String ipAddress, String deviceHash) {
        this.id = id;
        this.user = user;
        this.ipAddress = ipAddress;
        this.deviceHash = deviceHash;
    }


    public UserDataBase getUser() {
        return user;
    }

    public void setUser(UserDataBase user) {
        this.user = user;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDeviceHash() {
        return deviceHash;
    }

    public void setDeviceHash(String deviceHash) {
        this.deviceHash = deviceHash;
    }

    public Long getId() {
        return id;
    }
}
