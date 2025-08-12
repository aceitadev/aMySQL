# 📦 aMySQL

Uma biblioteca de persistência de dados para Java que implementa o padrão **Active Record**, permitindo uma interação fluida, assíncrona e intuitiva com o banco de dados MySQL.

O aMySQL foi projetado para alta performance e facilidade de uso, utilizando o pool de conexões **HikariCP**, um **Query Builder type-safe** e um sistema de **migração automática de schema**, que elimina a necessidade de escrever SQL para a maioria das operações de CRUD e gerenciamento de tabelas.

## 🚀 Funcionalidades

  - **Padrão Active Record**: Manipule registros do banco de dados como objetos Java, chamando métodos como `.save()` e `.delete()` diretamente nas instâncias do modelo.
  - **Pool de Conexões de Alta Performance**: Utiliza **HikariCP** para um gerenciamento de conexões rápido e eficiente.
  - **Operações de Escrita Assíncronas**: Métodos `save()` e `delete()` retornam `CompletableFuture` para não bloquear a thread principal da sua aplicação.
  - **Query Builder Type-Safe**: Construa consultas `SELECT` de forma segura e legível usando Method References (ex: `Player::getUsername`), evitando erros de digitação em nomes de campos.
  - **Gerenciamento Automático de Schema**: As tabelas e colunas são criadas e atualizadas automaticamente na inicialização com base nas suas classes de modelo, simplificando o desenvolvimento e o deploy.
  - **Suporte a Tipos Complexos e Anotações**: Persista tipos como `UUID`, `Instant`, `List` e `Enum` de forma transparente e configure o mapeamento com anotações intuitivas como `@Table`, `@Column` e `@Id`.

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
            <version>1.5</version>
        </dependency>
    </dependencies>
    ```

> **Nota**: O driver `mysql-connector-java` e o `HikariCP` já estão incluídos na biblioteca aMySQL. Você não precisa adicioná-los separadamente.

-----

## 🛠️ Como Usar

### 1\. Configuração e Inicialização

Primeiro, configure as credenciais de acesso e inicialize o aMySQL, registrando todas as suas classes de modelo. Isso geralmente é feito no método de inicialização da sua aplicação.

```java
import mysql.Auth;
import mysql.MySQL;
import java.util.List;
import java.util.logging.Logger;

// Obtenha as credenciais de seu arquivo de configuração
Auth auth = new Auth(
        "localhost",      // Host
        3306,             // Porta
        "meu_banco",      // Database
        "root",           // Usuário
        "password"        // Senha
);

// Inicialize o MySQL e registre as classes que representam suas tabelas
MySQL.init(auth, List.of(
        Player.class,
        PlayerStats.class // Adicione todas as suas classes de modelo aqui
), Logger.getLogger("aMySQL"));
```

### 2\. Criando a Classe de Modelo

Sua classe de modelo deve estender `ActiveRecord<T>`. **Todos os campos que você deseja persistir devem ser anotados com `@Column` ou `@Id`**. A classe também precisa de um construtor público sem argumentos.

```java
// models/Player.java
import mysql.annotations.*;
import mysql.core.ActiveRecord;
import java.time.Instant;
import java.util.UUID;

@Table("players") // Define o nome da tabela como 'players'
public class Player extends ActiveRecord<Player> {

    @Id // Marca este campo como a chave primária auto-incrementável (coluna 'id')
    private int id;

    @Column("player_uuid") // Mapeia o campo para a coluna 'player_uuid'
    private UUID uuid;

    @Column("player_name") // Mapeia para a coluna 'player_name'
    private String username;

    @Column("player_level") // Mapeia para a coluna 'player_level'
    private int level;

    @Column("last_seen")
    @CanBeNull // Permite que esta coluna seja nula no banco de dados
    private Instant lastSeen;

    // Construtor sem argumentos (obrigatório pelo aMySQL)
    public Player() {}

    // Construtor para facilitar a criação de novos objetos
    public Player(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
        this.level = 1;
        this.lastSeen = Instant.now();
    }

    // --- Getters ---
    public int getId() { return id; }
    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public int getLevel() { return level; }
    public Instant getLastSeen() { return lastSeen; }

