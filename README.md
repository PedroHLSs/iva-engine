# auditoria-ibs-cbs

Ferramenta de auditoria de coerência dos campos de **IBS** e **CBS** em
documentos fiscais eletrônicos (NF-e / NFC-e) já emitidos.

O sistema lê os XMLs, extrai os campos tributários de IBS/CBS de cada item e
confronta esses campos entre si e contra tabelas normativas carregadas por
importação de CSV, produzindo um **relatório de apontamentos** de incoerência.

Não calcula tributo, não emite documento, não corrige XML.

Trabalho de Conclusão de Curso.

## Stack

Java 21 · Spring Boot 3.3.x · PostgreSQL · Maven · Flyway · JUnit 5 + AssertJ

## Estrutura

```
src/main/java/br/edu/tcc/auditoria/
  dominio/          regras de negócio puras, sem framework
  aplicacao/        casos de uso, orquestração
  infraestrutura/   XML, banco, CSV, Spring
```

## Antes do primeiro build: baixar os esquemas XSD

A leitura de XML não é escrita à mão — as classes de leiaute são geradas do XSD
oficial da NF-e. **Os XSD não vêm no clone.** Baixe o Pacote de Liberação no
[Portal Nacional da NF-e](https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=BMPFMBoln3w=)
e copie seis arquivos para `src/main/resources/schemas`. A lista exata está em
[`src/main/resources/schemas/LEIAME.md`](src/main/resources/schemas/LEIAME.md).

Sem eles, o build falha em `generate-sources`.

**O caminho do projeto não pode conter acento.** O XJC não resolve os
`xs:include` relativos quando o caminho tem caractere não-ASCII — em
`...\Área de Trabalho\...` a geração falha dizendo que não achou
`leiauteNFe_v4.00.xsd`. Espaço no caminho é inofensivo; acento não. Detalhes no
`LEIAME.md` e em D005.

## Configuração obrigatória: sal de pseudonimização

CNPJ e CPF não entram no núcleo do sistema em texto claro — viram resumo
criptográfico com um sal de instalação. **Esse sal não tem valor padrão**, e sem
ele configurado o processamento para. Defina antes de rodar:

```bash
# variável de ambiente
export AUDITORIA_PSEUDONIMIZACAO_SAL="<valor aleatório com 32+ caracteres>"

# ou propriedade de sistema
mvn -Dauditoria.pseudonimizacao.sal="<valor aleatório com 32+ caracteres>" ...
```

Não versione o sal. Trocá-lo muda todos os pseudônimos e invalida a comparação
com o que já foi processado.

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
chamar um comando sem as opções obrigatórias mostra o modo de usar dele. Não há
API REST — ver D006.

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

## Como rodar os testes

Requer JDK 21 e Maven 3.9+.

```bash
mvn test
```

O teste de integração da persistência sobe um PostgreSQL de verdade com
Testcontainers e **exige Docker em execução**. Sem Docker ele se desabilita em
vez de falhar, e aparece como pulado no resumo do Maven — o build passa, mas a
persistência não foi verificada.

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
