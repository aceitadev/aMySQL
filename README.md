# 📦 aMySQL

Uma biblioteca de persistência de dados para Java que implementa o padrão **Active Record**, permitindo uma interação fluida, assíncrona e intuitiva com o banco de dados MySQL.

O aMySQL foi projetado para alta performance e facilidade de uso, utilizando o pool de conexões **HikariCP**, um **Query Builder type-safe** e um sistema de **migração automática de schema**, que elimina a necessidade de escrever SQL para a maioria das operações de CRUD e gerenciamento de tabelas.

## 🚀 Funcionalidades

  - **Padrão Active Record**: Manipule registros do banco de dados como objetos Java, chamando métodos como `.save()` e `.delete()` diretamente nas instâncias do modelo.
  - **Pool de Conexões de Alta Performance**: Utiliza **HikariCP** para um gerenciamento de conexões rápido e eficiente.
  - **Operações de Escrita Assíncronas**: Métodos `save()` e `delete()` retornam `CompletableFuture` para não bloquear a thread principal da sua aplicação.
  - **Query Builder Type-Safe**: Construa consultas `SELECT` de forma segura e legível usando Method References (ex: `Player::getUsername`), evitando erros de digitação em nomes de campos.
  - **Gerenciamento Automático de Schema**: As tabelas e colunas são criadas e atualizadas automaticamente na inicialização.
  - **Mapeamento Customizado com `ColumnAdapter`**: Converta tipos complexos que não são nativamente suportados (como objetos específicos de uma API) para tipos que o banco de dados entende.
  - **Suporte a Relacionamentos**: Mapeie relacionamentos entre objetos (ex: One-to-One) de forma declarativa usando a anotação `@Column`.
  - **Anotações Poderosas**: Configure tabelas, colunas, valores únicos, limites de `VARCHAR` e nulidade com anotações intuitivas.

-----

## 📚 Instalação

Para utilizar o aMySQL, adicione o repositório e a dependência ao seu arquivo `pom.xml`.

1.  **Adicionar o Repositório do GitHub Packages**

    ```xml
    <repositories>
        <repository>
            <id>github</id>
            <url>https://maven.pkg.github.com/aceitadev/amysql</url>
        </repository>
    </repositories>
    ```

2.  **Adicionar a Dependência do aMySQL**

    ```xml
    <dependencies>
        <dependency>
            <groupId>aceitadev</groupId>
            <artifactId>amysql</artifactId>
            <version>1.8</version>
        </dependency>
    </dependencies>
    ```

> **Nota**: O driver `mysql-connector-java` e o `HikariCP` já estão incluídos na biblioteca aMySQL. Você não precisa adicioná-los separadamente.

-----

## 🛠️ Como Usar (Básico)

O ciclo de vida básico de um objeto no banco de dados.

### 1\. Inicialização

Configure as credenciais e registre suas classes de modelo.

```java
import mysql.Auth;
import mysql.MySQL;
import java.util.List;
import java.util.logging.Logger;

Auth auth = new Auth("localhost", 3306, "meu_banco", "root", "password");

MySQL.init(auth, List.of(
        Player.class, PlayerProfile.class // Adicione todas as suas classes de modelo aqui
), Logger.getLogger("aMySQL"));
```

### 2\. Criando a Classe de Modelo

Crie sua classe estendendo `ActiveRecord<T>`. **Todos os campos a serem persistidos devem ser anotados com `@Column` ou `@Id`**.

```java
// models/Player.java
import mysql.annotations.*;
import mysql.core.ActiveRecord;
import java.util.UUID;

@Table("players")
public class Player extends ActiveRecord<Player> {

    @Id
    private int id;

    @Column(unique = true) // Garante que cada UUID será único na tabela
    private UUID uuid;

    @Column(name = "player_name", limit = 32) // Coluna 'player_name' com VARCHAR(32)
    private String username;

    public Player() {}

    public Player(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    // Getters
    public int getId() { return id; }
    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public void setUsername(String username) { this.username = username; }
}
```

### 3\. Salvar, Buscar e Deletar

```java
// Salvar (assíncrono)
Player player = new Player(UUID.randomUUID(), "Steve");
player.save().join();

// Buscar (síncrono)
Player foundPlayer = Player.find(Player.class)
                           .where(Player::getUsername, "Steve")
                           .first();

// Atualizar (operação de save é assíncrona)
if (foundPlayer != null) {
    foundPlayer.setUsername("Steve 2.0");
    foundPlayer.save().join();
}

// Deletar (assíncrono)
if (foundPlayer != null) {
    foundPlayer.delete().join();
}
```

