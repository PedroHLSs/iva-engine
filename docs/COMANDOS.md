# Comandos da linha de comando

Todos os comandos rodam pelo mesmo jar:

```bash
mvn -DskipTests package
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar <comando> [--opção=valor ...]
```

No PowerShell, o mesmo comando funciona com `target\` ou `target/`. Propriedade
de sistema (`-D...`) vai **entre aspas** no PowerShell, senão ele parte o
argumento no ponto: `java "-Dspring.profiles.active=api" -jar ...`.

## Como os argumentos são lidos

- O primeiro argumento é o nome do comando; os demais são opções no formato
  `--nome=valor`, sem abreviação e sem posição. Opção sem valor, como
  `--apenas-abertos`, é um sinalizador.
- Opção repetida, opção sem `--` e opção que o comando não conhece são recusadas.
- Comando sem as opções obrigatórias mostra o modo de usar dele. Nome de comando
  desconhecido mostra a lista de comandos.
- **Sem nenhum argumento**, a aplicação sobe o `servir` com o perfil `api`: a
  interface web e a API ficam no ar em `127.0.0.1`.
- Erro de uso e recusa com mensagem própria saem com código **2**, sem rastro de
  pilha. Sucesso é **0**.

A aplicação sobe o contexto inteiro antes de qualquer comando, e por isso exige
um banco PostgreSQL acessível: as migrations do Flyway rodam na subida. Antes do
comando também roda o guarda do sal (ver [CONFIGURACAO.md](CONFIGURACAO.md)):
com documentos gravados e um sal diferente do que os produziu, todo comando é
recusado, exceto `diagnosticar-sal` e `recomecar-do-zero`.

| Comando | O que faz |
|---|---|
| [`importar-catalogo`](#importar-catalogo) | importa as tabelas normativas de um diretório de CSV |
| [`auditar`](#auditar) | audita os documentos de um diretório ou de um `.zip` |
| [`listar-achados`](#listar-achados) | lista os apontamentos gravados, do mais grave para o menos grave |
| [`tratar-achado`](#tratar-achado) | aceita ou refuta um apontamento, com justificativa |
| [`exportar`](#exportar) | emite o papel de trabalho de uma execução em planilha `.xlsx` |
| [`avaliar-acuracia`](#avaliar-acuracia) | mede precisão, recall e F1 do motor contra um gabarito |
| [`criar-administrador`](#criar-administrador) | cria um administrador, ou recupera o acesso de um login |
| [`servir`](#servir) | sobe a interface web e a API |
| [`diagnosticar-sal`](#diagnosticar-sal) | mostra de onde o sal veio e a impressão digital dele |
| [`recomecar-do-zero`](#recomecar-do-zero) | apaga o acervo gravado e adota o sal atual; preserva tratativas e catálogo |

---

## `importar-catalogo`

```
importar-catalogo --diretorio=<caminho> [--versao=<nome>] [--partir-de=<versao>]
```

| Opção | Obrigatória | O que é |
|---|---|---|
| `--diretorio` | sim | diretório com os CSV do catálogo |
| `--versao` | não | nome da carga, registrado em cada execução feita contra ela. Se omitido, é gerado a partir da data e hora |
| `--partir-de` | só na importação parcial | a carga mais recente, de onde vêm as tabelas que a pasta não traz |

É o único caminho, junto da tela de cargas, pelo qual conteúdo da legislação
entra no sistema. Os arquivos e as colunas estão em
[FORMATO-CSV.md](FORMATO-CSV.md).

**Importação completa.** Com os cinco arquivos obrigatórios na pasta, nada é
herdado — nem um `anexos-declarados.csv` que não veio — e `--partir-de`, se
informada, não é usada. A primeira carga do acervo é sempre completa.

**Importação parcial.** Depois da primeira carga, a pasta pode trazer só parte
dos arquivos. As tabelas que faltam são copiadas da carga indicada em
`--partir-de`, que precisa ser a mais recente e fica intacta; o resultado é uma
carga nova, com a origem gravada. Sem `--partir-de`, ou com outra carga, o
comando recusa e diz qual é a mais recente, com os instantes de importação e de
alteração dela. Nada é herdado em silêncio.

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar \
    importar-catalogo --diretorio=/caminho/so-com-aliquotas \
    --versao=carga-2 --partir-de=carga-1
```

