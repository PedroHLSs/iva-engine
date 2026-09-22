# auditoria-ibs-cbs

Ferramenta de auditoria de coerência dos campos de **IBS** e **CBS** em
documentos fiscais eletrônicos (NF-e / NFC-e) já emitidos.

O sistema lê os XMLs, extrai os campos tributários de IBS/CBS de cada item e
confronta esses campos entre si e contra tabelas normativas carregadas por
importação de CSV, produzindo um **relatório de apontamentos** de incoerência.

Não calcula tributo, não emite documento, não corrige XML.

Trabalho de Conclusão de Curso.

## A pergunta que a ferramenta responde (Etapa 11)

O parágrafo acima descreve o motor, e continua verdadeiro. O que a Etapa 11
acrescentou foi uma camada de leitura que organiza o mesmo resultado por
**produto**, com a base normativa ao lado:

> *Que tratamento a base normativa carregada indica para os produtos desta nota,
> e o que o XML declara é coerente com ele?*

As sete regras, as severidades e os três desfechos internos
(`ACHADO | CONFORME | NAO_AVALIADO`) **não mudaram** — é o que mantém válidos os
números de acurácia medidos na Etapa 7. O que passou a existir são quatro estados
visíveis:

| Estado | O que ele afirma |
|---|---|
| **Possível divergência** | o declarado não corresponde ao que a regra aponta |
| **Requer conferência** | a situação merece leitura de quem responde pelo fiscal |
| **Não foi possível concluir** | faltou dado no documento ou na base carregada |
| **Sem divergência identificada** | as regras cadastradas foram aplicadas e nenhuma encontrou violação |

**O sistema nunca escreve "Conferido".** Ele não conferiu nada: aplicou as regras
cadastradas, sobre os campos que elas alcançam. Afirmar conferência seria afirmar
uma verificação que não houve.

**Não avaliado nunca é somado a sem divergência**, em tela nenhuma e em contagem
nenhuma. Decisão em `docs/DECISOES-ARQUITETURA.md`, **D012**.

## Stack

Java 21 · Spring Boot 3.3.x · PostgreSQL · Maven · Flyway · JUnit 5 + AssertJ

## Estrutura

```
src/main/java/br/edu/tcc/auditoria/
  dominio/          regras de negócio puras, sem framework
  aplicacao/        casos de uso, orquestração
  infraestrutura/   XML, banco, CSV, Spring, CLI, HTTP, interface web
```

## Os esquemas XSD vêm no clone

A leitura de XML não é escrita à mão — as classes de leiaute são geradas do XSD
oficial da NF-e. **Os cinco esquemas necessários estão versionados** em
`src/main/resources/schemas`, então `mvn test` funciona logo depois do clone, sem
baixar nada.

Eles não são dado fiscal: são os esquemas públicos publicados pelo
[Portal Nacional da NF-e](https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=BMPFMBoln3w=).
Versioná-los torna o build reprodutível e registra contra qual versão do leiaute
este trabalho foi escrito. Detalhes, e como atualizá-los para um Pacote de
Liberação mais novo, em
[`src/main/resources/schemas/LEIAME.md`](src/main/resources/schemas/LEIAME.md).

**O caminho do projeto não pode conter acento.** O XJC não resolve os
`xs:include` relativos quando o caminho tem caractere não-ASCII — em
`...\Área de Trabalho\...` a geração falha dizendo que não achou
`leiauteNFe_v4.00.xsd`. Espaço no caminho é inofensivo; acento não. Detalhes no
`LEIAME.md` e em D005.

## Sal de pseudonimização

CNPJ e CPF não entram no núcleo do sistema em texto claro — viram resumo
criptográfico com um sal de instalação. **Não é preciso configurá-lo para rodar**
(desde a Etapa 10): se você não declarar nada, o sistema procura um arquivo local
e, não achando, sorteia um sal de 256 bits, grava nesse arquivo e avisa no
terminal onde ele ficou.

A ordem de resolução é:

1. propriedade `auditoria.pseudonimizacao.sal`;
2. variável de ambiente `AUDITORIA_PSEUDONIMIZACAO_SAL`;
3. arquivo local — `%APPDATA%\auditoria-ibs-cbs\sal` no Windows,
   `$XDG_CONFIG_HOME` ou `~/.config/auditoria-ibs-cbs/sal` fora dele;
4. sal novo, sorteado e gravado no arquivo de (3).

