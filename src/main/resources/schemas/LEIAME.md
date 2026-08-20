# Esquemas XSD da NF-e / NFC-e

Este diretório guarda os esquemas XSD oficiais do leiaute da NF-e. **Eles não
vêm no clone**: precisam ser baixados do Portal Nacional da NF-e e copiados
para cá antes do primeiro `mvn test`.

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
este diretório **exatamente estes seis**:

| Arquivo | Por que é necessário |
|---|---|
| `procNFe_v4.00.xsd` | raiz `nfeProc` — documento com o protocolo de autorização |
| `nfe_v4.00.xsd` | raiz `NFe` — documento sem o envelope de autorização |
| `leiauteNFe_v4.00.xsd` | leiaute da NF-e; incluído pelos dois acima |
| `tiposBasico_v4.00.xsd` | tipos básicos do leiaute |
| `DFeTiposBasicos_v1.00.xsd` | tipos do grupo IBS/CBS (NT 2025.002) |
| `xmldsig-core-schema_v1.01.xsd` | assinatura digital |

Os quatro últimos entram por `xs:include` a partir dos dois primeiros, e por
isso não aparecem na configuração do plugin no `pom.xml` — mas precisam estar
neste diretório, com estes nomes, ao lado dos outros.

Não renomeie, não edite e não recorte nenhum deles. O `schemaLocation` de cada
`xs:include` é relativo e usa o nome original do arquivo.

## O caminho do projeto não pode ter acento

O XJC monta a URI base do esquema sem codificar caracteres não-ASCII, e aí os
`xs:include` relativos deixam de resolver. Com o projeto em
`C:\...\Área de Trabalho\...`, a geração falha assim:

```
schema_reference.4: Failed to read schema document 'leiauteNFe_v4.00.xsd',
because 1) could not find the document; ...
src-resolve: Cannot resolve the name 'TNfeProc' to a(n) 'type definition' component.
```

Os seis arquivos estão no lugar certo; o que não resolve é o caminho. Não é
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

## Versionar ou não

O `.gitignore` do projeto bloqueia dados fiscais, não esquemas: `.xsd` entra no
Git normalmente se você mandar. Versioná-los torna o build reprodutível e
registra contra qual versão do leiaute o TCC foi escrito; não versioná-los
mantém o repositório menor e obriga cada clone a baixar do Portal. A escolha é
sua — só não deixe o repositório num meio-termo, com parte dos seis arquivos
versionada e parte não.
