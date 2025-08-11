package mysql.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Auth {
    private String host;
    private int port;
    private String database;
    private String user;
    private String password;
}