Para fixar o sal você mesmo, qualquer uma das duas primeiras serve:

```bash
# variável de ambiente
export AUDITORIA_PSEUDONIMIZACAO_SAL="<valor aleatório com 32+ caracteres>"

# ou propriedade de sistema
mvn -Dauditoria.pseudonimizacao.sal="<valor aleatório com 32+ caracteres>" ...
```

**Não versione o sal, e guarde o arquivo.** Continua não havendo sal fixo em
código: o que o sistema gera é por instalação, não está no jar e não é
versionado.

### Trocar o sal com acervo gravado é recusado

O banco guarda a **impressão digital** do sal em uso — o resumo dele, nunca o
sal. Se o sal resolvido não bater com o que produziu os documentos já gravados, a
subida é recusada, com a explicação.

Não é zelo excessivo. Prosseguir não daria erro nenhum: o mesmo participante
passaria a existir sob dois pseudônimos no mesmo acervo, e o pseudônimo do
documento deixaria de bater com o que já saiu em planilha e em API — sem que
nenhuma contagem mudasse nem nenhum relatório acusasse.

As **tratativas não estão em risco** numa troca de sal: a chave delas é o hash do
item, que não leva sal.

```bash
# de onde o sal veio e qual a impressão digital (nunca o sal)
java -jar auditoria-ibs-cbs-<versao>.jar diagnosticar-sal

# recomeçar o acervo de propósito; preserva as tratativas e o catálogo
java -jar auditoria-ibs-cbs-<versao>.jar recomecar-do-zero --confirmo=sim
```

## Banco de dados

PostgreSQL. O esquema é criado pelas migrations do Flyway, que rodam sozinhas na
subida — **todas as tabelas nascem vazias**, inclusive as do catálogo normativo.
Nenhuma migration insere alíquota, código ou vigência.

O Hibernate roda com `ddl-auto=none`: quem cria e altera tabela é o Flyway, e só
ele.

Credenciais vêm do ambiente, nunca do repositório:

```bash
export AUDITORIA_BANCO_URL="jdbc:postgresql://localhost:5432/auditoria"
export AUDITORIA_BANCO_USUARIO="<usuário>"
export AUDITORIA_BANCO_SENHA="<senha>"
```

## Configuração obrigatória: tolerância de valor

A regra que confere valor de tributo contra base e alíquota precisa saber qual
diferença de arredondamento **não** deve virar apontamento. **Isso não tem valor
padrão** — não é conteúdo normativo, é escolha de quem audita, e decide quanta
divergência some do relatório.

```bash
export AUDITORIA_TOLERANCIA_DE_VALOR="0.01"   # um centavo
export AUDITORIA_TOLERANCIA_DE_VALOR="0"      # exige igualdade exata
```

Sem isso configurado, a aplicação para na subida dizendo o que falta.

## Como usar

Empacote e chame o comando desejado:

```bash
mvn -DskipTests package
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar <comando> [opções]
```

A aplicação sobe o contexto inteiro antes de rodar qualquer comando: exige a
configuração obrigatória acima — sem ela, para na subida dizendo o que falta — e
um banco acessível, porque as migrations do Flyway e o mapeamento JPA sobem
junto. Com o ambiente pronto, chamar sem argumento nenhum lista os comandos, e
chamar um comando sem as opções obrigatórias mostra o modo de usar dele.

Importar catálogo e tratar achado acontecem **só por aqui**, e continuam assim:
o primeiro decide o que o sistema afirma sobre a norma, o segundo é ato de uma
pessoa identificada, e não há autenticação na API.

Auditar também é comando. Desde a Etapa 11 há um segundo caminho para ele — a
tela de conferência, que envia a nota por `POST /api/analises` e dispara o mesmo
pipeline. Os dois gravam o mesmo tipo de execução. Ver `servir`, mais abaixo, e
as decisões D009 e D012.

### `importar-catalogo`

Carrega as tabelas normativas de um diretório de arquivos CSV. **É o único
caminho pelo qual conteúdo da legislação entra no sistema.**

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar \
    importar-catalogo --diretorio=/caminho/do/catalogo --versao=2026-01
