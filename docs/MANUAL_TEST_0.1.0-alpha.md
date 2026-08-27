# AtlasHybrid 0.1.0-alpha — teste manual real

Este modo executa um servidor contínuo em `run-manual/`. Ele carrega o AtlasHybrid e o `AtlasHybridTestPlugin`, não carrega o `AtlasHybridTestMod` de prova automática e permanece ativo até o comando `stop`.

O ambiente `run/` e a task `runServer` continuam reservados à prova integrada automatizada.

## Pré-requisitos

- JDK 17
- porta local `25565` disponível
- cliente Minecraft 1.19.2 com Forge 43.5.0 ou outro Forge 43.x compatível

Não instale no cliente:

- AtlasHybrid runtime
- AtlasHybridTestPlugin
- AtlasHybridTestMod

Conecte-se inicialmente a `localhost:25565`.

## Preparação e EULA

Na raiz do projeto, execute:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat :platform-forge-1.19.2:runManualServer --console=plain
```

Alternativamente:

```bat
run-manual-server.bat
```

O BAT verifica `run-manual/eula.txt` antes de iniciar. Se o arquivo não existir ou não contiver `eula=true`, ele mostra o link e o caminho da EULA. Digitar exatamente `true` no prompt registra o aceite explícito; pressionar Enter cancela sem iniciar o servidor.

Ao usar diretamente a task Gradle, a EULA não é aceita pela task. Na primeira execução, o Minecraft cria `run-manual/eula.txt` com `eula=false` e encerra normalmente. Leia o documento indicado no próprio arquivo e, somente se concordar, altere manualmente:

```properties
eula=true
```

Depois execute o mesmo comando novamente. O plugin é construído e copiado automaticamente para `run-manual/plugins/`. O `server.properties` inicial mantém `online-mode=true`, porta `25565`, whitelist desativada, limite de 5 jogadores e MOTD `AtlasHybrid Manual Test`. Um `server.properties` já existente não é sobrescrito.

## Testes funcionais

No cliente, abra o chat e execute:

```text
/atlas
/atlas info
```

Para testar a quebra normal, confirme em `run-manual/plugins/AtlasHybridTestPlugin/config.yml`:

```yaml
cancel-block-break: false
```

Para testar o cancelamento real, pare o servidor, altere a chave para `true`, reinicie e tente quebrar um bloco:

```yaml
cancel-block-break: true
```

O bloco deve permanecer no mundo. A configuração também contém `scheduler-delay-ticks`, cujo padrão é `20`. As tarefas imediata e atrasada são executadas após `onEnable` e registradas no log.

Use `stop` no console para validar `onDisable` e o salvamento limpo. Consulte `run-manual/logs/latest.log`. Sem FakePlayer, as mensagens esperadas incluem:

- `[AtlasHybrid] Runtime mod constructed`
- `Loaded plugin AtlasHybridTestPlugin`
- `[AtlasHybridTestPlugin] onLoad`
- `[AtlasHybridTestPlugin] onEnable`
- `PlayerJoinEvent`
- `PlayerQuitEvent`
- `BlockBreakEvent`
- `BlockBreakEvent cancelled by config` quando habilitado
- `immediate scheduler task executed`
- `delayed scheduler task executed`
- `[AtlasHybridTestPlugin] onDisable`

## Checklist

- [x] servidor inicia
- [x] AtlasHybrid carrega
- [x] plugin carrega
- [x] cliente Forge limpo conecta
- [x] permanece conectado por 5+ minutos
- [x] conexão não apresenta handshake mod mismatch
- [x] cliente não sofre kick após concluir a conexão
- [x] jogador aparece no servidor
- [ ] `/atlas` funciona
- [ ] `/atlas info` funciona
- [x] `PlayerJoinEvent` ocorre uma vez por entrada
- [x] `PlayerQuitEvent` ocorre uma vez por saída observada
- [x] `BlockBreakEvent` ocorre uma vez por tentativa
- [x] cancelamento impede quebra real
- [x] scheduler funciona
- [x] `stop` chama `onDisable` uma vez
- [x] shutdown limpo
- [x] restart do servidor funciona
- [x] segunda conexão funciona após restart
- [x] zero `ERROR`/`FATAL` nas execuções validadas

## Evidência observada em 26 de agosto de 2026

Os logs locais, que permanecem ignorados pelo Git, registraram:

- `Vanilla acceptance test: ACCEPTED` e `Handshake complete!` para um cliente
  Forge 1.19.2 sem os componentes AtlasHybrid instalados;
- uma sessão conectada por mais de sete minutos, encerrada pelo shutdown do
  servidor, sem kick após o handshake;
- `PlayerJoinEvent` e, em uma conexão posterior, `PlayerQuitEvent`;
- restart seguido por duas conexões aceitas;
- tarefas imediata e atrasada do scheduler;
- cancelamento manual, incluindo:

```text
[AtlasHybridTestPlugin] BlockBreakEvent: minecraft:stone
[AtlasHybridTestPlugin] BlockBreakEvent cancelled by config
```

- `onDisable` uma vez e `All dimensions are saved` no shutdown.

Os comandos `/atlas` e `/atlas info` passaram na prova integrada automatizada,
mas permanecem desmarcados neste checklist manual porque a resposta exibida no
cliente não é registrada pelo log do servidor.

## Encerramento

Digite no console do servidor:

```text
stop
```

Não feche à força durante a validação de shutdown.