Na importação parcial, trocar `classificacao-tributaria.csv`,
`registro-ncm.csv` ou `item-anexo.csv` exige `cobertura.csv` junto, e trocar
`item-anexo.csv` exige `anexos-declarados.csv` junto quando a carga de origem
declara algum anexo carregado: a declaração herdada afirmaria, sobre a tabela
nova, o que alguém declarou para a antiga. A carga montada passa pelas mesmas
recusas de uma importação completa.

**A carga é recusada inteira** quando qualquer linha de qualquer arquivo tem
problema, com todos os problemas numa mensagem só.

**Cada importação vira uma carga identificada pela versão.** A auditoria usa a
mais recente e registra a versão dela em cada execução; as cargas antigas
ficam, para que relatórios produzidos contra elas continuem conferíveis. A carga
usada por uma auditoria ou medição fica **selada**: não pode mais ser excluída
nem alterada no lugar (ver `PUT /api/cargas/{versao}` em [API.md](API.md)).

---

## `auditar`

```
auditar --origem=<caminho>
```

| Opção | Obrigatória | O que é |
|---|---|---|
| `--origem` | sim | diretório com arquivos `.xml`, ou um arquivo `.zip` que os contenha. Subdiretórios são percorridos |

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar auditar --origem=dados/lote
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar auditar --origem=dados/lote.zip
```

Lê os XML da origem, monta o contexto normativo **na data de emissão de cada
documento**, roda as sete regras com a carga mais recente e grava documentos,
apontamentos, avaliações não concluídas, os arquivos que não puderam ser lidos e
a identificação da execução.

O resumo impresso traz apontamentos por severidade e por regra, avaliações que
não concluíram, arquivos que não puderam ser lidos, documentos repetidos
descartados e a tolerância usada pela R05 com a origem dela. Os números importam
juntos: um lote com zero apontamentos e milhares de avaliações não concluídas não
é um lote limpo.

- **Arquivo ilegível não interrompe o lote.** Ele é registrado com o motivo, e
  aparece na API, na planilha (linha "Arquivos que não puderam ser lidos" e aba
  "Não lidos") e na tela.
- **O mesmo documento repetido no lote conta uma vez.** O caso comum é o
  `-nfe.xml` e o `-procNFe.xml` da mesma nota. Cópia com o mesmo conteúdo é
  descartada e contada. Se dois arquivos tiverem a mesma chave de acesso e
  conteúdo diferente, **nenhum dos dois é auditado**: os dois vão para a lista dos
  que ficaram de fora, com o motivo, e o sistema não escolhe.
- **Reprocessar o mesmo lote não duplica apontamento.** O apontamento é
  identificado pelo resumo do conteúdo do item, pelo identificador da regra e pela
  versão da regra, não pela linha em que foi gravado.
- A execução feita por este comando **não grava os itens lidos**. Por isso, ao
  abrir essa execução pela interface de conferência, a lista de produtos vem
  vazia e o histórico mostra a contagem de produtos como "não registrado". A
  execução também fica com "executor não registrado". A análise enviada pela
  interface web não tem essas limitações.

---

## `listar-achados`

```
listar-achados [--severidade=<CRITICA|GRAVE|MODERADA|INFORMATIVA>] [--regra=<id>]
               [--chave=<44 dígitos>] [--apenas-abertos] [--limite=<n>]
```

| Opção | O que faz |
|---|---|
| `--severidade` | só apontamentos dessa gravidade |
| `--regra` | só apontamentos dessa regra, por exemplo `R05` |
| `--chave` | só apontamentos desse documento |
| `--apenas-abertos` | omite os que já têm tratativa aplicável |
| `--limite` | quantos listar; padrão **50** |

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar listar-achados \
    --severidade=CRITICA --apenas-abertos --limite=200
```

Lista do mais grave para o menos grave, com evidências e com a tratativa que
houver. Apontamento tratado continua aparecendo, marcado com a decisão e a
justificativa. O identificador de cada apontamento, mostrado aqui, é o que o
`tratar-achado` pede.

---

## `tratar-achado`

```
tratar-achado --usuario=<login> --achado=<id> --decisao=<ACEITO|REFUTADO> --justificativa=<texto>
```