```

O diretório precisa ter cinco arquivos, todos obrigatórios:

| Arquivo | Colunas |
|---|---|
| `classificacao-tributaria.csv` | `codigo`, `cstsCompativeis`, `dispositivoLegal`, `indicadorDeBeneficio`, `percentualReducao`, `camposObrigatoriosCondicionados` |
| `registro-ncm.csv` | `ncm`, `descricao` |
| `item-anexo.csv` | `ncm`, `identificadorDoAnexo`, `tipoDeTratamento` |
| `aliquota-vigente.csv` | `tributo`, `percentual`, `abrangencia` |
| `cobertura.csv` | `tabela` |

Todos levam também `vigenciaInicio`, `vigenciaFim` e `fonteNormativa`. O
separador é `;`, listas dentro de um campo usam `|`, linhas começadas por `#`
são comentário, e `vigenciaFim` em branco significa vigência aberta.

**Desde 14/09/2026:**

- as datas podem vir em `aaaa-mm-dd` ou em `dd/mm/aaaa`. A data inexistente é
  recusada, nunca ajustada;
- `classificacao-tributaria.csv` aceita, campo a campo, a forma por tributo no
  lugar da coluna única: `dispositivoLegal_cbs` e `dispositivoLegal_ibs`,
  `reducao_cbs` e `reducao_ibs`, `fonteNormativa_cbs` e `fonteNormativa_ibs`.
  Valores iguais viram um só, texto diferente é guardado como
  `CBS: … | IBS: …`, e **redução diferente recusa a linha** — o importador não
  escolhe entre os dois. As duas formas para o mesmo campo, ou só metade do par,
  também são recusadas. Ver a revisão de 14/09/2026 na D012.

**Os quatro arquivos de dados levam ainda `natureza`** — `FICTICIO` ou
`NORMATIVO` —, acrescentada na Etapa 11. `cobertura.csv` não a tem: ele declara
período e fonte, não conteúdo.

> **Se você já tem CSV de catálogo, eles param de importar até ganharem a
> coluna.** O acréscimo é mecânico: `;natureza` no cabeçalho e `;FICTICIO` (ou
> `;NORMATIVO`) em cada linha.

A coluna é obrigatória, e obrigatória de propósito: é ela que faz a tela avisar
que está exibindo dado de demonstração, sem depender de ninguém lembrar de ligar
uma configuração. Linha sem ela recusa o arquivo inteiro, e duas naturezas no
mesmo arquivo também — um arquivo tem uma procedência só.

A procedência é guardada **por tabela**, e não por carga, por causa do caso misto:
quem carregar um anexo transcrito da norma com o resto fictício vê "catálogo
parcialmente fictício", com a lista de quais tabelas são de demonstração. Um
sinalizador único teria de escolher entre chamar a carga de real ou de fictícia, e
as duas respostas estariam erradas.

**Carga importada antes da Etapa 11 fica com procedência não declarada**, e a tela
pede reimportação. Ela não é tratada como normativa: supor que dado de origem
desconhecida é norma vigente seria a afirmação mais cara que este sistema poderia
fazer por engano.

**`cobertura.csv` declara o que a carga cobre**, uma linha para cada uma das
tabelas `CLASSIFICACAO_TRIBUTARIA`, `NCM` e `ITEM_ANEXO`. É o que permite ao
sistema separar "o catálogo foi carregado para esta data e não traz este
registro" — que vira apontamento — de "esta tabela não foi carregada para esta
data" — que vira não avaliado. O sistema não deduz cobertura a partir das linhas
importadas.

**Arquivo ausente não é lido como tabela vazia.** Para declarar uma tabela sem
registros, forneça o arquivo apenas com o cabeçalho: aí a ausência foi dita, e
não suposta.

Cada importação vira uma carga identificada pela versão. A auditoria usa a mais
recente e registra a versão dela em cada execução; as cargas antigas ficam, para
que relatórios produzidos contra elas continuem conferíveis.

### `auditar`

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar auditar --origem=dados/2026-01
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar auditar --origem=dados/lote.zip
```

Lê os XML da origem, monta o contexto normativo **na data de emissão de cada
documento**, roda as regras e grava documentos, itens, apontamentos e o recibo da
execução.

O resumo impresso traz apontamentos por severidade, por regra, avaliações que não
concluíram e arquivos que não puderam ser lidos. Os quatro números importam: um
lote com zero apontamentos e milhares de avaliações não concluídas não é um lote
limpo.

**Reprocessar o mesmo lote não duplica apontamento.** O apontamento é
identificado pelo que aponta — resumo do conteúdo do item, identificador da regra
e versão da regra —, não pela linha em que foi gravado.

### `listar-achados`

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar listar-achados
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar listar-achados \
    --severidade=CRITICA --apenas-abertos --limite=200
```