    // --- Setters ---
    public void setId(int id) { this.id = id; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public void setUsername(String username) { this.username = username; }
    public void setLevel(int level) { this.level = level; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
}
```

> **Observação sobre o Lombok**: Para um código mais limpo e conciso, você pode adicionar o [Project Lombok](https://projectlombok.org/) ao seu projeto e substituir todos os métodos getters, setters e o construtor sem argumentos pelas anotações `@Getter`, `@Setter` e `@NoArgsConstructor` na classe.

### 3\. Salvar um Novo Registro (Create)

Para criar um novo registro, instancie seu objeto e chame o método `.save()`. A operação é assíncrona.

```java
Player newPlayer = new Player(UUID.randomUUID(), "Herobrine");
newPlayer.save().join(); // .join() espera a operação assíncrona ser concluída
```

### 4\. Buscar Registros (Read)

As operações de busca são síncronas e usam um Query Builder fluente e type-safe.

**Buscar um único registro:**

```java
UUID targetUuid = UUID.fromString("...");
Player player = Player.find(Player.class)
                      .where(Player::getUuid, targetUuid) // Type-safe com Method Reference
                      .first(); // Pega o primeiro resultado ou null
if (player != null) {
    System.out.println("Jogador encontrado: " + player.getUsername());
}
```

**Buscar uma lista de registros:**

```java
// Busca todos os jogadores com nível maior que 10, ordenados pelo nome
List<Player> highLevelPlayers = Player.find(Player.class)
                                      .where(Player::getLevel, ">", 10)
                                      .orderBy(Player::getUsername) // Ordem ASC por padrão
                                      .get(); // Retorna uma lista com os resultados

highLevelPlayers.forEach(p -> System.out.println(p.getUsername()));
```

### 5\. Atualizar um Registro (Update)

Modifique as propriedades de um objeto existente e chame `.save()` novamente. A operação de salvar detectará a existência do ID e executará um `UPDATE`.

```java
Player playerToUpdate = Player.find(Player.class).where(Player::getUsername, "Herobrine").first();
if (playerToUpdate != null) {
    playerToUpdate.setLevel(99);
    playerToUpdate.setLastSeen(Instant.now());
    playerToUpdate.save().join(); // A operação de salvar é assíncrona
}
```

### 6\. Excluir um Registro (Delete)

Chame `.delete()` em uma instância de um objeto para removê-lo do banco de dados de forma assíncrona.

```java
Player playerToDelete = Player.find(Player.class).where(Player::getUsername, "Herobrine").first();
if (playerToDelete != null) {
    playerToDelete.delete().join(); // A operação de deletar é assíncrona
}
```

-----

## ✨ Anotações Disponíveis

  - `@Table("table_name")`: **(Obrigatória)** Define a classe como uma entidade persistível e especifica o nome da tabela no banco de dados.
  - `@Id`: **(Obrigatória)** Marca um campo como a chave primária da tabela. O nome da coluna será `id` por padrão e será `AUTO_INCREMENT`.
  - `@Column("column_name")`: **(Obrigatória para campos persistíveis)** Mapeia um campo da classe para uma coluna no banco de dados.
  - `@CanBeNull`: **(Opcional)** Marca um campo como `NULL` no banco de dados. Por padrão, os campos são `NOT NULL`.

-----

## 📝 Exemplo Completo

```java
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ExemploCompleto {

    public static void main(String[] args) {
        // A inicialização (MySQL.init) deve ter sido feita antes.

        UUID playerUuid = UUID.randomUUID();

        // 1. CREATE: Criar e salvar um novo jogador
        System.out.println("1. Criando jogador 'Steve'...");
        Player steve = new Player(playerUuid, "Steve");
        steve.setLevel(5);
        steve.save().join(); // Operação de escrita assíncrona
        System.out.println("   > Jogador 'Steve' salvo com ID: " + steve.getId());

        // 2. READ (Single): Buscar o jogador pelo UUID
        System.out.println("\n2. Buscando jogador pelo UUID...");
        Player foundPlayer = Player.find(Player.class).where(Player::getUuid, playerUuid).first();
        System.out.println("   > Encontrado: " + foundPlayer.getUsername() + " (Nível " + foundPlayer.getLevel() + ")");

        // 3. UPDATE: Atualizar o nível e a data de 'last_seen'
        System.out.println("\n3. Atualizando jogador 'Steve'...");
        foundPlayer.setLevel(8);
        foundPlayer.setLastSeen(Instant.now());
        foundPlayer.save().join(); // Operação de escrita assíncrona
        System.out.println("   > Nível do jogador 'Steve' atualizado para " + foundPlayer.getLevel());

        // 4. READ (List): Buscar jogadores com nível maior que 5
        System.out.println("\n4. Buscando todos os jogadores com nível > 5...");
        new Player(UUID.randomUUID(), "Alex").save().join(); // Adicionar outro jogador para o teste
        List<Player> highLevelPlayers = Player.find(Player.class)
                                              .where(Player::getLevel, ">", 5)
                                              .orderBy(Player::getUsername)
                                              .get();
        System.out.println("   > Jogadores encontrados:");
        highLevelPlayers.forEach(p -> System.out.println("     - " + p.getUsername()));


        // 5. DELETE: Excluir o jogador 'Steve' do banco
        System.out.println("\n5. Excluindo jogador 'Steve'...");
        foundPlayer.delete().join(); // Operação de escrita assíncrona
        System.out.println("   > Jogador excluído.");

        // 6. CONFIRMATION: Tentar buscar o jogador novamente
        System.out.println("\n6. Verificando se o jogador foi excluído...");
        Player deletedPlayer = Player.find(Player.class).where(Player::getUuid, playerUuid).first();
        if (deletedPlayer == null) {
            System.out.println("   > Sucesso! Jogador não encontrado após a exclusão.");
        } else {
            System.out.println("   > Falha! Jogador ainda existe no banco.");
        }
    }
}
```