| Opção | O que é |
|---|---|
| `--usuario` | login de quem decide |
| `--achado` | identificador do apontamento, como sai em `listar-achados` |
| `--decisao` | `ACEITO` se a incoerência procede; `REFUTADO` se o documento está correto |
| `--justificativa` | por que, em texto livre. Obrigatória |

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar tratar-achado \
    --usuario=fulano --achado=<id> --decisao=REFUTADO \
    --justificativa="O campo está correto segundo o pedido de compra."
```

- **A senha é pedida no terminal**, sem eco, e o comando grava quem decidiu. A
  senha nunca entra como opção, porque opção fica no histórico do terminal. Sem
  terminal interativo — entrada redirecionada, roteiro —, o comando recusa.
- Perfil de consulta não registra tratativa.
- **A justificativa é obrigatória**: apontamento tratado sem razão registrada é
  apontamento apagado.
- **A tratativa sobrevive ao reprocessamento do lote**: se a mesma incoerência
  for gerada de novo, o apontamento já vem tratado.
- **Se a versão da regra mudar, o apontamento reabre.** A justificativa foi dada
  contra um critério, e critério novo é pergunta nova. A tratativa antiga não é
  apagada; fica presa à versão em que foi dada. Mudar o conteúdo do item tem o
  mesmo efeito.

---

## `exportar`

```
exportar --arquivo=<caminho.xlsx> [--execucao=<id>]
```

| Opção | O que é |
|---|---|
| `--arquivo` | onde gravar a planilha. Pastas que faltarem são criadas |
| `--execucao` | identificador da execução a exportar. Se omitido, exporta a mais recente |

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar exportar --arquivo=relatorios/lote.xlsx
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar exportar --arquivo=relatorios/refeito.xlsx --execucao=<id>
```

Com `--execucao`, reemite o papel de trabalho de qualquer execução anterior, com
os apontamentos que **aquela** execução produziu e a identificação que ela tinha.
A planilha tem quatro abas, e cada uma abre com a faixa de procedência do
catálogo (destacada quando o catálogo é fictício, parcialmente fictício ou de
procedência não declarada):

| Aba | O que traz |
|---|---|
| **Resumo** | identificação da execução (data, versão do catálogo e natureza dele, versão das regras, resumo da entrada, tolerância da R05), totais por severidade e por regra, arquivos não lidos, documentos repetidos descartados, e os não avaliados com os motivos agrupados |
| **Achados** | uma linha por apontamento, com evidências, fundamento normativo, vigência aplicada, valor em risco e status de tratativa |
| **Não avaliados** | uma linha por avaliação que não concluiu, com o motivo |
| **Não lidos** | uma linha por arquivo que não pôde ser lido, com o motivo |

- **A identificação da execução abre o Resumo.** Sem saber contra qual catálogo a
  planilha foi produzida, um apontamento que deixou de proceder por mudança de
  tabela fica indistinguível de um erro do sistema.
- **Nenhum identificador em texto claro sai na planilha.** O documento aparece
  pelo pseudônimo da chave de acesso — cujos dígitos carregam o CNPJ do emitente
  — mais modelo, série, número, data de emissão e UF. O pseudônimo é estável
  dentro de uma instalação, então duas planilhas do mesmo acervo podem ser
  cruzadas. A descrição do produto nunca vai para a planilha.
- **A justificativa da tratativa é omitida por padrão**, com o motivo na célula;
  libera-se com `auditoria.exportacao.expor-justificativa=true` (ver
  [CONFIGURACAO.md](CONFIGURACAO.md)).
- **Ausência é escrita, nunca deixada em branco:** campo que não veio no documento
  sai `(não informado)`, regra sem valor de referência `(sem referência)`,
  vigência sem fim `(sem fim declarado)`, apontamento sem decisão `ABERTO`, e
  "Justificativa" e "Tratado em" sem tratativa `(sem tratativa)`. Do lado da
  tabela, as evidências de R01 e R06 dizem que a tabela carregada não tem registro
  com aquele código. Execução gravada sem o registro da leitura escreve
  `(não registrado: …)` onde caberia a contagem de não lidos.
- **O valor em risco sai na escala declarada** (`7,11100`, e não `7,11`); o valor
  que um `double` não representa exatamente sai como texto, para não ser
  arredondado em silêncio.

---

## `avaliar-acuracia`

```
avaliar-acuracia --origem=<caminho> --gabarito=<caminho.csv> [--relatorio=<caminho.csv>]
```