Lista do mais grave para o menos grave, com evidências e com a tratativa que
houver. Apontamento tratado continua aparecendo, marcado com a decisão e a
justificativa; `--apenas-abertos` mostra só o que falta decidir.

### `tratar-achado`

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar tratar-achado \
    --achado=<id que a listagem mostra> \
    --decisao=REFUTADO \
    --justificativa="Conferido com o contribuinte: o campo está correto."
```

`ACEITO` quando a incoerência procede; `REFUTADO` quando o documento está
correto. **A justificativa é obrigatória** — apontamento tratado sem razão
registrada é apontamento apagado.

**A tratativa sobrevive ao reprocessamento do lote** e, se a mesma incoerência
for gerada de novo, o apontamento já vem tratado.

**Se a versão da regra mudar, a tratativa não se aplica e o apontamento reabre.**
Isso é intencional: a justificativa foi dada contra um critério, e critério novo
é pergunta nova. A tratativa antiga não é apagada — fica no banco, presa à versão
em que foi dada. Mudar o conteúdo do item tem o mesmo efeito, pelo mesmo motivo.

### `exportar`

Emite o papel de trabalho de uma execução em planilha xlsx.

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar     exportar --arquivo=relatorios/2026-01.xlsx

java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar     exportar --arquivo=relatorios/refeito.xlsx --execucao=<id da rodada>
```

Sem `--execucao`, exporta a rodada mais recente. Com ela, reemite o papel de
trabalho de qualquer rodada anterior — com os apontamentos que **aquela** rodada
produziu e a identificação que ela tinha.

Três abas:

| Aba | O que traz |
|---|---|
| **Resumo** | Identificação da execução (data, versão de catálogo, versão de regras, resumo da entrada), totais por severidade e por regra, e os não avaliados com os motivos agrupados |
| **Achados** | Uma linha por apontamento, com evidências, fundamento normativo, vigência aplicada, valor em risco e status de tratativa |
| **Não avaliados** | Uma linha por avaliação que não concluiu, com o motivo |

**A identificação da execução abre o Resumo, sem nada acima.** É o que torna a
planilha conferível meses depois: sem saber contra qual catálogo ela foi
produzida, um apontamento que deixou de proceder por mudança de tabela fica
indistinguível de um erro do sistema.

**Nenhum identificador em texto claro sai na planilha.** O documento aparece pelo
pseudônimo da chave de acesso — cujos dígitos carregam o CNPJ do emitente — mais
modelo, série, número, data de emissão e UF, que localizam a nota no sistema da
empresa sem identificar ninguém. O pseudônimo é estável dentro de uma instalação,
então duas planilhas do mesmo acervo podem ser cruzadas entre si.

**Ausência é escrita, nunca deixada em branco:** campo que não veio no documento
sai como `(não informado)`, regra sem valor de referência a opor como
`(sem referência)`, vigência sem fim como `(sem fim declarado)`, e apontamento
sem decisão como `ABERTO`. Célula vazia numa planilha lida meses depois é
indistinguível de célula que ninguém preencheu.

### `avaliar-acuracia`

Mede o motor contra um gabarito rotulado à mão, e imprime precisão, recall e F1
por regra e consolidados. É o resultado empírico do trabalho.

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar avaliar-acuracia --origem=dados/amostra --gabarito=gabarito.csv

java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar avaliar-acuracia --origem=dados/amostra --gabarito=gabarito.csv --relatorio=relatorios/acuracia.csv
```

O gabarito é um CSV com quatro colunas obrigatórias — separador `;`, UTF-8,
linhas iniciadas por `#` são comentário:

| Coluna | O que traz |
|---|---|
| `chave_documento` | Chave de acesso do documento, 44 dígitos |
| `numero_item` | Número do item dentro do documento, a partir de 1 |
| `regra_id` | Identificador da regra (`R01` a `R07`) |
| `rotulo_esperado` | `ACHADO` ou `CONFORME` — nada mais |

Sem `--relatorio`, o resultado sai apenas no terminal. Com ele, sai também num
CSV com uma linha por regra e uma consolidada, e a identificação da rodada em
linhas de comentário no topo.

**`NAO_AVALIADO` não é rótulo de gabarito e não conta como acerto nem como
erro.** Quem rotula responde sobre o documento, não sobre o sistema. Quando o
motor não consegue julgar — faltou campo no documento ou tabela no catálogo —
isso fica **fora de precisão, recall e F1** e aparece na **cobertura**, que é
`avaliados / total do gabarito`, reportada como métrica própria.

