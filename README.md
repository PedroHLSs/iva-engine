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
