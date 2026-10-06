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

> **Emenda de 04/10/2026:** "nunca do repositório" deixou de valer em 21/09/2026
> (`192c802`). Sem as duas variáveis, o `application.properties` usa `auditoria`
> como usuário e como senha. Serve a um banco local de desenvolvimento; numa
> instalação que processa documento fiscal real, defina as duas variáveis.

## Tolerância de valor

A regra que confere valor de tributo contra base e alíquota (R05) precisa saber
qual diferença de arredondamento **não** deve virar apontamento. Não é conteúdo
normativo: é escolha de quem audita, e decide quanta divergência some do
relatório.

```bash
export AUDITORIA_TOLERANCIA_DE_VALOR="0.01"   # um centavo
export AUDITORIA_TOLERANCIA_DE_VALOR="0"      # exige igualdade exata
```

Sem a variável, vale o padrão declarado em `auditoria.tolerancia-de-valor-padrao`,
no `application.properties`. **Toda execução grava o valor usado e a origem** —
"configurada na instalação" ou "padrão do sistema; a instalação não configurou
outra" —, e os dois aparecem na saída do `auditar` ("tolerância R05"), no Resumo
da planilha ("Tolerância de valor (R05)"), no CSV e na tela de acurácia, nas
respostas da API, no resumo fixo do resultado, no detalhe do produto, no
histórico e nas telas da visão técnica. Execução gravada antes de 04/10/2026 sai
com "não registrada", nunca com um valor. Sem a variável e sem o padrão, a
aplicação para na subida dizendo o que falta. Ver D023.

*(Até 04/10/2026 esta seção dizia que a tolerância não tinha padrão e que a
aplicação parava na subida sem ela. Deixou de ser verdade em 21/09/2026, quando o
`application.properties` passou a trazer `0.01` dentro do placeholder da
propriedade — e nada registrava qual valor tinha sido usado.)*

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

> **Emenda de 04/10/2026:** "chamar sem argumento nenhum lista os comandos"
> deixou de valer em 21/09/2026 (`192c802`). Sem argumento, a aplicação sobe o
> `servir` com o perfil `api` (`AuditoriaApplication`): a interface web e a API
> ficam no ar em `127.0.0.1`, e nada é listado. A lista de comandos sai quando o
> nome do comando não é reconhecido. O resto da frase — comando sem as opções
> obrigatórias mostra o modo de usar — continua valendo. E a "configuração
> obrigatória acima" já não para a subida por falta: o sal se resolve sozinho
> desde a Etapa 10 (D011), usuário e senha do banco têm padrão desde 21/09/2026
> (ver a emenda em "Banco de dados"), e a tolerância tem padrão declarado desde a
> D023. Sem banco acessível, a subida continua parando.

Importar catálogo e tratar achado acontecem **só por aqui**, e continuam assim:
o primeiro decide o que o sistema afirma sobre a norma, o segundo é ato de uma
pessoa identificada, e não há autenticação na API.

