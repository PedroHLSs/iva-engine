# Configuração

Toda a configuração vem de variável de ambiente ou de propriedade de sistema
(`-Dnome=valor` antes do `-jar`). Os arquivos `application.properties` e
`application-api.properties` são versionados e não têm segredo nem valor
normativo. Os padrões abaixo foram conferidos nesses dois arquivos e no código
que os lê.

No PowerShell, propriedade de sistema vai entre aspas
(`java "-Dauditoria.tolerancia-de-valor=0" -jar ...`), senão ele parte o
argumento no ponto; variável de ambiente se define com `$env:NOME = "valor"`.

## Resumo

| Variável de ambiente | Propriedade | Padrão | Para quê |
|---|---|---|---|
| `AUDITORIA_BANCO_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/auditoria` | endereço do banco |
| `AUDITORIA_BANCO_USUARIO` | `spring.datasource.username` | `auditoria` | usuário do banco |
| `AUDITORIA_BANCO_SENHA` | `spring.datasource.password` | `auditoria` | senha do banco |
| `AUDITORIA_PSEUDONIMIZACAO_SAL` | `auditoria.pseudonimizacao.sal` | arquivo local, ou sorteado | sal da pseudonimização |
| `AUDITORIA_TOLERANCIA_DE_VALOR` | `auditoria.tolerancia-de-valor` | vazio: vale o padrão | tolerância da R05 |
| — | `auditoria.tolerancia-de-valor-padrao` | `0.01` | padrão declarado da tolerância |
| `AUDITORIA_API_PORTA` | `server.port` | `8080` | porta da API (perfil `api`) |
| `AUDITORIA_SESSAO_DURACAO` | `server.servlet.session.timeout` | `30m` | duração da sessão (perfil `api`) |
| `AUDITORIA_API_EXPOR_CHAVE_DE_ACESSO` | `auditoria.api.expor-chave-de-acesso` | `false` | chave de acesso em texto claro na API |
| `AUDITORIA_API_EXPOR_JUSTIFICATIVA` | `auditoria.api.expor-justificativa` | `false` | justificativa da tratativa na API |
| `AUDITORIA_API_EXPOR_DESCRICAO_DO_PRODUTO` | `auditoria.api.expor-descricao-do-produto` | `false` | `xProd` na API |
| — | `auditoria.exportacao.expor-justificativa` | `false` | justificativa da tratativa na planilha |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | nenhum | `api` liga o servidor web |

