# AtlasHybrid 0.1.0-alpha — relatório da prova integrada

**Data:** 26 de agosto de 2026
**Ambiente:** Windows 11 amd64, Java 17.0.12, Minecraft 1.19.2, Forge 43.5.0
**Resultado geral:** **PASS**

## Escopo e método

A prova iniciou um servidor Forge dedicado pelo perfil de desenvolvimento oficial do ForgeGradle (`forgeserveruserdev`). O `AtlasHybrid`, o `AtlasHybridTestMod` e o plugin de teste foram executados juntos. O plugin foi carregado do diretório `run/plugins/`.

O test mod criou um `FakePlayer` Forge com canal Netty embutido, publicou a sequência de login/logout, executou os comandos pelo dispatcher do servidor e tentou quebrar um bloco de pedra por `ServerPlayerGameMode.destroyBlock`. Portanto, o teste de cancelamento verificou o resultado da operação do Minecraft, não apenas o estado do objeto de evento.

O perfil `userdev` usa os source sets dos dois mods porque jars reobfuscados são destinados ao ambiente de produção. Os três jars finais foram construídos e reobfuscados separadamente antes da execução integrada final, realizada às 23:35–23:36.

## Critérios

| Critério | Resultado | Evidência |
|---|---:|---|
| Forge 1.19.2 / 43.x inicializando | **PASS** | Linhas 1, 22 e 23: alvo Forge server, MC 1.19.2, Forge 43.5.0 inicializado. |
| AtlasHybrid runtime como mod Forge | **PASS** | Linha 24: runtime construído durante o carregamento de mods. |
| `AtlasHybridTestMod` carregando | **PASS** | Linha 11: carregamento confirmado. |
| `AtlasHybridTestPlugin` descoberto em `plugins/` | **PASS** | Linhas 63–68: `onLoad`, descoberta, `onEnable` e total de um plugin. |
| `onLoad`, `onEnable` e `onDisable` | **PASS** | Linhas 63, 66 e 94; uma ocorrência de cada callback. |
| `/atlas` | **PASS** | Linha 71: `AtlasHybrid is running.`; linha 77: código de resultado `1`. |
| `/atlas info` | **PASS** | Linhas 72–76: versões e contagens; linha 77: código de resultado `1`. |
| `PlayerJoinEvent` | **PASS** | Linha 79; uma ocorrência. |
| `PlayerQuitEvent` | **PASS** | Linha 89; uma ocorrência. |
| `BlockBreakEvent` | **PASS** | Linha 86; uma ocorrência. |
| Cancelamento real de quebra de bloco | **PASS** | Linhas 87–88: cancelado pela configuração; `destroyed=false` e `blockStillPresent=true`. |
| Scheduler `runTask` | **PASS** | Linha 69: tarefa imediata executada uma vez. |
| Scheduler `runTaskLater` | **PASS** | Linha 92: tarefa atrasada executada uma vez. |
| Diagnóstico de API não implementada | **PASS** | Linhas 80–85: diagnóstico estruturado para `Player#getDisplayName`, módulo `bukkit-player`, estado `NOT_IMPLEMENTED`. |
| Ausência de eventos/lifecycle duplicados | **PASS** | Contagem automática: lifecycle, join, quit, block break, scheduler e diagnóstico tiveram exatamente uma ocorrência cada. |
| Shutdown limpo | **PASS** | Linhas 94–104: plugin desabilitado, jogadores e mundos salvos, todos os chunks/dimensões salvos. A tarefa Gradle terminou com `BUILD SUCCESSFUL`. |
| Banner e cores de console | **PASS** | Banner original nas linhas 12–21; 182 sequências ANSI na saída do console e zero no arquivo de log. |

## Contagens e integridade

- `onLoad=1`, `onEnable=1`, `onDisable=1`
- `PlayerJoinEvent=1`, `PlayerQuitEvent=1`, `BlockBreakEvent=1`
- `runTask=1`, `runTaskLater=1`, diagnóstico incompatível `=1`
- cancelamento comprovado `=1`
- ocorrências de nível `ERROR` ou `FATAL` no log final: `0`
- testes unitários: `9`, falhas: `0`, erros: `0`, ignorados: `0`
- build final: `BUILD SUCCESSFUL`

Os avisos iniciais sobre jars internos de linguagem sem `mods.toml` são emitidos pelo ambiente ForgeGradle userdev e não indicam falha de mod ou do AtlasHybrid.

## Artefatos finais

| Artefato | SHA-256 |
|---|---|
| `platform-forge-1.19.2/build/libs/atlashybrid-1.19.2-0.1.0-alpha.jar` | `461FCDB5D68BC6B64F4AE724726777A7F92EDF300FDF7B8EB1731B732A12A275` |
| `test-plugin/build/libs/AtlasHybridTestPlugin-0.1.0-alpha.jar` | `51CE3183ABD38AFF67EBD182708D1809D66F3AE2E5620688492E83C154D75FF7` |
| `test-mod/build/libs/atlashybrid-test-mod-1.19.2-0.1.0-alpha.jar` | `09565580D1F555DB706BD4A09D852797A23442E50FA38ABF01A156C18818E42B` |

## Correções realizadas durante a prova

As correções ficaram dentro da arquitetura aprovada:

- inclusão dos módulos internos no source set do mod no perfil ForgeGradle userdev;
- proteção mais precisa dos namespaces internos no classloader de plugins;
- execução do test mod pelo source set userdev, mantendo o jar final reobfuscado;
- canal Netty embutido no `FakePlayer` para satisfazer listeners oficiais do Forge;
- encaminhamento dos logs JUL de plugins para o Log4j do servidor;
- desligamento normal no perfil de execução, sem `System.exit` forçado pelo Gradle.

Não foram incorporados CraftBukkit/Paper, remapeamento NMS, Mixins nem patches amplos no Minecraft. A licença do projeto foi confirmada como GPL-3.0-only. Nenhum remote Git, release ou publicação foi criado.

## Evidência preservada

Os logs integrais permanecem locais e ignorados pelo Git para evitar publicar dados de ambiente. A validação final usou `clean test proofArtifacts` e `runServer`, ambas encerradas com código `0` e `BUILD SUCCESSFUL`.