Essa separação é o ponto da etapa: um sistema que não avalia nada tem precisão
**indefinida**, não precisão perfeita.

**Métrica sem denominador sai como `(indefinida)`, nunca como zero ou um.**
Precisão exige `VP + FP > 0`, recall exige `VP + FN > 0`, e F1 só é definido
quando os dois são. Campo em branco num relatório lido depois é indistinguível de
campo que ninguém preencheu.

**O consolidado soma células, não faz média das métricas por regra.** A média
trataria uma regra com três linhas rotuladas igual a uma com duzentas.

**Toda regra do conjunto ganha linha**, inclusive as que o gabarito não cita —
zeradas, com métricas indefinidas. Omiti-las faria o relatório parecer completo
quando não é.

**Linha de gabarito cujo item o motor não avaliou** — documento fora do lote,
item inexistente — é contada em `SemAval`, listada com endereço, e também fica
fora das métricas: é desalinhamento entre gabarito e acervo, não qualidade de
regra.

**Nada é gravado no banco.** Medir não é auditar: a medição roda o motor de novo
e não entra no histórico de execuções nem vira papel de trabalho. Rodar o motor
de novo é obrigatório, aliás — o banco não guarda avaliação conforme, e sem elas
metade da matriz de confusão seria inobservável.

### `servir`

Sobe a interface web e a API. Até a Etapa 10 isto era **somente leitura**; desde a
Etapa 11 existe **uma** porta de escrita — `POST /api/analises`, que recebe a nota
e dispara o mesmo pipeline do comando `auditar`. Importar catálogo e tratar achado
continuam sendo comandos.

```bash
java -Dspring.profiles.active=api -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar servir
# ou
SPRING_PROFILES_ACTIVE=api java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar servir
```

O perfil `api` **não pode vir como argumento** — o interpretador de argumentos
exige `<comando> [--opção=valor]` e recusaria o `--spring.profiles.active`. Ele
entra por propriedade de sistema (antes do `-jar`) ou por variável de ambiente.
Sem o perfil, `servir` recusa e diz a invocação certa, em vez de deixar o
processo pendurado sem servidor nenhum.

Escuta em `127.0.0.1` e na porta de `AUDITORIA_API_PORTA` (padrão 8080). Encerre
com Ctrl+C.

Leitura técnica (Etapa 8):

```
GET  /api/execucoes                       ?limite=25
GET  /api/execucoes/{id}
GET  /api/execucoes/{id}/achados          ?regra=R05&severidade=GRAVE&status=ABERTO
                                          &pagina=0&tamanho=50
GET  /api/execucoes/{id}/nao-avaliados    ?regra=R05&pagina=0&tamanho=50
```

Conferência (Etapa 11):

```
POST /api/analises                        campo "arquivo": um .xml ou um .zip
GET  /api/analises/{id}
GET  /api/analises/{id}/produtos          ?pagina=0&tamanho=50
GET  /api/analises/{id}/produtos/{endereco}
GET  /api/analises/{id}/grupos            ?ordem=VALOR_DOS_PRODUTOS
GET  /api/analises/{id}/grupos/produtos   ?ncm=&cClassTrib=&situacao=&pagina=0
GET  /api/base-tributaria                 ?data=2026-01-15&ncm=&cClassTrib=
```

**`.rar` não é aceito**, e a recusa é explícita: não há biblioteca Java confiável
para RAR5. Compacte em `.zip`. O pacote passa por um guarda antes de qualquer
leitura — contagem de entradas, tamanho descomprimido **medido** e não declarado,
razão de compressão acima de um piso, e caminho de entrada que escape do diretório
de extração.

**`data` é obrigatória em `/api/base-tributaria`**, e não tem padrão. O mesmo
catálogo responde coisas diferentes em datas diferentes, e um padrão silencioso de
"hoje" faria a resposta mudar sozinha de um dia para o outro (D003).

Não há autenticação nesta etapa, e é justamente por isso que a API escuta só em
localhost — **trocar `server.address` por `0.0.0.0` sem antes resolver
autenticação publica documento fiscal real para a rede inteira, e agora expõe
também a capacidade de gravar.**

#### As telas