-----

## ✨ Mapeamento Avançado

### Mapeando Relacionamentos (Chave Estrangeira)

Para criar um relacionamento, o campo deve ser anotado com `@Column`, assim como qualquer outro campo persistível. O aMySQL irá detectar que o tipo do campo (ex: `Player`) é outra entidade `@Table` e irá criar uma coluna de chave estrangeira (ex: `player_id`) automaticamente, em vez de tentar serializar o objeto inteiro.

```java
// models/PlayerProfile.java
import mysql.annotations.*;
import mysql.core.ActiveRecord;

@Table("player_profiles")
public class PlayerProfile extends ActiveRecord<PlayerProfile> {

    @Id
    private int id;

    // É obrigatório usar @Column para que o campo seja persistido.
    // aMySQL detecta o relacionamento e cria a coluna 'player_id'.
    @Column
    private Player player;

    @Column
    private String bio;

    public PlayerProfile() {}

    // Getters e Setters
    public int getId() { return id; }
    public Player getPlayer() { return player; }
    public String getBio() { return bio; }

    public void setId(int id) { this.id = id; }
    public void setPlayer(Player player) { this.player = player; }
    public void setBio(String bio) { this.bio = bio; }
}
```

**Como usar:**

```java
Player player = Player.find(Player.class).where(Player::getUsername, "Steve").first();

PlayerProfile profile = new PlayerProfile();
profile.setPlayer(player);
profile.setBio("Apenas um jogador comum.");
profile.save().join();

// Ao buscar o perfil, o objeto 'player' virá preenchido
PlayerProfile foundProfile = PlayerProfile.find(PlayerProfile.class)
                                          .where(PlayerProfile::getPlayer, player)
                                          .first();

System.out.println("Bio de: " + foundProfile.getPlayer().getUsername()); // Imprime "Bio de: Steve"
```

### Mapeamento Customizado com ColumnAdapter

O `ColumnAdapter` permite que você defina como serializar e desserializar um campo com um tipo complexo (ex: um objeto `Player` da API do Bukkit).

**1. Crie seu `ColumnAdapter`**

```java
// adapters/PlayerAdapter.java
import mysql.core.ColumnAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

public class PlayerAdapter implements ColumnAdapter<Player, UUID> {

    @Override
    public UUID serialize(Player player) {
        return (player == null) ? null : player.getUniqueId();
    }

    @Override
    public Player deserialize(UUID uuid) {
        return (uuid == null) ? null : Bukkit.getPlayer(uuid);
    }
}
```

**2. Use o Adapter na sua Classe de Modelo**

```java
// models/PlayerIgnored.java
import mysql.annotations.*;
import mysql.core.ActiveRecord;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;

@Table("player_ignored")
public class PlayerIgnored extends ActiveRecord<PlayerIgnored> {

    @Id
    private int id;

    // O campo 'player' será representado por um UUID no banco de dados.
    @Column(adapter = PlayerAdapter.class, unique = true)
    private Player player;

    // O aMySQL serializa listas para JSON (TEXT) automaticamente.
    @Column
    private List<UUID> ignoredPlayerUuids;

    public PlayerIgnored() {}

    // Getters e Setters...
}
```

-----

## ✨ Anotações Disponíveis

  - `@Table("table_name")`: **(Obrigatória)** Define a classe como uma entidade e especifica o nome da tabela.
  - `@Id`: **(Obrigatória)** Marca um campo como a chave primária (`id`, `INT`, `AUTO_INCREMENT`).
  - `@Nullable`: **(Opcional)** Permite que a coluna correspondente seja `NULL` no banco de dados. Por padrão, as colunas são `NOT NULL`.
  - `@Column`: **(Obrigatória para campos persistíveis)** Mapeia um campo para uma coluna.
      - `name = "column_name"`: Define um nome customizado para a coluna. Se omitido, usa o nome do campo em formato snake\_case (ex: `lastSeen` -\> `last_seen`).
      - `unique = true`: Adiciona uma restrição `UNIQUE` à coluna.
      - `limit = 255`: Define o tamanho para colunas `VARCHAR`.
      - `adapter = MyAdapter.class`: Especifica um `ColumnAdapter` para serialização/desserialização customizada.
