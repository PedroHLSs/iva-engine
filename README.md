# auditoria-ibs-cbs

Ferramenta de auditoria de coerência dos campos de **IBS** e **CBS** em NF-e e NFC-e já emitidas. Lê os XML,
extrai o grupo IBS/CBS de cada item e o confronta consigo mesmo e com tabelas normativas que **você** carrega
por CSV. Responde, por produto: *que tratamento a base carregada indica, e o que o XML declara é coerente com
ele?* Trabalho de Conclusão de Curso.

**O que não faz:** não calcula tributo, não emite nem corrige documento, não aconselha o contribuinte e não traz
nenhuma alíquota, código, vínculo ou vigência embutida — todo conteúdo normativo entra por importação de CSV.

## Como funciona

```
XML ou .zip ─▶ leitura e pseudonimização ─▶ motor R01–R07 ─▶ PostgreSQL ─▶ CLI · planilha .xlsx · API · web
```

CNPJ e CPF viram pseudônimo na leitura. Para cada item, o motor resolve o catálogo vigente **na data de emissão**
e cada regra responde `ACHADO`, `CONFORME` ou `NAO_AVALIADO` (sempre com motivo). Silêncio do catálogo só vira
apontamento dentro do período que a carga declara cobrir. A interface traduz isso em quatro estados:

| Estado | Vem de | O que afirma |
|---|---|---|
| Possível divergência | `ACHADO` crítico, grave ou moderado | o declarado não corresponde ao que a regra aponta a partir da base carregada |
| Requer conferência | `ACHADO` informativo | a situação merece leitura de quem responde pelo fiscal |
| Não foi possível concluir | `NAO_AVALIADO` | faltou dado no documento ou na base carregada |
| Sem divergência identificada | `CONFORME` | as regras cadastradas foram aplicadas, sobre os campos que alcançam, e nenhuma encontrou violação |

O sistema nunca escreve "Conferido", e não avaliado nunca é somado a sem divergência.

## Regras

| Regra | Severidade | Verifica |
|---|---|---|
| R01 | crítica | o cClassTrib do item existe na tabela de classificações na data de emissão |
| R02 | moderada | os CST de IBS e de CBS estão entre os CST que o catálogo admite para o cClassTrib |
| R03 | grave | se o cClassTrib é de benefício, o NCM está num dos anexos que o catálogo admite para ele |
| R04 | informativa | o NCM consta de anexo, mas o cClassTrib usado é declarado de tributação integral |
| R05 | grave | IBS UF, IBS municipal e CBS = base × alíquota do catálogo × (1 − redução/100), dentro da tolerância |
| R06 | crítica | o NCM do item existe na tabela de NCM na data de emissão |
| R07 | crítica | os campos que o catálogo exige para o cClassTrib vieram preenchidos no item |

Versões: R01, R02 e R06 `1.0.0`; R03, R04 e R07 `1.1.0`; R05 `1.3.0`; conjunto `2026.6`. Motivos em
[docs/DECISOES-ARQUITETURA.md](docs/DECISOES-ARQUITETURA.md); mudanças no tempo em [docs/HISTORICO.md](docs/HISTORICO.md).

## Requisitos

- JDK 21 e Maven 3.9+; PostgreSQL 16, instalado ou em contêiner; Docker para os testes de integração.
- **Caminho do projeto sem acento** — o gerador de classes do XSD falha com caractere não ASCII. Os esquemas
  XSD da NF-e já vêm no repositório ([src/main/resources/schemas/LEIAME.md](src/main/resources/schemas/LEIAME.md)).

## Início rápido

**1. Banco** (comando igual nos dois shells). Sem variáveis, a aplicação usa `localhost:5432/auditoria` com
usuário e senha `auditoria` — bom para desenvolvimento; com dado real, defina as suas ([docs/CONFIGURACAO.md](docs/CONFIGURACAO.md)).

```bash
docker run -d --name auditoria-pg -p 5432:5432 -e POSTGRES_DB=auditoria -e POSTGRES_USER=auditoria -e POSTGRES_PASSWORD=auditoria postgres:16
```

**2. Build** (igual nos dois shells): `mvn -DskipTests package`

**3. Primeiro administrador, importação do catálogo, auditoria e servidor** (a senha é pedida no terminal):

```bash
JAR=target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar
java -jar $JAR criar-administrador --login=admin --nome="Nome da Pessoa"
java -jar $JAR importar-catalogo --diretorio=/caminho/do/catalogo --versao=carga-1
java -jar $JAR auditar --origem=dados/lote          # pasta com .xml, ou um .zip
java -jar $JAR                                      # sem argumento: interface web e API
```

```powershell
$JAR = "target\auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar"
java -jar $JAR criar-administrador --login=admin '--nome=Nome da Pessoa'
java -jar $JAR importar-catalogo --diretorio=C:\caminho\do\catalogo --versao=carga-1
java -jar $JAR auditar --origem=dados\lote
java -jar $JAR
```

Abra `http://127.0.0.1:8080`, entre com o administrador e envie uma nota pela tela de conferência.

## Catálogo em CSV

Separador `;`, UTF-8 sem BOM, `#` para comentário, listas com `|`, datas em `aaaa-mm-dd` ou `dd/mm/aaaa`.
Os arquivos de dados levam ainda `vigenciaInicio`, `vigenciaFim` (em branco = aberta), `fonteNormativa` e
`natureza` (`FICTICIO` ou `NORMATIVO`). Qualquer linha inválida recusa a carga inteira, com todos os problemas.

