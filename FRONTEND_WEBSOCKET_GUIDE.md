# Guia de Conexão WebSocket para o Frontend (Flutter)

Este guia explica detalhadamente como o frontend em Flutter deve se conectar ao WebSocket do Kerosene Backend, especialmente contornando os problemas comuns onde proxies (como o Ngrok) removem headers HTTP customizados (`Authorization`) durante o handshake inicial (transição do HTTP para WebSocket).

---

## 1. O Problema com Headers em WebSockets

Normalmente, o frontend envia os headers de autenticação assim:
```dart
webSocketConnectHeaders: {
  'Authorization': 'Bearer $token',
},
```
Entretanto, conexões WebSocket iniciam com uma requisição HTTP de tipo **Upgrade**. Muitos proxies reversos, balanceadores de carga (Load Balancers) ou APIs de tunelamento (como o **Ngrok**) são configurados para limpar (strip) headers não padronizados ou de segurança pesada durante este processo de handshake. 

Quando isso acontece, o backend (no `JwtAuthenticationFilter`) não encontra as chaves, assumindo que a conexão é anônima, e encerra o socket imediatamente com erro `UnrrecognizedDevice: invalid session` (Código 1000).

---

## 2. A Solução: Fallback por Query Parameters

Para contornar esse comportamento invasivo da infraestrutura de rede, o backend foi adaptado para ler esses dois valores obrigatórios diretamente da URL (Query Parameters), caso eles desapareçam dos headers.

No Flutter, utilizando o pacote `stomp_dart_client`, precisamos alterar de onde as variáveis são injetadas.

### 2.1 Código de Exemplo Correto em Dart / Flutter

A URI de conexão do STOMP cliente deve ser montada dinamicamente com os parâmetros anexados ao final:

```dart
import 'package:stomp_dart_client/stomp_dart_client.dart';

// ... Dentro da classe/serviço que inicializa o WebSocket ...

Future<void> connectToWebSocket(String jwtToken) async {
  
  // 1. Defina a sua URL base (Ngrok, localhost, ou produção)
  // Certifique-se de usar WSS se for https:// ou WS se for http://
  final String baseUrl = "wss://seu-endereco-ngrok.app"; 
  
  // 2. Anexe explicitamente o token como Query Parameter na ROTA DO WS
  final String wsEndpoint = "$baseUrl/ws/balance/websocket?token=$jwtToken";

  // 3. Configure o cliente STOMP
  final stompClient = StompClient(
    config: StompConfig(
      url: wsEndpoint,
      
      // Opcional: Você AINDA pode/deve enviar nos headers.
      // Se a infraestrutura não remover (ex: testes locais sem Ngrok), 
      // o backend vai ler os headers primariamente e os parâmetros como plano B.
      webSocketConnectHeaders: {
        'Authorization': 'Bearer $jwtToken',
      },
      
      stompConnectHeaders: {
          // Algumas implementações de backend STOMP Broker também pedem
          // a repetição das credenciais no layer STOMP (o CONNECT frame)
         'Authorization': 'Bearer $jwtToken',
      },

      onConnect: (StompFrame frame) {
        print("✅ STOMP Client: Conectado com sucesso!");
        
        // Inscreva-se no tópico de balanço (ajuste para sua rota real de envio se for destino específico)
        // Exemplo: /user/queue/balance ou similar caso tenha implementado filas por usuário.
      },
      
      onWebSocketError: (dynamic error) {
        print("❌ Erro de conexão de rede do WebSocket: $error");
      },
      
      onWebSocketDone: () {
        print("❗ WebSocket Desconectado (Connection closed)");
      },
    ),
  );

  // 4. Inicie o cliente
  stompClient.activate();
}
```

---

## 3. Estrutura do Endpoint para Inscrição (Subscribing)

Uma vez que o `onConnect` disparar, o cliente STOMP está autenticado com sucesso e mantido vivo pelo servidor. A próxima etapa é se inscrever (Subscribe) para ouvir os eventos recebidos.

Verifique no seu `LedgerController` ou `WebSocketConfig` qual é a exata arquitetura de broker de mensagens que você definiu. 

Se o backend envia mensagens assim:
`simpMessagingTemplate.convertAndSendToUser(userId, "/queue/balance", dto);`

A inscrição no frontend deve ser feita desta forma dentro do `onConnect`:

```dart
      onConnect: (StompFrame frame) {
        print("✅ Conectado!");
        
        // Padrão do Spring para @SendToUser é ler de /user/destination
        stompClient.subscribe(
          destination: '/user/queue/balance', // Fila protegida e isolada para este usuário específico
          callback: (StompFrame frame) {
            if (frame.body != null) {
              print("📥 Nova atualização de saldo/transação recebida!");
              // Faça o parse do JSON aqui
              // var data = jsonDecode(frame.body);
              // notifyListeners() caso esteja usando Provider.
            }
          },
        );
      },
```

---

## 4. Checklist para Debugging caso ainda caia

Se o WebSocket fechar imediatamente mesmo injetando tudo na URL (o `onWebSocketDone` for printado logo de cara):

1. **Inspecione o Console do Spring Boot**: O `JwtAuthenticationFilter` agora envia um log rigoroso apontando _exatamente_ porque ele engasgou ("JWT Error parsing token..."). É o seu principal aliado.
2. **Protocolo Incorreto**: Se o Ngrok expõe `https://`, a URL do websocket MUST COMEÇAR com `wss://`. Se começar com `ws://`, o Ngrok bloqueará o upgrade.
3. **Token Expirado**: Se o Token JWT Expirou, você não pode tentar abrir o websocket. Você deve deslogar o usuário ou buscar um _refresh token_ (se implementado) antes de tentar o `.activate()`.
