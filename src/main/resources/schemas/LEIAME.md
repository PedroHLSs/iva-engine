# Esquemas XSD da NF-e / NFC-e

Este diretório guarda os esquemas XSD oficiais do leiaute da NF-e. **Eles vêm no
clone**: os cinco arquivos necessários estão versionados, e `mvn test` funciona
sem baixar nada.

As instruções de download abaixo servem para **atualizar** os esquemas para um
Pacote de Liberação mais novo, não para o primeiro build.

O projeto não escreve parser de XML à mão e não descreve o leiaute em código.
As classes de leitura são geradas pelo `jaxb2-maven-plugin` a partir dos
arquivos deste diretório, em `target/generated-sources/jaxb`, no pacote
`br.edu.tcc.auditoria.infraestrutura.xml.gerado`. Sem os XSD aqui, o plugin não
gera nada e a compilação falha dizendo que esse pacote não existe.

## Onde baixar

Portal Nacional da NF-e → **Documentos** → **Esquemas XML**:

<https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=BMPFMBoln3w=>

Espelho da SVRS, com o mesmo conteúdo:

<https://dfe-portal.svrs.rs.gov.br/NFe/Documentos>

Baixe o **Pacote de Liberação** mais recente que contemple a NT 2025.002
(IBS/CBS/IS) — na data em que este arquivo foi escrito, o `PL_010b v1.30`. É o
pacote que traz o `DFeTiposBasicos_v1.00.xsd`, onde moram os tipos do grupo
IBS/CBS. Um pacote anterior à NT 2025.002 compila, mas gera classes sem o grupo
que este sistema audita.

## Quais arquivos copiar

O pacote vem com mais de cento e cinquenta arquivos — consulta de status,
eventos, inutilização, distribuição de DFe. Nada disso é usado aqui. Copie para
este diretório **exatamente estes cinco**:

| Arquivo | Por que é necessário |
|---|---|
| `nfe_v4.00.xsd` | raiz `NFe`; é a única fonte listada no `pom.xml` |
| `leiauteNFe_v4.00.xsd` | leiaute da NF-e; incluído pelo acima |
| `tiposBasico_v4.00.xsd` | tipos básicos do leiaute |
| `DFeTiposBasicos_v1.00.xsd` | tipos do grupo IBS/CBS (NT 2025.002) |
| `xmldsig-core-schema_v1.01.xsd` | assinatura digital |

Os quatro últimos entram por `xs:include` a partir do primeiro, e por isso não
aparecem na configuração do plugin no `pom.xml` — mas precisam estar neste
diretório, com estes nomes, ao lado dele.

Não renomeie, não edite e não recorte nenhum deles. O `schemaLocation` de cada
`xs:include` é relativo e usa o nome original do arquivo.

## `procNFe_v4.00.xsd` não é necessário

Até 03/09/2026 este arquivo era o sexto da lista e aparecia como fonte no
`pom.xml`. Ele nunca esteve no repositório, e o plugin o ignorava em silêncio a
cada build, com a linha `Ignored given or default sources`.

Ele só declara o elemento raiz `nfeProc`. O tipo `TNfeProc`, que é o que este
projeto usa, é um `xs:complexType` do `leiauteNFe_v4.00.xsd` e vem de lá.
`LeitorDocumentoFiscal` lê o nome do elemento raiz por StAX e desserializa por
tipo declarado, sem depender de `@XmlRootElement` — de modo que documento com
envelope de autorização é lido normalmente sem este esquema.

Verificado gerando do zero, fora do OneDrive, com os cinco arquivos acima: as
mesmas 52 classes, `TNfeProc` entre elas, e os testes de leitura de XML verdes,
inclusive o que lê documento com envelope `nfeProc`.

## O caminho do projeto não pode ter acento

O XJC monta a URI base do esquema sem codificar caracteres não-ASCII, e aí os
`xs:include` relativos deixam de resolver. Com o projeto em
`C:\...\Área de Trabalho\...`, a geração falha assim:

```
schema_reference.4: Failed to read schema document 'leiauteNFe_v4.00.xsd',
because 1) could not find the document; ...
src-resolve: Cannot resolve the name 'TNfeProc' to a(n) 'type definition' component.
```

Os cinco arquivos estão no lugar certo; o que não resolve é o caminho. Não é
defeito do `jaxb2-maven-plugin` — o XJC chamado direto falha igual. Espaço no
caminho é inofensivo; acento não.

Solução: manter o repositório num caminho sem acento, por exemplo
`C:\projetos\tcc3`.

## Conferindo

```bash
mvn generate-sources
ls target/generated-sources/jaxb/br/edu/tcc/auditoria/infraestrutura/xml/gerado
```

Devem aparecer, entre outras, `TNfeProc.java`, `TNFe.java` e `TTribNFe.java`
— esta última é o grupo `IBSCBS` do item, e a sua ausência indica pacote de
esquemas anterior à NT 2025.002.

## Versionar ou não: decidido em 03/09/2026

**Os cinco arquivos são versionados.** A escolha é essa, e está registrada no
`.gitignore`, no comentário do plugin no `pom.xml`, no `README.md` e na D005.

O `.gitignore` bloqueia dado fiscal, não esquema, e por isso `.xsd` nunca esteve
na lista de bloqueio. Versioná-los torna o build reprodutível — quem clona roda
`mvn test` de imediato, o que importa para quem for avaliar este trabalho — e
registra contra qual versão do leiaute ele foi escrito. O custo é o tamanho do
repositório, que com cinco esquemas é irrelevante.

### Como o repositório chegou aqui

Os arquivos entraram no Git na Etapa 4, no commit `9a9a8be`, enquanto cinco
textos continuavam dizendo que eles não vinham no clone: este arquivo, o
`README.md`, o `pom.xml`, a D005 e o `CLAUDE.md`. A divergência durou até a
revisão de conformidade de 03/09/2026, que optou por ratificar o fato em vez de
desfazê-lo.

Havia também o meio-termo contra o qual este arquivo advertia: `procNFe_v4.00.xsd`
era pedido aqui e listado no `pom.xml` sem nunca ter entrado no repositório. Ele
foi removido das duas listas por não ser necessário — ver a seção acima.