| Endereço | Tela |
|---|---|
| `/` | Conferência: enviar a nota, resultado, detalhe do produto, lote, base tributária, histórico |
| `/tecnica.html` | Visão técnica (Etapa 9): apontamentos por regra e severidade, e acurácia |

Detalhes em [`docs/INTERFACE-WEB.md`](docs/INTERFACE-WEB.md).

**Os três desfechos aparecem inteiros.** `ACHADO` e `NAO_AVALIADO` são campos
próprios — o segundo com os motivos agrupados por regra —, e cada linha escreve o
próprio `resultado` por extenso. O `CONFORME` é derivado, porque o banco não grava
avaliação conforme, e a resposta traz a conta que o produziu:

```json
"desfechos" : {
  "avaliacoesProduzidas" : 20,
  "comoFoiObtido" : "quantidadeItens 10 x regras aplicadas 2: o motor produz ...",
  "achado" : 1,
  "naoAvaliado" : 3,
  "conforme" : {
    "valor" : 16,
    "derivacao" : "avaliacoesProduzidas 20 - achado 1 - naoAvaliado 3",
    "motivoDaAusencia" : null
  }
}
```

Nenhum campo é omitido: ausência é `null` **com um campo irmão dizendo por quê**.

#### Privacidade: três campos desligados por padrão

| propriedade | padrão | o que libera |
|---|---|---|
| `auditoria.api.expor-chave-de-acesso` | `false` | a chave em texto claro, além do pseudônimo |
| `auditoria.api.expor-justificativa` | `false` | o texto da justificativa da tratativa |
| `auditoria.api.expor-descricao-do-produto` | `false` | o `xProd` do XML |

Os dígitos intermediários da chave de acesso são o CNPJ do emitente; a
justificativa é texto livre digitado por pessoa e pode conter CNPJ ou razão
social. Por padrão, os dois saem como `null` acompanhados do motivo, e o documento
é identificado pelo pseudônimo — o mesmo do papel de trabalho. São configuração da
instalação, e não parâmetro de consulta: quem consome a API não escolhe quanto
dado pessoal recebe.

**A descrição do produto é o pior dos três nesse aspecto**, e por isso entrou sob
o mesmo regime. A justificativa é escrita por quem audita, uma de cada vez,
sabendo que fica registrada; o `xProd` vem da fonte, em escala, e ninguém o
revisou — na prática traz nome de cliente, referência de pedido e número de
contrato. Quem grava já substitui corrida de 44 dígitos por um marcador, mas
**nenhuma regra de forma alcança prosa**: "P/ OBRA FULANO" passa.

Consequência prática de estar desligada: a tela de detalhe mostra a descrição do
NCM no catálogo e, no lugar da descrição da nota, o motivo. A comparação entre as
duas — que é o sinal de classificação errada que nenhuma das duas dá sozinha — só
aparece inteira com a propriedade ligada.

**Nenhuma das três afeta a exportação.** O papel de trabalho nunca leva chave em
texto claro nem descrição de produto, ligadas ou não, e o guarda de vazamento da
Etapa 6 confere isso a cada exportação.

## Como rodar os testes

Requer JDK 21 e Maven 3.9+.

```bash
mvn test
```

Os testes de integração da persistência, da exportação e da API sobem um
PostgreSQL de verdade com Testcontainers e **exigem Docker em execução**. Sem
Docker eles se desabilitam em vez de falhar, e aparecem como pulados no resumo do
Maven — o build passa, mas a persistência e o contrato da API não foram
verificados.

Para compilar sem rodar os testes:

```bash
mvn -DskipTests package
```

## Dados fiscais reais não são versionados

Este projeto processa documentos fiscais reais de uma empresa. O `.gitignore`
bloqueia `dados/`, `*.xml`, `*.zip`, `*.csv`, `*.p12`, `*.pfx` e formatos
correlatos. **Nenhum arquivo de dado entra no histórico do Git.**

Coloque os documentos a auditar em `dados/` (diretório ignorado). Tabelas
normativas são carregadas por importação de CSV em tempo de execução — o
repositório não contém alíquotas, códigos nem qualquer valor da legislação.

Dados de exemplo existem apenas em `src/test/resources` e são explicitamente
fictícios.

## Convenções

Código, nomes e comentários em português. Regras completas de trabalho no
repositório: [`CLAUDE.md`](CLAUDE.md). Decisões de arquitetura:
[`docs/DECISOES-ARQUITETURA.md`](docs/DECISOES-ARQUITETURA.md).
