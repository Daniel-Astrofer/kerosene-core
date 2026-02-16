# 🔧 Setup & Configuration Guide

## ⚠️ Pré-requisitos

### 1. Java Development Kit (JDK)
```
Versão recomendada: Java 17+ (ou a versão que está usando)
JAVA_HOME: Deve apontar para a pasta raiz do JDK, NÃO para \bin
```

### Windows - Configurar JAVA_HOME
```powershell
# Verificar versão Java instalada
java -version

# Encontrar o caminho do JDK (exemplo)
# C:\Program Files\Java\jdk-21
# ou
# C:\Program Files\Java\openjdk-21

# Configurar variável de ambiente (PowerShell como Admin)
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-21", "User")

# Verificar se foi configurado
echo $env:JAVA_HOME

# Se não aparecer, reinicie o PowerShell
```

### Configurar PATH (se necessário)
```powershell
# Adicionar Java ao PATH
$javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
$path = [Environment]::GetEnvironmentVariable("PATH", "User")

if ($path -notlike "*$javaHome\bin*") {
    $newPath = "$path;$javaHome\bin"
    [Environment]::SetEnvironmentVariable("PATH", $newPath, "User")
    Write-Host "PATH atualizado. Reinicie o PowerShell."
}
```

---

## 📦 Dependências do Projeto

### Gradle Wrapper (já incluído)
```
✅ ./gradlew (Linux/Mac)
✅ ./gradlew.bat (Windows)

Não precisa instalar Gradle manualmente!
```

### Redis (necessário para runtime)
```bash
# Opção 1: Instalar localmente
# Windows: Baixar em https://github.com/microsoftarchive/redis/releases

# Opção 2: Docker (recomendado)
docker run -d -p 6379:6379 --name redis redis:latest

# Verificar conexão
redis-cli ping
# Saída: PONG
```

### PostgreSQL (para banco de dados)
```
Database: kerosene
User: api_system
Password: blue-sky-black-ocean0
Host: localhost:5432

Já configurado em: src/main/resources/application.properties
```

---

## 🚀 Compilar o Projeto

### Windows PowerShell (como Admin)
```powershell
cd c:\Users\omega\Documents\Kerosene\backend\kerosene

# Compilar sem testes (mais rápido)
.\gradlew.bat build -x test

# Ou compilar com testes
.\gradlew.bat build

# Limpar build anterior
.\gradlew.bat clean
```

### Saída esperada do build bem-sucedido
```
BUILD SUCCESSFUL in X.XXXs
```

---

## 🏃 Rodar a Aplicação

### Opção 1: Gradle (via terminal)
```powershell
.\gradlew.bat bootRun
```

Esperado:
```
✅ Started Application in X.XXXs
Server started on port: 8080
```

### Opção 2: IDE (IntelliJ/VS Code)
1. Abrir projeto
2. Click em "Run" ou "Debug"
3. Selecionar `Application.java`

### Opção 3: Executável JAR
```powershell
# Após compilar
.\gradlew.bat build -x test

# Executar JAR
java -jar build/libs/v0.5.jar
```

---

## ✅ Validar Instalação

### Teste 1: Java
```powershell
java -version
# Saída esperada: java version "xx.x.x"
```

### Teste 2: Gradle
```powershell
.\gradlew.bat --version
# Saída esperada: Gradle X.X.X
```

### Teste 3: Redis
```bash
redis-cli ping
# Saída esperada: PONG
```

### Teste 4: Banco de dados
```powershell
# Tentar conectar ao PostgreSQL
# (Se tiver instalado)
psql -U api_system -d kerosene -c "SELECT 1"
# Saída esperada: (1 row)
```

### Teste 5: Aplicação rodando
```bash
# Após iniciar a app
curl http://localhost:8080/api/payment-links/user/1
# Saída esperada: [] ou lista de payment links
```

---

## 📝 Arquivo build.gradle.kts

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "kerosene"
version = "PRE-ALPHA"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // Database
    implementation("org.postgresql:postgresql:42.7.7")
    runtimeOnly("com.h2database:h2")
    
    // Redis
    implementation("io.lettuce:lettuce-core:6.8.1.RELEASE")
    
    // Outros
    implementation("org.bitcoinj:bitcoinj-core:0.15.10")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