Os limites de envio de arquivo estão em [Envio de arquivos](#envio-de-arquivos).

## Banco de dados

PostgreSQL. O esquema é criado pelas migrations do Flyway, em
`src/main/resources/db/migration`, que rodam sozinhas na subida. **Todas as
tabelas nascem vazias**, inclusive as do catálogo normativo e a de usuários:
nenhuma migration insere alíquota, código, vigência, usuário ou senha. O
Hibernate roda com `ddl-auto=none`: quem cria e altera tabela é o Flyway, e só
ele.

Sem as variáveis, usuário e senha são `auditoria`. Isso serve a um banco local de
desenvolvimento; **numa instalação que processa documento fiscal real, defina
`AUDITORIA_BANCO_USUARIO` e `AUDITORIA_BANCO_SENHA`**.

```bash
export AUDITORIA_BANCO_URL="jdbc:postgresql://localhost:5432/auditoria"
export AUDITORIA_BANCO_USUARIO="<usuário>"
export AUDITORIA_BANCO_SENHA="<senha>"
```

```powershell
$env:AUDITORIA_BANCO_URL = "jdbc:postgresql://localhost:5432/auditoria"
$env:AUDITORIA_BANCO_USUARIO = "<usuário>"
$env:AUDITORIA_BANCO_SENHA = "<senha>"
```

Sem banco acessível, a aplicação para na subida, inclusive para os comandos que
não gravam nada.

## Sal de pseudonimização

CNPJ e CPF não entram no núcleo do sistema em texto claro: viram resumo
criptográfico com um sal da instalação. **Não é preciso configurá-lo.** O sal é
resolvido nesta ordem:

1. propriedade `auditoria.pseudonimizacao.sal`;
2. variável de ambiente `AUDITORIA_PSEUDONIMIZACAO_SAL`;
3. arquivo local — `%APPDATA%\auditoria-ibs-cbs\sal` no Windows;
   `$XDG_CONFIG_HOME/auditoria-ibs-cbs/sal`, ou `~/.config/auditoria-ibs-cbs/sal`,
   nos outros sistemas;
4. um sal novo de 256 bits, sorteado, gravado no arquivo de (3) e anunciado no
   terminal.

Sal informado por (1) ou (2) precisa ter **ao menos 32 caracteres**.

```bash
export AUDITORIA_PSEUDONIMIZACAO_SAL="<valor aleatório com 32 ou mais caracteres>"
```

```powershell
$env:AUDITORIA_PSEUDONIMIZACAO_SAL = "<valor aleatório com 32 ou mais caracteres>"
```

**Não versione o sal, e guarde o arquivo.** Não há sal fixo em código: o sorteado
é por instalação, não está no jar e não é versionado. Sal conhecido permite
descobrir o CNPJ por força bruta.

### Trocar o sal com acervo gravado é recusado

O banco guarda a **impressão digital** do sal em uso — o resumo dele, nunca o
sal. Havendo documento gravado e impressão digital diferente da do sal resolvido,
a subida é recusada, com a explicação. Só `diagnosticar-sal` e
`recomecar-do-zero` passam; qualquer outro comando, inclusive um criado no
futuro, é barrado.

Prosseguir não daria erro: o mesmo participante passaria a existir sob dois
pseudônimos no mesmo acervo, e o pseudônimo do documento deixaria de bater com o
que já saiu em planilha e em API, sem que nenhuma contagem mudasse. As
**tratativas não estão em risco**: a chave delas é o hash do item, que não leva
sal.

```bash
# de onde o sal veio e qual a impressão digital (nunca o sal)
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar diagnosticar-sal

# recomeçar o acervo de propósito; preserva as tratativas e o catálogo
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar recomecar-do-zero --confirmo=sim
```

## Tolerância de valor da R05

A R05 precisa saber que diferença de arredondamento **não** deve virar
apontamento. Não é conteúdo normativo: é escolha de quem audita, e decide quanta
divergência some do relatório.

```bash
export AUDITORIA_TOLERANCIA_DE_VALOR="0.01"   # um centavo
export AUDITORIA_TOLERANCIA_DE_VALOR="0"      # exige igualdade exata
```

```powershell
$env:AUDITORIA_TOLERANCIA_DE_VALOR = "0"
```

- Configurada (variável ou propriedade), vale com a origem **configurada na
  instalação**.
- Sem ela, vale `auditoria.tolerancia-de-valor-padrao` (`0.01` no
  `application.properties`), com a origem **padrão do sistema**.
- Sem nenhuma das duas, a aplicação para na subida dizendo o que falta. Valor que
  não é número, ou negativo, também.

**Toda execução grava o valor usado e a origem**, e os dois aparecem na saída do
`auditar`, no Resumo da planilha, no CSV e na tela de acurácia, nas respostas da
API e nas telas de resultado e de histórico. Execução que não gravou a tolerância
sai com "não registrada", nunca com um valor.

## Perfil `api`

O servidor web só sobe com o perfil `api`: chamando o jar sem argumento, ou com
`-Dspring.profiles.active=api` / `SPRING_PROFILES_ACTIVE=api` (ver `servir` em
[COMANDOS.md](COMANDOS.md#servir)). Sem o perfil, nenhum servidor sobe e a
aplicação roda o comando e encerra.

- `server.address=127.0.0.1`, fixo no `application-api.properties`. Não há HTTPS,
  e por isso o bind não deve ser aberto.
- Cookie de sessão `http-only`, `same-site=strict`, só por cookie.
- `spring.jackson.default-property-inclusion=always`: nenhum campo é omitido da
  resposta. Trocar por `NON_NULL` quebra o contrato da API, e os testes acusam.

### Envio de arquivos

O teto do contêiner vale para todo envio, inclusive os CSV de carga. Os demais
valem para as notas enviadas em `POST /api/analises`, nas correções e na medição
de acurácia.

| Variável de ambiente | Propriedade | Padrão | Para quê |
|---|---|---|---|
| `AUDITORIA_UPLOAD_TAMANHO_MAXIMO` | `spring.servlet.multipart.max-file-size` e `max-request-size` | `64MB` | teto do contêiner web, antes de o arquivo chegar ao código |
| `AUDITORIA_UPLOAD_TAMANHO_MAXIMO_BYTES` | `auditoria.upload.tamanho-maximo` | `67108864` | teto do envio, com mensagem própria |
| `AUDITORIA_UPLOAD_ENTRADAS_MAXIMAS` | `auditoria.upload.entradas-maximas` | `5000` | entradas no `.zip` |
| `AUDITORIA_UPLOAD_POR_ENTRADA` | `auditoria.upload.tamanho-maximo-por-entrada` | `16777216` | bytes descomprimidos por entrada |
| `AUDITORIA_UPLOAD_TOTAL_DESCOMPRIMIDO` | `auditoria.upload.total-descomprimido-maximo` | `536870912` | bytes descomprimidos no total |
| `AUDITORIA_UPLOAD_RAZAO_MAXIMA` | `auditoria.upload.razao-de-compressao-maxima` | `500` | razão de compressão máxima |
| `AUDITORIA_UPLOAD_PISO_DA_RAZAO` | `auditoria.upload.piso-para-conferir-razao` | `1048576` | tamanho a partir do qual a razão é conferida |

O tamanho descomprimido é **medido** durante a extração, não lido do cabeçalho do
`.zip`.

## Privacidade

As três propriedades `auditoria.api.expor-*` estão explicadas em
[API.md](API.md#privacidade). `auditoria.exportacao.expor-justificativa` libera
a justificativa da tratativa na planilha; desligada, a célula traz o motivo da
omissão. A planilha nunca leva chave de acesso em texto claro nem descrição do
produto, com qualquer configuração.

## Build, código e esquemas XSD

Java 21 · Spring Boot 3.3.x · PostgreSQL · Maven (módulo único) · Flyway ·
JUnit 5 + AssertJ · Testcontainers.

```
src/main/java/br/edu/tcc/auditoria/
  dominio/          regras de negócio puras, sem framework
  aplicacao/        casos de uso, orquestração
  infraestrutura/   XML, banco, CSV, Spring, CLI, HTTP, interface web
```

A dependência só anda num sentido: `infraestrutura` → `aplicacao` → `dominio`.
A interface web está em `src/main/resources/static`; as migrations, em
`src/main/resources/db/migration`.

As classes de leitura do XML são geradas do XSD oficial da NF-e pelo
`jaxb2-maven-plugin`. **Os cinco esquemas necessários estão versionados** em
`src/main/resources/schemas`, então `mvn test` funciona logo depois do clone, sem
baixar nada. Não são dado fiscal: são os esquemas públicos do
[Portal Nacional da NF-e](https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=BMPFMBoln3w=).
Detalhes, e como atualizá-los, em
[`src/main/resources/schemas/LEIAME.md`](../src/main/resources/schemas/LEIAME.md).

**O caminho do projeto não pode conter acento.** O XJC não resolve os
`xs:include` relativos quando o caminho tem caractere não ASCII: em
`...\Área de Trabalho\...` a geração falha dizendo que não achou
`leiauteNFe_v4.00.xsd`. Espaço no caminho é inofensivo.

**Pasta sincronizada (OneDrive e afins).** O sincronizador às vezes segura
arquivo em `target/generated-sources/jaxb`, e `mvn clean` falha ao apagar. Não é
problema de código: apague `target/` à mão e rode de novo.
