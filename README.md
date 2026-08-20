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

## Como rodar os testes

Requer JDK 21 e Maven 3.9+.

```bash
mvn test
```

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