| Opção | Obrigatória | O que é |
|---|---|---|
| `--origem` | sim | diretório com `.xml`, ou um `.zip` que os contenha |
| `--gabarito` | sim | CSV rotulado à mão (formato em [FORMATO-CSV.md](FORMATO-CSV.md#gabarito-da-acurácia)) |
| `--relatorio` | não | onde gravar o resultado em CSV. Sem ela, só no terminal |

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar avaliar-acuracia \
    --origem=dados/amostra --gabarito=gabarito.csv --relatorio=relatorios/acuracia.csv
```

Mede o motor contra o gabarito e imprime precisão, recall e F1 por regra e
consolidados. O relatório em CSV tem uma linha por regra e uma consolidada, e a
identificação da rodada — carga, regras, tolerância — em linhas de comentário no
topo.

- **`NAO_AVALIADO` fica fora de precisão, recall e F1** e aparece na
  **cobertura**, que é `avaliados / total do gabarito`. Um sistema que não avalia
  nada tem precisão indefinida, não perfeita.
- **Métrica sem denominador sai `(indefinida)`**, nunca zero ou um. Precisão exige
  `VP + FP > 0`, recall exige `VP + FN > 0`, e F1 só existe quando os dois
  existem.
- **O consolidado soma células**, não faz média das métricas por regra.
- **Toda regra do conjunto ganha linha**, inclusive as que o gabarito não cita,
  com métricas indefinidas.
- **Linha de gabarito cujo item o motor não avaliou** — documento fora do lote,
  item inexistente — é contada em `SemAval`, listada com o endereço, e fica fora
  das métricas.
- **Nada é gravado no banco**, exceto o selo da carga usada: medir não é
  auditar. A medição roda o motor de novo, porque o banco não guarda avaliação
  conforme.

---

## `criar-administrador`

```
criar-administrador --login=<login> --nome=<nome>
```

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar criar-administrador \
    --login=admin --nome="Nome da Pessoa"
```

É assim que nasce o primeiro usuário: nenhuma migration cria usuário nem senha
padrão. A senha é pedida no terminal, duas vezes, sem eco; mínimo de **12
caracteres**. Se o login já existe, o comando redefine a senha, põe o perfil de
administrador e reativa o usuário — é a recuperação de acesso, já que não há
recuperação por e-mail. Exige terminal interativo.

---

## `servir`

```
servir
```

Sobe a interface web e a API. Não recebe opção. Escuta só em `127.0.0.1`, na
porta de `AUDITORIA_API_PORTA` (padrão **8080**). Encerre com Ctrl+C.

O jeito mais simples é chamar o jar **sem argumento nenhum**, que sobe o `servir`
já com o perfil `api`. Chamado pelo nome, o comando exige o perfil `api`, que
**não pode vir como argumento** — o interpretador recusaria
`--spring.profiles.active`. Ele entra por propriedade de sistema, antes do
`-jar`, ou por variável de ambiente:

```bash
java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar
java -Dspring.profiles.active=api -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar servir
SPRING_PROFILES_ACTIVE=api java -jar target/auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar servir
```

```powershell
java -jar target\auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar
java "-Dspring.profiles.active=api" -jar target\auditoria-ibs-cbs-0.0.1-SNAPSHOT.jar servir
```

Sem o perfil, `servir` recusa e mostra a invocação certa, em vez de deixar o
processo pendurado sem servidor. Endpoints e perfis em [API.md](API.md); telas
em [INTERFACE-WEB.md](INTERFACE-WEB.md).

---

## `diagnosticar-sal`

```
diagnosticar-sal
```

Não recebe opção. Informa a origem do sal em uso (propriedade, variável de
ambiente, arquivo local ou sorteado agora), o arquivo onde ele está quando houver,
a impressão digital do sal em uso e a que está gravada no banco. **O sal em si
não é mostrado.** Roda mesmo quando o guarda de troca de sal recusa os outros
comandos.

---

## `recomecar-do-zero`

```
recomecar-do-zero --confirmo=sim
```

Apaga documentos, itens, execuções e apontamentos, e passa a registrar o sal
atual como o do acervo. Use quando a troca de sal foi intencional. Sem
`--confirmo=sim`, não faz nada.

**Não apaga as tratativas** — a chave delas é o hash do item, que não leva sal, e
elas voltam a valer quando o lote for reprocessado — nem o catálogo importado,
que não tem sal. O selo das cargas também sobrevive.