---

## 🐛 Troubleshooting

### ❌ "JAVA_HOME is set to an invalid directory"
```powershell
# Verificar caminho
echo $env:JAVA_HOME

# Corrigir (exemplo para JDK 21)
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-21", "User")

# Reiniciar PowerShell e aplicação
```

### ❌ "gradle: command not found"
```powershell
# Usar ./gradlew.bat (com ponto!)
.\gradlew.bat build

# Não use:
gradle build  ❌
```

### ❌ "Redis connection refused"
```bash
# Verificar se Redis está rodando
redis-cli ping

# Iniciar Redis
redis-server

# Ou Docker
docker start redis
```

### ❌ "PostgreSQL connection failed"
```bash
# Verificar se PostgreSQL está rodando
# Windows: verificar Services

# Testar conexão
psql -U api_system -d kerosene

# Se não funcionar, configurar em application.properties:
spring.datasource.url=jdbc:postgresql://localhost:5432/kerosene
spring.datasource.username=api_system
spring.datasource.password=blue-sky-black-ocean0
```

### ❌ "Build failed: Cannot resolve symbol"
```powershell
# Limpar cache de dependencies
.\gradlew.bat clean

# Rebuild
.\gradlew.bat build
```

---

## 🔧 Configurações Importantes

### application.properties
```properties
# Server
server.port=8080
server.address=0.0.0.0

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/kerosene
spring.datasource.username=api_system
spring.datasource.password=blue-sky-black-ocean0

# Redis
spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379

# Bitcoin
bitcoin.deposit-address=1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP
bitcoin.payment-link-expiration-minutes=60
bitcoin.mock-mode=true
```

---

## 📊 Estrutura do Projeto

```
backend/kerosene/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── source/
│   │   │       ├── Application.java
│   │   │       ├── config/
│   │   │       │   └── RedisConfig.java (NOVO)
│   │   │       ├── auth/
│   │   │       ├── ledger/
│   │   │       ├── wallet/
│   │   │       └── transactions/
│   │   │           ├── controller/
│   │   │           │   └── PaymentLinkController.java (NOVO)
│   │   │           ├── service/
│   │   │           │   └── PaymentLinkService.java (MODIFICADO)
│   │   │           ├── repository/
│   │   │           ├── dto/
│   │   │           └── model/
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/
│   │           └── schema.sql
│   └── test/
│       └── java/
│           └── source/
│               └── transactions/
│                   └── service/
│                       └── PaymentLinkServiceRedisTest.java (NOVO)
├── build.gradle.kts
├── gradlew
├── gradlew.bat
└── build/
    └── (gerado após compilar)
```

---

## 📚 Documentação do Projeto

1. **[SUMMARY.md](SUMMARY.md)** - Resumo executivo
2. **[REDIS_INTEGRATION.md](REDIS_INTEGRATION.md)** - Guia de integração Redis
3. **[REDIS_ARCHITECTURE.md](REDIS_ARCHITECTURE.md)** - Arquitetura de dados
4. **[REDIS_TESTS.md](REDIS_TESTS.md)** - Guia de testes
5. **[SETUP.md](SETUP.md)** - Este arquivo

---

## ✅ Checklist de Setup

- [ ] Java 17+ instalado e verificado
- [ ] JAVA_HOME configurado corretamente
- [ ] Redis rodando (redis-cli ping → PONG)
- [ ] PostgreSQL configurado e acessível
- [ ] Projeto compilado com sucesso (./gradlew.bat build)
- [ ] Aplicação inicia sem erros (./gradlew.bat bootRun)
- [ ] API respondendo (curl http://localhost:8080/api/...)
- [ ] Redis armazenando dados (redis-cli KEYS payment_link:*)

---

## 🎯 Próximos Passos

1. Configurar ambiente (este guia)
2. Compilar projeto: `./gradlew.bat build`
3. Rodar testes: `./gradlew.bat test`
4. Iniciar aplicação: `./gradlew.bat bootRun`
5. Testar endpoints: Ver [REDIS_TESTS.md](REDIS_TESTS.md)

---

## 📞 Suporte

Para mais informações, consulte:
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)
- [Gradle Documentation](https://docs.gradle.org/)

---

*Última atualização: 2024-12-25*