| Arquivo | Obrigatório | Colunas próprias |
|---|---|---|
| `classificacao-tributaria.csv` | sim | `codigo`, `cstsCompativeis`, `dispositivoLegal`, `indicadorDeBeneficio`, `percentualReducao`, `camposObrigatoriosCondicionados`; opcionais `tributacaoIntegral`, `anexosAdmitidos`, `reducaoIncideSobre` |
| `registro-ncm.csv` | sim | `ncm`, `descricao` |
| `item-anexo.csv` | sim | `ncm`, `identificadorDoAnexo`, `tipoDeTratamento` |
| `aliquota-vigente.csv` | sim | `tributo` (`IBS_UF`, `IBS_MUN`, `CBS`), `percentual`, `abrangencia` |
| `cobertura.csv` | sim | `tabela` (`CLASSIFICACAO_TRIBUTARIA`, `NCM`, `ITEM_ANEXO`), uma linha por tabela |
| `anexos-declarados.csv` | não | `identificadorDoAnexo`, `tipoDeCodigo` (`NCM`, `NBS`, `NCM_E_NBS`) |

Exemplo **fictício** de `registro-ncm.csv` (valores aceitos, célula em branco e recusas de cada coluna em
[docs/FORMATO-CSV.md](docs/FORMATO-CSV.md)):

```csv
ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
00000000;DESCRICAO FICTICIA AAA;01/01/1900;;Fonte fictícia AAA;FICTICIO
```

## Comandos

`java -jar <jar> <comando> [--opção=valor]`. Opções, exemplos e saídas em [docs/COMANDOS.md](docs/COMANDOS.md).

| Comando | O que faz |
|---|---|
| `importar-catalogo` | importa as tabelas de um diretório de CSV; depois da primeira carga, aceita só parte dos arquivos |
| `auditar` | audita uma pasta de `.xml` ou um `.zip` com a carga mais recente |
| `listar-achados` | lista os apontamentos, do mais grave para o menos grave |
| `tratar-achado` | aceita ou refuta um apontamento, com justificativa e senha |
| `exportar` | grava o papel de trabalho de uma execução em `.xlsx` |
| `avaliar-acuracia` | mede precisão, recall e F1 contra um gabarito rotulado |
| `criar-administrador` | cria o primeiro administrador, ou recupera o acesso |
| `servir` | sobe a interface web e a API (o jar sem argumento faz o mesmo) |
| `diagnosticar-sal` / `recomecar-do-zero` | diagnosticam e resolvem uma troca de sal de pseudonimização |

## Interface web e API

Em `http://127.0.0.1:8080`: `/` é a conferência (envio de nota, resultado por produto, lote, base tributária,
histórico, acurácia, cargas e usuários) e `/tecnica.html` é a visão técnica por regra e severidade
([docs/INTERFACE-WEB.md](docs/INTERFACE-WEB.md)). Toda chamada exige login, com três perfis — administrador, fiscal
e consulta — conferidos no servidor; escuta só em `127.0.0.1`, porque não há HTTPS ([docs/API.md](docs/API.md)).

## Configuração

| Variável | Padrão | Para quê |
|---|---|---|
| `AUDITORIA_BANCO_URL` / `_USUARIO` / `_SENHA` | `jdbc:postgresql://localhost:5432/auditoria` / `auditoria` / `auditoria` | banco |
| `AUDITORIA_PSEUDONIMIZACAO_SAL` | arquivo local, ou sorteado e gravado nele | sal da pseudonimização (32+ caracteres) |
| `AUDITORIA_TOLERANCIA_DE_VALOR` | `0.01` (`auditoria.tolerancia-de-valor-padrao`) | diferença que a R05 não aponta |
| `AUDITORIA_API_PORTA` / `AUDITORIA_SESSAO_DURACAO` | `8080` / `30m` | porta do servidor e duração da sessão |
| `AUDITORIA_API_EXPOR_CHAVE_DE_ACESSO` / `_JUSTIFICATIVA` / `_DESCRICAO_DO_PRODUTO` | `false` | dados pessoais na API |

Lista completa, limites de envio e o guarda de troca de sal em [docs/CONFIGURACAO.md](docs/CONFIGURACAO.md).

## Testes

`mvn test`. Os testes de persistência, exportação e API sobem um PostgreSQL com Testcontainers e **exigem
Docker**; sem ele, aparecem como pulados e o build passa sem que a persistência e a API tenham sido verificadas.
Em pasta sincronizada (OneDrive), se `mvn clean` falhar ao apagar `target/`, apague-o à mão.

## Dados fiscais não são versionados

O `.gitignore` bloqueia `dados/`, `*.xml`, `*.zip`, `*.csv`, `*.xlsx`, `*.p12`, `*.pfx` e formatos correlatos.
Coloque os documentos em `dados/` (ignorado). O repositório não contém alíquota, código nem valor da legislação, e
não há catálogo de exemplo versionado: monte os CSV a partir da norma. Fixtures fictícias de teste em
`src/test/resources` entram uma a uma, com `git add -f`.

## Convenções e licença

Código, nomes e comentários em português; `dominio/` não depende de framework. Regras de trabalho em
[CLAUDE.md](CLAUDE.md). **Licença: ainda não definida** — o repositório não tem arquivo `LICENSE`.