> **Emenda da Etapa 12 (D013):** o parágrafo acima valeu até a Etapa 11. Passou a
> haver login, com três perfis, e os dois também estão na web — importar catálogo
> só para administrador, tratar achado para fiscal e administrador. Ver
> [Usuários e perfis](#usuários-e-perfis-etapa-12). Pela linha de comando,
> `importar-catalogo` continua como operação de quem tem acesso à máquina, e
> `tratar-achado` passou a exigir usuário e senha.

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
| `cobertura.csv` | `tabela` (e `natureza`, desde 04/10/2026 — ver abaixo) |

Todos levam também `vigenciaInicio`, `vigenciaFim` e `fonteNormativa`. O
separador é `;`, listas dentro de um campo usam `|`, linhas começadas por `#`
são comentário, e `vigenciaFim` em branco significa vigência aberta.

> **Emenda de 04/10/2026 (D026): importação parcial.** "Cinco arquivos, todos
> obrigatórios" passou a valer só para a **primeira** carga — quando o acervo
> não tem nenhuma, inclusive quando ficou vazio porque todos os rascunhos foram
> excluídos. Depois dela, um arquivo basta. As tabelas que não vieram são
> copiadas da **carga mais recente**, que fica intacta, com a natureza que têm
> lá, e o resultado é sempre uma carga nova, com a versão informada e a origem
> gravada (`derivada_de`). Nada é herdado em silêncio:
>
> - pela linha de comando, a pasta incompleta exige
>   `--partir-de=<versao>`, e a versão precisa ser a da mais recente. Sem a
>   opção, ou com outra carga, o comando recusa e diz qual é a mais recente,
>   com os instantes de importação e de alteração dela;
>
>   ```bash
>   java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar \
>       importar-catalogo --diretorio=/caminho/so-com-aliquotas \
>       --versao=2026-02 --partir-de=2026-01
>   ```
>
> - pela tela de cargas, a origem e as tabelas que virão dela são escritas
>   antes do botão, e o pedido leva a carga que a tela mostrou, com os dois
>   instantes. Se outra passou a ser a mais recente, ou se a mesma foi alterada,
>   ou excluída e importada de novo com o mesmo nome, nada é gravado e a tela
>   mostra a origem nova.
>
> Trocar `classificacao-tributaria.csv`, `registro-ncm.csv` ou `item-anexo.csv`
> exige `cobertura.csv` junto, e trocar `item-anexo.csv` exige
> `anexos-declarados.csv` junto quando a carga declara algum anexo carregado —
> na importação parcial e na edição. A cobertura herdada afirmaria, sobre a
> tabela nova, o período que alguém declarou para a antiga. A carga montada
> passa pelas mesmas recusas de uma importação completa.
>
> Com os cinco arquivos, a importação é **completa** e não herda nada — nem o
> `anexos-declarados.csv` que não veio, que nesse caso nunca é herdado. Desde a
> mesma data a tela de cargas aceita também o `anexos-declarados.csv`, que até
> então ela recusava como "não é de nenhuma tabela do catálogo".

**Os arquivos precisam estar em UTF-8.** Desde 04/10/2026, arquivo em outra
codificação — o caso comum é Windows-1252, o "ANSI" do Excel e do Bloco de
Notas — é recusado, pelo `importar-catalogo` e pela tela de cargas, com a mesma
mensagem: o nome do arquivo, a linha e o byte que não forma caractere em UTF-8.
O sistema nunca troca o caractere por "?" e grava. Até essa data a tela aceitava
o arquivo e gravava "DESCRI?O", enquanto a linha de comando o recusava sem
dizer a codificação esperada. O gabarito da acurácia segue a mesma regra. Ver
D024.

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

**Desde 30/09/2026**, `classificacao-tributaria.csv` aceita a coluna opcional
`tributacaoIntegral`, com `S` ou `N`, que diz se o código é de tributação
integral. É ela que a R04 1.1.0 lê. A redução deixou de ser o critério:

- coluna ausente no arquivo, ou célula em branco, quer dizer "não declarado", e
  a R04 responde não avaliado para NCM em anexo, com o motivo escrito. Um
  arquivo anterior à coluna continua importando;
- qualquer valor que não seja `S` ou `N` recusa a linha;
- `S` com `indicadorDeBeneficio` `true`, ou `S` com redução diferente de zero,
  recusa a linha. `S` com redução em branco é aceito;
- como no resto do catálogo, uma linha recusada recusa a carga inteira.

Quais códigos são integrais é decisão de quem monta o CSV, a partir da norma. O
sistema não sabe isso e não preenche nada. **Cargas importadas antes desta
coluna ficam com ela vazia** no banco: para a R04 voltar a concluir, importe uma
carga nova com a coluna. Ver a revisão de 30/09/2026 na D012.

**Desde 01/10/2026**, a R03 1.1.0 lê duas coisas novas do catálogo:

- **a coluna opcional `anexosAdmitidos`**, em `classificacao-tributaria.csv`. Ela
  traz os anexos em que o NCM de um código de benefício pode estar, como
  identificadores do `item-anexo.csv` separados por `|`. O valor `NENHUM` quer
  dizer que o código não exige anexo. Coluna ausente ou célula em branco quer
  dizer "não declarado", e a R03 responde não avaliado;
- **o arquivo opcional `anexos-declarados.csv`**, com as colunas
  `identificadorDoAnexo`, `tipoDeCodigo` (`NCM`, `NBS` ou `NCM_E_NBS`),
  `vigenciaInicio`, `vigenciaFim`, `fonteNormativa` e `natureza`. Ele faz duas
  coisas:
  - **lista os anexos válidos.** Se um código cita anexo fora da lista, a carga
    é recusada inteira. Também é recusada se um código cita anexo e o arquivo
    não veio;
  - **diz quais anexos estão carregados.**

**A vigência quer dizer coisas diferentes nos dois arquivos de anexo:**

- **em `item-anexo.csv`**, é a vigência do vínculo entre o NCM e o anexo,
  tirada da norma;
- **em `anexos-declarados.csv`**, `vigenciaInicio` preenchida quer dizer "este
  anexo está carregado **por completo** no `item-anexo.csv` a partir desta
  data". **Não é a vigência da lei.**
  - Anexo sem vigência existe, mas não está carregado. Para ele, a R03 responde
    não avaliado com o motivo "anexo admitido não carregado", e não aponta,
    porque um recorte do anexo não prova que o NCM está fora dele.
  - Anexo `NBS` com vigência e sem nenhuma linha está carregado e vazio de NCM.
    Para ele, a R03 aponta qualquer NCM.

O `anexos-declarados.csv` declara `natureza` como os quatro arquivos de dados, e
aparece na procedência da carga como a tabela `ANEXO_DECLARADO`.

Quais anexos cada código admite, e quais estão carregados, é decisão de quem
monta o CSV, a partir da norma. O sistema não preenche nada. Em
`exemplos/catalogo`, os valores vêm dos arquivos de decisão
`dados/decisoes/d2-anexos-admitidos.csv` e `dados/decisoes/anexos-declarados.csv`,
que não são versionados. Só o ANEXO-II e o ANEXO-III estão declarados
carregados. O IV, o V, o VI e o IX têm linhas no `item-anexo.csv`, mas são
recortes.

**Cargas importadas antes desta mudança** não têm nem a coluna nem o arquivo, e
a R03 responde não avaliado para todo código de benefício. Para ela voltar a
concluir, importe uma carga nova. Ver a revisão de 30/09 a 02/10/2026 na D012.

**Desde 03/10/2026, `classificacao-tributaria.csv` aceita a coluna opcional
`reducaoIncideSobre`**, com `ALIQUOTA` ou `BASE`, que diz sobre o que incide a
redução declarada para o código. Com `BASE` e redução diferente de zero, a R05
responde não avaliado, porque não sabe fazer a conta de redução de base. Coluna
ausente ou célula em branco quer dizer alíquota, como a coluna de redução sempre
significou; outro valor recusa a linha. Até essa data, a R05 reconhecia a redução
de base por dois CST escritos no código dela. Ver D017.

**Desde 03/10/2026**, a coluna `camposObrigatoriosCondicionados` de
`classificacao-tributaria.csv` tem três estados, e a R07 1.1.0 só conclui quando
o catálogo afirmou algo:

- **célula em branco** quer dizer "não declarado", e a R07 responde não avaliado
  com o motivo escrito. Até essa data, branco virava "nenhum campo exigido" e a
  R07 respondia conforme sem ter conferido nada;
- **`NENHUM`** declara que o código não exige campo condicionado — é o único
  caminho para a R07 responder conforme sem lista de nomes;
- **nomes separados por `|`**, os do vocabulário do sistema, que agora inclui os
  seis campos do grupo de redução de alíquota do XML: `reducaoAliquotaIbsUf`,
  `aliquotaEfetivaIbsUf`, `reducaoAliquotaIbsMunicipal`,
  `aliquotaEfetivaIbsMunicipal`, `reducaoAliquotaCbs` e `aliquotaEfetivaCbs`;
- coluna ausente continua recusando a carga, e `NENHUM` misturado com nome, nome
  vazio entre `|`, nome com espaço em volta e nome repetido recusam a linha.

Que campos cada código exige é decisão de quem monta o CSV, a partir da norma: o
sistema não deduz isso da redução nem de outro campo. Com `exemplos/catalogo`,
que não foi alterado, a R07 dá não avaliado em todos os códigos. **Cargas
importadas antes desta mudança** ficam com os códigos sem nome como "não
declarado"; só uma carga nova resolve. Ver D015.

**Os quatro arquivos de dados levam ainda `natureza`** — `FICTICIO` ou
`NORMATIVO` —, acrescentada na Etapa 11. **Desde 04/10/2026 o `cobertura.csv`
também leva**, com a mesma regra: a fonte que ele declara é citada como
fundamento dos apontamentos, e sem a coluna uma fonte fictícia saía sob a faixa
"Catálogo normativo". Carga gravada antes dessa data fica com a cobertura sem
natureza declarada e deixa de ser dita normativa até ser reimportada. Ver D021.

> **Se você já tem CSV de catálogo, eles param de importar até ganharem a
> coluna.** O acréscimo é mecânico: `;natureza` no cabeçalho e `;FICTICIO` (ou
> `;NORMATIVO`) em cada linha.

A coluna é obrigatória, e obrigatória de propósito: é ela que faz a tela avisar
que está exibindo dado de demonstração, sem depender de ninguém lembrar de ligar
uma configuração. Linha sem ela recusa o arquivo inteiro, e duas naturezas no
mesmo arquivo também — um arquivo tem uma procedência só.

**Desde 04/10/2026, dispositivo legal e fonte normativa sem nenhuma letra — "0",
"-" — recusam a linha**, em todos os arquivos do catálogo e em cada lado da forma
por tributo: um número solto não identifica norma alguma. O critério é só de
forma; o sistema não confere se o texto aceito corresponde à norma. Ver D022.

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

**Desde 03/10/2026, a R05 também lê essa cobertura.** Quando o cClassTrib da
nota não está na tabela de classificações, a regra só faz a conta com a alíquota
cheia se a tabela cobre a data da nota; fora da cobertura, responde não avaliado,
dizendo o período coberto e a data de emissão. Até essa data ela assumia "sem
redução" nos dois casos, e chegava a apontar divergência em nota correta. Ver
D016.

**Arquivo ausente não é lido como tabela vazia.** Para declarar uma tabela sem
registros, forneça o arquivo apenas com o cabeçalho: aí a ausência foi dita, e
não suposta.

> **Emenda da Etapa 12:** isso continua valendo só para `aliquota-vigente.csv`.
> As três tabelas com cobertura declarada — classificação, NCM e item de anexo —
> não podem mais vir vazias: dentro da cobertura, registro ausente vira
> apontamento, e cobertura sobre tabela vazia faria todo item do período ser
> apontado por silêncio do catálogo. A carga é recusada.
>
> **Desde 03/10/2026, a conferência é também por anexo:** cada anexo declarado
> carregado em `anexos-declarados.csv`, de tipo `NCM` ou `NCM_E_NBS`, precisa de
> ao menos uma linha em `item-anexo.csv` vigente no período em que foi declarado
> carregado. Sem isso a carga é recusada, com o nome do anexo. Anexo de `NBS` e
> anexo sem vigência continuam aceitos sem linha. Ver a revisão de 03/10/2026 na
> D013.
>
> **A carga também é recusada inteira quando houver qualquer linha inválida**, e a
> mensagem lista todas de uma vez — arquivo, linha, coluna e valor —, em vez de
> parar na primeira.

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

**Desde 04/10/2026, os arquivos que não puderam ser lidos são gravados junto da
execução**, e aparecem na API, na planilha — a linha "Arquivos que não puderam
ser lidos", abaixo de "Itens auditados", e a aba "Não lidos" — e na tela. Até
essa data o `auditar` só os imprimia, e as outras saídas diziam que nenhum arquivo
tinha falhado. Execução gravada antes disso pelo `auditar` fica com a leitura
"não registrada", e as três saídas dizem isso, em vez de zero. Ver D018.

**Desde 04/10/2026, o mesmo documento repetido no lote conta uma vez.** O caso
comum é o `-nfe.xml` e o `-procNFe.xml` da mesma nota. Se o conteúdo lido for
igual, a cópia é descartada, e a quantidade de cópias descartadas aparece na
CLI ("repetidos descartados"), na API, na planilha ("Documentos repetidos
descartados") e na tela. Se dois arquivos tiverem a mesma chave de acesso e
conteúdo diferente, **nenhum dos dois é auditado**: os dois aparecem na lista
dos que ficaram de fora, com o motivo, e quem quiser auditar um deles precisa
tirar o outro do lote. O sistema não escolhe. Ver D019.

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
    --usuario=<seu login> \
    --achado=<id que a listagem mostra> \
    --decisao=REFUTADO \
    --justificativa="Conferido com o contribuinte: o campo está correto."
```

Desde a Etapa 12, o comando **pede a senha no terminal**, sem eco, e grava quem
decidiu. A senha nunca entra como opção: opção fica no histórico do terminal. Sem
terminal interativo — entrada redirecionada, roteiro —, o comando recusa. Perfil
de consulta não registra tratativa.

`ACEITO` quando a incoerência procede; `REFUTADO` quando o documento está
correto. **A justificativa é obrigatória** — apontamento tratado sem razão
registrada é apontamento apagado.

**A tratativa sobrevive ao reprocessamento do lote** e, se a mesma incoerência
for gerada de novo, o apontamento já vem tratado.

**Se a versão da regra mudar, a tratativa não se aplica e o apontamento reabre.**
Isso é intencional: a justificativa foi dada contra um critério, e critério novo
é pergunta nova. A tratativa antiga não é apagada — fica no banco, presa à versão
em que foi dada. Mudar o conteúdo do item tem o mesmo efeito, pelo mesmo motivo.

### `criar-administrador`

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar criar-administrador \
    --login=<login> --nome="<nome da pessoa>"
```

**É assim que nasce o primeiro usuário**: nenhuma migration cria usuário nem
senha padrão. A senha é pedida no terminal, duas vezes, sem eco; mínimo de 12
caracteres. Se o login já existe, o comando redefine a senha, põe o perfil de
administrador e reativa — é a recuperação de acesso, já que não há recuperação
por e-mail.

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

> **Emenda de 04/10/2026 (D025):** o parágrafo acima não valia para
> "Justificativa" e "Tratado em", que saíam em branco no apontamento sem
> tratativa; hoje dizem `(sem tratativa)`. E o lado da tabela das evidências de
> R01 e R06 saía `(não informado)`, sobre um código que a nota informou; hoje diz
> que a tabela não tem registro, inclusive nas execuções antigas. O valor em risco
> sai na escala declarada — `7,11100`, e não `7,11` —, e o valor que um `double`
> não representa exatamente sai como texto, para não ser arredondado em silêncio.

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

> **Emenda de 04/10/2026:** a última frase valeu até a Etapa 11. Desde a Etapa 12
> (D013), com login e três perfis, importar catálogo e tratar achado existem
> também pela web — importar catálogo só para administrador, tratar achado para
> fiscal e administrador —, como a emenda da seção "Como usar" já registrava. A
> frase tinha ficado para trás aqui.

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

> **Emenda da Etapa 12:** passou a haver autenticação, e a API **continua** em
> `127.0.0.1`. Sem HTTPS, a senha trafegaria em texto claro pela rede; abrir o
> bind exige HTTPS antes, e HTTPS continua fora do escopo.

#### Histórico e acurácia pela web (Etapa 13)

```
GET  /api/analises                  ?pagina=0&tamanho=20&de=2026-01-01&ate=2026-01-31
                                    &situacaoMaisGrave=NAO_FOI_POSSIVEL_CONCLUIR
                                    &minimoDeDivergencias=1&maximoDeDivergencias=10
                                    &executor=<login> | &semExecutorRegistrado=true
GET  /api/analises/{id}/autoria     quem executou, ou "executor não registrado"
GET  /api/acuracia/previa           qual carga a medição vai usar e selar
POST /api/acuracia                  fiscal e administrador: "notas" (.zip/.xml),
                                    "gabarito" (.csv) e "cargaEsperada"
```

O histórico é paginado e filtrado no servidor, da análise mais recente para a mais
antiga. **O filtro de situação é pela situação mais grave presente**, e não pela
da maioria: uma análise com um produto não concluído entra como "não foi
possível concluir", mesmo que os outros estejam sem divergência. Execução feita
pela linha de comando fica com **executor não registrado**, por desenho.

**Desde 04/10/2026, execução da linha de comando também fica com a contagem de
produtos "não registrado"**, e não com zeros: o `auditar` não grava os itens que
leu, e sem eles não há produtos a contar. Os filtros de situação e de quantidade
**não** excluem essas execuções, porque excluir por um valor que não foi medido
esconderia resultado real; com um desses filtros ativo, a tela diz quantas estão
incluídas e por quê. A análise pela web tem contagem; a da linha de comando, não —
é escolha registrada na D020, com o que a resolveria.

**Medir a acurácia sela a carga de catálogo mais recente**, como o comando
`avaliar-acuracia` já fazia. A tela diz qual carga antes de medir, e o pedido
leva a carga que ela mostrou: se a mais recente mudou, nada é medido nem selado.
O consolidado é micro — soma das células —, e não há média macro (D008, D014).

#### Usuários e perfis (Etapa 12)

Toda a API exige sessão, aberta com login e senha em `POST /api/sessao`. A sessão
fica no servidor; não há "lembrar-me" nem recuperação por e-mail.

| Perfil | Pode |
|---|---|
| Administrador | tudo o que o fiscal faz, mais usuários e cargas de catálogo |
| Fiscal | enviar nota, corrigir análise, registrar tratativa, ler tudo |
| Consulta | só ler |

A permissão é conferida **no servidor**, endpoint por endpoint, antes de qualquer
controlador; esconder o botão na tela é só conveniência. A matriz está em
`infraestrutura/seguranca/MatrizDePermissoes.java`. Execução, análise e achado
não têm alteração nem exclusão para perfil nenhum.

```
POST   /api/sessao                         entrar: {"login", "senha"}
GET    /api/sessao                         quem está logado
DELETE /api/sessao                         sair
PUT    /api/sessao/senha                   trocar a própria senha
GET    /api/sessao/csrf                    o token que toda escrita manda de volta

GET|POST        /api/usuarios              só administrador
GET|PUT|DELETE  /api/usuarios/{id}         excluir quem já tratou achado desativa

GET    /api/cargas                         qualquer perfil
GET    /api/cargas/{versao}                inclui o que salvar uma edição faria
POST   /api/cargas                         só administrador: "versao" e os cinco CSV em "arquivos"
PUT    /api/cargas/{versao}                só administrador: "efeitoEsperado", "versaoNova" e os CSV que mudam
DELETE /api/cargas/{versao}                só administrador; carga usada é recusada

GET    /api/achados/{id}/tratativas        histórico, com quem decidiu e quando
POST   /api/achados/{id}/tratativas        fiscal e administrador: {"decisao", "justificativa"}

POST   /api/analises/{id}/correcoes        fiscal e administrador: análise nova, ligada a esta
GET    /api/analises/{id}/vinculos
```

**Carga de catálogo usada por uma análise está selada.** Excluí-la é recusado,
com a quantidade de análises que dependem dela; editá-la cria uma carga nova, e a
original fica intacta — porque cada análise é reaberta com a carga que usou, e
mudar aquela carga mudaria o fundamento que a análise cita. A tela diz o efeito
antes de a pessoa confirmar. Rascunho, que nunca foi usado, é editado no lugar e
excluído livremente. Editar é substituir tabela por CSV; não há formulário de
linha.

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

**A justificativa na planilha tem o próprio opt-in desde a Etapa 12**:
`auditoria.exportacao.expor-justificativa`, desligada por padrão. Desligada, a
célula traz o motivo da omissão. Até a Etapa 11 ela saía sempre.

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

> **Emenda de 04/10/2026:** a frase valeu até 03/09/2026. Desde então existe
> também `exemplos/` — um catálogo, um gabarito e um relatório de acurácia de
> demonstração —, fora de `src/test/resources` e fora do Git, porque é CSV (seção
> 8 do `CLAUDE.md`). E o catálogo de lá não é explicitamente fictício: cinco dos
> seis arquivos declaram a natureza `NORMATIVO`, e só o `cobertura.csv` declara
> `FICTICIO` (D021). O cabeçalho do `aliquota-vigente.csv` diz que os percentuais
> não são referência normativa, e as linhas declaram `NORMATIVO`; a correção desse
> arquivo é do usuário, à mão, como na D022. Os dados de `src/test/resources`
> continuam fictícios.

## Convenções

Código, nomes e comentários em português. Regras completas de trabalho no
repositório: [`CLAUDE.md`](CLAUDE.md). Decisões de arquitetura:
[`docs/DECISOES-ARQUITETURA.md`](docs/DECISOES-ARQUITETURA.md).
